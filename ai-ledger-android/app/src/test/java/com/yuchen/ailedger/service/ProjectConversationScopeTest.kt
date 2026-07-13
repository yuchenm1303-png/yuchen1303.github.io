package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectConversationScopeTest {
    private val scopeA = "chat_user-a"
    private val scopeB = "chat_user-b"

    @After
    fun tearDown() {
        ProjectWorkspaceSessionContext.clear(null, scopeA)
        ProjectWorkspaceSessionContext.clear(null, scopeB)
    }

    @Test
    fun activeProjectsRemainIsolatedByConversation() {
        ProjectWorkspaceSessionContext.update(
            context = null,
            conversationId = scopeA,
            project = JSONObject().put("projectId", "project_a"),
        )
        ProjectWorkspaceSessionContext.update(
            context = null,
            conversationId = scopeB,
            project = JSONObject().put("projectId", "project_b"),
        )

        assertEquals(
            "project_a",
            ProjectWorkspaceSessionContext.current(null, scopeA)?.getString("projectId"),
        )
        assertEquals(
            "project_b",
            ProjectWorkspaceSessionContext.current(null, scopeB)?.getString("projectId"),
        )
        assertNull(ProjectWorkspaceSessionContext.current(null, "chat_unknown"))
    }

    @Test
    fun payloadUsesStableFirstUserMessageAsConversationScope() {
        ProjectWorkspaceSessionContext.update(
            context = null,
            conversationId = scopeA,
            project = JSONObject()
                .put("projectId", "project_a")
                .put("currentRevisionId", "rev_a"),
        )
        val messages = listOf(
            ChatMessage(id = "user-a", text = "创建一个网页", role = MessageRole.User),
            ChatMessage(id = "assistant-a", text = "好的", role = MessageRole.Assistant),
            ChatMessage(id = "user-a-2", text = "继续美化", role = MessageRole.User),
        )

        val payload = AiWorkerPayloadBuilder.build(
            messages = messages,
            route = AiWorkerModelRoute(
                requested = ChatModel.Kimi,
                resolved = ChatModel.Kimi,
                reason = "test",
            ),
            onlineEnabled = false,
            resolvedClientId = "test-device",
        )

        assertEquals(scopeA, payload.getString("conversationId"))
        assertEquals(scopeA, payload.getString("sessionId"))
        assertEquals(scopeA, payload.getString("chatThreadId"))
        assertEquals("project_a", payload.getJSONObject("activeProject").getString("projectId"))
        assertFalse(payload.getJSONObject("activeProject").has("clientConversationId"))
    }
}
