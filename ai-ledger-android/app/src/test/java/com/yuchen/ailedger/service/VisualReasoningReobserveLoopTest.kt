package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningReobserveLoopTest {
    @Test
    fun firstExplicitReobserveUsesNormalRegardlessOfDynamicFrame() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualActionThenReobserves(count = 1, frameChanged = true),
        )

        assertEquals(VisualReasoningDepth.Normal, context.depth)
        assertEquals(1, context.noProgressCount)
        assertEquals(1, context.sameActionCount)
        assertTrue(VisualReasoningTrigger.FirstNoProgress in context.triggers)
    }

    @Test
    fun repeatedReobserveForcesDeepEvenWhenChartPixelsKeepChanging() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualActionThenReobserves(count = 2, frameChanged = true),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(2, context.noProgressCount)
        assertEquals(2, context.sameActionCount)
        assertTrue(VisualReasoningTrigger.RepeatedNoProgress in context.triggers)
        assertNotNull(VisualReasoningPolicy.deepReplanLine(context))
    }

    @Test
    fun repeatedReobserveAlsoForcesDeepWhenFrameIsStatic() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualActionThenReobserves(count = 2, frameChanged = false),
        )

        assertEquals(VisualReasoningDepth.Deep, context.depth)
        assertEquals(2, context.noProgressCount)
    }

    @Test
    fun ordinaryLoadingWaitIsDistinctAndClearsReobservePressure() {
        val actions = actualActionThenReobserves(count = 2, frameChanged = true) + listOf(
            "wait|等待搜索结果加载完成:ok:target=等待搜索结果加载完成:result=等待 700ms",
            "visual_execution_observed:action=wait|等待搜索结果加载完成|frameChanged=true|replanRequired=false",
        )

        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actions,
        )

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(1, context.sameActionCount)
    }

    private fun actualActionThenReobserves(
        count: Int,
        frameChanged: Boolean,
    ): List<String> = buildList {
        add("tap_xy|切换日K|0.20|0.30:ok:target=切换日K")
        add("visual_execution_observed:action=tap_xy|切换日K|frameChanged=true|replanRequired=false")
        repeat(count) {
            add("wait|重新观察:ok:target=重新观察:result=等待 220ms")
            add("visual_execution_observed:action=wait|重新观察|frameChanged=$frameChanged|replanRequired=false")
        }
    }
}
