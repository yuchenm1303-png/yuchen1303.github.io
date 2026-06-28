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

    private var pendingForeignPackage: String = ""
    private var pendingForeignSamples: Int = 0

    fun beginLaunch(packageName: String): String? {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return null

        // Reopening the already verified base app is a state-layer no-op. Do not clear a pending
        // foreign sample here: a redundant model open_app must not erase real switch evidence.
        if (
            surfaceState == VisualSurfaceState.WorkSurface &&
            selectedTargetPackage == cleanPackage &&
            verifiedTargetPackage == cleanPackage
        ) {
            return cleanPackage
        }

        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = ""
        clearPendingForeignEvidence()
        transitionTo(VisualSurfaceState.Launching)
        return cleanPackage
    }

    /**
     * Completes only an already selected handoff. Package observations never call this method
     * implicitly; the session facade grants it only after the coordinator has produced stable
     * package samples and a visual frame for the same target.
     */
    fun markTargetVerified(packageName: String): String? {
        val cleanPackage = packageName.trim()
        if (
            cleanPackage.isNotBlank() &&
            surfaceState == VisualSurfaceState.WorkSurface &&
            selectedTargetPackage == cleanPackage &&
            verifiedTargetPackage == cleanPackage
        ) {
            clearPendingForeignEvidence()
            return cleanPackage
        }
        if (
            cleanPackage.isBlank() ||
            selectedTargetPackage.isBlank() ||
            cleanPackage != selectedTargetPackage ||
            !isHandoffState(surfaceState)
        ) return null
        verifiedTargetPackage = cleanPackage
        clearPendingForeignEvidence()
        transitionTo(VisualSurfaceState.WorkSurface)
        return cleanPackage
    }

    fun markStructuralReplan() {
        if (surfaceState != VisualSurfaceState.Replanning) routeEpoch += 1L
        verifiedTargetPackage = ""
        clearPendingForeignEvidence()
        transitionTo(VisualSurfaceState.Replanning)
    }

    fun requiresVisualObservation(): Boolean {
        return surfaceState == VisualSurfaceState.WorkSurface && verifiedTargetPackage.isNotBlank()
    }

    /**
     * Missing/transient package evidence never revokes the verified base app. A different concrete
     * app must be observed twice consecutively before the route is considered lost; this separates
     * short-lived overlays from a real app switch without relying on app-specific package lists.
     */
    fun synchronizeWith(currentPackage: String) {
        val cleanCurrentPackage = currentPackage.trim()
        if (surfaceState == VisualSurfaceState.Replanning) return

        if (verifiedTargetPackage.isBlank()) {
            clearPendingForeignEvidence()
            transitionTo(
                if (surfaceState == VisualSurfaceState.Launching) {
                    VisualSurfaceState.Launching
                } else {
                    VisualSurfaceState.Planning
                },
            )
            return
        }

        when {
            cleanCurrentPackage == verifiedTargetPackage -> {
                clearPendingForeignEvidence()
                transitionTo(VisualSurfaceState.WorkSurface)
            }
            VisualSurfacePackagePolicy.requiresForegroundFallback(cleanCurrentPackage) -> {
                clearPendingForeignEvidence()
                transitionTo(VisualSurfaceState.WorkSurface)
            }
            VisualSurfacePackagePolicy.isConfidentForeignPackage(
                cleanCurrentPackage,
                verifiedTargetPackage,
            ) -> {
                if (pendingForeignPackage == cleanCurrentPackage) {
                    pendingForeignSamples += 1
                } else {
                    pendingForeignPackage = cleanCurrentPackage
                    pendingForeignSamples = 1
                }
                if (pendingForeignSamples >= REQUIRED_FOREIGN_SAMPLES) {
                    markStructuralReplan()
                } else {
                    transitionTo(VisualSurfaceState.WorkSurface)
                }
            }
            else -> {
                clearPendingForeignEvidence()
                transitionTo(VisualSurfaceState.WorkSurface)
            }
        }
    }

    fun requiresForeignConfirmation(currentPackage: String): Boolean {
        val current = currentPackage.trim()
        return surfaceState == VisualSurfaceState.WorkSurface &&
            verifiedTargetPackage.isNotBlank() &&
            pendingForeignPackage == current &&
            pendingForeignSamples in 1 until REQUIRED_FOREIGN_SAMPLES &&
            VisualSurfacePackagePolicy.isConfidentForeignPackage(current, verifiedTargetPackage)
    }

    fun isVerifiedWorkSurface(currentPackage: String): Boolean {
        if (
            surfaceState != VisualSurfaceState.WorkSurface ||
            selectedTargetPackage.isBlank() ||
            verifiedTargetPackage.isBlank() ||
            selectedTargetPackage != verifiedTargetPackage
        ) return false

        val current = currentPackage.trim()
        return current == verifiedTargetPackage ||
            VisualSurfacePackagePolicy.requiresForegroundFallback(current) ||
            requiresForeignConfirmation(current)
    }

    private fun clearPendingForeignEvidence() {
        pendingForeignPackage = ""
        pendingForeignSamples = 0
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
        private const val REQUIRED_FOREIGN_SAMPLES = 2
    }
}
