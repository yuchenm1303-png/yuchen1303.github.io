package com.yuchen.ailedger.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualGestureExecutionPolicyTest {
    @Test
    fun completedTapIsTheOnlySuccessfulTapOutcome() {
        val completed = VisualGestureExecutionPolicy.tapResult(
            VisualGestureDispatchOutcome.Completed,
            "已点击目标",
        )
        val cancelled = VisualGestureExecutionPolicy.tapResult(
            VisualGestureDispatchOutcome.Cancelled,
            "已点击目标",
        )
        val timedOut = VisualGestureExecutionPolicy.tapResult(
            VisualGestureDispatchOutcome.TimedOut,
            "已点击目标",
        )

        assertTrue(completed.ok)
        assertTrue(completed.shouldContinue)
        assertFalse(cancelled.ok)
        assertFalse(cancelled.shouldContinue)
        assertFalse(timedOut.ok)
        assertFalse(timedOut.shouldContinue)
    }

    @Test
    fun completedSwipeIsTheOnlySuccessfulSwipeOutcome() {
        val completed = VisualGestureExecutionPolicy.swipeResult(
            VisualGestureDispatchOutcome.Completed,
            "up",
        )
        val rejected = VisualGestureExecutionPolicy.swipeResult(
            VisualGestureDispatchOutcome.Rejected,
            "up",
        )

        assertTrue(completed.ok)
        assertTrue(completed.shouldContinue)
        assertFalse(rejected.ok)
        assertFalse(rejected.shouldContinue)
    }
}
