package com.yuchen.ailedger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VisualTaskContractOrderingTest {
    @Test
    fun currentMilestoneCannotAlreadyBeCompleted() {
        val decision = VisualTaskContractProtocol.validateContract(
            contract(
                currentMilestoneId = "verify",
                completedIds = listOf("open", "verify"),
            ),
        )

        assertFalse(decision.accepted)
        assertEquals("current_milestone_already_completed", decision.code)
    }

    @Test
    fun currentMilestoneCannotSkipUnfinishedPriorMilestone() {
        val decision = VisualTaskContractProtocol.validateContract(
            contract(
                currentMilestoneId = "verify",
                completedIds = emptyList(),
            ),
        )

        assertFalse(decision.accepted)
        assertEquals("current_milestone_skips_unfinished_prior", decision.code)
    }

    @Test
    fun currentMilestoneMayAdvanceAfterAllPriorMilestonesAreCompleted() {
        val decision = VisualTaskContractProtocol.validateContract(
            contract(
                currentMilestoneId = "verify",
                completedIds = listOf("open"),
            ),
        )

        assertEquals(VisualTaskContractProtocol.Decision.Accepted, decision)
    }

    private fun contract(
        currentMilestoneId: String,
        completedIds: List<String>,
    ): VisualTaskContract = VisualTaskContract(
        originalGoal = "complete an ordered visual task",
        currentMilestoneId = currentMilestoneId,
        milestones = listOf(
            VisualTaskMilestone(
                id = "open",
                title = "open",
                purpose = "open the target",
                successEvidence = listOf("target is visible"),
                completed = "open" in completedIds,
            ),
            VisualTaskMilestone(
                id = "verify",
                title = "verify",
                purpose = "verify the target",
                successEvidence = listOf("verification is visible"),
                completed = "verify" in completedIds,
            ),
        ),
        completedMilestoneIds = completedIds,
        taskRevision = 1,
    )
}
