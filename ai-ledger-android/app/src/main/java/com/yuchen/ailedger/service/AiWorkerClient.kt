package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
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
        val cleanEndpoint = config.endpoint.trim().trimEnd('/')
        if (cleanEndpoint.isBlank()) throw IOException("AI Worker endpoint 未配置")

        val route = resolveModelRoute(messages, modelPreference, onlineEnabled)
        val payload = buildPayload(messages, route, onlineEnabled)
        val candidates = endpointCandidates(cleanEndpoint)
        var lastError: IOException? = null

        for (candidate in candidates) {
            try {
                return postChat(candidate, payload, route)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
            }
        }

        throw lastError ?: IOException("云端 AI 请求失败，请检查 Worker 配置。")
    }

    private fun resolveModelRoute(messages: List<ChatMessage>, modelPreference: ChatModel, onlineEnabled: Boolean): ModelRoute {
        if (modelPreference != ChatModel.Auto) {
            return ModelRoute(modelPreference, modelPreference, "manual_selection")
        }

        val latest = latestUserText(messages)
        val text = latest.lowercase()
        val resolved = when {
            onlineEnabled -> ChatModel.Gemini
            hasAny(text, listOf("什么意思", "翻译", "英文", "英语", "日语", "德语", "怎么读", "读音", "单词", "词语", "translate", "meaning")) -> ChatModel.Gemini
            latest.length >= 900 || (latest.length >= 220 && hasAny(text, listOf("总结", "概括", "归纳", "提纲", "大纲", "报告", "整理", "润色", "改写", "summary", "summarize", "outline"))) -> ChatModel.Mistral
            hasAny(text, listOf("代码", "报错", "kotlin", "compose", "android", "github", "gradle", "python", "java", "javascript", "html", "css", "接口", "api", "worker")) -> ChatModel.GptOss
            hasAny(text, listOf("方案", "规划", "架构", "策略", "推理", "分析", "为什么", "怎么设计", "建模", "论文", "电路", "证明", "优化", "迁移", "实现思路", "数学", "计算")) -> ChatModel.DeepSeekV4
            else -> ChatModel.Workers
        }
        return ModelRoute(ChatModel.Auto, resolved, "auto_v1")
    }

    private fun hasAny(text: String, keywords: List<String>): Boolean = keywords.any { text.contains(it) }

    private fun buildPayload(messages: List<ChatMessage>, route: ModelRoute, onlineEnabled: Boolean): JSONObject {
        val workerMessages = messages.toWorkerMessages()
        val latestUserText = latestUserText(messages)
        val resolvedId = route.resolved.id
        return JSONObject().apply {
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
            put("originalModelPreference", route.requested.id)
            put("autoRequested", route.isAuto)
            put("autoResolvedModel", resolvedId)
            put("autoRouteReason", route.reason)
            put("onlineEnabled", onlineEnabled)
            put("webSearch", onlineEnabled)
            put("searchEnabled", onlineEnabled)
            put("client", "android-compose")
            put("clientVersion", "compose-native-text-v1")
            put("now", System.currentTimeMillis())
        }
    }

    private fun endpointCandidates(cleanEndpoint: String): List<String> {
        val knownChatPath = cleanEndpoint.endsWith("/chat") || cleanEndpoint.endsWith("/api/chat")
        if (knownChatPath) return listOf(cleanEndpoint)
        return listOf("$cleanEndpoint/chat", "$cleanEndpoint/api/chat", cleanEndpoint).distinct()
    }

    private fun postChat(endpoint: String, payload: JSONObject, route: ModelRoute): AiChatResponse {
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
            connection.outputStream.use { stream -> stream.write(payload.toString().toByteArray(Charsets.UTF_8)) }
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

            val reply = extractReply(data, body).trim()
            if (reply.isBlank()) throw IOException("云端没有返回有效回复")

            val rawModel = data?.optString("model")?.takeIf { it.isNotBlank() }
                ?: data?.optString("modelId")?.takeIf { it.isNotBlank() }
                ?: route.resolved.id
            val rawVersion = data?.optString("version")?.takeIf { it.isNotBlank() }
            val serverLabel = data?.optString("modelLabel")
                ?.ifBlank { data.optString("modelName") }
                ?.ifBlank { null }
            val resolvedLabel = serverLabel ?: modelLabelFromId(rawModel) ?: route.resolved.label
            val displayLabel = if (route.isAuto && !resolvedLabel.startsWith("自动选择")) "自动选择 · $resolvedLabel" else resolvedLabel

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
        return messages.lastOrNull { it.role == MessageRole.User && it.text.isNotBlank() }?.text.orEmpty()
    }

    private fun modelLabelFromId(modelId: String?): String? {
        if (modelId.isNullOrBlank()) return null
        return ChatModel.fromId(modelId).takeIf { it != ChatModel.Auto }?.label
    }

    private fun List<ChatMessage>.toWorkerMessages(): JSONArray {
        val recent = filter { it.role == MessageRole.User || it.role == MessageRole.Assistant }
            .filter { it.text.isNotBlank() && it.status.name != "Sending" }
            .takeLast(16)
        return JSONArray().apply {
            recent.forEach { message ->
                put(JSONObject().apply {
                    put("role", if (message.role == MessageRole.User) "user" else "assistant")
                    put("content", message.text)
                })
            }
        }
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun String.toJsonOrNull(): JSONObject? {
        return try {
            takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://ai-ledger-parser.552078638.workers.dev"
    }
}
