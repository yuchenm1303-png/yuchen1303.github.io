package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualTargetBindingTest {
    @Test
    fun sessionUsesInjectedBindingInsteadOfHardCodedGlobalState() {
        val binding = RecordingVisualTargetBinding()
        val session = VisualExecutionSessionState(binding)

        session.beginLaunch(" com.example.target ")
        session.markTargetVerified("com.example.target")

        assertEquals(1, binding.resetCount)
        assertEquals(listOf("com.example.target"), binding.boundPackages)
    }

    @Test
    fun objectivePackageHandoffDoesNotRepeatSameBinding() {
        val binding = RecordingVisualTargetBinding()
        val session = VisualExecutionSessionState(binding)
        val snapshot = snapshot("com.example.target")

        session.beginLaunch("com.example.target")
        session.runtimeContext(snapshot)
        session.runtimeContext(snapshot)

        assertEquals(listOf("com.example.target"), binding.boundPackages)
    }

    @Test
    fun genuineTargetChangeStillUpdatesBinding() {
        val binding = RecordingVisualTargetBinding()
        val session = VisualExecutionSessionState(binding)

        session.beginLaunch("com.example.first")
        session.beginLaunch("com.example.second")
        session.markTargetVerified("com.example.second")

        assertEquals(
            listOf("com.example.first", "com.example.second"),
            binding.boundPackages,
        )
    }

    private fun snapshot(packageName: String) = AgentScreenSnapshot(
        currentApp = packageName,
        packageName = packageName,
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = emptyList(),
        allNodes = emptyList(),
        clickableNodes = emptyList(),
        inputNodes = emptyList(),
        scrollableNodes = emptyList(),
        visual = null,
    )

    private class RecordingVisualTargetBinding : VisualTargetBinding {
        var resetCount: Int = 0
        val boundPackages = mutableListOf<String>()

        override fun reset() {
            resetCount += 1
        }

        override fun bind(packageName: String) {
            boundPackages += packageName
        }
    }
}
