package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
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

data class CloudAgentAction(
    val capability: String,
    val title: String? = null,
    val goal: String? = null,
    val requiresConfirmation: Boolean = false,
    val reason: String? = null,
    val deviceControlStep: CloudAgentStep? = null,
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
    val searchUsed: Boolean = false,
    val searchProvider: String? = null,
)

private data class AiWorkerRouteScore(
    val model: ChatModel,
    val score: Int,
    val reason: String,
)

class AiWorkerClient(
    private val config: AiWorkerConfig = AiWorkerConfig(),
) {
    val endpoint: String get() = config.endpoint

    private val resolvedClientId: String by lazy {
        config.clientId
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotBlank() }
            ?: AiLedgerApplication.contextOrNull()
                ?.let { context -> AgentClientIdentity.getOrCreateDeviceId(context) }
                ?.take(120)
                ?.takeIf { it.isNotBlank() }
            ?: AI_WORKER_CHAT_CLIENT_NAME
    }

    private val transport: AiWorkerHttpTransport by lazy {
        AiWorkerHttpTransport(
            config = config,
            resolvedClientId = resolvedClientId,
        )
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
                    if (
                        error is SocketTimeoutException ||
                        error.cause is SocketTimeoutException
                    ) {
                        continue@endpointLoop
                    }
                }
            }
        }
        val failure = lastError
            ?: IOException("云端 AI 请求失败，请检查 Worker 配置。")
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
                    if (
                        error is SocketTimeoutException ||
                        error.cause is SocketTimeoutException
                    ) {
                        continue@endpointLoop
                    }
                }
            }
        }
        val failure = lastError
            ?: IOException("云端 AI 流式请求失败，请检查 Worker 配置。")
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

    internal fun buildRequestHeadersForTest(
        stream: Boolean = false,
    ): Map<String, String> {
        return transport.requestHeaders(stream)
    }

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
    ): JSONObject {
        return AiWorkerPayloadBuilder.build(
            messages = messages,
            route = route,
            onlineEnabled = onlineEnabled,
            resolvedClientId = resolvedClientId,
        )
    }

    private fun endpointPool(
        primary: String,
        fallbacks: List<String>,
    ): List<String> {
        return (listOf(primary) + fallbacks)
            .map { endpoint -> endpoint.trim().trimEnd('/') }
            .filter { endpoint -> endpoint.isNotBlank() }
            .distinct()
    }

    private fun endpointPlan(route: AiWorkerModelRoute): List<String> {
        val cn = config.endpoint.trim().trimEnd('/')
        val cf = (
            config.fallbackEndpoints.firstOrNull() ?: CLOUDFLARE_WORKER_ENDPOINT
            ).trim().trimEnd('/')
        val resolvedIsCnModel =
            route.resolved == ChatModel.Kimi ||
                route.resolved == ChatModel.DeepSeekV4
        return if (resolvedIsCnModel) {
            endpointPool(cn, emptyList())
        } else {
            endpointPool(cn, listOf(cf))
        }
    }

    private fun endpointCandidates(cleanEndpoint: String): List<String> {
        return if (
            cleanEndpoint.endsWith("/chat") ||
            cleanEndpoint.endsWith("/api/chat")
        ) {
            listOf(cleanEndpoint)
        } else {
            listOf(
                cleanEndpoint,
                "$cleanEndpoint/chat",
                "$cleanEndpoint/api/chat",
            ).distinct()
        }
    }

    private fun resolveModelRoute(
        messages: List<ChatMessage>,
        modelPreference: ChatModel,
    ): AiWorkerModelRoute {
        if (messages.hasLatestUserImageAttachments()) {
            return AiWorkerModelRoute(
                requested = modelPreference,
                resolved = ChatModel.Kimi,
                reason = "qwen_vision_image_attachment",
            )
        }
        if (modelPreference != ChatModel.Auto) {
            return AiWorkerModelRoute(
                requested = modelPreference,
                resolved = modelPreference,
                reason = "manual_selection",
            )
        }
        val latest = latestUserText(messages)
        val text = latest.lowercase()
        val route = scoreAutoV2(latest, text)
            .maxWithOrNull(
                compareBy<AiWorkerRouteScore> { score -> score.score }
                    .thenBy { score -> autoTieBreakPriority(score.model) },
            )
            ?: AiWorkerRouteScore(ChatModel.Kimi, 1, "qwen_default")
        return AiWorkerModelRoute(
            requested = ChatModel.Auto,
            resolved = route.model,
            reason = "auto_v2:${route.reason}",
        )
    }

    private fun scoreAutoV2(
        latest: String,
        text: String,
    ): List<AiWorkerRouteScore> {
        val codeScore =
            10 * countMatches(text, codeKeywords) +
                7 * countMatches(text, appDevKeywords) +
                if (looksLikeCodeOrError(latest, text)) 18 else 0
        val reasoningScore =
            9 * countMatches(text, reasoningKeywords) +
                11 * countMatches(text, stemKeywords) +
                6 * countMatches(text, designKeywords) +
                if (hasFormulaSignal(latest)) 14 else 0
        val translateScore = 10 * countMatches(text, translationKeywords)
        val longWritingScore =
            8 * countMatches(text, writingKeywords) + when {
                latest.length >= 1600 -> 18
                latest.length >= 900 -> 12
                latest.length >= 420 && hasAny(text, writingKeywords) -> 8
                else -> 0
            }
        val qwenGeneralScore =
            12 +
                (if (containsChinese(latest)) 6 else 0) +
                (if (latest.length < 420) 4 else 0)
        return listOf(
            AiWorkerRouteScore(ChatModel.GptOss, codeScore, "code_android_api"),
            AiWorkerRouteScore(
                ChatModel.DeepSeekV4,
                reasoningScore,
                "reasoning_stem_design",
            ),
            AiWorkerRouteScore(
                ChatModel.Kimi,
                qwenGeneralScore + translateScore + longWritingScore / 2,
                "qwen_general_cn_translation_writing",
            ),
            AiWorkerRouteScore(
                ChatModel.Gemini,
                if (text.contains("gemini")) 16 else translateScore / 2,
                "translation_or_explicit_gemini",
            ),
            AiWorkerRouteScore(
                ChatModel.Mistral,
                longWritingScore,
                "long_summary_polish",
            ),
        )
    }

    private fun autoTieBreakPriority(model: ChatModel): Int = when (model) {
        ChatModel.Kimi -> 5
        ChatModel.DeepSeekV4 -> 4
        ChatModel.GptOss -> 3
        ChatModel.Mistral -> 2
        ChatModel.Gemini -> 1
        ChatModel.Workers -> 0
        ChatModel.Auto -> -1
    }

    private fun looksLikeCodeOrError(
        latest: String,
        text: String,
    ): Boolean {
        return latest.contains("```") ||
            latest.contains("Exception") ||
            latest.contains("Traceback") ||
            latest.contains("NullPointer") ||
            latest.contains("Unresolved reference") ||
            latest.contains("Cannot resolve") ||
            text.contains("build failed") ||
            text.contains("stacktrace") ||
            Regex("\\b(error|failed|exception|fatal):").containsMatchIn(text)
    }

    private fun hasFormulaSignal(text: String): Boolean {
        return text.any { char -> char in listOf('∂', '∫', '∑', '√', 'θ', 'π', '∞') } ||
            Regex("[a-zA-Z][0-9]?\\s*=\\s*[-+]?\\d").containsMatchIn(text) ||
            Regex("\\d+\\s*/\\s*\\d+").containsMatchIn(text)
    }

    private fun latestUserText(messages: List<ChatMessage>): String {
        return messages.lastOrNull {
            message -> message.role == MessageRole.User && message.text.isNotBlank()
        }?.text.orEmpty()
    }

    private fun List<ChatMessage>.hasLatestUserImageAttachments(): Boolean {
        return lastOrNull {
            message ->
                message.role == MessageRole.User &&
                    message.status != MessageStatus.Sending
        }?.hasImageAttachments == true
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { char -> char in '\u4e00'..'\u9fff' }
    }

    private fun countMatches(
        text: String,
        keywords: List<String>,
    ): Int = keywords.count(text::contains)

    private fun hasAny(
        text: String,
        keywords: List<String>,
    ): Boolean = keywords.any(text::contains)

    private val codeKeywords = listOf(
        "代码", "报错", "bug", "修复", "编译", "构建", "函数", "类", "脚本", "依赖",
        "库", "接口", "api", "kotlin", "compose", "android", "github", "gradle",
        "cloudflare", "worker", "python", "java", "javascript", "typescript", "html",
        "css", "json", "http", "request", "response",
    )
    private val appDevKeywords = listOf(
        "app", "apk", "workflow", "actions", "commit", "分支", "仓库", "源码",
        "viewmodel", "client", "repository", "compose 原生",
    )
    private val reasoningKeywords = listOf(
        "推理", "证明", "分析", "为什么", "原理", "思路", "计算", "求解", "推导",
        "判别", "极限", "偏导", "积分", "二重积分", "链式法则", "全微分",
    )
    private val stemKeywords = listOf(
        "数学", "电路", "模电", "数电", "单片机", "stm32", "传感器", "建模", "模型",
        "仿真", "控制", "信号", "滤波", "放大器", "电磁", "物理",
    )
    private val designKeywords = listOf(
        "方案", "设计", "架构", "策略", "优化", "规划", "迁移", "实现思路", "怎么做",
        "怎么设计", "技术路线", "系统设计",
    )
    private val translationKeywords = listOf(
        "什么意思", "翻译", "英文", "英语", "日语", "德语", "怎么读", "读音", "单词",
        "词语", "translate", "meaning", "pronunciation",
    )
    private val writingKeywords = listOf(
        "总结", "概括", "归纳", "提纲", "大纲", "报告", "整理", "润色", "改写", "论文",
        "summary", "summarize", "outline", "polish", "rewrite",
    )

    companion object {
        const val ALIYUN_CN_ENDPOINT =
            "https://" + "ai-ledg-chat-cn-dnuxlrhytb.cn-hangzhou.fcapp.run"
        const val CLOUDFLARE_WORKER_ENDPOINT =
            "https://" + "ai-ledger-parser.552078638.workers.dev"
        const val DEFAULT_ENDPOINT = ALIYUN_CN_ENDPOINT
        val DEFAULT_FALLBACK_ENDPOINTS = listOf(CLOUDFLARE_WORKER_ENDPOINT)
    }
}
