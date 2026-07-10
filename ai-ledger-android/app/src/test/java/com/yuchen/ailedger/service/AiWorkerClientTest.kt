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
        AgentRuntimeController.setEnabled(false)
        AgentWorkspaceModeController.setEnabled(false)
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
        val toolExecutionPolicy = payload.getJSONObject("toolExecutionPolicy")
        assertEquals(
            "ai_ledger_chat_expression_preferences_v1",
            preferences.getString("schema"),
        )
        assertTrue(preferences.getInt("inlineStickerFrequency") in 0..100)
        assertTrue(preferences.getInt("inlineStickerIntensity") in 0..100)
        assertTrue(preferences.getInt("inlineStickerMaxPerReply") in 0..64)
        assertTrue(preferences.getInt("inlineStickerRepeatCount") in 1..4)
        assertEquals(
            "compose-native-cloud-first-v4-required-tool-policy",
            payload.getString("clientVersion"),
        )
        assertEquals("ai_ledger_tool_execution_policy_v1", toolExecutionPolicy.getString("schema"))
        assertEquals("auto", toolExecutionPolicy.getString("mode"))
        assertEquals("normal_chat", toolExecutionPolicy.getString("source"))
        assertEquals("cloud_final_model_v1", payload.getString("autoRouteAuthority"))
        assertEquals("cloud_final_chat_model", protocol.getString("decisionOwner"))
        assertEquals("classic", protocol.getString("workspaceMode"))
        assertFalse(protocol.getBoolean("workspaceModeEnabled"))
        assertEquals("android_structured_tool_executor", protocol.getString("executionOwner"))
        assertEquals(AI_WORKER_CLIENT_TOOL_CALL_SCHEMA, protocol.getString("clientToolCallSchema"))
        assertEquals(AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL, protocol.getString("clientToolResultProtocol"))
        assertEquals("ai_ledger_tool_execution_policy_v1", protocol.getString("toolExecutionPolicySchema"))
        assertEquals("gui_plus_exclusive", payload.getString("visualDecisionOwner"))
        assertFalse(payload.getBoolean("visualAgentBrainEnabled"))
        assertEquals("gui_plus_exclusive", protocol.getString("visualRouteMode"))
        assertEquals("gui_plus", capabilities.getString("computerUseOwner"))
        assertTrue(capabilities.getJSONArray("agentActions").length() > 0)
        assertTrue(capabilities.getJSONArray("deviceTools").length() > 0)
        assertTrue(capabilities.getBoolean("workspaceModeToggle"))
        assertFalse(capabilities.getBoolean("workspaceModeEnabled"))
        assertFalse(payload.getBoolean("workspaceModeEnabled"))
        assertEquals("classic", payload.getString("agentWorkspaceMode"))
        assertFalse(payload.getBoolean("agentProgressStream"))
        assertFalse(payload.getBoolean("workspaceProgressStream"))
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
    fun workspaceModeSwitchChangesChatPayloadContract() {
        try {
            val payload = payloadFor("帮我查天气并根据结果继续安排提醒", workspaceModeEnabled = true)
            val protocol = payload.getJSONObject("commandProtocol")
            val capabilities = payload.getJSONObject("clientCapabilities")
            val responseFormat = payload.getJSONObject("responseFormat")

            assertTrue(payload.getBoolean("workspaceModeEnabled"))
            assertEquals("workspace", payload.getString("agentWorkspaceMode"))
            assertEquals("cloud_controlled_multi_step", payload.getString("workspaceToolLoop"))
            assertEquals("cloud_workspace_agent", payload.getString("workspaceDecisionOwner"))
            assertTrue(payload.getBoolean("agentProgressStream"))
            assertTrue(payload.getBoolean("workspaceProgressStream"))
            assertEquals("cloud_workspace_agent", protocol.getString("decisionOwner"))
            assertEquals("workspace", protocol.getString("workspaceMode"))
            assertTrue(protocol.getBoolean("workspaceModeEnabled"))
            assertEquals("gui_plus_exclusive", payload.getString("visualDecisionOwner"))
            assertFalse(payload.getBoolean("visualAgentBrainEnabled"))
            assertEquals("gui_plus_exclusive", protocol.getString("visualRouteMode"))
            assertTrue(capabilities.getBoolean("workspaceModeToggle"))
            assertTrue(capabilities.getBoolean("workspaceModeEnabled"))
            assertTrue(responseFormat.getBoolean("includeAgentProgress"))
            assertTrue(responseFormat.getBoolean("deferClientToolReply"))
        } finally {
            AgentWorkspaceModeController.setEnabled(false)
        }
    }

    @Test
    fun ordinaryQuestionDeclaresCapabilitiesWithoutLocalIntentRouting() {
        val payload = payloadFor("解释一下三相异步电动机的工作原理")
        val protocol = payload.getJSONObject("commandProtocol")
        val capabilities = payload.getJSONObject("clientCapabilities")

        assertFalse(payload.has("normalChatDeviceToolProbe"))
        assertFalse(payload.has("agentModeEnabled"))
        assertEquals("cloud_final_chat_model", protocol.getString("decisionOwner"))
        assertEquals("classic", protocol.getString("workspaceMode"))
        assertFalse(protocol.getBoolean("workspaceModeEnabled"))
        assertEquals("gui_plus_exclusive", protocol.getString("visualDecisionOwner"))
        assertFalse(protocol.getBoolean("visualAgentBrainEnabled"))
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

    private fun payloadFor(
        text: String,
        model: ChatModel = ChatModel.Kimi,
        workspaceModeEnabled: Boolean = false,
    ) = AiWorkerClient(
        AiWorkerConfig(
            clientId = "test-device",
            clientAuthToken = "app-token",
            userAccessTokenProvider = { null },
        ),
    ).also {
        AgentRuntimeController.setEnabled(false)
        AgentWorkspaceModeController.setEnabled(workspaceModeEnabled)
    }.buildChatPayloadForTest(
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
