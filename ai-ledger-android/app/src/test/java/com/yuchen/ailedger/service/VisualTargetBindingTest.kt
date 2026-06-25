package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualTargetBindingTest {
    @Test
    fun sessionUsesInjectedBindingInsteadOfHardCodedGlobalState() {
        val binding = RecordingVisualTargetBinding()
        val session = VisualExecutionSessionState(binding)

        session.beginLaunch(" com.example.target ")
        val accepted = session.markTargetVerified(
            expectedPackage = "com.example.target",
            verification = stableVerification("com.example.target"),
        )

        assertTrue(accepted)
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
        val accepted = session.markTargetVerified(
            expectedPackage = "com.example.second",
            verification = stableVerification("com.example.second"),
        )

        assertTrue(accepted)
        assertEquals(
            listOf("com.example.first", "com.example.second"),
            binding.boundPackages,
        )
    }

    private fun stableVerification(packageName: String): VisualTargetPackageVerification {
        val visual = AgentScreenVisual(
            available = true,
            mimeType = "image/jpeg",
            width = 720,
            height = 1280,
            displayWidth = 1080,
            displayHeight = 2400,
            base64Jpeg = "YWJj",
            source = "test",
            reason = "test",
        )
        val observationVisual = ScreenVisualObservation(
            available = true,
            mimeType = "image/jpeg",
            width = 720,
            height = 1280,
            displayWidth = 1080,
            displayHeight = 2400,
            base64Jpeg = "YWJj",
            source = "test",
            reason = "test",
        )
        return VisualTargetPackageVerification(
            verified = true,
            stableSamples = 2,
            lastSnapshot = snapshot(packageName, visual),
            lastObservation = ScreenObservation(
                enabled = true,
                serviceConnected = true,
                packageName = packageName,
                visual = observationVisual,
            ),
        )
    }

    private fun snapshot(
        packageName: String,
        visual: AgentScreenVisual? = null,
    ) = AgentScreenSnapshot(
        currentApp = packageName,
        packageName = packageName,
        nodeCount = 0,
        capturedNodeCount = 0,
        texts = emptyList(),
        allNodes = emptyList(),
        clickableNodes = emptyList(),
        inputNodes = emptyList(),
        scrollableNodes = emptyList(),
        visual = visual,
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
