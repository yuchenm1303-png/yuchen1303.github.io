package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialAgentBrainRoutePayloadTest {
    @Test
    fun initialRouteIsTextOnlyAndCarriesOnlyGoalAndCanonicalApps() {
        val payload = buildInitialAgentBrainRoutePayload(
            goal = "打开同花顺并查看贵州茅台",
            appContext = listOf(
                VisualAgentAppContextItem("同花顺", "com.hexin.plat.android"),
                VisualAgentAppContextItem("微信", "com.tencent.mm"),
            ),
            deviceId = "device-test",
            agentSessionId = "session-test",
        )

        assertEquals("agent_brain_route", payload.getString("intent"))
        assertEquals("打开同花顺并查看贵州茅台", payload.getString("goal"))
        assertEquals(2, payload.getJSONArray("appContext").length())
        assertEquals(2, payload.getJSONObject("deviceContext").length())
        assertTrue(payload.length() <= 10)
        assertFalse(payload.has("screenshot"))
        assertFalse(payload.has("screenSnapshot"))
        assertFalse(payload.has("hasScreenshot"))
        assertFalse(payload.has("hasImage"))
        assertFalse(payload.has("observationId"))
        assertFalse(payload.has("recentActions"))
        assertFalse(payload.has("recentAgentActions"))
        assertFalse(payload.has("visualHistory"))
        assertFalse(payload.has("runtimeExecutionContext"))
        assertFalse(payload.has("executionFeedback"))
        assertFalse(payload.getJSONObject("deviceContext").has("installedApps"))
    }
}
