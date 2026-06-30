package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualReasoningMechanicalRegressionTest {
    @Test
    fun repeatedTargetHistoryDoesNotCreateLocalReasoningState() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|back|81.941,191.629:ok:target=back",
                "tap_xy|back|97.840,180.833:ok:target=back",
            ),
        )

        assertEquals(0, context.sameActionCount)
        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
    }

    @Test
    fun coordinateDriftIsNotInterpretedLocally() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap@81.941,191.629:ok:target=back",
                "tap@97.840,180.833:ok:target=back",
            ),
        )

        assertEquals(0, context.sameActionCount)
        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
    }

    @Test
    fun protocolFailuresDoNotEscalateLocalReasoning() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "visual_route_retry:attempt=1|code=visual_protocol_task_contract_required|retryable=true",
                "visual_route_retry:attempt=2|code=visual_protocol_action_purpose_required|retryable=true",
            ),
        )

        assertEquals(0, context.executionFailureCount)
        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
    }

    @Test
    fun differentTargetsAlsoRemainNeutralLocally() {
        val context = VisualReasoningPolicy.evaluate(
            baseMemory(),
            listOf(
                "tap_xy|first|0.200|0.300:ok:target=first",
                "tap_xy|second|0.201|0.301:ok:target=second",
            ),
        )

        assertEquals(0, context.sameActionCount)
        assertEquals(VisualReasoningDepth.Fast, context.depth)
        assertTrue(context.triggers.isEmpty())
    }

    private fun baseMemory(): VisualTaskMemory = VisualTaskMemory(
        originalGoal = "mechanical watchdog test",
        currentMilestoneId = "m2",
        progressStatus = "surface_verified",
        legacyMode = false,
        taskContract = VisualTaskContract(
            originalGoal = "mechanical watchdog test",
            currentMilestoneId = "m2",
            milestones = listOf(
                VisualTaskMilestone(
                    id = "m1",
                    title = "first",
                    successEvidence = listOf("first evidence"),
                    completed = true,
                ),
                VisualTaskMilestone(
                    id = "m2",
                    title = "second",
                    successEvidence = listOf("second evidence"),
                ),
            ),
            completedMilestoneIds = listOf("m1"),
        ),
    )
}
