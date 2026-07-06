package com.yuchen.ailedger.service

import android.content.Context
import android.content.Intent
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Replay phases for a cloud-visual Skill run. */
enum class SkillReplayPhase {
    Idle,
    Starting,
    Running,
    Completed,
    Failed,
}

data class SkillReplayState(
    val phase: SkillReplayPhase = SkillReplayPhase.Idle,
    val workflowId: String? = null,
    val title: String = "",
    val message: String = "",
) {
    val active: Boolean
        get() = phase == SkillReplayPhase.Starting || phase == SkillReplayPhase.Running
}

data class SkillReplayOutcome(
    val completed: Boolean,
    val message: String,
)

/**
 * Replay 不在本地复现演示路线。批准后的 Skill 仅作为云端视觉主循环的任务上下文，
 * 观察、规划、验证和恢复全部复用现有 VisualLoopRunner；本地只校验批准状态、输入和硬安全边界。
 */
object OperationSkillReplayCoordinator {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SkillReplayState())
    private val running = AtomicBoolean(false)

    val state: StateFlow<SkillReplayState> = mutableState.asStateFlow()

    suspend fun run(
        context: Context,
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        inputValues: Map<String, String>,
    ): SkillReplayOutcome = mutex.withLock {
        if (!running.compareAndSet(false, true)) {
            return SkillReplayOutcome(false, "已有 Skill 正在运行。")
        }
        var enabledAgentForReplay = false
        try {
            require(draft.id == skill.workflowId) { "Skill 与草稿不匹配" }
            require(draft.status in setOf(WorkflowDraftStatus.Approved, WorkflowDraftStatus.Verified)) {
                "Skill 尚未通过用户审核"
            }
            val validation = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Execution)
            require(validation.canProceed) {
                validation.blockingIssues.firstOrNull()?.message ?: "Skill 未通过执行前安全校验"
            }
            if (!AiAgentAccessibilityService.isConnected()) {
                return failBeforeStart(draft, skill, "请先启用 AI 智能体无障碍服务。")
            }
            val missing = skill.inputs.filter { input ->
                input.required && !input.sensitive && inputValues[input.key].orEmpty().trim().isBlank()
            }
            if (missing.isNotEmpty()) {
                return failBeforeStart(
                    draft,
                    skill,
                    "请先填写：${missing.joinToString { it.label }}。",
                )
            }

            if (!AgentRuntimeController.isEnabled()) {
                AgentRuntimeController.setEnabled(true)
                enabledAgentForReplay = true
            }

            mutableState.value = SkillReplayState(
                phase = SkillReplayPhase.Starting,
                workflowId = draft.id,
                title = skill.name,
                message = "正在打开授权应用并启动视觉 Skill…",
            )
            val opened = openFirstAuthorizedApp(context, draft)
            if (opened) delay(TARGET_APP_SETTLE_DELAY_MS)

            val goal = buildVisualGoal(draft, skill, inputValues)
            mutableState.value = mutableState.value.copy(
                phase = SkillReplayPhase.Running,
                message = if (opened) {
                    "视觉智能正在授权应用中重新完成 Skill。"
                } else {
                    "未能自动打开授权应用，请手动切到目标应用后继续。"
                },
            )
            val result = AgentOrchestrator(
                aiWorkerClient = AiWorkerClient(),
                appContext = context.applicationContext,
            ).run(
                goal = goal,
                modelPreference = ChatModel.DeepSeekV4,
                maxSteps = MAX_REPLAY_STEPS,
                executionMode = AgentExecutionMode.ExplicitAgent,
            )
            val outcome = SkillReplayOutcome(
                completed = result.completed,
                message = result.message.ifBlank {
                    if (result.completed) "Skill 已完成。" else "Skill 未能完成。"
                },
            )
            mutableState.value = SkillReplayState(
                phase = if (outcome.completed) SkillReplayPhase.Completed else SkillReplayPhase.Failed,
                workflowId = draft.id,
                title = skill.name,
                message = outcome.message,
            )
            outcome
        } catch (cancelled: CancellationException) {
            mutableState.value = SkillReplayState(
                phase = SkillReplayPhase.Failed,
                workflowId = draft.id,
                title = skill.name,
                message = "Skill Replay 已取消。",
            )
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message?.takeIf(String::isNotBlank) ?: "Skill Replay 失败。"
            mutableState.value = SkillReplayState(
                phase = SkillReplayPhase.Failed,
                workflowId = draft.id,
                title = skill.name,
                message = message,
            )
            SkillReplayOutcome(false, message)
        } finally {
            if (enabledAgentForReplay && AgentRuntimeController.isEnabled()) {
                AgentRuntimeController.setEnabled(false)
            }
            running.set(false)
        }
    }

    fun resetTerminalState() {
        if (!mutableState.value.active) mutableState.value = SkillReplayState()
    }

    private fun failBeforeStart(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        message: String,
    ): SkillReplayOutcome {
        mutableState.value = SkillReplayState(
            phase = SkillReplayPhase.Failed,
            workflowId = draft.id,
            title = skill.name,
            message = message,
        )
        return SkillReplayOutcome(false, message)
    }

    private fun openFirstAuthorizedApp(
        context: Context,
        draft: LearnedWorkflowDraft,
    ): Boolean = runCatching {
        val packageName = draft.appScope.normalizedPackages.firstOrNull().orEmpty()
        if (packageName.isBlank()) return@runCatching false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return@runCatching false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.applicationContext.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun buildVisualGoal(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        inputValues: Map<String, String>,
    ): String = buildString {
        appendLine("执行一个已经由用户演示并审核批准的视觉 Skill。")
        appendLine("Skill：${skill.name}")
        appendLine("用户原始教学目标：${draft.goal}")
        appendLine("云端整理后的目标说明：${skill.description}")
        appendLine("允许应用包：${draft.appScope.normalizedPackages.joinToString()}")
        appendLine()
        appendLine("执行要求：")
        appendLine("- 用户原始教学目标优先级最高；云端整理说明只作为补充，不得把任务简化成仅打开应用。")
        appendLine("- 如果目标是进入某个具体页面，例如设置页、详情页或功能页，必须继续观察并操作到该页面，不能在应用首页提前结束。")
        appendLine("- 只有同时满足用户原始教学目标和下方成功标准，才允许调用 finish。")
        appendLine()
        appendLine("本次输入：")
        if (skill.inputs.isEmpty()) {
            appendLine("- 无额外输入")
        } else {
            skill.inputs.forEach { input ->
                val value = if (input.sensitive) {
                    "[敏感内容不得读取或代填；需要时请求用户亲自完成]"
                } else {
                    inputValues[input.key].orEmpty().trim()
                }
                appendLine("- ${input.label}：$value")
            }
        }
        appendLine()
        appendLine("从演示中提炼的操作原则：")
        skill.operatingPrinciples.forEach { appendLine("- $it") }
        appendLine("成功标准：")
        skill.successCriteria.forEach { appendLine("- $it") }
        appendLine("安全边界：")
        (skill.safetyRules + listOf(
            "只允许在上述应用范围内完成目标；桌面、键盘和必要系统过渡只用于进入或退出授权应用。",
            "视觉截图是唯一界面理解权威，不得使用 Resource ID、无障碍节点或录制坐标复现路线。",
            "根据当前屏幕重新规划，不要求与演示路径完全相同。",
            "密码、验证码、支付确认、删除及不可逆操作必须暂停并交给用户确认或亲自完成。",
            "无法可靠判断时请求用户帮助，不得自由探索无关页面。",
        )).distinct().forEach { appendLine("- $it") }
        appendLine()
        appendLine("现在请观察当前屏幕并完成目标。每次动作后重新观察和验证，只有满足用户原始教学目标与成功标准才能结束。")
    }.take(MAX_GOAL_CHARS)

    private const val TARGET_APP_SETTLE_DELAY_MS = 650L
    private const val MAX_REPLAY_STEPS = 30
    private const val MAX_GOAL_CHARS = 8_000
}
