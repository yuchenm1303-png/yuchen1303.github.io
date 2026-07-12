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

        val payload = testClient().buildChatPayloadForTest(
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
        assertEquals("compose-native-project-workspace-v2", payload.optString("clientVersion"))
        assertEquals("project_12345678", payload.getJSONObject("activeProject").optString("projectId"))
        assertEquals("rev_000007", payload.getJSONObject("activeProject").optString("currentRevisionId"))

        val capabilities = payload.getJSONObject("clientCapabilities")
        assertEquals("ai_ledger_android_client_capabilities_v4", capabilities.optString("schema"))
        val workspace = capabilities.getJSONObject("projectWorkspace")
        assertTrue(workspace.optBoolean("enabled"))
        assertEquals("static_web", workspace.optString("projectType"))
        assertEquals("isolated_local_webview", workspace.optString("previewMode"))
        assertEquals("blocked", workspace.optString("networkPolicy"))
        assertEquals(AGENT_ARTIFACT_VERIFICATION_SCHEMA, workspace.optString("verificationSchema"))
        assertTrue(workspace.optBoolean("deterministicValidation"))
        assertTrue(workspace.optBoolean("previewRequiresValidation"))
        assertEquals("project_12345678", workspace.getJSONObject("activeProject").optString("projectId"))

        val tools = capabilities.getJSONArray("projectTools").asStrings()
        assertTrue("project_create" in tools)
        assertTrue("project_read_file" in tools)
        assertTrue("project_apply_edits" in tools)
        assertTrue("project_validate" in tools)
        assertTrue("project_build_preview" in tools)
        assertTrue("project_rollback" in tools)
    }

    @Test
    fun preservesWorkspaceIdentityWhenReportingClientToolResult() {
        val receipt = JSONObject()
            .put("protocol", AI_WORKER_CLIENT_TOOL_RESULT_PROTOCOL)
            .put("toolCallId", "call-project-create")
            .put("toolName", "project_create")
            .put("toolArguments", JSONObject().put("name", "产品官网"))
            .put("workspaceId", "workspace-project-123")
            .put("workspaceSchema", "ai_ledger_agent_workspace_v1")
            .put("status", "created")
            .put("completed", true)
            .put("handled", true)

        val payload = testClient().buildChatPayloadForTest(
            messages = listOf(
                ChatMessage(
                    id = "client-result-1",
                    text = "[[AI_LEDGER_CLIENT_TOOL_RESULT_V1]]$receipt",
                    role = MessageRole.User,
                )
            ),
            modelPreference = ChatModel.DeepSeekV4,
            onlineEnabled = false,
        )

        assertEquals("internal_control_report", payload.optString("action"))
        assertEquals("workspace-project-123", payload.optString("workspaceId"))
        assertEquals("workspace-project-123", payload.optString("agentWorkspaceId"))
        assertEquals("ai_ledger_agent_workspace_v1", payload.optString("workspaceSchema"))
        assertEquals("ai_ledger_agent_workspace_v1", payload.optString("agentWorkspaceSchema"))
        assertEquals("project_create", payload.getJSONObject("internalControlReceipt").optString("toolName"))
        assertTrue("project_validate" in payload.getJSONArray("projectTools").asStrings())
    }

    private fun testClient(): AiWorkerClient = AiWorkerClient(
        config = AiWorkerConfig(
            endpoint = "https://example.com",
            fallbackEndpoints = emptyList(),
            clientId = "payload-test",
            clientAuthToken = "",
        )
    )

    private fun JSONArray.asStrings(): List<String> = buildList {
        for (index in 0 until length()) add(optString(index))
    }
}
