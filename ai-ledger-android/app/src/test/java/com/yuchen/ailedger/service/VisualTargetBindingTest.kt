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
        assertEquals(
            listOf("com.example.target", "com.example.target"),
            binding.boundPackages,
        )
    }

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
