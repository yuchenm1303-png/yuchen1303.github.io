package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspacePayloadTest {
    @Test
    fun advertisesStaticWebProjectWorkspaceAndTools() {
        val payload = AiWorkerClient(
            config = AiWorkerConfig(
                endpoint = "https://example.com",
                fallbackEndpoints = emptyList(),
                clientId = "payload-test",
                clientAuthToken = "",
            )
        ).buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "user-1",
                    text = "帮我创建一个产品官网",
                    role = MessageRole.User,
                )
            ),
            modelPreference = ChatModel.Auto,
            onlineEnabled = false,
        )

        assertTrue(payload.optBoolean("projectWorkspaceEnabled"))
        assertEquals("ai_ledger_android_project_workspace_v1", payload.optString("projectWorkspaceSchema"))
        assertEquals("compose-native-project-workspace-v1", payload.optString("clientVersion"))

        val capabilities = payload.getJSONObject("clientCapabilities")
        assertEquals("ai_ledger_android_client_capabilities_v4", capabilities.optString("schema"))
        val workspace = capabilities.getJSONObject("projectWorkspace")
        assertTrue(workspace.optBoolean("enabled"))
        assertEquals("static_web", workspace.optString("projectType"))
        assertEquals("isolated_local_webview", workspace.optString("previewMode"))
        assertEquals("blocked", workspace.optString("networkPolicy"))

        val tools = capabilities.getJSONArray("projectTools").asStrings()
        assertTrue("project_create" in tools)
        assertTrue("project_read_file" in tools)
        assertTrue("project_apply_edits" in tools)
        assertTrue("project_build_preview" in tools)
        assertTrue("project_rollback" in tools)
    }

    private fun JSONArray.asStrings(): List<String> = buildList {
        for (index in 0 until length()) add(optString(index))
    }
}
