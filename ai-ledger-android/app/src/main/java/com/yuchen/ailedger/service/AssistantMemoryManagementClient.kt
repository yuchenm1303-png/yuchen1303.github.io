package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryMutationReceipt
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONObject

private const val MEMORY_MANAGEMENT_REQUEST_SCHEMA = "ai_ledger_memory_management_request_v1"

internal data class AssistantMemoryManagementRequest(
    val operationId: String,
    val action: String,
    val targetMemoryId: String = "",
    val content: String = "",
    val category: String = "other",
    val scope: String = "auto",
    val priority: Int = 1,
    val pinned: Boolean = false,
    val validUntil: String = "",
    val sourceType: String = "manual",
    val confidence: Double = 1.0,
    val namespaceType: String = "account",
    val namespaceId: String = "account",
    val expectedUpdatedAt: String = "",
    val deleteScope: String = "current_only",
    val reason: String = "settings_manual",
)

internal class AssistantMemoryManagementClient(
    private val endpoints: List<String> = listOf(
        AiWorkerClient.DEFAULT_ENDPOINT,
        *AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS.toTypedArray(),
    ),
) {
    private val resolvedClientId: String by lazy {
        AiLedgerApplication.contextOrNull()
            ?.let(AgentClientIdentity::getOrCreateDeviceId)
            ?.trim()
            ?.take(120)
            ?.takeIf(String::isNotBlank)
            ?: AI_WORKER_CHAT_CLIENT_NAME
    }

    @Throws(IOException::class)
    fun mutate(request: AssistantMemoryManagementRequest): AssistantMemoryMutationReceipt {
        val ticket = AssistantAccountSessionRuntime.currentTicket()
            ?: throw IOException("登录状态已失效，请重新登录。")
        val payload = request.toJson()
        val route = AiWorkerModelRoute(
            requested = ChatModel.Kimi,
            resolved = ChatModel.Kimi,
            reason = "memory_management",
        )
        val transport = AiWorkerHttpTransport(
            config = AiWorkerConfig(),
            resolvedClientId = resolvedClientId,
        )
        var lastError: IOException? = null

        for (endpoint in endpoints.asSequence().map(String::trim).filter(String::isNotBlank).distinct()) {
            for (candidate in endpointCandidates(endpoint.trimEnd('/'))) {
                AssistantMemoryRequestContextRuntime.clearCurrentThread()
                AssistantMemoryRequestContextRuntime.stageCurrentThread()
                try {
                    transport.postChat(candidate, payload, route)
                    AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)
                    if (!AssistantAccountSessionRuntime.isCurrent(ticket)) {
                        throw IOException("账号已切换，本次记忆操作结果已丢弃。")
                    }
                    val receipt = AssistantMemoryMutationRuntime.state.value.latestReceipt
                    if (receipt != null && receipt.operationId == request.operationId) return receipt
                    throw IOException("云端没有返回匹配的记忆事务回执。")
                } catch (error: IOException) {
                    AssistantMemoryUsageBridge.recordFailedPayload(payload, error)
                    lastError = error
                    if (!error.isRetryableMemoryManagementFailure()) throw error
                } finally {
                    AssistantMemoryRequestContextRuntime.clearCurrentThread()
                }
            }
        }
        throw lastError ?: IOException("云端记忆管理入口不可用。")
    }

    private fun AssistantMemoryManagementRequest.toJson(): JSONObject {
        val prompt = when (action) {
            "upsert" -> content
            "delete" -> "删除指定长期记忆"
            "archive" -> "停用指定长期记忆"
            "restore" -> "恢复指定长期记忆"
            "clear" -> "清空全部长期记忆"
            else -> "执行长期记忆管理"
        }
        return JSONObject().apply {
            put("requestId", operationId)
            put("action", "memory_management_mutation")
            put("intent", "memory_management_mutation")
            put("message", prompt)
            put("prompt", prompt)
            put("memoryMode", "auto")
            put("memoryEnabled", true)
            put("memorySchema", "ai_ledger_cloud_memory_request_v3")
            put("memoryManagementRequest", JSONObject().apply {
                put("schema", MEMORY_MANAGEMENT_REQUEST_SCHEMA)
                put("operationId", operationId)
                put("action", action)
                put("targetMemoryId", targetMemoryId.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("content", content.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("category", category)
                put("scope", scope)
                put("priority", priority)
                put("pinned", pinned)
                put("validUntil", validUntil.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("sourceType", sourceType)
                put("confidence", confidence)
                put("namespaceType", namespaceType)
                put("namespaceId", namespaceId)
                put("expectedUpdatedAt", expectedUpdatedAt.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                put("deleteScope", deleteScope)
                put("reason", reason)
            })
        }
    }

    private fun endpointCandidates(endpoint: String): List<String> {
        return if (endpoint.endsWith("/chat") || endpoint.endsWith("/api/chat")) {
            listOf(endpoint)
        } else {
            listOf(endpoint, "$endpoint/chat", "$endpoint/api/chat").distinct()
        }
    }
}

private fun IOException.isRetryableMemoryManagementFailure(): Boolean {
    val text = message.orEmpty().lowercase()
    return cause is SocketTimeoutException ||
        text.contains("timeout") ||
        text.contains("connection reset") ||
        text.contains("failed to connect") ||
        text.contains("unexpected end") ||
        text.contains("http 404") ||
        text.contains("http 408") ||
        text.contains("http 429") ||
        text.contains("http 5")
}
