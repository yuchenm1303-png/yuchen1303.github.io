package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAdaptiveReasoningIntegrationTest {
    @Test
    fun deepReasoningUsesExistingGuiPlusReplanAndRecoveryHistory() {
        val snapshot = snapshot()
        val context = VisualReasoningContext(
            depth = VisualReasoningDepth.Deep,
            triggers = listOf(VisualReasoningTrigger.RepeatedNoProgress),
            noProgressCount = 2,
            sameActionCount = 2,
            explorationPressureLevel = "high",
            historyItems = 4,
            selfCheckPasses = 2,
            candidateHypothesisLimit = 3,
            freshObservationRequired = true,
            completionEvidenceStrict = true,
            directExecutionAllowed = false,
        )
        val payload = buildVisualAgentPayload(
            goal = "打开目标页面",
            snapshot = snapshot,
            recentActions = listOf(
                context.toPromptLine(),
                VisualReasoningPolicy.deepReplanLine(context)!!,
            ),
            visualHistory = listOf(history("one"), history("two"), history("three"), history("four")),
            runtimeContext = runtime(snapshot),
            taskMemory = memory(context),
        )

        assertTrue(payload.getBoolean("localVisualRetryRequested"))
        assertTrue(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals(4, payload.getJSONArray("visualHistory").length())
        assertEquals(
            "deep",
            payload.getJSONObject("agentMemory")
                .getJSONObject("taskMemory")
                .getString("reasoningDepth"),
        )
    }

    @Test
    fun normalReasoningAddsSelfCheckContextWithoutForcingDeepReplan() {
        val snapshot = snapshot()
        val context = VisualReasoningContext(
            depth = VisualReasoningDepth.Normal,
            triggers = listOf(VisualReasoningTrigger.FirstNoProgress),
            noProgressCount = 1,
            explorationPressureLevel = "medium",
            historyItems = 2,
            selfCheckPasses = 1,
            candidateHypothesisLimit = 2,
        )
        val payload = buildVisualAgentPayload(
            goal = "打开目标页面",
            snapshot = snapshot,
            recentActions = listOf(context.toPromptLine()),
            visualHistory = listOf(history("one"), history("two"), history("three"), history("four")),
            runtimeContext = runtime(snapshot),
            taskMemory = memory(context),
        )

        assertFalse(payload.getBoolean("localVisualRetryRequested"))
        assertFalse(payload.getBoolean("guiPlusReplanRequested"))
        assertFalse(payload.getBoolean("routeRefreshRequested"))
        assertEquals(2, payload.getJSONArray("visualHistory").length())
        assertEquals(
            "normal",
            payload.getJSONObject("agentMemory")
                .getJSONObject("taskMemory")
                .getString("reasoningDepth"),
        )
    }

    private fun memory(context: VisualReasoningContext): VisualTaskMemory = VisualTaskMemory(
        originalGoal = "打开目标页面",
        currentMilestoneId = "m1",
        remainingExplorationBudget = 2,
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
