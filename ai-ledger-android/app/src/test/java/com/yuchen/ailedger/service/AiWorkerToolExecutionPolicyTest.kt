package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkerToolExecutionPolicyTest {
    private val client = AiWorkerClient()

    @After
    fun resetAgentMode() {
        AgentRuntimeController.setEnabled(false)
    }

    @Test
    fun agentModeFreezesRequiredComputerToolIntoCurrentRequest() {
        AgentRuntimeController.setEnabled(true)
        val message = ChatMessage(
            id = "user-complex-agent-task",
            text = "先打开QQ设置页，再打开同花顺找到热股榜里的华天科技",
            role = MessageRole.User,
        )

        val payload = client.buildChatPayloadForTest(
            messages = listOf(message),
            modelPreference = ChatModel.DeepSeekV4,
        )
        AgentRuntimeController.setEnabled(false)

        val policy = payload.getJSONObject("toolExecutionPolicy")
        assertEquals("ai_ledger_tool_execution_policy_v1", policy.getString("schema"))
        assertEquals("required_specific", policy.getString("mode"))
        assertEquals("computer_run_task", policy.getString("requiredTool"))
        assertEquals("agent_mode", policy.getString("source"))
        assertEquals(message.id, policy.getString("messageId"))
        assertTrue(payload.getBoolean("agentModeEnabled"))
        assertTrue(payload.getBoolean("forceVisualAgent"))
        assertEquals("visual_only", payload.getString("agentToolDomain"))

        val frozenPolicy = payload.getJSONObject("toolExecutionPolicy")
        assertEquals("required_specific", frozenPolicy.getString("mode"))
        assertEquals("computer_run_task", frozenPolicy.getString("requiredTool"))
    }

    @Test
    fun normalChatKeepsAutoToolSelection() {
        AgentRuntimeController.setEnabled(false)
        val message = ChatMessage(
            id = "user-normal-chat",
            text = "解释一下什么是视觉智能体",
            role = MessageRole.User,
        )

        val payload = client.buildChatPayloadForTest(
            messages = listOf(message),
            modelPreference = ChatModel.DeepSeekV4,
        )
        val policy = payload.getJSONObject("toolExecutionPolicy")

        assertEquals("auto", policy.getString("mode"))
        assertEquals("normal_chat", policy.getString("source"))
        assertFalse(policy.has("requiredTool"))
        assertFalse(payload.has("agentModeEnabled"))
        assertFalse(payload.has("forceVisualAgent"))
    }

    @Test
    fun imageUnderstandingForbidsExecutionToolsEvenWhenAgentToggleIsOn() {
        AgentRuntimeController.setEnabled(true)
        val message = ChatMessage(
            id = "user-image-chat",
            text = "分析这张图片",
            role = MessageRole.User,
            attachments = listOf(
                ChatAttachment(
                    id = "image-1",
                    mimeType = "image/jpeg",
                    base64Data = "AA==",
                )
            ),
        )

        val payload = client.buildChatPayloadForTest(
            messages = listOf(message),
            modelPreference = ChatModel.DeepSeekV4,
        )
        val policy = payload.getJSONObject("toolExecutionPolicy")

        assertEquals("none", policy.getString("mode"))
        assertEquals("vision_attachment", policy.getString("source"))
        assertFalse(policy.has("requiredTool"))
        assertFalse(payload.has("agentModeEnabled"))
        assertFalse(payload.has("forceVisualAgent"))
        assertTrue(payload.getBoolean("hasImage"))
    }
}
