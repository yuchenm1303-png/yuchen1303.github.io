package com.yuchen.ailedger.service

import org.json.JSONObject

/**
 * Repairs one known backend compatibility gap without making a local semantic decision.
 *
 * Some deployed GUI Plus gateways understand the official mobile_use action=open and resolve it to
 * open_app, but then reject open_app while filtering internal tools. Android may restore that exact
 * explicit model action only when its label maps to exactly one canonical app from the same request.
 */
internal object GuiPlusOpenAppProtocolRepair {
    fun repair(
        plan: CloudAgentPlan,
        appContext: List<VisualAgentAppContextItem>,
    ): CloudAgentPlan {
        val step = plan.step
        if (step.type != "need_user_help") return plan
        if (!step.reason.orEmpty().contains(UNSUPPORTED_OPEN_REASON, ignoreCase = true)) return plan

        val requestedLabel = extractRequestedOpenLabel(plan.rawModelOutput)
            ?.takeIf(String::isNotBlank)
            ?: step.targetText?.trim()?.takeIf(String::isNotBlank)
            ?: return plan
        val requestedKey = normalizeLabel(requestedLabel)
        val matches = appContext.asSequence()
            .filter { app ->
                normalizeLabel(app.label) == requestedKey ||
                    app.aliases.any { normalizeLabel(it) == requestedKey }
            }
            .distinctBy { it.packageName }
            .take(2)
            .toList()
        if (matches.size != 1) {
            recordRepair(
                accepted = false,
                requestedLabel = requestedLabel,
                packageName = "",
                reason = if (matches.isEmpty()) "canonical_app_not_found" else "canonical_app_ambiguous",
            )
            return plan
        }

        val app = matches.single()
        val args = JSONObject(step.toolArgs?.toString() ?: "{}").apply {
            put("protocolRepair", "gui_plus_mobile_use_open_to_open_app")
            put("protocolRepairSource", "exact_model_tool_call")
            put("canonicalAppLabel", app.label)
            put("canonicalPackageName", app.packageName)
        }
        val repaired = step.copy(
            type = "open_app",
            targetText = app.label,
            appName = app.label,
            packageName = app.packageName,
            reason = "GUI Plus mobile_use open restored from the exact canonical app catalog entry.",
            riskLevel = "low",
            requiresConfirmation = false,
            toolArgs = args,
        )
        recordRepair(
            accepted = true,
            requestedLabel = requestedLabel,
            packageName = app.packageName,
            reason = "exact_unique_canonical_match",
        )
        return plan.copy(
            step = repaired,
            steps = listOf(repaired),
        )
    }

    private fun extractRequestedOpenLabel(rawOutput: String): String? {
        val raw = rawOutput.trim()
        if (raw.isBlank()) return null
        val toolCallBody = TOOL_CALL_PATTERN.find(raw)?.groupValues?.getOrNull(1)?.trim()
            ?: raw.takeIf { it.startsWith("{") && it.endsWith("}") }
            ?: return null
        val root = runCatching { JSONObject(toolCallBody) }.getOrNull() ?: return null
        if (!root.optString("name").equals("mobile_use", ignoreCase = true)) return null
        val arguments = root.optJSONObject("arguments") ?: return null
        if (!arguments.optString("action").equals("open", ignoreCase = true)) return null
        return arguments.optString("text").trim().takeIf(String::isNotBlank)
    }

    private fun normalizeLabel(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), "")

    private fun recordRepair(
        accepted: Boolean,
        requestedLabel: String,
        packageName: String,
        reason: String,
    ) {
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "gui_plus_open_app_protocol_repair",
            details = JSONObject().apply {
                put("accepted", accepted)
                put("requestedLabel", requestedLabel.take(120))
                put("packageName", packageName.take(160))
                put("reason", reason)
                put("semanticSelectionPerformed", false)
            },
        )
    }

    private val TOOL_CALL_PATTERN = Regex(
        "<tool_call>\\s*(\\{.*?})\\s*</tool_call>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private const val UNSUPPORTED_OPEN_REASON = "Android client does not support GUI Plus action: open_app"
}
