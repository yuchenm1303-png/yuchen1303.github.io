package com.yuchen.ailedger.service

/**
 * Session facade that applies target-binding side effects around a pure execution state machine.
 * It never reads the user's instruction and never chooses, ranks or substitutes an app.
 */
class VisualExecutionSessionState(
    private val targetBinding: VisualTargetBinding = GlobalVisualTargetBinding,
    private val stateMachine: VisualExecutionStateMachine = VisualExecutionStateMachine(),
) {
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
        targetBinding.reset()
    }

    fun beginLaunch(packageName: String) {
        stateMachine.beginLaunch(packageName)?.let(targetBinding::bind)
    }

    fun markTargetVerified(packageName: String) {
        stateMachine.markTargetVerified(packageName)?.let(targetBinding::bind)
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
        val verifiedBefore = stateMachine.verifiedTargetPackage
        stateMachine.synchronizeWith(snapshot?.packageName.orEmpty())
        val verifiedAfter = stateMachine.verifiedTargetPackage
        if (verifiedAfter.isNotBlank() && verifiedAfter != verifiedBefore) {
            targetBinding.bind(verifiedAfter)
        }
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
    }
}
