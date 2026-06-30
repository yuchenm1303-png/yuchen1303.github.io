package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAdaptiveReasoningIntegrationTest {
    @Test
    fun visualPayloadHasNoLegacyLocalControlFields() {
        val snapshot = snapshot()
        val payload = buildVisualAgentPayload(
            goal = "open target",
            snapshot = snapshot,
            recentActions = listOf(
                "visual_reasoning_context:off|enabled=false",
                "visual_replan_requested:reason=adaptive_reasoning_depth|depth=deep",
            ),
            visualHistory = listOf(history("one"), history("two"), history("three"), history("four")),
            runtimeContext = runtime(snapshot),
            taskMemory = VisualTaskMemory(
                originalGoal = "open target",
                currentMilestoneId = "m1",
                reasoningContext = VisualReasoningContext(depth = VisualReasoningDepth.Deep),
            ),
        )

        assertFalse(payload.has("localVisualRetryRequested"))
        assertFalse(payload.has("guiPlusReplanRequested"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.has("agentMemory"))
        assertFalse(payload.has("lastToolResponse"))
        assertEquals(4, payload.getJSONArray("visualHistory").length())
        val memory = payload.getJSONObject("taskMemory")
        assertFalse(memory.has("reasoningContext"))
        assertFalse(memory.has("reasoningDepth"))
        assertFalse(memory.has("reasoningTriggers"))
        assertTrue(memory.getBoolean("executionLedgerOnly"))
    }

    @Test
    fun localPolicyCannotEscalateCloudReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "open target"),
            recentActions = listOf("tap:retry", "tap:retry"),
        )
        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
        assertFalse(context.deepThinkingRequested)
    }

    private fun history(label: String) = VisualAgentHistoryItem(
        screenshot = snapshot().visual!!,
        assistantOutput = label,
        executionResult = label,
    )

    private fun runtime(snapshot: AgentScreenSnapshot) = VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.WorkSurface,
        selectedTargetPackage = snapshot.packageName,
        verifiedTargetPackage = snapshot.packageName,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 1L, 1L),
        routeEpoch = 1L,
        surfaceEpoch = 1L,
        guiPlusEligible = true,
    )

    private fun snapshot() = AgentScreenSnapshot(
        currentApp = "com.example.app",
        packageName = "com.example.app",
        nodeCount = 1,
        capturedNodeCount = 1,
        texts = listOf("target"),
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
