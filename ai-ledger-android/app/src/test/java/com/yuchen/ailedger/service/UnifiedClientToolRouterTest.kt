package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UnifiedClientToolRouterTest {
    @Test
    fun ledgerToolCallReachesLedgerStepAndKeepsReceiptCorrelation() {
        val toolCall = ledgerAddToolCall("call-ledger-1")

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
    fun workerResponseParserProjectsLedgerToolIntoExecutableClientStep() {
        val response = JSONObject().apply {
            put("ok", true)
            put("source", "final_chat_model_native_tool")
            put("model", "qwen")
            put("clientToolCall", ledgerAddToolCall("call-parser-ledger"))
        }
        val parsed = AiWorkerResponseParser.parse(
            data = response,
            body = response.toString(),
            payload = JSONObject().put("intent", "chat"),
            route = AiWorkerModelRoute(
                requested = ChatModel.Auto,
                resolved = ChatModel.Kimi,
                reason = "test",
            ),
        )

        val step = parsed.agentAction?.deviceControlStep
        assertEquals("run_device_control", parsed.agentAction?.capability)
        assertEquals("ledger_add_record", step?.type)
        assertEquals("call-parser-ledger", parsed.clientToolCall?.id)
        assertEquals("call-parser-ledger", ClientToolCallRegistry.consume(step)?.id)
    }

    @Test
    fun undeclaredLedgerArgumentIsRejectedBeforeExecution() {
        val toolCall = ledgerAddToolCall("call-ledger-invalid").apply {
            getJSONObject("arguments").put("memorySlot", "profile.favorite_drink")
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

    private fun ledgerAddToolCall(id: String): JSONObject = JSONObject().apply {
        put("schema", AI_WORKER_CLIENT_TOOL_CALL_SCHEMA)
        put("id", id)
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
}
