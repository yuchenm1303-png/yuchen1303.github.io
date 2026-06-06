package com.yuchen.ailedger.service

import android.os.SystemClock
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val AGENT_STEP_CONNECT_TIMEOUT_MS = 8_000
private const val AGENT_STEP_READ_TIMEOUT_MS = 18_000
private const val AGENT_VISION_ROUTE_ID = "qwen_vision"

@Throws(IOException::class)
fun AiWorkerClient.requestAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
): CloudAgentStep {
    return requestAgentPlan(goal, snapshot, modelPreference).step
}

@Throws(IOException::class)
fun AiWorkerClient.requestAgentPlan(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
    recentActions: List<String> = emptyList(),
    deviceContext: AgentDeviceContextSnapshot? = null,
    agentMemory: JSONObject? = null,
): CloudAgentPlan {
    val payload = buildAgentStepPayload(goal, snapshot, modelPreference, recentActions, deviceContext, agentMemory)
    val endpoints = listOf(endpoint.trim().trimEnd('/')).filter { it.isNotBlank() }.distinct()
    var lastError: IOException? = null
    for (base in endpoints) {
        for (candidate in agentEndpointCandidates(base)) {
            try {
                return postAgentPlan(candidate, payload)
            } catch (error: IOException) {
                lastError = error
                if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
            }
        }
    }
    throw lastError ?: IOException("云端智能体规划请求失败")
}

private fun buildAgentStepPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel,
    recentActions: List<String>,
    deviceContext: AgentDeviceContextSnapshot?,
    agentMemory: JSONObject?,
): JSONObject {
    val cleanGoal = goal.trim().take(240)
    val hasScreenshot = snapshot.hasVisualImage
    val modelId = if (hasScreenshot) AGENT_VISION_ROUTE_ID else if (modelPreference == ChatModel.Auto) ChatModel.Kimi.id else modelPreference.id
    val snapshotWithoutImage = snapshot.toJson(includeImage = false)
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_step")
        put("agentMode", true)
        put("computerUseMode", true)
        put("visionFirst", hasScreenshot)
        put("coordinateProtocol", "normalized_screen_0_1")
        put("agentGoal", cleanGoal)
        put("message", cleanGoal)
        put("recentAgentActions", JSONArray().apply { recentActions.takeLast(8).forEach { put(it) } })
        agentMemory?.let { put("agentMemory", it) }
        deviceContext?.let { put("deviceContext", it.json) }
        put("screenSnapshot", snapshotWithoutImage)
        put("hasScreenshot", hasScreenshot)
        put("hasImage", hasScreenshot)
        put("hasImages", false)
        put("imageCount", if (hasScreenshot) 1 else 0)
        snapshot.visual?.takeIf { it.hasImage }?.let { visual ->
            put("screenshot", JSONObject().apply {
                put("mimeType", visual.mimeType)
                put("base64Data", visual.base64Jpeg)
                put("width", visual.width)
                put("height", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
                put("source", visual.source)
                put("reason", visual.reason)
            })
        }
        put("vision", JSONObject().apply {
            put("enabled", hasScreenshot)
            put("provider", "qwen")
            put("route", AGENT_VISION_ROUTE_ID)
            put("coordinateSystem", "normalized_screen_0_1")
        })
        put("supportedAgentSteps", JSONArray(CloudAgentStep.supportedTypes.toList()))
        put("modelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put("clientVersion", if (hasScreenshot) "compose-native-agent-fast-json-v8" else "compose-native-agent-tool-only-v8")
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun agentEndpointCandidates(cleanEndpoint: String): List<String> = listOf(cleanEndpoint)

private fun postAgentPlan(endpoint: String, payload: JSONObject): CloudAgentPlan {
    val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_STEP_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_STEP_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent")
    }
    return try {
        val writeStart = SystemClock.elapsedRealtime()
        connection.outputStream.use { it.write(requestBytes) }
        val writeMs = SystemClock.elapsedRealtime() - writeStart

        val statusStart = SystemClock.elapsedRealtime()
        val status = connection.responseCode
        val waitMs = SystemClock.elapsedRealtime() - statusStart

        val readStart = SystemClock.elapsedRealtime()
        val body = connection.agentReadBody(status)
        val readMs = SystemClock.elapsedRealtime() - readStart
        val totalMs = SystemClock.elapsedRealtime() - requestStart
        val data = body.agentJsonOrNull()
        AgentRuntimeController.noteDiagnostic(buildAgentTimingDiagnostic(data, requestBytes.size, writeMs, waitMs, readMs, totalMs))
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体规划失败：HTTP $status" }
            throw IOException(message)
        }
        val step = CloudAgentStep.fromJson(data) ?: extractAgentStepFromText(body)
            ?: throw IOException("云端没有返回有效的智能体下一步动作")
        val state = CloudAgentState.fromJson(data) ?: extractAgentStateFromText(body)
        CloudAgentPlan(step = step, state = state)
    } catch (error: SocketTimeoutException) {
        val totalMs = SystemClock.elapsedRealtime() - requestStart
        AgentRuntimeController.noteDiagnostic("AgentDebug 超时 · http=${totalMs}ms · req=${requestBytes.size / 1024}KB")
        throw IOException("云端智能体规划超过 ${AGENT_STEP_READ_TIMEOUT_MS / 1000} 秒未返回：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun buildAgentTimingDiagnostic(
    data: JSONObject?,
    requestBytes: Int,
    writeMs: Long,
    waitMs: Long,
    readMs: Long,
    totalMs: Long,
): String {
    val debug = data?.optJSONObject("debug")
    val version = data?.optString("version").orEmpty()
        .replace("qwen-deepseek-cn-web-data-", "")
        .replace("agent-", "")
        .take(24)
    val providerMs = debug?.optLongOrNull("providerMs")
    val buildMs = debug?.optLongOrNull("buildMessagesMs")
    val parseMs = debug?.optLongOrNull("parseMs")
    val promptChars = debug?.optLongOrNull("promptChars")
    val screenshotKb = debug?.optLongOrNull("screenshotBytesApprox")?.let { it / 1024 }
    val local = "req=${requestBytes / 1024}KB http=${totalMs}ms wait=${waitMs}ms read=${readMs}ms"
    val server = buildString {
        if (providerMs != null) append(" prov=${providerMs}ms")
        if (buildMs != null) append(" build=${buildMs}ms")
        if (parseMs != null) append(" parse=${parseMs}ms")
        if (promptChars != null) append(" prompt=${promptChars}")
        if (screenshotKb != null) append(" img=${screenshotKb}KB")
        if (version.isNotBlank()) append(" v=").append(version)
        if (debug == null) append(" debug=无")
    }
    return "AgentDebug $local$server"
}

private fun HttpURLConnection.agentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.agentJsonOrNull(): JSONObject? {
    return try { takeIf { it.isNotBlank() }?.let { JSONObject(it) } } catch (_: Exception) { null }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return try { getLong(key) } catch (_: Exception) { optString(key).toLongOrNull() }
}

private fun extractAgentStepFromText(text: String): CloudAgentStep? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentStep.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

private fun extractAgentStateFromText(text: String): CloudAgentState? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentState.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}
