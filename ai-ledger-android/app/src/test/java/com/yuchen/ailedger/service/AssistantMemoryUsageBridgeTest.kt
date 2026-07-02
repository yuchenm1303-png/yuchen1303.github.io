package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantMemoryDiagnosticItem
import com.yuchen.ailedger.data.AssistantMemoryDiagnosticRecord
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRequestContextRuntime
import com.yuchen.ailedger.data.switchAccount
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantMemoryUsageBridgeTest {
    @Before
    fun resetBefore() {
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        val ticket = AssistantAccountSessionRuntime.updateUser("user-a")
        AssistantMemoryMutationRuntime.switchAccount(ticket)
        AssistantMemoryDiagnostics.switchAccount(ticket)
    }

    @After
    fun resetAfter() {
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryMutationRuntime.switchAccount(null)
        AssistantMemoryDiagnostics.switchAccount(null)
    }

    @Test
    fun androidDoesNotMutateCloudMemoryPayload() {
        val payload = JSONObject()
            .put("memoryEnabled", true)
            .put("memoryMode", "auto")
            .put("memoryContextDiagnostics", JSONObject().put("selectionOwner", "backend_cloud_v4"))
        val before = payload.toString()

        AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)

        assertEquals(before, payload.toString())
    }

    @Test
    fun fallbackAttemptCannotReusePreviousEndpointsPartialReceipt() {
        AssistantMemoryRequestContextRuntime.stageCurrentThread()
        AssistantMemoryUsageBridge.beginTransportAttempt()
        AssistantMemoryUsageBridge.captureResponseJson(
            responseWithMutation("operation-old", "router_failed"),
        )

        AssistantMemoryUsageBridge.beginTransportAttempt()
        AssistantMemoryUsageBridge.captureResponseJson(
            responseWithMutation("operation-final", "applied"),
        )

        val receipt = AssistantMemoryUsageBridge.recordSuccessfulPayload(
            JSONObject().put("requestId", "request-final").put("message", "记住测试内容"),
        )

        assertNotNull(receipt)
        assertEquals("operation-final", receipt?.operationId)
        assertEquals("applied", receipt?.status)
    }

    @Test
    fun diagnosticJsonKeepsCandidatePathAndScore() {
        val item = AssistantMemoryDiagnosticItem(
            id = "memory-test",
            stage = "dynamic",
            content = "测试记忆",
            retrievalScore = 0.82,
            disposition = "selected",
        )
        val record = AssistantMemoryDiagnosticRecord(
            requestId = "request-test",
            createdAtMillis = 1L,
            prompt = "测试问题",
            replyPreview = "",
            model = "test-model",
            backendVersion = "v176",
            requestMode = "auto",
            requestSchema = "ai_ledger_cloud_memory_request_v3",
            requestEnabled = true,
            memoryUsed = true,
            memoryStatus = "ready",
            memorySource = "cloud_hybrid_single_selection",
            degraded = false,
            itemCount = 1,
            gateStatus = "ok",
            budgetLevel = "normal",
            retrievalStatus = "ok",
            embeddingStatus = "ok",
            rerankStatus = "ok",
            candidateCount = 1,
            anchorCandidateCount = 0,
            dynamicCandidateCount = 1,
            filteredHistoryCount = 0,
            filteredSensitiveCount = 0,
            totalMs = 120L,
            stageTimings = mapOf("dynamicMs" to 30L),
            selectedItems = listOf(item),
            anchorCandidates = emptyList(),
            dynamicCandidates = listOf(item),
            mergedCandidates = listOf(item),
            error = "",
            traceAvailable = true,
        )

        val json = record.toJson()
        val dynamic = json.getJSONArray("dynamicCandidates").getJSONObject(0)

        assertEquals("request-test", json.getString("requestId"))
        assertEquals("memory-test", dynamic.getString("id"))
        assertEquals(0.82, dynamic.getDouble("retrievalScore"), 0.0001)
        assertTrue(json.getBoolean("traceAvailable"))
    }

    private fun responseWithMutation(operationId: String, status: String): JSONObject {
        return JSONObject()
            .put("memoryRequestId", "request-final")
            .put("memoryStatus", "mutation_only_not_retrieved")
            .put(
                "memoryMutation",
                JSONObject()
                    .put("operationId", operationId)
                    .put("action", "upsert")
                    .put("status", status)
                    .put("applied", status == "applied"),
            )
    }
}
