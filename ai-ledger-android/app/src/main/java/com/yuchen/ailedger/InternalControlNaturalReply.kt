package com.yuchen.ailedger

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.service.AgentExecutionResult
import com.yuchen.ailedger.service.AgentTaskRunResult
import com.yuchen.ailedger.service.AiWorkerClient
import com.yuchen.ailedger.service.ClientToolCallRegistry
import com.yuchen.ailedger.service.CloudAgentStep
import com.yuchen.ailedger.service.CloudClientToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val CLIENT_TOOL_RESULT_MARKER = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]"
private const val CLIENT_TOOL_RESULT_SOURCE = "final_chat_model_client_tool_result"
private const val CLIENT_TOOL_RESULT_PROTOCOL = "android_client_tool_result_v1"
private const val INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL = 1_600

internal suspend fun AiWorkerClient.buildNaturalInternalControlMessage(
    id: String,
    goal: String,
    result: AgentTaskRunResult,
    clientToolCall: CloudClientToolCall? = null,
    modelPreference: ChatModel = clientToolCall?.finalModel
        ?.let { ChatModel.fromId(it) }
        ?.takeIf { it != ChatModel.Auto }
        ?: ChatModel.Auto,
): ChatMessage {
    val receipt = clientToolReceipt(goal = goal, result = result, clientToolCall = clientToolCall)
    return reportClientToolReceipt(id, receipt, modelPreference)
}

internal suspend fun AiWorkerClient.buildNaturalInternalControlMessage(
    id: String,
    goal: String,
    step: CloudAgentStep?,
    execution: AgentExecutionResult?,
    statusOverride: String? = null,
    detailOverride: String? = null,
    clientToolCall: CloudClientToolCall? = null,
    modelPreference: ChatModel = ChatModel.Auto,
): ChatMessage {
    val resolvedCall = clientToolCall ?: ClientToolCallRegistry.consume(step)
    val resolvedModel = if (modelPreference != ChatModel.Auto) {
        modelPreference
    } else {
        resolvedCall?.finalModel
            ?.let { ChatModel.fromId(it) }
            ?.takeIf { it != ChatModel.Auto }
            ?: ChatModel.Auto
    }
    val receipt = clientToolReceipt(
        goal = goal,
        step = step,
        execution = execution,
        statusOverride = statusOverride,
        detailOverride = detailOverride,
        clientToolCall = resolvedCall,
    )
    return reportClientToolReceipt(id, receipt, resolvedModel)
}

private suspend fun AiWorkerClient.reportClientToolReceipt(
    id: String,
    receipt: JSONObject,
    modelPreference: ChatModel,
): ChatMessage {
    val fallback = localReportFallback(receipt)
    val response = runCatching {
        withContext(Dispatchers.IO) {
            sendChat(
                messages = listOf(
                    ChatMessage(
                        id = "client-tool-result-${System.currentTimeMillis()}",
                        text = "$CLIENT_TOOL_RESULT_MARKER\n$receipt",
                        role = MessageRole.User,
                    ),
                ),
                modelPreference = modelPreference,
                onlineEnabled = false,
            )
        }
    }.getOrNull()
    val cloudReply = response
        ?.takeIf { it.source == CLIENT_TOOL_RESULT_SOURCE }
        ?.reply
        ?.trim()
        ?.takeIf(String::isNotBlank)

    return ChatMessage(
        id = id,
        text = cloudReply ?: fallback,
        role = MessageRole.Assistant,
        status = MessageStatus.Sent,
        source = if (cloudReply != null) CLIENT_TOOL_RESULT_SOURCE else "local_agent",
        model = response?.model,
        modelLabel = if (cloudReply != null) response.modelLabel ?: modelPreference.label else "客户端工具",
        version = response?.version,
        errorText = null,
    )
}

private fun clientToolReceipt(
    goal: String,
    result: AgentTaskRunResult,
    clientToolCall: CloudClientToolCall?,
): JSONObject {
    val actions = JSONArray().apply {
        result.logs.forEach { log -> put(actionReceipt(log.step, log.execution)) }
    }
    val lastExecution = result.logs.asReversed().firstNotNullOfOrNull { it.execution }
    val status = when {
        result.stoppedForConfirmation -> "confirmation_required"
        lastExecution != null -> executionStatus(lastExecution)
        result.completed -> "verified"
        result.handled -> "failed"
        else -> "unsupported"
    }
    return baseReceipt(goal, status, clientToolCall).apply {
        put("completed", result.completed)
        put("handled", result.handled)
        put("stoppedForConfirmation", result.stoppedForConfirmation)
        put("resultSummary", result.message.take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL))
        put("actions", actions)
        put("actionCount", actions.length())
    }
}

private fun clientToolReceipt(
    goal: String,
    step: CloudAgentStep?,
    execution: AgentExecutionResult?,
    statusOverride: String?,
    detailOverride: String?,
    clientToolCall: CloudClientToolCall?,
): JSONObject {
    val action = actionReceipt(step, execution).apply {
        if (!detailOverride.isNullOrBlank()) {
            put("technicalDetail", detailOverride.take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL))
        }
    }
    return baseReceipt(
        goal = goal,
        status = statusOverride ?: execution?.let(::executionStatus) ?: "failed",
        clientToolCall = clientToolCall,
    ).apply {
        put("completed", execution?.ok == true)
        put("handled", true)
        put(
            "stoppedForConfirmation",
            statusOverride == "confirmation_required" || statusOverride == "cancelled",
        )
        put(
            "resultSummary",
            detailOverride?.take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL)
                ?: execution?.message.orEmpty().take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL),
        )
        put("actions", JSONArray().put(action))
        put("actionCount", 1)
    }
}

private fun baseReceipt(
    goal: String,
    status: String,
    clientToolCall: CloudClientToolCall?,
): JSONObject = JSONObject().apply {
    put("protocol", CLIENT_TOOL_RESULT_PROTOCOL)
    put("reportMode", "same_final_model_read_only_continuation")
    put("goal", goal.trim().take(1_200))
    put("status", status)
    put("toolCallId", clientToolCall?.id.orEmpty())
    put("toolName", clientToolCall?.name.orEmpty())
    put("toolArguments", clientToolCall?.arguments?.copyJson() ?: JSONObject())
    put("finalModel", clientToolCall?.finalModel.orEmpty())
    put("allowNewAction", false)
    put("allowVisualAgent", false)
}

private fun actionReceipt(
    step: CloudAgentStep?,
    execution: AgentExecutionResult?,
): JSONObject = JSONObject().apply {
    put("tool", step?.type.orEmpty())
    put("toolLabel", step?.typeLabel.orEmpty())
    put("requestedArgs", step?.toolArgs?.copyJson() ?: JSONObject())
    put("riskLevel", step?.riskLevel.orEmpty())
    put("requiresConfirmation", step?.requiresConfirmation == true)
    put("appName", step?.appName.orEmpty())
    put("packageName", step?.packageName.orEmpty())
    put("status", execution?.let(::executionStatus) ?: "not_executed")
    put("ok", execution?.ok == true)
    put("verified", execution?.ok == true)
    put("shouldContinue", execution?.shouldContinue == true)
    put("errorCode", executionErrorCode(execution))
    put("diagnostics", execution?.diagnostics.orEmpty().take(300))
    put("technicalDetail", execution?.message.orEmpty().take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL))
    put("undoAvailable", execution?.undoStep != null)
}

private fun executionStatus(execution: AgentExecutionResult): String {
    val diagnostics = execution.diagnostics.orEmpty().lowercase()
    return when {
        executionPermissionCode(execution) != null -> "permission_required"
        diagnostics.contains("waiting_confirmation") -> "confirmation_required"
        diagnostics.contains("validation_failed") -> "invalid_command"
        diagnostics.contains("verification_failed") || (!execution.ok && diagnostics.contains("verified")) -> "state_mismatch"
        execution.ok -> "verified"
        else -> "failed"
    }
}

private fun executionErrorCode(execution: AgentExecutionResult?): String {
    if (execution == null || execution.ok) return ""
    executionPermissionCode(execution)?.let { return it }
    return execution.diagnostics.orEmpty().trim().takeIf(String::isNotBlank)?.take(300)
        ?: "execution_failed"
}

private fun executionPermissionCode(execution: AgentExecutionResult): String? {
    val diagnostics = execution.diagnostics.orEmpty().lowercase()
    val detail = execution.message.lowercase()
    return when {
        diagnostics.contains("waiting_permission:write_settings") || detail.contains("修改系统设置") ->
            "write_settings_permission_required"
        diagnostics.contains("permission_request_pending_or_failed:shizuku") ->
            "shizuku_permission_request_pending_or_failed"
        detail.contains("shizuku 服务已运行但尚未授权") || detail.contains("本应用尚未授权") ->
            "shizuku_permission_required"
        detail.contains("没有检测到正在运行的 shizuku 服务") -> "shizuku_service_unavailable"
        detail.contains("需要先接入并启动 shizuku/adb bridge") ||
            detail.contains("当前不是 adb/shell 级运行身份") ||
            detail.contains("缺少 shizuku/adb shell 增强权限") -> "enhanced_shell_unavailable"
        else -> null
    }
}

private fun localReportFallback(receipt: JSONObject): String = when (receipt.optString("status")) {
    "verified" -> receipt.optString("resultSummary").ifBlank { "操作已经完成并通过本地复核。" }
    "permission_required" -> "操作还没有完成，当前缺少所需权限。"
    "confirmation_required", "cancelled" -> "操作尚未执行，因为确认流程没有完成。"
    "unsupported", "invalid_command" -> "这项客户端工具暂时无法执行。"
    "state_mismatch" -> "我已经尝试执行，但本地复核没有确认它生效。"
    else -> receipt.optString("resultSummary").ifBlank { "操作没有完成。" }
}

private fun JSONObject.copyJson(): JSONObject = runCatching { JSONObject(toString()) }.getOrDefault(JSONObject())
