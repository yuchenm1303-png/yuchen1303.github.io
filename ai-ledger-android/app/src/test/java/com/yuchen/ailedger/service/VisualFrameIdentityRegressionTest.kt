package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VisualFrameIdentityRegressionTest {
    @Test
    fun visuallyDifferentFramesCannotShareObjectiveFrameIdWhenNodesAreEmpty() {
        val tracker = VisualSemanticProgressTracker("frame identity test")
        val first = snapshot("AQID")
        val second = snapshot("BAUG")

        tracker.onVerifiedSurface(first)
        val firstId = tracker.memorySnapshot().currentPage?.id
        tracker.onVerifiedSurface(second)
        val secondId = tracker.memorySnapshot().currentPage?.id

        assertNotEquals(firstId, secondId)
    }

    @Test
    fun identicalVisualAndStructureKeepStableObjectiveFrameId() {
        val tracker = VisualSemanticProgressTracker("frame identity test")
        val first = snapshot("AQID")
        val second = snapshot("AQID")

        tracker.onVerifiedSurface(first)
        val firstId = tracker.memorySnapshot().currentPage?.id
        tracker.onVerifiedSurface(second)
        val secondId = tracker.memorySnapshot().currentPage?.id

        assertEquals(firstId, secondId)
    }

    private fun snapshot(base64Jpeg: String): AgentScreenSnapshot = AgentScreenSnapshot(
        currentApp = "com.example.target",
        packageName = "com.example.target",
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = emptyList(),
        allNodes = emptyList(),
        clickableNodes = emptyList(),
        inputNodes = emptyList(),
        scrollableNodes = emptyList(),
        visual = AgentScreenVisual(
            available = true,
            mimeType = "image/jpeg",
            width = 100,
            height = 200,
            displayWidth = 100,
            displayHeight = 200,
            base64Jpeg = base64Jpeg,
            source = "unit_test",
            reason = "",
        ),
    )
}
