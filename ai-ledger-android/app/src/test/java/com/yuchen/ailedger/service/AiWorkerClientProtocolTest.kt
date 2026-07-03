package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkerClientProtocolTest {
    private val client = AiWorkerClient(
        AiWorkerConfig(
            endpoint = "https://example.com",
            fallbackEndpoints = emptyList(),
            clientId = "android-install-test",
        ),
    )

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
        assertEquals("top_level_images_v2", payload.getString("imageTransport"))

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
    fun ordinaryChatDeclaresUnifiedToolsWithoutEmbeddedModelCommands() {
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
        assertFalse(payload.getBoolean("allowModelCommands"))

        val protocol = payload.getJSONObject("commandProtocol")
        assertFalse(protocol.getBoolean("allowModelCommands"))
        assertTrue(protocol.getBoolean("structuredCommandsOnly"))
        assertEquals("cloud_final_chat_model", protocol.getString("decisionOwner"))
        assertEquals("android_local_transaction_executor", protocol.getString("executionOwner"))
        assertEquals("structured_response_only", protocol.getString("fallbackTransport"))
        assertEquals(AI_WORKER_CLIENT_TOOL_CALL_SCHEMA, protocol.getString("clientToolCallSchema"))
        assertEquals(AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL, protocol.getString("clientToolResultProtocol"))

        val supportedAgentActions = protocol.getJSONArray("supportedAgentActions").toString()
        assertTrue(supportedAgentActions.contains("run_device_control"))
        assertTrue(supportedAgentActions.contains("run_agent_task"))
        assertTrue(supportedAgentActions.contains("observe_screen"))

        val responseFormat = payload.getJSONObject("responseFormat")
        assertTrue(responseFormat.getBoolean("includeAgentAction"))
        assertTrue(responseFormat.getBoolean("includeClientToolCall"))
        assertFalse(responseFormat.getBoolean("includeEmbeddedCommandMarker"))

        val legacyCapabilities = protocol.getJSONArray("supportedDeviceControlActions")
        assertEquals(0, legacyCapabilities.length())

        val supportedSteps = protocol.getJSONArray("supportedDeviceToolSteps")
        assertTrue(supportedSteps.length() > 0)
        assertTrue(supportedSteps.toString().contains("ledger_add_record"))

        val probe = payload.getJSONObject("normalChatDeviceToolProbe")
        assertFalse(probe.getBoolean("enabled"))
        assertEquals("cloud_final_chat_model", probe.getString("decisionOwner"))
        assertEquals("android_local_transaction_executor", probe.getString("executionOwner"))
        assertEquals(supportedSteps.length(), probe.getJSONArray("supportedDeviceToolSteps").length())
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
        assertFalse(payload.getBoolean("agentStartRequested"))
        val actions = payload.getJSONObject("commandProtocol")
            .getJSONArray("supportedAgentActions")
            .toString()
        assertTrue(actions.contains("run_agent_task"))
        assertTrue(actions.contains("run_device_control"))
        assertTrue(payload.getJSONObject("responseFormat").getBoolean("includeAgentAction"))
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

        val steps = payload.getJSONObject("commandProtocol")
            .getJSONArray("supportedDeviceToolSteps")
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
