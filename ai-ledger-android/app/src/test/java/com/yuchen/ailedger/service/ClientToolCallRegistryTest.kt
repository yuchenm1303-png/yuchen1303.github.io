package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClientToolCallRegistryTest {
    @Before
    fun clearBeforeTest() {
        ClientToolCallRegistry.clearVisual()
    }

    @After
    fun clearAfterTest() {
        ClientToolCallRegistry.clearVisual()
    }

    @Test
    fun consumesOnlyCallMatchingCurrentGoal() {
        visualCall("call-a", "打开 QQ 设置")
        visualCall("call-b", "打开微信设置")

        assertEquals("call-b", ClientToolCallRegistry.consumeVisual("打开微信设置")?.id)
        assertEquals("call-a", ClientToolCallRegistry.consumeVisual("打开 QQ 设置")?.id)
    }

    @Test
    fun unmatchedGoalDoesNotConsumeAnotherWorkspaceCall() {
        visualCall("call-a", "打开 QQ 设置")

        assertNull(ClientToolCallRegistry.consumeVisual("打开微信设置"))
        assertEquals("call-a", ClientToolCallRegistry.consumeVisual("打开 QQ 设置")?.id)
    }

    @Test
    fun ambiguousUnscopedConsumeFailsClosed() {
        visualCall("call-a", "打开 QQ 设置")
        visualCall("call-b", "打开微信设置")

        assertNull(ClientToolCallRegistry.consumeVisual())
        assertEquals("call-a", ClientToolCallRegistry.consumeVisual("打开 QQ 设置")?.id)
        assertEquals("call-b", ClientToolCallRegistry.consumeVisual("打开微信设置")?.id)
    }

    @Test
    fun latestCallForSameGoalReplacesStaleCall() {
        visualCall("call-old", "打开 QQ 设置")
        visualCall("call-new", "打开 QQ 设置")

        assertEquals("call-new", ClientToolCallRegistry.consumeVisual("打开 QQ 设置")?.id)
        assertNull(ClientToolCallRegistry.consumeVisual("打开 QQ 设置"))
    }

    private fun visualCall(id: String, goal: String): CloudClientToolCall {
        return CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = id,
            name = "computer_run_task",
            arguments = JSONObject().put("goal", goal),
            originalUserGoal = goal,
            finalModel = "deepseek_v4",
        )
    }
}
