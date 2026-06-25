package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionSessionStateTest {
    @Test
    fun trustedProbeCanCompleteExactPackageHandoff() {
        val target = "com.example.target"
        val evidence = ForegroundPackageEvidenceResolver.resolve(
            accessibilityPackage = "",
            shellProbe = ForegroundPackageProbeResult(
                packageName = target,
                source = ForegroundPackageEvidenceSource.ActivityManager,
                available = true,
            ),
        )
        val session = VisualExecutionSessionState().apply { beginLaunch(target) }
        val runtime = session.runtimeContext(snapshot(evidence.packageName))

        assertEquals(VisualSurfaceState.WorkSurface, runtime.surfaceState)
        assertTrue(runtime.guiPlusEligible)
    }

    @Test
    fun differentPackageCannotCompleteHandoff() {
        val session = VisualExecutionSessionState().apply { beginLaunch("com.example.target") }
        val runtime = session.runtimeContext(snapshot("com.example.other"))

        assertEquals(VisualSurfaceState.Launching, runtime.surfaceState)
        assertFalse(runtime.guiPlusEligible)
    }

    @Test
    fun unavailableProbeDoesNotInventPackage() {
        val evidence = ForegroundPackageEvidenceResolver.resolve(
            accessibilityPackage = "",
            shellProbe = ForegroundPackageProbeResult(available = false),
        )

        assertEquals("", evidence.packageName)
        assertFalse(evidence.available)
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
}
