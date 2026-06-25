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
            else -> 900L
        }
        return VisualRouteRetryDecision.Retry(attempt = attempt, backoffMs = backoffMs)
    }

    internal fun isRetryableVisualRouteFailure(error: IOException): Boolean {
        val structured = error as? VisualAgentRequestException
        if (structured != null) {
            if (structured.retryable) return true
            if (structured.httpStatus?.let(TRANSIENT_HTTP_STATUSES::contains) == true) return true

            val code = structured.code.trim().lowercase()
            val detail = listOf(
                structured.backendMessage,
                structured.message.orEmpty(),
            ).joinToString(" ").lowercase()

            if (code in TRANSIENT_ROUTE_CODES && detail.hasTransientRouteFailureMarker()) {
                return true
            }
            if (code.startsWith("agent_brain_") && detail.hasTransientRouteFailureMarker()) {
                return true
            }
            if (code.startsWith("provider_") && code.hasTransientRouteFailureMarker()) {
                return true
            }
            return false
        }

        val detail = error.message.orEmpty().lowercase()
        return detail.contains("visual_agent_step timed out") ||
            detail.contains("route step timeout") ||
            detail.contains("provider_body_timeout") ||
            detail.contains("provider_stream_header_timeout") ||
            detail.contains("provider_stream_idle_timeout") ||
            detail.contains("provider_stream_absolute_timeout") ||
            (
                (detail.contains("agent_brain_route_failed") || detail.contains("deepseek 主脑路由失败")) &&
                    detail.hasTransientRouteFailureMarker()
                )
    }

    private fun String.hasTransientRouteFailureMarker(): Boolean {
        return TRANSIENT_FAILURE_MARKERS.any(::contains)
    }

    private val TRANSIENT_HTTP_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)

    private val TRANSIENT_ROUTE_CODES = setOf(
        "agent_brain_route_failed",
        "agent_route_failed",
        "route_planner_failed",
        "network_timeout",
        "provider_timeout",
    )

    private val TRANSIENT_FAILURE_MARKERS = listOf(
        "provider_body_timeout",
        "provider_stream_header_timeout",
        "provider_stream_idle_timeout",
        "provider_stream_absolute_timeout",
        "finish_reason=length",
        "content_chars=0",
        "choices=0",
        "empty",
        "timed out",
        "timeout",
        "temporar",
        "rate limit",
        "too many requests",
        "connection reset",
        "econnreset",
        "socket closed",
        "upstream",
        "暂不可用",
        "超时",
    )
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
