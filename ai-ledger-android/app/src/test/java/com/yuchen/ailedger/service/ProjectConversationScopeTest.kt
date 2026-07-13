package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ProjectConversationScopeTest {
    private val scopeA = "chat_user-a"
    private val scopeB = "chat_user-b"

    @Before
    fun setUp() {
        AgentConversationScopeResolver.clearForTest()
        ProjectWorkspaceSessionContext.clear(null, scopeA)
        ProjectWorkspaceSessionContext.clear(null, scopeB)
    }

    @After
    fun tearDown() {
        AgentConversationScopeResolver.clearForTest()
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
    fun payloadUsesStableConversationScopeAndActiveProject() {
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

        val payload = buildPayload(messages)

        assertEquals(scopeA, payload.getString("conversationId"))
        assertEquals(scopeA, payload.getString("sessionId"))
        assertEquals(scopeA, payload.getString("chatThreadId"))
        assertEquals("project_a", payload.getJSONObject("activeProject").getString("projectId"))
        assertFalse(payload.getJSONObject("activeProject").has("clientConversationId"))
    }

    @Test
    fun conversationScopeSurvivesTruncatedHistory() {
        val initial = listOf(
            ChatMessage(id = "user-a", text = "创建网页", role = MessageRole.User),
            ChatMessage(id = "assistant-a", text = "完成", role = MessageRole.Assistant),
            ChatMessage(id = "user-a-2", text = "继续修改", role = MessageRole.User),
        )
        val initialScope = AgentConversationScopeResolver.resolve(null, initial)

        val truncated = listOf(
            ChatMessage(id = "user-a-2", text = "继续修改", role = MessageRole.User),
            ChatMessage(id = "assistant-a-2", text = "完成", role = MessageRole.Assistant),
            ChatMessage(id = "user-a-3", text = "再优化一下", role = MessageRole.User),
        )
        val restoredScope = AgentConversationScopeResolver.resolve(null, truncated)
        val newChatScope = AgentConversationScopeResolver.resolve(
            null,
            listOf(ChatMessage(id = "user-b", text = "另一个任务", role = MessageRole.User)),
        )

        assertEquals(scopeA, initialScope)
        assertEquals(initialScope, restoredScope)
        assertEquals(scopeB, newChatScope)
        assertNotEquals(initialScope, newChatScope)
    }

    private fun buildPayload(messages: List<ChatMessage>): JSONObject = AiWorkerPayloadBuilder.build(
        messages = messages,
        route = AiWorkerModelRoute(
            requested = ChatModel.Kimi,
            resolved = ChatModel.Kimi,
            reason = "test",
        ),
        onlineEnabled = false,
        resolvedClientId = "test-device",
    )
}
