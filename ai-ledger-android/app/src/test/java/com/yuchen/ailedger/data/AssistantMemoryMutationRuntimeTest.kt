package com.yuchen.ailedger.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryMutationRuntimeTest {
    @Test
    fun nestedFinalReceiptIsParsedWithoutFlatteningTheResponse() {
        val data = JSONObject().put(
            "final",
            JSONObject()
                .put("memoryRequestId", "request-1")
                .put(
                    "memoryMutation",
                    JSONObject()
                        .put("schema", "ai_ledger_memory_mutation_receipt_v1")
                        .put("operationId", "operation-nested-1")
                        .put("action", "upsert")
                        .put("status", "applied")
                        .put("applied", true)
                        .put("affectedCount", 1)
                        .put("resultIds", JSONArray().put("memory-1")),
                ),
        )

        val receipt = data.findAssistantMemoryMutationReceipt()

        assertNotNull(receipt)
        assertEquals("operation-nested-1", receipt?.operationId)
        assertEquals("request-1", receipt?.requestId)
        assertEquals("upsert", receipt?.action)
        assertTrue(receipt?.inventoryMayHaveChanged == true)
        assertEquals(listOf("memory-1"), receipt?.resultIds)
    }

    @Test
    fun flattenedCompatibilityFieldsRemainSupported() {
        val data = JSONObject()
            .put("memoryRequestId", "request-flat")
            .put("memoryMutationOperationId", "operation-flat-1")
            .put("memoryMutationAction", "delete")
            .put("memoryMutationStatus", "applied")
            .put("memoryMutationApplied", true)
            .put("memoryMutationAffectedCount", 2)

        val receipt = data.findAssistantMemoryMutationReceipt()

        assertNotNull(receipt)
        assertEquals("operation-flat-1", receipt?.operationId)
        assertEquals("delete", receipt?.action)
        assertEquals(2, receipt?.affectedCount)
        assertTrue(receipt?.succeeded == true)
    }

    @Test
    fun oneOperationIdOnlySchedulesOneInventoryRefresh() {
        val receipt = AssistantMemoryMutationReceipt(
            operationId = "operation-dedupe-${System.nanoTime()}",
            action = "upsert",
            status = "applied",
            applied = true,
        )

        assertTrue(AssistantMemoryMutationRuntime.markInventoryRefreshNeeded(receipt))
        assertFalse(AssistantMemoryMutationRuntime.markInventoryRefreshNeeded(receipt))
    }

    @Test
    fun noopAndClarificationDoNotPretendThatInventoryChanged() {
        val noop = AssistantMemoryMutationReceipt(
            operationId = "operation-noop-${System.nanoTime()}",
            action = "upsert",
            status = "noop",
        )
        val clarification = AssistantMemoryMutationReceipt(
            operationId = "operation-clarify-${System.nanoTime()}",
            action = "delete",
            status = "confirmation_required",
            requiresClarification = true,
        )

        assertTrue(noop.succeeded)
        assertFalse(noop.inventoryMayHaveChanged)
        assertFalse(AssistantMemoryMutationRuntime.markInventoryRefreshNeeded(noop))
        assertFalse(clarification.succeeded)
        assertFalse(clarification.inventoryMayHaveChanged)
    }

    @Test
    fun candidateFieldsAcceptBackendCamelCaseAndSnakeCase() {
        val data = JSONObject().put(
            "memoryMutation",
            JSONObject()
                .put("operationId", "operation-candidates-1")
                .put("action", "delete")
                .put("status", "confirmation_required")
                .put(
                    "candidates",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "memory-a")
                                .put("content", "第一条候选")
                                .put("namespaceType", "account")
                                .put("subjectKey", "food.preference")
                                .put("retrievalScore", 0.82),
                        )
                        .put(
                            JSONObject()
                                .put("id", "memory-b")
                                .put("content", "第二条候选")
                                .put("namespace_type", "project")
                                .put("subject_key", "android.project")
                                .put("retrieval_score", 0.63),
                        ),
                ),
        )

        val receipt = data.findAssistantMemoryMutationReceipt()

        assertEquals(2, receipt?.candidates?.size)
        assertEquals("account", receipt?.candidates?.get(0)?.namespaceType)
        assertEquals("project", receipt?.candidates?.get(1)?.namespaceType)
        assertEquals(0.82, receipt?.candidates?.get(0)?.retrievalScore ?: 0.0, 0.0001)
        assertEquals(0.63, receipt?.candidates?.get(1)?.retrievalScore ?: 0.0, 0.0001)
    }

    @Test
    fun ordinaryMemoryRetrievalResponseDoesNotCreateMutationReceipt() {
        val data = JSONObject()
            .put("memoryStatus", "ready")
            .put("memoryUsed", true)

        assertNull(data.findAssistantMemoryMutationReceipt())
    }
}
