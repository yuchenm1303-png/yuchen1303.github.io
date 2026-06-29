package com.yuchen.ailedger.service

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

internal data class VisualActionContextFreshness(
    val fresh: Boolean,
    val reason: String,
    val surfaceSimilarity: Float = 1f,
)

object VisualObservationProtocol {
    /**
     * Identifies the exact observation sent to GUI Plus. Visual bytes participate only so a returned
     * action can be bound to the frame the model actually saw; the digest is never a progress judge.
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
     * Freshness is a protocol guard, not a second visual judge.
     *
     * Screenshot-native actions are authoritative GUI Plus decisions. Accessibility nodes may be
     * incomplete, delayed, wrapper-only or absent, so they cannot veto click, swipe, navigation or
     * focused-direct input. Android only verifies that the target app still owns the foreground.
     * Explicit node actions retain a narrow same-node check because their executor needs that node.
     */
    internal fun evaluateActionContextFreshness(
        step: CloudAgentStep,
        observedSnapshot: AgentScreenSnapshot,
        currentSnapshot: AgentScreenSnapshot,
    ): VisualActionContextFreshness {
        val observedPackage = observedSnapshot.packageName.trim()
        val currentPackage = currentSnapshot.packageName.trim()
        if (observedPackage.isBlank() || observedPackage != currentPackage) {
            return VisualActionContextFreshness(false, "foreground_package_changed", 0f)
        }

        if (step.type in VISUAL_AUTHORITATIVE_ACTION_TYPES ||
            (step.type == "input_text" && step.shouldUseFocusedDirectInput)
        ) {
            return VisualActionContextFreshness(true, "visual_action_package_verified")
        }

        targetFreshness(step, observedSnapshot, currentSnapshot)?.let { return it }
        return VisualActionContextFreshness(true, "node_action_package_verified")
    }

    /**
     * Compatibility entry point. Without a concrete action there is no legitimate local basis for
     * comparing page meaning, so only foreground package ownership is checked.
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
     * Observation identity remains exact because it is used only for anti-replay binding. It must not
     * be interpreted as semantic page identity or task progress.
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

    private fun targetFreshness(
        step: CloudAgentStep,
        observedSnapshot: AgentScreenSnapshot,
        currentSnapshot: AgentScreenSnapshot,
    ): VisualActionContextFreshness? {
        val nodeDependentType = step.type in NODE_DEPENDENT_ACTION_TYPES
        if (!nodeDependentType) return null

        val observedTarget = findTargetNode(step, observedSnapshot)
        val currentTarget = findTargetNode(step, currentSnapshot)
        return when {
            observedTarget == null -> null
            currentTarget == null -> VisualActionContextFreshness(false, "action_target_missing")
            !samePhysicalTarget(observedTarget, currentTarget) ->
                VisualActionContextFreshness(false, "action_target_changed")
            else -> VisualActionContextFreshness(true, "node_target_verified")
        }
    }

    private fun findTargetNode(step: CloudAgentStep, snapshot: AgentScreenSnapshot): AgentScreenNode? {
        val preferred = when (step.type) {
            "input_text" -> snapshot.inputNodes
            "scroll" -> snapshot.scrollableNodes
            "tap_node" -> snapshot.clickableNodes
            else -> emptyList()
        }
        val candidates = (preferred + snapshot.allNodes)
            .distinctBy { "${it.id}|${it.bounds}|${it.className}|${it.text}" }
        val targetText = normalizeStableText(step.targetText.orEmpty())
        if (targetText.isNotBlank()) {
            candidates.firstOrNull { normalizeStableText(it.text) == targetText }?.let { return it }
            candidates.firstOrNull {
                val nodeText = normalizeStableText(it.text)
                nodeText.isNotBlank() && (nodeText.contains(targetText) || targetText.contains(nodeText))
            }?.let { return it }
        }
        val targetId = step.targetNodeId?.trim().orEmpty()
        return targetId.takeIf(String::isNotBlank)?.let { id -> candidates.firstOrNull { it.id == id } }
    }

    private fun samePhysicalTarget(first: AgentScreenNode, second: AgentScreenNode): Boolean {
        if (nodeRole(first) != nodeRole(second)) return false
        val firstClass = stableClassName(first.className)
        val secondClass = stableClassName(second.className)
        val firstText = normalizeStableText(first.text)
        val secondText = normalizeStableText(second.text)
        if (firstText.isNotBlank() && secondText.isNotBlank() && firstText != secondText) return false
        val stableLabelMatch = firstText.isNotBlank() && firstText == secondText
        if (
            firstClass.isNotBlank() &&
            secondClass.isNotBlank() &&
            firstClass != secondClass &&
            !stableLabelMatch
        ) return false
        val firstBounds = parseBounds(first.bounds) ?: return first.bounds == second.bounds
        val secondBounds = parseBounds(second.bounds) ?: return first.bounds == second.bounds
        return firstBounds.isNear(secondBounds) || firstBounds.iou(secondBounds) >= MIN_TARGET_IOU
    }

    private fun nodeRole(node: AgentScreenNode): String = buildString(3) {
        if (node.clickable) append('c')
        if (node.editable) append('e')
        if (node.scrollable) append('s')
        if (isEmpty()) append('n')
    }

    private fun stableClassName(className: String): String = className
        .trim()
        .substringAfterLast('.')
        .lowercase()
        .take(32)

    private fun normalizeStableText(value: String): String = value
        .trim()
        .lowercase()
        .replace(DYNAMIC_NUMBER_PATTERN, "#")
        .replace(WHITESPACE_PATTERN, " ")
        .take(64)

    private fun parseBounds(value: String): SurfaceBounds? {
        val values = BOUNDS_NUMBER_PATTERN.findAll(value)
            .mapNotNull { it.value.toIntOrNull() }
            .take(4)
            .toList()
        if (values.size != 4) return null
        val left = min(values[0], values[2])
        val top = min(values[1], values[3])
        val right = max(values[0], values[2])
        val bottom = max(values[1], values[3])
        if (right <= left || bottom <= top) return null
        return SurfaceBounds(left, top, right, bottom)
    }

    private data class SurfaceBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val area: Long
            get() = (right - left).toLong() * (bottom - top).toLong()

        fun isNear(other: SurfaceBounds): Boolean =
            maxOf(
                kotlin.math.abs(left - other.left),
                kotlin.math.abs(top - other.top),
                kotlin.math.abs(right - other.right),
                kotlin.math.abs(bottom - other.bottom),
            ) <= TARGET_BOUNDS_TOLERANCE_PX

        fun iou(other: SurfaceBounds): Float {
            val intersectionWidth = (min(right, other.right) - max(left, other.left)).coerceAtLeast(0)
            val intersectionHeight = (min(bottom, other.bottom) - max(top, other.top)).coerceAtLeast(0)
            val intersection = intersectionWidth.toLong() * intersectionHeight.toLong()
            if (intersection <= 0L) return 0f
            val union = area + other.area - intersection
            return if (union <= 0L) 0f else intersection.toFloat() / union.toFloat()
        }
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
            append(':').append(VisualFrameDigestCache.digest(visual.base64Jpeg).take(VISUAL_DIGEST_CHARS))
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private val VISUAL_AUTHORITATIVE_ACTION_TYPES = setOf(
        "tap_xy", "swipe", "back", "home", "recents", "notifications", "quick_settings",
    )
    private val NODE_DEPENDENT_ACTION_TYPES = setOf("tap_node", "input_text", "scroll")
    private val BOUNDS_NUMBER_PATTERN = Regex("-?\\d+")
    private val DYNAMIC_NUMBER_PATTERN = Regex("\\d+(?:[.,:/-]\\d+)*")
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private const val MAX_TEXT_ITEMS = 24
    private const val MAX_NODE_ITEMS = 20
    private const val MIN_TARGET_IOU = 0.62f
    private const val TARGET_BOUNDS_TOLERANCE_PX = 16
    private const val VISUAL_DIGEST_CHARS = 16
}
