package com.yuchen.ailedger.service

/**
 * Allows one whole-loop retry only before GUI interaction begins. This prevents a transient
 * AgentBrain entry failure from pausing the task while avoiding replay of taps, typing or swipes.
 */
internal object AgentEntryRetryPolicy {
    fun shouldRetry(result: AgentTaskRunResult): Boolean {
        if (result.completed || result.stoppedForConfirmation) return false
        val message = result.message.lowercase()
        val retryableRouteFailure = message.contains("visual_agent_step failed") && (
            message.contains("deepseek 主脑路由失败") ||
                message.contains("agent_brain_route_failed") ||
                message.contains("timed out") ||
                message.contains("timeout")
            )
        if (!retryableRouteFailure) return false
        return result.logs.all { log -> log.step.type in SAFE_ENTRY_RETRY_STEP_TYPES }
    }

    private val SAFE_ENTRY_RETRY_STEP_TYPES = setOf("open_app", "wait")
}
