package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryBackendOwnedCompilerTest {
    @Test
    fun backendOwnedChatContractDoesNotNeedLocalInventoryState() {
        val compilation = AssistantMemoryCompiler.compileBackendOwned(
            userText = "继续处理 Android 项目",
            customInstructions = "回答保持简洁。",
        )

        assertTrue(compilation.memoryRequested)
        assertTrue(compilation.hasAnyContext)
        assertEquals("auto", compilation.requestMode)
        assertEquals("backend_cloud_requested", compilation.selectionStatus)
        assertEquals("backend_cloud_v4", compilation.selectionOwner)
        assertEquals(setOf("backend_cloud_v4"), compilation.activeScopes)
        assertEquals("回答保持简洁。", compilation.personaConfigJson()?.optString("customInstructions"))
        assertNull(compilation.memorySnapshot)
        assertTrue(compilation.selectedMemoryIds.isEmpty())
        assertTrue(compilation.sources.isEmpty())
    }

    @Test
    fun blankInputDoesNotRequestMemoryOrSendInstructions() {
        val compilation = AssistantMemoryCompiler.compileBackendOwned(
            userText = "   ",
            customInstructions = "回答简洁。",
        )

        assertFalse(compilation.memoryRequested)
        assertFalse(compilation.hasAnyContext)
        assertEquals("off", compilation.requestMode)
        assertNull(compilation.personaConfigJson())
        assertEquals("empty", compilation.selectionStatus)
    }
}
