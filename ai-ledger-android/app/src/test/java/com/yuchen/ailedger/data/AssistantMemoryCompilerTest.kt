package com.yuchen.ailedger.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantMemoryCompilerTest {
    @Before
    fun resetBefore() {
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
    }

    @After
    fun resetAfter() {
        AssistantAccountSessionRuntime.updateUser(null)
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
    }

    @Test
    fun normalChatOnlyRequestsBackendMemoryWithoutLocalContentOrInstructions() {
        val memory = AssistantMemoryItem(
            id = "profile-name",
            content = "用户姓名为测试用户",
            category = "profile",
            scope = "global",
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "你还记得我吗",
            memoryState = readyState(listOf(memory)),
        )

        assertTrue(compilation.memoryRequested)
        assertTrue(compilation.hasAnyContext)
        assertEquals("auto", compilation.requestMode)
        assertEquals("backend_cloud_requested", compilation.selectionStatus)
        assertEquals("backend_cloud_v4", compilation.selectionOwner)
        assertEquals("ai_ledger_cloud_memory_request_v3", compilation.schema)
        assertTrue(compilation.selectedMemoryIds.isEmpty())
        assertTrue(compilation.sources.isEmpty())
        assertNull(compilation.memorySnapshot)
        assertNull(compilation.personaConfigJson())
        assertFalse(compilation.diagnosticsJson().toString().contains("测试用户"))
    }

    @Test
    fun differentTopicsProduceTheSameNonSemanticRequestContract() {
        val state = readyState(
            listOf(
                AssistantMemoryItem(id = "name", content = "用户姓名为测试用户", category = "profile"),
                AssistantMemoryItem(id = "english", content = "英语单词需要例句", category = "preference"),
            ),
        )

        val identityQuestion = AssistantMemoryCompiler.compile("你认识我吗", state)
        val englishQuestion = AssistantMemoryCompiler.compile("resilient 是什么意思", state)
        val projectQuestion = AssistantMemoryCompiler.compile("继续处理 Android 项目", state)

        listOf(identityQuestion, englishQuestion, projectQuestion).forEach { compilation ->
            assertTrue(compilation.memoryRequested)
            assertEquals("auto", compilation.requestMode)
            assertEquals("backend_cloud_requested", compilation.selectionStatus)
            assertTrue(compilation.selectedMemoryIds.isEmpty())
            assertTrue(compilation.sources.isEmpty())
            assertNull(compilation.memorySnapshot)
            assertNull(compilation.personaConfigJson())
        }
    }

    @Test
    fun locallyDisabledMemoryDoesNotReenableThroughInstructions() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "解释一下这个概念",
            memoryState = AssistantMemoryState(
                accountUserId = "user-test",
                cloudReady = true,
                memoryEnabled = false,
            ),
        )

        assertFalse(compilation.memoryRequested)
        assertFalse(compilation.hasAnyContext)
        assertEquals("off", compilation.requestMode)
        assertEquals("disabled_by_user", compilation.selectionStatus)
        assertNull(compilation.personaConfigJson())
    }

    @Test
    fun unknownLocalIdentityStillDelegatesAuthenticationToBackend() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "你好",
            memoryState = AssistantMemoryState(),
        )

        assertTrue(compilation.memoryRequested)
        assertTrue(compilation.hasAnyContext)
        assertEquals("auto", compilation.requestMode)
        assertEquals("backend_identity_pending", compilation.selectionStatus)
        assertEquals(setOf("backend_cloud_v4"), compilation.activeScopes)
    }

    @Test
    fun cloudInventoryUnavailableStillDelegatesDecisionToBackend() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "继续上次的项目",
            memoryState = AssistantMemoryState(
                accountUserId = "user-test",
                cloudReady = false,
                memoryEnabled = false,
                loading = true,
            ),
        )

        assertTrue(compilation.memoryRequested)
        assertEquals("auto", compilation.requestMode)
        assertEquals("backend_cloud_requested", compilation.selectionStatus)
        assertEquals(setOf("backend_cloud_v4"), compilation.activeScopes)
    }

    @Test
    fun cloudInventoryFailureDoesNotBecomeAConfirmedUserOptOut() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "你还记得我的偏好吗",
            memoryState = AssistantMemoryState(
                accountUserId = "user-test",
                cloudReady = false,
                memoryEnabled = false,
                error = true,
            ),
        )

        assertTrue(compilation.memoryRequested)
        assertEquals("backend_cloud_requested", compilation.selectionStatus)
    }

    @Test
    fun blankInputDoesNotRequestMemoryOrKeepContext() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "   ",
            memoryState = readyState(emptyList()),
        )

        assertFalse(compilation.memoryRequested)
        assertNull(compilation.personaConfigJson())
        assertEquals("empty", compilation.selectionStatus)
        assertNull(AssistantMemoryRequestContextRuntime.peekCurrentThread())
    }

    @Test
    fun localMemoryInventoryDoesNotAffectRequestDecision() {
        val withoutItems = AssistantMemoryCompiler.compile(
            userText = "继续",
            memoryState = readyState(emptyList()),
        )
        val withItems = AssistantMemoryCompiler.compile(
            userText = "继续",
            memoryState = readyState(
                listOf(
                    AssistantMemoryItem(id = "one", content = "任意正文"),
                    AssistantMemoryItem(id = "two", content = "另一条正文", enabled = false, status = "archived"),
                ),
            ),
        )

        assertEquals(withoutItems.memoryRequested, withItems.memoryRequested)
        assertEquals(withoutItems.selectionStatus, withItems.selectionStatus)
        assertEquals(withoutItems.diagnosticsJson().toString(), withItems.diagnosticsJson().toString())
    }

    private fun readyState(items: List<AssistantMemoryItem>) = AssistantMemoryState(
        accountUserId = "user-test",
        cloudReady = true,
        memoryEnabled = true,
        memories = items,
    )
}
