package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowConfirmationPolicy
import com.yuchen.ailedger.model.WorkflowExecutionMode
import com.yuchen.ailedger.model.WorkflowRiskLevel
import com.yuchen.ailedger.model.WorkflowSelectorKind
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

object OperationWorkflowValidator {
    fun validate(
        draft: LearnedWorkflowDraft,
        stage: WorkflowValidationStage,
    ): WorkflowValidationReport {
        val issues = buildList {
            validateIdentity(draft)
            validateScope(draft)
            validateVariables(draft)
            validateRiskAndRecovery(draft)

            if (stage != WorkflowValidationStage.RecordingIntent) {
                validateStructure(draft)
                validateSteps(draft, stage)
                validateCompletion(draft)
            }

            if (stage == WorkflowValidationStage.Execution) {
                validateExecutionReadiness(draft)
            }
        }
        return WorkflowValidationReport(stage = stage, issues = issues)
    }

    private fun MutableList<WorkflowValidationIssue>.validateIdentity(draft: LearnedWorkflowDraft) {
        if (draft.title.trim().isBlank()) {
            blocking("workflow_title_missing", "请输入操作名称。")
        }
        if (draft.goal.trim().isBlank()) {
            blocking("workflow_goal_missing", "请说明这个操作最终要完成什么。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateScope(draft: LearnedWorkflowDraft) {
        if (draft.appScope.normalizedPackages.isEmpty()) {
            blocking("workflow_app_scope_missing", "至少指定一个允许操作的应用包名。")
        }
        if (draft.appScope.normalizedPackages.size > 4) {
            warning("workflow_app_scope_wide", "当前流程跨越较多应用，建议拆成更小、更稳定的操作。")
        }
        if (draft.appScope.allowSystemSurfaces) {
            warning("workflow_system_surface_enabled", "流程允许进入系统界面，运行时需要更严格的确认。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateVariables(draft: LearnedWorkflowDraft) {
        val normalizedKeys = draft.variables.map { it.key.trim() }
        normalizedKeys.filter(String::isBlank).forEach {
            blocking("workflow_variable_key_missing", "变量必须有稳定的内部标识。")
        }
        normalizedKeys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { key ->
            blocking("workflow_variable_key_duplicate", "变量标识“$key”重复。")
        }

        draft.variables.forEach { variable ->
            if (variable.label.trim().isBlank()) {
                blocking("workflow_variable_label_missing", "变量“${variable.key}”缺少显示名称。")
            }
            if (variable.sensitive && variable.persistValue) {
                blocking("workflow_sensitive_variable_persisted", "敏感变量“${variable.label}”不能保存实际值。")
            }
            if (variable.type == WorkflowVariableType.SecretReference && !variable.sensitive) {
                blocking("workflow_secret_reference_not_sensitive", "敏感信息引用必须标记为敏感变量。")
            }
            if (variable.type == WorkflowVariableType.Choice && variable.allowedValues.isEmpty()) {
                warning("workflow_choice_values_empty", "选项变量“${variable.label}”还没有候选值。")
            }
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateRiskAndRecovery(draft: LearnedWorkflowDraft) {
        if (draft.riskPolicy.maximumAllowedRisk == WorkflowRiskLevel.Prohibited) {
            blocking("workflow_prohibited_risk_allowed", "禁止把最高风险级别设置为可执行。")
        }
        if (draft.recoveryPolicy.maximumAutomaticRetries !in 0..2) {
            blocking("workflow_retry_out_of_range", "自动重试次数必须限制在 0 到 2 次。")
        }
        if (draft.recoveryPolicy.allowRouteMutation && draft.executionMode == WorkflowExecutionMode.Deterministic) {
            blocking("workflow_route_mutation_in_deterministic_mode", "确定性执行模式不能自动改写操作路线。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateStructure(draft: LearnedWorkflowDraft) {
        if (draft.milestones.isEmpty()) {
            blocking("workflow_milestones_missing", "流程至少需要一个里程碑。")
        }
        if (draft.steps.isEmpty()) {
            blocking("workflow_steps_missing", "演示尚未整理出可审核步骤。")
        }

        val milestoneIds = draft.milestones.map { it.id.trim() }
        milestoneIds.filter(String::isBlank).forEach {
            blocking("workflow_milestone_id_missing", "里程碑必须有稳定标识。")
        }
        milestoneIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { id ->
            blocking("workflow_milestone_id_duplicate", "里程碑标识“$id”重复。")
        }

        val stepIds = draft.steps.map { it.id.trim() }
        stepIds.filter(String::isBlank).forEach {
            blocking("workflow_step_id_missing", "步骤必须有稳定标识。")
        }
        stepIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { id ->
            blocking("workflow_step_id_duplicate", "步骤标识“$id”重复。")
        }

        val stepOrders = draft.steps.map { it.order }
        if (stepOrders.any { it < 0 }) {
            blocking("workflow_step_order_negative", "步骤顺序不能为负数。")
        }
        if (stepOrders.distinct().size != stepOrders.size) {
            blocking("workflow_step_order_duplicate", "步骤顺序不能重复。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateSteps(
        draft: LearnedWorkflowDraft,
        stage: WorkflowValidationStage,
    ) {
        val milestoneIds = draft.milestones.map { it.id }.toSet()
        val variableKeys = draft.variables.map { it.key }.toSet()
        val appPackages = draft.appScope.normalizedPackages.toSet()

        draft.steps.forEach { step ->
            if (step.title.trim().isBlank()) {
                blocking("workflow_step_title_missing", "步骤缺少名称。", step.id)
            }
            if (step.milestoneId !in milestoneIds) {
                blocking("workflow_step_milestone_missing", "步骤“${step.title}”没有对应的里程碑。", step.id)
            }
            if (step.retryPolicy.maxAttempts !in 1..3) {
                blocking("workflow_step_retry_out_of_range", "步骤“${step.title}”最多只能尝试 1 到 3 次。", step.id)
            }

            val requiresTarget = step.action.type in setOf(
                WorkflowActionType.Tap,
                WorkflowActionType.LongPress,
                WorkflowActionType.InputText,
                WorkflowActionType.ClearText,
                WorkflowActionType.Scroll,
                WorkflowActionType.Swipe,
            )
            if (requiresTarget && step.target == null) {
                blocking("workflow_step_target_missing", "步骤“${step.title}”缺少目标定位信息。", step.id)
            }

            step.target?.let { target ->
                if (target.minimumScore !in 0.5f..1f) {
                    blocking("workflow_selector_threshold_invalid", "步骤“${step.title}”的匹配阈值无效。", step.id)
                }
                if (target.candidates.isEmpty()) {
                    blocking("workflow_selector_empty", "步骤“${step.title}”没有任何候选选择器。", step.id)
                }
                if (!target.hasStableCandidate) {
                    blocking("workflow_selector_coordinate_only", "步骤“${step.title}”不能只依赖录制坐标。", step.id)
                }
                if (target.coordinateFallbackAllowed && stage == WorkflowValidationStage.Execution) {
                    warning("workflow_coordinate_fallback_enabled", "步骤“${step.title}”启用了坐标兜底，只能在页面指纹高度一致时使用。", step.id)
                }
                target.candidates.forEach { candidate ->
                    if (candidate.value.trim().isBlank()) {
                        blocking("workflow_selector_value_missing", "步骤“${step.title}”包含空选择器。", step.id)
                    }
                    if (candidate.weight !in 0f..1f) {
                        blocking("workflow_selector_weight_invalid", "步骤“${step.title}”的选择器权重必须在 0 到 1 之间。", step.id)
                    }
                    candidate.packageName?.takeIf(String::isNotBlank)?.let { packageName ->
                        if (packageName !in appPackages) {
                            blocking("workflow_selector_outside_scope", "步骤“${step.title}”定位到了未授权应用。", step.id)
                        }
                    }
                    if (candidate.kind == WorkflowSelectorKind.RecordedBounds && candidate.weight > 0.25f) {
                        warning("workflow_coordinate_weight_high", "步骤“${step.title}”的坐标线索权重过高。", step.id)
                    }
                }
            }

            if (step.action.type == WorkflowActionType.InputText) {
                val variableKey = step.action.variableKey
                if (variableKey.isNullOrBlank()) {
                    blocking("workflow_input_variable_missing", "输入步骤“${step.title}”必须绑定变量。", step.id)
                } else if (variableKey !in variableKeys) {
                    blocking("workflow_input_variable_unknown", "输入步骤“${step.title}”绑定了不存在的变量。", step.id)
                }
                if (!step.action.fixedArgument.isNullOrBlank()) {
                    warning("workflow_input_fixed_value", "输入步骤“${step.title}”包含固定文本，需确认其中没有敏感信息。", step.id)
                }
            }

            if (step.action.type !in setOf(
                    WorkflowActionType.WaitForState,
                    WorkflowActionType.RequestUserConfirmation,
                ) && step.postconditions.isEmpty()
            ) {
                blocking("workflow_step_postcondition_missing", "步骤“${step.title}”必须定义执行后的验证条件。", step.id)
            }

            if (step.riskLevel == WorkflowRiskLevel.Prohibited) {
                blocking("workflow_step_prohibited", "步骤“${step.title}”属于禁止执行操作。", step.id)
            }
            if (step.riskLevel == WorkflowRiskLevel.High && step.confirmationPolicy != WorkflowConfirmationPolicy.Always) {
                blocking("workflow_high_risk_without_confirmation", "高风险步骤“${step.title}”必须每次确认。", step.id)
            }
            if (step.riskLevel.ordinal > draft.riskPolicy.maximumAllowedRisk.ordinal) {
                blocking("workflow_step_exceeds_risk_policy", "步骤“${step.title}”超出流程允许的风险级别。", step.id)
            }
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateCompletion(draft: LearnedWorkflowDraft) {
        if (draft.completionChecks.isEmpty()) {
            blocking("workflow_completion_checks_missing", "流程必须定义最终成功证据。")
        }
        if (draft.completionChecks.none { it.required }) {
            blocking("workflow_required_completion_check_missing", "至少需要一个必需的成功证据。")
        }
    }

    private fun MutableList<WorkflowValidationIssue>.validateExecutionReadiness(draft: LearnedWorkflowDraft) {
        if (draft.executionMode != WorkflowExecutionMode.Deterministic &&
            draft.recoveryPolicy.mode.name != "AssistedRepairAfterConsent"
        ) {
            blocking("workflow_assisted_mode_without_consent_gate", "受控辅助修复必须经过用户同意后才能启用。")
        }
        if (draft.status.name !in setOf("Approved", "Verified")) {
            blocking("workflow_not_approved", "流程尚未通过用户审核，不能执行。")
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
