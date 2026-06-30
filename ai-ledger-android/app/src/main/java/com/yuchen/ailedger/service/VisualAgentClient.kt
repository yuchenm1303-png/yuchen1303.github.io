package com.yuchen.ailedger.service

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONObject

private const val VISUAL_AGENT_CONNECT_TIMEOUT_MS = 8_000
private const val VISUAL_AGENT_READ_TIMEOUT_MS = 25_000
private const val VISUAL_AGENT_SESSION_PROTOCOL = "android_visual_agent_v15_unified_execution_permit"

internal object VisualAgentProtocol {
    const val coordinateProtocol = "normalized_screen_0_1"
    const val appIdentityProtocol = "package_name_v2"

    /** Single source of truth shared by parser, validator, advertised protocol and executor. */
    val supportedStepTypes: Set<String> = CloudAgentStep.supportedTypes
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

class VisualAgentRequestException(
    val httpStatus: Int?,
    val code: String,
    val retryable: Boolean,
    val backendMessage: String,
    cause: Throwable? = null,
) : IOException(buildVisualAgentErrorMessage(httpStatus, code, retryable, backendMessage), cause)

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
    deviceProfile: AgentDeviceProfile? = null,
    runtimeContext: VisualAgentRuntimeContext? = null,
    taskMemory: VisualTaskMemory? = null,
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
        deviceProfile = deviceProfile,
        runtimeContext = runtimeContext,
        taskMemory = taskMemory,
    )
    VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordModelRequestPayload(payload)
    return postVisualAgentStep(endpointBase, payload, deviceId, agentSessionId)
}

/**
 * The one and only visual request builder.
 *
 * Android contributes objective device, frame, execution and user-revision facts only. It does not
 * calculate reasoning depth, semantic progress, repeated-action policy, route strategy or GUI Plus
 * replanning signals.
 */
internal fun buildVisualAgentPayload(
    goal: String,
    snapshot: AgentScreenSnapshot,
    recentActions: List<String>,
    visualHistory: List<VisualAgentHistoryItem> = emptyList(),
    appContext: List<VisualAgentAppContextItem> = emptyList(),
    deviceId: String = "android-compose-visual",
    agentSessionId: String = "visual-session-test",
    executionMode: AgentExecutionMode = AgentExecutionMode.ExplicitAgent,
    deviceProfile: AgentDeviceProfile? = null,
    runtimeContext: VisualAgentRuntimeContext? = null,
    taskMemory: VisualTaskMemory? = null,
): JSONObject = buildLeanVisualAgentPayload(
    goal = goal,
    snapshot = snapshot,
    recentActions = recentActions,
    visualHistory = visualHistory,
    appContext = appContext,
    deviceId = deviceId,
    agentSessionId = agentSessionId,
    executionMode = executionMode,
    runtimeContext = runtimeContext,
    taskMemory = taskMemory,
).apply {
    deviceProfile?.let { profile ->
        put("deviceProfile", JSONObject().apply {
            put("schema", "android_device_profile_v1")
            put("manufacturer", profile.manufacturer.take(60))
            put("brand", profile.brand.take(60))
            put("model", profile.model.take(80))
            put("androidRelease", profile.release.take(40))
            put("sdkInt", profile.sdkInt)
            put("buildDisplay", profile.display.take(100))
        })
    }
}

@Throws(IOException::class)
internal fun validateVisualAgentResponseObservationId(
    expectedObservationId: String,
    data: JSONObject?,
): String {
    val expected = expectedObservationId.trim()
    if (expected.isBlank()) throw IOException("visual_agent_step request is missing expectedActionObservationId")
    if (data == null) throw IOException("visual_agent_step returned invalid JSON without an observationId")
    val envelopes = buildList {
        add(data)
        data.optJSONObject("data")?.let(::add)
        data.optJSONObject("result")?.let(::add)
        data.optJSONObject("agentPlan")?.let(::add)
    }
    val echoedIds = linkedSetOf<String>()
    envelopes.forEach { envelope ->
        listOf("expectedActionObservationId", "actionObservationId", "observationId")
            .map { envelope.optString(it).trim() }
            .filterTo(echoedIds, String::isNotBlank)
        envelope.optJSONObject("verifiedSurfaceProtocol")?.optString("observationId")?.trim()
            ?.takeIf(String::isNotBlank)?.let(echoedIds::add)
        envelope.optJSONObject("runtimeExecutionContext")?.optString("observationId")?.trim()
            ?.takeIf(String::isNotBlank)?.let(echoedIds::add)
    }
    if (echoedIds.isEmpty()) throw IOException("visual_agent_step response did not echo expectedActionObservationId")
    if (echoedIds.size != 1) throw IOException("visual_agent_step response contains conflicting observationIds")
    val actual = echoedIds.single()
    if (actual != expected) throw IOException("visual_agent_step returned a stale observationId")
    return actual
}

private fun AiWorkerClient.postVisualAgentStep(
    endpoint: String,
    payload: JSONObject,
    deviceId: String,
    agentSessionId: String,
): CloudAgentPlan {
    val requestStart = SystemClock.elapsedRealtime()
    var requestByteCount = 0
    var terminalDiagnosticRecorded = false
    val diagnostics = VisualIntelligenceDiagnosticsStore.currentOrNull()
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = VISUAL_AGENT_CONNECT_TIMEOUT_MS
        readTimeout = VISUAL_AGENT_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-agent-v15-unified-permit")
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        setRequestProperty("X-Agent-Session-Protocol", VISUAL_AGENT_SESSION_PROTOCOL)
        setRequestProperty("X-Agent-Session-Id", agentSessionId.take(120))
        AiWorkerRequestIdentity.applyTo(
            connection = this,
            appClientToken = AiWorkerRequestIdentity.defaultAppClientToken(),
            mode = AiWorkerIdentityMode.AppOnly,
        )
    }
    return try {
        val requestBytes = payload.toString().toByteArray(Charsets.UTF_8)
        requestByteCount = requestBytes.size
        connection.outputStream.use { it.write(requestBytes) }
        val status = connection.responseCode
        val body = connection.visualAgentReadBody(status)
        val responseByteCount = body.toByteArray(Charsets.UTF_8).size
        val data = body.visualAgentJsonOrNull()
        val workerVersion = connection.getHeaderField("X-AI-Ledger-Worker-Version").orEmpty().take(48)
        val routeProtocol = connection.getHeaderField("X-AI-Ledger-Route-Protocol").orEmpty().take(48)
        val durationMs = SystemClock.elapsedRealtime() - requestStart
        AgentRuntimeController.noteDiagnostic(buildString {
            append("VisualDirect q=").append(visualAgentBytesToKb(requestBytes.size)).append("K")
            append(" r=").append(visualAgentBytesToKb(responseByteCount)).append("K")
            append(" h=").append(durationMs)
            if (workerVersion.isNotBlank()) append(" w=").append(workerVersion)
            if (routeProtocol.isNotBlank()) append(" p=").append(routeProtocol)
        })

        if (status !in 200..299) {
            diagnostics?.recordModelTransportResponse(
                httpStatus = status,
                body = body,
                requestBytes = requestByteCount,
                responseBytes = responseByteCount,
                durationMs = durationMs,
                workerVersion = workerVersion,
                routeProtocol = routeProtocol,
                parseOutcome = "http_error",
                parsedStepType = "",
                observationIdValid = false,
            )
            terminalDiagnosticRecorded = true
            throw parseVisualAgentHttpFailure(status, body)
        }

        val observationValidation = runCatching {
            validateVisualAgentResponseObservationId(payload.optString("expectedActionObservationId"), data)
        }
        if (observationValidation.isFailure) {
            diagnostics?.recordModelTransportResponse(
                httpStatus = status,
                body = body,
                requestBytes = requestByteCount,
                responseBytes = responseByteCount,
                durationMs = durationMs,
                workerVersion = workerVersion,
                routeProtocol = routeProtocol,
                parseOutcome = "observation_id_invalid",
                parsedStepType = "",
                observationIdValid = false,
            )
            terminalDiagnosticRecorded = true
            throw (observationValidation.exceptionOrNull() as? IOException
                ?: IOException("visual_agent_step observation validation failed", observationValidation.exceptionOrNull()))
        }

        val plan = CloudAgentPlan.fromJson(data)
            ?: CloudAgentStep.fromJson(data)?.let { CloudAgentPlan(step = it, state = CloudAgentState.fromJson(data)) }
        if (plan == null) {
            diagnostics?.recordModelTransportResponse(
                httpStatus = status,
                body = body,
                requestBytes = requestByteCount,
                responseBytes = responseByteCount,
                durationMs = durationMs,
                workerVersion = workerVersion,
                routeProtocol = routeProtocol,
                parseOutcome = if (data == null) "invalid_json" else "agent_step_missing",
                parsedStepType = "",
                observationIdValid = true,
            )
            terminalDiagnosticRecorded = true
            throw IOException("visual_agent_step did not return one agentStep")
        }

        diagnostics?.recordModelTransportResponse(
            httpStatus = status,
            body = body,
            requestBytes = requestByteCount,
            responseBytes = responseByteCount,
            durationMs = durationMs,
            workerVersion = workerVersion,
            routeProtocol = routeProtocol,
            parseOutcome = "ok",
            parsedStepType = plan.step.type,
            observationIdValid = true,
        )
        terminalDiagnosticRecorded = true
        plan
    } catch (error: SocketTimeoutException) {
        if (!terminalDiagnosticRecorded) {
            diagnostics?.recordModelTransportFailure(
                code = "network_timeout",
                message = error.message.orEmpty(),
                durationMs = SystemClock.elapsedRealtime() - requestStart,
                requestBytes = requestByteCount,
            )
        }
        throw VisualAgentRequestException(
            httpStatus = null,
            code = "network_timeout",
            retryable = true,
            backendMessage = "visual_agent_step timed out after ${VISUAL_AGENT_READ_TIMEOUT_MS / 1000}s",
            cause = error,
        )
    } catch (error: IOException) {
        if (!terminalDiagnosticRecorded) {
            diagnostics?.recordModelTransportFailure(
                code = (error as? VisualAgentRequestException)?.code ?: "io_error",
                message = error.message.orEmpty(),
                durationMs = SystemClock.elapsedRealtime() - requestStart,
                requestBytes = requestByteCount,
            )
        }
        throw error
    } finally {
        connection.disconnect()
    }
}

internal fun parseVisualAgentHttpFailure(status: Int, body: String): VisualAgentRequestException {
    val data = body.visualAgentJsonOrNull()
    val errorObject = data?.optJSONObject("error")
    val code = listOfNotNull(
        data?.visualAgentString("code"),
        errorObject?.visualAgentString("code"),
        data?.visualAgentString("errorCode"),
        errorObject?.visualAgentString("errorCode"),
    ).firstOrNull(String::isNotBlank) ?: "http_$status"
    val message = listOfNotNull(
        data?.visualAgentString("message"),
        errorObject?.visualAgentString("message"),
        data?.visualAgentString("error"),
        errorObject?.visualAgentString("error"),
        body.trim().take(240),
    ).firstOrNull(String::isNotBlank) ?: "visual_agent_step HTTP $status"
    val explicitRetryable = when {
        data?.has("retryable") == true -> data.optBoolean("retryable")
        errorObject?.has("retryable") == true -> errorObject.optBoolean("retryable")
        else -> null
    }
    return VisualAgentRequestException(
        httpStatus = status,
        code = code.take(120),
        retryable = explicitRetryable ?: status in RETRYABLE_VISUAL_AGENT_HTTP_STATUSES,
        backendMessage = message.take(320),
    )
}

private fun buildVisualAgentErrorMessage(
    httpStatus: Int?,
    code: String,
    retryable: Boolean,
    backendMessage: String,
): String = buildString {
    append(backendMessage.ifBlank { "visual_agent_step failed" }).append(" [")
    httpStatus?.let { append("HTTP ").append(it).append(", ") }
    append("code=").append(code.ifBlank { "unknown" })
    append(", retryable=").append(retryable).append(']')
}

private fun JSONObject.visualAgentString(key: String): String? =
    (opt(key) as? String)?.trim()?.takeIf(String::isNotBlank)

private fun HttpURLConnection.visualAgentReadBody(status: Int): String {
    val stream = if (status in 200..299) inputStream else errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun String.visualAgentJsonOrNull(): JSONObject? =
    try {
        takeIf(String::isNotBlank)?.let(::JSONObject)
    } catch (_: Exception) {
        null
    }

private fun visualAgentBytesToKb(bytes: Int): Int = if (bytes <= 0) 0 else (bytes + 1023) / 1024

private val RETRYABLE_VISUAL_AGENT_HTTP_STATUSES = setOf(408, 425, 429, 500, 502, 503, 504)
