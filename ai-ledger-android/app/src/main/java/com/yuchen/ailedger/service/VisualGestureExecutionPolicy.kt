package com.yuchen.ailedger.service

internal enum class VisualGestureDispatchOutcome {
    Completed,
    Cancelled,
    Rejected,
    TimedOut,
}

internal object VisualGestureExecutionPolicy {
    fun tapResult(
        outcome: VisualGestureDispatchOutcome,
        successMessage: String,
    ): AgentExecutionResult = when (outcome) {
        VisualGestureDispatchOutcome.Completed -> AgentExecutionResult(
            ok = true,
            message = successMessage,
            shouldContinue = true,
        )
        VisualGestureDispatchOutcome.Cancelled -> AgentExecutionResult(
            ok = false,
            message = "点击手势被 Android 取消，已停止把本次点击当作成功",
            shouldContinue = false,
        )
        VisualGestureDispatchOutcome.Rejected -> AgentExecutionResult(
            ok = false,
            message = "点击手势提交失败",
            shouldContinue = false,
        )
        VisualGestureDispatchOutcome.TimedOut -> AgentExecutionResult(
            ok = false,
            message = "点击手势完成回执超时，已转入重新观察",
            shouldContinue = false,
        )
    }

    fun swipeResult(
        outcome: VisualGestureDispatchOutcome,
        direction: String,
    ): AgentExecutionResult = when (outcome) {
        VisualGestureDispatchOutcome.Completed -> AgentExecutionResult(
            ok = true,
            message = "已滑动：$direction",
            shouldContinue = true,
        )
        VisualGestureDispatchOutcome.Cancelled -> AgentExecutionResult(
            ok = false,
            message = "滑动手势被 Android 取消：$direction",
            shouldContinue = false,
        )
        VisualGestureDispatchOutcome.Rejected -> AgentExecutionResult(
            ok = false,
            message = "滑动手势提交失败：$direction",
            shouldContinue = false,
        )
        VisualGestureDispatchOutcome.TimedOut -> AgentExecutionResult(
            ok = false,
            message = "滑动手势完成回执超时：$direction",
            shouldContinue = false,
        )
    }
}
