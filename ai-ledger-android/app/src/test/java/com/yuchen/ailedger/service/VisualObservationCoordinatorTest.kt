package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationCoordinatorTest {
    @Test
    fun trustedFallbackRepairsBlankAccessibilityPackage() = runBlocking {
        val overlay = RecordingOverlayController()
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
        assertEquals(1, overlay.beginCount)
        assertEquals(1, overlay.endCount)
    }

    @Test
    fun usableAccessibilityPackageSkipsShellProbe() = runBlocking {
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
                ForegroundPackageProbeResult(
                    packageName = "com.example.other",
                    source = ForegroundPackageEvidenceSource.WindowManager,
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
        assertEquals(0, probeCount)
        assertEquals(1, overlay.beginCount)
        assertEquals(1, overlay.endCount)
    }

    @Test
    fun twoStablePackageSamplesAndVisualFrameCompleteHandoff() = runBlocking {
        var clock = 0L
        val overlay = RecordingOverlayController()
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource { forceVisual ->
                ScreenObservation(
                    enabled = true,
                    serviceConnected = true,
                    packageName = "com.example.target",
                    visual = if (forceVisual) visualFrame() else null,
                )
            },
            foregroundPackageReader = ForegroundPackageReader {
                ForegroundPackageProbeResult(available = false)
            },
            overlayController = overlay,
            timing = zeroTiming().copy(openAppVerifyTimeoutMs = 20L),
            elapsedRealtime = { clock++ },
            sleeper = {},
        )

        val verification = coordinator.awaitStableTargetPackage(
            expectedPackage = "com.example.target",
            isStopped = { false },
        )

        assertTrue(verification.verified)
        assertEquals(2, verification.stableSamples)
        assertEquals("com.example.target", verification.lastSnapshot?.packageName)
        assertTrue(verification.lastObservation?.visual?.hasImage == true)
        assertEquals(3, overlay.beginCount)
        assertEquals(3, overlay.endCount)
    }

    @Test
    fun oneTargetSampleFollowedByForeignPackageCannotCompleteHandoff() = runBlocking {
        var clock = 0L
        var captureIndex = 0
        var visualCaptureCount = 0
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource { forceVisual ->
                if (forceVisual) visualCaptureCount += 1
                captureIndex += 1
                ScreenObservation(
                    enabled = true,
                    serviceConnected = true,
                    packageName = if (captureIndex == 1) "com.example.target" else "com.example.other",
                    visual = if (forceVisual) visualFrame() else null,
                )
            },
            foregroundPackageReader = ForegroundPackageReader {
                ForegroundPackageProbeResult(available = false)
            },
            overlayController = RecordingOverlayController(),
            timing = zeroTiming().copy(openAppVerifyTimeoutMs = 7L),
            elapsedRealtime = { clock++ },
            sleeper = {},
        )

        val verification = coordinator.awaitStableTargetPackage(
            expectedPackage = "com.example.target",
            isStopped = { false },
        )

        assertFalse(verification.verified)
        assertEquals(0, verification.stableSamples)
        assertEquals(0, visualCaptureCount)
        assertEquals("com.example.other", verification.lastSnapshot?.packageName)
    }

    @Test
    fun overlayIsRestoredWhenCaptureFails() = runBlocking {
        val overlay = RecordingOverlayController()
        val coordinator = VisualObservationCoordinator(
            captureSource = VisualObservationCaptureSource {
                error("capture failed")
            },
            foregroundPackageReader = ForegroundPackageReader {
                ForegroundPackageProbeResult(available = false)
            },
            overlayController = overlay,
            timing = zeroTiming(),
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

    private fun zeroTiming() = VisualObservationTiming(
        fullVisualSettleMs = 0L,
        nonVisualSettleMs = 0L,
        packageProbeSettleMs = 0L,
        openAppInitialSettleMs = 0L,
        openAppVerifyPollMs = 0L,
        openAppVerifyTimeoutMs = 0L,
        requiredStableSamples = 2,
    )

    private fun visualFrame() = ScreenVisualObservation(
        available = true,
        width = 100,
        height = 200,
        displayWidth = 100,
        displayHeight = 200,
        base64Jpeg = "YWJj",
        source = "test",
    )

    private class RecordingOverlayController : VisualCaptureOverlayController {
        var beginCount: Int = 0
        var endCount: Int = 0

        override fun beginCapture() {
            beginCount += 1
        }

        override fun endCapture() {
            endCount += 1
        }
    }
}
