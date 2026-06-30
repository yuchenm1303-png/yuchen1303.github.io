package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRouteRetryPolicyTest {
    @Test
    fun structuredFalseProviderTimeoutStops() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "provider_body_timeout",
        )
        val decision = VisualRouteRetryPolicy.decide(error, 0)
        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("non_retryable", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun structuredFalseLengthErrorStops() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "finish_reason=length content_chars=0",
        )
        val decision = VisualRouteRetryPolicy.decide(error, 0)
        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("non_retryable", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun structuredFalse503Stops() {
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "upstream unavailable",
        )
        val decision = VisualRouteRetryPolicy.decide(error, 0)
        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("non_retryable", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun structuredTrue503Retries() {
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "visual_provider_unavailable",
            retryable = true,
            backendMessage = "upstream temporarily unavailable",
        )
        val decision = VisualRouteRetryPolicy.decide(error, 0)
        assertTrue(decision is VisualRouteRetryDecision.Retry)
        decision as VisualRouteRetryDecision.Retry
        assertEquals(1, decision.attempt)
        assertEquals(350L, decision.backoffMs)
    }

    @Test
    fun initialAgentBrainTimeoutStopsWithoutVisualRetry() {
        val error = VisualAgentRequestException(
            httpStatus = null,
            code = "agent_brain_route_timeout",
            retryable = false,
            backendMessage = "initial text planning exceeded client boundary",
        )
        val decision = VisualRouteRetryPolicy.decide(error, 0)
        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("request_timeout", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun ordinaryBadRequestRemainsNonRetryable() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "invalid_visual_request",
            retryable = false,
            backendMessage = "expectedActionObservationId is missing",
        )
        val decision = VisualRouteRetryPolicy.decide(error, 0)
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
