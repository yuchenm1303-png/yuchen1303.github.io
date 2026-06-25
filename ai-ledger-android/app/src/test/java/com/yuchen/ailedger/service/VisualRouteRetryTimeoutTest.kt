package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRouteRetryTimeoutTest {
    @Test
    fun networkTimeoutStopsWithoutAnotherLongRetry() {
        val error = VisualAgentRequestException(
            httpStatus = null,
            code = "network_timeout",
            retryable = true,
            backendMessage = "visual request timed out",
        )

        val decision = VisualRouteRetryPolicy.decide(error, completedRetries = 0)

        assertTrue(decision is VisualRouteRetryDecision.Stop)
        assertEquals("request_timeout", (decision as VisualRouteRetryDecision.Stop).reason)
    }

    @Test
    fun transientServerFailureStillRetries() {
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "route_unavailable",
            retryable = true,
            backendMessage = "temporary upstream failure",
        )

        val decision = VisualRouteRetryPolicy.decide(error, completedRetries = 0)

        assertTrue(decision is VisualRouteRetryDecision.Retry)
        assertEquals(1, (decision as VisualRouteRetryDecision.Retry).attempt)
    }
}
