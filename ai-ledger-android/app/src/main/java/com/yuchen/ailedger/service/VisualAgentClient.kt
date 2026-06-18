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
private const val VISUAL_AGENT_MAX_HISTORY_ITEMS = 4
private const val VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS = 6_000
private const val VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS = 240
private const val VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS = 36
private const val VISUAL_AGENT_MAX_APP_ALIASES = 4
private const val VISUAL_AGENT_MAX_APP_CAPABILITIES = 5
private const val VISUAL_AGENT_MAX_APP_TEXT_CHARS = 80

data class VisualAgentHistoryItem(
    val screenshot: AgentScreenVisual,
    val assistantOutput: String,
    val executionResult: String,
)

data class VisualAgentAppContextItem(
    val label: String,
    val packageName: String,
    val aliases: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
)

@Throws(IOException::class)
fun AiWorkerClient.requestVisualAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String> = emptyList(),
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
): CloudAgentPlan {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank()) throw IOException("AI Worker endpoint is not configured")
    return postVisualAgentStep(endpointBase, buildVisualAgentPayload(goal, snapshot, recentActions, visualHistory, appContext))
}

internal fun buildVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
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
        put("visualHistory", JSONArray().apply {
            visualHistory
                .takeLast(VISUAL_AGENT_MAX_HISTORY_ITEMS)
                .filter { it.screenshot.hasImage && it.assistantOutput.isNotBlank() }
                .forEach { item ->
                    put(JSONObject().apply {
                        put("assistantOutput", item.assistantOutput.take(VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS))
                        put("executionResult", item.executionResult.take(VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS))
                        put("screenshot", item.screenshot.toPayloadJson())
                    })
                }
        })
        put("appContext", JSONArray().apply {
            appContext
                .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
                .take(VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS)
                .forEach { item ->
                    put(JSONObject().apply {
                        put("label", item.label.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS))
                        put("packageName", item.packageName.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS))
                        put("aliases", JSONArray().apply {
                            item.aliases
                                .map { it.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS) }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .take(VISUAL_AGENT_MAX_APP_ALIASES)
                                .forEach { put(it) }
                        })
                        put("capabilities", JSONArray().apply {
                            item.capabilities
                                .map { it.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS) }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .take(VISUAL_AGENT_MAX_APP_CAPABILITIES)
                                .forEach { put(it) }
                        })
                    })
                }
        })
        if (visual?.hasImage == true) {
            put("screenshot", visual.toPayloadJson())
        }
        put("coordinateProtocol", "normalized_screen_0_1")
        put("client", "android-compose")
        put("clientVersion", "visual-agent-direct-v3-app-context")
        put("now", System.currentTimeMillis())
    }
}

private fun AgentScreenVisual.toPayloadJson(): JSONObject {
    return JSONObject().apply {
        put("mimeType", mimeType)
        put("base64Data", base64Jpeg)
        put("width", width)
        put("height", height)
        put("displayWidth", displayWidth)
        put("displayHeight", displayHeight)
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
