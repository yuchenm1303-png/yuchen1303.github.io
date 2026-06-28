package com.yuchen.ailedger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryCompilerTest {
    private val nowMillis = 1_800_000_000_000L

    @Test
    fun englishVocabularyQuestionActivatesExampleSentenceSkill() {
        val learningRule = AssistantMemoryItem(
            id = "english-rule",
            content = "以后你要尽力帮助我学习英语，我问的单词都要结合例句通俗解释。",
            category = "preference",
            scope = "auto",
            priority = 2,
            enabled = true,
            updatedAt = "2026-06-28T12:00:00Z",
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "brokered 是什么意思",
            customInstructions = null,
            memoryState = readyState(learningRule),
            nowMillis = nowMillis,
        )

        assertEquals(AssistantMemoryIntent.ENGLISH_VOCABULARY, compilation.intent)
        assertTrue(compilation.personaInstructions.orEmpty().contains("至少2个自然英文例句"))
        assertTrue(compilation.personaInstructions.orEmpty().contains("不要只给定义"))
        assertTrue(compilation.selectedMemoryIds.contains("english-rule"))
    }

    @Test
    fun englishSkillDoesNotPolluteUnrelatedAndroidQuestion() {
        val learningRule = AssistantMemoryItem(
            id = "english-rule",
            content = "以后我问英语单词时都要给例句和翻译。",
            category = "skill",
            scope = "english",
            priority = 3,
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "这个 Compose 重组问题怎么优化",
            customInstructions = null,
            memoryState = readyState(learningRule),
            nowMillis = nowMillis,
        )

        assertEquals(AssistantMemoryIntent.ANDROID_DEVELOPMENT, compilation.intent)
        assertFalse(compilation.personaInstructions.orEmpty().contains("至少2个自然英文例句"))
        assertFalse(compilation.selectedMemoryIds.contains("english-rule"))
    }

    @Test
    fun scopedAndroidProjectMemoryIsRetrievedForAndroidQuestion() {
        val androidProject = AssistantMemoryItem(
            id = "android-project",
            content = "AI Ledger 使用 Kotlin Compose，核心界面必须直接修改正式源码。",
            category = "project",
            scope = "android",
            priority = 2,
        )
        val travelMemory = AssistantMemoryItem(
            id = "travel",
            content = "用户计划去温州自驾旅行。",
            category = "project",
            scope = "travel",
            priority = 2,
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "检查一下 Android Compose 首页代码",
            customInstructions = null,
            memoryState = readyState(androidProject, travelMemory),
            nowMillis = nowMillis,
        )

        assertTrue(compilation.selectedMemoryIds.contains("android-project"))
        assertFalse(compilation.selectedMemoryIds.contains("travel"))
        assertTrue(
            compilation.memorySnapshot
                ?.optJSONArray("relevantMemories")
                ?.toString()
                .orEmpty()
                .contains("Kotlin Compose")
        )
    }

    @Test
    fun expiredAndDisabledMemoriesAreExcluded() {
        val expired = AssistantMemoryItem(
            id = "expired",
            content = "下周要参加考试。",
            category = "episode",
            scope = "general",
            validUntil = "2026-01-01T00:00:00Z",
        )
        val disabled = AssistantMemoryItem(
            id = "disabled",
            content = "回答必须使用列表。",
            category = "rule",
            scope = "global",
            enabled = false,
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "我最近有什么安排",
            customInstructions = null,
            memoryState = readyState(expired, disabled),
            nowMillis = nowMillis,
        )

        assertNull(compilation.memorySnapshot)
        assertNull(compilation.personaInstructions)
        assertTrue(compilation.selectedMemoryIds.isEmpty())
    }

    @Test
    fun higherPriorityConflictingProfileFactWins() {
        val oldName = AssistantMemoryItem(
            id = "old-name",
            content = "用户名字是小明。",
            category = "profile",
            priority = 1,
            updatedAt = "2026-01-01T00:00:00Z",
        )
        val currentName = AssistantMemoryItem(
            id = "current-name",
            content = "用户名字是邹羽宸。",
            category = "profile",
            priority = 3,
            updatedAt = "2026-06-28T00:00:00Z",
        )
        val compilation = AssistantMemoryCompiler.compile(
            userText = "你知道我的名字吗",
            customInstructions = null,
            memoryState = readyState(oldName, currentName),
            nowMillis = nowMillis,
        )

        val profile = compilation.memorySnapshot?.optString("profileSummary").orEmpty()
        assertTrue(profile.contains("邹羽宸"))
        assertFalse(profile.contains("小明"))
        assertEquals(1, compilation.suppressedConflictCount)
    }

    @Test
    fun customInstructionsArePromotedWithoutBecomingBackgroundMemory() {
        val compilation = AssistantMemoryCompiler.compile(
            userText = "帮我润色这段报告",
            customInstructions = "写作时保持学术语气，但不要堆砌术语。",
            memoryState = readyState(),
            nowMillis = nowMillis,
        )

        assertTrue(compilation.personaInstructions.orEmpty().contains("保持学术语气"))
        assertNull(compilation.memorySnapshot)
        assertTrue(compilation.hasAnyContext)
    }

    private fun readyState(vararg items: AssistantMemoryItem): AssistantMemoryState {
        return AssistantMemoryState(
            cloudReady = true,
            memoryEnabled = true,
            memories = items.toList(),
        )
    }
}
