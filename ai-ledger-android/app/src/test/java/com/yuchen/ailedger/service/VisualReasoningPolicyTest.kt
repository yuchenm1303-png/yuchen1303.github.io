package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningPolicyTest {
    @Test
    fun reasoningPolicyIsNeutral() {
        val context = VisualReasoningPolicy.evaluate(VisualTaskMemory(originalGoal = "test"))
        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
        assertFalse(context.deepThinkingRequested)
    }
}
