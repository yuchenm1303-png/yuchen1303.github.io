package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualTransactionalStateTest {
    @Test
    fun provisionalFinishCannotCommitCompletedTaskContract() {
        val root = JSONObject()
            .put(
                "agentStep",
                JSONObject()
                    .put("type", "finish")
                    .put(
                        "args",
                        JSONObject()
                            .put("completionCandidate", true)
                            .put("responseSessionId", "agent-session")
                            .put("responseObservationId", "observation-1"),
                    ),
            )
            .put("taskContract", syntheticCompletedGoalContract())

        assertNull(VisualTaskContract.fromJson(root))
    }

    @Test
    fun backendTapRewriteCannotCommitWaitAsActualTaskState() {
        val root = JSONObject()
            .put(
                "agentStep",
                JSONObject()
                    .put("type", "wait")
                    .put(
                        "args",
                        JSONObject()
                            .put("rejectedActionType", "tap_xy")
                            .put("responseSessionId", "agent-session")
                            .put("responseObservationId", "observation-1"),
                    ),
            )
            .put(
                "debug",
                JSONObject().put(
                    "guiCompactAction",
                    JSONObject()
                        .put("a", "tap_xy")
                        .put("x", 0.2)
                        .put("y", 0.3)
                        .put("t", "日K"),
                ),
            )
            .put("taskContract", syntheticCompletedGoalContract())

        assertNull(VisualTaskContract.fromJson(root))
    }

    @Test
    fun syntheticSingleGoalActionContractIsNeverCommitted() {
        val root = JSONObject()
            .put(
                "agentStep",
                JSONObject()
                    .put("type", "tap_xy")
                    .put("x", 0.2)
                    .put("y", 0.3),
            )
            .put(
                "taskContract",
                JSONObject()
                    .put("originalGoal", "复杂任务")
                    .put("currentMilestoneId", "goal")
                    .put(
                        "milestones",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "goal")
                                .put("title", "点击日K")
                                .put("completed", false),
                        ),
                    ),
            )

        assertNull(VisualTaskContract.fromJson(root))
    }

    @Test
    fun structuredMultiMilestoneContractRemainsAccepted() {
        val root = JSONObject()
            .put("agentStep", JSONObject().put("type", "tap_xy").put("x", 0.2).put("y", 0.3))
            .put(
                "taskContract",
                JSONObject()
                    .put("originalGoal", "查看贵州茅台并返回行情")
                    .put("currentMilestoneId", "return_market")
                    .put(
                        "milestones",
                        JSONArray()
                            .put(JSONObject().put("id", "open_stock").put("completed", true))
                            .put(JSONObject().put("id", "verify_day_k").put("completed", true))
                            .put(JSONObject().put("id", "verify_timeline").put("completed", true))
                            .put(JSONObject().put("id", "return_market").put("completed", false)),
                    )
                    .put(
                        "completedMilestoneIds",
                        JSONArray().put("open_stock").put("verify_day_k").put("verify_timeline"),
                    ),
            )

        val contract = VisualTaskContract.fromJson(root)

        assertEquals("return_market", contract?.currentMilestoneId)
        assertEquals(4, contract?.milestones?.size)
        assertEquals(
            listOf("open_stock", "verify_day_k", "verify_timeline"),
            contract?.completedMilestoneIds,
        )
    }

    @Test
    fun rolledBackCompletionCandidateIsRemovedButLaterCandidateIsKept() {
        val actions = listOf(
            "tap_xy|分时|0.2|0.3:ok:target=分时",
            "finish_verification_pending:observationId=observation-1",
            "finish_permit_rejected:reason=not_confirmed",
            "back:ok:target=返回",
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
            step = CloudAgentStep(type = "back", targetText = "返回"),
            signature = "back|返回",
            result = AgentExecutionResult(ok = true, message = "已执行返回", shouldContinue = true),
        )

        assertTrue(summary.contains("executionAccepted=true"))
        assertTrue(summary.contains("gestureDispatched=true"))
        assertTrue(summary.contains("semanticOutcome=gui_plus_pending_judgement"))
        assertFalse(summary.contains("semanticOutcome=success"))
    }

    private fun syntheticCompletedGoalContract(): JSONObject = JSONObject()
        .put("originalGoal", "复杂任务")
        .put("currentMilestoneId", "goal")
        .put(
            "milestones",
            JSONArray().put(
                JSONObject()
                    .put("id", "goal")
                    .put("title", "所有步骤均已完成")
                    .put("completed", true),
            ),
        )
        .put("completedMilestoneIds", JSONArray().put("goal"))
}
