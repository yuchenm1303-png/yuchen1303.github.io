package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiWorkerClientProtocolTest {
    private val client = AiWorkerClient(
        AiWorkerConfig(
            endpoint = "https://example.com",
            fallbackEndpoints = emptyList(),
            clientId = "android-install-test",
        ),
    )

    @Before
    fun resetAgentModes() {
        AgentRuntimeController.setEnabled(false)
        AgentWorkspaceModeController.setEnabled(false)
    }

    @Test
    fun imagePayloadContainsOneBase64CopyOnly() {
        val base64 = "dGVzdC1pbWFnZS1wYXlsb2Fk"
        val payload = client.buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    text = "分析图片",
                    role = MessageRole.User,
                    attachments = listOf(
                        ChatAttachment(
                            id = "img1",
                            mimeType = "image/jpeg",
                            base64Data = base64,
                            fileName = "test.jpg",
                            width = 640,
                            height = 480,
                            sizeBytes = 128,
                        ),
                    ),
                ),
            ),
            modelPreference = ChatModel.Auto,
        )

        assertTrue(payload.getBoolean("hasImage"))
        assertTrue(payload.has("images"))
        assertFalse(payload.has("attachments"))

        val image = payload.getJSONArray("images").getJSONObject(0)
        assertTrue(image.has("base64Data"))
        assertFalse(image.has("data"))
        assertFalse(image.has("imageBase64"))
        assertFalse(image.has("mediaType"))

        val messages = payload.getJSONArray("messages")
        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            assertFalse(message.has("images"))
            assertFalse(message.has("attachments"))
        }
        assertEquals(1, Regex(Regex.escape(base64)).findAll(payload.toString()).count())
    }

    @Test
    fun ordinaryChatDeclaresCloudOwnedToolsWithoutLocalSemanticRouting() {
        val payload = client.buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    text = "打开 Wi-Fi",
                    role = MessageRole.User,
                ),
            ),
        )

        assertEquals("chat", payload.getString("intent"))
        assertFalse(payload.has("allowModelCommands"))
        assertFalse(payload.has("systemPrompt"))
        assertFalse(payload.has("normalChatDeviceToolProbe"))
        assertFalse(payload.has("agentModeEnabled"))

        val protocol = payload.getJSONObject("commandProtocol")
        assertEquals("cloud_final_chat_model", protocol.getString("decisionOwner"))
        assertEquals("android_structured_tool_executor", protocol.getString("executionOwner"))
        assertEquals(AI_WORKER_CLIENT_TOOL_CALL_SCHEMA, protocol.getString("clientToolCallSchema"))
        assertEquals(AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL, protocol.getString("clientToolResultProtocol"))

        val capabilities = payload.getJSONObject("clientCapabilities")
        val supportedAgentActions = capabilities.getJSONArray("agentActions").toString()
        assertTrue(supportedAgentActions.contains("run_device_control"))
        assertTrue(supportedAgentActions.contains("run_agent_task"))
        assertTrue(supportedAgentActions.contains("observe_screen"))

        val responseFormat = payload.getJSONObject("responseFormat")
        assertTrue(responseFormat.getBoolean("includeClientToolCall"))
        assertFalse(responseFormat.optBoolean("includeEmbeddedCommandMarker", false))

        val supportedSteps = capabilities.getJSONArray("deviceTools")
        assertTrue(supportedSteps.length() > 0)
        assertTrue(supportedSteps.toString().contains("ledger_add_record"))
    }

    @Test
    fun explicitAgentPrefixIsNotClassifiedLocally() {
        val payload = client.buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    text = "/agent 打开微信",
                    role = MessageRole.User,
                ),
            ),
        )

        assertEquals("chat", payload.getString("intent"))
        assertFalse(payload.has("agentStartRequested"))
        val actions = payload.getJSONObject("clientCapabilities")
            .getJSONArray("agentActions")
            .toString()
        assertTrue(actions.contains("run_agent_task"))
        assertTrue(actions.contains("run_device_control"))
        assertTrue(payload.getJSONObject("responseFormat").getBoolean("includeClientToolCall"))
    }

    @Test
    fun normalChatDoesNotAdvertiseDestructiveDeviceTools() {
        val payload = client.buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    text = "你好",
                    role = MessageRole.User,
                ),
            ),
        )

        val steps = payload.getJSONObject("clientCapabilities")
            .getJSONArray("deviceTools")
            .toString()
        assertFalse(steps.contains("uninstall_app"))
        assertFalse(steps.contains("clear_app_data"))
        assertFalse(steps.contains("force_stop_app"))
        assertFalse(steps.contains("set_animation_scale"))
        assertFalse(steps.contains("request_shizuku_permission"))
    }

    @Test
    fun oldImageIsNotResentForLaterTextTurn() {
        val payload = client.buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    text = "第一张图片",
                    role = MessageRole.User,
                    attachments = listOf(
                        ChatAttachment(
                            id = "img1",
                            mimeType = "image/jpeg",
                            base64Data = "b2xkLWltYWdl",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "a1",
                    text = "已分析",
                    role = MessageRole.Assistant,
                    source = "cloud_ai",
                ),
                ChatMessage(
                    id = "u2",
                    text = "继续解释上一条结论",
                    role = MessageRole.User,
                ),
            ),
        )

        assertFalse(payload.getBoolean("hasImage"))
        assertEquals(0, payload.getInt("imageCount"))
        assertFalse(payload.has("images"))
    }
}
