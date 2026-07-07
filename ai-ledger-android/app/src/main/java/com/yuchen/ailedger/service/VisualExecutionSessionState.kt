package com.yuchen.ailedger.service

/**
 * Session facade that applies target-binding side effects around a pure execution state machine.
 * It never reads the user's instruction and never chooses, ranks or substitutes an app.
 */
class VisualExecutionSessionState(
    targetBinding: VisualTargetBinding = GlobalVisualTargetBinding,
    private val stateMachine: VisualExecutionStateMachine = VisualExecutionStateMachine(),
) {
    private val sessionBinding = SessionVisualTargetBinding(targetBinding)

    val surfaceState: VisualSurfaceState
        get() = stateMachine.surfaceState
    val selectedTargetPackage: String
        get() = stateMachine.selectedTargetPackage
    val verifiedTargetPackage: String
        get() = stateMachine.verifiedTargetPackage
    val routeEpoch: Long
        get() = stateMachine.routeEpoch
    val surfaceEpoch: Long
        get() = stateMachine.surfaceEpoch

    init {
        sessionBinding.reset()
    }

    fun beginLaunch(packageName: String) {
        stateMachine.beginLaunch(packageName)?.let(sessionBinding::bind)
    }

    /**
     * Grants WorkSurface only from the coordinator's complete verification proof: the exact
     * DeepSeek-selected package, at least two stable samples and a final visual frame.
     */
    fun markTargetVerified(
        expectedPackage: String,
        verification: VisualTargetPackageVerification,
    ): Boolean {
        val cleanExpected = expectedPackage.trim()
        val finalSnapshot = verification.lastSnapshot
        val finalObservation = verification.lastObservation
        val proofValid = verification.verified &&
            verification.stableSamples >= MIN_STABLE_TARGET_SAMPLES &&
            finalObservation?.packageName == cleanExpected &&
            finalObservation.visual?.hasImage == true &&
            finalSnapshot?.packageName == cleanExpected &&
            finalSnapshot.visual?.hasImage == true &&
            cleanExpected.isNotBlank() &&
            cleanExpected == stateMachine.selectedTargetPackage
        if (!proofValid) return false
        val verified = stateMachine.markTargetVerified(cleanExpected) ?: return false
        sessionBinding.bind(verified)
        return true
    }

    fun markStructuralReplan() {
        stateMachine.markStructuralReplan()
    }

    /**
     * The visual loop is screenshot-authoritative. A full clean screenshot is requested before the
     * cloud route has a package binding so GUI Plus can understand the current page directly.
     */
    fun requiresVisualObservation(): Boolean {
        return stateMachine.requiresVisualObservation()
    }

    fun synchronizeWith(snapshot: AgentScreenSnapshot? = null) {
        stateMachine.synchronizeWith(snapshot?.packageName.orEmpty())
    }

    fun requiresForeignConfirmation(snapshot: AgentScreenSnapshot): Boolean {
        return stateMachine.requiresForeignConfirmation(snapshot.packageName)
    }

    fun isVerifiedWorkSurface(snapshot: AgentScreenSnapshot): Boolean {
        return stateMachine.isVerifiedWorkSurface(snapshot.packageName) ||
            isScreenshotBoundVisualSurface(snapshot)
    }

    fun runtimeContext(snapshot: AgentScreenSnapshot): VisualAgentRuntimeContext {
        synchronizeWith(snapshot)
        val observationId = VisualObservationProtocol.observationId(
            snapshot = snapshot,
            routeEpoch = stateMachine.routeEpoch,
            surfaceEpoch = stateMachine.surfaceEpoch,
        )
        return VisualAgentRuntimeContext(
            surfaceState = stateMachine.surfaceState,
            selectedTargetPackage = stateMachine.selectedTargetPackage,
            verifiedTargetPackage = stateMachine.verifiedTargetPackage,
            currentPackage = snapshot.packageName,
            observationId = observationId,
            routeEpoch = stateMachine.routeEpoch,
            surfaceEpoch = stateMachine.surfaceEpoch,
            guiPlusEligible = isVerifiedWorkSurface(snapshot),
        )
    }

    /**
     * Global visual tasks may start from any current screen. Before DeepSeek has selected an app,
     * the clean screenshot itself is the only legitimate work surface for low-risk GUI Plus actions.
     * This does not mutate the state machine into WorkSurface and does not replace the stricter
     * package binding used after an explicit open_app handoff.
     */
    private fun isScreenshotBoundVisualSurface(snapshot: AgentScreenSnapshot): Boolean {
        return stateMachine.selectedTargetPackage.isBlank() &&
            stateMachine.verifiedTargetPackage.isBlank() &&
            snapshot.visual?.hasImage == true
    }

    companion object {
        const val ASSISTANT_HOST_PACKAGE = VisualExecutionStateMachine.ASSISTANT_HOST_PACKAGE
        private const val MIN_STABLE_TARGET_SAMPLES = 2
    }
}
