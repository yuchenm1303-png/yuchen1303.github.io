package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAgentPayloadTest {
    @Test
    fun visualPayloadUsesOneTaskMemorySourceWithoutCleanupPass() {
        val snapshot = AgentScreenSnapshot(
            currentApp = "com.example.app",
            packageName = "com.example.app",
            nodeCount = 0,
            capturedNodeCount = 0,
            texts = emptyList(),
            allNodes = emptyList(),
            clickableNodes = emptyList(),
            inputNodes = emptyList(),
            scrollableNodes = emptyList(),
        )
        val payload = buildVisualAgentPayload(
            goal = "test",
            snapshot = snapshot,
            recentActions = emptyList(),
            taskMemory = VisualTaskMemory(originalGoal = "test"),
        )

        assertTrue(payload.has("taskMemory"))
        assertTrue(payload.has("executionFeedback"))
        assertFalse(payload.has("agentMemory"))
        assertFalse(payload.has("lastToolResponse"))
        assertFalse(payload.has("routeRefreshRequested"))
        assertFalse(payload.getJSONObject("taskMemory").has("reasoningContext"))
    }

    @Test
    fun visualTaskUsesOriginalClientToolCallIdAsSessionBoundary() {
        val call = CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = "call_visual_123",
            name = "computer_run_task",
            arguments = JSONObject().put("goal", "打开示例应用"),
            finalModel = "deepseek-v4",
        )

        val invocation = VisualTaskInvocationRuntime.begin("打开示例应用")
        try {
            assertEquals(call.id, invocation.taskInvocationId)
            assertEquals(call.id, invocation.sessionId)
            assertEquals(call.id, AgentClientIdentity.newVisualSessionId())
            assertEquals(call.finalModel, invocation.clientToolCall?.finalModel)
        } finally {
            VisualTaskInvocationRuntime.clear(invocation)
        }
    }

    @Test
    fun staleVisualToolCallCannotBindToDifferentManualGoal() {
        val staleCall = CloudClientToolCall(
            schema = AI_WORKER_CLIENT_TOOL_CALL_SCHEMA,
            id = "call_stale_456",
            name = "computer_run_task",
            arguments = JSONObject().put("goal", "旧任务"),
        )

        val invocation = VisualTaskInvocationRuntime.begin("新的手动任务")
        try {
            assertNotEquals(staleCall.id, invocation.sessionId)
            assertNull(invocation.clientToolCall)
            assertTrue(invocation.sessionId.startsWith("visual-session-"))
        } finally {
            VisualTaskInvocationRuntime.clear(invocation)
        }
    }
}
