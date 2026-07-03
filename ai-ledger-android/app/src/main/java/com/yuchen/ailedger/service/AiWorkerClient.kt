package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.StructuredDataCard
import com.yuchen.ailedger.model.WebSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import org.json.JSONObject

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
    val mobileAction: CloudMobileAction? = null,
    val preferenceUpdate: CloudPreferenceUpdate? = null,
    val agentAction: CloudAgentAction? = null,
    val clientToolCall: CloudClientToolCall? = null,
    val searchUsed: Boolean = false,
    val searchProvider: String? = null,
)

class AiWorkerClient(
    private val config: AiWorkerConfig = AiWorkerConfig(),
) {
    val endpoint: String get() = config.endpoint

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
                    val response = transport.postChat(candidate, payload, route)
                    AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)
                    return response
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
    fun streamChat(
        messages: List<ChatMessage>,
        modelPreference: ChatModel = ChatModel.Auto,
        onlineEnabled: Boolean = false,
        onDelta: (String) -> Unit,
    ): AiChatResponse {
        val route = resolveModelRoute(messages, modelPreference)
        val endpoints = endpointPlan(route)
        if (endpoints.isEmpty()) throw IOException("AI Worker endpoint 未配置")
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
                    val response = transport.postStreamChat(
                        endpoint = candidate,
                        payload = payload,
                        route = route,
                        onDelta = onDelta,
                    )
                    AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)
                    return response
                } catch (error: IOException) {
                    lastError = error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) {
                        continue@endpointLoop
                    }
                }
            }
        }
        val failure = lastError ?: IOException("云端 AI 流式请求失败，请检查 Worker 配置。")
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

    private fun endpointPool(primary: String, fallbacks: List<String>): List<String> =
        (listOf(primary) + fallbacks)
            .map { endpoint -> endpoint.trim().trimEnd('/') }
            .filter(String::isNotBlank)
            .distinct()

    private fun endpointPlan(route: AiWorkerModelRoute): List<String> {
        val cn = config.endpoint.trim().trimEnd('/')
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
        const val ALIYUN_CN_ENDPOINT =
            "https://" + "ai-ledg-chat-cn-dnuxlrhytb.cn-hangzhou.fcapp.run"
        const val CLOUDFLARE_WORKER_ENDPOINT =
            "https://" + "ai-ledger-parser.552078638.workers.dev"
        const val DEFAULT_ENDPOINT = ALIYUN_CN_ENDPOINT
        val DEFAULT_FALLBACK_ENDPOINTS = listOf(CLOUDFLARE_WORKER_ENDPOINT)
    }
}
