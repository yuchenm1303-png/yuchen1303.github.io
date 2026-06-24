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
        if (surfaceState != VisualSurfaceState.Replanning) routeEpoch += 1L
        verifiedTargetPackage = ""
        transitionTo(VisualSurfaceState.Replanning)
    }

    fun requiresVisualObservation(): Boolean {
        return selectedTargetPackage.isNotBlank()
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

object VisualObservationProtocol {
    /**
     * Identifies the exact observation sent to the cloud. Visual bytes intentionally participate
     * here so a returned action can be bound to the frame the model actually saw.
     */
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
        return sha256(canonical).take(24)
    }

    /**
     * Execution freshness is a deterministic surface check, not a semantic screen comparison.
     * Dynamic content such as time, battery, market prices, cursors, animations and JPEG encoding
     * may legitimately change between model observation and action execution. Their meaning is
     * evaluated by the next GUI Plus observation after the action, never by local pixel equality.
     *
     * The caller separately enforces verified-target ownership and route/surface state. This check
     * therefore only rejects an objective foreground-package change.
     */
    fun isActionContextFresh(
        observedSnapshot: AgentScreenSnapshot,
        currentSnapshot: AgentScreenSnapshot,
    ): Boolean {
        val observedPackage = observedSnapshot.packageName.trim()
        val currentPackage = currentSnapshot.packageName.trim()
        return observedPackage.isNotBlank() && observedPackage == currentPackage
    }

    /**
     * Observation identity may remain exact because it is never used to decide whether two live
     * screens are semantically equivalent. It only binds a cloud response to one captured frame.
     */
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
            visualFrameFingerprint(snapshot),
        ).joinToString("::")
    }

    private fun visualFrameFingerprint(snapshot: AgentScreenSnapshot): String {
        val visual = snapshot.visual ?: return "visual:none"
        if (!visual.hasImage) {
            return "visual:unavailable:${visual.width}x${visual.height}:${visual.displayWidth}x${visual.displayHeight}"
        }
        return buildString {
            append("visual:")
            append(visual.width).append('x').append(visual.height)
            append(':').append(visual.displayWidth).append('x').append(visual.displayHeight)
            append(':').append(sha256(visual.base64Jpeg).take(VISUAL_DIGEST_CHARS))
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val MAX_TEXT_ITEMS = 24
    private const val MAX_NODE_ITEMS = 20
    private const val VISUAL_DIGEST_CHARS = 16
}
