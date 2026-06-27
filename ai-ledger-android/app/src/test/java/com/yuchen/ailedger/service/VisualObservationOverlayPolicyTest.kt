package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationOverlayPolicyTest {
    @Test
    fun trustedFallbackDoesNotHideOverlay() = runBlocking {
        val overlay = RecordingOverlayController()
        var probeCount = 0
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource {
                ScreenObservation(enabled = true, serviceConnected = true, packageName = "")
            },
            foregroundPackageReader = ForegroundPackageReader {
                probeCount += 1
                ForegroundPackageProbeResult(
                    packageName = "com.example.target",
                    source = ForegroundPackageEvidenceSource.ActivityManager,
                    available = true,
                )
            },
            overlayController = overlay,
            timing = zeroTiming(),
            elapsedRealtime = { 0L },
            sleeper = {},
        )

        val observation = coordinator.captureTrustedObservation(
            forceVisual = false,
            expectedPackage = "com.example.target",
        )

        assertEquals("com.example.target", observation.packageName)
        assertTrue(observation.windowTitle.contains("activity_manager"))
        assertEquals(1, probeCount)
        assertEquals(0, overlay.beginCount)
        assertEquals(0, overlay.endCount)
    }

    @Test
    fun usablePackageSkipsProbeAndOverlayHide() = runBlocking {
        val overlay = RecordingOverlayController()
        var probeCount = 0
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource {
                ScreenObservation(
                    enabled = true,
                    serviceConnected = true,
                    packageName = "com.example.target",
                )
            },
            foregroundPackageReader = ForegroundPackageReader {
                probeCount += 1
                ForegroundPackageProbeResult(available = false)
            },
            overlayController = overlay,
            timing = zeroTiming(),
            elapsedRealtime = { 0L },
            sleeper = {},
        )

        val observation = coordinator.captureTrustedObservation(
            forceVisual = false,
            expectedPackage = "com.example.target",
        )

        assertEquals("com.example.target", observation.packageName)
        assertEquals(0, probeCount)
        assertEquals(0, overlay.beginCount)
        assertEquals(0, overlay.endCount)
    }

    private fun zeroTiming() = VisualObservationTiming(
        fullVisualSettleMs = 0L,
        nonVisualSettleMs = 0L,
        packageProbeSettleMs = 0L,
        openAppInitialSettleMs = 0L,
        openAppVerifyPollMs = 0L,
        openAppVerifyTimeoutMs = 0L,
        requiredStableSamples = 2,
    )

    private class RecordingOverlayController : VisualCaptureOverlayController {
        var beginCount = 0
        var endCount = 0

        override fun beginCapture() {
            beginCount += 1
        }

        override fun endCapture() {
            endCount += 1
        }
    }
}
