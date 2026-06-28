package com.yuchen.ailedger.service

/**
 * Structural policy for an open_app handoff. It never selects an app and never interprets page
 * semantics; it only prevents the same already-owned launch from being physically restarted while
 * Android is still collecting package evidence.
 */
internal object VisualOpenAppHandoffPolicy {
    fun shouldSuppressPhysicalLaunch(
        runtime: VisualAgentRuntimeContext,
        requestedPackage: String,
        alreadyForeground: Boolean,
    ): Boolean {
        if (alreadyForeground) return true
        val requested = requestedPackage.trim()
        if (requested.isBlank()) return false
        return when (runtime.surfaceState) {
            VisualSurfaceState.Launching -> runtime.selectedTargetPackage == requested
            VisualSurfaceState.WorkSurface ->
                runtime.selectedTargetPackage == requested && runtime.verifiedTargetPackage == requested
            else -> false
        }
    }

    fun isPendingVerification(verification: VisualTargetPackageVerification): Boolean {
        if (verification.verified) return false
        return verification.reason in PENDING_REASONS
    }

    fun suppressionMessage(
        runtime: VisualAgentRuntimeContext,
        requestedPackage: String,
        alreadyForeground: Boolean,
    ): String = when {
        alreadyForeground -> "Target package is already foreground: $requestedPackage"
        runtime.surfaceState == VisualSurfaceState.Launching ->
            "Target package launch is already in progress; continuing local verification: $requestedPackage"
        else ->
            "Target package already owns the verified work surface; redundant open_app was skipped: $requestedPackage"
    }

    private val PENDING_REASONS = setOf(
        "stable_samples_incomplete",
        "transient_surface",
        "task_stopped",
    )
}
