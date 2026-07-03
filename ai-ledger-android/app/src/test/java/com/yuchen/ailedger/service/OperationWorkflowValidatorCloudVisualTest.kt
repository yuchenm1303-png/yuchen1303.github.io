package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.model.WorkflowRiskPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationWorkflowValidatorCloudVisualTest {
    @Test
    fun cloudVisualReviewDoesNotRequireLegacyStepGraph() {
        val report = OperationWorkflowValidator.validate(
            draft = validDraft(WorkflowDraftStatus.ReadyForReview),
            stage = WorkflowValidationStage.Review,
        )

        assertTrue(report.canProceed)
        assertTrue(report.issues.none { it.code == "workflow_legacy_structure_missing" })
    }

    @Test
    fun executionStillRequiresExplicitUserApproval() {
        val report = OperationWorkflowValidator.validate(
            draft = validDraft(WorkflowDraftStatus.ReadyForReview),
            stage = WorkflowValidationStage.Execution,
        )

        assertFalse(report.canProceed)
        assertTrue(report.blockingIssues.any { it.code == "workflow_not_approved" })
    }

    @Test
    fun sensitiveGuardsCannotBeDisabled() {
        val report = OperationWorkflowValidator.validate(
            draft = validDraft(WorkflowDraftStatus.Approved).copy(
                riskPolicy = WorkflowRiskPolicy(blockPasswordCapture = false),
            ),
            stage = WorkflowValidationStage.Execution,
        )

        assertFalse(report.canProceed)
        assertTrue(report.blockingIssues.any { it.code == "workflow_sensitive_guard_disabled" })
    }

    private fun validDraft(status: WorkflowDraftStatus) = LearnedWorkflowDraft(
        id = "workflow-1",
        title = "测试 Skill",
        goal = "完成一个视觉任务",
        appScope = WorkflowAppScope(packageNames = listOf("com.example.app")),
        executionMode = WorkflowExecutionMode.CloudVisual,
        status = status,
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        sourceDemonstrationId = "demo-1",
    )
}
