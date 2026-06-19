package com.yuchen.ailedger.service

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_AGENT_CONNECT_TIMEOUT_MS = 8_000
private const val VISUAL_AGENT_READ_TIMEOUT_MS = 20_000
private const val VISUAL_AGENT_MAX_RECENT_ACTIONS = 8
private const val VISUAL_AGENT_MAX_RECENT_ACTION_CHARS = 240
private const val VISUAL_AGENT_MAX_HISTORY_ITEMS = 4
private const val VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS = 1_200
private const val VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS = 240
private const val VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS = 160
private const val VISUAL_AGENT_MAX_APP_TEXT_CHARS = 120
private const val VISUAL_AGENT_MAX_VERIFICATION_EVENTS = 8
private const val VISUAL_AGENT_MAX_BLOCKED_SIGNATURES = 6
private const val VISUAL_AGENT_SESSION_PROTOCOL = "android_visual_agent_v6_structured_feedback"

internal object VisualAgentProtocol {
    const val coordinateProtocol = "normalized_screen_0_1"

    val supportedStepTypes: Set<String> = linkedSetOf(
        "open_app",
        "tap_xy",
        "input_text",
        "swipe",
        "back",
        "home",
        "wait",
        "finish",
        "need_user_help",
    )
}

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
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-${System.currentTimeMillis()}",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
): CloudAgentPlan {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank()) throw IOException("AI Worker endpoint is not configured")
    val payload = buildVisualAgentPayload(
        goal = goal,
        snapshot = snapshot,
        recentActions = recentActions,
        visualHistory = visualHistory,
        appContext = appContext,
        deviceId = deviceId,
        agentSessionId = agentSessionId,
        executionMode = executionMode,
    )
    return postVisualAgentStep(endpointBase, payload, deviceId, agentSessionId)
}

internal fun buildVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-test",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
): JSONObject {
    val cleanGoal = goal.trim().take(240)
    val cleanDeviceId = deviceId.trim().take(120).ifBlank { "android-compose-visual" }
    val cleanSessionId = agentSessionId.trim().take(120).ifBlank { "visual-session-${System.currentTimeMillis()}" }
    val modeKey = when (executionMode) {
        AgentExecutionMode.VisualForce -> "visual_force"
        AgentExecutionMode.ExplicitAgent -> "explicit_agent"
        AgentExecutionMode.NormalChatDeviceTool -> "normal_chat_device_tool"
    }
    val cleanRecentActionLines = recentActions
        .takeLast(VISUAL_AGENT_MAX_RECENT_ACTIONS)
        .map { it.trim().take(VISUAL_AGENT_MAX_RECENT_ACTION_CHARS) }
        .filter { it.isNotBlank() }
    val recentAgentActions = JSONArray(cleanRecentActionLines)
    val historyExecutionResults = visualHistory
        .takeLast(VISUAL_AGENT_MAX_HISTORY_ITEMS)
        .map { it.executionResult.trim().take(VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS) }
        .filter { it.isNotBlank() }
    val feedbackLines = (cleanRecentActionLines + historyExecutionResults)
        .distinct()
        .takeLast(VISUAL_AGENT_MAX_VERIFICATION_EVENTS)
    val verificationEvents = feedbackLines.filter { it.isVisualRuntimeFeedback() }
    val lastScreenChangeIndex = feedbackLines.indexOfLast { it.isVisualScreenChangedFeedback() }
    val activeFeedbackWindow = if (lastScreenChangeIndex >= 0) {
        feedbackLines.drop(lastScreenChangeIndex + 1)
    } else {
        feedbackLines
    }
    val activeVerificationEvents = activeFeedbackWindow.filter { it.isVisualRuntimeFeedback() }
    val noProgressCount = activeVerificationEvents.count { it.isVisualNoProgressFeedback() }
    val blockedActionSignatures = activeVerificationEvents
        .filter { it.isVisualNoProgressFeedback() || it.isVisualFailureFeedback() }
        .mapNotNull { it.visualActionSignatureOrNull() }
        .distinct()
        .takeLast(VISUAL_AGENT_MAX_BLOCKED_SIGNATURES)
    val lastVerificationEvent = verificationEvents.lastOrNull().orEmpty()
    val lastVerification = when {
        lastVerificationEvent.isVisualNoProgressFeedback() -> "visual_no_screen_change"
        lastVerificationEvent.isVisualScreenChangedFeedback() -> "visual_screen_changed"
        lastVerificationEvent.isVisualFailureFeedback() -> "execution_failed"
        else -> "unknown"
    }
    val lastResultOk = feedbackLines.asReversed().firstNotNullOfOrNull { it.visualResultOkOrNull() }
    val executedActionSignatures = feedbackLines
        .filter { it.contains(":ok:", ignoreCase = true) || it.contains(":failed:", ignoreCase = true) }
        .mapNotNull { it.visualActionSignatureOrNull() }
    val lastActionSignature = executedActionSignatures.lastOrNull()
        ?: activeVerificationEvents.asReversed().firstNotNullOfOrNull { it.visualActionSignatureOrNull() }
        ?: ""
    val sameActionCount = if (lastActionSignature.isBlank()) {
        0
    } else {
        executedActionSignatures.asReversed().takeWhile { it == lastActionSignature }.count()
    }
    val routeRefreshRequested = noProgressCount > 0 || activeVerificationEvents.any { it.isVisualFailureFeedback() }
    val executionFeedback = JSONObject().apply {
        put("lastResultOk", lastResultOk ?: JSONObject.NULL)
        put("lastVerification", lastVerification)
        put("noProgressCount", noProgressCount)
        put("sameActionCount", sameActionCount)
        put("lastActionSignature", lastActionSignature)
        put("blockedActionSignatures", JSONArray(blockedActionSignatures))
        put("verificationEvents", JSONArray(verificationEvents))
        put("latestEvent", lastVerificationEvent)
        put("routeRefreshRequested", routeRefreshRequested)
    }
    val canonicalApps = JSONArray().apply {
        appContext
            .asSequence()
            .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .take(VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS)
            .forEach { item ->
                put(JSONObject().apply {
                    put("label", item.label.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS))
                    put("packageName", item.packageName.trim().take(VISUAL_AGENT_MAX_APP_TEXT_CHARS))
                    put("launchable", true)
                })
            }
    }
    val screenSnapshot = snapshot.toJson(includeImage = false)
    val visual = snapshot.visual?.takeIf { it.hasImage }

    return JSONObject().apply {
        put("action", "visual_agent_step")
        put("intent", "visual_agent_step")
        put("type", "agent_step")
        put("requestType", "visual_agent_step")
        put("agentStepRequest", true)
        put("visualAgentDirect", true)
        put("agentMode", true)
        put("computerUseMode", true)
        put("forceVisualAgent", true)
        put("allowInternalDeviceTools", false)
        put("executionMode", modeKey)
        put("goal", cleanGoal)
        put("agentGoal", cleanGoal)
        put("message", cleanGoal)
        put("agentSessionId", cleanSessionId)
        put("sessionId", cleanSessionId)
        put("agentSessionProtocol", VISUAL_AGENT_SESSION_PROTOCOL)
        put("deviceId", cleanDeviceId)
        put("clientId", cleanDeviceId)
        put("currentPackage", snapshot.packageName)
        put("screenSnapshot", screenSnapshot)
        put("recentAgentActions", recentAgentActions)
        put("recentActions", recentAgentActions)
        put("executionFeedback", executionFeedback)
        put("routeRefreshRequested", routeRefreshRequested)
        put("invalidateCachedAgentBrainRoute", routeRefreshRequested)
        put("agentMemory", JSONObject().apply {
            put("schema", "android_visual_agent_loop_memory_v6_structured_feedback")
            put("recentActions", recentAgentActions)
            put("verificationEvents", JSONArray(verificationEvents))
            put("blockedActionSignatures", JSONArray(blockedActionSignatures))
            put("executionFeedback", executionFeedback)
            put("loopSignals", JSONObject().apply {
                put("agentSessionId", cleanSessionId)
                put("loopIndex", cleanRecentActionLines.size)
                put("executedStepCount", executedActionSignatures.size)
                put("noProgressCount", noProgressCount)
                put("sameActionCount", sameActionCount)
                put("lastResultOk", lastResultOk ?: JSONObject.NULL)
                put("lastVerification", lastVerification)
                put("routeRefreshRequested", routeRefreshRequested)
                put("lastActionSignature", lastActionSignature)
                put("postActionFeedback", executionFeedback)
            })
        })
        put("visualHistory", JSONArray().apply {
            visualHistory
                .takeLast(VISUAL_AGENT_MAX_HISTORY_ITEMS)
                .filter { it.assistantOutput.isNotBlank() || it.executionResult.isNotBlank() }
                .forEach { item ->
                    put(JSONObject().apply {
                        put("assistantOutput", item.assistantOutput.take(VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS))
                        put("executionResult", item.executionResult.take(VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS))
                    })
                }
        })
        put("appContext", canonicalApps)
        put("deviceContext", JSONObject().apply {
            put("schema", "android_visual_agent_context_v1")
            put("currentApp", JSONObject().apply {
                put("packageName", snapshot.packageName)
                put("isAssistantHost", snapshot.packageName == "com.yuchen.ailedger")
            })
            put("screen", JSONObject().apply {
                put("widthPx", visual?.displayWidth ?: 0)
                put("heightPx", visual?.displayHeight ?: 0)
                put("coordinateProtocol", VisualAgentProtocol.coordinateProtocol)
            })
            put("installedApps", canonicalApps)
            put("installedAppCount", canonicalApps.length())
            put("uploadedAppCount", canonicalApps.length())
            put("installedAppsTruncated", appContext.size > canonicalApps.length())
        })
        put("coordinateProtocol", VisualAgentProtocol.coordinateProtocol)
        put("supportedAgentSteps", JSONArray(VisualAgentProtocol.supportedStepTypes.toList()))
        put("supportedDeviceTools", JSONArray())
        put("supportsAgentStepBatch", false)
        put("actionBatchMax", 1)
        put("hasScreenshot", visual != null)
        put("hasImage", visual != null)
        put("hasImages", visual != null)
        put("imageCount", if (visual != null) 1 else 0)
        visual?.let { item ->
            put("screenshot", JSONObject().apply {
                put("mimeType", item.mimeType)
                put("base64Data", item.base64Jpeg)
                put("width", item.width)
                put("height", item.height)
                put("displayWidth", item.displayWidth)
                put("displayHeight", item.displayHeight)
                put("source", item.source)
                put("reason", item.reason)
            })
        }
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentState", true)
            put("includeAgentStep", true)
            put("includeAgentSteps", true)
            put("includeStopConditions", true)
            put("includePerformanceDebug", true)
        })
        put("client", "android-compose")
        put("clientVersion", "visual-agent-direct-v6-structured-feedback")
        put("now", System.currentTimeMillis())
    }
}

private fun String.isVisualRuntimeFeedback(): Boolean {
    val value = lowercase()
    return value.contains(":failed:") ||
        value.contains("visual_no_progress") ||
        value.contains("visual_screen_changed") ||
        value.contains("no_progress") ||
        value.contains("no progress") ||
        value.contains("same screen") ||
        value.contains("没有变化") ||
        value.contains("未生效") ||
        value.contains("重复循环") ||
        value.contains("blocked")
}

private fun String.isVisualNoProgressFeedback(): Boolean {
    val value = lowercase()
    return value.contains("visual_no_progress") ||
        value.contains("no_progress") ||
        value.contains("no progress") ||
        value.contains("same screen") ||
        value.contains("没有变化") ||
        value.contains("未生效") ||
        value.contains("重复循环")
}

private fun String.isVisualScreenChangedFeedback(): Boolean {
    val value = lowercase()
    return value.contains("visual_screen_changed") ||
        value.contains("screen=changed") ||
        value.contains("visual_progress")
}

private fun String.isVisualFailureFeedback(): Boolean {
    val value = lowercase()
    return value.contains(":failed:") || value.contains("blocked") || value.contains("执行失败")
}

private fun String.visualResultOkOrNull(): Boolean? {
    val value = lowercase()
    return when {
        value.contains(":failed:") || value.contains("执行失败") -> false
        value.contains(":ok:") -> true
        else -> null
    }
}

private fun String.visualActionSignatureOrNull(): String? {
    val clean = trim()
    val signature = when {
        ":failed:" in clean -> clean.substringBefore(":failed:")
        ":ok:" in clean -> clean.substringBefore(":ok:")
        clean.startsWith("visual_no_progress:") -> clean.substringAfter("visual_no_progress:").substringBefore(":count=")
        clean.startsWith("visual_screen_changed:") -> clean.substringAfter("visual_screen_changed:").substringBefore(":screen=")
        else -> Regex("(?:tap@\\d+,\\d+|tap_node@[^\\s，。；;:：]+|open@[^\\s，。；;:：]+|input@[^\\s，。；;:：]+|scroll@[a-z]+|swipe@[a-z]+|back|home|recents)")
            .find(clean)
            ?.value
    }
    return signature?.trim()?.take(160)?.takeIf { it.isNotBlank() }
}

private fun postVisualAgentStep(
    endpoint: String,
    payload: JSONObject,
    deviceId: String,
    agentSessionId: String,
): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = VISUAL_AGENT_CONNECT_TIMEOUT_MS
        readTimeout = VISUAL_AGENT_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-agent-v6")
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        setRequestProperty("X-Agent-Session-Protocol", VISUAL_AGENT_SESSION_PROTOCOL)
        setRequestProperty("X-Agent-Session-Id", agentSessionId.take(120))
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
