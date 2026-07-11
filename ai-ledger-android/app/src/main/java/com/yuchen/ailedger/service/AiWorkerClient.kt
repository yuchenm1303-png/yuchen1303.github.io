package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageContentBlock
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.WebSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private const val CLIENT_TOOL_RESULT_MARKER = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]"
private const val VISUAL_CLIENT_TOOL_CALL_TTL_MS = 30_000L
private const val MAX_PENDING_VISUAL_CLIENT_TOOL_CALLS = 8

data class AiWorkerConfig(
    val endpoint: String = AiWorkerClient.DEFAULT_ENDPOINT,
    val fallbackEndpoints: List<String> = AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS,
    val connectTimeoutMs: Int = AI_WORKER_DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = AI_WORKER_DEFAULT_READ_TIMEOUT_MS,
    val clientId: String? = null,
    val clientAuthToken: String? = AiWorkerRequestIdentity.defaultAppClientToken(),
    val userAccessTokenProvider: (() -> String?)? = null,
)

data class CloudMobileAction(
    val type: String,
    val title: String? = null,
    val destination: String? = null,
    val appName: String? = null,
    val packageName: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val label: String? = null,
)

data class CloudPreferenceUpdate(
    val type: String,
    val slot: String,
    val label: String,
    val value: String,
)

data class CloudClientToolCall(
    val schema: String,
    val id: String,
    val name: String,
    val arguments: JSONObject,
    val resultProtocol: String = AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL,
    val riskLevel: String = "low",
    val requiresConfirmation: Boolean = false,
    val reason: String? = null,
    val originalUserGoal: String? = null,
    val finalModel: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", schema)
        put("id", id)
        put("name", name)
        put("arguments", JSONObject(arguments.toString()))
        put("resultProtocol", resultProtocol)
        put("riskLevel", riskLevel)
        put("requiresConfirmation", requiresConfirmation)
        reason?.let { put("reason", it) }
        originalUserGoal?.let { put("originalUserGoal", it) }
        finalModel?.let { put("finalModel", it) }
    }
}

data class CloudAgentAction(
    val capability: String,
    val title: String? = null,
    val goal: String? = null,
    val requiresConfirmation: Boolean = false,
    val reason: String? = null,
    val deviceControlStep: CloudAgentStep? = null,
    val clientToolCall: CloudClientToolCall? = null,
)

data class AiChatResponse(
    val reply: String,
    val source: String = "cloud_ai",
    val model: String? = null,
    val modelLabel: String? = null,
    val version: String? = null,
    val webSources: List<WebSource> = emptyList(),
    val structuredData: StructuredDataCard? = null,
    val contentBlocks: List<MessageContentBlock> = emptyList(),
    val mobileAction: CloudMobileAction? = null,
    val preferenceUpdate: CloudPreferenceUpdate? = null,
    val agentAction: CloudAgentAction? = null,
    val clientToolCall: CloudClientToolCall? = null,
    val searchUsed: Boolean = false,
    val searchProvider: String? = null,
    val stickerDiagnosticsJson: String? = null,
)

private data class PendingVisualClientToolCall(
    val call: CloudClientToolCall,
    val goalKey: String,
    val registeredAt: Long,
)

class AiWorkerClient(
    private val config: AiWorkerConfig = AiWorkerConfig(),
) {
    val endpoint: String get() = resolvedPrimaryEndpoint()

    private val resolvedClientId: String by lazy {
        config.clientId
            ?.trim()
            ?.take(120)
            ?.takeIf(String::isNotBlank)
            ?: AiLedgerApplication.contextOrNull()
                ?.let { context -> AgentClientIdentity.getOrCreateDeviceId(context) }
                ?.take(120)
                ?.takeIf(String::isNotBlank)
            ?: AI_WORKER_CHAT_CLIENT_NAME
    }

    private val transport: AiWorkerHttpTransport by lazy {
        AiWorkerHttpTransport(config = config, resolvedClientId = resolvedClientId)
    }
    private val visualClientToolCallLock = Any()
    private val pendingVisualClientToolCalls = mutableListOf<PendingVisualClientToolCall>()

    fun cancelActiveRequests() {
        transport.cancelActiveRequests()
        clearVisualClientToolCalls()
    }

    @Throws(IOException::class)
    fun sendChat(
        messages: List<ChatMessage>,
        modelPreference: ChatModel = ChatModel.Auto,
        onlineEnabled: Boolean = false,
    ): AiChatResponse {
        val route = resolveModelRoute(messages, modelPreference)
        val endpoints = endpointPlan(route)
        if (endpoints.isEmpty()) throw IOException("AI Worker endpoint 未配置")
        val payload = buildPayload(messages, route, onlineEnabled)
        var lastError: IOException? = null
        endpointLoop@ for (cleanEndpoint in endpoints) {
            for (candidate in endpointCandidates(cleanEndpoint)) {
                try {
                    val rawResponse = transport.postChat(candidate, payload, route)
                    AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)
                    val completedResponse = completeClientToolCallIfNeeded(rawResponse, modelPreference) ?: rawResponse
                    return rememberVisualClientToolCall(completedResponse)
                } catch (error: IOException) {
                    lastError = error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) {
                        continue@endpointLoop
                    }
                }
            }
        }
        val failure = lastError ?: IOException("云端 AI 请求失败，请检查 Worker 配置。")
        AssistantMemoryUsageBridge.recordFailedPayload(payload, failure)
        throw failure
    }

    @Throws(IOException::class)
    suspend fun streamChat(
        messages: List<ChatMessage>,
        modelPreference: ChatModel = ChatModel.Auto,
        onlineEnabled: Boolean = false,
        onDelta: (String) -> Unit,
    ): AiChatResponse = coroutineScope {
        val completed = AtomicBoolean(false)
        val cancellationWatcher = launch(Dispatchers.IO) {
            try {
                awaitCancellation()
            } finally {
                if (!completed.get()) transport.cancelActiveRequests()
            }
        }
        try {
            streamChatBlocking(
                messages = messages,
                modelPreference = modelPreference,
                onlineEnabled = onlineEnabled,
                onDelta = onDelta,
            )
        } finally {
            completed.set(true)
            cancellationWatcher.cancel()
        }
    }

    private fun streamChatBlocking(
        messages: List<ChatMessage>,
        modelPreference: ChatModel,
        onlineEnabled: Boolean,
        onDelta: (String) -> Unit,
    ): AiChatResponse {
        val route = resolveModelRoute(messages, modelPreference)
        val endpoints = endpointPlan(route)
        if (endpoints.isEmpty()) throw IOException("云端 AI 流式请求失败，请检查网络或 Worker 配置。")
        val payload = buildPayload(messages, route, onlineEnabled).apply {
            put("stream", true)
            put("streaming", true)
            put("streamFormat", "sse")
            put("responseMode", "stream")
        }
        var lastError: IOException? = null
        endpointLoop@ for (cleanEndpoint in endpoints) {
            for (candidate in endpointCandidates(cleanEndpoint)) {
                try {
                    val rawResponse = transport.postStreamChat(
                        endpoint = candidate,
                        payload = payload,
                        route = route,
                        onDelta = onDelta,
                    )
                    AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)
                    val completedResponse = completeClientToolCallIfNeeded(rawResponse, modelPreference) ?: rawResponse
                    return rememberVisualClientToolCall(completedResponse)
                } catch (error: IOException) {
                    lastError = error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) {
                        continue@endpointLoop
                    }
                }
            }
        }
        val failure = lastError ?: IOException("云端 AI 流式请求失败，请检查网络或 Worker 配置。")
        AssistantMemoryUsageBridge.recordFailedPayload(payload, failure)
        throw failure
    }

    internal fun buildChatPayloadForTest(
        messages: List<ChatMessage>,
        modelPreference: ChatModel = ChatModel.Auto,
        onlineEnabled: Boolean = false,
    ): JSONObject {
        val route = resolveModelRoute(messages, modelPreference)
        return buildPayload(messages, route, onlineEnabled)
    }

    internal fun buildRequestHeadersForTest(stream: Boolean = false): Map<String, String> =
        transport.requestHeaders(stream)

    internal fun applyRequestIdentityHeaders(
        connection: HttpURLConnection,
        stream: Boolean = false,
    ) {
        transport.applyRequestIdentityHeaders(connection, stream)
    }

    internal fun rememberVisualClientToolCall(response: AiChatResponse): AiChatResponse {
        val action = response.agentAction
        val call = action
            ?.takeIf { it.capability == "run_agent_task" }
            ?.clientToolCall
            ?.takeIf { it.name == "computer_run_task" && it.id.isNotBlank() }
            ?: return response
        val now = System.currentTimeMillis()
        val pending = PendingVisualClientToolCall(
            call = call,
            goalKey = call.visualGoalKey(),
            registeredAt = now,
        )
        synchronized(visualClientToolCallLock) {
            pruneExpiredVisualClientToolCalls(now)
            pendingVisualClientToolCalls.removeAll { it.call.id == call.id }
            pendingVisualClientToolCalls += pending
            while (pendingVisualClientToolCalls.size > MAX_PENDING_VISUAL_CLIENT_TOOL_CALLS) {
                pendingVisualClientToolCalls.removeAt(0)
            }
        }
        return response
    }

    internal fun consumeVisualClientToolCall(goal: String? = null): CloudClientToolCall? =
        synchronized(visualClientToolCallLock) {
            val now = System.currentTimeMillis()
            pruneExpiredVisualClientToolCalls(now)
            val requestedGoalKey = normalizeVisualClientToolGoal(goal.orEmpty())
            val matchIndex = when {
                requestedGoalKey.isNotBlank() -> pendingVisualClientToolCalls.indexOfLast {
                    it.goalKey == requestedGoalKey
                }
                pendingVisualClientToolCalls.size == 1 -> 0
                else -> -1
            }
            if (matchIndex < 0) return@synchronized null

            val selected = pendingVisualClientToolCalls.removeAt(matchIndex)
            if (selected.goalKey.isNotBlank()) {
                pendingVisualClientToolCalls.removeAll { it.goalKey == selected.goalKey }
            }
            selected.call
        }

    internal fun clearVisualClientToolCalls(callId: String? = null) {
        synchronized(visualClientToolCallLock) {
            if (callId.isNullOrBlank()) {
                pendingVisualClientToolCalls.clear()
            } else {
                pendingVisualClientToolCalls.removeAll { it.call.id == callId }
            }
        }
    }

    private fun pruneExpiredVisualClientToolCalls(now: Long) {
        pendingVisualClientToolCalls.removeAll {
            now - it.registeredAt > VISUAL_CLIENT_TOOL_CALL_TTL_MS
        }
    }

    private fun CloudClientToolCall.visualGoalKey(): String {
        val toolGoal = arguments.optString("goal").trim()
        return normalizeVisualClientToolGoal(toolGoal.ifBlank { originalUserGoal.orEmpty() })
    }

    private fun normalizeVisualClientToolGoal(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "")
            .take(300)
    }

    private fun buildPayload(
        messages: List<ChatMessage>,
        route: AiWorkerModelRoute,
        onlineEnabled: Boolean,
    ): JSONObject = AiWorkerPayloadBuilder.build(
        messages = messages,
        route = route,
        onlineEnabled = onlineEnabled,
        resolvedClientId = resolvedClientId,
    )

    private fun completeClientToolCallIfNeeded(
        response: AiChatResponse,
        modelPreference: ChatModel,
    ): AiChatResponse? {
        return completeProjectClientToolCallIfNeeded(response, modelPreference)
            ?: completePlanClientToolCallIfNeeded(response, modelPreference)
            ?: completeDeviceClientToolCallIfNeeded(response, modelPreference)
    }

    private fun completeProjectClientToolCallIfNeeded(
        response: AiChatResponse,
        modelPreference: ChatModel,
    ): AiChatResponse? {
        val call = response.clientToolCall ?: return null
        if (!ProjectClientToolExecutor.isProjectTool(call.name)) return null
        val app = AiLedgerApplication.contextOrNull() ?: return null
        val receipt = ProjectClientToolExecutor(app).execute(call, response.reply)
        return sendClientToolResultForFinalReply(call, receipt, response, modelPreference)
    }

    private fun completePlanClientToolCallIfNeeded(
        response: AiChatResponse,
        modelPreference: ChatModel,
    ): AiChatResponse? {
        val call = response.clientToolCall ?: return null
        if (!PlanClientToolExecutor.isPlanTool(call.name)) return null
        val app = AiLedgerApplication.contextOrNull() ?: return null
        val receipt = PlanClientToolExecutor(app).execute(call, response.reply)
        return sendClientToolResultForFinalReply(call, receipt, response, modelPreference)
    }

    private fun completeDeviceClientToolCallIfNeeded(
        response: AiChatResponse,
        modelPreference: ChatModel,
    ): AiChatResponse? {
        val call = response.clientToolCall ?: return null
        val action = response.agentAction ?: return null
        if (action.capability != "run_device_control") return null
        val step = action.deviceControlStep ?: DeviceControlRouter.fromClientToolCall(call.toJson()) ?: return null
        return completeDeviceClientToolCall(call, action, step, response, modelPreference)
    }

    private fun completeDeviceClientToolCall(
        call: CloudClientToolCall,
        action: CloudAgentAction,
        step: CloudAgentStep,
        response: AiChatResponse,
        modelPreference: ChatModel,
    ): AiChatResponse? {
        val app = AiLedgerApplication.contextOrNull() ?: return null
        val goal = call.originalUserGoal
            ?.takeIf(String::isNotBlank)
            ?: action.goal?.takeIf(String::isNotBlank)
            ?: call.arguments.optString("goal").takeIf(String::isNotBlank)
            ?: response.reply
        val receipt = baseClientToolReceipt(call, goal)
        val executor = DeviceToolExecutor(app)
        if (!executor.canExecute(step)) {
            receipt.put("status", "unsupported")
            receipt.put("completed", false)
            receipt.put("handled", false)
            receipt.put("resultSummary", "Android 当前不支持云端选择的客户端工具：${step.type}。")
            receipt.put("actions", JSONArray().apply {
                put(deviceToolActionReceipt(call, step, null, "unsupported", "Android 当前不支持云端选择的客户端工具：${step.type}。"))
            })
            return sendClientToolResultForFinalReply(call, receipt, response, modelPreference)
        }

        val requiresConfirmation = call.requiresConfirmation || action.requiresConfirmation || AgentSafetyPolicy.requiresConfirmation(goal, step)
        val confirmedHighRisk = if (requiresConfirmation) {
            runBlocking { AgentRuntimeController.requestRiskConfirmation(goal, step) }
        } else {
            false
        }
        if (requiresConfirmation && !confirmedHighRisk) {
            receipt.put("status", "cancelled")
            receipt.put("completed", false)
            receipt.put("handled", true)
            receipt.put("stoppedForConfirmation", true)
            receipt.put("resultSummary", "该客户端工具需要用户确认，但确认流程未完成，所以没有执行。")
            receipt.put("actions", JSONArray().apply {
                put(deviceToolActionReceipt(call, step, null, "cancelled", "用户没有确认该客户端工具调用。"))
            })
            return sendClientToolResultForFinalReply(call, receipt, response, modelPreference)
        }

        val raw = executor.execute(step, confirmedHighRisk = confirmedHighRisk)
        val verified = DeviceControlActionVerifier(app).verify(step, raw)
        val status = when {
            verified.ok -> "verified"
            raw.ok -> "state_mismatch"
            else -> "failed"
        }
        val summary = verified.message.ifBlank { raw.message }
        receipt.put("status", status)
        receipt.put("completed", verified.ok)
        receipt.put("handled", true)
        receipt.put("resultSummary", summary.take(1_800))
        receipt.put("actions", JSONArray().apply {
            put(deviceToolActionReceipt(call, step, verified, status, summary))
        })
        return sendClientToolResultForFinalReply(call, receipt, response, modelPreference)
    }

    private fun baseClientToolReceipt(call: CloudClientToolCall, goal: String): JSONObject = JSONObject().apply {
        put("protocol", call.resultProtocol)
        put("toolCallId", call.id)
        put("toolName", call.name)
        put("toolArguments", JSONObject(call.arguments.toString()))
        put("finalModel", call.finalModel ?: "")
        put("goal", goal.trim().take(300))
        put("stoppedForConfirmation", false)
    }

    private fun deviceToolActionReceipt(
        call: CloudClientToolCall,
        step: CloudAgentStep,
        execution: AgentExecutionResult?,
        status: String,
        detail: String,
    ): JSONObject = JSONObject().apply {
        put("tool", step.type)
        put("toolLabel", step.typeLabel)
        put("requestedArgs", step.toolArgs?.let { JSONObject(it.toString()) } ?: JSONObject())
        put("riskLevel", call.riskLevel)
        put("requiresConfirmation", call.requiresConfirmation)
        put("appName", step.appName.orEmpty())
        put("packageName", step.packageName.orEmpty())
        put("status", status)
        put("ok", execution?.ok == true)
        put("verified", status == "verified")
        put("shouldContinue", execution?.shouldContinue == true)
        put("technicalDetail", detail.take(1_800))
        put("undoAvailable", false)
    }

    private fun sendClientToolResultForFinalReply(
        call: CloudClientToolCall,
        receipt: JSONObject,
        originalResponse: AiChatResponse,
        modelPreference: ChatModel,
    ): AiChatResponse {
        val resolvedModel = call.finalModel
            ?.takeIf(String::isNotBlank)
            ?.let(ChatModel::fromId)
            ?: modelPreference
        val marker = "$CLIENT_TOOL_RESULT_MARKER$receipt"
        return runCatching {
            sendChat(
                messages = listOf(
                    ChatMessage(
                        id = "client-tool-result-${call.id}",
                        text = marker,
                        role = MessageRole.User,
                    ),
                ),
                modelPreference = resolvedModel,
                onlineEnabled = false,
            )
        }.getOrElse { error ->
            originalResponse.copy(
                reply = "客户端工具已返回结构化结果，但最终模型续写失败：${error.message.orEmpty().take(120)}",
                source = "client_tool_result_report_failed",
                agentAction = null,
                clientToolCall = null,
                mobileAction = null,
            )
        }
    }

    private fun endpointPool(primary: String, fallbacks: List<String>): List<String> =
        (listOf(primary) + fallbacks)
            .map { endpoint -> endpoint.trim().trimEnd('/') }
            .filter(String::isNotBlank)
            .distinct()

    private fun resolvedPrimaryEndpoint(): String {
        val configuredEndpoint = config.endpoint.trim().trimEnd('/')
        val managedEndpoints = setOf(
            DEFAULT_ENDPOINT.trim().trimEnd('/'),
            ALIYUN_CN_ENDPOINT.trim().trimEnd('/'),
        )
        if (configuredEndpoint.isNotBlank() && configuredEndpoint !in managedEndpoints) {
            return configuredEndpoint
        }
        return BackendEndpointStore.currentEndpointOrDefault(
            configuredEndpoint.ifBlank { DEFAULT_ENDPOINT }
        ).trim().trimEnd('/')
    }

    private fun endpointPlan(route: AiWorkerModelRoute): List<String> {
        val cn = resolvedPrimaryEndpoint()
        val cf = (config.fallbackEndpoints.firstOrNull() ?: CLOUDFLARE_WORKER_ENDPOINT)
            .trim()
            .trimEnd('/')
        val resolvedIsCnModel = route.isAuto ||
            route.resolved == ChatModel.Kimi ||
            route.resolved == ChatModel.DeepSeekV4
        return if (resolvedIsCnModel) endpointPool(cn, emptyList()) else endpointPool(cn, listOf(cf))
    }

    private fun endpointCandidates(cleanEndpoint: String): List<String> {
        return if (cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")) {
            listOf(cleanEndpoint)
        } else {
            listOf(cleanEndpoint, "$cleanEndpoint/chat", "$cleanEndpoint/api/chat").distinct()
        }
    }

    /**
     * Local routing is modality/transport only. Natural-language model selection belongs to the
     * cloud Final Chat Model. Auto therefore always reaches the CN gateway as `auto`.
     */
    private fun resolveModelRoute(
        messages: List<ChatMessage>,
        modelPreference: ChatModel,
    ): AiWorkerModelRoute {
        if (messages.hasLatestUserImageAttachments()) {
            return AiWorkerModelRoute(
                requested = modelPreference,
                resolved = ChatModel.Kimi,
                reason = "qwen_vision_image_transport",
            )
        }
        if (modelPreference == ChatModel.Auto) {
            return AiWorkerModelRoute(
                requested = ChatModel.Auto,
                resolved = ChatModel.Kimi,
                reason = "cloud_final_model_auto",
            )
        }
        return AiWorkerModelRoute(
            requested = modelPreference,
            resolved = modelPreference,
            reason = "manual_selection",
        )
    }

    private fun List<ChatMessage>.hasLatestUserImageAttachments(): Boolean =
        lastOrNull { message ->
            message.role == com.yuchen.ailedger.model.MessageRole.User &&
                message.status != com.yuchen.ailedger.model.MessageStatus.Sending
        }?.hasImageAttachments == true

    companion object {
        val ALIYUN_CN_ENDPOINT: String = AI_WORKER_ALIYUN_CN_ENDPOINT
        val TENCENT_SERVER_ENDPOINT: String = AI_WORKER_TENCENT_SERVER_ENDPOINT
        val CLOUDFLARE_WORKER_ENDPOINT: String = AI_WORKER_CLOUDFLARE_WORKER_ENDPOINT
        val DEFAULT_ENDPOINT: String = TENCENT_SERVER_ENDPOINT
        val DEFAULT_FALLBACK_ENDPOINTS = listOf(CLOUDFLARE_WORKER_ENDPOINT)
    }
}
