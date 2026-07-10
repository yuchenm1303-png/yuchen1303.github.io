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
        val client = AiWorkerClient(
            AiWorkerConfig(
                endpoint = "https://example.com",
                fallbackEndpoints = emptyList(),
                clientId = "agent-orchestrator-test",
            )
        )
        val goal = "打开示例应用"
        val call = CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = "call_visual_test",
            name = "computer_run_task",
            arguments = JSONObject().put("goal", goal),
            originalUserGoal = goal,
        )
        client.rememberVisualClientToolCall(
            AiChatResponse(
                reply = "",
                agentAction = CloudAgentAction(
                    capability = "run_agent_task",
                    goal = goal,
                    clientToolCall = call,
                ),
            )
        )

        assertEquals(call.id, client.consumeVisualClientToolCall(goal)?.id)
        assertNull(client.consumeVisualClientToolCall(goal))
    }

    @Test
    fun onlyManualVisualForceDependsOnTheHomeAgentSwitch() {
        assertTrue(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.VisualForce))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.ExplicitAgent))
        assertFalse(VisualLoopRunner.requiresAgentSwitch(AgentExecutionMode.NormalChatDeviceTool))
    }
}
