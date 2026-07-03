package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {
    @Test
    fun cloudVisualCallIsConsumedExactlyOnce() {
        ClientToolCallRegistry.clearVisual()
        val call = CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = "call_visual_test",
            name = "computer_run_task",
            arguments = JSONObject().put("goal", "打开示例应用"),
        )

        assertEquals(call.id, ClientToolCallRegistry.consumeVisual()?.id)
        assertNull(ClientToolCallRegistry.consumeVisual())
    }

    @Test
    fun onlyManualVisualForceDependsOnTheHomeAgentSwitch() {
        assertTrue(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.VisualForce))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.ExplicitAgent))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.NormalChatDeviceTool))
    }
}
