package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.TargetSelectorBundle
import com.yuchen.ailedger.model.TargetSelectorCandidate
import com.yuchen.ailedger.model.WorkflowActionSpec
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowAppScope
import com.yuchen.ailedger.model.WorkflowConfirmationPolicy
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowMilestone
import com.yuchen.ailedger.model.WorkflowRiskLevel
import com.yuchen.ailedger.model.WorkflowSelectorKind
import com.yuchen.ailedger.model.WorkflowStateCheck
import com.yuchen.ailedger.model.WorkflowStateCheckType
import com.yuchen.ailedger.model.WorkflowStep
import com.yuchen.ailedger.model.WorkflowVariableDefinition
import com.yuchen.ailedger.model.WorkflowVariableType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationWorkflowValidatorTest {
    @Test
    fun recordingIntentAllowsMissingDemonstrationSteps() {
        val report = OperationWorkflowValidator.validate(
            draft = baseDraft(),
            stage = WorkflowValidationStage.RecordingIntent,
        )

        assertTrue(report.canProceed)
        assertTrue(report.issues.none { it.code == "workflow_steps_missing" })
    }

    @Test
    fun reviewRejectsCoordinateOnlyTarget() {
        val draft = executableDraft(
            target = TargetSelectorBundle(
                candidates = listOf(
                    TargetSelectorCandidate(
                        kind = WorkflowSelectorKind.RecordedBounds,
                        value = "0.22,0.41,0.58,0.49",
                        weight = 0.2f,
                        packageName = TEST_PACKAGE,
                    ),
                ),
                coordinateFallbackAllowed = true,
            ),
        )

        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Review)

        assertFalse(report.canProceed)
        assertTrue(report.blockingIssues.any { it.code == "workflow_selector_coordinate_only" })
    }

    @Test
    fun highRiskStepRequiresEveryRunConfirmation() {
        val draft = executableDraft(
            riskLevel = WorkflowRiskLevel.High,
            confirmationPolicy = WorkflowConfirmationPolicy.OnRisk,
        ).copy(
            riskPolicy = executableDraft().riskPolicy.copy(
                maximumAllowedRisk = WorkflowRiskLevel.High,
            ),
        )

        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Review)

        assertFalse(report.canProceed)
        assertTrue(report.blockingIssues.any { it.code == "workflow_high_risk_without_confirmation" })
    }

    @Test
    fun sensitiveVariableCannotPersistActualValue() {
        val draft = baseDraft().copy(
            variables = listOf(
                WorkflowVariableDefinition(
                    key = "account_secret",
                    label = "账号密码",
                    type = WorkflowVariableType.SecretReference,
                    sensitive = true,
                    persistValue = true,
                ),
            ),
        )

        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)

        assertFalse(report.canProceed)
        assertTrue(report.blockingIssues.any { it.code == "workflow_sensitive_variable_persisted" })
    }

    @Test
    fun approvedDeterministicWorkflowCanExecute() {
        val draft = executableDraft().copy(status = WorkflowDraftStatus.Approved)

        val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.Execution)

        assertTrue(report.blockingIssues.joinToString { it.code }, report.canProceed)
    }

    private fun baseDraft(): LearnedWorkflowDraft {
        return LearnedWorkflowDraft(
            id = "workflow-test",
            title = "发送固定格式消息",
            goal = "在测试应用中打开会话并发送由用户提供的文本",
            appScope = WorkflowAppScope(
                packageNames = listOf(TEST_PACKAGE),
                displayNames = listOf("测试应用"),
            ),
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
    }

    private fun executableDraft(
        target: TargetSelectorBundle = stableTarget(),
        riskLevel: WorkflowRiskLevel = WorkflowRiskLevel.Low,
        confirmationPolicy: WorkflowConfirmationPolicy = WorkflowConfirmationPolicy.OnRisk,
    ): LearnedWorkflowDraft {
        val afterSend = WorkflowStateCheck(
            id = "check-sent",
            type = WorkflowStateCheckType.TextVisible,
            expectedValue = "已发送",
            packageName = TEST_PACKAGE,
        )
        return baseDraft().copy(
            variables = listOf(
                WorkflowVariableDefinition(
                    key = "message",
                    label = "消息内容",
                    type = WorkflowVariableType.Text,
                ),
            ),
            milestones = listOf(
                WorkflowMilestone(
                    id = "milestone-send",
                    title = "完成发送",
                    order = 0,
                    completionChecks = listOf(afterSend),
                ),
            ),
            steps = listOf(
                WorkflowStep(
                    id = "step-send",
                    order = 0,
                    title = "点击发送",
                    milestoneId = "milestone-send",
                    action = WorkflowActionSpec(type = WorkflowActionType.Tap),
                    target = target,
                    postconditions = listOf(afterSend),
                    riskLevel = riskLevel,
                    confirmationPolicy = confirmationPolicy,
                ),
            ),
            completionChecks = listOf(afterSend),
        )
    }

    private fun stableTarget(): TargetSelectorBundle {
        return TargetSelectorBundle(
            candidates = listOf(
                TargetSelectorCandidate(
                    kind = WorkflowSelectorKind.ResourceId,
                    value = "com.example.testing:id/send",
                    weight = 1f,
                    packageName = TEST_PACKAGE,
                    role = "Button",
                ),
                TargetSelectorCandidate(
                    kind = WorkflowSelectorKind.TextAndRole,
                    value = "发送|Button",
                    weight = 0.72f,
                    packageName = TEST_PACKAGE,
                    role = "Button",
                ),
            ),
        )
    }

    private companion object {
        const val TEST_PACKAGE = "com.example.testing"
    }
}
