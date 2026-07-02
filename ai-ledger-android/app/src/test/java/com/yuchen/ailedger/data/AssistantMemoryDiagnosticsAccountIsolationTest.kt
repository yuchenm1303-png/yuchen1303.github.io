package com.yuchen.ailedger.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantMemoryDiagnosticsAccountIsolationTest {
    @Before
    fun resetBefore() {
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryDiagnostics.switchAccount(null)
    }

    @After
    fun resetAfter() {
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryDiagnostics.switchAccount(null)
    }

    @Test
    fun switchingAccountRemovesPreviousAccountsInMemoryReport() {
        val ticketA = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-a"))
        AssistantMemoryDiagnostics.switchAccount(ticketA)
        AssistantMemoryDiagnostics.record(
            ticket = ticketA,
            payload = JSONObject()
                .put("requestId", "request-a")
                .put("message", "用户 A 的私密测试内容")
                .put("memoryMode", "auto"),
            response = JSONObject()
                .put("memoryRequestId", "request-a")
                .put("memoryStatus", "ready")
                .put("memoryUsed", false),
        )
        assertEquals(1, AssistantMemoryDiagnostics.state.value.records.size)

        val ticketB = requireNotNull(AssistantAccountSessionRuntime.updateUser("user-b"))
        AssistantMemoryDiagnostics.switchAccount(ticketB)

        assertTrue(AssistantMemoryDiagnostics.state.value.records.isEmpty())
        assertTrue(!AssistantMemoryDiagnostics.fullReport().contains("用户 A 的私密测试内容"))
    }
}
