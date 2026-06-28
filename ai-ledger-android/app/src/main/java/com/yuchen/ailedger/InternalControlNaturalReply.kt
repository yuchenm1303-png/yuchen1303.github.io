package com.yuchen.ailedger

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.service.AgentExecutionResult
import com.yuchen.ailedger.service.AgentTaskRunResult
import com.yuchen.ailedger.service.AiWorkerClient
import com.yuchen.ailedger.service.CloudAgentStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val INTERNAL_CONTROL_REPORT_MARKER = "[[AI_LEDGER_INTERNAL_CONTROL_REPORT_V1]]"
private const val INTERNAL_CONTROL_REPORT_SOURCE = "deepseek_internal_control_report"
private const val INTERNAL_CONTROL_REPORT_PROTOCOL = "android_internal_control_receipt_v1"
private const val INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL = 1_600

internal suspend fun AiWorkerClient.buildNaturalInternalControlMessage(
    id: String,
    goal: String,
    result: AgentTaskRunResult,
): ChatMessage {
    val receipt = internalControlReceipt(goal = goal, result = result)
    return reportInternalControlReceipt(id = id, receipt = receipt)
}

internal suspend fun AiWorkerClient.buildNaturalInternalControlMessage(
    id: String,
    goal: String,
    step: CloudAgentStep?,
    execution: AgentExecutionResult?,
    statusOverride: String? = null,
    detailOverride: String? = null,
): ChatMessage {
    val receipt = internalControlReceipt(
        goal = goal,
        step = step,
        execution = execution,
        statusOverride = statusOverride,
        detailOverride = detailOverride,
    )
    return reportInternalControlReceipt(id = id, receipt = receipt)
}

private suspend fun AiWorkerClient.reportInternalControlReceipt(
    id: String,
    receipt: JSONObject,
): ChatMessage {
    val fallback = localReportFallback(receipt)
    val response = runCatching {
        withContext(Dispatchers.IO) {
            sendChat(
                messages = listOf(
                    ChatMessage(
                        id = "internal-control-report-${System.currentTimeMillis()}",
                        text = "$INTERNAL_CONTROL_REPORT_MARKER\n${receipt}",
                        role = MessageRole.User,
                    ),
                ),
                modelPreference = ChatModel.DeepSeekV4,
                onlineEnabled = false,
            )
        }
    }.getOrNull()

    val cloudReply = response
        ?.takeIf { it.source == INTERNAL_CONTROL_REPORT_SOURCE }
        ?.reply
        ?.trim()
        ?.takeIf(String::isNotBlank)

    return ChatMessage(
        id = id,
        text = cloudReply ?: fallback,
        role = MessageRole.Assistant,
        status = MessageStatus.Sent,
        source = if (cloudReply != null) INTERNAL_CONTROL_REPORT_SOURCE else "local_agent",
        model = response?.model,
        modelLabel = if (cloudReply != null) {
            response.modelLabel ?: "DeepSeek V4 Pro"
        } else {
            "内部控制"
        },
        version = response?.version,
        errorText = null,
    )
}

private fun internalControlReceipt(
    goal: String,
    result: AgentTaskRunResult,
): JSONObject {
    val actions = JSONArray().apply {
        result.logs.forEach { log ->
            put(actionReceipt(step = log.step, execution = log.execution))
        }
    }
    val lastExecution = result.logs.asReversed().firstNotNullOfOrNull { it.execution }
    val status = when {
        result.stoppedForConfirmation -> "confirmation_required"
        lastExecution != null -> executionStatus(lastExecution)
        result.completed -> "verified"
        result.handled -> "failed"
        else -> "unsupported"
    }
    return JSONObject().apply {
        put("protocol", INTERNAL_CONTROL_REPORT_PROTOCOL)
        put("reportMode", "read_only_natural_reply")
        put("goal", goal.trim().take(300))
        put("status", status)
        put("completed", result.completed)
        put("handled", result.handled)
        put("stoppedForConfirmation", result.stoppedForConfirmation)
        put("resultSummary", result.message.take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL))
        put("actions", actions)
        put("actionCount", actions.length())
        put("allowNewAction", false)
        put("allowVisualAgent", false)
    }
}

private fun internalControlReceipt(
    goal: String,
    step: CloudAgentStep?,
    execution: AgentExecutionResult?,
    statusOverride: String?,
    detailOverride: String?,
): JSONObject {
    val action = actionReceipt(step = step, execution = execution).apply {
        if (!detailOverride.isNullOrBlank()) {
            put("technicalDetail", detailOverride.take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL))
        }
    }
    return JSONObject().apply {
        put("protocol", INTERNAL_CONTROL_REPORT_PROTOCOL)
        put("reportMode", "read_only_natural_reply")
        put("goal", goal.trim().take(300))
        put("status", statusOverride ?: execution?.let(::executionStatus) ?: "failed")
        put("completed", execution?.ok == true)
        put("handled", true)
        put("stoppedForConfirmation", statusOverride == "confirmation_required" || statusOverride == "cancelled")
        put(
            "resultSummary",
            detailOverride?.take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL)
                ?: execution?.message.orEmpty().take(INTERNAL_CONTROL_MAX_TECHNICAL_DETAIL),
        )
        put("actions", JSONArray().put(action))
        put("actionCount", 1)
        put("allowNewAction", false)
        put("allowVisualAgent", false)
    }
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
    val diagnostics = execution.diagnostics.orEmpty().trim()
    if (diagnostics.isNotBlank()) return diagnostics.take(300)
    return "execution_failed"
}

/**
 * Mechanical classification of executor facts only. This never reads or interprets the user's
 * command; it converts known permission failures into stable receipt codes for DeepSeek reporting.
 */
private fun executionPermissionCode(execution: AgentExecutionResult): String? {
    val diagnostics = execution.diagnostics.orEmpty().lowercase()
    val detail = execution.message.lowercase()
    return when {
        diagnostics.contains("waiting_permission:write_settings") ||
            detail.contains("修改系统设置") -> "write_settings_permission_required"
        diagnostics.contains("permission_request_pending_or_failed:shizuku") -> "shizuku_permission_request_pending_or_failed"
        detail.contains("shizuku 服务已运行但尚未授权") ||
            detail.contains("本应用尚未授权") -> "shizuku_permission_required"
        detail.contains("没有检测到正在运行的 shizuku 服务") -> "shizuku_service_unavailable"
        detail.contains("需要先接入并启动 shizuku/adb bridge") ||
            detail.contains("当前不是 adb/shell 级运行身份") ||
            detail.contains("缺少 shizuku/adb shell 增强权限") -> "enhanced_shell_unavailable"
        else -> null
    }
}

private fun localReportFallback(receipt: JSONObject): String = when (receipt.optString("status")) {
    "verified" -> "操作已经完成，但云端结果说明暂时生成失败。"
    "permission_required" -> "操作还没有完成，当前缺少所需权限；详细说明暂时无法生成。"
    "confirmation_required", "cancelled" -> "操作尚未执行，因为确认流程没有完成。"
    "unsupported", "invalid_command" -> "这项内部控制暂时无法执行，详细说明暂时无法生成。"
    "state_mismatch" -> "我已经尝试执行，但设备状态没有达到目标；详细说明暂时无法生成。"
    else -> "操作没有完成，详细原因暂时无法生成。"
}

private fun JSONObject.copyJson(): JSONObject = try {
    JSONObject(toString())
} catch (_: Exception) {
    JSONObject()
}
