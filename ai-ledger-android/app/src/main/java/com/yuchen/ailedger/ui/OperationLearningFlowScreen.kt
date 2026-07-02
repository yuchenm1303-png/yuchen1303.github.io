package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowActionType
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.model.WorkflowStateCheck
import com.yuchen.ailedger.model.WorkflowStateCheckType
import com.yuchen.ailedger.model.WorkflowStep
import com.yuchen.ailedger.service.OperationLearningRecordingCoordinator
import com.yuchen.ailedger.service.WorkflowValidationIssue
import com.yuchen.ailedger.service.WorkflowValidationSeverity

private val WorkflowReviewAccent = Color(0xFF8DF9EA)
private val WorkflowReviewViolet = Color(0xFFCAB8FF)
private val WorkflowReviewDanger = Color(0xFFFFA6B2)
private val WorkflowReviewSurface = Color(0xFF10153A)

@Composable
fun OperationLearningFlowScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    learningViewModel: OperationLearningViewModel = viewModel(),
    reviewViewModel: OperationWorkflowReviewViewModel = viewModel(),
) {
    val learningState = learningViewModel.uiState
    val reviewState = reviewViewModel.uiState
    val recordingState by OperationLearningRecordingCoordinator.state.collectAsState()

    LaunchedEffect(reviewState.revision) {
        if (reviewState.revision > 0L) learningViewModel.refresh()
    }

    reviewState.draft?.let { draft ->
        OperationWorkflowReviewScreen(
            state = state,
            draft = draft,
            issues = reviewState.issues,
            approving = reviewState.approving,
            notice = reviewState.notice,
            onBack = reviewViewModel::close,
            onVariableLabelChange = reviewViewModel::updateVariableLabel,
            onCompletionEvidenceChange = reviewViewModel::updateCompletionEvidence,
            onApprove = reviewViewModel::approve,
            onReset = reviewViewModel::resetForNewDemonstration,
            onDismissNotice = reviewViewModel::clearNotice,
        )
        return
    }

    val selectedDraft = learningState.selectedDraftId?.let { selectedId ->
        learningState.drafts.firstOrNull { it.id == selectedId }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        OperationLearningScreen(
            state = state,
            onBack = onBack,
            viewModel = learningViewModel,
        )

        val actionVisible = selectedDraft != null &&
            !recordingState.active &&
            selectedDraft.status in setOf(
                WorkflowDraftStatus.Compiling,
                WorkflowDraftStatus.ReadyForReview,
                WorkflowDraftStatus.Approved,
                WorkflowDraftStatus.Verified,
            )
        if (actionVisible && selectedDraft != null) {
            OperationLearningStageActionBar(
                state = state,
                draft = selectedDraft,
                compiling = selectedDraft.id in reviewState.compilingDraftIds,
                notice = reviewState.notice,
                onCompile = { reviewViewModel.compile(selectedDraft.id) },
                onReview = { reviewViewModel.open(selectedDraft.id) },
                onDismissNotice = reviewViewModel::clearNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun OperationLearningStageActionBar(
    state: AssistantUiState,
    draft: LearnedWorkflowDraft,
    compiling: Boolean,
    notice: String?,
    onCompile: () -> Unit,
    onReview: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when {
        compiling -> "正在整理结构化流程…"
        draft.status == WorkflowDraftStatus.Compiling -> "整理演示为可审核流程"
        draft.status == WorkflowDraftStatus.ReadyForReview -> "打开流程审核页"
        draft.status == WorkflowDraftStatus.Approved -> "已批准 · 等待执行器接入"
        draft.status == WorkflowDraftStatus.Verified -> "已验证"
        else -> draft.status.label
    }
    val enabled = !compiling && draft.status in setOf(
        WorkflowDraftStatus.Compiling,
        WorkflowDraftStatus.ReadyForReview,
    )
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.11f,
        dimAlpha = 0f,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF10163D).copy(alpha = 0.42f))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.title,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = notice ?: "先整理和审核，不会直接执行。",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PressableGlass(
                    quality = state.quality,
                    glassIntensity = state.glassIntensity * 0.92f,
                    motionIntensity = state.motionIntensity,
                    radius = 999,
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .height(43.dp),
                    role = GlassRole.Card,
                    onClick = if (enabled) {
                        if (draft.status == WorkflowDraftStatus.Compiling) onCompile else onReview
                    } else {
                        ({ })
                    },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = if (enabled) 0.90f else 0.42f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!notice.isNullOrBlank()) {
                Text(
                    text = "收起提示",
                    color = WorkflowReviewAccent.copy(alpha = 0.68f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun OperationWorkflowReviewScreen(
    state: AssistantUiState,
    draft: LearnedWorkflowDraft,
    issues: List<WorkflowValidationIssue>,
    approving: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onVariableLabelChange: (String, String) -> Unit,
    onCompletionEvidenceChange: (String, String) -> Unit,
    onApprove: () -> Unit,
    onReset: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val blockingCount = issues.count { it.severity == WorkflowValidationSeverity.Blocking }
    val manualStepCount = draft.steps.count { it.action.type == WorkflowActionType.RequestUserConfirmation }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            ReviewBackButton(state = state, onBack = onBack)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "WORKFLOW REVIEW",
                    color = WorkflowReviewAccent.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "审核操作流程",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "确认步骤、输入变量、目标定位和成功证据。批准后只生成版本，不会在本阶段自动运行。",
                    color = Color.White.copy(alpha = 0.54f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        if (!notice.isNullOrBlank()) {
            item {
                ReviewNotice(text = notice, onDismiss = onDismissNotice)
            }
        }
        item {
            ReviewSummaryCard(
                draft = draft,
                blockingCount = blockingCount,
                manualStepCount = manualStepCount,
            )
        }
        if (issues.isNotEmpty()) {
            item { ReviewIssuesCard(issues) }
        }
        if (draft.variables.isNotEmpty()) {
            item { ReviewSectionTitle("每次运行的输入", "${draft.variables.size} 项") }
            item {
                ReviewVariablesCard(
                    draft = draft,
                    onVariableLabelChange = onVariableLabelChange,
                )
            }
        }
        item { ReviewSectionTitle("结构化步骤", "${draft.steps.size} 步") }
        draft.milestones.sortedBy { it.order }.forEach { milestone ->
            item(key = milestone.id) {
                ReviewMilestoneCard(
                    milestoneTitle = milestone.title,
                    steps = draft.steps.filter { it.milestoneId == milestone.id }.sortedBy { it.order },
                )
            }
        }
        item { ReviewSectionTitle("最终成功证据", "必须确认") }
        item {
            ReviewCompletionCard(
                checks = draft.completionChecks,
                onCompletionEvidenceChange = onCompletionEvidenceChange,
            )
        }
        item {
            ReviewActionCard(
                state = state,
                approving = approving,
                canApprove = blockingCount == 0,
                onApprove = onApprove,
                onReset = onReset,
            )
        }
    }
}

@Composable
private fun ReviewBackButton(state: AssistantUiState, onBack: () -> Unit) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier
            .fillMaxWidth(0.42f)
            .height(40.dp),
        role = GlassRole.Chip,
        onClick = onBack,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "‹ 操作学习",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun ReviewSummaryCard(
    draft: LearnedWorkflowDraft,
    blockingCount: Int,
    manualStepCount: Int,
) {
    ReviewPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = draft.title,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = draft.goal,
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ReviewMetric("应用", draft.appScope.normalizedPackages.size.toString(), Modifier.weight(1f))
                ReviewMetric("步骤", draft.steps.size.toString(), Modifier.weight(1f))
                ReviewMetric("人工确认", manualStepCount.toString(), Modifier.weight(1f))
                ReviewMetric("阻断项", blockingCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReviewMetric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.35f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ReviewIssuesCard(issues: List<WorkflowValidationIssue>) {
    val hasBlocking = issues.any { it.severity == WorkflowValidationSeverity.Blocking }
    ReviewPanel(tint = if (hasBlocking) WorkflowReviewDanger else WorkflowReviewViolet) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (hasBlocking) "批准前必须处理" else "审核提醒",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            issues.take(8).forEach { issue ->
                Text(
                    text = "• ${issue.message}",
                    color = if (issue.severity == WorkflowValidationSeverity.Blocking) {
                        WorkflowReviewDanger.copy(alpha = 0.86f)
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun ReviewVariablesCard(
    draft: LearnedWorkflowDraft,
    onVariableLabelChange: (String, String) -> Unit,
) {
    ReviewPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            draft.variables.forEach { variable ->
                OutlinedTextField(
                    value = variable.label,
                    onValueChange = { onVariableLabelChange(variable.key, it) },
                    label = { Text("输入名称 · ${variable.key}") },
                    supportingText = { Text(variable.description) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = TextStyle(color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp),
                    colors = reviewTextFieldColors(),
                )
            }
        }
    }
}

@Composable
private fun ReviewMilestoneCard(
    milestoneTitle: String,
    steps: List<WorkflowStep>,
) {
    ReviewPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = milestoneTitle,
                color = WorkflowReviewViolet.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            steps.forEachIndexed { index, step ->
                ReviewStepRow(index = index + 1, step = step)
            }
        }
    }
}

@Composable
private fun ReviewStepRow(index: Int, step: WorkflowStep) {
    val primarySelector = step.target?.candidates?.maxByOrNull { it.weight }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.038f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index.toString().padStart(2, '0'),
                color = WorkflowReviewAccent.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = step.title,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp),
            )
            Text(
                text = step.action.type.label,
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = when {
                step.action.type == WorkflowActionType.RequestUserConfirmation -> "此步骤不会自动执行，需要用户亲自完成。"
                primarySelector != null -> "主要定位：${primarySelector.kind.label} · 权重 ${"%.2f".format(primarySelector.weight)}"
                step.action.type == WorkflowActionType.OpenApp -> "按已授权包名打开应用。"
                else -> "没有自动定位目标。"
            },
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReviewBadge("前置 ${step.preconditions.size}")
            ReviewBadge("后置 ${step.postconditions.size}")
            ReviewBadge(step.riskLevel.label)
            ReviewBadge(step.confirmationPolicy.label)
        }
    }
}

@Composable
private fun ReviewCompletionCard(
    checks: List<WorkflowStateCheck>,
    onCompletionEvidenceChange: (String, String) -> Unit,
) {
    ReviewPanel {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            checks.forEach { check ->
                val editable = check.type in setOf(
                    WorkflowStateCheckType.TextVisible,
                    WorkflowStateCheckType.UserConfirmed,
                )
                if (editable) {
                    OutlinedTextField(
                        value = check.expectedValue,
                        onValueChange = { onCompletionEvidenceChange(check.id, it) },
                        label = { Text(check.type.label) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = TextStyle(color = Color.White.copy(alpha = 0.90f), fontSize = 12.5.sp),
                        colors = reviewTextFieldColors(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(check.type.label, color = Color.White.copy(alpha = 0.40f), fontSize = 9.5.sp)
                        Text(
                            check.expectedValue,
                            color = Color.White.copy(alpha = 0.76f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewActionCard(
    state: AssistantUiState,
    approving: Boolean,
    canApprove: Boolean,
    onApprove: () -> Unit,
    onReset: () -> Unit,
) {
    ReviewPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "批准后会冻结为版本快照；后续发现变化时只能生成新版本，不能静默覆盖。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ReviewGlassButton(
                    state = state,
                    label = "重新演示",
                    enabled = !approving,
                    danger = true,
                    modifier = Modifier.weight(0.38f),
                    onClick = onReset,
                )
                ReviewGlassButton(
                    state = state,
                    label = when {
                        approving -> "正在保存版本…"
                        canApprove -> "批准并保存版本"
                        else -> "仍有阻断项"
                    },
                    enabled = canApprove && !approving,
                    modifier = Modifier.weight(0.62f),
                    onClick = onApprove,
                )
            }
        }
    }
}

@Composable
private fun ReviewGlassButton(
    state: AssistantUiState,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.9f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(45.dp),
        role = GlassRole.Card,
        onClick = if (enabled) onClick else ({ }),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.34f)
                    danger -> WorkflowReviewDanger.copy(alpha = 0.86f)
                    else -> Color.White.copy(alpha = 0.90f)
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReviewSectionTitle(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReviewNotice(text: String, onDismiss: () -> Unit) {
    ReviewPanel(tint = WorkflowReviewAccent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
            PressableGlass(
                quality = com.yuchen.ailedger.model.GlassQuality.High,
                glassIntensity = 0.7f,
                motionIntensity = 0f,
                radius = 999,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(34.dp),
                role = GlassRole.Chip,
                onClick = onDismiss,
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("知道了", color = WorkflowReviewAccent.copy(alpha = 0.78f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ReviewBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.45f), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewPanel(
    tint: Color = WorkflowReviewSurface,
    content: @Composable () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.078f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(tint.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun reviewTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White.copy(alpha = 0.92f),
    unfocusedTextColor = Color.White.copy(alpha = 0.84f),
    focusedBorderColor = WorkflowReviewAccent.copy(alpha = 0.48f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedLabelColor = WorkflowReviewAccent.copy(alpha = 0.72f),
    unfocusedLabelColor = Color.White.copy(alpha = 0.42f),
    cursorColor = WorkflowReviewAccent,
    focusedContainerColor = Color.White.copy(alpha = 0.025f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.018f),
)
