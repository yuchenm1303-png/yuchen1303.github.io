package com.yuchen.ailedger.service

/**
 * Session facade for the visual execution loop.
 *
 * Package binding is retained as diagnostic/bootstrap metadata, but a fresh visual frame is the
 * authoritative continuous computer-use surface. Android does not revoke GUI Plus ownership merely
 * because the foreground package changed between observations.
 */
class VisualExecutionSessionState(
    targetBinding: VisualTargetBinding = GlobalVisualTargetBinding,
    private val stateMachine: VisualExecutionStateMachine = VisualExecutionStateMachine(),
) {
    private val sessionBinding = SessionVisualTargetBinding(targetBinding)
    private var forceFirstVisualObservation: Boolean = false
    private var entryHandoffActive: Boolean = false

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
        val firstFrameHandoff = VisualBootstrapFirstFrameState.consumeForceFirstVisualObservation()
        forceFirstVisualObservation = firstFrameHandoff
        entryHandoffActive = true
        val bootstrapTarget = VisualBootstrapFirstFrameState.consumeVerifiedTargetPackage()
        if (bootstrapTarget.isNotBlank()) {
            entryHandoffActive = false
            stateMachine.beginLaunch(bootstrapTarget)
            stateMachine.markTargetVerified(bootstrapTarget)?.let(sessionBinding::bind)
        }
    }

    fun beginLaunch(packageName: String) {
        entryHandoffActive = false
        stateMachine.beginLaunch(packageName)?.let(sessionBinding::bind)
    }

    /**
     * Retains the strict package proof used by deterministic open_app bootstrap. Normal visual turns
     * do not depend on this proof: once Android has a fresh screenshot, GUI Plus may continue across
     * launcher, system UI, another app, a file picker or any other visible surface.
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
        entryHandoffActive = false
        sessionBinding.bind(verified)
        return true
    }

    fun markStructuralReplan() {
        entryHandoffActive = true
        stateMachine.markStructuralReplan()
    }

    /** Every GUI Plus turn is screenshot-native. This avoids node-only transition turns. */
    fun requiresVisualObservation(): Boolean {
        if (forceFirstVisualObservation) forceFirstVisualObservation = false
        return true
    }

    fun synchronizeWith(snapshot: AgentScreenSnapshot? = null) {
        val value = snapshot ?: return
        // A complete visual frame is already the authoritative current surface. Do not feed its
        // package transition into the legacy foreign-package revocation state machine.
        if (!value.hasVisualImage) stateMachine.synchronizeWith(value.packageName)
    }

    fun requiresForeignConfirmation(snapshot: AgentScreenSnapshot): Boolean {
        if (snapshot.hasVisualImage) return false
        return stateMachine.requiresForeignConfirmation(snapshot.packageName)
    }

    fun isVerifiedWorkSurface(snapshot: AgentScreenSnapshot): Boolean {
        if (snapshot.hasVisualImage) return true
        return stateMachine.isVerifiedWorkSurface(snapshot.packageName) || isEntryHandoffSurface(snapshot)
    }

    fun runtimeContext(snapshot: AgentScreenSnapshot): VisualAgentRuntimeContext {
        synchronizeWith(snapshot)
        val observationId = VisualObservationProtocol.observationId(
            snapshot = snapshot,
            routeEpoch = stateMachine.routeEpoch,
            surfaceEpoch = stateMachine.surfaceEpoch,
        )
        val hasFreshVisualFrame = snapshot.hasVisualImage
        val currentPackage = snapshot.packageName.trim()
        val legacyVerifiedPackage = stateMachine.verifiedTargetPackage
        val packageChanged = currentPackage.isNotBlank() &&
            legacyVerifiedPackage.isNotBlank() &&
            currentPackage != legacyVerifiedPackage
        val entryHandoffSurface = isEntryHandoffSurface(snapshot)
        val continuousSurface = hasFreshVisualFrame

        // WorkSurface remains visible for the exact deterministic bootstrap target. A package switch
        // is represented as a new continuous planning frame, never as structural regression.
        val effectiveSurfaceState = when {
            continuousSurface && (packageChanged || stateMachine.surfaceState == VisualSurfaceState.Replanning) ->
                VisualSurfaceState.Planning
            else -> stateMachine.surfaceState
        }
        val effectivePackage = currentPackage.takeIf(String::isNotBlank)
        val effectiveSelectedPackage = if (continuousSurface) {
            effectivePackage ?: stateMachine.selectedTargetPackage
        } else {
            stateMachine.selectedTargetPackage
        }
        val effectiveVerifiedPackage = if (continuousSurface) {
            effectivePackage ?: stateMachine.verifiedTargetPackage
        } else {
            stateMachine.verifiedTargetPackage
        }

        return VisualAgentRuntimeContext(
            surfaceState = effectiveSurfaceState,
            selectedTargetPackage = effectiveSelectedPackage,
            verifiedTargetPackage = effectiveVerifiedPackage,
            currentPackage = snapshot.packageName,
            observationId = observationId,
            routeEpoch = stateMachine.routeEpoch,
            surfaceEpoch = stateMachine.surfaceEpoch,
            guiPlusEligible = continuousSurface ||
                stateMachine.isVerifiedWorkSurface(snapshot.packageName) ||
                entryHandoffSurface,
        )
    }

    private fun isEntryHandoffSurface(snapshot: AgentScreenSnapshot): Boolean {
        val packageName = snapshot.packageName.trim()
        return entryHandoffActive &&
            packageName.isNotBlank() &&
            packageName != ASSISTANT_HOST_PACKAGE
    }

    companion object {
        const val ASSISTANT_HOST_PACKAGE = VisualExecutionStateMachine.ASSISTANT_HOST_PACKAGE
        private const val MIN_STABLE_TARGET_SAMPLES = 2
    }
}
