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

private const val DEFAULT_TIMEOUT_MS = 30_000

data class AiWorkerConfig(
    val endpoint: String = AiWorkerClient.DEFAULT_ENDPOINT,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS
)

data class AiChatResponse(
    val reply: String,
    val source: String = "cloud_ai",
    val model: String? = null,
    val modelLabel: String? = null,
    val version: String? = null
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
        val cleanEndpoint = config.endpoint.trim().trimEnd('/')
        if (cleanEndpoint.isBlank()) throw IOException("AI Worker endpoint 未配置")

        val payload = JSONObject().apply {
            put("messages", messages.toWorkerMessages())
            put("modelPreference", modelPreference.id)
            put("aiModelPreference", modelPreference.id)
            put("requestedModelPreference", modelPreference.id)
            put("onlineEnabled", onlineEnabled)
            put("webSearch", onlineEnabled)
            put("client", "android-compose")
            put("now", System.currentTimeMillis())
        }

        val connection = (URL(cleanEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutMs
            readTimeout = config.timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val body = readBody(connection, status)
            val data = body.takeIf { it.isNotBlank() }?.let { JSONObject(it) } ?: JSONObject()

            if (status !in 200..299) {
                val code = data.optString("code").ifBlank { "HTTP $status" }
                val message = data.optString("error")
                    .ifBlank { data.optString("message") }
                    .ifBlank { "云端 AI 调用失败：$code" }
                throw IOException(message)
            }

            val reply = data.optString("reply")
                .ifBlank { data.optString("response") }
                .ifBlank { data.optString("text") }
                .trim()

            if (reply.isBlank()) throw IOException("云端没有返回有效回复")

            val rawModel = data.optString("model").takeIf { it.isNotBlank() }
            val rawVersion = data.optString("version").takeIf { it.isNotBlank() }
            val rawModelLabel = data.optString("modelLabel")
                .ifBlank { rawModel.orEmpty() }
                .ifBlank { modelPreference.label }

            AiChatResponse(
                reply = reply,
                source = data.optString("source").ifBlank { "cloud_ai" },
                model = rawModel,
                modelLabel = rawModelLabel,
                version = rawVersion
            )
        } catch (error: SocketTimeoutException) {
            throw IOException("云端 AI 请求超时，请稍后再试", error)
        } finally {
            connection.disconnect()
        }
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

    companion object {
        const val DEFAULT_ENDPOINT = "https://ai-ledger-parser.552078638.workers.dev"
    }
}
