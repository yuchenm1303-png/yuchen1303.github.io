package com.yuchen.ailedger.service

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Idempotent best-effort cleanup for every terminal visual-task path. */
internal fun AiWorkerClient.forgetVisualSessionBestEffort(
    deviceId: String,
    invocation: VisualTaskInvocation,
    terminalReason: String,
): Boolean {
    val endpointBase = endpoint.trim().trimEnd('/')
    if (endpointBase.isBlank() || invocation.sessionId.isBlank()) return false
    val payload = JSONObject().apply {
        put("action", "visual_session_forget")
        put("intent", "visual_session_forget")
        put("requestType", "visual_session_forget")
        put("visualSessionForget", true)
        put("agentSessionId", invocation.sessionId.take(180))
        put("visualTaskInvocationId", invocation.taskInvocationId.take(180))
        put("clientToolCallId", invocation.taskInvocationId.take(180))
        put("goal", invocation.goal.take(1_200))
        put("terminalReason", terminalReason.take(160))
        put("clientId", deviceId.take(120))
        put("deviceId", deviceId.take(120))
    }
    val connection = (URL(endpointBase).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5_000
        readTimeout = 6_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Client", "android-compose-visual-session-v1")
        setRequestProperty("X-Client-Id", deviceId.take(120))
        setRequestProperty("X-Device-Id", deviceId.take(120))
        applyRequestIdentityHeaders(this, stream = false)
    }
    return try {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        val status = connection.responseCode
        if (status !in 200..299) {
            val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IOException(body.take(160).ifBlank { "visual_session_forget HTTP $status" })
        }
        true
    } finally {
        connection.disconnect()
    }
}
