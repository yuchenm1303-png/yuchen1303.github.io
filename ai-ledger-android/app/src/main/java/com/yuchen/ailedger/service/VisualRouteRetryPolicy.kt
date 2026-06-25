package com.yuchen.ailedger.service

import java.io.IOException

sealed interface VisualRouteRetryDecision {
    data class Retry(
        val attempt: Int,
        val backoffMs: Long,
    ) : VisualRouteRetryDecision

    data class Stop(
        val reason: String,
    ) : VisualRouteRetryDecision
}

object VisualRouteRetryPolicy {
    const val maxRetries: Int = 2

    fun decide(error: IOException, completedRetries: Int): VisualRouteRetryDecision {
        return decide(
            retryable = isRetryableVisualRouteFailure(error),
            completedRetries = completedRetries,
        )
    }

    internal fun decide(retryable: Boolean, completedRetries: Int): VisualRouteRetryDecision {
        if (!retryable) {
            return VisualRouteRetryDecision.Stop("non_retryable")
        }
        if (completedRetries >= maxRetries) {
            return VisualRouteRetryDecision.Stop("retry_limit_reached")
        }
        val attempt = completedRetries + 1
        val backoffMs = when (attempt) {
            1 -> 350L
            else -> 850L
        }
        return VisualRouteRetryDecision.Retry(attempt = attempt, backoffMs = backoffMs)
    }

    private fun isRetryableVisualRouteFailure(error: IOException): Boolean {
        val structured = error as? VisualAgentRequestException
        if (structured != null) {
            if (structured.retryable) return true
            if (structured.code == "agent_brain_route_failed") {
                val detail = listOf(
                    structured.backendMessage,
                    structured.message.orEmpty(),
                ).joinToString(" ").lowercase()
                return detail.contains("finish_reason=length") ||
                    detail.contains("content_chars=0") ||
                    detail.contains("empty") ||
                    detail.contains("timed out") ||
                    detail.contains("timeout") ||
                    detail.contains("temporar")
            }
            return false
        }

        val text = error.message.orEmpty().lowercase()
        return text.contains("agent_brain_route_failed") ||
            text.contains("deepseek 主脑路由失败") ||
            text.contains("visual_agent_step timed out") ||
            text.contains("route step timeout")
    }
}

/**
 * Owns the consecutive route-failure budget for one visual session. Recovery observations never
 * reset the budget; only a successfully returned cloud plan does.
 */
class VisualRouteRetryState {
    var completedRetries: Int = 0
        private set

    fun onFailure(error: IOException): VisualRouteRetryDecision {
        return VisualRouteRetryPolicy.decide(error, completedRetries).also { decision ->
            if (decision is VisualRouteRetryDecision.Retry) {
                completedRetries = decision.attempt
            }
        }
    }

    fun onSuccess() {
        completedRetries = 0
    }
}
