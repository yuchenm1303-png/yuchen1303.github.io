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
private const val AGENT_STEP_READ_TIMEOUT_MS = 20_000
private const val AGENT_VISION_ROUTE_ID = "qwen_vision"
private const val AGENT_SESSION_PROTOCOL = "android_agent_step_v4_no_task_contract"

@Throws(IOException::class)
fun AiWorkerClient.requestAgentStep(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
    executionMode: AgentExecutionMode = AgentExecutionMode.VisualForce,
): CloudAgentStep = requestAgentPlan(goal, snapshot, modelPreference, executionMode = executionMode).step

@Throws(IOException::class)
fun AiWorkerClient.requestAgentPlan(
    goal: String,
    snapshot: AgentScreenSnapshot,
    modelPreference: ChatModel = ChatModel.Auto,
    recentActions: List<String> = emptyList(),
    deviceContext: AgentDeviceContextSnapshot? = null,
    agentMemory: JSONObject? = null,
    executionMode: AgentExecutionMode = AgentExecutionMode.VisualForce,
): CloudAgentPlan {
    val payload = buildAgentStepPayload(
        goal = goal,
        snapshot = snapshot,
        modelPreference = modelPreference,
        recentActions = recentActions,
        deviceContext = deviceContext,
        agentMemory = agentMemory,
        executionMode = executionMode,
    )
    var lastError: IOException? = null
    val candidates = listOf(endpoint.trim().trimEnd('/'))
        .filter { candidate -> candidate.isNotBlank() }
        .distinct()
    for (candidate in candidates) {
        try {
            return postAgentPlan(candidate, payload)
        } catch (error: IOException) {
            lastError = error
            if (error is SocketTimeoutException || error.cause is SocketTimeoutException) break
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
    executionMode: AgentExecutionMode,
): JSONObject {
    val cleanGoal = goal.trim().take(240)
    val forceVisual = executionMode != AgentExecutionMode.NormalChatDeviceTool
    val hasVisualPayload = snapshot.hasVisualImage && forceVisual
    val isNormalChatToolProbe = executionMode == AgentExecutionMode.NormalChatDeviceTool
    val modeKey = when (executionMode) {
        AgentExecutionMode.NormalChatDeviceTool -> "normal_chat_device_tool"
        AgentExecutionMode.VisualForce -> "visual_force"
        AgentExecutionMode.ExplicitAgent -> "explicit_agent"
    }
    val modelId = when {
        hasVisualPayload -> AGENT_VISION_ROUTE_ID
        isNormalChatToolProbe -> ChatModel.DeepSeekV4.id
        modelPreference == ChatModel.Auto -> ChatModel.Kimi.id
        else -> modelPreference.id
    }
    val loopSignals = agentMemory?.optJSONObject("loopSignals")
    val postActionFeedback = loopSignals?.optJSONObject("postActionFeedback")
    val loopIndex = loopSignals?.optIntOrNull("loopIndex") ?: 0
    val noProgressCount = postActionFeedback?.optIntOrNull("noProgressCount") ?: 0
    val blockedActionCount = postActionFeedback?.optJSONArray("blockedActionSignatures")?.length() ?: 0
    val lastVerification = postActionFeedback?.optString("lastVerification").orEmpty().lowercase(Locale.ROOT)
    val routeRefreshRequested = forceVisual && (
        noProgressCount > 0 || blockedActionCount > 0 || lastVerification.contains("no_progress") ||
            recentActions.takeLast(3).any { action ->
                action.contains("visual_no_progress", ignoreCase = true) ||
                    action.contains("paused_for_user_takeover", ignoreCase = true) ||
                    action.contains("postActionFeedbackReset", ignoreCase = true)
            }
        )
    val sessionId = loopSignals?.optString("agentSessionId")
        ?.takeIf { value -> value.isNotBlank() }
        ?: "android-agent-step-${System.currentTimeMillis()}"

    return JSONObject().apply {
        put("action", "agent_step")
        put("intent", "agent_step")
        put("type", "agent_step")
        put("requestType", "agent_step")
        put("agentStepRequest", true)
        put("agentMode", true)
        put("computerUseMode", forceVisual)
        put("forceVisualAgent", forceVisual)
        put("allowInternalDeviceTools", true)
        put("normalChatDeviceToolMode", isNormalChatToolProbe)
        put("executionMode", modeKey)
        put("visionFirst", hasVisualPayload)
        put("coordinateProtocol", "normalized_screen_0_1")
        put("agentGoal", cleanGoal)
        put("goal", cleanGoal)
        put("message", cleanGoal)
        put("agentSessionId", sessionId)
        put("agentSessionProtocol", AGENT_SESSION_PROTOCOL)
        put("agentSessionStep", loopIndex)
        put("taskContractRequired", false)
        put("taskContractOwner", "visual_agent_only")
        put("fixedStepLimit", isNormalChatToolProbe)
        put("maxAgentSteps", if (isNormalChatToolProbe) 2 else JSONObject.NULL)
        put("routeRefreshRequested", routeRefreshRequested)
        put("invalidateCachedAgentBrainRoute", routeRefreshRequested)
        put("replanAfterSettingsEntry", true)
        put("crossActionNoProgressAccumulation", false)
        put("recentAgentActions", JSONArray().apply {
            recentActions.takeLast(10).forEach { action -> put(action) }
        })
        agentMemory?.let { memory -> put("agentMemory", memory) }
        deviceContext?.let { contextSnapshot ->
            put("deviceContext", contextSnapshot.json)
            contextSnapshot.json.optString("appInventoryHash")
                .takeIf { hash -> hash.isNotBlank() }
                ?.let { hash -> put("appInventoryHash", hash) }
        }
        put("screenSnapshot", snapshot.toJson(includeImage = false))
        put("hasScreenshot", hasVisualPayload)
        put("hasImage", hasVisualPayload)
        put("hasImages", hasVisualPayload)
        put("imageCount", if (hasVisualPayload) 1 else 0)
        if (hasVisualPayload) {
            snapshot.visual?.takeIf { visual -> visual.hasImage }?.let { visual ->
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
        }
        put("vision", JSONObject().apply {
            put("enabled", hasVisualPayload)
            put("provider", "qwen")
            put("route", AGENT_VISION_ROUTE_ID)
            put("coordinateSystem", "normalized_screen_0_1")
        })
        put("supportedAgentSteps", JSONArray(CloudAgentStep.supportedTypes.toList()))
        put("supportedDeviceTools", JSONArray(CloudAgentStep.deviceToolTypes.toList()))
        put("supportsAgentStepBatch", false)
        put("actionBatchMax", 1)
        put("modelPreference", modelId)
        put("model", modelId)
        put("modelId", modelId)
        put("client", "android-compose")
        put(
            "clientVersion",
            if (hasVisualPayload) "compose-native-agent-visual-v17-clean"
            else "compose-native-agent-tool-v17-clean",
        )
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
            put("includeAgentSteps", true)
            put("includeActionBatch", true)
            put("includeStopConditions", true)
            put("includePerformanceDebug", true)
            put("includeTaskExecutionContract", false)
            put("includeTargetAppResolutionAck", false)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun postAgentPlan(endpoint: String, payload: JSONObject): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_STEP_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_STEP_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent-v17-clean")
        setRequestProperty("X-Agent-Session-Protocol", AGENT_SESSION_PROTOCOL)
    }
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { output -> output.write(requestBytes) }
        val status = connection.responseCode
        val body = connection.agentReadBody(status)
        val data = body.agentJsonOrNull()
        AgentRuntimeController.noteDiagnostic(
            buildCompactAgentDiagnostic(
                data = data,
                requestBytes = requestBytes.size,
                responseChars = body.length,
                totalMs = SystemClock.elapsedRealtime() - requestStart,
            ),
        )
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { value -> value.isNotBlank() }
                ?: data?.optString("message")?.takeIf { value -> value.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体规划失败：HTTP $status" }
            throw IOException(message)
        }
        CloudAgentPlan.fromJson(data)
            ?: extractAgentPlanFromText(body)
            ?: CloudAgentStep.fromJson(data)?.let { step ->
                CloudAgentPlan(step = step, state = CloudAgentState.fromJson(data))
            }
            ?: extractAgentStepFromText(body)?.let { step ->
                CloudAgentPlan(step = step, state = extractAgentStateFromText(body))
            }
            ?: throw IOException("云端没有返回有效的智能体下一步动作")
    } catch (error: SocketTimeoutException) {
        throw IOException(
            "云端智能体规划超过 ${AGENT_STEP_READ_TIMEOUT_MS / 1000} 秒未返回：${endpoint.substringAfter("://")}",
            error,
        )
    } finally {
        connection.disconnect()
    }
}

private fun buildCompactAgentDiagnostic(
    data: JSONObject?,
    requestBytes: Int,
    responseChars: Int,
    totalMs: Long,
): String {
    val debug = data?.optJSONObject("debug")
    val step = data?.optJSONObject("agentStep")?.optString("type").orEmpty().take(8)
    val clientContextMs = debug?.optLongOrZero("clientDeviceContextMs") ?: 0L
    val bodyMs = debug?.optLongOrZero("readBodyMs") ?: 0L
    val brainMs = debug?.optLongOrZero("agentBrainMs") ?: 0L
    val visionMs = debug?.optLongOrZero("providerMs") ?: 0L
    val serverTotalMs = debug?.optLongOrZero("totalMs") ?: 0L
    val cold = debug?.optLongOrZero("coldStart") ?: 0L
    return buildString {
        append("D q").append(bytesToKb(requestBytes))
        append(" r").append(bytesToKb(responseChars))
        append(" h").append(totalMs)
        if (step.isNotBlank()) append(" s").append(step)
        append(" | c").append(clientContextMs)
        append(" b").append(bodyMs)
        append(" n").append(brainMs)
        append(" v").append(visionMs)
        append(" t").append(serverTotalMs)
        append(" C").append(cold)
    }
}

private fun JSONObject.optLongOrZero(key: String): Long {
    if (!has(key) || isNull(key)) return 0L
    return runCatching { getLong(key) }
        .getOrElse { optString(key).toDoubleOrNull()?.toLong() ?: 0L }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else ((bytes + 1023) / 1024)

private fun HttpURLConnection.agentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
}

private fun String.agentJsonOrNull(): JSONObject? {
    if (isBlank()) return null
    return try {
        JSONObject(this)
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return try {
        getInt(key)
    } catch (_: Exception) {
        optString(key).toIntOrNull()
    }
}

private fun extractAgentStepFromText(text: String): CloudAgentStep? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        CloudAgentStep.fromJson(JSONObject(text.substring(start, end + 1)))
    }.getOrNull()
}

private fun extractAgentPlanFromText(text: String): CloudAgentPlan? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        CloudAgentPlan.fromJson(JSONObject(text.substring(start, end + 1)))
    }.getOrNull()
}

private fun extractAgentStateFromText(text: String): CloudAgentState? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        CloudAgentState.fromJson(JSONObject(text.substring(start, end + 1)))
    }.getOrNull()
}
