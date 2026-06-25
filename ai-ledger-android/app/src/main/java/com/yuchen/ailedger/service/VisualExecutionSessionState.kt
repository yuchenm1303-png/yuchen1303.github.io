package com.yuchen.ailedger.service

/**
 * Deterministic execution state only. It never reads the user's instruction and never chooses,
 * ranks or substitutes an app. The selected package can only come from a cloud open_app action.
 */
class VisualExecutionSessionState(
    private val targetBinding: VisualTargetBinding = GlobalVisualTargetBinding,
) {
    var surfaceState: VisualSurfaceState = VisualSurfaceState.Planning
        private set
    var selectedTargetPackage: String = ""
        private set
    var verifiedTargetPackage: String = ""
        private set
    var routeEpoch: Long = 0L
        private set
    var surfaceEpoch: Long = 0L
        private set

    init {
        targetBinding.reset()
    }

    fun beginLaunch(packageName: String) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return
        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = ""
        targetBinding.bind(cleanPackage)
        transitionTo(VisualSurfaceState.Launching)
    }

    fun markTargetVerified(packageName: String) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return
        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = cleanPackage
        targetBinding.bind(cleanPackage)
        transitionTo(VisualSurfaceState.WorkSurface)
    }

    fun markStructuralReplan() {
        if (surfaceState != VisualSurfaceState.Replanning) routeEpoch += 1L
        verifiedTargetPackage = ""
        transitionTo(VisualSurfaceState.Replanning)
    }

    /**
     * Launching and replanning use the lightweight package/node probe first. A full screenshot is
     * requested only after the exact target package has been verified as the work surface. This
     * keeps package identity and the GUI Plus frame in the correct order and avoids reading the
     * old overlay/system window before it has disappeared.
     */
    fun requiresVisualObservation(): Boolean {
        return surfaceState == VisualSurfaceState.WorkSurface && verifiedTargetPackage.isNotBlank()
    }

    fun synchronizeWith(snapshot: AgentScreenSnapshot? = null) {
        val currentPackage = snapshot?.packageName?.trim().orEmpty()

        // Package identity is objective evidence. Once DeepSeek selected a package, observing that
        // exact package is enough to complete the handoff; screenshot bytes are not part of the
        // local semantic decision and are supplied separately by the runner for GUI Plus.
        if (
            selectedTargetPackage.isNotBlank() &&
            currentPackage == selectedTargetPackage &&
            verifiedTargetPackage.isBlank() &&
            surfaceState in setOf(VisualSurfaceState.Launching, VisualSurfaceState.Replanning)
        ) {
            markTargetVerified(selectedTargetPackage)
            return
        }

        if (surfaceState == VisualSurfaceState.Replanning) return

        if (
            verifiedTargetPackage.isNotBlank() &&
            isConfidentForeignPackage(currentPackage, verifiedTargetPackage)
        ) {
            markStructuralReplan()
            return
        }

        val nextState = when {
            verifiedTargetPackage.isBlank() -> {
                if (surfaceState == VisualSurfaceState.Launching) VisualSurfaceState.Launching
                else VisualSurfaceState.Planning
            }
            else -> VisualSurfaceState.WorkSurface
        }
        transitionTo(nextState)
    }

    fun isVerifiedWorkSurface(snapshot: AgentScreenSnapshot): Boolean {
        return surfaceState == VisualSurfaceState.WorkSurface &&
            selectedTargetPackage.isNotBlank() &&
            verifiedTargetPackage.isNotBlank() &&
            selectedTargetPackage == verifiedTargetPackage &&
            snapshot.packageName == verifiedTargetPackage
    }

    fun runtimeContext(snapshot: AgentScreenSnapshot): VisualAgentRuntimeContext {
        synchronizeWith(snapshot)
        val observationId = VisualObservationProtocol.observationId(
            snapshot = snapshot,
            routeEpoch = routeEpoch,
            surfaceEpoch = surfaceEpoch,
        )
        return VisualAgentRuntimeContext(
            surfaceState = surfaceState,
            selectedTargetPackage = selectedTargetPackage,
            verifiedTargetPackage = verifiedTargetPackage,
            currentPackage = snapshot.packageName,
            observationId = observationId,
            routeEpoch = routeEpoch,
            surfaceEpoch = surfaceEpoch,
            guiPlusEligible = isVerifiedWorkSurface(snapshot),
        )
    }

    private fun transitionTo(nextState: VisualSurfaceState) {
        if (surfaceState == nextState) return
        surfaceState = nextState
        surfaceEpoch += 1L
    }

    private fun isConfidentForeignPackage(currentPackage: String, expectedPackage: String): Boolean {
        if (currentPackage.isBlank() || currentPackage == expectedPackage) return false
        if (currentPackage == ASSISTANT_HOST_PACKAGE) return false
        if (currentPackage in TRANSIENT_SYSTEM_SURFACE_PACKAGES) return false
        return true
    }

    companion object {
        const val ASSISTANT_HOST_PACKAGE = "com.yuchen.ailedger"

        private val TRANSIENT_SYSTEM_SURFACE_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
        )
    }
}
