package com.yuchen.ailedger.service

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

private const val VISUAL_AGENT_CONNECT_TIMEOUT_MS = 8_000
private const val VISUAL_AGENT_READ_TIMEOUT_MS = 25_000
private const val VISUAL_AGENT_MAX_RECENT_ACTIONS = 12
private const val VISUAL_AGENT_MAX_RECENT_ACTION_CHARS = 1_200
private const val VISUAL_AGENT_MAX_HISTORY_ITEMS = 4
private const val VISUAL_AGENT_MAX_HISTORY_OUTPUT_CHARS = 1_200
private const val VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS = 240
private const val VISUAL_AGENT_MAX_APP_CONTEXT_ITEMS = 160
private const val VISUAL_AGENT_MAX_APP_TEXT_CHARS = 120
private const val VISUAL_AGENT_MAX_VERIFICATION_EVENTS = 8
private const val VISUAL_AGENT_MAX_BLOCKED_SIGNATURES = 6
private const val VISUAL_AGENT_MAX_INTERACTION_ITEMS = 16
private const val VISUAL_AGENT_MAX_INTERACTION_CHARS = 1_200
private const val VISUAL_AGENT_SESSION_PROTOCOL = "android_visual_agent_v8_controller_handoff"
private const val VISUAL_AGENT_INTERACTION_PROTOCOL = "gui_plus_dialogue_v1"
private const val ASSISTANT_HOST_PACKAGE = "com.yuchen.ailedger"

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
    val interactionHistory = buildInteractionHistory(cleanRecentActionLines)
    val historyExecutionResults = visualHistory
        .takeLast(VISUAL_AGENT_MAX_HISTORY_ITEMS)
        .map { it.executionResult.trim().take(VISUAL_AGENT_MAX_HISTORY_RESULT_CHARS) }
        .filter { it.isNotBlank() }

    val feedbackLines = (cleanRecentActionLines + historyExecutionResults)
        .distinct()
        .takeLast(VISUAL_AGENT_MAX_VERIFICATION_EVENTS)
    val verificationEvents = feedbackLines.filter { it.isVisualRuntimeFeedback() }
    val lastScreenChangeIndex = feedbackLines.indexOfLast { it.isVisualScreenChangedFeedback() }
    val activeFeedbackWindow = if (lastScreenChangeIndex >= 0) feedbackLines.drop(lastScreenChangeIndex + 1) else feedbackLines
    val activeVerificationEvents = activeFeedbackWindow.filter { it.isVisualRuntimeFeedback() }
    val noProgressCount = activeVerificationEvents.count { it.isVisualNoProgressFeedback() }
    val finishVerificationRequested = activeVerificationEvents.any { it.isVisualFinishVerificationFeedback() }
    val blockedActionSignatures = activeVerificationEvents
        .filter { it.isVisualNoProgressFeedback() || it.isVisualFailureFeedback() }
        .mapNotNull { it.visualActionSignatureOrNull() }
        .distinct()
        .takeLast(VISUAL_AGENT_MAX_BLOCKED_SIGNATURES)
    val lastVerificationEvent = verificationEvents.lastOrNull().orEmpty()
    val lastVerification = when {
        lastVerificationEvent.isVisualFinishVerificationFeedback() -> "finish_verification_pending"
        lastVerificationEvent.isVisualNoProgressFeedback() -> "visual_no_screen_change"
        lastVerificationEvent.isVisualScreenChangedFeedback() -> "visual_screen_changed"
        lastVerificationEvent.isVisualFailureFeedback() -> "execution_failed"
        else -> "unknown"
    }
    val executedActionLines = cleanRecentActionLines.filter {
        it.contains(":ok:", ignoreCase = true) || it.contains(":failed:", ignoreCase = true)
    }
    val historyResultLines = historyExecutionResults.filter {
        it.contains(":ok:", ignoreCase = true) || it.contains(":failed:", ignoreCase = true)
    }
    val lastExecutionResultOk = executedActionLines.asReversed().firstNotNullOfOrNull { it.visualResultOkOrNull() }
        ?: historyResultLines.asReversed().firstNotNullOfOrNull { it.visualResultOkOrNull() }
    val lastResultOk = when {
        lastVerificationEvent.isVisualNoProgressFeedback() || lastVerificationEvent.isVisualFailureFeedback() -> false
        lastVerificationEvent.isVisualScreenChangedFeedback() -> true
        else -> lastExecutionResultOk
    }
    val executedActionSignatures = executedActionLines.mapNotNull { it.visualActionSignatureOrNull() }
    val lastActionSignature = executedActionSignatures.lastOrNull()
        ?: activeVerificationEvents.asReversed().firstNotNullOfOrNull { it.visualActionSignatureOrNull() }
        ?: ""
    val sameActionCount = if (lastActionSignature.isBlank()) {
        0
    } else {
        executedActionSignatures.asReversed().takeWhile { it == lastActionSignature }.count()
    }
    val guiPlusReplanRequested = finishVerificationRequested ||
        noProgressCount > 0 ||
        activeVerificationEvents.any { it.isVisualFailureFeedback() }

    val executionFeedback = JSONObject().apply {
        put("lastResultOk", lastResultOk ?: JSONObject.NULL)
        put("lastVerification", lastVerification)
        put("noProgressCount", noProgressCount)
        put("sameActionCount", sameActionCount)
        put("lastActionSignature", lastActionSignature)
        put("blockedActionSignatures", JSONArray(blockedActionSignatures))
        put("verificationEvents", JSONArray(verificationEvents))
        put("latestEvent", lastVerificationEvent)
        put("finishVerificationRequested", finishVerificationRequested)
        put("visualReplanRequested", guiPlusReplanRequested)
        put("guiPlusReplanRequested", guiPlusReplanRequested)
        put("routeRefreshRequested", false)
    }
    val lastToolResponse = JSONObject().apply {
        put("type", "tool_response")
        put("toolName", "mobile_use")
        put("success", lastResultOk ?: JSONObject.NULL)
        put("result", executedActionLines.lastOrNull() ?: historyExecutionResults.lastOrNull() ?: lastVerificationEvent)
        put("verification", lastVerification)
        put("actionSignature", lastActionSignature)
        put("screenChanged", lastVerification == "visual_screen_changed")
        put("finishVerificationRequested", finishVerificationRequested)
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
    val isAssistantHost = snapshot.packageName == ASSISTANT_HOST_PACKAGE
    val isFirstVisualTurn = visualHistory.isEmpty() && executedActionLines.isEmpty()
    val controllerHandoffActive = isAssistantHost && isFirstVisualTurn
    val surfaceContext = JSONObject().apply {
        put("schema", "android_visual_surface_context_v1")
        put("role", if (isAssistantHost) "controller" else "work_surface")
        put("controllerPackage", ASSISTANT_HOST_PACKAGE)
        put("isAssistantHost", isAssistantHost)
        put("isFirstVisualTurn", isFirstVisualTurn)
        put("controllerHandoffActive", controllerHandoffActive)
        put("directCrossAppLaunchSupported", true)
        put("homeTransitionRequired", false)
    }

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
        put("decisionOwner", "gui_plus")
        put("visualDecisionOwner", "gui_plus")
        put("exclusiveVisualSession", true)
        put("allowAgentBrain", false)
        put("allowRoutePlanner", false)
        put("allowSemanticJudge", false)
        put("allowTaskContractJudge", false)
        put("executionMode", modeKey)
        put("goal", cleanGoal)
        put("agentGoal", cleanGoal)
        put("message", cleanGoal)
        put("agentSessionId", cleanSessionId)
        put("sessionId", cleanSessionId)
        put("agentSessionProtocol", VISUAL_AGENT_SESSION_PROTOCOL)
        put("interactionProtocol", VISUAL_AGENT_INTERACTION_PROTOCOL)
        put("interactionHistory", interactionHistory)
        put("interactionTurnCount", interactionHistory.length())
        put("deviceId", cleanDeviceId)
        put("clientId", cleanDeviceId)
        put("currentPackage", snapshot.packageName)
        put("surfaceRole", surfaceContext.getString("role"))
        put("controllerHandoff", surfaceContext)
        put("screenSnapshot", screenSnapshot)
        put("recentAgentActions", recentAgentActions)
        put("recentActions", recentAgentActions)
        put("executionFeedback", executionFeedback)
        put("lastToolResponse", lastToolResponse)
        put("toolResponse", lastToolResponse)
        put("finishVerificationRequested", finishVerificationRequested)
        put("visualReplanRequested", guiPlusReplanRequested)
        put("guiPlusReplanRequested", guiPlusReplanRequested)
        put("routeRefreshRequested", false)
        put("invalidateCachedAgentBrainRoute", false)
        put("visualOwnership", JSONObject().apply {
            put("schema", "android_gui_plus_exclusive_ownership_v1")
            put("owner", "gui_plus")
            put("exclusive", true)
            put("entryRouterReleased", true)
            put("allowAgentBrain", false)
            put("allowRoutePlanner", false)
            put("allowSemanticJudge", false)
            put("allowTaskContractJudge", false)
        })
        put("agentMemory", JSONObject().apply {
            put("schema", "android_visual_agent_loop_memory_v8_controller_handoff")
            put("recentActions", recentAgentActions)
            put("interactionProtocol", VISUAL_AGENT_INTERACTION_PROTOCOL)
            put("interactionHistory", interactionHistory)
            put("verificationEvents", JSONArray(verificationEvents))
            put("blockedActionSignatures", JSONArray(blockedActionSignatures))
            put("executionFeedback", executionFeedback)
            put("lastToolResponse", lastToolResponse)
            put("surfaceContext", surfaceContext)
            put("loopSignals", JSONObject().apply {
                put("agentSessionId", cleanSessionId)
                put("loopIndex", cleanRecentActionLines.size)
                put("executedStepCount", executedActionSignatures.size)
                put("noProgressCount", noProgressCount)
                put("sameActionCount", sameActionCount)
                put("lastResultOk", lastResultOk ?: JSONObject.NULL)
                put("lastVerification", lastVerification)
                put("finishVerificationRequested", finishVerificationRequested)
                put("visualReplanRequested", guiPlusReplanRequested)
                put("guiPlusReplanRequested", guiPlusReplanRequested)
                put("routeRefreshRequested", false)
                put("lastActionSignature", lastActionSignature)
                put("postActionFeedback", executionFeedback)
                put("lastToolResponse", lastToolResponse)
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
            put("schema", "android_visual_agent_context_v2_controller_handoff")
            put("currentApp", JSONObject().apply {
                put("packageName", snapshot.packageName)
                put("isAssistantHost", isAssistantHost)
                put("surfaceRole", surfaceContext.getString("role"))
            })
            put("surfaceContext", surfaceContext)
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
        put("clientVersion", "visual-agent-controller-handoff-v9")
        put("now", System.currentTimeMillis())
    }
}

private fun buildInteractionHistory(recentActions: List<String>): JSONArray {
    val turns = recentActions.mapNotNull { line ->
        when {
            line.startsWith("guiPlusQuestion:") -> "assistant" to line.substringAfter("guiPlusQuestion:")
            line.startsWith("userReply:") -> "user" to line.substringAfter("userReply:")
            line.startsWith("userInstruction:") -> "user" to line.substringAfter("userInstruction:")
            else -> null
        }
    }.takeLast(VISUAL_AGENT_MAX_INTERACTION_ITEMS)

    return JSONArray().apply {
        turns.forEachIndexed { index, (role, content) ->
            val cleanContent = content.trim().take(VISUAL_AGENT_MAX_INTERACTION_CHARS)
            if (cleanContent.isNotBlank()) {
                put(JSONObject().apply {
                    put("index", index)
                    put("role", role)
                    put("content", cleanContent)
                    put("sensitiveRedacted", cleanContent.contains("敏感输入") || cleanContent.contains("private_step"))
                })
            }
        }
    }
}

private fun String.isVisualRuntimeFeedback(): Boolean {
    val value = lowercase()
    return value.contains(":failed:") ||
        value.contains("visual_no_progress") ||
        value.contains("visual_screen_changed") ||
        value.contains("finish_verification_pending") ||
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
    return value.contains("visual_screen_changed") || value.contains("screen=changed") || value.contains("visual_progress")
}

private fun String.isVisualFinishVerificationFeedback(): Boolean = lowercase().contains("finish_verification_pending")

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
        clean.startsWith("finish_verification_pending:") -> "finish"
        else -> Regex("(?:tap@\\d+,\\d+|tap_node@[^\\s，。；;:：]+|open@[^\\s，。；;:：]+|input@[^\\s，。；;:：]+|scroll@[a-z]+|swipe@[a-z]+|back|home|recents)")
            .find(clean)
            ?.value
    }
    return signature?.trim()?.take(160)?.takeIf { it.isNotBlank() }
}

private fun AiWorkerClient.postVisualAgentStep(
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
        setRequestProperty("X-Client", "android-compose-visual-agent-v9")
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
