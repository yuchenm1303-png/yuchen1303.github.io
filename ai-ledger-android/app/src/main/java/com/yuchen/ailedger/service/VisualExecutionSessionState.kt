package com.yuchen.ailedger.service

import java.security.MessageDigest

enum class VisualSurfaceState(val wireValue: String) {
    Planning("planning"),
    Launching("launching"),
    WorkSurface("work_surface"),
    Replanning("replanning"),
}

data class VisualAgentRuntimeContext(
    val surfaceState: VisualSurfaceState = VisualSurfaceState.Planning,
    val selectedTargetPackage: String = "",
    val verifiedTargetPackage: String = "",
    val currentPackage: String = "",
    val observationId: String = "",
    val routeEpoch: Long = 0L,
    val surfaceEpoch: Long = 0L,
    val guiPlusEligible: Boolean = false,
)

/**
 * Deterministic execution state only. It never reads the user's instruction and never chooses,
 * ranks or substitutes an app. The selected package can only come from a cloud open_app action.
 */
class VisualExecutionSessionState {
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

    fun beginLaunch(packageName: String) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return
        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = ""
        transitionTo(VisualSurfaceState.Launching)
    }

    fun markTargetVerified(packageName: String) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return
        selectedTargetPackage = cleanPackage
        verifiedTargetPackage = cleanPackage
        transitionTo(VisualSurfaceState.WorkSurface)
    }

    fun markStructuralReplan() {
        if (verifiedTargetPackage.isNotBlank()) return
        if (surfaceState != VisualSurfaceState.Replanning) routeEpoch += 1L
        transitionTo(VisualSurfaceState.Replanning)
    }

    fun synchronizeWith() {
        if (surfaceState == VisualSurfaceState.Replanning) return
        val nextState = when {
            verifiedTargetPackage.isBlank() -> {
                if (surfaceState == VisualSurfaceState.Launching) VisualSurfaceState.Launching
                else VisualSurfaceState.Planning
            }
            else -> VisualSurfaceState.WorkSurface
        }
        transitionTo(nextState)
    }

    @Suppress("UNUSED_PARAMETER")
    fun isVerifiedWorkSurface(snapshot: AgentScreenSnapshot): Boolean {
        return surfaceState == VisualSurfaceState.WorkSurface &&
            verifiedTargetPackage.isNotBlank()
    }

    fun runtimeContext(snapshot: AgentScreenSnapshot): VisualAgentRuntimeContext {
        synchronizeWith()
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

    companion object {
        const val ASSISTANT_HOST_PACKAGE = "com.yuchen.ailedger"
    }
}

object VisualObservationProtocol {
    fun observationId(
        snapshot: AgentScreenSnapshot,
        routeEpoch: Long,
        surfaceEpoch: Long,
    ): String {
        val canonical = listOf(
            routeEpoch.toString(),
            surfaceEpoch.toString(),
            actionContextFingerprint(snapshot),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(24)
    }

    fun isActionContextFresh(
        observedSnapshot: AgentScreenSnapshot,
        currentSnapshot: AgentScreenSnapshot,
    ): Boolean {
        return observedSnapshot.packageName == currentSnapshot.packageName &&
            actionContextFingerprint(observedSnapshot) == actionContextFingerprint(currentSnapshot)
    }

    fun actionContextFingerprint(snapshot: AgentScreenSnapshot): String {
        val texts = snapshot.texts
            .asSequence()
            .map { it.trim().take(48) }
            .filter(String::isNotBlank)
            .take(MAX_TEXT_ITEMS)
            .joinToString("|")
        val clickable = snapshot.clickableNodes
            .asSequence()
            .map { "${it.text.trim().take(32)}#${it.bounds}" }
            .take(MAX_NODE_ITEMS)
            .joinToString("|")
        val inputs = snapshot.inputNodes
            .asSequence()
            .map { "${it.text.trim().take(32)}#${it.bounds}" }
            .take(MAX_NODE_ITEMS)
            .joinToString("|")
        val scrollable = snapshot.scrollableNodes
            .asSequence()
            .map { "${it.text.trim().take(32)}#${it.bounds}" }
            .take(MAX_NODE_ITEMS)
            .joinToString("|")
        return listOf(
            snapshot.packageName,
            snapshot.nodeCount.toString(),
            snapshot.capturedNodeCount.toString(),
            texts,
            clickable,
            inputs,
            scrollable,
        ).joinToString("::")
    }

    private const val MAX_TEXT_ITEMS = 24
    private const val MAX_NODE_ITEMS = 20
}
