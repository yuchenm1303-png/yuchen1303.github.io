package com.yuchen.ailedger.service

sealed interface AgentTaskOutcome {
    val message: String

    data class Completed(override val message: String) : AgentTaskOutcome
    data class Failed(override val message: String) : AgentTaskOutcome
    data class Paused(override val message: String) : AgentTaskOutcome
    data class Cancelled(override val message: String) : AgentTaskOutcome
    data class BudgetExceeded(override val message: String) : AgentTaskOutcome
}

internal data class AgentTaskTerminalPresentation(
    val status: String,
    val currentAction: String,
    val defaultMessage: String,
    val logPrefix: String,
)

internal fun AgentTaskOutcome.toTerminalPresentation(): AgentTaskTerminalPresentation {
    return when (this) {
        is AgentTaskOutcome.Completed -> AgentTaskTerminalPresentation(
            status = "已完成",
            currentAction = "任务完成",
            defaultMessage = "任务完成",
            logPrefix = "完成",
        )
        is AgentTaskOutcome.Failed -> AgentTaskTerminalPresentation(
            status = "执行失败",
            currentAction = "任务异常",
            defaultMessage = "智能体执行失败",
            logPrefix = "失败",
        )
        is AgentTaskOutcome.Paused -> AgentTaskTerminalPresentation(
            status = "已暂停",
            currentAction = "任务已暂停",
            defaultMessage = "任务暂停",
            logPrefix = "暂停",
        )
        is AgentTaskOutcome.Cancelled -> AgentTaskTerminalPresentation(
            status = "已手动停止",
            currentAction = "用户手动停止",
            defaultMessage = "用户手动停止了本次智能体任务。",
            logPrefix = "停止",
        )
        is AgentTaskOutcome.BudgetExceeded -> AgentTaskTerminalPresentation(
            status = "已达上限",
            currentAction = "达到执行上限",
            defaultMessage = "智能体任务达到执行上限。",
            logPrefix = "上限",
        )
    }
}
