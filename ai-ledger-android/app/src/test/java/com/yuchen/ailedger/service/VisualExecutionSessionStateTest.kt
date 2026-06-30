package com.yuchen.ailedger.service

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualExecutionSessionStateTest {
    @Test
    fun trustedProbeAloneCannotCompleteExactPackageHandoff() {
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

        assertEquals(VisualSurfaceState.Launching, runtime.surfaceState)
        assertFalse(runtime.guiPlusEligible)
    }

    @Test
    fun stableCoordinatorProofCompletesExactPackageHandoff() {
        val target = "com.example.target"
        val session = VisualExecutionSessionState().apply { beginLaunch(target) }

        val accepted = session.markTargetVerified(target, verification(target))
        val runtime = session.runtimeContext(snapshot(target, "YWJj"))

        assertTrue(accepted)
        assertEquals(VisualSurfaceState.WorkSurface, runtime.surfaceState)
        assertTrue(runtime.guiPlusEligible)
    }

    @Test
    fun oneStableSampleCannotGrantWorkSurface() {
        val target = "com.example.target"
        val session = VisualExecutionSessionState().apply { beginLaunch(target) }

        val accepted = session.markTargetVerified(
            target,
            verification(target, stableSamples = 1),
        )

        assertFalse(accepted)
        assertEquals(VisualSurfaceState.Launching, session.surfaceState)
        assertEquals("", session.verifiedTargetPackage)
    }

    @Test
    fun proofWithoutVisualFrameCannotGrantWorkSurface() {
        val target = "com.example.target"
        val session = VisualExecutionSessionState().apply { beginLaunch(target) }

        val accepted = session.markTargetVerified(
            target,
            verification(target, visualBase64 = ""),
        )

        assertFalse(accepted)
        assertEquals(VisualSurfaceState.Launching, session.surfaceState)
    }

    @Test
    fun proofForAnotherPackageCannotReplaceSelectedTarget() {
        val session = VisualExecutionSessionState().apply { beginLaunch("com.example.target") }

        val accepted = session.markTargetVerified(
            "com.example.target",
            verification("com.example.other"),
        )

        assertFalse(accepted)
        assertEquals("com.example.target", session.selectedTargetPackage)
        assertEquals("", session.verifiedTargetPackage)
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

    @Test
    fun taskWindowPackageHintSurvivesSlowCurrentCaptureButNotNextCapture() {
        ScreenObservationStore.markDisabled()
        ScreenObservationStore.updateWindowHint("com.example.target", "Target")
        val hint = ScreenObservationStore.recentWindowPackageHint()
        val observedAt = hint?.observedAt ?: 0L

        assertEquals("com.example.target", hint?.packageName)
        assertEquals("Target", hint?.windowTitle)

        ScreenObservationStore.update(ScreenObservation(updatedAt = observedAt - 1L))
        assertEquals(
            "com.example.target",
            ScreenObservationStore.recentWindowPackageHint(
                maxAgeMs = 600L,
                nowMs = observedAt + 5_000L,
            )?.packageName,
        )

        ScreenObservationStore.update(ScreenObservation(updatedAt = observedAt + 1L))
        assertTrue(
            ScreenObservationStore.recentWindowPackageHint(
                maxAgeMs = 600L,
                nowMs = observedAt + 5_000L,
            ) == null,
        )
        ScreenObservationStore.markDisabled()
    }

    @Test
    fun finiteAttemptsStopAtLimit() {
        assertTrue(VisualRouteRetryPolicy.decide(true, 0) is VisualRouteRetryDecision.Retry)
        assertTrue(VisualRouteRetryPolicy.decide(true, 1) is VisualRouteRetryDecision.Retry)
        assertTrue(VisualRouteRetryPolicy.decide(true, 2) is VisualRouteRetryDecision.Stop)
    }

    @Test
    fun retryStateDoesNotResetWhenWorkSurfaceRecoveryOnlySucceeds() {
        val retryState = VisualRouteRetryState()
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "route_unavailable",
            retryable = true,
            backendMessage = "temporary",
        )

        val first = retryState.onFailure(error)
        val second = retryState.onFailure(error)
        val third = retryState.onFailure(error)

        assertTrue(first is VisualRouteRetryDecision.Retry && first.attempt == 1)
        assertTrue(second is VisualRouteRetryDecision.Retry && second.attempt == 2)
        assertTrue(third is VisualRouteRetryDecision.Stop)
        assertTrue(retryState.completedRetries == 2)
    }

    @Test
    fun retryStateResetsOnlyAfterSuccessfulCloudPlan() {
        val retryState = VisualRouteRetryState()
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "route_unavailable",
            retryable = true,
            backendMessage = "temporary",
        )

        retryState.onFailure(error)
        retryState.onFailure(error)
        retryState.onSuccess()
        val next = retryState.onFailure(error)

        assertTrue(next is VisualRouteRetryDecision.Retry && next.attempt == 1)
    }

    @Test
    fun disabledRecoveryStopsImmediately() {
        assertTrue(VisualRouteRetryPolicy.decide(false, 0) is VisualRouteRetryDecision.Stop)
    }

    @Test
    fun legacyRouteFailureRetriesInsideCurrentLoop() {
        val decision = VisualRouteRetryPolicy.decide(
            IOException("visual_agent_step failed: agent_brain_route_failed timeout"),
            completedRetries = 0,
        )

        assertTrue(decision is VisualRouteRetryDecision.Retry)
        assertEquals(1, (decision as VisualRouteRetryDecision.Retry).attempt)
    }

    @Test
    fun explicitlyFalseTruncatedAgentBrainOutputStillRetriesBoundedly() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "RetryCompact empty finish_reason=length content_chars=0 completion_tokens=420",
        )

        val first = VisualRouteRetryPolicy.decide(error, 0)
        val exhausted = VisualRouteRetryPolicy.decide(error, VisualRouteRetryPolicy.maxRetries)

        assertTrue(first is VisualRouteRetryDecision.Retry)
        assertTrue(exhausted is VisualRouteRetryDecision.Stop)
    }

    @Test
    fun explicitRouteRejectionDoesNotBecomeRetryable() {
        val error = VisualAgentRequestException(
            httpStatus = 400,
            code = "route_rejected",
            retryable = false,
            backendMessage = "request rejected",
        )

        assertTrue(VisualRouteRetryPolicy.decide(error, 0) is VisualRouteRetryDecision.Stop)
    }

    @Test
    fun ordinaryIoFailureStops() {
        val decision = VisualRouteRetryPolicy.decide(IOException("failed"), 0)

        assertTrue(decision is VisualRouteRetryDecision.Stop)
    }

    @Test
    fun structuredRouteErrorKeepsServerFields() {
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "route_unavailable",
            retryable = true,
            backendMessage = "temporary",
        )

        assertTrue("HTTP status should remain 503 but was ${error.httpStatus}", error.httpStatus == 503)
        assertEquals("route_unavailable", error.code)
        assertTrue(error.retryable)
        assertEquals("temporary", error.backendMessage)
        assertTrue(error.message.orEmpty().contains("HTTP 503"))
        assertTrue(error.message.orEmpty().contains("retryable=true"))
    }

    @Test
    fun explicitNonRetryableRouteErrorStaysNonRetryable() {
        val error = VisualAgentRequestException(
            httpStatus = 503,
            code = "route_rejected",
            retryable = false,
            backendMessage = "rejected",
        )

        assertFalse(error.retryable)
        assertEquals("route_rejected", error.code)
        assertEquals("rejected", error.backendMessage)
        assertTrue(error.message.orEmpty().contains("retryable=false"))
    }

    @Test
    fun oneForeignSampleKeepsGuiOwnershipButSecondMatchingSampleRevokesIt() {
        val target = "com.example.target"
        val session = VisualExecutionSessionState().apply { beginLaunch(target) }
        assertTrue(session.markTargetVerified(target, verification(target)))
        val before = session.routeEpoch

        val ambiguous = session.runtimeContext(snapshot("com.example.other"))

        assertEquals(VisualSurfaceState.WorkSurface, ambiguous.surfaceState)
        assertTrue(ambiguous.guiPlusEligible)
        assertEquals(before, ambiguous.routeEpoch)

        val revoked = session.runtimeContext(snapshot("com.example.other"))

        assertEquals(VisualSurfaceState.Replanning, revoked.surfaceState)
        assertFalse(revoked.guiPlusEligible)
        assertEquals(before + 1L, revoked.routeEpoch)
    }

    @Test
    fun unknownAndTransientPackagesKeepVerifiedGuiOwnership() {
        val target = "com.example.target"
        val transientPackages = listOf("", "unknown", "android", "com.android.systemui")

        transientPackages.forEach { packageName ->
            val session = VisualExecutionSessionState().apply { beginLaunch(target) }
            assertTrue(session.markTargetVerified(target, verification(target)))

            val runtime = session.runtimeContext(snapshot(packageName))

            assertEquals(VisualSurfaceState.WorkSurface, runtime.surfaceState)
            assertTrue(runtime.guiPlusEligible)
            assertEquals(target, runtime.verifiedTargetPackage)
        }
    }

    @Test
    fun observationIdChangesWhenVisualFrameOrSurfaceEpochChanges() {
        val first = snapshot("com.example.target", "YWJj")
        val changedFrame = snapshot("com.example.target", "ZGVm")

        val firstId = VisualObservationProtocol.observationId(first, 1L, 2L)
        val changedFrameId = VisualObservationProtocol.observationId(changedFrame, 1L, 2L)
        val changedEpochId = VisualObservationProtocol.observationId(first, 1L, 3L)

        assertNotEquals(firstId, changedFrameId)
        assertNotEquals(firstId, changedEpochId)
    }

    private fun verification(
        packageName: String,
        stableSamples: Int = 2,
        verified: Boolean = true,
        visualBase64: String = "YWJj",
    ): VisualTargetPackageVerification {
        val snapshot = snapshot(packageName, visualBase64)
        val visual = visualBase64.takeIf(String::isNotBlank)?.let {
            ScreenVisualObservation(
                available = true,
                mimeType = "image/jpeg",
                width = 720,
                height = 1280,
                displayWidth = 1080,
                displayHeight = 2400,
                base64Jpeg = it,
                source = "test",
                reason = "test",
            )
        }
        return VisualTargetPackageVerification(
            verified = verified,
            stableSamples = stableSamples,
            lastSnapshot = snapshot,
            lastObservation = ScreenObservation(
                enabled = true,
                serviceConnected = true,
                packageName = packageName,
                visual = visual,
            ),
        )
    }

    private fun snapshot(
        packageName: String,
        visualBase64: String = "",
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
        visual = visualBase64.takeIf { it.isNotBlank() }?.let {
            AgentScreenVisual(
                available = true,
                mimeType = "image/jpeg",
                width = 720,
                height = 1280,
                displayWidth = 1080,
                displayHeight = 2400,
                base64Jpeg = it,
                source = "test",
                reason = "test",
            )
        },
    )
}
