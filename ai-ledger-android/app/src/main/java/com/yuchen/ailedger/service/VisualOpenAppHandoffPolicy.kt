package com.yuchen.ailedger.service

/**
 * Structural policy for an open_app handoff. It never selects an app and never interprets page
 * semantics; it only prevents the same already-owned launch from being physically restarted while
 * Android is still collecting package evidence.
 */
internal object VisualOpenAppHandoffPolicy {
    /**
     * A repeated request for the exact verified target is a local no-op. It must be intercepted
     * before the generic GUI Plus action validator, because rejecting it as an internal tool would
     * fabricate a structural route failure and could erase genuine foreign-package evidence.
     */
    fun isRedundantVerifiedTarget(
        runtime: VisualAgentRuntimeContext,
        requestedPackage: String,
    ): Boolean {
        val requested = requestedPackage.trim()
        return requested.isNotBlank() &&
            runtime.surfaceState == VisualSurfaceState.WorkSurface &&
            runtime.selectedTargetPackage == requested &&
            runtime.verifiedTargetPackage == requested
    }

    fun shouldSuppressPhysicalLaunch(
        runtime: VisualAgentRuntimeContext,
        requestedPackage: String,
        alreadyForeground: Boolean,
    ): Boolean {
        if (alreadyForeground) return true
        val requested = requestedPackage.trim()
        if (requested.isBlank()) return false
        return when {
            isRedundantVerifiedTarget(runtime, requested) -> true
            runtime.surfaceState == VisualSurfaceState.Launching ->
                runtime.selectedTargetPackage == requested
            else -> false
        }
    }

    fun suppressionMessage(
        runtime: VisualAgentRuntimeContext,
        requestedPackage: String,
        alreadyForeground: Boolean,
    ): String = when {
        isRedundantVerifiedTarget(runtime, requestedPackage) ->
            "Target package already owns the verified work surface; redundant open_app was skipped: $requestedPackage"
        alreadyForeground -> "Target package is already foreground: $requestedPackage"
        runtime.surfaceState == VisualSurfaceState.Launching ->
            "Target package launch is already in progress; continuing local verification: $requestedPackage"
        else ->
            "Target package launch was suppressed: $requestedPackage"
    }
}
