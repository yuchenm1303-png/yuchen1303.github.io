package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.service.OperationLearningRecordingCoordinator
import com.yuchen.ailedger.service.OperationRecordingPhase
import com.yuchen.ailedger.service.OperationRecordingState
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationStage

private val OperationLearningAccent = Color(0xFF8DF9EA)
private val OperationLearningViolet = Color(0xFFCAB8FF)
private val OperationLearningSurface = Color(0xFF10153A)
private val OperationLearningDanger = Color(0xFFFFA6B2)

private data class LearningFlowStep(
    val title: String,
    val description: String,
)

private val learningFlowSteps = listOf(
    LearningFlowStep(
        title = "定义目标与范围",
        description = "先明确最终结果、允许进入的应用，以及每次运行会变化的输入。",
    ),
    LearningFlowStep(
        title = "亲自演示一次",
        description = "录制期间由你操作手机，系统只采集动作、控件证据和页面变化。",
    ),
    LearningFlowStep(
        title = "审核结构化流程",
        description = "检查步骤、变量、选择器、成功证据和风险确认，不直接保存原始点击轨迹。",
    ),
    LearningFlowStep(
        title = "逐步执行并验证",
        description = "默认按已批准路线确定性执行，每一步完成后都验证页面状态。",
    ),
)

@Composable
fun OperationLearningScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    viewModel: OperationLearningViewModel = viewModel(),
) {
    val uiState = viewModel.uiState
    val recordingState by OperationLearningRecordingCoordinator.state.collectAsState()

    LaunchedEffect(recordingState.phase, recordingState.demonstrationId) {
        if (recordingState.phase == OperationRecordingPhase.Captured) {
            viewModel.refresh()
        }
    }

    BackHandler {
        when {
            uiState.editorVisible -> viewModel.closeIntentEditor()
            recordingState.active -> Unit
            else -> onBack()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            OperationLearningBackButton(
                state = state,
                enabled = !recordingState.active,
                onBack = {
                    if (uiState.editorVisible) viewModel.closeIntentEditor() else onBack()
                },
            )
        }

        item { OperationLearningHeader() }

        if (recordingState.phase != OperationRecordingPhase.Idle || !recordingState.message.isNullOrBlank()) {
            item {
                RecordingStatusCard(
                    state = state,
                    recordingState = recordingState,
                    onFinish = viewModel::finishRecording,
                    onCancel = viewModel::cancelRecording,
                    onDismiss = OperationLearningRecordingCoordinator::resetTerminalState,
                )
            }
        }

        uiState.notice?.let { notice ->
            item {
                OperationLearningNotice(
                    text = notice,
                    onDismiss = viewModel::clearNotice,
                )
            }
        }

        item {
            CreateIntentCard(
                state = state,
                editorVisible = uiState.editorVisible,
                enabled = !recordingState.active,
                onClick = {
                    if (uiState.editorVisible) viewModel.closeIntentEditor() else viewModel.openIntentEditor()
                },
            )
        }

        item {
            AnimatedVisibility(
                visible = uiState.editorVisible && !recordingState.active,
                enter = fadeIn() + slideInVertically { -it / 10 },
                exit = fadeOut() + slideOutVertically { -it / 10 },
            ) {
                WorkflowIntentEditor(
                    state = state,
                    uiState = uiState,
                    onTitleChange = viewModel::updateTitle,
                    onGoalChange = viewModel::updateGoal,
                    onAppNameChange = viewModel::updateAppName,
                    onPackageNameChange = viewModel::updatePackageName,
                    onCancel = viewModel::closeIntentEditor,
                    onSave = viewModel::createIntentDraft,
                )
            }
        }

        item {
            LearningSectionTitle(
                title = "工作方式",
                trailing = "4 个阶段",
            )
        }
        item { LearningFlowCard() }

        item {
            LearningSectionTitle(
                title = "执行原则",
                trailing = "默认确定性",
            )
        }
        item { DeterministicExecutionCard() }

        item {
            LearningSectionTitle(
                title = "我的操作",
                trailing = if (uiState.loading) "读取中" else "${uiState.drafts.size} 个",
            )
        }

        if (uiState.drafts.isEmpty()) {
            item {
                LearnedOperationsEmptyCard(
                    state = state,
                    enabled = !recordingState.active,
                    onCreate = viewModel::openIntentEditor,
                )
            }
        } else {
            items(
                items = uiState.drafts,
                key = { it.id },
            ) { draft ->
                WorkflowDraftCard(
                    state = state,
                    draft = draft,
                    selected = draft.id == uiState.selectedDraftId,
                    recordingState = recordingState,
                    onSelect = { viewModel.selectDraft(draft.id) },
                    onStartRecording = { viewModel.startRecording(draft.id) },
                    onFinishRecording = viewModel::finishRecording,
                    onDelete = { viewModel.deleteDraft(draft.id) },
                )
            }
        }

        item { SafetyBoundaryCard() }
    }
}

@Composable
private fun OperationLearningBackButton(
    state: AssistantUiState,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier
            .fillMaxWidth(0.28f)
            .height(40.dp),
        role = GlassRole.Chip,
        onClick = if (enabled) onBack else ({ }),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (enabled) "‹ 功能" else "录制中",
                color = Color.White.copy(alpha = if (enabled) 0.88f else 0.42f),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun OperationLearningHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "LEARN BY DEMONSTRATION",
            color = OperationLearningAccent.copy(alpha = 0.74f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = "操作学习",
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "你演示一次，助手整理成可检查的操作流程；运行时按你批准的路线逐步执行并验证。",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecordingStatusCard(
    state: AssistantUiState,
    recordingState: OperationRecordingState,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = recordingState.active
    val accent = when (recordingState.phase) {
        OperationRecordingPhase.Failed -> OperationLearningDanger
        OperationRecordingPhase.Captured -> OperationLearningAccent
        else -> OperationLearningViolet
    }
    val title = when (recordingState.phase) {
        OperationRecordingPhase.Starting -> "正在准备录制"
        OperationRecordingPhase.Recording -> "正在录制演示"
        OperationRecordingPhase.Stopping -> "正在封存轨迹"
        OperationRecordingPhase.Captured -> "演示采集完成"
        OperationRecordingPhase.Failed -> "录制未完成"
        OperationRecordingPhase.Idle -> "录制状态"
    }

    FrostInfoGlassPanel(
        radius = 19f,
        backdropAlpha = 1f,
        frostAlpha = 0.092f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(27.dp))
                .background(accent.copy(alpha = 0.07f))
                .padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = recordingState.workflowTitle.ifBlank {
                            recordingState.message.orEmpty()
                        },
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (recordingState.capturedEventCount > 0) {
                    Text(
                        text = "${recordingState.capturedEventCount} 条证据",
                        color = accent.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            recordingState.message?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }

            Text(
                text = if (active) {
                    "输入框内容会被替换为长度区间；密码、验证码和支付敏感内容不会写入轨迹。也可以从系统通知结束或取消录制。"
                } else {
                    "原始轨迹仍不能直接执行，下一阶段会将它整理为可审核的步骤、选择器和成功证据。"
                },
                color = Color.White.copy(alpha = 0.43f),
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
            )

            if (active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OperationLearningActionButton(
                        state = state,
                        label = "取消并删除",
                        modifier = Modifier.weight(0.42f),
                        enabled = recordingState.phase == OperationRecordingPhase.Recording,
                        danger = true,
                        onClick = onCancel,
                    )
                    OperationLearningActionButton(
                        state = state,
                        label = "结束并保存",
                        modifier = Modifier.weight(0.58f),
                        enabled = recordingState.phase == OperationRecordingPhase.Recording,
                        onClick = onFinish,
                    )
                }
            } else {
                OperationLearningActionButton(
                    state = state,
                    label = "收起状态",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun OperationLearningNotice(
    text: String,
    onDismiss: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 15f,
        backdropAlpha = 1f,
        frostAlpha = 0.074f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(OperationLearningAccent.copy(alpha = 0.055f))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "知道了",
                color = OperationLearningAccent.copy(alpha = 0.78f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun CreateIntentCard(
    state: AssistantUiState,
    editorVisible: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 20f,
        backdropAlpha = 1f,
        frostAlpha = 0.092f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF101743).copy(alpha = 0.24f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "第一步 · 定义意图",
                    color = OperationLearningAccent.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "先说清楚要学什么",
                    color = Color.White,
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "创建草稿时只记录目标和允许操作的应用，不会立即开启无障碍监听。",
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                LearningTag("显式录制", Modifier.weight(1f))
                LearningTag("流程确认", Modifier.weight(1f))
                LearningTag("逐步验证", Modifier.weight(1f))
            }

            OperationLearningActionButton(
                state = state,
                label = when {
                    !enabled -> "请先结束当前录制"
                    editorVisible -> "收起草稿设置"
                    else -> "创建操作草稿"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = enabled,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun WorkflowIntentEditor(
    state: AssistantUiState,
    uiState: OperationLearningUiState,
    onTitleChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onAppNameChange: (String) -> Unit,
    onPackageNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Boolean,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.084f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(OperationLearningSurface.copy(alpha = 0.24f))
                .padding(horizontal = 16.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "录制前目标草稿",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "这一步只冻结意图和应用边界。步骤、选择器与成功证据将在演示后生成，再由你审核。",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )

            OperationLearningTextField(uiState.titleInput, onTitleChange, "操作名称", true)
            OperationLearningTextField(uiState.goalInput, onGoalChange, "最终目标", false)
            OperationLearningTextField(uiState.appNameInput, onAppNameChange, "应用名称（可选）", true)
            OperationLearningTextField(uiState.packageNameInput, onPackageNameChange, "允许操作的应用包名", true)

            if (uiState.editorIssues.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OperationLearningDanger.copy(alpha = 0.075f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.editorIssues.take(3).forEach { issue ->
                        Text(
                            text = "• ${issue.message}",
                            color = OperationLearningDanger.copy(alpha = 0.88f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OperationLearningActionButton(
                    state = state,
                    label = "取消",
                    modifier = Modifier.weight(0.38f),
                    enabled = true,
                    onClick = onCancel,
                )
                OperationLearningActionButton(
                    state = state,
                    label = "保存草稿",
                    modifier = Modifier.weight(0.62f),
                    enabled = true,
                    onClick = { onSave() },
                )
            }
        }
    }
}

@Composable
private fun OperationLearningTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 5,
        shape = RoundedCornerShape(17.dp),
        textStyle = TextStyle(
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White.copy(alpha = 0.92f),
            unfocusedTextColor = Color.White.copy(alpha = 0.84f),
            focusedBorderColor = OperationLearningAccent.copy(alpha = 0.48f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedLabelColor = OperationLearningAccent.copy(alpha = 0.72f),
            unfocusedLabelColor = Color.White.copy(alpha = 0.42f),
            cursorColor = OperationLearningAccent,
            focusedContainerColor = Color.White.copy(alpha = 0.025f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.018f),
        ),
    )
}

@Composable
private fun OperationLearningActionButton(
    state: AssistantUiState,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.88f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(43.dp),
        role = GlassRole.Card,
        onClick = if (enabled) onClick else ({ }),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.34f)
                    danger -> OperationLearningDanger.copy(alpha = 0.86f)
                    else -> Color.White.copy(alpha = 0.88f)
                },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LearningTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(31.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.055f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.61f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LearningSectionTitle(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, start = 2.dp, end = 2.dp),
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LearningFlowCard() {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.078f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(OperationLearningSurface.copy(alpha = 0.22f))
                .padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            learningFlowSteps.forEach { step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = step.title,
                        color = Color.White.copy(alpha = 0.93f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = step.description,
                        color = Color.White.copy(alpha = 0.49f),
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeterministicExecutionCard() {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.076f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF11163D).copy(alpha = 0.21f))
                .padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            PrincipleRow("路线不可静默改写", "找不到目标时停止，不允许自行发明新步骤。")
            PrincipleRow("坐标只能做弱线索", "必须同时满足页面指纹和稳定选择器，不能单独依赖录制坐标。")
            PrincipleRow("成功证据优先", "每一步和最终任务都必须有可检查的完成条件。")
            PrincipleRow("辅助修复需再次同意", "视觉循环只作为受控升级能力，不是默认执行内核。")
        }
    }
}

@Composable
private fun PrincipleRow(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = description,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun LearnedOperationsEmptyCard(
    state: AssistantUiState,
    enabled: Boolean,
    onCreate: () -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.074f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF12163D).copy(alpha = 0.20f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("还没有操作草稿", color = Color.White.copy(alpha = 0.91f), fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(
                text = "先定义一个明确目标和允许进入的应用，再开始演示。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            OperationLearningActionButton(
                state = state,
                label = "创建第一个草稿",
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .padding(top = 5.dp),
                enabled = enabled,
                onClick = onCreate,
            )
        }
    }
}

@Composable
private fun WorkflowDraftCard(
    state: AssistantUiState,
    draft: LearnedWorkflowDraft,
    selected: Boolean,
    recordingState: OperationRecordingState,
    onSelect: () -> Unit,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onDelete: () -> Unit,
) {
    val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
    val appLabel = draft.appScope.displayNames.firstOrNull()
        ?: draft.appScope.normalizedPackages.firstOrNull()
        ?: "未指定应用"
    val thisRecording = recordingState.active && recordingState.workflowId == draft.id
    val anotherRecording = recordingState.active && recordingState.workflowId != draft.id
    val canStart = draft.status == WorkflowDraftStatus.Intent && report.canProceed && !recordingState.active
    val actionLabel = when {
        thisRecording -> "结束演示"
        anotherRecording -> "其他操作录制中"
        draft.status == WorkflowDraftStatus.Compiling -> "等待整理"
        draft.status == WorkflowDraftStatus.ReadyForReview -> "等待审核"
        draft.status == WorkflowDraftStatus.Approved -> "已批准"
        draft.status == WorkflowDraftStatus.Verified -> "已验证"
        canStart -> "开始演示"
        else -> "暂不可录制"
    }

    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = if (selected || thisRecording) 0.088f else 0.072f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(
                    if (selected || thisRecording) OperationLearningViolet.copy(alpha = 0.075f)
                    else Color(0xFF11163D).copy(alpha = 0.20f),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    WorkflowStatusChip(draft.status)
                    Text(
                        text = draft.executionMode.label,
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = draft.title,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = draft.goal,
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DraftMeta("应用", appLabel, Modifier.weight(1f))
                DraftMeta("步骤", if (draft.steps.isEmpty()) "待整理" else "${draft.steps.size} 步", Modifier.weight(1f))
                DraftMeta("校验", if (report.canProceed) "已通过" else "需补充", Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OperationLearningActionButton(
                    state = state,
                    label = if (selected) "已选择" else "查看",
                    modifier = Modifier.weight(0.28f),
                    enabled = !recordingState.active,
                    onClick = onSelect,
                )
                OperationLearningActionButton(
                    state = state,
                    label = actionLabel,
                    modifier = Modifier.weight(0.50f),
                    enabled = thisRecording || canStart,
                    onClick = if (thisRecording) onFinishRecording else onStartRecording,
                )
                OperationLearningActionButton(
                    state = state,
                    label = "删除",
                    modifier = Modifier.weight(0.22f),
                    enabled = !recordingState.active,
                    danger = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun WorkflowStatusChip(status: WorkflowDraftStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OperationLearningAccent.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = status.label,
            color = OperationLearningAccent.copy(alpha = 0.78f),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DraftMeta(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.74f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SafetyBoundaryCard() {
    FrostInfoGlassPanel(
        radius = 17f,
        backdropAlpha = 1f,
        frostAlpha = 0.066f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101536).copy(alpha = 0.18f))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "原始演示不能直接执行",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "录制只在用户主动开启期间工作；离开授权应用、开始智能体任务、失败或取消后都会恢复低负载 Idle。轨迹采用本机加密短期保存。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
