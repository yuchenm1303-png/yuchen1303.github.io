package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryDiagnosticSnapshotTest {
    @Test
    fun payloadSnapshotDropsConversationAndImageData() {
        val source = JSONObject()
            .put("requestId", "request-test")
            .put("message", "继续项目")
            .put("memoryMode", "auto")
            .put("messages", JSONArray().put(JSONObject().put("content", "完整历史")))
            .put("images", JSONArray().put(JSONObject().put("base64Data", "large-image")))

        val snapshot = compactMemoryDiagnosticPayload(source)

        assertEquals("request-test", snapshot.optString("requestId"))
        assertEquals("继续项目", snapshot.optString("message"))
        assertEquals("auto", snapshot.optString("memoryMode"))
        assertFalse(snapshot.has("messages"))
        assertFalse(snapshot.has("images"))
    }

    @Test
    fun responseSnapshotKeepsMemoryTraceButDropsUnrelatedData() {
        val trace = JSONObject().put("prompt", "测试问题")
        val source = JSONObject()
            .put("memoryStatus", "used")
            .put("memoryTrace", trace)
            .put("sources", JSONArray().put("unrelated"))

        val snapshot = compactMemoryDiagnosticResponse(source)

        assertEquals("used", snapshot.optString("memoryStatus"))
        assertTrue(snapshot.optJSONObject("memoryTrace") === trace)
        assertFalse(snapshot.has("sources"))
    }
}
