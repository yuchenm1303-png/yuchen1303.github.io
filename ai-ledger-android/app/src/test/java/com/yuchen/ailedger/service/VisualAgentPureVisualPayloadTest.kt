package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentPureVisualPayloadTest {
    @Test
    fun dynamicFrameTelemetryCannotRequestRetryOrRouteRefresh() {
        val snapshot = snapshot()
        val tracker = VisualSemanticProgressTracker(originalGoal = "查看行情")
        tracker.onVerifiedSurface(snapshot)
        val memory = tracker.memorySnapshot(snapshot)
        val payload = buildVisualAgentPayload(
            goal = "查看行情",
            snapshot = snapshot,
            recentActions = listOf(
                "wait|观察日K:ok:target=观察日K:result=等待 700ms",
                "visual_execution_observed:action=wait|观察日K|executionObserved=true|frameChanged=true|packageChanged=false|replanRequired=false",
            ),
            runtimeContext = runtime(snapshot),
            taskMemory = memory,
        )

        assertFalse(payload.getBoolean("localVisualRetryRequested"))
        assertFalse(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        val feedback = payload.getJSONObject("executionFeedback")
        assertEquals(0, feedback.getInt("screenChangedCount"))
        assertEquals(0, feedback.getInt("noProgressCount"))
        assertEquals(0, feedback.getJSONArray("blockedActionSignatures").length())
        val taskMemory = payload.getJSONObject("agentMemory").getJSONObject("taskMemory")
        assertEquals("visual_task_memory_v5_transactional_visual_authority", taskMemory.getString("schema"))
        assertFalse(taskMemory.getBoolean("localProgressClassification"))
        assertTrue(taskMemory.getBoolean("transactionalCompletion"))
        assertFalse(taskMemory.getBoolean("provisionalStateCommitted"))
        assertEquals(0, taskMemory.getJSONArray("failedHypotheses").length())
        assertEquals(0, taskMemory.getJSONArray("blockedActions").length())
    }

    @Test
    fun explicitMechanicalDeepSignalRequestsGuiPlusReplanWithoutRouteRefresh() {
        val snapshot = snapshot()
        val context = VisualReasoningContext(
            depth = VisualReasoningDepth.Deep,
            triggers = listOf(VisualReasoningTrigger.RepeatedAction),
            sameActionCount = 2,
            explorationPressureLevel = "high",
            historyItems = 4,
            selfCheckPasses = 2,
            candidateHypothesisLimit = 3,
            freshObservationRequired = true,
            directExecutionAllowed = false,
        )
        val payload = buildVisualAgentPayload(
            goal = "查看行情",
            snapshot = snapshot,
            recentActions = listOf(
                context.toPromptLine(),
                VisualReasoningPolicy.deepReplanLine(context)!!,
            ),
            runtimeContext = runtime(snapshot),
            taskMemory = VisualTaskMemory(
                originalGoal = "查看行情",
                currentMilestoneId = "chart",
                progressStatus = "execution_observed",
                reasoningContext = context,
            ),
        )

        assertTrue(payload.getBoolean("localVisualRetryRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
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
