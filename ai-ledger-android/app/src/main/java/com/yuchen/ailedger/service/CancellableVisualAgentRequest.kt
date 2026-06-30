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
// 后端首次 DeepSeek 规划的 Provider 截止为 15 秒。客户端必须给冷启动、鉴权、
// JSON 解析和回包留下明确余量，不能抢在后端前面制造“假 DeepSeek 超时”。
private const val INITIAL_ROUTE_CONNECT_TIMEOUT_MS = 8_000
private const val INITIAL_ROUTE_READ_TIMEOUT_MS = 22_000
private const val INITIAL_ROUTE_CALL_TIMEOUT_MS = 26_000L
private const val CANCELLABLE_VISUAL_STOP_POLL_MS = 50L
private const val CANCELLABLE_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v16_text_bootstrap_gui_loop"

/**
 * Two deliberately separate cloud phases:
 *
 * 1. Before a task contract exists, DeepSeek receives only the user goal and canonical
 *    launchable-app directory. No screenshot, observation, node tree, visual history or runtime
 *    reasoning state is uploaded.
 * 2. After Android opens and verifies the exact target package, GUI Plus receives the fresh
 *    screenshot and committed cloud-authored task contract.
 *
 * The committed task contract is the irreversible handoff boundary. Once it exists, Android must
 * never fall back into DeepSeek planning because of a transient package or screenshot state.
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

    val initialRoute = shouldUseInitialAgentBrainRoute(taskMemory)
    if (!initialRoute && runtimeContext?.guiPlusEligible != true) {
        throw VisualAgentRequestException(
            httpStatus = null,
            code = "visual_work_surface_not_verified",
            retryable = false,
            backendMessage = "A committed task contract exists, but Android has not verified the target work surface. DeepSeek and GUI Plus were not called.",
        )
    }
    val payload = if (initialRoute) {
        buildInitialAgentBrainRoutePayload(
            goal = goal,
            appContext = appContext,
            deviceId = deviceId,
            agentSessionId = agentSessionId,
            taskContract = null,
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
                    "The initial text-planning request exceeded the ${absoluteTimeoutMs / 1000}s Android client boundary. No screenshot retry or GUI request was started."
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

internal fun shouldUseInitialAgentBrainRoute(taskMemory: VisualTaskMemory?): Boolean =
    taskMemory?.taskContract == null

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
        put("intent", "agent_brain_route")
        put("goal", goal.trim().take(240))
        put("agentSessionId", agentSessionId.trim().take(120))
        put("deviceId", deviceId.trim().take(120))
        put("agentSessionProtocol", CANCELLABLE_VISUAL_SESSION_PROTOCOL)
        put("appInventoryHash", inventoryHash)
        put("appContext", appArray)
        put("deviceContext", JSONObject().apply {
            put("schema", "android_agent_brain_text_bootstrap_v1")
            put("appInventoryHash", inventoryHash)
        })
        taskContract?.let { contract ->
            put("taskContract", contract.toJson())
            put("agentMemory", JSONObject().apply { put("taskContract", contract.toJson()) })
        }
        put("client", "android-compose")
        put("clientVersion", "text-bootstrap-gui-loop-v2")
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
            if (initialRoute) "android-compose-agent-brain-bootstrap-v2" else "android-compose-visual-agent-v16",
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
        val responseHeadersMs = SystemClock.elapsedRealtime() - requestStart
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val data = body.takeIf(String::isNotBlank)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        val workerVersion = connection.getHeaderField("X-AI-Ledger-Worker-Version").orEmpty().take(64)
        val routeProtocol = connection.getHeaderField("X-AI-Ledger-Route-Protocol").orEmpty().take(64)
        val providerElapsedMs = data?.optLong("providerElapsedMs", -1L) ?: -1L
        val serverElapsedMs = data?.optLong("elapsedMs", -1L) ?: -1L
        val providerRequestCount = data?.optInt("providerRequestCount", -1) ?: -1
        val internalRetryCount = data?.optInt("internalRetryCount", -1) ?: -1
        AgentRuntimeController.noteDiagnostic(buildString {
            append(if (initialRoute) "AgentBrainText" else "GUIPlusVisual")
            append(" q=").append(bytesToKb(requestBytes.size)).append("K")
            append(" r=").append(bytesToKb(body.toByteArray(Charsets.UTF_8).size)).append("K")
            append(" headers=").append(responseHeadersMs)
            append(" total=").append(SystemClock.elapsedRealtime() - requestStart)
            if (providerElapsedMs >= 0L) append(" provider=").append(providerElapsedMs)
            if (serverElapsedMs >= 0L) append(" server=").append(serverElapsedMs)
            if (providerRequestCount >= 0) append(" providerCalls=").append(providerRequestCount)
            if (internalRetryCount >= 0) append(" internalRetries=").append(internalRetryCount)
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
                "The Android client received no complete initial-planning response within ${INITIAL_ROUTE_READ_TIMEOUT_MS / 1000}s. This is a client boundary report, not proof that DeepSeek itself failed."
            } else {
                "visual_agent_step timed out after ${CANCELLABLE_VISUAL_READ_TIMEOUT_MS / 1000}s"
            },
            cause = error,
        )
    } catch (error: VisualAgentRequestException) {
        if (!initialRoute || !error.retryable) throw error
        throw VisualAgentRequestException(
            httpStatus = error.httpStatus,
            code = error.code,
            retryable = false,
            backendMessage = "${error.backendMessage}; initial text planning is never retried inside the visual loop",
            cause = error,
        )
    } catch (error: java.io.IOException) {
        if (!initialRoute) throw error
        throw VisualAgentRequestException(
            httpStatus = null,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "Initial text planning failed before GUI Plus started: ${error.message.orEmpty().take(240)}",
            cause = error,
        )
    } finally {
        activeConnection.compareAndSet(connection, null)
        connection.disconnect()
    }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else (bytes + 1023) / 1024
