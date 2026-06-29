package com.yuchen.ailedger.service

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VisualTaskContractReuseTest {
    @Test
    fun unifiedPermitCanAuthorizeFirstWorkSurfaceActionWithoutRepeatedContract() {
        val parsed = VisualTaskContract.fromJson(
            root = workSurfaceActionRoot(
                milestoneId = "",
                includeUnifiedPermit = true,
            ),
            committedContract = null,
        )

        assertNull(parsed)
    }

    @Test
    fun laterLegacyWorkSurfaceActionReusesCommittedContract() {
        val parsed = VisualTaskContract.fromJson(
            root = workSurfaceActionRoot(milestoneId = "m1"),
            committedContract = committedContract(),
        )

        assertNull(parsed)
    }

    @Test
    fun firstLegacyWorkSurfaceActionStillRequiresInitialContract() {
        val error = captureProtocolFailure {
            VisualTaskContract.fromJson(
                root = workSurfaceActionRoot(milestoneId = "m1"),
                committedContract = null,
            )
        }

        assertNotNull(error)
        assertEquals("visual_protocol_task_contract_required", error!!.code)
    }

    @Test
    fun reusedLegacyContractStillRejectsWrongMilestoneBinding() {
        val error = captureProtocolFailure {
            VisualTaskContract.fromJson(
                root = workSurfaceActionRoot(milestoneId = "m2"),
                committedContract = committedContract(),
            )
        }

        assertNotNull(error)
        assertEquals("visual_protocol_action_milestone_mismatch", error!!.code)
    }

    private fun captureProtocolFailure(block: () -> Unit): VisualAgentRequestException? {
        return try {
            block()
            null
        } catch (error: VisualAgentRequestException) {
            error
        }
    }

    private fun workSurfaceActionRoot(
        milestoneId: String,
        includeUnifiedPermit: Boolean = false,
    ): JSONObject = JSONObject().apply {
        put("surfaceState", "work_surface")
        put("agentStep", JSONObject().apply {
            put("type", "tap_xy")
            put("purpose", "进入行情页面")
            if (milestoneId.isNotBlank()) put("milestoneId", milestoneId)
            put("expectedEvidence", JSONArray().put("行情页面可见"))
            put("args", JSONObject().apply {
                put("responseSessionId", "visual-session-test")
                put("responseObservationId", "observation-test")
                if (includeUnifiedPermit) {
                    put("executionPermitVersion", "visual_execution_permit_v2")
                    put("executionPermitId", "permit_test")
                    put("executionPermitKind", "independent_gui_visual_grounding")
                }
            })
        })
    }

    private fun committedContract() = VisualTaskContract(
        originalGoal = "查看贵州茅台行情",
        currentMilestoneId = "m1",
        milestones = listOf(
            VisualTaskMilestone(
                id = "m1",
                title = "进入行情页面",
                purpose = "打开行情工作面",
                successEvidence = listOf("行情页面可见"),
            ),
            VisualTaskMilestone(
                id = "m2",
                title = "独立确认结果",
                purpose = "确认目标股票页面与行情状态",
                successEvidence = listOf("贵州茅台页面和目标行情状态可见"),
            ),
        ),
        taskRevision = 1,
    )
}
