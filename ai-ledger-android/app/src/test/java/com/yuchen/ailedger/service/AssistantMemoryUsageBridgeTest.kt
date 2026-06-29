package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMemoryUsageBridgeTest {
    @Test
    fun androidDoesNotParseOrWriteCloudMemoryUsage() {
        val payload = JSONObject()
            .put("memoryEnabled", true)
            .put("memoryContextDiagnostics", JSONObject().put("selectionOwner", "backend_cloud_v4"))
        val before = payload.toString()

        AssistantMemoryUsageBridge.recordSuccessfulPayload(payload)

        assertEquals(before, payload.toString())
    }
}
