package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject

private const val CLIENT_TOOL_EXECUTION_RESULT_SCHEMA = "ai_ledger_client_tool_execution_result_v1"

/**
 * Lossless receipt codec for structured Android tool executions stored in the persistent
 * at-most-once ledger. It preserves the verified result and the exact undo step without
 * reinterpreting user text or tool intent.
 */
internal object ClientToolExecutionResultCodec {
    fun encode(
        call: CloudClientToolCall,
        step: CloudAgentStep,
        result: AgentExecutionResult,
        executionOwner: String,
    ): JSONObject = JSONObject().apply {
        put("schema", CLIENT_TOOL_EXECUTION_RESULT_SCHEMA)
        put("protocol", call.resultProtocol)
        put("toolCallId", call.id)
        put("toolName", call.name)
        put("toolArguments", JSONObject(call.arguments.toString()))
        put("stepType", step.type)
        put("executionOwner", executionOwner)
        put("ok", result.ok)
        put("message", result.message.take(4_000))
        put("shouldContinue", result.shouldContinue)
        put("diagnostics", result.diagnostics.take(1_000))
        result.undoStep?.let { put("undoStep", it.toReceiptJson()) }
    }

    fun decode(
        receipt: JSONObject,
        fallbackMessage: String,
    ): AgentExecutionResult = AgentExecutionResult(
        ok = receipt.optBoolean("ok", false),
        message = receipt.optString("message").ifBlank { fallbackMessage },
        shouldContinue = receipt.optBoolean("shouldContinue", false),
        undoStep = receipt.optJSONObject("undoStep")?.let(CloudAgentStep::fromJson),
        diagnostics = appendDiagnostic(receipt.optString("diagnostics"), "idempotent_replay"),
    )

    fun appendDiagnostic(existing: String, value: String): String = when {
        existing.isBlank() -> value
        existing.contains(value) -> existing
        else -> "$existing;$value"
    }

    private fun CloudAgentStep.toReceiptJson(): JSONObject = JSONObject().apply {
        put("type", type)
        targetNodeId?.takeIf(String::isNotBlank)?.let { put("targetNodeId", it) }
        targetText?.takeIf(String::isNotBlank)?.let { put("targetText", it) }
        text?.takeIf(String::isNotBlank)?.let { put("text", it) }
        direction?.takeIf(String::isNotBlank)?.let { put("direction", it) }
        reason?.takeIf(String::isNotBlank)?.let { put("reason", it) }
        put("riskLevel", riskLevel)
        put("requiresConfirmation", requiresConfirmation)
        appName?.takeIf(String::isNotBlank)?.let { put("appName", it) }
        packageName?.takeIf(String::isNotBlank)?.let { put("packageName", it) }
        x?.let { put("x", it) }
        y?.let { put("y", it) }
        durationMs?.let { put("durationMs", it) }
        inputMode?.takeIf(String::isNotBlank)?.let { put("inputMode", it) }
        put("requiresInputNode", requiresInputNode)
        put("expectsFocusedInput", expectsFocusedInput)
        put("useFocusedInput", useFocusedInput)
        toolArgs?.let { put("args", JSONObject(it.toString())) }
        purpose?.takeIf(String::isNotBlank)?.let { put("purpose", it) }
        milestoneId?.takeIf(String::isNotBlank)?.let { put("milestoneId", it) }
        if (expectedEvidence.isNotEmpty()) put("expectedEvidence", JSONArray(expectedEvidence))
        if (failureEvidence.isNotEmpty()) put("failureEvidence", JSONArray(failureEvidence))
        put("exploratory", exploratory)
        put("reversible", reversible)
        confidence?.let { put("confidence", it) }
        hypothesisId?.takeIf(String::isNotBlank)?.let { put("hypothesisId", it) }
        put("legacyIntent", legacyIntent)
    }
}
