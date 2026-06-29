package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VisualTransactionalStateTest {
    @Test
    fun provisionalFinishCannotCommitCompletedTaskContract() {
        val root = workSurfaceResponse(
            step = JSONObject()
                .put("type", "finish")
                .put(
                    "args",
                    JSONObject()
                        .put("completionCandidate", true)
                        .put("responseSessionId", "agent-session")
                        .put("responseObservationId", "observation-1"),
                ),
            contract = completedContract(),
        )
        assertNull(VisualTaskContract.fromJson(root))
    }

    @Test
    fun backendTapRewriteCannotCommitWaitAsActualTaskState() {
        val root = workSurfaceResponse(
            step = validStep("wait").put(
                "args",
                JSONObject()
                    .put("rejectedActionType", "tap_xy")
                    .put("responseSessionId", "agent-session")
                    .put("responseObservationId", "observation-1"),
            ),
            contract = validContract(),
        ).put(
            "debug",
            JSONObject().put(
                "guiCompactAction",
                JSONObject().put("a", "tap_xy").put("x", 0.2).put("y", 0.3),
            ),
        )
        assertNull(VisualTaskContract.fromJson(root))
    }

    @Test
    fun syntheticSingleGoalContractIsRejectedBeforeExecution() {
        val decision = VisualTaskContractProtocol.validateContract(
            VisualTaskContract(
                originalGoal = "multi-step task",
                currentMilestoneId = "goal",
                milestones = listOf(
                    VisualTaskMilestone(
                        id = "goal",
                        title = "current action",
                        successEvidence = listOf("visible result"),
                    ),
                ),
            ),
        )
        assertFalse(decision.accepted)
        assertEquals("ordered_milestones_required", decision.code)
    }

    @Test
    fun workSurfaceWithoutContractBecomesRetryableProtocolFailure() {
        val error = assertProtocolFailure("visual_protocol_task_contract_required") {
            VisualTaskContract.fromJson(workSurfaceResponse(validStep("tap_xy"), null))
        }
        assertTrue(error.retryable)
        assertTrue(error.backendMessage.contains(VisualTaskContractProtocol.PROMPT_LINE))
    }

    @Test
    fun workSurfaceActionWithoutIntentBecomesRetryableProtocolFailure() {
        val error = assertProtocolFailure("visual_protocol_action_purpose_required") {
            VisualTaskContract.fromJson(
                workSurfaceResponse(
                    JSONObject().put("type", "tap_xy").put("x", 0.2).put("y", 0.3),
                    validContract(),
                ),
            )
        }
        assertTrue(error.retryable)
    }

    @Test
    fun structuredMultiMilestoneContractRemainsAccepted() {
        val contract = VisualTaskContract.fromJson(
            workSurfaceResponse(validStep("tap_xy"), validContract()),
        )
        assertEquals("return_home", contract?.currentMilestoneId)
        assertEquals(4, contract?.milestones?.size)
        assertEquals(
            listOf("open_detail", "verify_mode_a", "verify_mode_b"),
            contract?.completedMilestoneIds,
        )
    }

    @Test
    fun sameRevisionCannotReorderOrDeleteCommittedMilestones() {
        val previous = contractModel()
        val incoming = previous.copy(
            milestones = listOf(previous.milestones[1], previous.milestones[3]),
            currentMilestoneId = "return_home",
        )
        val decision = VisualTaskContractProtocol.validateTransition(previous, incoming)
        assertFalse(decision.accepted)
        assertEquals("contract_history_rewritten", decision.code)
    }

    @Test
    fun sameRevisionCannotRollbackCompletedMilestones() {
        val previous = contractModel()
        val incoming = previous.copy(
            completedMilestoneIds = listOf("open_detail"),
            milestones = previous.milestones.map { milestone ->
                milestone.copy(completed = milestone.id == "open_detail")
            },
        )
        val decision = VisualTaskContractProtocol.validateTransition(previous, incoming)
        assertFalse(decision.accepted)
        assertEquals("completed_milestone_rollback", decision.code)
    }

    @Test
    fun rolledBackCompletionCandidateIsRemovedButLaterCandidateIsKept() {
        val actions = listOf(
            "tap_xy|mode|0.2|0.3:ok:target=mode",
            "finish_verification_pending:observationId=observation-1",
            "finish_permit_rejected:reason=not_confirmed",
            "back:ok:target=back",
            "finish_verification_pending:observationId=observation-2",
        )
        val filtered = VisualLoopSupport.discardRolledBackCompletionCandidates(actions)
        assertFalse(filtered.contains("finish_verification_pending:observationId=observation-1"))
        assertTrue(filtered.contains("finish_permit_rejected:reason=not_confirmed"))
        assertTrue(filtered.contains("finish_verification_pending:observationId=observation-2"))
    }

    @Test
    fun gestureDispatchIsNotReportedAsSemanticSuccess() {
        val summary = VisualLoopSupport.resultSummary(
            step = CloudAgentStep(type = "back", targetText = "back"),
            signature = "back|back",
            result = AgentExecutionResult(true, "gesture dispatched", true),
        )
        assertTrue(summary.contains("executionAccepted=true"))
        assertTrue(summary.contains("gestureDispatched=true"))
        assertTrue(summary.contains("semanticOutcome=gui_plus_pending_judgement"))
        assertFalse(summary.contains("semanticOutcome=success"))
    }

    private fun workSurfaceResponse(step: JSONObject, contract: JSONObject?): JSONObject = JSONObject()
        .put("verifiedSurfaceState", "work_surface")
        .put("observationId", "observation-1")
        .put("agentStep", step)
        .apply { contract?.let { put("taskContract", it) } }

    private fun validStep(type: String): JSONObject = JSONObject()
        .put("type", type)
        .put("x", 0.2)
        .put("y", 0.3)
        .put("purpose", "advance the current milestone")
        .put("milestoneId", "return_home")
        .put("expectedEvidence", JSONArray().put("fresh visible evidence"))

    private fun validContract(): JSONObject = contractModel().toJson()

    private fun completedContract(): JSONObject = contractModel().copy(
        completedMilestoneIds = listOf("open_detail", "verify_mode_a", "verify_mode_b", "return_home"),
        milestones = contractModel().milestones.map { it.copy(completed = true) },
    ).toJson()

    private fun contractModel(): VisualTaskContract = VisualTaskContract(
        originalGoal = "complete an ordered visual task and verify the final page",
        currentMilestoneId = "return_home",
        milestones = listOf(
            milestone("open_detail", true),
            milestone("verify_mode_a", true),
            milestone("verify_mode_b", true),
            milestone("return_home", false),
        ),
        completedMilestoneIds = listOf("open_detail", "verify_mode_a", "verify_mode_b"),
        taskRevision = 1,
    )

    private fun milestone(id: String, completed: Boolean): VisualTaskMilestone = VisualTaskMilestone(
        id = id,
        title = id,
        purpose = "complete $id",
        successEvidence = listOf("visible evidence for $id"),
        failureEvidence = listOf("evidence contradicting $id"),
        completed = completed,
    )

    private fun assertProtocolFailure(
        expectedCode: String,
        block: () -> Unit,
    ): VisualAgentRequestException {
        try {
            block()
            fail("Expected VisualAgentRequestException")
        } catch (error: VisualAgentRequestException) {
            assertEquals(expectedCode, error.code)
            return error
        }
        throw AssertionError("unreachable")
    }
}
