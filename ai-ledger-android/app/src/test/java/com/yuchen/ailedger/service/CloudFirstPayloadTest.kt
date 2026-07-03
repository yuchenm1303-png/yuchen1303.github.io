package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudFirstPayloadTest {
    @Test
    fun normalChatHasNoLocalSemanticHints() {
        val payload = AiWorkerClient(
            AiWorkerConfig(endpoint = "https://example.invalid", fallbackEndpoints = emptyList()),
        ).buildChatPayloadForTest(
            messages = listOf(ChatMessage(id = "u1", text = "hello", role = MessageRole.User)),
            modelPreference = ChatModel.Auto,
            onlineEnabled = false,
        )

        assertEquals("chat", payload.getString("intent"))
        assertEquals("hello", payload.getString("message"))
        assertFalse(payload.has("systemPrompt"))
        assertFalse(payload.has("agentModeEnabled"))
        assertFalse(payload.has("normalChatDeviceToolProbe"))
        assertFalse(payload.has("memorySnapshot"))
    }
}
