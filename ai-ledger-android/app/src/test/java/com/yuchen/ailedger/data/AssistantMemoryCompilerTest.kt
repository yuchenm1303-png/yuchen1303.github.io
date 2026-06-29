package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryCompilerTest {
    @Test
    fun normalChatOnlyRequestsBackendMemoryWithoutLocalContent() {
        val memory = AssistantMemoryItem(
            id = "profile-name",
            content = "用户姓名为测试用户",
            category = "profile",
            scope = "global",
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "你还记得我吗",
            customInstructions = "回答保持通俗。",
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
        assertFalse(compilation.diagnosticsJson().toString().contains("测试用户"))
    }

    @Test
    fun differentTopicsProduceTheSameNonSemanticRequestContract() {
        val state = readyState(
            listOf(
                AssistantMemoryItem(id = "name", content = "用户姓名为测试用户", category = "profile"),
                AssistantMemoryItem(id = "english", content = "英语单词需要例句", category = "preference"),
            )
        )

        val identityQuestion = AssistantMemoryCompiler.compile("你认识我吗", null, state)
        val englishQuestion = AssistantMemoryCompiler.compile("resilient 是什么意思", null, state)
        val projectQuestion = AssistantMemoryCompiler.compile("继续处理 Android 项目", null, state)

        listOf(identityQuestion, englishQuestion, projectQuestion).forEach { compilation ->
            assertTrue(compilation.memoryRequested)
            assertEquals("auto", compilation.requestMode)
            assertEquals("backend_cloud_requested", compilation.selectionStatus)
            assertTrue(compilation.selectedMemoryIds.isEmpty())
            assertTrue(compilation.sources.isEmpty())
            assertNull(compilation.memorySnapshot)
        }
    }

    @Test
    fun customInstructionsRemainIndependentFromLongTermMemory() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "解释一下这个概念",
            customInstructions = "回答保持简洁。",
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
        assertEquals("回答保持简洁。", compilation.personaConfigJson()?.optString("customInstructions"))
    }

    @Test
    fun anonymousChatDoesNotRequestUserMemory() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "你好",
            customInstructions = null,
            memoryState = AssistantMemoryState(),
        )

        assertFalse(compilation.memoryRequested)
        assertFalse(compilation.hasAnyContext)
        assertEquals("off", compilation.requestMode)
        assertEquals("disabled_anonymous", compilation.selectionStatus)
    }

    @Test
    fun cloudInventoryUnavailableStillDelegatesDecisionToBackend() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "继续上次的项目",
            customInstructions = null,
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
            customInstructions = null,
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
    fun blankInputDoesNotRequestMemoryOrSendInstructions() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "   ",
            customInstructions = "回答简洁。",
            memoryState = readyState(emptyList()),
        )

        assertFalse(compilation.memoryRequested)
        assertNull(compilation.personaConfigJson())
        assertEquals("empty", compilation.selectionStatus)
    }

    @Test
    fun localMemoryInventoryDoesNotAffectRequestDecision() {
        val withoutItems = AssistantMemoryCompiler.compile(
            userText = "继续",
            customInstructions = null,
            memoryState = readyState(emptyList()),
        )
        val withItems = AssistantMemoryCompiler.compile(
            userText = "继续",
            customInstructions = null,
            memoryState = readyState(
                listOf(
                    AssistantMemoryItem(id = "one", content = "任意正文"),
                    AssistantMemoryItem(id = "two", content = "另一条正文", enabled = false, status = "archived"),
                )
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
