package com.yuchen.ailedger.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantMemoryBackendOwnedCompilerTest {
    @Before
    fun resetAccount() {
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        AssistantAccountSessionRuntime.updateUser(null)
    }

    @After
    fun cleanup() {
        AssistantMemoryRequestContextRuntime.clearCurrentThread()
        AssistantAccountSessionRuntime.updateUser(null)
    }

    @Test
    fun backendOwnedChatContractDoesNotNeedLocalInventoryOrInstructionBody() {
        AssistantAccountSessionRuntime.updateUser("user-a")
        val compilation = AssistantMemoryCompiler.compileBackendOwned(
            userText = "继续处理 Android 项目",
        )

        assertTrue(compilation.memoryRequested)
        assertTrue(compilation.hasAnyContext)
        assertEquals("auto", compilation.requestMode)
        assertEquals("backend_cloud_requested", compilation.selectionStatus)
        assertEquals("backend_cloud_v4", compilation.selectionOwner)
        assertEquals(setOf("backend_cloud_v4"), compilation.activeScopes)
        assertNull(compilation.personaConfigJson())
        assertNull(compilation.memorySnapshot)
        assertTrue(compilation.selectedMemoryIds.isEmpty())
        assertTrue(compilation.sources.isEmpty())
        assertEquals("user-a", AssistantMemoryRequestContextRuntime.peekCurrentThread()?.ticket?.userId)
    }

    @Test
    fun blankInputDoesNotRequestMemoryOrKeepRequestContext() {
        AssistantAccountSessionRuntime.updateUser("user-a")
        val compilation = AssistantMemoryCompiler.compileBackendOwned(
            userText = "   ",
        )

        assertFalse(compilation.memoryRequested)
        assertFalse(compilation.hasAnyContext)
        assertEquals("off", compilation.requestMode)
        assertNull(compilation.personaConfigJson())
        assertEquals("empty", compilation.selectionStatus)
        assertNull(AssistantMemoryRequestContextRuntime.peekCurrentThread())
    }
}
