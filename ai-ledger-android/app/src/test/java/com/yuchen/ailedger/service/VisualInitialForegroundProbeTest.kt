package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualInitialForegroundProbeTest {
    @Test
    fun planningObservationResolvesForegroundPackageBeforeTargetIsKnown() = runBlocking {
        var probeCount = 0
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource {
                ScreenObservation(
                    enabled = true,
                    serviceConnected = true,
                    packageName = "",
                )
            },
            foregroundPackageReader = ForegroundPackageReader {
                probeCount += 1
                ForegroundPackageProbeResult(
                    packageName = "com.example.foreground",
                    source = ForegroundPackageEvidenceSource.ActivityManager,
                    available = true,
                )
            },
            overlayController = NoOpOverlayController,
            timing = VisualObservationTiming(
                fullVisualSettleMs = 0L,
                nonVisualSettleMs = 0L,
                packageProbeSettleMs = 0L,
                openAppInitialSettleMs = 0L,
                openAppVerifyPollMs = 0L,
                openAppVerifyTimeoutMs = 0L,
                openAppEvidenceGraceMs = 0L,
                openAppTransientGraceMs = 0L,
                openAppMaxVerifyTimeoutMs = 0L,
                requiredStableSamples = 2,
                trustedPackageTtlMs = 0L,
            ),
            elapsedRealtime = { 0L },
            sleeper = {},
        )

        val observation = coordinator.captureTrustedObservation(
            forceVisual = false,
            expectedPackage = "",
        )

        assertEquals("com.example.foreground", observation.packageName)
        assertEquals(1, probeCount)
    }

    private object NoOpOverlayController : VisualCaptureOverlayController {
        override fun beginCapture() = Unit
        override fun endCapture() = Unit
    }
}
