package com.yuchen.ailedger.service

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

internal data class VisualCompletionAckResult(
    val acknowledged: Boolean,
    val reason: String,
)

/** Best-effort cleanup acknowledgement. Local task completion never depends on this network call. */
internal fun AiWorkerClient.acknowledgeVisualCompletion(
    goal: String,
    deviceId: String,
    permit: VisualCompletionPermit,
): VisualCompletionAckResult {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank()) return VisualCompletionAckResult(false, "endpoint_missing")
    val payload = JSONObject().apply {
        put("action", "visual_completion_ack")
        put("intent", "visual_completion_ack")
        put("requestType", "visual_completion_ack")
        put("visualCompletionAck", true)
        put("goal", goal.trim().take(240))
        put("agentGoal", goal.trim().take(240))
        put("agentSessionId", permit.sessionId)
        put("sessionId", permit.sessionId)
        put("deviceId", deviceId.trim().take(120))
        put("clientId", deviceId.trim().take(120))
        put("completionPermitId", permit.id)
        put("completionPermitKind", permit.kind)
        put("completionPermitObservationId", permit.observationId)
        put("completionPermitActionHash", permit.actionHash)
        put("completionCandidateId", permit.candidate.id)
        put("completionCandidateObservationId", permit.candidate.observationId)
    }
    val connection = (URL(endpointBase).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = ACK_CONNECT_TIMEOUT_MS
        readTimeout = ACK_READ_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-completion-ack")
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        setRequestProperty("X-Agent-Session-Id", permit.sessionId.take(120))
    }
    return try {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val data = runCatching { JSONObject(body) }.getOrNull()
        val acknowledged = status in 200..299 && data?.optBoolean("acknowledged", false) == true
        VisualCompletionAckResult(
            acknowledged = acknowledged,
            reason = if (acknowledged) "acknowledged" else data?.optString("code").orEmpty().ifBlank { "http_$status" },
        )
    } catch (error: Throwable) {
        VisualCompletionAckResult(false, error.message.orEmpty().take(180).ifBlank { "ack_failed" })
    } finally {
        connection.disconnect()
    }
}

private const val ACK_CONNECT_TIMEOUT_MS = 4_000
private const val ACK_READ_TIMEOUT_MS = 6_000
