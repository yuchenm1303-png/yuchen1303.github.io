package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.ui.InlineStickerDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkerClientTest {
    @Test
    fun chatPayloadIncludesStickerExpressionPreferenceContract() {
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
        assertTrue(preferences.getInt("inlineStickerFrequency") in 0..100)
        assertTrue(preferences.getInt("inlineStickerIntensity") in 0..100)
        assertTrue(preferences.getInt("inlineStickerMaxPerReply") in 0..64)
        assertTrue(preferences.getInt("inlineStickerRepeatCount") in 1..4)
        assertEquals(
            "compose-native-command-chat-v7-memory-retrieval",
            payload.getString("clientVersion"),
        )
    }

    @Test
    fun stickerExpressionDefaultsRemainStable() {
        assertEquals(50, InlineStickerDisplaySettings.DefaultFrequency)
        assertEquals(50, InlineStickerDisplaySettings.DefaultIntensity)
        assertEquals(0, InlineStickerDisplaySettings.DefaultMaxPerReply)
        assertEquals(1, InlineStickerDisplaySettings.DefaultRepeatCount)
    }
}
