package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryStateTest {
    @Test
    fun disabledMemoryDoesNotCreateSnapshot() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = false,
            memories = listOf(AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答"))
        )

        assertNull(state.snapshotText())
    }

    @Test
    fun unavailableCloudDoesNotCreateSnapshot() {
        val state = AssistantMemoryState(
            cloudReady = false,
            memoryEnabled = true,
            memories = listOf(AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答"))
        )

        assertNull(state.snapshotText())
    }

    @Test
    fun snapshotContainsOnlyEnabledDistinctMemories() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = true,
            memories = listOf(
                AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答", enabled = true),
                AssistantMemoryItem(id = "2", content = "不要使用太多分点", enabled = false),
                AssistantMemoryItem(id = "3", content = " 用户喜欢简洁回答 ", enabled = true),
                AssistantMemoryItem(id = "4", content = "当前项目使用 Kotlin Compose", enabled = true)
            )
        )

        val snapshot = state.snapshotText()

        assertTrue(snapshot?.contains("用户喜欢简洁回答") == true)
        assertTrue(snapshot?.contains("当前项目使用 Kotlin Compose") == true)
        assertFalse(snapshot?.contains("不要使用太多分点") == true)
        assertEquals(1, snapshot?.windowed("用户喜欢简洁回答".length)?.count { it == "用户喜欢简洁回答" })
    }
}
