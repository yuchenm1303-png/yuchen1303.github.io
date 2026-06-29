package com.yuchen.ailedger.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileUseTerminateRepairTest {
    @Test
    fun terminateProtocolMisclassifiedAsHelpBecomesBoundCompletionCandidate() {
        val raw = "Action: 当前页面已满足任务要求。\n" +
            "{\"name\":\"mobile_use\",\"arguments\":{\"action\":\"terminate\"}}"
        val root = JSONObject()
            .put(
                "agentStep",
                JSONObject()
                    .put("type", "need_user_help")
                    .put("reason", "当前页面已满足任务要求。")
                    .put(
                        "args",
                        JSONObject()
                            .put("responseSessionId", SESSION_ID)
                            .put("responseObservationId", OBSERVATION_ID),
                    ),
            )
            .put(
                "debug",
                JSONObject().put(
                    "guiCompactAction",
                    JSONObject()
                        .put("a", "need_user_help")
                        .put("raw", raw),
                ),
            )

        val plan = CloudAgentPlan.fromJson(root)!!
        val step = plan.step

        assertEquals("finish", step.type)
        assertTrue(step.toolArgs!!.getBoolean("completionCandidate"))
        assertEquals(SESSION_ID, step.toolArgs!!.getString("completionCandidateSessionId"))
        assertEquals(OBSERVATION_ID, step.toolArgs!!.getString("completionCandidateObservationId"))
        assertEquals("terminate", step.toolArgs!!.getString("mobileUseOriginalAction"))
        assertTrue(plan.rawModelOutput.contains("\"action\":\"terminate\""))

        val candidate = VisualCompletionPermitPolicy.candidate(
            step = step,
            expectedSessionId = SESSION_ID,
            expectedObservationId = OBSERVATION_ID,
            candidateTaskRevision = 0,
        )
        assertTrue(candidate.valid)
    }

    @Test
    fun genuineUserHelpIsNotRepairedWithoutTerminateProtocol() {
        val raw = "Action: 请用户确认收货地址后继续。\n" +
            "{\"name\":\"mobile_use\",\"arguments\":{\"action\":\"interact\"}}"
        val root = JSONObject()
            .put(
                "agentStep",
                JSONObject()
                    .put("type", "need_user_help")
                    .put("reason", "请用户确认收货地址后继续。")
                    .put(
                        "args",
                        JSONObject()
                            .put("responseSessionId", SESSION_ID)
                            .put("responseObservationId", OBSERVATION_ID),
                    ),
            )
            .put(
                "debug",
                JSONObject().put(
                    "guiCompactAction",
                    JSONObject().put("raw", raw),
                ),
            )

        val plan = CloudAgentPlan.fromJson(root)!!

        assertEquals("need_user_help", plan.step.type)
        assertFalse(plan.step.toolArgs?.optBoolean("completionCandidate", false) ?: false)
    }

    companion object {
        private const val SESSION_ID = "agent-session-test"
        private const val OBSERVATION_ID = "observation-test"
    }
}
