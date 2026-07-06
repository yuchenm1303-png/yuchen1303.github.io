package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.ui.InlineStickerDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkerClientTest {
    @Test
    fun chatPayloadIncludesCloudFirstClientToolContract() {
        val payload = AiWorkerClient().buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "user-test",
                    text = "你好",
                    role = MessageRole.User,
                ),
            ),
            modelPreference = ChatModel.Kimi,
            onlineEnabled = false,
        )

        val preferences = payload.getJSONObject("chatExpressionPreferences")
        val protocol = payload.getJSONObject("commandProtocol")
        val capabilities = payload.getJSONObject("clientCapabilities")
        val memoryRequest = payload.getJSONObject("memoryRequest")
        assertEquals(
            "ai_ledger_chat_expression_preferences_v1",
            preferences.getString("schema"),
        )
        assertTrue(preferences.getInt("inlineStickerFrequency") in 0..100)
        assertTrue(preferences.getInt("inlineStickerIntensity") in 0..100)
        assertTrue(preferences.getInt("inlineStickerMaxPerReply") in 0..64)
        assertTrue(preferences.getInt("inlineStickerRepeatCount") in 1..4)
        assertEquals(
            "compose-native-cloud-first-v2",
            payload.getString("clientVersion"),
        )
        assertEquals("cloud_final_model_v1", payload.getString("autoRouteAuthority"))
        assertEquals("cloud_final_chat_model", protocol.getString("decisionOwner"))
        assertEquals("android_structured_tool_executor", protocol.getString("executionOwner"))
        assertEquals(AI_WORKER_CLIENT_TOOL_CALL_SCHEMA, protocol.getString("clientToolCallSchema"))
        assertEquals(AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL, protocol.getString("clientToolResultProtocol"))
        assertTrue(capabilities.getJSONArray("agentActions").length() > 0)
        assertTrue(capabilities.getJSONArray("deviceTools").length() > 0)
        assertTrue(payload.getString("requestId").isNotBlank())
        assertEquals("auto", payload.getString("memoryMode"))
        assertTrue(payload.getBoolean("memoryEnabled"))
        assertEquals("ai_ledger_cloud_memory_request_v3", memoryRequest.getString("schema"))
        assertEquals("cloud_orchestrated", memoryRequest.getString("intent"))
        assertEquals("backend_cloud_v4", memoryRequest.getString("selectionOwner"))
        assertFalse(payload.has("memorySnapshot"))
        assertFalse(payload.has("personaConfig"))
        assertFalse(payload.has("systemPrompt"))
    }

    @Test
    fun ordinaryQuestionDeclaresCapabilitiesWithoutLocalIntentRouting() {
        val payload = payloadFor("解释一下三相异步电动机的工作原理")
        val protocol = payload.getJSONObject("commandProtocol")
        val capabilities = payload.getJSONObject("clientCapabilities")

        assertFalse(payload.has("normalChatDeviceToolProbe"))
        assertFalse(payload.has("agentModeEnabled"))
        assertEquals("cloud_final_chat_model", protocol.getString("decisionOwner"))
        assertTrue(capabilities.getJSONArray("deviceTools").length() > 0)
        assertTrue(capabilities.getJSONArray("agentActions").length() > 0)
        assertTrue(payload.getJSONObject("responseFormat").getBoolean("includeClientToolCall"))
    }

    @Test
    fun appLaunchAndOrdinaryQuestionUseTheSameCapabilityDeclaration() {
        val ordinary = payloadFor("解释一下电动机")
        val appLaunch = payloadFor("请帮我打开微信")

        assertFalse(appLaunch.has("normalChatDeviceToolProbe"))
        assertEquals(
            ordinary.getJSONObject("clientCapabilities").getJSONArray("agentActions").toString(),
            appLaunch.getJSONObject("clientCapabilities").getJSONArray("agentActions").toString(),
        )
        assertEquals(
            ordinary.getJSONObject("clientCapabilities").getJSONArray("deviceTools").toString(),
            appLaunch.getJSONObject("clientCapabilities").getJSONArray("deviceTools").toString(),
        )
    }

    @Test
    fun autoModelDoesNotUseLocalNaturalLanguageRouting() {
        val explanation = payloadFor("请解释这个概念", ChatModel.Auto)
        val coding = payloadFor("请分析这段复杂代码", ChatModel.Auto)

        assertEquals("auto", explanation.getString("modelPreference"))
        assertEquals("auto", coding.getString("modelPreference"))
        assertEquals(
            explanation.getJSONObject("clientCapabilities").getJSONArray("deviceTools").toString(),
            coding.getJSONObject("clientCapabilities").getJSONArray("deviceTools").toString(),
        )
    }

    @Test
    fun stickerExpressionDefaultsRemainStable() {
        assertEquals(34, InlineStickerDisplaySettings.DefaultFrequency)
        assertEquals(79, InlineStickerDisplaySettings.DefaultIntensity)
        assertEquals(0, InlineStickerDisplaySettings.DefaultMaxPerReply)
        assertEquals(1, InlineStickerDisplaySettings.DefaultRepeatCount)
    }

    @Test
    fun appAndUserCredentialsUseSeparateHeaders() {
        val client = AiWorkerClient(
            AiWorkerConfig(
                clientId = "test-device",
                clientAuthToken = "app-token",
                userAccessTokenProvider = { "header.payload.signature" },
            ),
        )

        val headers = client.buildRequestHeadersForTest()

        assertEquals("app-token", headers["X-AI-Ledger-Token"])
        assertEquals("Bearer header.payload.signature", headers["Authorization"])
        assertFalse(headers["Authorization"] == "Bearer app-token")
    }

    @Test
    fun loggedOutRequestKeepsAppCredentialWithoutAuthorization() {
        val client = AiWorkerClient(
            AiWorkerConfig(
                clientId = "test-device",
                clientAuthToken = "app-token",
                userAccessTokenProvider = { null },
            ),
        )

        val headers = client.buildRequestHeadersForTest()

        assertEquals("app-token", headers["X-AI-Ledger-Token"])
        assertNull(headers["Authorization"])
    }

    @Test
    fun duplicateAppTokenIsNeverReusedAsUserBearer() {
        val client = AiWorkerClient(
            AiWorkerConfig(
                clientId = "test-device",
                clientAuthToken = "same-token",
                userAccessTokenProvider = { "same-token" },
            ),
        )

        val headers = client.buildRequestHeadersForTest()

        assertEquals("same-token", headers["X-AI-Ledger-Token"])
        assertNull(headers["Authorization"])
    }

    @Test
    fun streamingRequestKeepsIdentityAndSseHeader() {
        val client = AiWorkerClient(
            AiWorkerConfig(
                clientId = "test-device",
                clientAuthToken = "app-token",
                userAccessTokenProvider = { "header.payload.signature" },
            ),
        )

        val headers = client.buildRequestHeadersForTest(stream = true)

        assertEquals("sse", headers["X-AI-Ledger-Stream"])
        assertEquals("app-token", headers["X-AI-Ledger-Token"])
        assertEquals("Bearer header.payload.signature", headers["Authorization"])
    }

    @Test
    fun appOnlyModeNeverDependsOnLoginOrSendsUserBearer() {
        val headers = AiWorkerRequestIdentity.headers(
            appClientToken = "app-token",
            userAccessTokenProvider = { "header.payload.signature" },
            mode = AiWorkerIdentityMode.AppOnly,
        )

        assertEquals("app-token", headers["X-AI-Ledger-Token"])
        assertNull(headers["Authorization"])
    }

    private fun payloadFor(text: String, model: ChatModel = ChatModel.Kimi) = AiWorkerClient(
        AiWorkerConfig(
            clientId = "test-device",
            clientAuthToken = "app-token",
            userAccessTokenProvider = { null },
        ),
    ).buildChatPayloadForTest(
        messages = listOf(
            ChatMessage(
                id = "user-test",
                text = text,
                role = MessageRole.User,
            ),
        ),
        modelPreference = model,
        onlineEnabled = false,
    )
}
