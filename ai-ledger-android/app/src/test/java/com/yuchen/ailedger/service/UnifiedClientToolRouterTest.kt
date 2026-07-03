package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class UnifiedClientToolRouterTest {
    @Test
    fun ledgerToolCallReachesLedgerStepAndKeepsReceiptCorrelation() {
        val toolCall = JSONObject().apply {
            put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
            put("id", "call-ledger-1")
            put("name", "ledger_add_record")
            put("resultProtocol", AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
            put("riskLevel", "low")
            put("requiresConfirmation", false)
            put("finalModel", "qwen")
            put("originalUserGoal", "帮我记一杯 16 元奶茶")
            put("arguments", JSONObject().apply {
                put("amount", 16)
                put("recordType", "expense")
                put("title", "奶茶")
                put("category", "饮品")
                put("date", "today")
            })
        }

        val step = DeviceControlRouter.fromClientToolCall(toolCall)
        assertNotNull(step)
        assertEquals("ledger_add_record", step?.type)
        assertEquals(16.0, step?.toolArgs?.getDouble("amount") ?: 0.0, 0.0)
        assertEquals("饮品", step?.toolArgs?.getString("category"))

        val correlated = ClientToolCallRegistry.consume(step)
        assertEquals("call-ledger-1", correlated?.id)
        assertEquals("ledger_add_record", correlated?.name)
        assertEquals("qwen", correlated?.finalModel)
    }

    @Test
    fun undeclaredLedgerArgumentIsRejectedBeforeExecution() {
        val toolCall = JSONObject().apply {
            put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
            put("id", "call-ledger-invalid")
            put("name", "ledger_add_record")
            put("arguments", JSONObject().apply {
                put("amount", 16)
                put("recordType", "expense")
                put("title", "奶茶")
                put("category", "饮品")
                put("date", "today")
                put("memorySlot", "profile.favorite_drink")
            })
        }

        assertNull(DeviceControlRouter.fromClientToolCall(toolCall))
    }

    @Test
    fun memoryToolCannotEnterAndroidLedgerOrDeviceExecutor() {
        val toolCall = JSONObject().apply {
            put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
            put("id", "call-memory-1")
            put("name", "memory_upsert")
            put("arguments", JSONObject().put("content", "用户喜欢奶茶"))
        }

        assertNull(DeviceControlRouter.fromClientToolCall(toolCall))
    }
}
