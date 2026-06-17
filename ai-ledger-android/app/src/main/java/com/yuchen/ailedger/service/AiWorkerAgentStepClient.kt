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
private const val AGENT_SESSION_PROTOCOL = "android_v3_task_contract"

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
        goal,
        snapshot,
        modelPreference,
        recentActions,
        deviceContext,
        agentMemory,
        executionMode,
    )
    var lastError: IOException? = null
    val candidates = listOf(endpoint.trim().trimEnd('/')).filter { it.isNotBlank() }.distinct()
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
    val sessionId = loopSignals?.optString("agentSessionId")?.takeIf { it.isNotBlank() }
        ?: "android-agent-v3-${System.currentTimeMillis()}"
    val contract = AgentTaskContractRuntime.ensureSession(sessionId, cleanGoal)

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
        put("taskExecutionProtocol", JSONObject().apply {
            put("schema", "agent_task_execution_contract_v1")
            put("cloudDefinesRequirements", true)
            put("deviceResolvesInstalledTargetApp", true)
            put("singleActionPerObservation", true)
            put("deterministicEntryBeforeVisualNavigation", true)
            put("echoContractOnEveryTurn", true)
        })
        contract?.let {
            put("taskExecutionContract", it.toJson())
            put("taskPhase", it.phase)
        }
        put("fixedStepLimit", isNormalChatToolProbe)
        put("maxAgentSteps", if (isNormalChatToolProbe) 2 else JSONObject.NULL)
        put("routeRefreshRequested", routeRefreshRequested)
        put("invalidateCachedAgentBrainRoute", routeRefreshRequested)
        put("replanAfterSettingsEntry", true)
        put("crossActionNoProgressAccumulation", false)
        put("recentAgentActions", JSONArray().apply { recentActions.takeLast(10).forEach { put(it) } })
        agentMemory?.let { put("agentMemory", it) }
        deviceContext?.let {
            put("deviceContext", it.json)
            it.json.optJSONObject("targetAppResolution")?.let { resolution ->
                put("targetAppResolution", resolution)
            }
            it.json.optString("appInventoryHash").takeIf { hash -> hash.isNotBlank() }?.let { hash ->
                put("appInventoryHash", hash)
            }
        }
        put("screenSnapshot", snapshot.toJson(includeImage = false))
        put("hasScreenshot", hasVisualPayload)
        put("hasImage", hasVisualPayload)
        put("hasImages", hasVisualPayload)
        put("imageCount", if (hasVisualPayload) 1 else 0)
        if (hasVisualPayload) {
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
            if (hasVisualPayload) "compose-native-agent-visual-contract-v15"
            else "compose-native-agent-tool-contract-v15",
        )
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
            put("includeAgentSteps", true)
            put("includeActionBatch", true)
            put("includeStopConditions", true)
            put("includeTaskExecutionContract", true)
            put("includeTargetAppResolutionAck", true)
        })
        put("now", System.currentTimeMillis())
    }
}

private fun postAgentPlan(endpoint: String, payload: JSONObject): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val sessionId = payload.optString("agentSessionId")
    val goal = payload.optString("agentGoal")
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AGENT_STEP_CONNECT_TIMEOUT_MS
        readTimeout = AGENT_STEP_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json, text/plain")
        setRequestProperty("X-Client", "android-compose-agent-v15")
        setRequestProperty("X-Agent-Session-Protocol", AGENT_SESSION_PROTOCOL)
    }
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(requestBytes) }
        val status = connection.responseCode
        val body = connection.agentReadBody(status)
        val data = body.agentJsonOrNull()
        AgentRuntimeController.noteDiagnostic(
            buildCompactAgentDiagnostic(data, requestBytes.size, body.length, SystemClock.elapsedRealtime() - requestStart),
        )
        if (status !in 200..299) {
            val message = data?.optString("error")?.takeIf { it.isNotBlank() }
                ?: data?.optString("message")?.takeIf { it.isNotBlank() }
                ?: body.take(120).ifBlank { "云端智能体规划失败：HTTP $status" }
            throw IOException(message)
        }
        val parsedContract = AgentTaskContractRuntime.update(sessionId, goal, data)
        val plan = CloudAgentPlan.fromJson(data)
            ?: extractAgentPlanFromText(body)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
            ?: extractAgentStepFromText(body)?.let { CloudAgentPlan(step = it, state = extractAgentStateFromText(body)) }
            ?: throw IOException("云端没有返回有效的智能体下一步动作")
        if (plan.state?.isComplete == true || parsedContract?.phase == AGENT_TASK_PHASE_COMPLETED) {
            AgentTaskContractRuntime.clear(sessionId)
        }
        plan
    } catch (error: SocketTimeoutException) {
        throw IOException("云端智能体规划超过 ${AGENT_STEP_READ_TIMEOUT_MS / 1000} 秒未返回：${endpoint.substringAfter("://")}", error)
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
    val version = data?.optString("version").orEmpty()
        .replace("qwen-deepseek-cn-web-data-", "")
        .replace("agent-", "")
        .take(24)
    val source = data?.optString("source").orEmpty().take(32)
    val step = data?.optJSONObject("agentStep")?.optString("type").orEmpty()
    val phase = AgentTaskExecutionContract.fromResponse(data)?.phase.orEmpty()
    return buildString {
        append("AgentDebug req=").append(bytesToKb(requestBytes))
            .append("K resp=").append(bytesToKb(responseChars))
            .append("K http=").append(totalMs)
        if (step.isNotBlank()) append(" step=").append(step)
        if (phase.isNotBlank()) append(" phase=").append(phase)
        if (source.isNotBlank()) append(" src=").append(source)
        if (version.isNotBlank()) append(" v=").append(version)
        if (debug == null) append(" debug=无")
    }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else ((bytes + 1023) / 1024)

private fun HttpURLConnection.agentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.agentJsonOrNull(): JSONObject? = try {
    takeIf { it.isNotBlank() }?.let { JSONObject(it) }
} catch (_: Exception) {
    null
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return try { getInt(key) } catch (_: Exception) { optString(key).toIntOrNull() }
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
