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
                val action = extractActionText(item.output) ?: return@mapIndexedNotNull null
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

    private fun compactResultSuffix(result: String): String {
        val clean = result.trim()
        if (clean.isBlank()) return ""
        val lower = clean.lowercase()
        val important = lower.contains("failed") ||
            lower.contains("retry") ||
            lower.contains("rejected") ||
            lower.contains("stale") ||
            lower.contains("blocked") ||
            lower.contains("failed") ||
            lower.contains("失败") ||
            lower.contains("拒绝") ||
            lower.contains("重试") ||
            lower.contains("未通过")
        if (!important) return ""
        return " | result: ${clean.take(MAX_RESULT_TEXT_CHARS)}"
    }

    private fun compactInteractionLine(line: String): String? {
        val clean = line.trim()
        return when {
            clean.startsWith("userReply:") -> "User reply: " +
                clean.substringAfter("userReply:").trim().take(MAX_ACTION_TEXT_CHARS)
            clean.startsWith("guiPlusQuestion:") -> "User help requested: " +
                clean.substringAfter("guiPlusQuestion:").trim().take(MAX_ACTION_TEXT_CHARS)
            else -> null
        }.takeIf { it.substringAfter(':', "").trim().isNotBlank() }
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
}
