package com.yuchen.ailedger.service

/**
 * Pure state transitions for the visual execution surface. This class has no Android, shell,
 * accessibility, overlay or global binding side effects.
 */
class VisualExecutionStateMachine {
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

    fun beginLaunch(packageName: String): String? {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return null
        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = ""
        transitionTo(VisualSurfaceState.Launching)
        return cleanPackage
    }

    fun markTargetVerified(packageName: String): String? {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return null
        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = cleanPackage
        transitionTo(VisualSurfaceState.WorkSurface)
        return cleanPackage
    }

    fun markStructuralReplan() {
        if (surfaceState != VisualSurfaceState.Replanning) routeEpoch += 1L
        verifiedTargetPackage = ""
        transitionTo(VisualSurfaceState.Replanning)
    }

    fun requiresVisualObservation(): Boolean {
        return surfaceState == VisualSurfaceState.WorkSurface && verifiedTargetPackage.isNotBlank()
    }

    fun synchronizeWith(currentPackage: String) {
        val cleanCurrentPackage = currentPackage.trim()

        if (
            selectedTargetPackage.isNotBlank() &&
            cleanCurrentPackage == selectedTargetPackage &&
            verifiedTargetPackage.isBlank() &&
            isHandoffState(surfaceState)
        ) {
            markTargetVerified(selectedTargetPackage)
            return
        }

        if (surfaceState == VisualSurfaceState.Replanning) return

        if (
            verifiedTargetPackage.isNotBlank() &&
            VisualSurfacePackagePolicy.isConfidentForeignPackage(
                cleanCurrentPackage,
                verifiedTargetPackage,
            )
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

    fun isVerifiedWorkSurface(currentPackage: String): Boolean {
        return surfaceState == VisualSurfaceState.WorkSurface &&
            selectedTargetPackage.isNotBlank() &&
            verifiedTargetPackage.isNotBlank() &&
            selectedTargetPackage == verifiedTargetPackage &&
            currentPackage.trim() == verifiedTargetPackage
    }

    private fun transitionTo(nextState: VisualSurfaceState) {
        if (surfaceState == nextState) return
        surfaceState = nextState
        surfaceEpoch += 1L
    }

    private fun isHandoffState(state: VisualSurfaceState): Boolean {
        return state == VisualSurfaceState.Launching || state == VisualSurfaceState.Replanning
    }

    companion object {
        const val ASSISTANT_HOST_PACKAGE = VisualSurfacePackagePolicy.ASSISTANT_HOST_PACKAGE
    }
}
