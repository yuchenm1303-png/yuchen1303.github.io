package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningReobserveLoopTest {
    @Test
    fun firstVerifierReobserveUsesNormalReasoningEvenWhenPixelsChanged() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualProgressThenVerifierReobserves(count = 1),
        )

        assertEquals(VisualReasoningDepth.Normal, context.depth)
        assertEquals(1, context.noProgressCount)
        assertEquals(1, context.sameActionCount)
        assertTrue(VisualReasoningTrigger.FirstNoProgress in context.triggers)
    }

    @Test
    fun repeatedVerifierReobserveForcesDeepReplanDespiteDynamicChartFrames() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualProgressThenVerifierReobserves(count = 2),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(2, context.noProgressCount)
        assertEquals(2, context.sameActionCount)
        assertTrue(VisualReasoningTrigger.RepeatedNoProgress in context.triggers)
        assertNotNull(VisualReasoningPolicy.deepReplanLine(context))
    }

    @Test
    fun ordinaryLoadingWaitWithChangedScreenStillResetsTheActiveWindow() {
        val actions = actualProgressThenVerifierReobserves(count = 2) + listOf(
            "wait|等待搜索结果加载完成:ok:target=等待搜索结果加载完成:result=等待 700ms",
            "visual_execution_observed:action=wait|等待搜索结果加载完成|screenChanged=true|packageChanged=false|replanRequired=false",
        )

        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actions,
        )

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(0, context.sameActionCount)
    }

    private fun actualProgressThenVerifierReobserves(count: Int): List<String> = buildList {
        add("tap_xy|切换日K|248.0|807.0:ok:target=切换日K")
        add("visual_execution_observed:action=tap_xy|切换日K|248.0|807.0|screenChanged=true|packageChanged=false|replanRequired=false")
        repeat(count) {
            add("wait|重新观察:ok:target=重新观察:result=等待 220ms")
            add("visual_execution_observed:action=wait|重新观察|screenChanged=true|packageChanged=false|replanRequired=false")
        }
    }
}
