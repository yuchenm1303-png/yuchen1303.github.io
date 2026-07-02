package com.yuchen.ailedger.service

import android.os.Process
import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationReceipt
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import com.yuchen.ailedger.data.AssistantMemoryRequestSource
import java.util.concurrent.Executors
import org.json.JSONObject

internal object AssistantMemoryUsageBridge {
    private data class CapturedResponse(
        val requestToken: String,
        val response: JSONObject,
    )

    private val responseForCurrentThread = ThreadLocal<CapturedResponse?>()
    private val diagnosticsExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "AssistantMemoryDiagnostics",
        ).apply { isDaemon = true }
    }

    fun captureResponseJson(data: JSONObject) {
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
        val requestContext = AssistantMemoryRequestContextRuntime.consumeCurrentThread()
        val captured = responseForCurrentThread.get()
        responseForCurrentThread.remove()
        val ticket = requestContext?.ticket ?: return null
        if (!AssistantAccountSessionRuntime.isCurrent(ticket)) return null

        val response = captured
            ?.takeIf { it.requestToken == requestContext.token }
            ?.response
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
        val requestContext = AssistantMemoryRequestContextRuntime.consumeCurrentThread()
        responseForCurrentThread.remove()
        val ticket = requestContext?.ticket ?: return
        if (!AssistantAccountSessionRuntime.isCurrent(ticket)) return

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
}
