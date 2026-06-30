package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentPayloadTest {
    @Test
    fun visualPayloadUsesOneTaskMemorySourceWithoutCleanupPass() {
        val snapshot = AgentScreenSnapshot(
            currentApp = "com.example.app",
            packageName = "com.example.app",
            nodeCount = 0,
            capturedNodeCount = 0,
            texts = emptyList(),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
        )
        val payload = buildVisualAgentPayload(
            goal = "test",
            snapshot = snapshot,
            recentActions = emptyList(),
            taskMemory = VisualTaskMemory(originalGoal = "test"),
        )

        assertTrue(payload.has("taskMemory"))
        assertTrue(payload.has("executionFeedback"))
        assertFalse(payload.has("agentMemory"))
        assertFalse(payload.has("lastToolResponse"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.getJSONObject("taskMemory").has("reasoningContext"))
    }
}
