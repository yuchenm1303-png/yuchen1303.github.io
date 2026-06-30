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

// AgentBrain 首轮或交接重放只上传文本目标、应用目录和已提交契约。后端采用分阶段
// 超时：响应头 8 秒、正文 21 秒；Android 留出网络回包与 JSON 解析余量。
private const val AGENT_BRAIN_CONNECT_TIMEOUT_MS = 8_000
private const val AGENT_BRAIN_READ_TIMEOUT_MS = 24_000
private const val AGENT_BRAIN_CALL_TIMEOUT_MS = 30_000L
private const val CANCELLABLE_VISUAL_STOP_POLL_MS = 50L
private const val CANCELLABLE_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v16_text_bootstrap_gui_loop"

/**
 * Runs the cloud loop in two explicit phases:
 *
 * 1. AgentBrain route phase: before a contract exists, or while Android is still completing the
 *    mechanical target-app handoff, send only the goal, canonical app directory and any committed
 *    contract. Replaying this phase never uploads a screenshot and never authorizes GUI actions.
 * 2. GUI Plus phase: only after Android has verified the exact target package, send the fresh
 *    screenshot and observation-bound runtime context.
 *
 * The transport layer does not manufacture a local WorkSurface error. An unfinished handoff simply
 * replays the cloud-authored open_app route, normally from the backend cache, until Android verifies
 * the target or the route request fails explicitly.
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

    val committedContract = taskMemory?.taskContract
    val agentBrainRouteRequest = shouldUseAgentBrainRoute(
        taskContract = committedContract,
        runtimeContext = runtimeContext,
    )
    val payload = if (agentBrainRouteRequest) {
        buildInitialAgentBrainRoutePayload(
            goal = goal,
            appContext = appContext,
            deviceId = deviceId,
            agentSessionId = agentSessionId,
            taskContract = committedContract,
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
            agentBrainRouteRequest = agentBrainRouteRequest,
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
    val absoluteTimeoutMs = if (agentBrainRouteRequest) {
        AGENT_BRAIN_CALL_TIMEOUT_MS
    } else {
        CANCELLABLE_VISUAL_CALL_TIMEOUT_MS
    }
    try {
        withTimeoutOrNull(absoluteTimeoutMs) {
            request.await()
        } ?: run {
            activeConnection.get()?.disconnect()
            request.cancel(CancellationException("Cloud request exceeded its absolute timeout."))
            throw VisualAgentRequestException(
                httpStatus = null,
                code = if (agentBrainRouteRequest) "agent_brain_route_timeout" else "network_timeout",
                retryable = !agentBrainRouteRequest,
                backendMessage = if (agentBrainRouteRequest) {
                    "AgentBrain route request exceeded the ${absoluteTimeoutMs / 1000}s Android boundary. No screenshot or GUI action was started."
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

/**
 * A committed contract is not enough to enter GUI Plus. Until the exact WorkSurface is verified,
 * replay the AgentBrain entry route with that immutable contract instead of throwing a local error.
 */
internal fun shouldUseAgentBrainRoute(
    taskContract: VisualTaskContract?,
    runtimeContext: VisualAgentRuntimeContext?,
): Boolean = taskContract == null || runtimeContext?.guiPlusEligible != true

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
            put("schema", "android_agent_brain_text_bootstrap_v2")
            put("appInventoryHash", inventoryHash)
        })
        taskContract?.let { contract ->
            put("taskContract", contract.toJson())
            put("agentMemory", JSONObject().apply {
                put("schema", "android_agent_brain_handoff_replay_v1")
                put("taskContract", contract.toJson())
            })
        }
        put("client", "android-compose")
        put("clientVersion", "text-bootstrap-gui-loop-v3")
    }
}

private fun postCancellableAgentRequest(
    endpoint: String,
    payload: JSONObject,
    deviceId: String,
    agentSessionId: String,
    agentBrainRouteRequest: Boolean,
    activeConnection: AtomicReference<HttpURLConnection?>,
): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = if (agentBrainRouteRequest) AGENT_BRAIN_CONNECT_TIMEOUT_MS else CANCELLABLE_VISUAL_CONNECT_TIMEOUT_MS
        readTimeout = if (agentBrainRouteRequest) AGENT_BRAIN_READ_TIMEOUT_MS else CANCELLABLE_VISUAL_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty(
            "X-Client",
            if (agentBrainRouteRequest) "android-compose-agent-brain-bootstrap-v3" else "android-compose-visual-agent-v16",
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
        val routeReplay = agentBrainRouteRequest && payload.has("taskContract")
        AgentRuntimeController.noteDiagnostic(buildString {
            append(
                when {
                    routeReplay -> "AgentBrainHandoff"
                    agentBrainRouteRequest -> "AgentBrainText"
                    else -> "GUIPlusVisual"
                },
            )
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
        if (!agentBrainRouteRequest) {
            validateVisualAgentResponseObservationId(payload.optString("expectedActionObservationId"), data)
        }
        CloudAgentPlan.fromJson(data)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
            ?: throw java.io.IOException(
                if (agentBrainRouteRequest) {
                    "agent_brain_route did not return one executable agentStep"
                } else {
                    "visual_agent_step did not return one agentStep"
                },
            )
    } catch (error: SocketTimeoutException) {
        throw VisualAgentRequestException(
            httpStatus = null,
            code = if (agentBrainRouteRequest) "agent_brain_route_timeout" else "network_timeout",
            retryable = !agentBrainRouteRequest,
            backendMessage = if (agentBrainRouteRequest) {
                "Android received no complete AgentBrain route response within ${AGENT_BRAIN_READ_TIMEOUT_MS / 1000}s. No screenshot or GUI action was started."
            } else {
                "visual_agent_step timed out after ${CANCELLABLE_VISUAL_READ_TIMEOUT_MS / 1000}s"
            },
            cause = error,
        )
    } catch (error: VisualAgentRequestException) {
        if (!agentBrainRouteRequest || !error.retryable) throw error
        throw VisualAgentRequestException(
            httpStatus = error.httpStatus,
            code = error.code,
            retryable = false,
            backendMessage = "${error.backendMessage}; AgentBrain route requests are never nested-retried inside the visual loop",
            cause = error,
        )
    } catch (error: java.io.IOException) {
        if (!agentBrainRouteRequest) throw error
        throw VisualAgentRequestException(
            httpStatus = null,
            code = "agent_brain_route_failed",
            retryable = false,
            backendMessage = "AgentBrain route failed before GUI Plus started: ${error.message.orEmpty().take(240)}",
            cause = error,
        )
    } finally {
        activeConnection.compareAndSet(connection, null)
        connection.disconnect()
    }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else (bytes + 1023) / 1024
