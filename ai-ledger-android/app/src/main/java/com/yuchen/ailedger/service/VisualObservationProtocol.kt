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
     * Performs a lightweight, action-aware execution guard against the fresh accessibility probe.
     * It deliberately does not compare JPEG bytes, dynamic values or full text dumps, so normal
     * clocks, prices, cursors and animations do not add screenshots, CPU load or false replans.
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

        targetFreshness(step, observedSnapshot, currentSnapshot)?.let { return it }

        val observedTokens = interactionSurfaceTokens(observedSnapshot)
        val currentTokens = interactionSurfaceTokens(currentSnapshot)
        if (observedTokens.isEmpty() && currentTokens.isEmpty()) {
            // Visual-only apps have no objective local semantic surface to compare. Package ownership
            // and the verified WorkSurface state remain mandatory at the caller.
            return VisualActionContextFreshness(true, "visual_only_package_verified")
        }
        if (observedTokens.size >= MIN_RICH_SURFACE_TOKENS && currentTokens.isEmpty()) {
            return VisualActionContextFreshness(false, "interaction_surface_unavailable", 0f)
        }
        if (observedTokens.isEmpty() || currentTokens.isEmpty()) {
            return VisualActionContextFreshness(true, "sparse_surface_package_verified")
        }

        val similarity = overlapCoefficient(observedTokens, currentTokens)
        val minimumEvidence = min(observedTokens.size, currentTokens.size)
        if (minimumEvidence >= MIN_RICH_SURFACE_TOKENS && similarity < MIN_SURFACE_OVERLAP) {
            return VisualActionContextFreshness(false, "interaction_surface_changed", similarity)
        }
        return VisualActionContextFreshness(true, "fresh", similarity)
    }

    /**
     * Compatibility entry point for callers that only need package/surface freshness. New visual
     * execution paths should use [evaluateActionContextFreshness] with the actual action.
     */
    fun isActionContextFresh(
        observedSnapshot: AgentScreenSnapshot,
        currentSnapshot: AgentScreenSnapshot,
    ): Boolean {
        val observedPackage = observedSnapshot.packageName.trim()
        val currentPackage = currentSnapshot.packageName.trim()
        if (observedPackage.isBlank() || observedPackage != currentPackage) return false
        val observedTokens = interactionSurfaceTokens(observedSnapshot)
        val currentTokens = interactionSurfaceTokens(currentSnapshot)
        if (observedTokens.isEmpty() && currentTokens.isEmpty()) return true
        if (observedTokens.size >= MIN_RICH_SURFACE_TOKENS && currentTokens.isEmpty()) return false
        if (observedTokens.isEmpty() || currentTokens.isEmpty()) return true
        return min(observedTokens.size, currentTokens.size) < MIN_RICH_SURFACE_TOKENS ||
            overlapCoefficient(observedTokens, currentTokens) >= MIN_SURFACE_OVERLAP
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

    private fun targetFreshness(
        step: CloudAgentStep,
        observedSnapshot: AgentScreenSnapshot,
        currentSnapshot: AgentScreenSnapshot,
    ): VisualActionContextFreshness? {
        if (step.type == "tap_xy") {
            val x = step.x ?: return null
            val y = step.y ?: return null
            val observedHits = hitNodes(observedSnapshot, x, y)
            val currentHits = hitNodes(currentSnapshot, x, y)
            val sameTargetStillUnderPoint = observedHits.any { observed ->
                currentHits.any { current -> samePhysicalTarget(observed, current) }
            }
            return when {
                observedHits.isEmpty() && currentHits.isEmpty() -> null
                observedHits.isEmpty() -> VisualActionContextFreshness(false, "coordinate_target_covered")
                currentHits.isEmpty() -> VisualActionContextFreshness(false, "coordinate_target_missing")
                sameTargetStillUnderPoint -> null
                else -> VisualActionContextFreshness(false, "coordinate_target_changed")
            }
        }

        val targetAwareType = step.type in setOf("tap_node", "input_text", "scroll")
        if (!targetAwareType) return null
        if (step.type == "input_text" && step.shouldUseFocusedDirectInput) return null

        val observedTarget = findTargetNode(step, observedSnapshot)
        val currentTarget = findTargetNode(step, currentSnapshot)
        return when {
            observedTarget == null -> null
            currentTarget == null -> VisualActionContextFreshness(false, "action_target_missing")
            !samePhysicalTarget(observedTarget, currentTarget) ->
                VisualActionContextFreshness(false, "action_target_changed")
            else -> null
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

    private fun hitNodes(snapshot: AgentScreenSnapshot, x: Float, y: Float): List<AgentScreenNode> {
        return interactionNodes(snapshot)
            .asSequence()
            .mapNotNull { node -> parseBounds(node.bounds)?.takeIf { it.contains(x, y) }?.let { node to it } }
            .sortedBy { (_, bounds) -> bounds.area }
            .map { (node, _) -> node }
            .take(MAX_COORDINATE_HIT_CANDIDATES)
            .toList()
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

    private fun interactionSurfaceTokens(snapshot: AgentScreenSnapshot): Set<String> {
        return interactionNodes(snapshot)
            .asSequence()
            .mapNotNull { node ->
                val bounds = parseBounds(node.bounds) ?: return@mapNotNull null
                listOf(
                    nodeRole(node),
                    stableClassName(node.className),
                    bounds.bucketedKey(),
                ).joinToString("|")
            }
            .take(MAX_EXECUTION_SURFACE_NODES)
            .toSet()
    }

    private fun interactionNodes(snapshot: AgentScreenSnapshot): List<AgentScreenNode> {
        val specialized = snapshot.clickableNodes + snapshot.inputNodes + snapshot.scrollableNodes
        val source = if (specialized.isNotEmpty()) specialized else snapshot.allNodes
        return source.distinctBy { "${nodeRole(it)}|${it.className}|${it.bounds}" }
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

    private fun overlapCoefficient(first: Set<String>, second: Set<String>): Float {
        val denominator = min(first.size, second.size)
        if (denominator <= 0) return 1f
        return first.count(second::contains).toFloat() / denominator.toFloat()
    }

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

        fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

        fun bucketedKey(): String = listOf(left, top, right, bottom)
            .joinToString(",") { (it / BOUNDS_BUCKET_PX).toString() }

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

    private val BOUNDS_NUMBER_PATTERN = Regex("-?\\d+")
    private val DYNAMIC_NUMBER_PATTERN = Regex("\\d+(?:[.,:/-]\\d+)*")
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private const val MAX_TEXT_ITEMS = 24
    private const val MAX_NODE_ITEMS = 20
    private const val MAX_EXECUTION_SURFACE_NODES = 64
    private const val MAX_COORDINATE_HIT_CANDIDATES = 8
    private const val MIN_RICH_SURFACE_TOKENS = 4
    private const val MIN_SURFACE_OVERLAP = 0.58f
    private const val MIN_TARGET_IOU = 0.62f
    private const val TARGET_BOUNDS_TOLERANCE_PX = 16
    private const val BOUNDS_BUCKET_PX = 16
    private const val VISUAL_DIGEST_CHARS = 16
}
