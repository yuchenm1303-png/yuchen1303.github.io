package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
                return SkillReplayOutcome(false, "请先启用 AI 智能体无障碍服务。")
            }
            val missing = skill.inputs.filter { input ->
                input.required && !input.sensitive && inputValues[input.key].orEmpty().trim().isBlank()
            }
            if (missing.isNotEmpty()) {
                return SkillReplayOutcome(
                    false,
                    "请先填写：${missing.joinToString { it.label }}。",
                )
            }

            mutableState.value = SkillReplayState(
                phase = SkillReplayPhase.Starting,
                workflowId = draft.id,
                title = skill.name,
                message = "正在把批准后的 Skill 交给视觉智能…",
            )
            val goal = buildVisualGoal(draft, skill, inputValues)
            mutableState.value = mutableState.value.copy(
                phase = SkillReplayPhase.Running,
                message = "视觉智能正在根据当前屏幕重新完成 Skill。",
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
            running.set(false)
        }
    }

    fun resetTerminalState() {
        if (!mutableState.value.active) mutableState.value = SkillReplayState()
    }

    private fun buildVisualGoal(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        inputValues: Map<String, String>,
    ): String = buildString {
        appendLine("执行一个已经由用户演示并审核批准的视觉 Skill。")
        appendLine("Skill：${skill.name}")
        appendLine("目标：${skill.description}")
        appendLine("允许应用包：${draft.appScope.normalizedPackages.joinToString()}")
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
        appendLine("现在请观察当前屏幕并完成目标。每次动作后重新观察和验证，只有满足成功标准才能结束。")
    }.take(MAX_GOAL_CHARS)

    private const val MAX_REPLAY_STEPS = 30
    private const val MAX_GOAL_CHARS = 8_000
}
