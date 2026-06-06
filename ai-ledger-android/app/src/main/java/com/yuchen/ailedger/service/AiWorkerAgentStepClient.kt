package com.yuchen.ailedger.service

import android.os.SystemClock
import com.yuchen.ailedger.model.ChatModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
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
    val loopIndex = agentMemory?.optJSONObject("loopSignals")?.optIntOrNull("loopIndex") ?: 0
    val sessionId = AgentClientSessionStore.sessionId(cleanGoal, loopIndex)
    return JSONObject().apply {
        put("action", "chat")
        put("intent", "agent_step")
        put("agentMode", true)
        put("computerUseMode", true)
        put("visionFirst", hasScreenshot)
        put("coordinateProtocol", "normalized_screen_0_1")
        put("agentGoal", cleanGoal)
        put("agentSessionId", sessionId)
        put("agentSessionStep", loopIndex)
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
        put("supportsAgentStepBatch", true)
        put("actionBatchMax", CloudAgentPlan.MAX_BATCH_STEPS)
        put("modelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put("clientVersion", if (hasScreenshot) "compose-native-agent-visual-batch-v11-debug" else "compose-native-agent-tool-batch-v11-debug")
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
            put("includeAgentSteps", true)
            put("includeActionBatch", true)
            put("includeStopConditions", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private object AgentClientSessionStore {
    private var lastGoal: String = ""
    private var lastLoopIndex: Int = -1
    private var currentSessionId: String = ""

    @Synchronized
    fun sessionId(goal: String, loopIndex: Int): String {
        val normalizedGoal = goal.trim().lowercase(Locale.ROOT)
        if (currentSessionId.isBlank() || normalizedGoal != lastGoal || loopIndex <= lastLoopIndex) {
            currentSessionId = "android-agent-${System.currentTimeMillis()}"
        }
        lastGoal = normalizedGoal
        lastLoopIndex = loopIndex
        return currentSessionId
    }
}

private fun agentEndpointCandidates(cleanEndpoint: String): List<String> = listOf(cleanEndpoint)

private fun postAgentPlan(endpoint: String, payload: JSONObject): CloudAgentPlan {
    val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
    val requestStart = SystemClock.elapsedRealtime()
    var stage = "prepare"
    var writeMs: Long? = null
    var waitMs: Long? = null
    var readMs: Long? = null
    var responseBytes = 0
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
        stage = "write_request"
        val writeStart = SystemClock.elapsedRealtime()
        connection.outputStream.use { it.write(requestBytes) }
        writeMs = SystemClock.elapsedRealtime() - writeStart

        stage = "wait_status"
        val statusStart = SystemClock.elapsedRealtime()
        val status = connection.responseCode
        waitMs = SystemClock.elapsedRealtime() - statusStart

        stage = "read_body"
        val readStart = SystemClock.elapsedRealtime()
        val body = connection.agentReadBody(status)
        readMs = SystemClock.elapsedRealtime() - readStart
        responseBytes = body.toByteArray(Charsets.UTF_8).size
        val totalMs = SystemClock.elapsedRealtime() - requestStart
        val data = body.agentJsonOrNull()
        AgentRuntimeController.noteDiagnostic(
            buildAgentTimingDiagnostic(
                data = data,
                requestBytes = requestBytes.size,
                responseBytes = responseBytes,
                writeMs = writeMs ?: 0L,
                waitMs = waitMs ?: 0L,
                readMs = readMs ?: 0L,
                totalMs = totalMs,
            )
        )
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体规划失败：HTTP $status" }
            throw IOException(message)
        }
        val plan = CloudAgentPlan.fromJson(data) ?: extractAgentPlanFromText(body)
        if (plan != null) return plan
        val step = CloudAgentStep.fromJson(data) ?: extractAgentStepFromText(body)
            ?: throw IOException("云端没有返回有效的智能体下一步动作")
        val state = CloudAgentState.fromJson(data) ?: extractAgentStateFromText(body)
        CloudAgentPlan(step = step, state = state)
    } catch (error: SocketTimeoutException) {
        val totalMs = SystemClock.elapsedRealtime() - requestStart
        AgentRuntimeController.noteDiagnostic(
            buildAgentTimeoutDiagnostic(
                stage = stage,
                requestBytes = requestBytes.size,
                responseBytes = responseBytes,
                writeMs = writeMs,
                waitMs = waitMs,
                readMs = readMs,
                totalMs = totalMs,
            )
        )
        throw IOException("云端智能体规划超过 ${AGENT_STEP_READ_TIMEOUT_MS / 1000} 秒未返回：${endpoint.substringAfter("://")}", error)
    } finally {
        connection.disconnect()
    }
}

private fun buildAgentTimingDiagnostic(
    data: JSONObject?,
    requestBytes: Int,
    responseBytes: Int,
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
    val textPlannerMs = debug?.optLongOrNull("textPlannerMs")
    val buildMs = debug?.optLongOrNull("buildMessagesMs")
    val parseMs = debug?.optLongOrNull("parseMs")
    val serverTotalMs = debug?.optLongOrNull("totalMs")
    val serverReadBodyMs = debug?.optLongOrNull("readBodyMs")
    val serverRequestKb = debug?.optLongOrNull("requestBytes")?.let { bytesToKb(it) }
    val promptChars = debug?.optLongOrNull("promptChars")
    val screenshotKb = debug?.optLongOrNull("screenshotBytesApprox")?.let { bytesToKb(it) }
    val visualCalled = debug?.optBooleanOrNull("visualCalled")
    val visualCacheHit = debug?.optBooleanOrNull("visualCacheHit")
    val sessionStep = debug?.optLongOrNull("sessionStep")
    val batchCount = data?.optJSONArray("agentSteps")?.length()
        ?: data?.optJSONArray("steps")?.length()
        ?: data?.optJSONObject("plan")?.optJSONArray("agentSteps")?.length()
        ?: data?.optJSONObject("plan")?.optJSONArray("steps")?.length()
    val local = "req=${bytesToKb(requestBytes)}K resp=${bytesToKb(responseBytes)}K http=${totalMs} wait=${waitMs}"
    val server = buildString {
        if (serverTotalMs != null) append(" srv=${serverTotalMs}")
        if (serverReadBodyMs != null) append(" body=${serverReadBodyMs}")
        if (providerMs != null) append(" prov=${providerMs}")
        if (textPlannerMs != null) append(" text=${textPlannerMs}")
        if (serverRequestKb != null) append(" in=${serverRequestKb}K")
        append(" wr=${writeMs} rd=${readMs}")
        if (buildMs != null) append(" build=${buildMs}")
        if (parseMs != null) append(" parse=${parseMs}")
        if (promptChars != null) append(" prompt=${promptChars}")
        if (screenshotKb != null) append(" img=${screenshotKb}K")
        if (visualCalled != null) append(" visual=").append(if (visualCalled) "调" else "免")
        if (visualCacheHit != null && visualCacheHit) append(" cache=命中")
        if (sessionStep != null) append(" s#").append(sessionStep)
        if (batchCount != null && batchCount > 1) append(" batch=").append(batchCount)
        if (version.isNotBlank()) append(" v=").append(version)
        if (debug == null) append(" debug=无")
    }
    return "AgentDebug $local$server"
}

private fun buildAgentTimeoutDiagnostic(
    stage: String,
    requestBytes: Int,
    responseBytes: Int,
    writeMs: Long?,
    waitMs: Long?,
    readMs: Long?,
    totalMs: Long,
): String {
    val estimatedWaitMs = when {
        stage == "wait_status" && writeMs != null -> (totalMs - writeMs).coerceAtLeast(0L)
        else -> null
    }
    return buildString {
        append("AgentDebug 超时 stage=").append(stage.shortStageLabel())
        append(" req=").append(bytesToKb(requestBytes)).append('K')
        if (responseBytes > 0) append(" resp=").append(bytesToKb(responseBytes)).append('K')
        append(" http=").append(totalMs)
        if (writeMs != null) append(" wr=").append(writeMs)
        if (waitMs != null) append(" wait=").append(waitMs) else if (estimatedWaitMs != null) append(" wait>").append(estimatedWaitMs)
        if (readMs != null) append(" rd=").append(readMs)
    }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else ((bytes + 1023) / 1024)
private fun bytesToKb(bytes: Long): Long = if (bytes <= 0L) 0L else ((bytes + 1023L) / 1024L)

private fun String.shortStageLabel(): String = when (this) {
    "prepare" -> "prep"
    "write_request" -> "write"
    "wait_status" -> "wait"
    "read_body" -> "read"
    else -> take(10)
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

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return try { getInt(key) } catch (_: Exception) { optString(key).toIntOrNull() }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return try { getBoolean(key) } catch (_: Exception) {
        when (optString(key).lowercase(Locale.ROOT)) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }
}

private fun extractAgentStepFromText(text: String): CloudAgentStep? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentStep.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

private fun extractAgentPlanFromText(text: String): CloudAgentPlan? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentPlan.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}

private fun extractAgentStateFromText(text: String): CloudAgentState? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { CloudAgentState.fromJson(JSONObject(text.substring(start, end + 1))) } catch (_: Exception) { null }
}
