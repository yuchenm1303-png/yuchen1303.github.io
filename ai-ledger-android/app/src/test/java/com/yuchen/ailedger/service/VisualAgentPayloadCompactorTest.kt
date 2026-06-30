package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentPayloadCompactorTest {
    @Test
    fun compactorKeepsOneCanonicalCopyOfVisualState() {
        val snapshot = testSnapshot()
        val runtime = testRuntime(snapshot)
        val payload = buildVisualAgentPayload(
            goal = "查看订单",
            snapshot = snapshot,
            recentActions = listOf("tap_xy:ok:result=已点击"),
            deviceId = "device-test",
            agentSessionId = "session-test",
            runtimeContext = runtime,
        )
        val originalSize = payload.toString().length

        payload.compactVisualAgentPayloadForTransport()

        assertEquals("android_visual_agent_v16_text_bootstrap_gui_loop", payload.getString("agentSessionProtocol"))
        assertEquals("gui_plus_dialogue_v2_bound_turns", payload.getString("interactionProtocol"))
        assertTrue(payload.has("recentAgentActions"))
        assertTrue(payload.has("lastToolResponse"))
        assertTrue(payload.has("executionFeedback"))
        assertTrue(payload.has("agentSessionId"))
        assertTrue(payload.has("deviceId"))
        assertTrue(payload.has("goal"))
        assertTrue(payload.has("hasScreenshot"))
        assertTrue(payload.has("runtimeExecutionContext"))
        assertTrue(payload.has("expectedActionObservationId"))
        assertTrue(payload.has("observationId"))
        assertTrue(payload.has("agentMemory"))

        assertFalse(payload.has("agentGoal"))
        assertFalse(payload.has("recentActions"))
        assertFalse(payload.has("toolResponse"))
        assertFalse(payload.has("sessionId"))
        assertFalse(payload.has("clientId"))
        assertFalse(payload.has("message"))
        assertFalse(payload.has("hasImage"))
        assertFalse(payload.has("hasImages"))
        assertFalse(payload.has("taskContract"))

        val memory = payload.getJSONObject("agentMemory")
        assertEquals("android_visual_agent_loop_memory_v16_text_bootstrap_gui_loop", memory.getString("schema"))
        assertEquals("gui_plus_dialogue_v2_bound_turns", memory.getString("interactionProtocol"))
        assertFalse(memory.has("recentActions"))
        assertTrue(memory.has("taskMemory"))
        assertTrue(memory.has("loopSignals"))
        assertFalse(memory.has("executionFeedback"))
        assertFalse(memory.has("lastToolResponse"))
        assertFalse(memory.has("taskContract"))
        assertFalse(memory.has("runtimeExecutionContext"))
        assertFalse(memory.has("surfaceContext"))
        assertFalse(memory.has("deviceProfile"))
        assertFalse(memory.has("appSelectionProtocol"))
        assertFalse(memory.getJSONObject("loopSignals").has("postActionFeedback"))
        assertFalse(memory.getJSONObject("loopSignals").has("lastToolResponse"))
        assertTrue(payload.toString().length < originalSize)
    }

    @Test
    fun handoffTelemetryCannotTriggerDeepGuiPlusReasoning() {
        val snapshot = testSnapshot()
        val payload = buildVisualAgentPayload(
            goal = "查看订单",
            snapshot = snapshot,
            recentActions = emptyList(),
            deviceId = "device-test",
            agentSessionId = "session-test",
            runtimeContext = testRuntime(snapshot),
        )
        payload.put("recentAgentActions", JSONArray(listOf(
            "open_app|com.example.shop|商店:ok:result=已打开",
            "open_app|com.example.shop|商店:ok:result=already foreground",
            "open_app_package_verified:package=com.example.shop|stableSamples=2|visualFrame=true",
            "visual_reasoning_context:v3|depth=deep|triggers=repeated-action|sameActionCount=2",
            "visual_replan_requested:reason=adaptive_reasoning_depth|replanRequired=true",
        )))
        payload.getJSONObject("executionFeedback").apply {
            put("lastResultOk", false)
            put("lastVerification", "visual_local_retry")
            put("sameActionCount", 2)
            put("localVisualRetryCount", 1)
            put("visualReplanRequested", true)
            put("guiPlusReplanRequested", true)
        }
        payload.getJSONObject("agentMemory").getJSONObject("taskMemory").put(
            "reasoningContext",
            JSONObject().put("depth", "deep").put("sameActionCount", 2),
        )

        payload.compactVisualAgentPayloadForTransport()

        val actions = payload.getJSONArray("recentAgentActions").toString()
        assertFalse(actions.contains("visual_reasoning_context"))
        assertFalse(actions.contains("visual_replan_requested"))
        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals("surface_verified", feedback.getString("lastVerification"))
        assertEquals(0, feedback.getInt("sameActionCount"))
        assertFalse(feedback.getBoolean("visualReplanRequested"))
        assertFalse(feedback.getBoolean("guiPlusReplanRequested"))
        assertFalse(
            payload.getJSONObject("agentMemory").getJSONObject("taskMemory").has("reasoningContext"),
        )
    }

    private fun testRuntime(snapshot: AgentScreenSnapshot) = VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.WorkSurface,
        selectedTargetPackage = snapshot.packageName,
        verifiedTargetPackage = snapshot.packageName,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 2L, 3L),
        routeEpoch = 2L,
        surfaceEpoch = 3L,
        guiPlusEligible = true,
    )

    private fun testSnapshot() = AgentScreenSnapshot(
        currentApp = "com.example.shop",
        packageName = "com.example.shop",
        nodeCount = 2,
        capturedNodeCount = 2,
        texts = listOf("订单", "全部订单"),
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
