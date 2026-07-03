package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.model.WorkflowRecoveryMode
import com.yuchen.ailedger.model.WorkflowRiskLevel
import com.yuchen.ailedger.model.WorkflowVariableType

enum class WorkflowValidationStage {
    RecordingIntent,
    Review,
    Execution,
}

enum class WorkflowValidationSeverity {
    Blocking,
    Warning,
}

data class WorkflowValidationIssue(
    val code: String,
    val message: String,
    val severity: WorkflowValidationSeverity,
    val stepId: String? = null,
)

data class WorkflowValidationReport(
    val stage: WorkflowValidationStage,
    val issues: List<WorkflowValidationIssue>,
) {
    val blockingIssues: List<WorkflowValidationIssue>
        get() = issues.filter { it.severity == WorkflowValidationSeverity.Blocking }

    val warnings: List<WorkflowValidationIssue>
        get() = issues.filter { it.severity == WorkflowValidationSeverity.Warning }

    val canProceed: Boolean
        get() = blockingIssues.isEmpty()
}

/**
 * 本地校验器只守住身份、授权范围、变量隐私和执行批准等硬边界。
 * Skill 的方法、步骤、页面理解、成功判断和恢复策略由云端视觉智能负责，
 * 不再在 Android 端复制一套选择器和流程决策树。
 */
object OperationWorkflowValidator {
    private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun validate(
        draft: LearnedWorkflowDraft,
        stage: WorkflowValidationStage,
    ): WorkflowValidationReport {
        val issues = buildList {
            validateIdentity(draft)
            validateScope(draft)
            validateVariables(draft)
            validateSafetyPolicy(draft)

            if (draft.executionMode != WorkflowExecutionMode.CloudVisual &&
                stage != WorkflowValidationStage.RecordingIntent
            ) {
                validateLegacyStructure(draft)
            }
            if (stage == WorkflowValidationStage.Review) {
                validateReviewReadiness(draft)
            }
            if (stage == WorkflowValidationStage.Execution) {
                validateExecutionReadiness(draft)
            }
        }
        return WorkflowValidationReport(stage = stage, issues = issues)
    }

    private fun MutableList<WorkflowValidationIssue>.validateIdentity(draft: LearnedWorkflowDraft) {
        if (draft.title.trim().isBlank()) {
            blocking("workflow_title_missing", "请输入 Skill 名称。")
        }
        if (draft.goal.trim().isBlank()) {
            blocking("workflow_goal_missing", "请说明这个 Skill 最终要完成什么。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateScope(draft: LearnedWorkflowDraft) {
        val packages = draft.appScope.normalizedPackages
        if (packages.isEmpty()) {
            blocking("workflow_app_scope_missing", "至少指定一个允许观察和操作的应用包名。")
            return
        }
        packages.forEach { packageName ->
            if (!packageNamePattern.matches(packageName)) {
                blocking("workflow_app_package_invalid", "应用包名“$packageName”格式无效。")
            }
        }
        if (packages.size > 4) {
            warning("workflow_app_scope_wide", "当前 Skill 跨越较多应用，请确认这些应用都确实必要。")
        }
        if (draft.appScope.allowSystemSurfaces) {
            warning("workflow_system_surface_enabled", "Skill 允许进入系统界面，运行时需要更严格的本地确认。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateVariables(draft: LearnedWorkflowDraft) {
        val normalizedKeys = draft.variables.map { it.key.trim() }
        normalizedKeys.filter(String::isBlank).forEach {
            blocking("workflow_variable_key_missing", "Skill 输入必须有稳定标识。")
        }
        normalizedKeys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { key ->
            blocking("workflow_variable_key_duplicate", "Skill 输入标识“$key”重复。")
        }
        draft.variables.forEach { variable ->
            if (variable.label.trim().isBlank()) {
                blocking("workflow_variable_label_missing", "输入“${variable.key}”缺少显示名称。")
            }
            if (variable.sensitive && variable.persistValue) {
                blocking("workflow_sensitive_variable_persisted", "敏感输入“${variable.label}”不能保存实际值。")
            }
            if (variable.type == WorkflowVariableType.SecretReference && !variable.sensitive) {
                blocking("workflow_secret_reference_not_sensitive", "敏感信息引用必须标记为敏感输入。")
            }
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateSafetyPolicy(draft: LearnedWorkflowDraft) {
        if (draft.riskPolicy.maximumAllowedRisk == WorkflowRiskLevel.Prohibited) {
            blocking("workflow_prohibited_risk_allowed", "禁止把最高风险级别设置为可执行。")
        }
        if (draft.recoveryPolicy.maximumAutomaticRetries !in 0..2) {
            blocking("workflow_retry_out_of_range", "本地自动重试次数必须限制在 0 到 2 次。")
        }
        if (draft.executionMode == WorkflowExecutionMode.AssistedRepair &&
            draft.recoveryPolicy.mode != WorkflowRecoveryMode.AssistedRepairAfterConsent
        ) {
            blocking("workflow_assisted_mode_without_consent_gate", "历史受控修复必须经过用户同意后才能启用。")
        }
        if (!draft.riskPolicy.blockPasswordCapture ||
            !draft.riskPolicy.blockOtpCapture ||
            !draft.riskPolicy.blockPaymentConfirmation
        ) {
            blocking("workflow_sensitive_guard_disabled", "密码、验证码和支付确认保护不能关闭。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateLegacyStructure(draft: LearnedWorkflowDraft) {
        if (draft.milestones.isEmpty() || draft.steps.isEmpty()) {
            blocking("workflow_legacy_structure_missing", "历史流程缺少可执行结构。")
        }
        if (draft.completionChecks.none { it.required }) {
            blocking("workflow_legacy_completion_missing", "历史流程缺少最终完成校验。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateReviewReadiness(draft: LearnedWorkflowDraft) {
        if (draft.status !in setOf(
                WorkflowDraftStatus.ReadyForReview,
                WorkflowDraftStatus.Approved,
                WorkflowDraftStatus.Verified,
            )
        ) {
            blocking("workflow_not_ready_for_review", "云端尚未生成可审核的 Skill 草稿。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateExecutionReadiness(draft: LearnedWorkflowDraft) {
        if (draft.status !in setOf(WorkflowDraftStatus.Approved, WorkflowDraftStatus.Verified)) {
            blocking("workflow_not_approved", "Skill 尚未通过用户审核，不能执行。")
        }
        if (draft.executionMode != WorkflowExecutionMode.CloudVisual) {
            warning("workflow_legacy_execution", "这是历史流程格式，不属于新的云端视觉 Skill 主链。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.blocking(
        code: String,
        message: String,
        stepId: String? = null,
    ) {
        add(
            WorkflowValidationIssue(
                code = code,
                message = message,
                severity = WorkflowValidationSeverity.Blocking,
                stepId = stepId,
            ),
        )
    }

    private fun MutableList<WorkflowValidationIssue>.warning(
        code: String,
        message: String,
        stepId: String? = null,
    ) {
        add(
            WorkflowValidationIssue(
                code = code,
                message = message,
                severity = WorkflowValidationSeverity.Warning,
                stepId = stepId,
            ),
        )
    }
}
