package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AiWorkerClientVisualToolCallTest {
    private lateinit var client: AiWorkerClient

    @Before
    fun setUp() {
        client = testClient("visual-call-primary")
        client.clearVisualClientToolCalls()
    }

    @After
    fun tearDown() {
        client.clearVisualClientToolCalls()
    }

    @Test
    fun consumesOnlyCallMatchingCurrentGoal() {
        client.rememberVisualClientToolCall(visualResponse("call-a", "打开 QQ 设置"))
        client.rememberVisualClientToolCall(visualResponse("call-b", "打开微信设置"))

        assertEquals("call-b", client.consumeVisualClientToolCall("打开微信设置")?.id)
        assertEquals("call-a", client.consumeVisualClientToolCall("打开 QQ 设置")?.id)
    }

    @Test
    fun unmatchedGoalDoesNotConsumeAnotherWorkspaceCall() {
        client.rememberVisualClientToolCall(visualResponse("call-a", "打开 QQ 设置"))

        assertNull(client.consumeVisualClientToolCall("打开微信设置"))
        assertEquals("call-a", client.consumeVisualClientToolCall("打开 QQ 设置")?.id)
    }

    @Test
    fun ambiguousUnscopedConsumeFailsClosed() {
        client.rememberVisualClientToolCall(visualResponse("call-a", "打开 QQ 设置"))
        client.rememberVisualClientToolCall(visualResponse("call-b", "打开微信设置"))

        assertNull(client.consumeVisualClientToolCall())
        assertEquals("call-a", client.consumeVisualClientToolCall("打开 QQ 设置")?.id)
        assertEquals("call-b", client.consumeVisualClientToolCall("打开微信设置")?.id)
    }

    @Test
    fun latestCallForSameGoalReplacesStaleCall() {
        client.rememberVisualClientToolCall(visualResponse("call-old", "打开 QQ 设置"))
        client.rememberVisualClientToolCall(visualResponse("call-new", "打开 QQ 设置"))

        assertEquals("call-new", client.consumeVisualClientToolCall("打开 QQ 设置")?.id)
        assertNull(client.consumeVisualClientToolCall("打开 QQ 设置"))
    }

    @Test
    fun visualCallsAreIsolatedBetweenClientInstances() {
        val anotherClient = testClient("visual-call-secondary")
        client.rememberVisualClientToolCall(visualResponse("call-a", "打开 QQ 设置"))

        assertNull(anotherClient.consumeVisualClientToolCall("打开 QQ 设置"))
        assertEquals("call-a", client.consumeVisualClientToolCall("打开 QQ 设置")?.id)
    }

    @Test
    fun constructingToolCallAloneHasNoHiddenRegistrationSideEffect() {
        visualCall("call-a", "打开 QQ 设置")

        assertNull(client.consumeVisualClientToolCall("打开 QQ 设置"))
    }

    @Test
    fun unrelatedAgentActionIsNotRegistered() {
        val call = visualCall("call-a", "打开 QQ 设置")
        client.rememberVisualClientToolCall(
            AiChatResponse(
                reply = "",
                agentAction = CloudAgentAction(
                    capability = "observe_screen",
                    goal = "打开 QQ 设置",
                    clientToolCall = call,
                ),
            )
        )

        assertNull(client.consumeVisualClientToolCall("打开 QQ 设置"))
    }

    private fun testClient(id: String): AiWorkerClient = AiWorkerClient(
        AiWorkerConfig(
            endpoint = "https://example.com",
            fallbackEndpoints = emptyList(),
            clientId = id,
        )
    )

    private fun visualResponse(id: String, goal: String): AiChatResponse {
        return AiChatResponse(
            reply = "",
            agentAction = CloudAgentAction(
                capability = "run_agent_task",
                goal = goal,
                clientToolCall = visualCall(id, goal),
            ),
        )
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
