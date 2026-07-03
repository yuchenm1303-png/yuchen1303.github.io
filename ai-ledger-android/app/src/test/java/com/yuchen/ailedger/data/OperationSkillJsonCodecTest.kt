package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.VisualSkillInput
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationSkillJsonCodecTest {
    @Test
    fun skillRoundTripPreservesSemanticContract() {
        val original = sampleSkill()

        val restored = OperationSkillJsonCodec.decode(OperationSkillJsonCodec.encode(original))

        assertEquals(original, restored)
        assertFalse(OperationSkillJsonCodec.encode(original).contains("ResourceId"))
        assertFalse(OperationSkillJsonCodec.encode(original).contains("RecordedBounds"))
    }

    @Test
    fun approvedSnapshotFreezesWorkflowAndFullSkillTogether() {
        val snapshot = JSONObject(
            OperationSkillJsonCodec.encodeApprovedSnapshot(
                draft = sampleDraft().copy(status = WorkflowDraftStatus.Approved),
                skill = sampleSkill(),
            ),
        )

        assertEquals("ai_ledger_visual_skill_approval_v1", snapshot.getString("schemaVersion"))
        assertEquals("CloudVisual", snapshot.getJSONObject("workflow").getString("executionMode"))
        val skill = snapshot.getJSONObject("skill")
        assertEquals("下载月度账单", skill.getString("name"))
        assertTrue(skill.getJSONArray("operatingPrinciples").length() > 0)
        assertTrue(skill.getJSONArray("successCriteria").length() > 0)
        assertTrue(skill.getJSONArray("safetyRules").length() > 0)
    }

    private fun sampleDraft() = LearnedWorkflowDraft(
        id = "workflow-1",
        title = "下载月度账单",
        goal = "下载用户指定月份的账单",
        appScope = WorkflowAppScope(packageNames = listOf("com.example.billing")),
        executionMode = WorkflowExecutionMode.CloudVisual,
        status = WorkflowDraftStatus.ReadyForReview,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        sourceDemonstrationId = "demo-1",
    )

    private fun sampleSkill() = LearnedVisualSkill(
        workflowId = "workflow-1",
        name = "下载月度账单",
        description = "在账单应用中下载指定月份的账单",
        triggerExamples = listOf("下载上个月账单"),
        inputs = listOf(
            VisualSkillInput(
                key = "month",
                label = "月份",
                description = "本次需要下载的账单月份",
            ),
        ),
        operatingPrinciples = listOf("进入账单页面后选择目标月份"),
        successCriteria = listOf("页面明确显示文件已经保存"),
        safetyRules = listOf("遇到身份验证时交给用户"),
        cloudSummary = "按月份选择并导出账单",
        confidence = 0.86f,
        learnedAtMillis = 3L,
    )
}
