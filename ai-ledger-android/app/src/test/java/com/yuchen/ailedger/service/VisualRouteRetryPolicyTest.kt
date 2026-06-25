package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRouteRetryPolicyTest {
    @Test
    fun legacyHttp400ProviderTimeoutIsRetried() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "DeepSeek 主脑路由失败: provider_body_timeout",
        )

        val decision = VisualRouteRetryPolicy.decide(error, completedRetries = 0)

        assertTrue(decision is VisualRouteRetryDecision.Retry)
        decision as VisualRouteRetryDecision.Retry
        assertEquals(1, decision.attempt)
        assertEquals(350L, decision.backoffMs)
    }

    @Test
    fun emptyLengthTruncatedRouteOutputIsRetried() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "RetryCompact empty finish_reason=length choices=1 content_chars=0",
        )

        assertTrue(VisualRouteRetryPolicy.decide(error, 0) is VisualRouteRetryDecision.Retry)
    }

    @Test
    fun transientHttpStatusOverridesIncorrectBackendRetryFlag() {
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "upstream unavailable",
        )

        assertTrue(VisualRouteRetryPolicy.decide(error, 0) is VisualRouteRetryDecision.Retry)
    }

    @Test
    fun ordinaryBadRequestRemainsNonRetryable() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "invalid_visual_request",
            retryable = false,
            backendMessage = "expectedActionObservationId is missing",
        )

        val decision = VisualRouteRetryPolicy.decide(error, completedRetries = 0)

        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("non_retryable", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun retryBudgetStopsAfterTwoRecoveryAttempts() {
        val decision = VisualRouteRetryPolicy.decide(retryable = true, completedRetries = 2)

        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("retry_limit_reached", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun canonicalLabelIsNotRepeatedAsAnAlias() {
        assertEquals(emptyList<String>(), buildNeutralInstalledAppAliases("QQ"))
        assertEquals(emptyList<String>(), buildNeutralInstalledAppAliases("同花顺炒股票"))
    }

    @Test
    fun onlyDistinctNormalizedLabelIsUploadedAsAlias() {
        assertEquals(listOf("googleplay"), buildNeutralInstalledAppAliases("Google Play"))
        assertEquals(listOf("同花顺"), buildNeutralInstalledAppAliases("同 花 顺"))
    }
}
