package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOpenAppHandoffPolicyTest {
    @Test
    fun sameTargetLaunchInProgressSuppressesPhysicalRestart() {
        assertTrue(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(VisualSurfaceState.Launching, "com.example.target"),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    @Test
    fun verifiedTargetIsInterceptedBeforeGenericValidation() {
        val runtime = runtime(
            VisualSurfaceState.WorkSurface,
            "com.example.target",
            "com.example.target",
        )

        assertTrue(
            VisualOpenAppHandoffPolicy.isRedundantVerifiedTarget(
                runtime = runtime,
                requestedPackage = "com.example.target",
            ),
        )
        assertTrue(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime,
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    @Test
    fun differentTargetNeverUsesVerifiedTargetNoOp() {
        assertFalse(
            VisualOpenAppHandoffPolicy.isRedundantVerifiedTarget(
                runtime = runtime(
                    VisualSurfaceState.WorkSurface,
                    "com.example.target",
                    "com.example.target",
                ),
                requestedPackage = "com.example.other",
            ),
        )
    }

    @Test
    fun verifiedTargetNoOpDoesNotErasePendingForeignEvidence() {
        val session = VisualExecutionSessionState()
        session.beginLaunch("com.example.target")
        assertTrue(
            session.markTargetVerified(
                expectedPackage = "com.example.target",
                verification = verifiedTargetProof("com.example.target"),
            ),
        )

        val firstForeign = snapshot("com.example.other")
        val runtime = session.runtimeContext(firstForeign)
        assertTrue(session.requiresForeignConfirmation(firstForeign))
        assertTrue(
            VisualOpenAppHandoffPolicy.isRedundantVerifiedTarget(
                runtime = runtime,
                requestedPackage = "com.example.target",
            ),
        )
        assertTrue(session.routeEpoch == 0L)

        session.runtimeContext(snapshot("com.example.other"))
        assertTrue(session.surfaceState == VisualSurfaceState.Replanning)
        assertTrue(session.routeEpoch == 1L)
    }

    @Test
    fun replanningAllowsRealRelaunch() {
        assertFalse(
            VisualOpenAppHandoffPolicy.shouldSuppressPhysicalLaunch(
                runtime = runtime(VisualSurfaceState.Replanning, "com.example.target"),
                requestedPackage = "com.example.target",
                alreadyForeground = false,
            ),
        )
    }

    private fun runtime(
        state: VisualSurfaceState,
        selected: String,
        verified: String = "",
    ) = VisualAgentRuntimeContext(
        surfaceState = state,
        selectedTargetPackage = selected,
        verifiedTargetPackage = verified,
        currentPackage = "",
        observationId = "observation",
        routeEpoch = 0L,
        surfaceEpoch = 0L,
        guiPlusEligible = state == VisualSurfaceState.WorkSurface && verified.isNotBlank(),
    )

    private fun verifiedTargetProof(packageName: String): VisualTargetPackageVerification {
        val observation = ScreenObservation(
            enabled = true,
            serviceConnected = true,
            packageName = packageName,
            visual = visualFrame(),
        )
        return VisualTargetPackageVerification(
            verified = true,
            stableSamples = 2,
            lastSnapshot = observation.toAgentScreenSnapshot(),
            lastObservation = observation,
            reason = VisualTargetPackageVerificationReason.StableTargetWithVisualFrame,
        )
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

    private fun visualFrame() = ScreenVisualObservation(
        available = true,
        width = 100,
        height = 200,
        displayWidth = 100,
        displayHeight = 200,
        base64Jpeg = "YWJj",
        source = "test",
    )
}
