package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationVisualRetryTest {
    @Test
    fun unavailableVisualFrameDoesNotDiscardStablePackageEvidence() = runBlocking {
        var clock = 0L
        var visualAttempts = 0
        val target = "com.example.target"
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource { forceVisual ->
                val visual = if (forceVisual) {
                    visualAttempts += 1
                    if (visualAttempts == 1) {
                        ScreenVisualObservation(available = false, source = "test")
                    } else {
                        ScreenVisualObservation(
                            available = true,
                            width = 100,
                            height = 200,
                            displayWidth = 100,
                            displayHeight = 200,
                            base64Jpeg = "YWJj",
                            source = "test",
                        )
                    }
                } else {
                    null
                }
                ScreenObservation(
                    enabled = true,
                    serviceConnected = true,
                    packageName = target,
                    visual = visual,
                )
            },
            foregroundPackageReader = ForegroundPackageReader {
                ForegroundPackageProbeResult(available = false)
            },
            overlayController = object : VisualCaptureOverlayController {
                override fun beginCapture() = Unit
                override fun endCapture() = Unit
            },
            timing = VisualObservationTiming(
                fullVisualSettleMs = 0L,
                nonVisualSettleMs = 0L,
                packageProbeSettleMs = 0L,
                openAppInitialSettleMs = 0L,
                openAppVerifyPollMs = 1L,
                openAppVerifyTimeoutMs = 3L,
                openAppEvidenceGraceMs = 2L,
                openAppTransientGraceMs = 0L,
                openAppMaxVerifyTimeoutMs = 4L,
                requiredStableSamples = 2,
                trustedPackageTtlMs = 100L,
            ),
            elapsedRealtime = { clock },
            sleeper = { clock += it },
        )

        val verification = coordinator.awaitStableTargetPackage(target) { false }

        assertTrue(verification.verified)
        assertEquals(2, verification.stableSamples)
        assertEquals(2, visualAttempts)
        assertEquals(
            VisualTargetPackageVerificationReason.StableTargetWithVisualFrame,
            verification.reason,
        )
    }
}
