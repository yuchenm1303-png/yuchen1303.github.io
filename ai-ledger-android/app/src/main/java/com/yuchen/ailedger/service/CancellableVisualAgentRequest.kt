package com.yuchen.ailedger.service

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

private const val CANCELLABLE_VISUAL_CONNECT_TIMEOUT_MS = 8_000
private const val CANCELLABLE_VISUAL_READ_TIMEOUT_MS = 25_000
private const val CANCELLABLE_VISUAL_CALL_TIMEOUT_MS = 35_000L
private const val INITIAL_ROUTE_CONNECT_TIMEOUT_MS = 5_000
private const val INITIAL_ROUTE_READ_TIMEOUT_MS = 10_000
private const val INITIAL_ROUTE_CALL_TIMEOUT_MS = 12_000L
private const val CANCELLABLE_VISUAL_STOP_POLL_MS = 50L
private const val CANCELLABLE_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v16_text_bootstrap_gui_loop"

/**
 * Uses two deliberately separate cloud phases:
 *
 * 1. Before a verified work surface exists, DeepSeek receives only the user goal and the canonical
 *    launchable-app directory. No screenshot, observation, node tree, visual history or runtime
 *    reasoning state is uploaded.
 * 2. After Android has opened and verified the exact target package, the regular GUI Plus request
 *    carries the fresh screenshot and the committed cloud-authored task contract.
 *
 * Android does not select an app, split the goal or infer a milestone in either phase.
 */
internal suspend fun AiWorkerClient.requestVisualAgentStepCancellable(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String> = emptyList(),
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-${System.currentTimeMillis()}",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
    deviceProfile: AgentDeviceProfile? = null,
    runtimeContext: VisualAgentRuntimeContext? = null,
    taskMemory: VisualTaskMemory? = null,
    isStopped: () -> Boolean,
): CloudAgentPlan = coroutineScope {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank()) throw java.io.IOException("AI Worker endpoint is not configured")

    val initialRoute = runtimeContext?.guiPlusEligible != true
    val payload = if (initialRoute) {
        buildInitialAgentBrainRoutePayload(
            goal = goal,
            appContext = appContext,
            deviceId = deviceId,
            agentSessionId = agentSessionId,
            taskContract = taskMemory?.taskContract,
        )
    } else {
        buildVisualAgentPayload(
            goal = goal,
            snapshot = snapshot,
            recentActions = recentActions,
            visualHistory = visualHistory,
            appContext = appContext,
            deviceId = deviceId,
            agentSessionId = agentSessionId,
            executionMode = executionMode,
            deviceProfile = deviceProfile,
            runtimeContext = runtimeContext,
            taskMemory = taskMemory,
        ).compactVisualAgentPayloadForTransport()
    }
    VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordModelRequestPayload(payload)

    val activeConnection = AtomicReference<HttpURLConnection?>(null)
    val request = async(Dispatchers.IO) {
        postCancellableAgentRequest(
            endpoint = endpointBase,
            payload = payload,
            deviceId = deviceId,
            agentSessionId = agentSessionId,
            initialRoute = initialRoute,
            activeConnection = activeConnection,
        )
    }
    val stopWatcher = launch {
        while (request.isActive) {
            if (isStopped()) {
                activeConnection.get()?.disconnect()
                request.cancel(CancellationException("Visual task stopped while waiting for the cloud plan."))
                break
            }
            delay(CANCELLABLE_VISUAL_STOP_POLL_MS)
        }
    }
    val absoluteTimeoutMs = if (initialRoute) INITIAL_ROUTE_CALL_TIMEOUT_MS else CANCELLABLE_VISUAL_CALL_TIMEOUT_MS
    try {
        withTimeoutOrNull(absoluteTimeoutMs) {
            request.await()
        } ?: run {
            activeConnection.get()?.disconnect()
            request.cancel(CancellationException("Cloud request exceeded its absolute timeout."))
            throw VisualAgentRequestException(
                httpStatus = null,
                code = if (initialRoute) "agent_brain_route_timeout" else "network_timeout",
                retryable = !initialRoute,
                backendMessage = if (initialRoute) {
                    "DeepSeek initial text plan exceeded ${absoluteTimeoutMs / 1000}s; no screenshot or GUI request was started."
                } else {
                    "visual_agent_step exceeded ${absoluteTimeoutMs / 1000}s absolute timeout"
                },
            )
        }
    } finally {
        stopWatcher.cancel()
        activeConnection.getAndSet(null)?.disconnect()
    }
}

internal fun buildInitialAgentBrainRoutePayload(
    goal: String,
    appContext: List<VisualAgentAppContextItem>,
    deviceId: String,
    agentSessionId: String,
    taskContract: VisualTaskContract? = null,
): JSONObject {
    val apps = appContext.asSequence()
        .map { it.label.trim() to it.packageName.trim() }
        .filter { (label, packageName) -> label.isNotBlank() && packageName.isNotBlank() }
        .distinctBy { it.second }
        .take(160)
        .toList()
    val inventoryCanonical = apps.sortedBy { it.second }
        .joinToString("\n") { (label, packageName) -> "$packageName|$label" }
    val inventoryHash = MessageDigest.getInstance("SHA-256")
        .digest(inventoryCanonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(24)
    val appArray = JSONArray().apply {
        apps.forEach { (label, packageName) ->
            put(JSONObject().apply {
                put("label", label.take(120))
                put("packageName", packageName.take(120))
            })
        }
    }

    return JSONObject().apply {
        put("action", "agent_brain_route")
        put("intent", "agent_brain_route")
        put("type", "agent_brain_route")
        put("requestType", "agent_brain_route")
        put("agentBrainRoute", true)
        put("goal", goal.trim().take(240))
        put("agentGoal", goal.trim().take(240))
        put("message", goal.trim().take(240))
        put("agentSessionId", agentSessionId.trim().take(120))
        put("sessionId", agentSessionId.trim().take(120))
        put("deviceId", deviceId.trim().take(120))
        put("clientId", deviceId.trim().take(120))
        put("appInventoryHash", inventoryHash)
        put("appContext", appArray)
        put("deviceContext", JSONObject().apply {
            put("schema", "android_agent_brain_text_bootstrap_v1")
            put("appInventoryHash", inventoryHash)
            put("installedApps", appArray)
        })
        taskContract?.let { contract ->
            put("taskContract", contract.toJson())
            put("agentMemory", JSONObject().apply { put("taskContract", contract.toJson()) })
        }
        put("hasScreenshot", false)
        put("hasImage", false)
        put("imageCount", 0)
        put("allowAgentBrain", true)
        put("allowRoutePlanner", false)
        put("allowSemanticJudge", false)
        put("decisionOwner", "deepseek")
        put("visualDecisionOwner", "none_before_verified_work_surface")
        put("responseFormat", JSONObject().apply {
            put("type", "json_object")
            put("includeAgentBrainRoute", true)
            put("includeAgentStep", true)
            put("includeTaskContract", true)
        })
        put("client", "android-compose")
        put("clientVersion", "text-bootstrap-gui-loop-v1")
        put("now", System.currentTimeMillis())
    }
}

private fun postCancellableAgentRequest(
    endpoint: String,
    payload: JSONObject,
    deviceId: String,
    agentSessionId: String,
    initialRoute: Boolean,
    activeConnection: AtomicReference<HttpURLConnection?>,
): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = if (initialRoute) INITIAL_ROUTE_CONNECT_TIMEOUT_MS else CANCELLABLE_VISUAL_CONNECT_TIMEOUT_MS
        readTimeout = if (initialRoute) INITIAL_ROUTE_READ_TIMEOUT_MS else CANCELLABLE_VISUAL_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty(
            "X-Client",
            if (initialRoute) "android-compose-agent-brain-bootstrap-v1" else "android-compose-visual-agent-v16",
        )
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        setRequestProperty("X-Agent-Session-Protocol", CANCELLABLE_VISUAL_SESSION_PROTOCOL)
        setRequestProperty("X-Agent-Session-Id", agentSessionId.take(120))
        AiWorkerRequestIdentity.applyTo(
            connection = this,
            appClientToken = AiWorkerRequestIdentity.defaultAppClientToken(),
            mode = AiWorkerIdentityMode.AppOnly,
        )
    }
    activeConnection.set(connection)
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(requestBytes.size)
        connection.outputStream.use { it.write(requestBytes) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val data = body.takeIf(String::isNotBlank)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        val workerVersion = connection.getHeaderField("X-AI-Ledger-Worker-Version").orEmpty().take(48)
        val routeProtocol = connection.getHeaderField("X-AI-Ledger-Route-Protocol").orEmpty().take(48)
        AgentRuntimeController.noteDiagnostic(buildString {
            append(if (initialRoute) "AgentBrainText" else "GUIPlusVisual")
            append(" q=").append(bytesToKb(requestBytes.size)).append("K")
            append(" r=").append(bytesToKb(body.toByteArray(Charsets.UTF_8).size)).append("K")
            append(" h=").append(SystemClock.elapsedRealtime() - requestStart)
            if (workerVersion.isNotBlank()) append(" w=").append(workerVersion)
            if (routeProtocol.isNotBlank()) append(" p=").append(routeProtocol)
        })
        if (status !in 200..299) throw parseVisualAgentHttpFailure(status, body)
        if (!initialRoute) {
            validateVisualAgentResponseObservationId(payload.optString("expectedActionObservationId"), data)
        }
        CloudAgentPlan.fromJson(data)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
            ?: throw java.io.IOException(
                if (initialRoute) "agent_brain_route did not return one executable agentStep" else "visual_agent_step did not return one agentStep",
            )
    } catch (error: SocketTimeoutException) {
        throw VisualAgentRequestException(
            httpStatus = null,
            code = if (initialRoute) "agent_brain_route_timeout" else "network_timeout",
            retryable = !initialRoute,
            backendMessage = if (initialRoute) {
                "DeepSeek initial text plan timed out after ${INITIAL_ROUTE_READ_TIMEOUT_MS / 1000}s; GUI Plus was not started."
            } else {
                "visual_agent_step timed out after ${CANCELLABLE_VISUAL_READ_TIMEOUT_MS / 1000}s"
            },
            cause = error,
        )
    } finally {
        activeConnection.compareAndSet(connection, null)
        connection.disconnect()
    }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else (bytes + 1023) / 1024
