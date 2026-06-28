package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationFailureTest {
    @Test
    fun overlayIsRestoredWhenVisualCaptureFails() = runBlocking {
        val overlay = RecordingOverlayController()
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource {
                error("capture failed")
            },
            foregroundPackageReader = ForegroundPackageReader {
                ForegroundPackageProbeResult(available = false)
            },
            overlayController = overlay,
            timing = VisualObservationTiming(
                fullVisualSettleMs = 0L,
                nonVisualSettleMs = 0L,
                packageProbeSettleMs = 0L,
                openAppInitialSettleMs = 0L,
                openAppVerifyPollMs = 0L,
                openAppVerifyTimeoutMs = 0L,
                requiredStableSamples = 2,
            ),
            elapsedRealtime = { 0L },
            sleeper = {},
        )

        var failed = false
        try {
            coordinator.captureTrustedObservation(
                forceVisual = true,
                expectedPackage = "com.example.target",
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(1, overlay.beginCount)
        assertEquals(1, overlay.endCount)
    }

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
