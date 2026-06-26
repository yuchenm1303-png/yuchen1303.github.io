package com.yuchen.ailedger.service

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val CANCELLABLE_VISUAL_CONNECT_TIMEOUT_MS = 8_000
private const val CANCELLABLE_VISUAL_READ_TIMEOUT_MS = 25_000
private const val CANCELLABLE_VISUAL_STOP_POLL_MS = 50L
private const val CANCELLABLE_VISUAL_SESSION_PROTOCOL = "android_visual_agent_v14_task_contract_harness"

/**
 * Runs one visual planning request on Dispatchers.IO while keeping the active connection reachable
 * from the task-stop watcher. Disconnecting the HttpURLConnection unblocks upload/read immediately;
 * normal requests keep the same payload, headers and timeout policy as the legacy synchronous path.
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
    val payload = buildVisualAgentPayload(
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
    )
    val activeConnection = AtomicReference<HttpURLConnection?>(null)
    val request = async(Dispatchers.IO) {
        postCancellableVisualAgentStep(
            endpoint = endpointBase,
            payload = payload,
            deviceId = deviceId,
            agentSessionId = agentSessionId,
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
    try {
        request.await()
    } finally {
        stopWatcher.cancel()
        activeConnection.getAndSet(null)?.disconnect()
    }
}

private fun postCancellableVisualAgentStep(
    endpoint: String,
    payload: JSONObject,
    deviceId: String,
    agentSessionId: String,
    activeConnection: AtomicReference<HttpURLConnection?>,
): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = CANCELLABLE_VISUAL_CONNECT_TIMEOUT_MS
        readTimeout = CANCELLABLE_VISUAL_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-agent-v14-task-contract")
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        setRequestProperty("X-Agent-Session-Protocol", CANCELLABLE_VISUAL_SESSION_PROTOCOL)
        setRequestProperty("X-Agent-Session-Id", agentSessionId.take(120))
    }
    activeConnection.set(connection)
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(requestBytes.size)
        connection.outputStream.use { it.write(requestBytes) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val data = body.takeIf(String::isNotBlank)?.let { runCatching(::JSONObject).getOrNull() }
        val workerVersion = connection.getHeaderField("X-AI-Ledger-Worker-Version").orEmpty().take(48)
        val routeProtocol = connection.getHeaderField("X-AI-Ledger-Route-Protocol").orEmpty().take(48)
        AgentRuntimeController.noteDiagnostic(buildString {
            append("VisualDirect q=").append(bytesToKb(requestBytes.size)).append("K")
            append(" r=").append(bytesToKb(body.length)).append("K")
            append(" h=").append(SystemClock.elapsedRealtime() - requestStart)
            if (workerVersion.isNotBlank()) append(" w=").append(workerVersion)
            if (routeProtocol.isNotBlank()) append(" p=").append(routeProtocol)
        })
        if (status !in 200..299) throw parseVisualAgentHttpFailure(status, body)
        validateVisualAgentResponseObservationId(payload.optString("expectedActionObservationId"), data)
        CloudAgentPlan.fromJson(data)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
            ?: throw java.io.IOException("visual_agent_step did not return one agentStep")
    } catch (error: SocketTimeoutException) {
        throw VisualAgentRequestException(
            httpStatus = null,
            code = "network_timeout",
            retryable = true,
            backendMessage = "visual_agent_step timed out after ${CANCELLABLE_VISUAL_READ_TIMEOUT_MS / 1000}s",
            cause = error,
        )
    } finally {
        activeConnection.compareAndSet(connection, null)
        connection.disconnect()
    }
}

private fun bytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else (bytes + 1023) / 1024
