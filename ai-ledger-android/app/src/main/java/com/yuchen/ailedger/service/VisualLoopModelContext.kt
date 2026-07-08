package com.yuchen.ailedger.service

import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the model-visible previous-actions channel for GUI Plus.
 *
 * The runner still records rich runtime, ledger, permit and trace lines internally. This object keeps
 * those diagnostics out of the GUI Plus prompt so the visual model receives a loop that is close to
 * the official example: the original goal, the current screenshot and a short list of recent UI
 * actions.
 */
internal object VisualLoopModelContext {
    private const val MAX_MODEL_ACTIONS = 4
    private const val MAX_INTERACTION_LINES = 3
    private const val MAX_ACTION_TEXT_CHARS = 160
    private const val MAX_RESULT_TEXT_CHARS = 140

    fun previousActions(
        visualHistory: List<VisualAgentHistoryItem>,
        interactionActions: List<String>,
        internalRecentActions: List<String>,
    ): List<String> {
        val actionLines = visualHistory
            .takeLast(MAX_MODEL_ACTIONS)
            .mapIndexedNotNull { index, item ->
                val modelOutput = item.component2()
                val action = extractActionText(modelOutput) ?: summarizeToolCall(modelOutput)
                    ?: return@mapIndexedNotNull null
                val result = compactOutcomeSuffix(item.executionResult)
                "Step ${index + 1}: ${action.take(MAX_ACTION_TEXT_CHARS)}$result"
            }

        val userLines = interactionActions
            .takeLast(MAX_INTERACTION_LINES)
            .mapNotNull(::compactInteractionLine)

        val request = (actionLines + userLines).takeLast(MAX_MODEL_ACTIONS + MAX_INTERACTION_LINES)
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "model_visible_previous_actions",
            details = JSONObject().apply {
                put("source", "official_loop_clean_history_with_action_outcomes")
                put("historyItems", visualHistory.size)
                put("internalRecentActions", internalRecentActions.size)
                put("interactionActions", interactionActions.size)
                put("modelVisibleActions", JSONArray(request))
                put("filteredInternalRuntimeLines", internalRecentActions.count(::isInternalRuntimeLine))
            },
        )
        return request
    }

    private fun extractActionText(output: String): String? {
        val clean = output.trim()
        if (clean.isBlank()) return null
        ACTION_LINE.find(clean)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)?.let {
            return it
        }
        val withoutThink = clean.replace(THINK_BLOCK, "").trim()
        val firstLine = withoutThink
            .lineSequence()
            .map(String::trim)
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("<tool_call", ignoreCase = true) &&
                    !line.startsWith("</tool_call", ignoreCase = true) &&
                    !line.startsWith("{\"name\"", ignoreCase = true) &&
                    !BARE_COORDINATE_COMMAND.matches(line)
            }
            ?.removePrefix("Action:")
            ?.trim()
        return firstLine?.takeIf(String::isNotBlank)
    }

    private fun summarizeToolCall(output: String): String? {
        val json = extractToolCallJson(output) ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val args = root.optJSONObject("arguments")
            ?: root.optJSONObject("args")
            ?: root
        val action = args.optString("action").trim().lowercase().takeIf(String::isNotBlank) ?: return null
        val text = args.optString("text").trim()
        val button = args.optString("button").trim()
        val target = text.ifBlank { button }
        val point = normalizedPoint(args.optJSONArray("coordinate"))
        return when (action) {
            "open" -> if (target.isNotBlank()) "Open $target" else "Open app"
            "click" -> if (target.isNotBlank()) "Click $target" else "Click ${point?.let(::clickLocationLabel) ?: "visible target"}"
            "long_press" -> if (target.isNotBlank()) "Long press $target" else "Long press ${point?.let(::clickLocationLabel) ?: "visible target"}"
            "swipe", "scroll" -> if (target.isNotBlank()) "Swipe $target" else "Swipe ${swipeDirection(args) ?: "on screen"}"
            "type" -> "Type text"
            "system_button" -> "Press ${if (button.isNotBlank()) button else "system button"}"
            "wait" -> "Wait"
            "interact" -> "Ask user: ${text.take(80)}"
            "terminate" -> "Terminate with ${args.optString("status").ifBlank { "status" }}"
            else -> action.replace('_', ' ')
        }.trim().takeIf(String::isNotBlank)
    }

    private fun extractToolCallJson(output: String): String? {
        val raw = output.trim()
        if (raw.isBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = raw.substring(start, end + 1).trim()
        return json.takeIf { it.contains("\"mobile_use\"") || it.contains("\"action\"") }
    }

    private fun normalizedPoint(array: JSONArray?): Point? {
        if (array == null || array.length() < 2) return null
        val rawX = array.optDouble(0, Double.NaN)
        val rawY = array.optDouble(1, Double.NaN)
        if (rawX.isNaN() || rawY.isNaN() || rawX.isInfinite() || rawY.isInfinite()) return null
        val x = if (abs(rawX) > 1.0) rawX / 1000.0 else rawX
        val y = if (abs(rawY) > 1.0) rawY / 1000.0 else rawY
        return Point(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))
    }

    private fun clickLocationLabel(point: Point): String = when {
        point.y <= 0.12 && point.x <= 0.18 -> "top-left back/profile area"
        point.y <= 0.12 && point.x >= 0.82 -> "top-right menu/add area"
        point.y <= 0.12 -> "top bar area"
        point.y >= 0.88 && point.x <= 0.25 -> "bottom-left tab"
        point.y >= 0.88 && point.x <= 0.50 -> "bottom-left-center tab"
        point.y >= 0.88 && point.x <= 0.75 -> "bottom-right-center tab"
        point.y >= 0.88 -> "bottom-right tab"
        point.x <= 0.20 -> "left side"
        point.x >= 0.80 -> "right side"
        point.y <= 0.33 -> "upper screen"
        point.y >= 0.67 -> "lower screen"
        else -> "center area"
    }

    private fun swipeDirection(args: JSONObject): String? {
        val from = normalizedPoint(args.optJSONArray("coordinate")) ?: return null
        val to = normalizedPoint(args.optJSONArray("coordinate2")) ?: return null
        val dx = to.x - from.x
        val dy = to.y - from.y
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) "right" else "left"
        } else {
            if (dy > 0) "down" else "up"
        }
    }

    private fun compactOutcomeSuffix(result: String): String {
        val clean = result.trim()
        if (clean.isBlank()) return ""
        val lower = clean.lowercase()
        val message = when {
            lower.contains("open_app_package_verified") ->
                "app opened and verified"
            lower.contains("open_app_package_verification_failed") ->
                "app did not verify; choose another route"
            lower.contains("open_app_package_verification_pending") ->
                "app launch still settling; judge from current screenshot"
            lower.contains("visual_execution_observed") ->
                executionObservationOutcome(clean)
            lower.contains("executionaccepted=false") || lower.contains("gesturedispatched=false") ->
                "not executed; choose a different action"
            lower.contains("visual_action_stale") || lower.contains("stale") ->
                "screen changed before execution; re-plan from current screenshot"
            lower.contains("visual_action_rejected") || lower.contains("rejected") ||
                lower.contains("拒绝") || lower.contains("未通过") ->
                "rejected; choose a different visible action"
            lower.contains("blocked") ->
                "blocked; choose a different route"
            lower.contains("failed") || lower.contains("失败") ->
                "failed; re-plan"
            lower.contains("retry") || lower.contains("重试") ->
                "retry required; choose a different action"
            else -> ""
        }
        return if (message.isBlank()) "" else " -> ${message.take(MAX_RESULT_TEXT_CHARS)}"
    }

    private fun executionObservationOutcome(line: String): String {
        val frameChanged = booleanField(line, "frameChanged")
        val packageChanged = booleanField(line, "packageChanged")
        val structuralRegression = booleanField(line, "structuralRegression")
        return when {
            structuralRegression == true ->
                "target surface changed; re-plan from current screenshot"
            packageChanged == true ->
                "app/surface changed; judge only from current screenshot"
            frameChanged == false ->
                "no visible change; avoid repeating the same area"
            frameChanged == true ->
                "screen changed; judge progress from current screenshot"
            else ->
                "executed; fresh screenshot is authoritative"
        }
    }

    private fun booleanField(line: String, key: String): Boolean? {
        val pattern = Regex("(?:^|[|;:])" + Regex.escape(key) + "=([^|;:]+)", RegexOption.IGNORE_CASE)
        val value = pattern.find(line)?.groupValues?.getOrNull(1)?.trim()?.lowercase() ?: return null
        return when (value) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }

    private fun compactInteractionLine(line: String): String? {
        val clean = line.trim()
        val visible = when {
            clean.startsWith("userReply:") -> "User reply: " +
                clean.substringAfter("userReply:").trim().take(MAX_ACTION_TEXT_CHARS)
            clean.startsWith("guiPlusQuestion:") -> "User help requested: " +
                clean.substringAfter("guiPlusQuestion:").trim().take(MAX_ACTION_TEXT_CHARS)
            else -> return null
        }
        return visible.takeIf { it.substringAfter(':', "").trim().isNotBlank() }
    }

    private fun isInternalRuntimeLine(line: String): Boolean {
        val value = line.trim()
        return value.startsWith("visual_runtime_context:") ||
            value.startsWith("visual_execution_ledger:") ||
            value.startsWith("visual_task_memory:") ||
            value.startsWith("visual_reasoning_context:") ||
            value.startsWith("gui_plus_visual_ownership:") ||
            value.startsWith("app_identity:") ||
            value.startsWith("device_profile:") ||
            value.contains("executionTrace=") ||
            value.contains("observationId=") ||
            value.contains("routeEpoch=") ||
            value.contains("semanticDecisionOwner=")
    }

    private data class Point(val x: Double, val y: Double)

    private val ACTION_LINE = Regex(
        pattern = "(?im)^\\s*Action\\s*:\\s*(.+?)\\s*$",
    )
    private val THINK_BLOCK = Regex(
        pattern = "(?is)<think>.*?</think>",
    )
    private val BARE_COORDINATE_COMMAND = Regex(
        pattern = "(?i)^(click|tap|long_press)\\s+\\d+(?:\\.\\d+)?\\s+\\d+(?:\\.\\d+)?$",
    )
}