package com.yuchen.ailedger.service

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_AGENT_CONNECT_TIMEOUT_MS = 8_000
private const val VISUAL_AGENT_READ_TIMEOUT_MS = 12_000
private const val VISUAL_AGENT_MAX_RECENT_ACTIONS = 6
private const val VISUAL_AGENT_MAX_RECENT_ACTION_CHARS = 160

@Throws(IOException::class)
fun AiWorkerClient.requestVisualAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String> = emptyList(),
): CloudAgentPlan {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank()) throw IOException("AI Worker endpoint is not configured")
    return postVisualAgentStep(endpointBase, buildVisualAgentPayload(goal, snapshot, recentActions))
}

internal fun buildVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
): JSONObject {
    val visual = snapshot.visual
    return JSONObject().apply {
        put("action", "visual_agent_step")
        put("requestType", "visual_agent_step")
        put("visualAgentDirect", true)
        put("goal", goal.trim().take(240))
        put("currentPackage", snapshot.currentApp)
        put("recentActions", JSONArray().apply {
            recentActions
                .takeLast(VISUAL_AGENT_MAX_RECENT_ACTIONS)
                .map { it.trim().take(VISUAL_AGENT_MAX_RECENT_ACTION_CHARS) }
                .filter { it.isNotBlank() }
                .forEach { put(it) }
        })
        if (visual?.hasImage == true) {
            put("screenshot", JSONObject().apply {
                put("mimeType", visual.mimeType)
                put("base64Data", visual.base64Jpeg)
                put("width", visual.width)
                put("height", visual.height)
                put("displayWidth", visual.displayWidth)
                put("displayHeight", visual.displayHeight)
            })
        }
        put("coordinateProtocol", "normalized_screen_0_1")
        put("client", "android-compose")
        put("clientVersion", "visual-agent-direct-v1")
        put("now", System.currentTimeMillis())
    }
}

private fun postVisualAgentStep(endpoint: String, payload: JSONObject): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = VISUAL_AGENT_CONNECT_TIMEOUT_MS
        readTimeout = VISUAL_AGENT_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-agent-direct")
    }
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(requestBytes) }
        val status = connection.responseCode
        val body = connection.visualAgentReadBody(status)
        val data = body.visualAgentJsonOrNull()
        AgentRuntimeController.noteDiagnostic(
            "VisualDirect q=${visualAgentBytesToKb(requestBytes.size)}K r=${visualAgentBytesToKb(body.length)}K h=${SystemClock.elapsedRealtime() - requestStart}",
        )
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "visual_agent_step HTTP $status" }
            throw IOException(message)
        }
        CloudAgentPlan.fromJson(data)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
            ?: throw IOException("visual_agent_step did not return one agentStep")
    } catch (error: SocketTimeoutException) {
        throw IOException("visual_agent_step timed out after ${VISUAL_AGENT_READ_TIMEOUT_MS / 1000}s", error)
    } finally {
        connection.disconnect()
    }
}

private fun HttpURLConnection.visualAgentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.visualAgentJsonOrNull(): JSONObject? {
    return try {
        takeIf { it.isNotBlank() }?.let { JSONObject(it) }
    } catch (_: Exception) {
        null
    }
}

private fun visualAgentBytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else ((bytes + 1023) / 1024)
