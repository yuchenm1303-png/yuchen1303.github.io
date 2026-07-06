package com.yuchen.ailedger.service

import android.os.Process
import android.os.SystemClock
import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationReceipt
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import com.yuchen.ailedger.data.AssistantMemoryRequestSource
import com.yuchen.ailedger.data.record
import java.util.concurrent.Executors
import org.json.JSONObject

internal object AssistantMemoryUsageBridge {
    private data class CapturedResponse(
        val requestToken: String,
        val response: JSONObject,
    )

    private val responseForCurrentThread = ThreadLocal<CapturedResponse?>()
    private val analyticsResponseForCurrentThread = ThreadLocal<JSONObject?>()
    private val analyticsAttemptStartedAt = ThreadLocal<Long?>()
    private val analyticsRequestBytes = ThreadLocal<Long?>()
    private val analyticsResponseBytes = ThreadLocal<Long?>()
    private val analyticsOwnerForCurrentThread = ThreadLocal<String?>()
    private val diagnosticsExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "AssistantMemoryDiagnostics",
        ).apply { isDaemon = true }
    }

    fun beginTransportAttempt() {
        responseForCurrentThread.remove()
        analyticsResponseForCurrentThread.remove()
        analyticsRequestBytes.remove()
        analyticsResponseBytes.remove()
        analyticsOwnerForCurrentThread.remove()
        AiLedgerApplication.contextOrNull()?.let { context ->
            analyticsOwnerForCurrentThread.set(
                AgentAnalyticsOwnerRuntime.currentStorageKey(context),
            )
        }
        analyticsAttemptStartedAt.set(SystemClock.elapsedRealtime())
    }

    fun captureRequestBytes(byteCount: Int) {
        analyticsRequestBytes.set(byteCount.toLong().coerceAtLeast(0L))
    }

    fun addResponseBytes(byteCount: Int) {
        if (byteCount <= 0) return
        val current = analyticsResponseBytes.get() ?: 0L
        val delta = byteCount.toLong()
        analyticsResponseBytes.set(
            if (Long.MAX_VALUE - current < delta) Long.MAX_VALUE else current + delta,
        )
    }

    fun captureResponseJson(data: JSONObject) {
        captureAnalyticsResponse(data)

        val requestContext = AssistantMemoryRequestContextRuntime.peekCurrentThread() ?: return
        val candidate = bestMemoryEnvelope(data) ?: return
        val current = responseForCurrentThread.get()
        val merged = if (current?.requestToken == requestContext.token) {
            JSONObject(current.response.toString())
        } else {
            JSONObject()
        }
        candidate.keys().forEach { key ->
            merged.put(key, deepCopyMemoryEnvelopeValue(candidate.opt(key)))
        }
        responseForCurrentThread.set(CapturedResponse(requestContext.token, merged))
    }

    fun recordSuccessfulPayload(payload: JSONObject): AssistantMemoryMutationReceipt? {
        recordAnalyticsTransport(payload = payload, success = true)

        val requestContext = AssistantMemoryRequestContextRuntime.consumeCurrentThread()
        val captured = responseForCurrentThread.get()
        responseForCurrentThread.remove()
        val response = captured
            ?.takeIf { requestContext == null || it.requestToken == requestContext.token }
            ?.response
        val ticket = requestContext?.ticket
        if (ticket == null || !AssistantAccountSessionRuntime.isCurrent(ticket)) {
            recordDiagnosticsWithoutTicket(payload = payload, response = response, failure = null)
            return null
        }

        val mutationReceipt = response?.let { AssistantMemoryMutationRuntime.captureResponse(it, ticket) }
        val appContext = AiLedgerApplication.contextOrNull()
        if (
            requestContext.source == AssistantMemoryRequestSource.Chat &&
            mutationReceipt != null &&
            appContext != null &&
            AssistantMemoryMutationRuntime.markInventoryRefreshNeeded(mutationReceipt, ticket)
        ) {
            AssistantMemoryRepository.get(appContext)
                .refreshAfterCloudMutation(mutationReceipt, ticket)
        }

        val payloadSnapshot = compactMemoryDiagnosticPayload(payload)
        val responseSnapshot = response?.let(::compactMemoryDiagnosticResponse)
        diagnosticsExecutor.execute {
            runCatching {
                AssistantMemoryDiagnostics.record(
                    ticket = ticket,
                    payload = payloadSnapshot,
                    response = responseSnapshot,
                )
            }
        }
        return mutationReceipt
    }

    fun recordFailedPayload(payload: JSONObject, error: Throwable) {
        recordAnalyticsTransport(payload = payload, success = false)

        val requestContext = AssistantMemoryRequestContextRuntime.consumeCurrentThread()
        val captured = responseForCurrentThread.get()
        responseForCurrentThread.remove()
        val response = captured
            ?.takeIf { requestContext == null || it.requestToken == requestContext.token }
            ?.response
        val ticket = requestContext?.ticket
        if (ticket == null || !AssistantAccountSessionRuntime.isCurrent(ticket)) {
            recordDiagnosticsWithoutTicket(payload = payload, response = response, failure = error)
            return
        }

        val payloadSnapshot = compactMemoryDiagnosticPayload(payload)
        diagnosticsExecutor.execute {
            runCatching {
                AssistantMemoryDiagnostics.record(
                    ticket = ticket,
                    payload = payloadSnapshot,
                    response = null,
                    failure = error,
                )
            }
        }
    }

    private fun recordDiagnosticsWithoutTicket(
        payload: JSONObject,
        response: JSONObject?,
        failure: Throwable?,
    ) {
        if (
            !payload.has("memoryMode") &&
            !payload.has("memoryEnabled") &&
            !payload.has("memoryRequest") &&
            response == null &&
            failure == null
        ) return
        val payloadSnapshot = compactMemoryDiagnosticPayload(payload)
        val responseSnapshot = response?.let(::compactMemoryDiagnosticResponse)
        diagnosticsExecutor.execute {
            runCatching {
                AssistantMemoryDiagnostics.record(
                    payload = payloadSnapshot,
                    response = responseSnapshot,
                    failure = failure,
                )
            }
        }
    }

    private fun captureAnalyticsResponse(data: JSONObject) {
        val compact = compactAnalyticsEnvelope(data, depth = 0) ?: return
        val merged = analyticsResponseForCurrentThread.get() ?: JSONObject()
        mergeAnalyticsEnvelope(target = merged, source = compact)
        analyticsResponseForCurrentThread.set(merged)
    }

    private fun compactAnalyticsEnvelope(source: JSONObject, depth: Int): JSONObject? {
        if (depth > MAX_ANALYTICS_ENVELOPE_DEPTH) return null
        val result = JSONObject()

        ANALYTICS_DIRECT_KEYS.forEach { key ->
            if (source.has(key)) {
                result.put(key, deepCopyMemoryEnvelopeValue(source.opt(key)))
            }
        }
        if (
            source.optJSONArray("sources")?.length()?.let { it > 0 } == true ||
            source.optJSONArray("webSources")?.length()?.let { it > 0 } == true
        ) {
            result.put("searchUsed", true)
        }
        ANALYTICS_ENVELOPE_KEYS.forEach { key ->
            source.optJSONObject(key)?.let { child ->
                compactAnalyticsEnvelope(child, depth + 1)?.let { compactChild ->
                    result.put(key, compactChild)
                }
            }
        }
        return result.takeIf { it.length() > 0 }
    }

    private fun mergeAnalyticsEnvelope(target: JSONObject, source: JSONObject) {
        source.keys().forEach { key ->
            val incomingObject = source.optJSONObject(key)
            val existingObject = target.optJSONObject(key)
            if (
                key in ANALYTICS_ENVELOPE_KEYS &&
                incomingObject != null &&
                existingObject != null
            ) {
                mergeAnalyticsEnvelope(existingObject, incomingObject)
            } else {
                target.put(key, deepCopyMemoryEnvelopeValue(source.opt(key)))
            }
        }
    }

    private fun recordAnalyticsTransport(payload: JSONObject, success: Boolean) {
        val response = analyticsResponseForCurrentThread.get()
        val startedAt = analyticsAttemptStartedAt.get()
        val requestBytes = analyticsRequestBytes.get()
        val responseBytes = analyticsResponseBytes.get()
        val ownerStorageKey = analyticsOwnerForCurrentThread.get()
        analyticsResponseForCurrentThread.remove()
        analyticsAttemptStartedAt.remove()
        analyticsRequestBytes.remove()
        analyticsResponseBytes.remove()
        analyticsOwnerForCurrentThread.remove()
        val durationMs = startedAt?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) } ?: 0L
        runCatching {
            AgentAnalyticsRuntime.recordChatTransport(
                payload = payload,
                response = response,
                success = success,
                durationMs = durationMs,
                requestBytes = requestBytes ?: -1L,
                responseBytes = responseBytes ?: -1L,
                ownerStorageKey = ownerStorageKey,
            )
        }
    }

    private fun bestMemoryEnvelope(data: JSONObject): JSONObject? {
        return sequenceOf(
            data,
            data.optJSONObject("response"),
            data.optJSONObject("final"),
            data.optJSONObject("data"),
            data.optJSONObject("result"),
        )
            .filterNotNull()
            .map { envelope -> envelope to memoryMetadataScore(envelope) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun memoryMetadataScore(data: JSONObject): Int {
        var score = 0
        if (data.has("memoryRequestId")) score += 2
        if (data.has("memoryStatus")) score += 4
        if (data.has("memoryUsed")) score += 2
        if (data.has("memoryTrace")) score += 8
        if (data.has("memoryMutation")) score += 8
        if (data.has("memoryMutationStatus")) score += 6
        if (data.has("memoryMutationOperationId")) score += 3
        if (data.has("memoryMutationStageTimings")) score += 3
        return score
    }

    private fun deepCopyMemoryEnvelopeValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is JSONObject -> JSONObject(value.toString())
        is org.json.JSONArray -> org.json.JSONArray(value.toString())
        is String, is Number, is Boolean -> value
        else -> value.toString()
    }

    private val ANALYTICS_ENVELOPE_KEYS = setOf(
        "data", "result", "response", "final", "metadata", "meta", "output",
    )
    private val ANALYTICS_DIRECT_KEYS = setOf(
        "model", "modelId", "model_id", "providerModel",
        "modelLabel", "modelName", "model_name",
        "usage", "tokenUsage", "token_usage", "usageMetadata", "tokenUsageMetadata",
        "searchUsed", "webSearchUsed",
        "agentAction", "mobileAction", "preferenceUpdate", "structuredData",
        "reply", "answer", "text", "content", "rawModelOutput",
    )
    private const val MAX_ANALYTICS_ENVELOPE_DEPTH = 3
}
