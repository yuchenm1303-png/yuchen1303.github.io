package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.TargetSelectorBundle
import com.yuchen.ailedger.model.TargetSelectorCandidate
import com.yuchen.ailedger.model.WorkflowActionSpec
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowConfirmationPolicy
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowMilestone
import com.yuchen.ailedger.model.WorkflowRiskLevel
import com.yuchen.ailedger.model.WorkflowRiskPolicy
import com.yuchen.ailedger.model.WorkflowSelectorKind
import com.yuchen.ailedger.model.WorkflowStateCheck
import com.yuchen.ailedger.model.WorkflowStateCheckType
import com.yuchen.ailedger.model.WorkflowStep
import com.yuchen.ailedger.model.WorkflowVariableDefinition
import com.yuchen.ailedger.model.WorkflowVariableType
import kotlin.math.abs

enum class WorkflowCompilationSeverity {
    Blocking,
    Warning,
}

data class WorkflowCompilationIssue(
    val code: String,
    val message: String,
    val severity: WorkflowCompilationSeverity,
    val stepId: String? = null,
)

data class WorkflowCompilationResult(
    val draft: LearnedWorkflowDraft?,
    val issues: List<WorkflowCompilationIssue>,
    val rawRecordCount: Int,
    val normalizedActionCount: Int,
) {
    val succeeded: Boolean
        get() = draft != null && issues.none { it.severity == WorkflowCompilationSeverity.Blocking }
}

object OperationWorkflowCompiler {
    private data class CompiledAction(
        val event: OperationAccessibilityEventRecord,
        val before: OperationNodeSnapshotRecord?,
        val after: OperationNodeSnapshotRecord?,
    )

    private data class StepBuildResult(
        val step: WorkflowStep,
        val variable: WorkflowVariableDefinition? = null,
        val issue: WorkflowCompilationIssue? = null,
    )

    fun compile(
        baseDraft: LearnedWorkflowDraft,
        records: List<OperationTraceRecord>,
        nowMillis: Long = System.currentTimeMillis(),
    ): WorkflowCompilationResult {
        val ordered = records.sortedBy(OperationTraceRecord::capturedAtMillis)
        val snapshots = ordered.filterIsInstance<OperationNodeSnapshotRecord>()
            .filter { it.packageName in baseDraft.appScope.normalizedPackages }
        val normalizedEvents = normalizeEvents(
            ordered.filterIsInstance<OperationAccessibilityEventRecord>()
                .filter { it.packageName in baseDraft.appScope.normalizedPackages },
        )
        if (normalizedEvents.isEmpty()) {
            return WorkflowCompilationResult(
                draft = null,
                issues = listOf(
                    WorkflowCompilationIssue(
                        code = "compilation_no_actionable_events",
                        message = "演示中没有可整理的点击、输入、长按或滚动操作。",
                        severity = WorkflowCompilationSeverity.Blocking,
                    ),
                ),
                rawRecordCount = records.size,
                normalizedActionCount = 0,
            )
        }

        val actions = normalizedEvents.map { event ->
            CompiledAction(
                event = event,
                before = snapshots.lastOrNull {
                    it.packageName == event.packageName && it.capturedAtMillis < event.capturedAtMillis
                },
                after = snapshots.firstOrNull {
                    it.packageName == event.packageName &&
                        it.capturedAtMillis >= event.capturedAtMillis &&
                        it.capturedAtMillis - event.capturedAtMillis <= SNAPSHOT_MATCH_WINDOW_MS
                },
            )
        }

        val issues = mutableListOf<WorkflowCompilationIssue>()
        val variables = linkedMapOf<String, WorkflowVariableDefinition>()
        val milestones = mutableListOf<WorkflowMilestone>()
        val steps = mutableListOf<WorkflowStep>()
        var stepOrder = 0
        var segmentOrder = 0
        var actionIndex = 0
        var maximumRisk = WorkflowRiskLevel.Medium

        while (actionIndex < actions.size) {
            val packageName = actions[actionIndex].event.packageName
            val segment = mutableListOf<CompiledAction>()
            while (actionIndex < actions.size && actions[actionIndex].event.packageName == packageName) {
                segment += actions[actionIndex]
                actionIndex += 1
            }

            val milestoneId = "${baseDraft.id}-milestone-$segmentOrder"
            val packageCheck = packageVisibleCheck(
                workflowId = baseDraft.id,
                ownerSuffix = "open-$segmentOrder",
                packageName = packageName,
            )
            steps += WorkflowStep(
                id = "${baseDraft.id}-step-$stepOrder",
                order = stepOrder,
                title = "打开 ${displayNameForPackage(baseDraft, packageName)}",
                milestoneId = milestoneId,
                action = WorkflowActionSpec(
                    type = WorkflowActionType.OpenApp,
                    fixedArgument = packageName,
                ),
                postconditions = listOf(packageCheck),
                confirmationPolicy = WorkflowConfirmationPolicy.Never,
            )
            stepOrder += 1

            segment.forEachIndexed { indexInSegment, action ->
                val result = buildStep(
                    baseDraft = baseDraft,
                    milestoneId = milestoneId,
                    order = stepOrder,
                    action = action,
                    variableIndex = variables.size + 1,
                )
                steps += result.step
                result.variable?.let { variables.putIfAbsent(it.key, it) }
                result.issue?.let(issues::add)
                if (result.step.riskLevel.ordinal > maximumRisk.ordinal) {
                    maximumRisk = result.step.riskLevel
                }
                stepOrder += 1

                if (indexInSegment > 0) {
                    val previous = segment[indexInSegment - 1].event
                    if (action.event.capturedAtMillis - previous.capturedAtMillis > LONG_IDLE_GAP_MS) {
                        issues += WorkflowCompilationIssue(
                            code = "compilation_long_idle_gap",
                            message = "步骤之间存在较长停顿，请在审核时确认是否需要等待条件。",
                            severity = WorkflowCompilationSeverity.Warning,
                            stepId = result.step.id,
                        )
                    }
                }
            }

            val lastSegmentStep = steps.last()
            val milestoneChecks = lastSegmentStep.postconditions.takeIf { it.isNotEmpty() }
                ?: listOf(
                    WorkflowStateCheck(
                        id = "${baseDraft.id}-milestone-$segmentOrder-user-check",
                        type = WorkflowStateCheckType.UserConfirmed,
                        expectedValue = "用户确认已完成本阶段",
                        packageName = packageName,
                    ),
                )
            milestones += WorkflowMilestone(
                id = milestoneId,
                title = "在 ${displayNameForPackage(baseDraft, packageName)} 完成第 ${segmentOrder + 1} 阶段",
                order = segmentOrder,
                completionChecks = milestoneChecks,
            )
            segmentOrder += 1
        }

        val completionChecks = deriveCompletionChecks(baseDraft.id, steps, issues)
        val compiled = baseDraft.copy(
            variables = variables.values.toList(),
            milestones = milestones,
            steps = steps,
            completionChecks = completionChecks,
            riskPolicy = WorkflowRiskPolicy(
                maximumAllowedRisk = if (maximumRisk == WorkflowRiskLevel.Prohibited) {
                    WorkflowRiskLevel.High
                } else {
                    maximumRisk
                },
                requireConfirmationForHighRisk = true,
                blockPasswordCapture = true,
                blockOtpCapture = true,
                blockPaymentConfirmation = true,
            ),
            status = WorkflowDraftStatus.ReadyForReview,
            updatedAtMillis = nowMillis,
        )

        val validation = OperationWorkflowValidator.validate(compiled, WorkflowValidationStage.Review)
        issues += validation.issues.map { issue ->
            WorkflowCompilationIssue(
                code = issue.code,
                message = issue.message,
                severity = when (issue.severity) {
                    WorkflowValidationSeverity.Blocking -> WorkflowCompilationSeverity.Blocking
                    WorkflowValidationSeverity.Warning -> WorkflowCompilationSeverity.Warning
                },
                stepId = issue.stepId,
            )
        }

        return WorkflowCompilationResult(
            draft = compiled,
            issues = issues.distinctBy { listOf(it.code, it.message, it.stepId).joinToString("|") },
            rawRecordCount = records.size,
            normalizedActionCount = normalizedEvents.size,
        )
    }

    private fun buildStep(
        baseDraft: LearnedWorkflowDraft,
        milestoneId: String,
        order: Int,
        action: CompiledAction,
        variableIndex: Int,
    ): StepBuildResult {
        val event = action.event
        val source = event.source
        val stepId = "${baseDraft.id}-step-$order"
        val preconditions = listOf(
            packageVisibleCheck(baseDraft.id, "pre-$order", event.packageName),
        )
        val paymentRisk = source?.riskHints?.contains("payment") == true
        val sensitiveInputRisk = source?.sensitive == true ||
            source?.riskHints?.any { it == "password" || it == "otp" } == true

        if (sensitiveInputRisk || paymentRisk) {
            val reason = if (sensitiveInputRisk) "敏感输入" else "支付相关操作"
            return StepBuildResult(
                step = WorkflowStep(
                    id = stepId,
                    order = order,
                    title = "由用户完成$reason",
                    milestoneId = milestoneId,
                    action = WorkflowActionSpec(WorkflowActionType.RequestUserConfirmation),
                    preconditions = preconditions,
                    riskLevel = WorkflowRiskLevel.High,
                    confirmationPolicy = WorkflowConfirmationPolicy.Always,
                ),
                issue = WorkflowCompilationIssue(
                    code = if (sensitiveInputRisk) {
                        "compilation_sensitive_action_manual"
                    } else {
                        "compilation_payment_action_manual"
                    },
                    message = "$reason不会被编译为自动执行步骤，运行时必须由用户亲自完成。",
                    severity = WorkflowCompilationSeverity.Warning,
                    stepId = stepId,
                ),
            )
        }

        val actionType = when (event.eventTypeLabel) {
            "view_clicked" -> WorkflowActionType.Tap
            "view_long_clicked" -> WorkflowActionType.LongPress
            "view_text_changed" -> WorkflowActionType.InputText
            "view_scrolled" -> WorkflowActionType.Scroll
            else -> WorkflowActionType.RequestUserConfirmation
        }
        val selector = buildSelectorBundle(source, event.packageName)
        val requiresSelector = actionType in setOf(
            WorkflowActionType.Tap,
            WorkflowActionType.LongPress,
            WorkflowActionType.InputText,
            WorkflowActionType.Scroll,
        )
        if (requiresSelector && selector == null) {
            return StepBuildResult(
                step = WorkflowStep(
                    id = stepId,
                    order = order,
                    title = "由用户完成无法稳定定位的操作",
                    milestoneId = milestoneId,
                    action = WorkflowActionSpec(WorkflowActionType.RequestUserConfirmation),
                    preconditions = preconditions,
                    riskLevel = WorkflowRiskLevel.Medium,
                    confirmationPolicy = WorkflowConfirmationPolicy.Always,
                ),
                issue = WorkflowCompilationIssue(
                    code = "compilation_selector_insufficient",
                    message = "该操作缺少足够稳定的控件证据，已转换为人工确认步骤，没有猜测坐标。",
                    severity = WorkflowCompilationSeverity.Warning,
                    stepId = stepId,
                ),
            )
        }

        if (actionType == WorkflowActionType.Scroll && inferScrollDirection(event) == null) {
            return StepBuildResult(
                step = WorkflowStep(
                    id = stepId,
                    order = order,
                    title = "由用户完成方向不明确的滚动",
                    milestoneId = milestoneId,
                    action = WorkflowActionSpec(WorkflowActionType.RequestUserConfirmation),
                    preconditions = preconditions,
                    riskLevel = WorkflowRiskLevel.Medium,
                    confirmationPolicy = WorkflowConfirmationPolicy.Always,
                ),
                issue = WorkflowCompilationIssue(
                    code = "compilation_scroll_direction_unknown",
                    message = "无法从事件中可靠判断滚动方向，已转换为人工确认步骤。",
                    severity = WorkflowCompilationSeverity.Warning,
                    stepId = stepId,
                ),
            )
        }

        val variable = if (actionType == WorkflowActionType.InputText) {
            val key = "input_$variableIndex"
            WorkflowVariableDefinition(
                key = key,
                label = variableLabel(source, variableIndex),
                type = WorkflowVariableType.Text,
                required = true,
                sensitive = false,
                persistValue = false,
                description = "每次运行前由用户提供，演示中的真实输入未被保存。",
            )
        } else {
            null
        }
        val postconditions = derivePostconditions(
            workflowId = baseDraft.id,
            stepOrder = order,
            packageName = event.packageName,
            before = action.before,
            after = action.after,
            target = source,
        )
        val title = stepTitle(actionType, source)
        val risk = when (actionType) {
            WorkflowActionType.LongPress -> WorkflowRiskLevel.Medium
            else -> WorkflowRiskLevel.Low
        }
        return StepBuildResult(
            step = WorkflowStep(
                id = stepId,
                order = order,
                title = title,
                milestoneId = milestoneId,
                action = WorkflowActionSpec(
                    type = actionType,
                    variableKey = variable?.key,
                    fixedArgument = if (actionType == WorkflowActionType.Scroll) inferScrollDirection(event) else null,
                ),
                target = selector,
                preconditions = preconditions,
                postconditions = postconditions,
                riskLevel = risk,
                confirmationPolicy = if (risk == WorkflowRiskLevel.Medium) {
                    WorkflowConfirmationPolicy.OnRisk
                } else {
                    WorkflowConfirmationPolicy.Never
                },
            ),
            variable = variable,
        )
    }

    private fun normalizeEvents(
        events: List<OperationAccessibilityEventRecord>,
    ): List<OperationAccessibilityEventRecord> {
        val actionable = events
            .filter { it.eventTypeLabel in ACTIONABLE_EVENT_TYPES }
            .sortedBy(OperationAccessibilityEventRecord::capturedAtMillis)
        val result = mutableListOf<OperationAccessibilityEventRecord>()
        actionable.forEach { event ->
            val previous = result.lastOrNull()
            val sameTarget = previous != null &&
                previous.packageName == event.packageName &&
                targetKey(previous.source) == targetKey(event.source) &&
                previous.eventTypeLabel == event.eventTypeLabel
            val delta = if (previous == null) Long.MAX_VALUE else event.capturedAtMillis - previous.capturedAtMillis
            when {
                sameTarget && event.eventTypeLabel == "view_text_changed" && delta <= TEXT_MERGE_WINDOW_MS -> {
                    result[result.lastIndex] = event
                }
                sameTarget && event.eventTypeLabel == "view_scrolled" && delta <= SCROLL_MERGE_WINDOW_MS -> {
                    result[result.lastIndex] = mergeScrollEvents(previous!!, event)
                }
                sameTarget && delta <= DUPLICATE_EVENT_WINDOW_MS -> Unit
                else -> result += event
            }
        }
        return result
    }

    private fun mergeScrollEvents(
        first: OperationAccessibilityEventRecord,
        second: OperationAccessibilityEventRecord,
    ): OperationAccessibilityEventRecord {
        return second.copy(
            scrollDeltaX = safeAdd(first.scrollDeltaX, second.scrollDeltaX),
            scrollDeltaY = safeAdd(first.scrollDeltaY, second.scrollDeltaY),
        )
    }

    private fun safeAdd(first: Int, second: Int): Int {
        val sum = first.toLong() + second.toLong()
        return sum.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private fun buildSelectorBundle(
        source: OperationNodeEvidence?,
        packageName: String,
    ): TargetSelectorBundle? {
        source ?: return null
        val candidates = buildList {
            source.viewId.safeEvidence()?.let {
                add(TargetSelectorCandidate(WorkflowSelectorKind.ResourceId, it, 1f, packageName, source.role))
            }
            source.contentDescription.safeEvidence()?.let {
                add(TargetSelectorCandidate(WorkflowSelectorKind.ContentDescription, it, 0.90f, packageName, source.role))
            }
            source.text.safeEvidence()?.let {
                add(TargetSelectorCandidate(WorkflowSelectorKind.TextAndRole, "$it|${source.role.orEmpty()}", 0.86f, packageName, source.role))
            }
            source.hint.safeEvidence()?.let {
                add(TargetSelectorCandidate(WorkflowSelectorKind.TextAndRole, "hint:$it|${source.role.orEmpty()}", 0.80f, packageName, source.role))
            }
            relativeAnchor(source)?.let {
                add(TargetSelectorCandidate(WorkflowSelectorKind.RelativeAnchor, it, 0.58f, packageName, source.role))
            }
            source.bounds.safeEvidence()?.let {
                add(TargetSelectorCandidate(WorkflowSelectorKind.RecordedBounds, it, 0.18f, packageName, source.role))
            }
        }.distinctBy { it.kind to it.value }
        val hasStrongSelector = candidates.any {
            it.kind != WorkflowSelectorKind.RecordedBounds && it.weight >= MINIMUM_SELECTOR_SCORE
        }
        if (!hasStrongSelector) return null
        return TargetSelectorBundle(
            candidates = candidates,
            minimumScore = MINIMUM_SELECTOR_SCORE,
            coordinateFallbackAllowed = false,
        )
    }

    private fun derivePostconditions(
        workflowId: String,
        stepOrder: Int,
        packageName: String,
        before: OperationNodeSnapshotRecord?,
        after: OperationNodeSnapshotRecord?,
        target: OperationNodeEvidence?,
    ): List<WorkflowStateCheck> {
        val beforeTexts = before?.nodes.orEmpty().mapNotNull { it.bestVisibleText() }.toSet()
        val targetText = target?.bestVisibleText()
        val newText = after?.nodes.orEmpty()
            .mapNotNull { it.bestVisibleText() }
            .firstOrNull { candidate ->
                candidate !in beforeTexts &&
                    candidate != targetText &&
                    candidate.length in 2..64 &&
                    candidate.lowercase() !in GENERIC_TEXTS
            }
        return if (before != null && newText != null) {
            listOf(
                WorkflowStateCheck(
                    id = "$workflowId-step-$stepOrder-post-text",
                    type = WorkflowStateCheckType.TextVisible,
                    expectedValue = newText,
                    packageName = packageName,
                    required = true,
                ),
            )
        } else {
            listOf(packageVisibleCheck(workflowId, "post-$stepOrder", packageName))
        }
    }

    private fun deriveCompletionChecks(
        workflowId: String,
        steps: List<WorkflowStep>,
        issues: MutableList<WorkflowCompilationIssue>,
    ): List<WorkflowStateCheck> {
        val strongCheck = steps.asReversed()
            .flatMap(WorkflowStep::postconditions)
            .firstOrNull { it.type in STRONG_COMPLETION_CHECKS }
        if (strongCheck != null) {
            return listOf(
                strongCheck.copy(
                    id = "$workflowId-completion-evidence",
                    required = true,
                ),
            )
        }
        issues += WorkflowCompilationIssue(
            code = "compilation_completion_requires_user",
            message = "演示没有产生足够强的最终成功证据，完成时必须由用户确认。",
            severity = WorkflowCompilationSeverity.Warning,
        )
        return listOf(
            WorkflowStateCheck(
                id = "$workflowId-completion-user",
                type = WorkflowStateCheckType.UserConfirmed,
                expectedValue = "用户确认任务已经完成",
                required = true,
            ),
        )
    }

    private fun packageVisibleCheck(
        workflowId: String,
        ownerSuffix: String,
        packageName: String,
    ): WorkflowStateCheck = WorkflowStateCheck(
        id = "$workflowId-$ownerSuffix-package",
        type = WorkflowStateCheckType.PackageVisible,
        expectedValue = packageName,
        packageName = packageName,
        required = true,
    )

    private fun inferScrollDirection(event: OperationAccessibilityEventRecord): String? {
        return when {
            abs(event.scrollDeltaY) >= abs(event.scrollDeltaX) && event.scrollDeltaY > 0 -> "forward"
            abs(event.scrollDeltaY) >= abs(event.scrollDeltaX) && event.scrollDeltaY < 0 -> "backward"
            event.scrollDeltaX > 0 -> "forward"
            event.scrollDeltaX < 0 -> "backward"
            event.fromIndex >= 0 && event.toIndex > event.fromIndex -> "forward"
            event.fromIndex >= 0 && event.toIndex < event.fromIndex -> "backward"
            event.maxScrollY > 0 && event.scrollY > 0 -> "forward"
            event.maxScrollX > 0 && event.scrollX > 0 -> "forward"
            else -> null
        }
    }

    private fun stepTitle(
        actionType: WorkflowActionType,
        source: OperationNodeEvidence?,
    ): String {
        val target = source?.bestVisibleText()
            ?: source?.viewId?.substringAfterLast('/')?.replace('_', ' ')
            ?: source?.role
            ?: "目标控件"
        return when (actionType) {
            WorkflowActionType.Tap -> "点击 $target"
            WorkflowActionType.LongPress -> "长按 $target"
            WorkflowActionType.InputText -> "在 $target 输入内容"
            WorkflowActionType.Scroll -> "滚动 $target"
            else -> actionType.label
        }.take(80)
    }

    private fun variableLabel(source: OperationNodeEvidence?, index: Int): String {
        val label = source?.hint.safeEvidence()
            ?: source?.contentDescription.safeEvidence()
            ?: source?.viewId?.substringAfterLast('/')?.replace('_', ' ')?.safeEvidence()
        return label?.take(36) ?: "输入内容 $index"
    }

    private fun targetKey(source: OperationNodeEvidence?): String {
        source ?: return "unknown"
        return listOf(
            source.viewId.orEmpty(),
            source.contentDescription.orEmpty(),
            source.hint.orEmpty(),
            source.role.orEmpty(),
            source.bounds.orEmpty(),
        ).joinToString("|")
    }

    private fun relativeAnchor(source: OperationNodeEvidence): String? {
        val values = source.bounds?.split(',')?.mapNotNull(String::toIntOrNull) ?: return null
        if (values.size != 4 || source.screenWidth <= 0 || source.screenHeight <= 0) return null
        val centerX = (values[0] + values[2]) / 2f / source.screenWidth
        val centerY = (values[1] + values[3]) / 2f / source.screenHeight
        val horizontal = when {
            centerX < 0.34f -> "left"
            centerX > 0.66f -> "right"
            else -> "center"
        }
        val vertical = when {
            centerY < 0.34f -> "top"
            centerY > 0.66f -> "bottom"
            else -> "middle"
        }
        return "role=${source.role.orEmpty()};region=$vertical-$horizontal;class=${source.className.orEmpty()}"
    }

    private fun OperationNodeEvidence.bestVisibleText(): String? {
        return contentDescription.safeEvidence()
            ?: text.safeEvidence()
            ?: hint.safeEvidence()
    }

    private fun String?.safeEvidence(): String? {
        val value = this?.trim().orEmpty()
        if (value.isBlank() || value.startsWith("[") && value.endsWith("]")) return null
        return value.take(160)
    }

    private fun displayNameForPackage(baseDraft: LearnedWorkflowDraft, packageName: String): String {
        val index = baseDraft.appScope.normalizedPackages.indexOf(packageName)
        return baseDraft.appScope.displayNames.getOrNull(index)
            ?.takeIf(String::isNotBlank)
            ?: packageName
    }

    private val ACTIONABLE_EVENT_TYPES = setOf(
        "view_clicked",
        "view_long_clicked",
        "view_text_changed",
        "view_scrolled",
    )
    private val STRONG_COMPLETION_CHECKS = setOf(
        WorkflowStateCheckType.TextVisible,
        WorkflowStateCheckType.TextHidden,
        WorkflowStateCheckType.NodeExists,
        WorkflowStateCheckType.NodeMissing,
        WorkflowStateCheckType.VisualRegionMatches,
    )
    private val GENERIC_TEXTS = setOf(
        "返回", "取消", "确定", "完成", "关闭", "back", "cancel", "ok", "done", "close",
    )
    private const val MINIMUM_SELECTOR_SCORE = 0.72f
    private const val DUPLICATE_EVENT_WINDOW_MS = 420L
    private const val TEXT_MERGE_WINDOW_MS = 1_800L
    private const val SCROLL_MERGE_WINDOW_MS = 650L
    private const val SNAPSHOT_MATCH_WINDOW_MS = 3_000L
    private const val LONG_IDLE_GAP_MS = 12_000L
}
