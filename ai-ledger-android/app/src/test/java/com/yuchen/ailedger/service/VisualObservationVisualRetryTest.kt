package com.yuchen.ailedger.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualObservationVisualRetryTest {
    @Test
    fun unavailableVisualFrameDoesNotDiscardStablePackageEvidence() = runBlocking {
        var clock = 0L
        var visualAttempts = 0
        val target = "com.example.target"
        val coordinator = coordinator(
            target = target,
            clock = { clock },
            advanceClock = { clock += it },
            visualProvider = {
                visualAttempts += 1
                if (visualAttempts == 1) unavailableVisual() else availableVisual()
            },
            timeoutMs = 3L,
            maxTimeoutMs = 4L,
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

    @Test
    fun exhaustedVisualRetriesStayPendingInsteadOfReplanning() = runBlocking {
        var clock = 0L
        val target = "com.example.target"
        val coordinator = coordinator(
            target = target,
            clock = { clock },
            advanceClock = { clock += it },
            visualProvider = ::unavailableVisual,
            timeoutMs = 3L,
            maxTimeoutMs = 3L,
        )

        val verification = coordinator.awaitStableTargetPackage(target) { false }

        assertFalse(verification.verified)
        assertEquals(2, verification.stableSamples)
        assertEquals(
            VisualTargetPackageVerificationReason.VisualFrameUnavailable,
            verification.reason,
        )
        assertTrue(verification.reason.pending)
    }

    private fun coordinator(
        target: String,
        clock: () -> Long,
        advanceClock: (Long) -> Unit,
        visualProvider: () -> ScreenVisualObservation,
        timeoutMs: Long,
        maxTimeoutMs: Long,
    ) = VisualObservationCoordinator(
        captureSource = VisualObservationCaptureSource { forceVisual ->
            ScreenObservation(
                enabled = true,
                serviceConnected = true,
                packageName = target,
                visual = if (forceVisual) visualProvider() else null,
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
            openAppVerifyTimeoutMs = timeoutMs,
            openAppEvidenceGraceMs = 2L,
            openAppTransientGraceMs = 0L,
            openAppMaxVerifyTimeoutMs = maxTimeoutMs,
            requiredStableSamples = 2,
            trustedPackageTtlMs = 100L,
        ),
        elapsedRealtime = clock,
        sleeper = { durationMs -> advanceClock(durationMs) },
    )

    private fun unavailableVisual() = ScreenVisualObservation(
        available = false,
        source = "test",
    )

    private fun availableVisual() = ScreenVisualObservation(
        available = true,
        width = 100,
        height = 200,
        displayWidth = 100,
        displayHeight = 200,
        base64Jpeg = "YWJj",
        source = "test",
    )
}
