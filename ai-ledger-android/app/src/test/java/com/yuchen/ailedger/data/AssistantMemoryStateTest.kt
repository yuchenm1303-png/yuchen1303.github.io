package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryStateTest {
    @Test
    fun disabledMemoryHasNoActiveItemsButKeepsManageableCount() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = false,
            memories = listOf(AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答")),
        )

        assertEquals(0, state.activeCount)
        assertEquals(1, state.enabledItemCount)
    }

    @Test
    fun unavailableCloudHasNoActiveItems() {
        val state = AssistantMemoryState(
            cloudReady = false,
            memoryEnabled = true,
            memories = listOf(AssistantMemoryItem(id = "1", content = "用户喜欢简洁回答")),
        )

        assertEquals(0, state.activeCount)
    }

    @Test
    fun activeCountUsesV4StatusAndEnabledState() {
        val state = AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = true,
            memories = listOf(
                AssistantMemoryItem(id = "1", content = "启用内容", enabled = true, status = "active"),
                AssistantMemoryItem(id = "2", content = "归档内容", enabled = false, status = "archived"),
                AssistantMemoryItem(id = "3", content = "停用内容", enabled = false, status = "active"),
                AssistantMemoryItem(id = "4", content = "已删除内容", enabled = false, status = "deleted"),
            ),
        )

        assertEquals(1, state.activeCount)
        assertEquals(1, state.enabledItemCount)
    }

    @Test
    fun v4MemoryActivityHonorsValidityWindow() {
        val nowMillis = 1_800_000_000_000L
        val active = AssistantMemoryItem(
            id = "active",
            content = "有效内容",
            validFrom = "2027-01-14T00:00:00Z",
            validUntil = "2027-01-16T00:00:00Z",
        )
        val notStarted = active.copy(
            id = "future",
            validFrom = "2027-01-16T00:00:00Z",
            validUntil = "2027-01-17T00:00:00Z",
        )
        val expired = active.copy(
            id = "expired",
            validFrom = "2027-01-10T00:00:00Z",
            validUntil = "2027-01-14T00:00:00Z",
        )

        assertTrue(active.isActiveAt(nowMillis))
        assertFalse(notStarted.isActiveAt(nowMillis))
        assertFalse(expired.isActiveAt(nowMillis))
    }

    @Test
    fun archivedAndSupersededMemoriesNeverBecomeActive() {
        val nowMillis = 1_800_000_000_000L
        val base = AssistantMemoryItem(id = "1", content = "内容")

        assertFalse(base.copy(status = "archived", enabled = false).isActiveAt(nowMillis))
        assertFalse(base.copy(status = "superseded", enabled = false).isActiveAt(nowMillis))
        assertFalse(base.copy(status = "deleted", enabled = false).isActiveAt(nowMillis))
    }

    @Test
    fun customInstructionsPreserveParagraphsAndRespectLimit() {
        val raw = "第一条规则\r\n\r\n第二条   规则" + "x".repeat(ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH)

        val normalized = normalizeCustomInstructions(raw)

        assertTrue(normalized.contains("第一条规则\n\n第二条 规则"))
        assertEquals(ASSISTANT_CUSTOM_INSTRUCTIONS_MAX_LENGTH, normalized.length)
    }
}
