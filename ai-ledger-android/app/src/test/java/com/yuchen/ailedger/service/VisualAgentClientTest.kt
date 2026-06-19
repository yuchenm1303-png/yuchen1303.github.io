package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentClientTest {
    @Test
    fun payloadUsesUnifiedBackendContractWithoutDuplicatingVisualHistoryImages() {
        val snapshot = testSnapshot()
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = snapshot,
            recentActions = listOf("open_app:ok"),
            visualHistory = listOf(
                VisualAgentHistoryItem(
                    screenshot = snapshot.visual!!,
                    assistantOutput = "opened",
                    executionResult = "ok",
                ),
            ),
            appContext = listOf(
                VisualAgentAppContextItem(
                    label = "QQ",
                    packageName = "com.tencent.mobileqq",
                    aliases = listOf("腾讯QQ"),
                    capabilities = listOf("social_chat"),
                ),
            ),
            deviceId = "android-install-test",
            agentSessionId = "visual-session-test",
            executionMode = AgentExecutionMode.ExplicitAgent,
        )

        assertEquals("visual_agent_step", payload.getString("action"))
        assertEquals("explicit_agent", payload.getString("executionMode"))
        assertEquals("visual-session-test", payload.getString("agentSessionId"))
        assertEquals("android-install-test", payload.getString("deviceId"))
        assertTrue(payload.has("screenSnapshot"))
        assertTrue(payload.has("recentAgentActions"))
        assertTrue(payload.has("executionFeedback"))
        assertTrue(payload.has("lastToolResponse"))
        assertTrue(payload.has("toolResponse"))
        assertTrue(payload.has("screenshot"))

        val snapshotJson = payload.getJSONObject("screenSnapshot")
        assertFalse(snapshotJson.getJSONObject("visual").has("base64Jpeg"))

        val historyItem = payload.getJSONArray("visualHistory").getJSONObject(0)
        assertFalse(historyItem.has("screenshot"))

        val app = payload.getJSONArray("appContext").getJSONObject(0)
        assertEquals("QQ", app.getString("label"))
        assertEquals("com.tencent.mobileqq", app.getString("packageName"))
        assertFalse(app.has("aliases"))
        assertFalse(app.has("capabilities"))

        val supported = payload.getJSONArray("supportedAgentSteps")
        val supportedTypes = (0 until supported.length()).map { supported.getString(it) }.toSet()
        assertEquals(VisualAgentProtocol.supportedStepTypes, supportedTypes)
        assertFalse("tap_node" in supportedTypes)
        assertFalse("scroll" in supportedTypes)

        val memory = payload.getJSONObject("agentMemory")
        assertEquals("android_visual_agent_loop_memory_v7_tool_response", memory.getString("schema"))
        assertTrue(memory.has("lastToolResponse"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
    }

    @Test
    fun payloadRequestsReplanAfterFailureOrNoProgress() {
        val snapshot = testSnapshot()
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = snapshot,
            recentActions = listOf(
                "tap_xy|0.5|0.5:failed:点击失败",
                "visual_no_progress:tap_xy|0.5|0.5:count=1:screen=unchanged",
            ),
            agentSessionId = "visual-session-feedback",
        )

        assertTrue(payload.getBoolean("routeRefreshRequested"))
        assertTrue(payload.getBoolean("invalidateCachedAgentBrainRoute"))

        val feedback = payload.getJSONObject("executionFeedback")
        assertFalse(feedback.getBoolean("lastResultOk"))
        assertEquals("visual_no_screen_change", feedback.getString("lastVerification"))
        assertEquals(1, feedback.getInt("noProgressCount"))
        assertEquals("tap_xy|0.5|0.5", feedback.getString("lastActionSignature"))
        assertTrue(feedback.getJSONArray("blockedActionSignatures").length() > 0)

        val toolResponse = payload.getJSONObject("lastToolResponse")
        assertEquals("tool_response", toolResponse.getString("type"))
        assertEquals("mobile_use", toolResponse.getString("toolName"))
        assertFalse(toolResponse.getBoolean("success"))

        val memory = payload.getJSONObject("agentMemory")
        val signals = memory.getJSONObject("loopSignals")
        assertEquals(1, signals.getInt("noProgressCount"))
        assertTrue(signals.getBoolean("routeRefreshRequested"))
        assertTrue(memory.getJSONArray("verificationEvents").length() > 0)
        assertTrue(memory.getJSONArray("blockedActionSignatures").length() > 0)
    }

    @Test
    fun finishCandidateRequestsFreshScreenVerification() {
        val payload = buildVisualAgentPayload(
            goal = "进入个人主页",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "finish_verification_pending:package=com.tencent.mobileqq:fingerprint=abc:reason=已进入个人主页",
            ),
            agentSessionId = "visual-session-finish",
        )

        assertTrue(payload.getBoolean("finishVerificationRequested"))
        assertTrue(payload.getBoolean("routeRefreshRequested"))
        assertTrue(payload.getBoolean("invalidateCachedAgentBrainRoute"))

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals("finish_verification_pending", feedback.getString("lastVerification"))
        assertTrue(feedback.getBoolean("finishVerificationRequested"))

        val toolResponse = payload.getJSONObject("lastToolResponse")
        assertEquals("finish", toolResponse.getString("actionSignature"))
        assertTrue(toolResponse.getBoolean("finishVerificationRequested"))
    }

    @Test
    fun successfulScreenChangeClearsStaleNoProgressBlock() {
        val payload = buildVisualAgentPayload(
            goal = "打开 QQ 个人主页",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "tap_xy|0.5|0.5:ok:result=已点击",
                "visual_no_progress:tap_xy|0.5|0.5:count=1:screen=unchanged",
                "back:ok:result=已返回",
                "visual_screen_changed:back:screen=changed",
            ),
            agentSessionId = "visual-session-recovered",
        )

        val feedback = payload.getJSONObject("executionFeedback")
        assertTrue(feedback.getBoolean("lastResultOk"))
        assertEquals("visual_screen_changed", feedback.getString("lastVerification"))
        assertEquals(0, feedback.getInt("noProgressCount"))
        assertEquals(0, feedback.getJSONArray("blockedActionSignatures").length())
        assertFalse(payload.getBoolean("routeRefreshRequested"))
    }

    @Test
    fun repeatedSameActionIsCountedForPlannerGuard() {
        val payload = buildVisualAgentPayload(
            goal = "进入个人主页",
            snapshot = testSnapshot(),
            recentActions = listOf(
                "tap_xy|0.5|0.5:ok:result=已点击",
                "tap_xy|0.5|0.5:ok:result=已点击",
            ),
            agentSessionId = "visual-session-repeat",
        )

        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals(2, feedback.getInt("sameActionCount"))
        assertEquals("tap_xy|0.5|0.5", feedback.getString("lastActionSignature"))
        assertTrue(feedback.getBoolean("lastResultOk"))
    }

    private fun testSnapshot(): AgentScreenSnapshot {
        return AgentScreenSnapshot(
            currentApp = "com.tencent.mobileqq",
            packageName = "com.tencent.mobileqq",
            nodeCount = 2,
            capturedNodeCount = 2,
            texts = listOf("QQ", "消息"),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
            visual = AgentScreenVisual(
                available = true,
                mimeType = "image/jpeg",
                width = 720,
                height = 1280,
                displayWidth = 1080,
                displayHeight = 2400,
                base64Jpeg = "YWJj",
                source = "test",
                reason = "test",
            ),
        )
    }
}
