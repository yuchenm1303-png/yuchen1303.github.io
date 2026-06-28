package com.yuchen.ailedger.data

import com.yuchen.ailedger.service.CLOUD_MEMORY_CUSTOM_ORIGIN_ID
import com.yuchen.ailedger.service.CloudMemoryCandidate
import com.yuchen.ailedger.service.CloudMemorySelectionResult
import com.yuchen.ailedger.service.CloudSelectedMemory
import com.yuchen.ailedger.service.buildCloudMemoryBatches
import com.yuchen.ailedger.service.buildCloudMemoryCandidates
import com.yuchen.ailedger.service.parseCloudMemorySelectionReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryCompilerTest {
    private val nowMillis = 1_800_000_000_000L

    @Test
    fun activeMemoriesEnterCloudCorpusWithoutLocalTopicFiltering() {
        val memories = listOf(
            memory("english", "问英语单词时结合例句解释。", "english"),
            memory("travel", "用户准备去温州旅行。", "travel"),
        )
        val candidates = buildCloudMemoryCandidates(null, readyState(memories), nowMillis)
        assertEquals(setOf("english", "travel"), candidates.map { it.originId }.toSet())
    }

    @Test
    fun objectiveStateFilteringAndBatchingPreserveEligibleItems() {
        val active = (1..80).map { memory("m$it", "内容$it", "auto") }
        val disabled = memory("off", "停用内容", "auto").copy(enabled = false)
        val candidates = buildCloudMemoryCandidates(null, readyState(active + disabled), nowMillis)
        val flattened = buildCloudMemoryBatches(candidates).flatten()
        assertEquals(active.map { it.id }, flattened.map { it.originId })
    }

    @Test
    fun parserOnlyAcceptsExactCandidateIds() {
        val first = candidate("first", "第一条")
        val second = candidate("second", "第二条")
        val result = parseCloudMemorySelectionReply(
            """{"selected":[{"id":"first#1","role":"instruction","reason":"适用"},{"id":"unknown#1","role":"profile"}],"suppressedCount":1}""",
            listOf(first, second),
        )
        assertEquals("selected", result.status)
        assertEquals(listOf("first"), result.selections.map { it.candidate.originId })
        assertEquals("instruction", result.selections.single().role)
    }

    @Test
    fun cloudInstructionBuildsPersonaAndUsageId() {
        val selected = CloudSelectedMemory(
            candidate("english-rule", "问英语单词时必须给出自然例句和中文翻译。"),
            role = "instruction",
            reason = "当前请求适用",
        )
        val compilation = AssistantMemoryCompiler.composeCloudCompilation(
            CloudMemorySelectionResult("selected", listOf(selected))
        )
        assertEquals(AssistantMemoryIntent.CLOUD_ORCHESTRATED, compilation.intent)
        assertTrue(compilation.personaInstructions.orEmpty().contains("自然例句"))
        assertEquals(listOf("english-rule"), compilation.selectedMemoryIds)
    }

    @Test
    fun selectorFailureHasNoLocalFallbackAndCustomInstructionIsNotUsageId() {
        val failed = AssistantMemoryCompiler.composeCloudCompilation(
            CloudMemorySelectionResult("unavailable", errorCode = "cloud_selector_timeout")
        )
        assertFalse(failed.hasAnyContext)
        assertTrue(failed.selectedMemoryIds.isEmpty())

        val custom = CloudSelectedMemory(
            CloudMemoryCandidate(
                "$CLOUD_MEMORY_CUSTOM_ORIGIN_ID#1",
                CLOUD_MEMORY_CUSTOM_ORIGIN_ID,
                "回答保持自然。",
                "explicit_instruction",
                "user_defined",
                "user_explicit",
                3,
                true,
                true,
            ),
            "instruction",
            "全局要求",
        )
        val customCompilation = AssistantMemoryCompiler.composeCloudCompilation(
            CloudMemorySelectionResult("selected", listOf(custom))
        )
        assertTrue(customCompilation.hasAnyContext)
        assertTrue(customCompilation.selectedMemoryIds.isEmpty())
    }

    private fun readyState(items: List<AssistantMemoryItem>) = AssistantMemoryState(
        cloudReady = true,
        memoryEnabled = true,
        memories = items,
    )

    private fun memory(id: String, content: String, scope: String) = AssistantMemoryItem(
        id = id,
        content = content,
        scope = scope,
    )

    private fun candidate(id: String, content: String) = CloudMemoryCandidate(
        "$id#1",
        id,
        content,
        "other",
        "auto",
        "manual",
        1,
        false,
        false,
    )
}
