package com.yuchen.ailedger.service

import java.security.MessageDigest

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
