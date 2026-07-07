package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.AgentAccessibilityGuideActivity
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.VisualSkillRouteStep
import com.yuchen.ailedger.model.WorkflowDraftStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
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
                val message = "需要先开启手机智能体无障碍服务，才能运行视觉 Skill。"
                AgentAccessibilityGuideActivity.open(context)
                return failBeforeStart(draft, skill, message)
            }
            val targetPackage = draft.appScope.normalizedPackages.firstOrNull().orEmpty().trim()
            if (targetPackage.isBlank()) {
                return failBeforeStart(draft, skill, "Skill 缺少授权应用范围，无法运行。")
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
                message = "正在交给视觉循环启动并验证授权应用…",
            )

            val goal = buildVisualGoal(draft, skill, inputValues, targetPackage)
            mutableState.value = mutableState.value.copy(
                phase = SkillReplayPhase.Running,
                message = "视觉智能正在优先沿演示路线运行 Skill。",
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

    private fun buildVisualGoal(
        draft: LearnedWorkflowDraft,
        skill: LearnedVisualSkill,
        inputValues: Map<String, String>,
        targetPackage: String,
    ): String = buildString {
        appendLine("执行一个已经由用户演示并审核批准的视觉 Skill。")
        appendLine("Skill：${skill.name}")
        appendLine("用户原始教学目标：${draft.goal}")
        appendLine("云端整理后的目标说明：${skill.description}")
        appendLine("目标应用包：$targetPackage")
        appendLine("允许应用包：${draft.appScope.normalizedPackages.joinToString()}")
        appendLine()
        appendLine("路线执行协议：")
        appendLine("- 先通过 open_app 打开并验证目标应用包 $targetPackage，让视觉循环完成目标工作面绑定。")
        appendLine("- 完成目标时优先遵循用户演示路线，不要把 Skill 当作一次全新自由探索任务。")
        appendLine("- 不使用固定坐标、Resource ID、无障碍节点或录制坐标；路线约束只作为语义锚点和顺序约束。")
        appendLine("- 维护 routeCursor：先处理当前路线步骤，看到该步骤完成证据后再推进到下一步。")
        appendLine("- 每次动作前先判断当前屏幕对应哪一个路线步骤；如果已经在路线中间页，继续当前步骤。")
        appendLine("- 只有当前步骤锚点确实不可用，或界面版本阻断该路线时，才使用对应兜底策略。")
        appendLine("- 如果采用兜底策略，请在 reason 中说明 routeFallback、当前步骤序号、缺失锚点和改道原因。")
        appendLine("- 如果目标是进入某个具体页面，例如设置页、详情页或功能页，必须继续观察并操作到该页面，不能在应用首页提前结束。")
        appendLine("- 只有同时满足用户原始教学目标、演示路线意图和成功标准，才允许调用 finish。")
        appendLine()
        appendRouteContract(skill)
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
            "只在上述应用范围内完成目标；桌面、键盘和必要系统过渡只用于进入或退出授权应用。",
            "视觉截图是唯一界面理解权威，不使用 Resource ID、无障碍节点或录制坐标复现路线。",
            "根据当前屏幕重新规划，但默认沿演示路线语义骨架前进，不自由寻找完全不同入口。",
            "密码、验证码、支付确认、删除及不可逆操作必须暂停并交给用户确认或亲自完成。",
            "无法可靠判断时请求用户帮助，不要探索无关页面。",
        )).distinct().forEach { appendLine("- $it") }
        appendLine()
        appendLine("现在请按标准视觉循环执行：先打开并验证目标应用包，再按照 routeCursor 和演示路线检查点观察当前屏幕并完成目标。每次动作后重新观察和验证，只有满足用户原始教学目标、演示路线意图与成功标准才能结束。")
    }.take(MAX_GOAL_CHARS)

    private fun StringBuilder.appendRouteContract(skill: LearnedVisualSkill) {
        val routeSteps = skill.routeSteps.sortedBy(VisualSkillRouteStep::order)
        if (routeSteps.isNotEmpty()) {
            appendLine("演示路线检查点（必须优先按顺序遵循）：")
            routeSteps.forEachIndexed { index, step ->
                appendLine("${index + 1}. ${step.instruction}")
                if (step.startState.isNotBlank()) appendLine("   起始状态：${step.startState}")
                step.effectiveAnchors.takeIf { it.isNotEmpty() }?.let { anchors ->
                    appendLine("   视觉锚点：${anchors.joinToString("；")}")
                }
                if (step.preferredAction.isNotBlank()) appendLine("   推荐动作：${step.preferredAction}")
                step.effectiveEvidence.takeIf { it.isNotEmpty() }?.let { evidence ->
                    appendLine("   完成证据：${evidence.joinToString("；")}")
                }
                if (step.discouragedActions.isNotEmpty()) {
                    appendLine("   不建议改道：${step.discouragedActions.joinToString("；")}")
                }
                if (step.effectiveFallback.isNotBlank()) appendLine("   兜底策略：${step.effectiveFallback}")
                appendLine("   可跳过：${if (step.skippable) "是，但需要说明已满足后续证据" else "否，除非当前屏幕已经满足后续步骤证据"}")
            }
            appendLine("routeCursor 规则：")
            appendLine("- routeCursor 从第 1 步开始，只能在当前步骤完成证据出现后前进。")
            appendLine("- 当前步骤的视觉锚点可见时，围绕该锚点选择动作。")
            appendLine("- 当前屏幕已经满足后续步骤证据时，可以把游标前移到对应步骤，但需要说明证据。")
            appendLine("- 偏离演示路线的动作需要作为 fallback 处理，并在 reason 中说明。")
        } else {
            appendLine("演示路线检查点（旧版 Skill，无 routeSteps 字段）：")
            appendLine("- 这个 Skill 是旧版格式，没有独立路线检查点。请把下面 operatingPrinciples 当作用户演示路线的顺序约束，而不是普通建议。")
            appendLine("- 如果 operatingPrinciples 提到头像、个人中心、设置入口等演示锚点，请先尝试这些锚点；不要直接改走底部导航、搜索或其他自由探索入口，除非该锚点在当前页面确实不可用。")
        }
    }

    private const val MAX_REPLAY_STEPS = 30
    private const val MAX_GOAL_CHARS = 10_000
}
