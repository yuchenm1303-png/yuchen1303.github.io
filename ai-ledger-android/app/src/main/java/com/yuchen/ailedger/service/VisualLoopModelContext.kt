package com.yuchen.ailedger.service

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
                val result = compactResultSuffix(item.executionResult)
                "Step ${index + 1}: ${action.take(MAX_ACTION_TEXT_CHARS)}$result"
            }

        val userLines = interactionActions
            .takeLast(MAX_INTERACTION_LINES)
            .mapNotNull(::compactInteractionLine)

        val request = (actionLines + userLines).takeLast(MAX_MODEL_ACTIONS + MAX_INTERACTION_LINES)
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "model_visible_previous_actions",
            details = JSONObject().apply {
                put("source", "official_loop_clean_history")
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
                    !line.startsWith("{\"name\"", ignoreCase = true)
            }
            ?.removePrefix("Action:")
            ?.trim()
        return firstLine?.takeIf(String::isNotBlank)
    }

    private fun summarizeToolCall(output: String): String? {
        val json = TOOL_CALL_JSON.find(output)?.groupValues?.getOrNull(1)?.trim()
            ?: RAW_TOOL_JSON.find(output)?.value?.trim()
            ?: return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val args = root.optJSONObject("arguments")
            ?: root.optJSONObject("args")
            ?: root
        val action = args.optString("action").trim().lowercase().takeIf(String::isNotBlank) ?: return null
        val text = args.optString("text").trim()
        val button = args.optString("button").trim()
        val target = text.ifBlank { button }
        return when (action) {
            "open" -> "Open ${target.ifBlank { "app" }}"
            "click" -> "Click ${target.ifBlank { coordinateText(args) ?: "visible target" }}"
            "long_press" -> "Long press ${target.ifBlank { coordinateText(args) ?: "visible target" }}"
            "swipe", "scroll" -> "Swipe ${swipeText(args) ?: target.ifBlank { "on screen" }}"
            "type" -> "Type text"
            "system_button" -> "Press ${button.ifBlank { "system button" }}"
            "wait" -> "Wait"
            "interact" -> "Ask user: ${text.take(80)}"
            "terminate" -> "Terminate with ${args.optString("status").ifBlank { "status" }}"
            else -> action.replace('_', ' ')
        }.trim().takeIf(String::isNotBlank)
    }

    private fun coordinateText(args: JSONObject): String? {
        val coordinate = args.optJSONArray("coordinate") ?: return null
        if (coordinate.length() < 2) return null
        return "(${coordinate.optDouble(0)}, ${coordinate.optDouble(1)})"
    }

    private fun swipeText(args: JSONObject): String? {
        val from = args.optJSONArray("coordinate")
        val to = args.optJSONArray("coordinate2")
        if (from == null || to == null || from.length() < 2 || to.length() < 2) return null
        return "(${from.optDouble(0)}, ${from.optDouble(1)}) to (${to.optDouble(0)}, ${to.optDouble(1)})"
    }

    private fun compactResultSuffix(result: String): String {
        val clean = result.trim()
        if (clean.isBlank()) return ""
        val lower = clean.lowercase()
        val important = lower.contains("failed") ||
            lower.contains("retry") ||
            lower.contains("rejected") ||
            lower.contains("stale") ||
            lower.contains("blocked") ||
            lower.contains("失败") ||
            lower.contains("拒绝") ||
            lower.contains("重试") ||
            lower.contains("未通过")
        if (!important) return ""
        return " | result: ${clean.take(MAX_RESULT_TEXT_CHARS)}"
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

    private val ACTION_LINE = Regex(
        pattern = "(?im)^\\s*Action\\s*:\\s*(.+?)\\s*$",
    )
    private val THINK_BLOCK = Regex(
        pattern = "(?is)<think>.*?</think>",
    )
    private val TOOL_CALL_JSON = Regex(
        pattern = "(?is)<tool_call>\\s*(\\{.*?})\\s*</tool_call>",
    )
    private val RAW_TOOL_JSON = Regex(
        pattern = "(?is)\\{\\s*\"name\"\\s*:\\s*\"mobile_use\"[\\s\\S]*}",
    )
}
