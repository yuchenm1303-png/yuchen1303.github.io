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

internal object AgentTaskOutcomeResolver {
    fun resolve(
        completed: Boolean,
        stoppedForConfirmation: Boolean,
        message: String,
    ): AgentTaskOutcome {
        val clean = message.trim()
        if (completed) return AgentTaskOutcome.Completed(clean)
        if (stoppedForConfirmation) return AgentTaskOutcome.Paused(clean)

        val normalized = clean.lowercase()
        return when {
            normalized.contains("用户已手动停止") ||
                normalized.contains("用户手动停止") ||
                normalized.contains("本次智能体任务已取消") ||
                normalized.contains("user stopped") ||
                normalized.contains("cancelled") ||
                normalized.contains("canceled") -> AgentTaskOutcome.Cancelled(clean)

            normalized.contains("达到执行上限") ||
                normalized.contains("达到安全动作预算") ||
                normalized.contains("达到安全运行时长") ||
                normalized.contains("action budget") ||
                normalized.contains("planning budget") ||
                normalized.contains("budget reached") -> AgentTaskOutcome.BudgetExceeded(clean)

            normalized.contains("已暂停") ||
                normalized.contains("暂停等待") ||
                normalized.contains("等待用户接管") ||
                normalized.contains("用户接管") ||
                normalized.contains("visual loop stopped") ||
                normalized.contains("task paused") -> AgentTaskOutcome.Paused(clean)

            else -> AgentTaskOutcome.Failed(clean)
        }
    }

    fun resolveLegacyCompletion(completed: Boolean, message: String): AgentTaskOutcome {
        return resolve(completed, false, message)
    }
}

internal fun AgentTaskRunResult.resolvedOutcome(): AgentTaskOutcome {
    return AgentTaskOutcomeResolver.resolve(
        completed = completed,
        stoppedForConfirmation = stoppedForConfirmation,
        message = message,
    )
}
