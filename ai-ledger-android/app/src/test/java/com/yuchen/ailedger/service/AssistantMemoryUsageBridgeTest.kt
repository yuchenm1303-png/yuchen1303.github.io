package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryUsageBridgeTest {
    @Test
    fun extractsDistinctSelectedMemoryIds() {
        val payload = JSONObject().put(
            "memoryContextDiagnostics",
            JSONObject().put(
                "selectedMemoryIds",
                JSONArray(listOf("first", "", "first", " second ")),
            ),
        )

        assertEquals(
            listOf("first", "second"),
            AssistantMemoryUsageBridge.selectedIdsFromPayload(payload),
        )
    }

    @Test
    fun missingDiagnosticsProducesNoUsageIds() {
        assertTrue(
            AssistantMemoryUsageBridge
                .selectedIdsFromPayload(JSONObject())
                .isEmpty(),
        )
    }

    @Test
    fun usageIdsAreCappedToRpcLimit() {
        val ids = (1..40).map { "memory-$it" }
        val payload = JSONObject().put(
            "memoryContextDiagnostics",
            JSONObject().put("selectedMemoryIds", JSONArray(ids)),
        )

        assertEquals(
            ids.take(24),
            AssistantMemoryUsageBridge.selectedIdsFromPayload(payload),
        )
    }
}
