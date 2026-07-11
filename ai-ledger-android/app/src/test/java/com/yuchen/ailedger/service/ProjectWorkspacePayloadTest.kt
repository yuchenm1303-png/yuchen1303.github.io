package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspacePayloadTest {
    @After
    fun clearProjectContext() {
        ProjectWorkspaceSessionContext.clear()
    }

    @Test
    fun advertisesStaticWebProjectWorkspaceAndTools() {
        ProjectWorkspaceSessionContext.update(
            JSONObject()
                .put("projectId", "project_12345678")
                .put("name", "产品官网")
                .put("currentRevisionId", "rev_000007")
                .put("status", "preview_ready")
        )

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
                    text = "继续修改这个产品官网",
                    role = MessageRole.User,
                )
            ),
            modelPreference = ChatModel.Auto,
            onlineEnabled = false,
        )

        assertTrue(payload.optBoolean("projectWorkspaceEnabled"))
        assertEquals("ai_ledger_android_project_workspace_v1", payload.optString("projectWorkspaceSchema"))
        assertEquals("compose-native-project-workspace-v1", payload.optString("clientVersion"))
        assertEquals("project_12345678", payload.getJSONObject("activeProject").optString("projectId"))
        assertEquals("rev_000007", payload.getJSONObject("activeProject").optString("currentRevisionId"))

        val capabilities = payload.getJSONObject("clientCapabilities")
        assertEquals("ai_ledger_android_client_capabilities_v4", capabilities.optString("schema"))
        val workspace = capabilities.getJSONObject("projectWorkspace")
        assertTrue(workspace.optBoolean("enabled"))
        assertEquals("static_web", workspace.optString("projectType"))
        assertEquals("isolated_local_webview", workspace.optString("previewMode"))
        assertEquals("blocked", workspace.optString("networkPolicy"))
        assertEquals("project_12345678", workspace.getJSONObject("activeProject").optString("projectId"))

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
