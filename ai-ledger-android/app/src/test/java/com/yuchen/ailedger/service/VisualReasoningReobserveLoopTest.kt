package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningReobserveLoopTest {
    @Test
    fun firstReobserveDoesNotCreateLocalReasoningPressure() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualActionThenReobserves(count = 1, frameChanged = true),
        )

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(0, context.sameActionCount)
        assertTrue(context.triggers.isEmpty())
        assertNull(VisualReasoningPolicy.deepReplanLine(context))
    }

    @Test
    fun repeatedReobserveStillLeavesReasoningToCloud() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualActionThenReobserves(count = 2, frameChanged = true),
        )

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertEquals(0, context.sameActionCount)
        assertTrue(context.triggers.isEmpty())
        assertNull(VisualReasoningPolicy.deepReplanLine(context))
    }

    @Test
    fun staticFrameReobserveDoesNotTriggerAndroidDeepMode() {
        val context = VisualReasoningPolicy.evaluate(
            memory = VisualTaskMemory(originalGoal = "测试任务"),
            recentActions = actualActionThenReobserves(count = 2, frameChanged = false),
        )

        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertEquals(0, context.noProgressCount)
        assertTrue(context.triggers.isEmpty())
    }

    @Test
    fun ordinaryLoadingWaitAlsoRemainsNeutral() {
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
        assertEquals(0, context.sameActionCount)
        assertTrue(context.triggers.isEmpty())
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
