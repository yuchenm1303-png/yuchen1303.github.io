package com.yuchen.ailedger.service

import kotlin.math.max
import kotlin.math.min

internal object VisualLoopSupport {
    const val MAX_RECENT_ACTIONS = 14
    const val MAX_RECENT_ACTION_CHARS = 1_200
    const val MAX_INTERACTION_TEXT_CHARS = 1_000
    const val MAX_INTERACTION_ACTIONS = 12
    const val MAX_INTERACTION_IN_REQUEST = 8
    const val CLIENT_ACTION_LIMIT = 14
    const val MIN_RUNTIME_ACTIONS = 6
    const val NORMAL_HISTORY_ITEMS = 2
    const val RECOVERY_HISTORY_ITEMS = 4
    const val MAX_APP_CONTEXT_ITEMS = 160
    const val MAX_REJECTIONS = 3
    const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"

    fun materializeTap(step: CloudAgentStep, snapshot: AgentScreenSnapshot): CloudAgentStep {
        if (step.type == "tap_node") {
            val declaredTarget = step.targetText?.trim().orEmpty()
            if (declaredTarget.isNotBlank()) {
                val terms = declaredTargetTerms(declaredTarget)
                val selectedBounds = (snapshot.clickableNodes + snapshot.allNodes.filter(AgentScreenNode::clickable))
                    .distinctBy { "${it.text}|${it.bounds}|${it.className}" }
                    .mapNotNull { node ->
                        val bounds = parseBounds(node.bounds) ?: return@mapNotNull null
                        val score = targetMatchScore(node.text, terms)
                        score.takeIf { it > 0 }?.let { scoreValue -> bounds to scoreValue }
                    }
                    .maxByOrNull { it.second }
                    ?.first
                if (selectedBounds != null) {
                    VisualAgentHudRuntime.notePlannedTarget(
                        step = step,
                        x = selectedBounds.centerX,
                        y = selectedBounds.centerY,
                    )
                }
            }
            return step
        }
        if (step.type != "tap_xy") return step
        val x = step.x ?: return step
        val y = step.y ?: return step
        val visual = snapshot.visual
        val materialized = if (visual == null) {
            step
        } else {
            val width = visual.displayWidth.takeIf { it > 0 } ?: visual.width.takeIf { it > 0 }
            val height = visual.displayHeight.takeIf { it > 0 } ?: visual.height.takeIf { it > 0 }
            if (width == null || height == null) {
                step
            } else {
                val pixelX = (x * width).coerceIn(0f, width.toFloat())
                val pixelY = (y * height).coerceIn(0f, height.toFloat())
                groundDeclaredTapTarget(
                    step = step.copy(x = pixelX, y = pixelY),
                    snapshot = snapshot,
                    displayWidth = width,
                    displayHeight = height,
                )
            }
        }
        VisualAgentHudRuntime.notePlannedStep(materialized)
        return materialized
    }

    private fun groundDeclaredTapTarget(
        step: CloudAgentStep,
        snapshot: AgentScreenSnapshot,
        displayWidth: Int,
        displayHeight: Int,
    ): CloudAgentStep {
        val pointX = step.x ?: return step
        val pointY = step.y ?: return step
        val declaredTarget = step.targetText?.trim().orEmpty()
        if (declaredTarget.isBlank()) return step

        val targetTerms = declaredTargetTerms(declaredTarget)
        if (targetTerms.isEmpty()) return step
        val candidates = (snapshot.clickableNodes + snapshot.allNodes.filter(AgentScreenNode::clickable))
            .distinctBy { "${it.text}|${it.bounds}|${it.className}" }
            .mapNotNull { node ->
                val bounds = parseBounds(node.bounds) ?: return@mapNotNull null
                val score = targetMatchScore(node.text, targetTerms)
                score.takeIf { it > 0 }?.let { DeclaredTapCandidate(node, bounds, score) }
            }
        if (candidates.isEmpty()) return step

        val bestScore = candidates.maxOf(DeclaredTapCandidate::score)
        val bestCandidates = candidates.filter { it.score == bestScore }
        val selected = when {
            bestCandidates.size == 1 -> bestCandidates.single()
            else -> bestCandidates.minByOrNull { it.bounds.distanceTo(pointX, pointY) } ?: return step
        }
        if (selected.bounds.contains(pointX, pointY)) return step

        val targetDistance = selected.bounds.distanceTo(pointX, pointY)
        val proximityLimit = (min(displayWidth, displayHeight) * TARGET_GROUNDING_PROXIMITY_RATIO)
            .coerceIn(MIN_TARGET_GROUNDING_PX, MAX_TARGET_GROUNDING_PX)
        val exactDeclaredMatch = bestScore >= EXACT_TARGET_SCORE
        // A unique exact/quoted label may be grounded across the page. Partial text matches and
        // repeated labels are corrected only when the original coordinate is already nearby.
        if (!exactDeclaredMatch && targetDistance > proximityLimit) return step
        if (bestCandidates.size > 1 && targetDistance > proximityLimit) return step

        val corrected = selected.bounds.nearestInteriorPoint(pointX, pointY)
        val label = selected.node.text.trim().take(32)
        val groundingNote = "坐标已按声明目标${label.takeIf(String::isNotBlank)?.let { "“$it”" }.orEmpty()}校准到可点击区域"
        val mergedReason = listOfNotNull(
            step.reason?.trim()?.takeIf(String::isNotBlank),
            groundingNote,
        ).joinToString("。").take(320)
        return step.copy(
            x = corrected.first,
            y = corrected.second,
            reason = mergedReason,
        )
    }

    private fun declaredTargetTerms(targetText: String): List<String> {
        val quoted = QUOTED_TARGET_PATTERN.findAll(targetText)
            .mapNotNull { match -> match.groupValues.drop(1).firstOrNull(String::isNotBlank) }
            .map(::normalizeTargetText)
            .filter { it.length >= MIN_TARGET_TERM_CHARS }
            .toList()
        return (quoted + normalizeTargetText(targetText))
            .filter { it.length >= MIN_TARGET_TERM_CHARS }
            .distinct()
    }

    private fun targetMatchScore(nodeText: String, terms: List<String>): Int {
        val normalizedNode = normalizeTargetText(nodeText)
        if (normalizedNode.length < MIN_TARGET_TERM_CHARS) return 0
        return terms.maxOfOrNull { term ->
            when {
                term == normalizedNode -> EXACT_TARGET_SCORE + normalizedNode.length
                term.contains(normalizedNode) -> CONTAINED_TARGET_SCORE + normalizedNode.length
                normalizedNode.contains(term) -> CONTAINED_TARGET_SCORE + term.length
                else -> 0
            }
        } ?: 0
    }

    private fun normalizeTargetText(value: String): String = value
        .trim()
        .lowercase()
        .replace(TARGET_PUNCTUATION_PATTERN, "")
        .replace(TARGET_WHITESPACE_PATTERN, "")
        .take(MAX_TARGET_TEXT_CHARS)

    private fun parseBounds(value: String): TapBounds? {
        val values = BOUNDS_NUMBER_PATTERN.findAll(value)
            .mapNotNull { it.value.toIntOrNull() }
            .take(4)
            .toList()
        if (values.size != 4) return null
        val left = min(values[0], values[2]).toFloat()
        val top = min(values[1], values[3]).toFloat()
        val right = max(values[0], values[2]).toFloat()
        val bottom = max(values[1], values[3]).toFloat()
        if (right <= left || bottom <= top) return null
        return TapBounds(left, top, right, bottom)
    }

    private data class DeclaredTapCandidate(
        val node: AgentScreenNode,
        val bounds: TapBounds,
        val score: Int,
    )

    private data class TapBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f

        fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

        fun distanceTo(x: Float, y: Float): Float {
            val dx = when {
                x < left -> left - x
                x > right -> x - right
                else -> 0f
            }
            val dy = when {
                y < top -> top - y
                y > bottom -> y - bottom
                else -> 0f
            }
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }

        fun nearestInteriorPoint(x: Float, y: Float): Pair<Float, Float> {
            val insetX = ((right - left) * TARGET_INTERIOR_INSET_RATIO)
                .coerceIn(MIN_TARGET_INTERIOR_INSET_PX, MAX_TARGET_INTERIOR_INSET_PX)
                .coerceAtMost((right - left) / 3f)
            val insetY = ((bottom - top) * TARGET_INTERIOR_INSET_RATIO)
                .coerceIn(MIN_TARGET_INTERIOR_INSET_PX, MAX_TARGET_INTERIOR_INSET_PX)
                .coerceAtMost((bottom - top) / 3f)
            return x.coerceIn(left + insetX, right - insetX) to
                y.coerceIn(top + insetY, bottom - insetY)
        }
    }

    fun requiresFreshObservation(step: CloudAgentStep): Boolean =
        step.type !in CloudAgentStep.deviceToolTypes &&
            step.type !in setOf("open_app", "wait", "need_user_help", "finish")

    fun requiresAccessibility(step: CloudAgentStep): Boolean =
        step.type == "open_app" ||
            (step.type !in CloudAgentStep.deviceToolTypes && step.type !in setOf("need_user_help", "finish"))

    fun validationFeedback(
        step: CloudAgentStep,
        validation: VisualActionValidation,
        runtime: VisualAgentRuntimeContext,
    ): String {
        val prefix = if (validation.failureClass == VisualFailureClass.StructuralRoute) {
            "visual_action_rejected"
        } else {
            "visual_action_retry"
        }
        return buildString {
            append(prefix).append(":type=").append(step.type)
            append("|failureClass=").append(validation.failureClass.wireValue)
            append("|surfaceState=").append(runtime.surfaceState.wireValue)
            append("|observationId=").append(runtime.observationId)
            append("|reason=").append(validation.message.take(260))
            append("|replanRequired=").append(validation.failureClass == VisualFailureClass.StructuralRoute)
        }.take(MAX_RECENT_ACTION_CHARS)
    }

    fun resultSummary(
        step: CloudAgentStep,
        signature: String,
        result: AgentExecutionResult,
    ): String {
        val status = when {
            result.ok -> "ok"
            step.type == "open_app" -> "failed"
            else -> "retry"
        }
        val target = step.targetText?.takeIf(String::isNotBlank)
            ?: step.appName?.takeIf(String::isNotBlank)
            ?: step.packageName?.takeIf(String::isNotBlank)
            ?: step.text?.take(32)?.takeIf(String::isNotBlank)
        return buildList {
            add(signature)
            add(status)
            target?.let { add("target=${it.take(56)}") }
            step.purpose?.takeIf(String::isNotBlank)?.let { add("purpose=${it.take(72)}") }
            step.hypothesisId?.takeIf(String::isNotBlank)?.let { add("hypothesis=${it.take(72)}") }
            add("result=${result.message.take(80)}")
        }.joinToString(":").take(MAX_RECENT_ACTION_CHARS)
    }

    fun appendRecent(actions: MutableList<String>, value: String) {
        value.trim().take(MAX_RECENT_ACTION_CHARS).takeIf(String::isNotBlank)?.let(actions::add)
        while (actions.size > MAX_RECENT_ACTIONS) actions.removeAt(0)
    }

    fun appendInteraction(actions: MutableList<String>, value: String) {
        value.trim().take(MAX_INTERACTION_TEXT_CHARS + 80).takeIf(String::isNotBlank)?.let(actions::add)
        while (actions.size > MAX_INTERACTION_ACTIONS) actions.removeAt(0)
    }

    fun requestActions(recent: List<String>, interactions: List<String>): List<String> {
        val interactionBudget = interactions.takeLast(MAX_INTERACTION_IN_REQUEST)
        val runtimeBudget = (CLIENT_ACTION_LIMIT - interactionBudget.size).coerceAtLeast(MIN_RUNTIME_ACTIONS)
        return recent.takeLast(runtimeBudget) + interactionBudget
    }

    fun modelTurnBudget(maxSteps: Int): Int {
        if (maxSteps == Int.MAX_VALUE) return Int.MAX_VALUE
        return (maxSteps * 3).coerceAtLeast(maxSteps + 8).coerceAtMost(120)
    }

    private val BOUNDS_NUMBER_PATTERN = Regex("-?\\d+")
    private val QUOTED_TARGET_PATTERN = Regex("[“\"'‘]([^”\"'’]{2,48})[”\"'’]")
    private val TARGET_PUNCTUATION_PATTERN = Regex("[\\p{P}\\p{S}]")
    private val TARGET_WHITESPACE_PATTERN = Regex("\\s+")
    private const val MIN_TARGET_TERM_CHARS = 2
    private const val MAX_TARGET_TEXT_CHARS = 160
    private const val EXACT_TARGET_SCORE = 1_000
    private const val CONTAINED_TARGET_SCORE = 700
    private const val TARGET_GROUNDING_PROXIMITY_RATIO = 0.1f
    private const val MIN_TARGET_GROUNDING_PX = 48f
    private const val MAX_TARGET_GROUNDING_PX = 180f
    private const val TARGET_INTERIOR_INSET_RATIO = 0.08f
    private const val MIN_TARGET_INTERIOR_INSET_PX = 6f
    private const val MAX_TARGET_INTERIOR_INSET_PX = 18f
}
