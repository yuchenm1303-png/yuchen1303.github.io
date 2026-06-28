package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AiWorkerClientTest {
    @Test
    fun chatPayloadIncludesDefaultStickerExpressionPreferences() {
        val payload = AiWorkerClient().buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "user-test",
                    text = "你好",
                    role = MessageRole.User,
                )
            ),
            modelPreference = ChatModel.Kimi,
            onlineEnabled = false,
        )

        val preferences = payload.getJSONObject("chatExpressionPreferences")
        assertEquals("ai_ledger_chat_expression_preferences_v1", preferences.getString("schema"))
        assertEquals(50, preferences.getInt("inlineStickerFrequency"))
        assertEquals(50, preferences.getInt("inlineStickerIntensity"))
        assertEquals(0, preferences.getInt("inlineStickerMaxPerReply"))
        assertEquals(1, preferences.getInt("inlineStickerRepeatCount"))
        assertEquals(
            "compose-native-command-chat-v6-parallel-device-probe",
            payload.getString("clientVersion"),
        )
    }
}
