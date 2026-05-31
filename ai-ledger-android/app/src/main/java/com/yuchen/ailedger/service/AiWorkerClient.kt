package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
private const val DEFAULT_READ_TIMEOUT_MS = 45_000

data class AiWorkerConfig(
    val endpoint: String = AiWorkerClient.DEFAULT_ENDPOINT,
    val fallbackEndpoints: List<String> = AiWorkerClient.DEFAULT_FALLBACK_ENDPOINTS,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
)

data class AiChatResponse(
    val reply: String,
    val source: String = "cloud_ai",
    val model: String? = null,
    val modelLabel: String? = null,
    val version: String? = null
)

private data class ModelRoute(
    val requested: ChatModel,
    val resolved: ChatModel,
    val reason: String
) {
    val isAuto: Boolean get() = requested == ChatModel.Auto
}

private data class RouteScore(
    val model: ChatModel,
    val score: Int,
    val reason: String
)

class AiWorkerClient(
    private val config: AiWorkerConfig = AiWorkerConfig()
) {
    val endpoint: String
        get() = config.endpoint

    @Throws(IOException::class)
    fun sendChat(
        messages: List<ChatMessage>,
        modelPreference: ChatModel = ChatModel.Auto,
        onlineEnabled: Boolean = false
    ): AiChatResponse {
        val route = resolveModelRoute(messages, modelPreference)
        val endpoints = endpointPlan()
        if (endpoints.isEmpty()) throw IOException("AI Worker endpoint 未配置")

        val payload = buildPayload(messages, route, onlineEnabled)
        var lastError: IOException? = null

        endpointLoop@ for (cleanEndpoint in endpoints) {
            for (candidate in endpointCandidates(cleanEndpoint)) {
                try {
                    return postChat(candidate, payload, route)
                } catch (error: IOException) {
                    lastError = error
                    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) {
                        continue@endpointLoop
                    }
                }
            }
        }

        throw lastError ?: IOException("云端 AI 请求失败，请检查 Worker 配置。")
    }

    private fun endpointPool(primary: String, fallbacks: List<String>): List<String> {
        return (listOf(primary) + fallbacks)
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * 入口选择策略：
     * - Android 客户端统一优先请求阿里云国内入口，保证大陆网络下基础聊天稳定可用。
     * - Qwen / DeepSeek 由国内入口优先承接。
     * - Gemini / Mistral / GPT OSS / Workers AI 也先交给国内入口尝试代理；如果国内入口不支持或不可用，再 fallback 到 Cloudflare。
     * - Cloudflare 继续作为海外模型和海外网络环境的备用通道，不因为国内入口优先而被删除。
     * - 联网按钮只作为 payload 能力标记传给后端，不直接改变 App 端入口顺序。
     */
    private fun endpointPlan(): List<String> {
        val cn = config.endpoint.trim().trimEnd('/')
        val cf = (config.fallbackEndpoints.firstOrNull() ?: CLOUDFLARE_WORKER_ENDPOINT)
            .trim()
            .trimEnd('/')

        return endpointPool(cn, listOf(cf))
    }

    private fun resolveModelRoute(
        messages: List<ChatMessage>,
        modelPreference: ChatModel
    ): ModelRoute {
        if (modelPreference != ChatModel.Auto) {
            return ModelRoute(modelPreference, modelPreference, "manual_selection")
        }

        val latest = latestUserText(messages)
        val text = latest.lowercase()

        val route = scoreAutoV2(latest, text).maxWithOrNull(
            compareBy<RouteScore> { it.score }
                .thenBy { autoTieBreakPriority(it.model) }
        ) ?: RouteScore(ChatModel.Kimi, 1, "qwen_default")

        return ModelRoute(ChatModel.Auto, route.model, "auto_v2:${route.reason}")
    }

    private fun scoreAutoV2(latest: String, text: String): List<RouteScore> {
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
            8 * countMatches(text, writingKeywords) +
                when {
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
            RouteScore(ChatModel.GptOss, codeScore, "code_android_api"),
            RouteScore(ChatModel.DeepSeekV4, reasoningScore, "reasoning_stem_design"),
            RouteScore(
                ChatModel.Kimi,
                qwenGeneralScore + translateScore + longWritingScore / 2,
                "qwen_general_cn_translation_writing"
            ),
            RouteScore(
                ChatModel.Gemini,
                if (text.contains("gemini")) 16 else translateScore / 2,
                "translation_or_explicit_gemini"
            ),
            RouteScore(ChatModel.Mistral, longWritingScore, "long_summary_polish")
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

    private fun looksLikeCodeOrError(latest: String, text: String): Boolean {
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
        return text.any { it in listOf('∂', '∫', '∑', '√', 'θ', 'π', '∞') } ||
            Regex("[a-zA-Z][0-9]?\\s*=\\s*[-+]?\\d").containsMatchIn(text) ||
            Regex("\\d+\\s*/\\s*\\d+").containsMatchIn(text)
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { it in '\u4e00'..'\u9fff' }
    }

    private fun countMatches(text: String, keywords: List<String>): Int {
        return keywords.count { text.contains(it) }
    }

    private fun hasAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private val codeKeywords = listOf(
        "代码", "报错", "bug", "修复", "编译", "构建", "函数", "类", "脚本", "依赖", "库", "接口", "api",
        "kotlin", "compose", "android", "github", "gradle", "cloudflare", "worker", "python", "java",
        "javascript", "typescript", "html", "css", "json", "http", "request", "response"
    )

    private val appDevKeywords = listOf(
        "app", "apk", "workflow", "actions", "commit", "分支", "仓库", "源码", "viewmodel",
        "client", "repository", "compose 原生"
    )

    private val reasoningKeywords = listOf(
        "推理", "证明", "分析", "为什么", "原理", "思路", "计算", "求解", "推导", "判别",
        "极限", "偏导", "积分", "二重积分", "链式法则", "全微分"
    )

    private val stemKeywords = listOf(
        "数学", "电路", "模电", "数电", "单片机", "stm32", "传感器", "建模", "模型",
        "仿真", "控制", "信号", "滤波", "放大器", "电磁", "物理"
    )

    private val designKeywords = listOf(
        "方案", "设计", "架构", "策略", "优化", "规划", "迁移", "实现思路", "怎么做",
        "怎么设计", "技术路线", "系统设计"
    )

    private val translationKeywords = listOf(
        "什么意思", "翻译", "英文", "英语", "日语", "德语", "怎么读", "读音", "单词",
        "词语", "translate", "meaning", "pronunciation"
    )

    private val writingKeywords = listOf(
        "总结", "概括", "归纳", "提纲", "大纲", "报告", "整理", "润色", "改写", "论文",
        "summary", "summarize", "outline", "polish", "rewrite"
    )

    private fun buildPayload(
        messages: List<ChatMessage>,
        route: ModelRoute,
        onlineEnabled: Boolean
    ): JSONObject {
        val workerMessages = messages.toWorkerMessages()
        val latestUserText = latestUserText(messages)
        val resolvedId = route.resolved.id
        val searchMode = if (onlineEnabled) "force" else "off"

        return JSONObject().apply {
            put("action", "chat")
            put("intent", "chat")
            put("messages", workerMessages)
            put("message", latestUserText)
            put("prompt", latestUserText)
            put("text", latestUserText)
            put("content", latestUserText)
            put("modelPreference", resolvedId)
            put("aiModelPreference", resolvedId)
            put("requestedModelPreference", resolvedId)
            put("model", resolvedId)
            put("modelId", resolvedId)
            put("legacyModelPreference", if (route.resolved == ChatModel.Kimi) "kimi" else resolvedId)
            put("originalModelPreference", route.requested.id)
            put("autoRequested", route.isAuto)
            put("autoResolvedModel", resolvedId)
            put("autoRouteReason", route.reason)

            put("onlineEnabled", onlineEnabled)
            put("searchEnabled", onlineEnabled)
            put("forceWebSearch", onlineEnabled)
            put("webSearchMode", searchMode)
            put("searchMode", searchMode)
            put("webSearch", JSONObject().apply {
                put("mode", searchMode)
                put("force", onlineEnabled)
                put("requireCitationsWhenForced", true)
                put("keepAutoSearchWhenOff", false)
            })
            put("accessPolicy", "cn_gateway_primary")
            put("primaryEndpointRole", "aliyun_cn_gateway")
            put("fallbackEndpointRole", "cloudflare_worker")

            put("client", "android-compose")
            put("clientVersion", "compose-native-cn-gateway-primary-v1")
            put("now", System.currentTimeMillis())
        }
    }

    private fun endpointCandidates(cleanEndpoint: String): List<String> {
        val knownChatPath =
            cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")

        if (knownChatPath) return listOf(cleanEndpoint)

        return listOf(
            cleanEndpoint,
            "$cleanEndpoint/chat",
            "$cleanEndpoint/api/chat"
        ).distinct()
    }

    private fun postChat(
        endpoint: String,
        payload: JSONObject,
        route: ModelRoute
    ): AiChatResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/plain")
            setRequestProperty("X-Client", "android-compose")
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val body = readBody(connection, status)
            val data = body.toJsonOrNull()

            if (status !in 200..299) {
                val code = data?.optString("code")?.ifBlank { "HTTP $status" } ?: "HTTP $status"
                val message = data?.optString("error")
                    ?.ifBlank { data.optString("message") }
                    ?.ifBlank { null }
                    ?: body.take(120).ifBlank { "云端 AI 调用失败：$code" }

                throw IOException(message)
            }

            throwIfServerReturnedFallbackSignal(data)

            val reply = extractReply(data, body).trim()
            if (reply.isBlank()) throw IOException("云端没有返回有效回复")

            val rawModel =
                data?.optString("model")?.takeIf { it.isNotBlank() }
                    ?: data?.optString("modelId")?.takeIf { it.isNotBlank() }
                    ?: route.resolved.id

            val rawVersion = data?.optString("version")?.takeIf { it.isNotBlank() }

            val serverLabel = data?.optString("modelLabel")
                ?.ifBlank { data.optString("modelName") }
                ?.ifBlank { null }

            val resolvedLabel = serverLabel ?: modelLabelFromId(rawModel) ?: route.resolved.label
            val displayLabel =
                if (route.isAuto && !resolvedLabel.startsWith("自动选择")) {
                    "自动选择 · $resolvedLabel"
                } else {
                    resolvedLabel
                }

            AiChatResponse(
                reply = reply,
                source = data?.optString("source")?.ifBlank { "cloud_ai" } ?: "cloud_ai",
                model = rawModel,
                modelLabel = displayLabel,
                version = rawVersion
            )
        } catch (error: SocketTimeoutException) {
            throw IOException("云端 AI 请求超时：${endpoint.substringAfter("://")}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun throwIfServerReturnedFallbackSignal(data: JSONObject?) {
        if (data == null) return

        val code = data.optString("code").lowercase()
        val error = data.optString("error")
            .ifBlank { data.optString("message") }
        val normalized = "$code $error".lowercase()

        val shouldFallback =
            data.optBoolean("unsupportedModel", false) ||
                data.optBoolean("shouldFallback", false) ||
                normalized.contains("unsupported") ||
                normalized.contains("not supported") ||
                normalized.contains("not_configured") ||
                normalized.contains("model_not_available") ||
                normalized.contains("provider_not_available") ||
                normalized.contains("不支持") ||
                normalized.contains("未配置") ||
                normalized.contains("不可用")

        if (shouldFallback) {
            throw IOException(error.ifBlank { "当前入口不支持该模型，正在尝试备用入口。" })
        }
    }

    private fun extractReply(data: JSONObject?, body: String): String {
        if (data == null) return body

        return data.optString("reply")
            .ifBlank { data.optString("response") }
            .ifBlank { data.optString("answer") }
            .ifBlank { data.optString("text") }
            .ifBlank { data.optString("content") }
            .ifBlank { data.optJSONObject("data")?.optString("reply").orEmpty() }
            .ifBlank { data.optJSONObject("result")?.optString("reply").orEmpty() }
            .ifBlank { data.optJSONObject("result")?.optString("text").orEmpty() }
    }

    private fun latestUserText(messages: List<ChatMessage>): String {
        return messages
            .lastOrNull { it.role == MessageRole.User && it.text.isNotBlank() }
            ?.text
            .orEmpty()
    }

    private fun modelLabelFromId(modelId: String?): String? {
        if (modelId.isNullOrBlank()) return null
        return ChatModel.fromId(modelId).takeIf { it != ChatModel.Auto }?.label
    }

    private fun List<ChatMessage>.toWorkerMessages(): JSONArray {
        val recent = filter { message ->
            when (message.role) {
                MessageRole.User -> message.text.isNotBlank() && message.status != MessageStatus.Sending
                MessageRole.Assistant -> message.isCloudAssistantContextMessage()
            }
        }.takeLast(16)

        val clean = recent.dropWhile { it.role != MessageRole.User }

        return JSONArray().apply {
            clean.forEach { message ->
                put(JSONObject().apply {
                    put("role", if (message.role == MessageRole.User) "user" else "assistant")
                    put("content", message.text)
                })
            }
        }
    }

    private fun ChatMessage.isCloudAssistantContextMessage(): Boolean {
        if (text.isBlank()) return false
        if (status != MessageStatus.Sent) return false

        return when (source) {
            null, "", "local", "local_ledger", "local_mobile", "cloud_fetch_failed", "cloud_error_normalized" -> false
            else -> true
        }
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream =
            if (status in 200..299) connection.inputStream else connection.errorStream

        return stream
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
    }

    private fun String.toJsonOrNull(): JSONObject? {
        return try {
            takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ALIYUN_CN_ENDPOINT =
            "https://ai-ledg-chat-cn-dnuxlrhytb.cn-hangzhou.fcapp.run"

        const val CLOUDFLARE_WORKER_ENDPOINT =
            "https://ai-ledger-parser.552078638.workers.dev"

        const val DEFAULT_ENDPOINT = ALIYUN_CN_ENDPOINT

        val DEFAULT_FALLBACK_ENDPOINTS = listOf(CLOUDFLARE_WORKER_ENDPOINT)
    }
}
