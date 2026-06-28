package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VisualObservationCacheEvidenceTest {
    @Test
    fun cachedTargetAloneStaysPending() = runBlocking {
        var clock = 0L
        var captureIndex = 0
        val target = "com.example.target"
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource {
                captureIndex += 1
                ScreenObservation(
                    enabled = true,
                    serviceConnected = true,
                    packageName = if (captureIndex == 1) target else "com.android.permissioncontroller",
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
                openAppEvidenceGraceMs = 0L,
                openAppTransientGraceMs = 0L,
                openAppMaxVerifyTimeoutMs = 3L,
                requiredStableSamples = 2,
                trustedPackageTtlMs = 100L,
            ),
            elapsedRealtime = { clock },
            sleeper = { clock += it },
        )

        coordinator.captureTrustedObservation(false, target)
        val verification = coordinator.awaitStableTargetPackage(target) { false }

        assertFalse(verification.verified)
        assertEquals(0, verification.stableSamples)
        assertEquals(VisualTargetPackageVerificationReason.TransientSurface, verification.reason)
    }
}
