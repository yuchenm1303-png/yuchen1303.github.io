package com.yuchen.ailedger.service

import com.yuchen.ailedger.AiLedgerApplication
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val MEMORY_SELECTOR_CLIENT = "android-compose-cloud-memory"
private const val MEMORY_SELECTOR_MODEL = "deepseek_v4"
private const val MEMORY_SELECTOR_CONNECT_TIMEOUT_MS = 10_000
private const val MEMORY_SELECTOR_READ_TIMEOUT_MS = 18_000

internal object CloudMemorySelectionTransport {
    fun select(
        userText: String,
        candidates: List<CloudMemoryCandidate>,
        phase: String,
        selectionLimit: Int,
    ): CloudMemorySelectionResult {
        if (candidates.isEmpty()) return CloudMemorySelectionResult("empty")
        val prompt = buildCloudMemorySelectorPrompt(
            userText = userText,
            candidates = candidates,
            phase = phase,
            selectionLimit = selectionLimit,
        )
        val payload = buildPayload(prompt)
        var lastError: IOException? = null
        endpointCandidates().forEach { endpoint ->
            try {
                val response = post(endpoint, payload)
                val reply = response.optString("reply")
                    .ifBlank { response.optString("response") }
                    .ifBlank { response.optString("answer") }
                    .ifBlank { response.optString("text") }
                if (reply.isBlank()) throw IOException("cloud_selector_empty_reply")
                return parseCloudMemorySelectionReply(reply, candidates, selectionLimit)
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("cloud_selector_request_failed")
    }

    private fun buildPayload(prompt: String): JSONObject = JSONObject().apply {
        put("action", "chat")
        put("intent", "memory_select")
        put("message", prompt)
        put("prompt", prompt)
        put("text", prompt)
        put("content", prompt)
        put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        put("modelPreference", MEMORY_SELECTOR_MODEL)
        put("aiModelPreference", MEMORY_SELECTOR_MODEL)
        put("requestedModelPreference", MEMORY_SELECTOR_MODEL)
        put("model", MEMORY_SELECTOR_MODEL)
        put("modelId", MEMORY_SELECTOR_MODEL)
        put("originalModelPreference", MEMORY_SELECTOR_MODEL)
        put("autoRequested", false)
        put("onlineEnabled", false)
        put("searchEnabled", false)
        put("forceWebSearch", false)
        put("webSearchMode", "off")
        put("searchMode", "off")
        put("allowModelCommands", false)
        put("memoryEnabled", false)
        put("memorySchema", CLOUD_MEMORY_SCHEMA)
        put("chatExpressionPreferences", JSONObject()
            .put("schema", "ai_ledger_chat_expression_preferences_v1")
            .put("inlineStickerFrequency", 0)
            .put("inlineStickerIntensity", 0)
            .put("inlineStickerMaxPerReply", 0)
            .put("inlineStickerRepeatCount", 1))
        put("normalChatDeviceToolProbe", JSONObject()
            .put("enabled", false)
            .put("supportedDeviceToolSteps", JSONArray())
            .put("installedApps", JSONArray()))
        put("commandProtocol", JSONObject()
            .put("enabled", false)
            .put("allowModelCommands", false)
            .put("supportedAgentActions", JSONArray())
            .put("supportedDeviceToolSteps", JSONArray()))
        put("responseFormat", JSONObject()
            .put("includeSources", false)
            .put("includeStructuredData", false)
            .put("includeMobileAction", false)
            .put("includePreferenceUpdate", false)
            .put("includeAgentAction", false)
            .put("includeEmbeddedCommandMarker", false)
            .put("allowModelCommands", false))
        put("client", MEMORY_SELECTOR_CLIENT)
        put("clientId", clientId())
        put("deviceId", clientId())
        put("clientVersion", "compose-cloud-memory-selector-v2")
        put("now", System.currentTimeMillis())
    }

    private fun post(endpoint: String, payload: JSONObject): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = MEMORY_SELECTOR_CONNECT_TIMEOUT_MS
            readTimeout = MEMORY_SELECTOR_READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/plain")
            setRequestProperty("X-Client", MEMORY_SELECTOR_CLIENT)
            setRequestProperty("X-Client-Id", clientId())
            setRequestProperty("X-Device-Id", clientId())
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (status !in 200..299) throw IOException("cloud_selector_http_$status")
            runCatching { JSONObject(text) }
                .getOrElse { throw IOException("cloud_selector_invalid_response", it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun endpointCandidates(): List<String> {
        val base = AiWorkerClient.DEFAULT_ENDPOINT.trim().trimEnd('/')
        return listOf(base, "$base/chat", "$base/api/chat").distinct()
    }

    private fun clientId(): String {
        val context = AiLedgerApplication.contextOrNull() ?: return MEMORY_SELECTOR_CLIENT
        return AgentClientIdentity.getOrCreateDeviceId(context).take(120).ifBlank { MEMORY_SELECTOR_CLIENT }
    }
}
