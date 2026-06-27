package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryStateTest {
    @Test
    fun disabledMemoryDoesNotCreateSnapshotAndActiveCountIsZero() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = false,
            memories = listOf(AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答"))
        )

        assertNull(state.snapshotText())
        assertEquals(0, state.activeCount)
        assertEquals(1, state.enabledItemCount)
    }

    @Test
    fun unavailableCloudDoesNotCreateSnapshot() {
        val state = AssistantMemoryState(
            cloudReady = false,
            memoryEnabled = true,
            memories = listOf(AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答"))
        )

        assertNull(state.snapshotText())
        assertEquals(0, state.activeCount)
    }

    @Test
    fun snapshotContainsOnlyEnabledDistinctMemories() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = true,
            memories = listOf(
                AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答", enabled = true),
                AssistantMemoryItem(id = "2", content = "不要使用太多分点", enabled = false),
                AssistantMemoryItem(id = "3", content = "用户喜欢简洁回答", enabled = true),
                AssistantMemoryItem(id = "4", content = "当前项目使用 Kotlin Compose", enabled = true)
            )
        )

        val snapshot = state.snapshotText()

        assertTrue(snapshot?.contains("用户喜欢简洁回答") == true)
        assertTrue(snapshot?.contains("当前项目使用 Kotlin Compose") == true)
        assertFalse(snapshot?.contains("不要使用太多分点") == true)
        assertEquals(1, snapshot?.windowed("用户喜欢简洁回答".length)?.count { it == "用户喜欢简洁回答" })
    }

    @Test
    fun snapshotPrioritizesPinnedAndCoreMemories() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = true,
            memories = listOf(
                AssistantMemoryItem(id = "1", content = "普通内容", priority = 1),
                AssistantMemoryItem(id = "2", content = "核心内容", priority = 3),
                AssistantMemoryItem(id = "3", content = "置顶内容", priority = 0, pinned = true)
            )
        )

        val snapshot = state.snapshotText().orEmpty()

        assertTrue(snapshot.indexOf("置顶内容") < snapshot.indexOf("核心内容"))
        assertTrue(snapshot.indexOf("核心内容") < snapshot.indexOf("普通内容"))
        assertTrue(snapshot.length <= ASSISTANT_MEMORY_SNAPSHOT_MAX_LENGTH)
    }

    @Test
    fun customInstructionsPreserveParagraphsAndRespectLimit() {
        val raw = "第一条规则\r\n\r\n第二条   规则" + "x".repeat(ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH)

        val normalized = normalizeCustomInstructions(raw)

        assertTrue(normalized.contains("第一条规则\n\n第二条 规则"))
        assertEquals(ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH, normalized.length)
    }
}
