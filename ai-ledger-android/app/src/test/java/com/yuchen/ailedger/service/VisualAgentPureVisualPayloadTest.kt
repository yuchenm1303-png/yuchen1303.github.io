package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentPureVisualPayloadTest {
    @Test
    fun transportKeepsOnlyObjectiveVisualState() {
        val snapshot = snapshot()
        val tracker = VisualSemanticProgressTracker(originalGoal = "查看行情")
        tracker.onVerifiedSurface(snapshot)
        val payload = buildVisualAgentPayload(
            goal = "查看行情",
            snapshot = snapshot,
            recentActions = listOf(
                "wait|观察日K:ok:target=观察日K:result=等待 700ms",
                "visual_execution_observed:action=wait|观察日K|executionObserved=true|frameChanged=true|packageChanged=false|replanRequired=false",
            ),
            runtimeContext = runtime(snapshot),
            taskMemory = tracker.memorySnapshot(snapshot),
        ).compactVisualAgentPayloadForTransport()

        assertFalse(payload.has("localVisualRetryRequested"))
        assertFalse(payload.has("guiPlusReplanRequested"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.has("agentMemory"))
        assertFalse(payload.has("lastToolResponse"))
        assertTrue(payload.has("executionFeedback"))

        val memory = payload.getJSONObject("taskMemory")
        assertFalse(memory.has("reasoningContext"))
        assertFalse(memory.has("reasoningDepth"))
        assertFalse(memory.has("reasoningTriggers"))
        assertFalse(memory.has("failedHypotheses"))
        assertFalse(memory.has("blockedActions"))
        assertTrue(memory.getBoolean("executionLedgerOnly"))
    }

    @Test
    fun legacyDeepSignalCannotRequestLocalOrGuiReplan() {
        val snapshot = snapshot()
        val payload = buildVisualAgentPayload(
            goal = "查看行情",
            snapshot = snapshot,
            recentActions = listOf(
                "visual_reasoning_context:v4|depth=deep|trigger=repeated_action",
                "visual_replan_requested:reason=adaptive_reasoning_depth|depth=deep",
            ),
            runtimeContext = runtime(snapshot),
            taskMemory = VisualTaskMemory(
                originalGoal = "查看行情",
                currentMilestoneId = "chart",
                progressStatus = "execution_observed",
                reasoningContext = VisualReasoningContext(depth = VisualReasoningDepth.Deep),
            ),
        ).compactVisualAgentPayloadForTransport()

        assertFalse(payload.has("localVisualRetryRequested"))
        assertFalse(payload.has("guiPlusReplanRequested"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.getJSONObject("taskMemory").has("reasoningDepth"))
    }

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
        currentApp = "com.hexin.plat.android",
        packageName = "com.hexin.plat.android",
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = listOf("贵州茅台", "日K", "09:31"),
        allNodes = emptyList(),
        clickableNodes = emptyList(),
        inputNodes = emptyList(),
        scrollableNodes = emptyList(),
        visual = AgentScreenVisual(
            available = true,
            mimeType = "image/jpeg",
            width = 720,
            height = 1720,
            displayWidth = 1080,
            displayHeight = 2580,
            base64Jpeg = "YWJj",
            source = "test",
            reason = "test",
        ),
    )
}
