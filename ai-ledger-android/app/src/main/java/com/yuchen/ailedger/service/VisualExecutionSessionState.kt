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
     * Launching and replanning use the lightweight package/node probe first. A full screenshot is
     * requested only after the exact target package has been verified as the work surface.
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
        return stateMachine.isVerifiedWorkSurface(snapshot.packageName)
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
            guiPlusEligible = stateMachine.isVerifiedWorkSurface(snapshot.packageName),
        )
    }

    companion object {
        const val ASSISTANT_HOST_PACKAGE = VisualExecutionStateMachine.ASSISTANT_HOST_PACKAGE
        private const val MIN_STABLE_TARGET_SAMPLES = 2
    }
}
