package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAdaptiveReasoningIntegrationTest {
    @Test
    fun legacyDeepSignalsAreRemovedAtTransportBoundary() {
        val snapshot = snapshot()
        val legacyContext = VisualReasoningContext(
            depth = VisualReasoningDepth.Deep,
            triggers = listOf(VisualReasoningTrigger.RepeatedNoProgress),
            noProgressCount = 2,
            sameActionCount = 2,
            directExecutionAllowed = false,
        )
        val payload = buildVisualAgentPayload(
            goal = "打开目标页面",
            snapshot = snapshot,
            recentActions = listOf(
                legacyContext.toPromptLine(),
                "visual_replan_requested:reason=adaptive_reasoning_depth|depth=deep",
            ),
            visualHistory = listOf(history("one"), history("two"), history("three"), history("four")),
            runtimeContext = runtime(snapshot),
            taskMemory = memory(legacyContext),
        ).compactVisualAgentPayloadForTransport()

        assertFalse(payload.has("localVisualRetryRequested"))
        assertFalse(payload.has("guiPlusReplanRequested"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.has("agentMemory"))
        assertEquals(2, payload.getJSONArray("visualHistory").length())

        val taskMemory = payload.getJSONObject("taskMemory")
        assertFalse(taskMemory.has("reasoningContext"))
        assertFalse(taskMemory.has("reasoningDepth"))
        assertFalse(taskMemory.has("reasoningTriggers"))
        assertTrue(taskMemory.getBoolean("executionLedgerOnly"))
    }

    @Test
    fun localPolicyCannotEscalateCloudReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "打开目标页面"),
            recentActions = listOf(
                "tap_xy|目标:retry:result=失败",
                "tap_xy|目标:retry:result=失败",
            ),
        )

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
        assertFalse(context.deepThinkingRequested)
        assertEquals(null, VisualReasoningPolicy.deepReplanLine(context))
    }

    private fun memory(context: VisualReasoningContext): VisualTaskMemory = VisualTaskMemory(
        originalGoal = "打开目标页面",
        currentMilestoneId = "m1",
        progressStatus = "surface_verified",
        legacyMode = false,
        taskContract = VisualTaskContract(
            originalGoal = "打开目标页面",
            currentMilestoneId = "m1",
            milestones = listOf(VisualTaskMilestone(id = "m1")),
        ),
        reasoningContext = context,
    )

    private fun history(label: String): VisualAgentHistoryItem = VisualAgentHistoryItem(
        screenshot = snapshot().visual!!,
        assistantOutput = label,
        executionResult = label,
    )

    private fun runtime(snapshot: AgentScreenSnapshot): VisualAgentRuntimeContext = VisualAgentRuntimeContext(
        surfaceState = VisualSurfaceState.WorkSurface,
        selectedTargetPackage = snapshot.packageName,
        verifiedTargetPackage = snapshot.packageName,
        currentPackage = snapshot.packageName,
        observationId = VisualObservationProtocol.observationId(snapshot, 1L, 1L),
        routeEpoch = 1L,
        surfaceEpoch = 1L,
        guiPlusEligible = true,
    )

    private fun snapshot(): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = "com.example.app",
        packageName = "com.example.app",
        nodeCount = 1,
        capturedNodeCount = 1,
        texts = listOf("目标页面"),
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
