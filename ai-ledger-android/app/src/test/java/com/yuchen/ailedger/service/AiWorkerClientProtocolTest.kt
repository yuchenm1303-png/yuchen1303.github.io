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
    fun ordinaryChatExplicitlyAllowsLowRiskStructuredCommandsOnly() {
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
        assertTrue(payload.getBoolean("allowModelCommands"))
        val protocol = payload.getJSONObject("commandProtocol")
        assertTrue(protocol.getBoolean("allowModelCommands"))
        assertTrue(protocol.getBoolean("structuredCommandsOnly"))
        assertEquals("structured_low_risk_only", protocol.getString("deviceControlMode"))

        val capabilities = protocol.getJSONArray("supportedDeviceControlActions").toString()
        assertTrue(capabilities.contains("network.wifi.set"))
        assertFalse(capabilities.contains("app.uninstall"))
        assertFalse(capabilities.contains("app.clear_data"))
        assertFalse(capabilities.contains("app.force_stop"))
        assertFalse(capabilities.contains("system.animation_scale.set"))
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
