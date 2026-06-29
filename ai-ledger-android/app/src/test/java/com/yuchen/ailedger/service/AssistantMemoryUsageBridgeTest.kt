package com.yuchen.ailedger.service

import com.yuchen.ailedger.data.AssistantMemoryDiagnosticItem
import com.yuchen.ailedger.data.AssistantMemoryDiagnosticRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryUsageBridgeTest {
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
}
