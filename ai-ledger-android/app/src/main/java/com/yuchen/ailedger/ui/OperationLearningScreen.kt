package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationStage

private val OperationLearningAccent = Color(0xFF8DF9EA)
private val OperationLearningViolet = Color(0xFFCAB8FF)
private val OperationLearningSurface = Color(0xFF10153A)

private data class LearningFlowStep(
    val index: String,
    val title: String,
    val description: String,
)

private val learningFlowSteps = listOf(
    LearningFlowStep(
        index = "01",
        title = "定义目标与范围",
        description = "先明确最终结果、允许进入的应用，以及每次运行会变化的输入。",
    ),
    LearningFlowStep(
        index = "02",
        title = "亲自演示一次",
        description = "录制期间由你操作手机，系统只采集动作、控件证据和页面变化。",
    ),
    LearningFlowStep(
        index = "03",
        title = "审核结构化流程",
        description = "检查步骤、变量、选择器、成功证据和风险确认，不直接保存原始点击轨迹。",
    ),
    LearningFlowStep(
        index = "04",
        title = "逐步执行并验证",
        description = "默认按已批准路线确定性执行，每一步完成后都验证页面状态。",
    ),
)

@Composable
fun OperationLearningScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onStartDemonstration: (String) -> Unit = {},
    viewModel: OperationLearningViewModel = viewModel(),
) {
    val uiState = viewModel.uiState

    BackHandler {
        if (uiState.editorVisible) {
            viewModel.closeIntentEditor()
        } else {
            onBack()
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
                onBack = {
                    if (uiState.editorVisible) viewModel.closeIntentEditor() else onBack()
                },
            )
        }

        item { OperationLearningHeader() }

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
                onClick = {
                    if (uiState.editorVisible) viewModel.closeIntentEditor() else viewModel.openIntentEditor()
                },
            )
        }

        item {
            AnimatedVisibility(
                visible = uiState.editorVisible,
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
                trailing = "${uiState.drafts.size} 个草稿",
            )
        }

        if (uiState.drafts.isEmpty()) {
            item {
                LearnedOperationsEmptyCard(
                    state = state,
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
                    onSelect = { viewModel.selectDraft(draft.id) },
                    onPrepare = {
                        viewModel.prepareDemonstration(draft.id)
                        onStartDemonstration(draft.id)
                    },
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
        onClick = onBack,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "‹ 功能",
                color = Color.White.copy(alpha = 0.88f),
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
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun CreateIntentCard(
    state: AssistantUiState,
    editorVisible: Boolean,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
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
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(OperationLearningViolet.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "意",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                LearningTag("显式录制", Modifier.weight(1f))
                LearningTag("流程确认", Modifier.weight(1f))
                LearningTag("逐步验证", Modifier.weight(1f))
            }

            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity * 0.94f,
                motionIntensity = state.motionIntensity,
                radius = 999,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                role = GlassRole.Card,
                onClick = onClick,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editorVisible) "收起草稿设置" else "创建操作草稿",
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (editorVisible) "⌃" else "→",
                        color = OperationLearningAccent.copy(alpha = 0.82f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
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

            OperationLearningTextField(
                value = uiState.titleInput,
                onValueChange = onTitleChange,
                label = "操作名称",
                singleLine = true,
            )
            OperationLearningTextField(
                value = uiState.goalInput,
                onValueChange = onGoalChange,
                label = "最终目标",
                singleLine = false,
            )
            OperationLearningTextField(
                value = uiState.appNameInput,
                onValueChange = onAppNameChange,
                label = "应用名称（可选）",
                singleLine = true,
            )
            OperationLearningTextField(
                value = uiState.packageNameInput,
                onValueChange = onPackageNameChange,
                label = "允许操作的应用包名",
                singleLine = true,
            )

            if (uiState.editorIssues.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFF7A8A).copy(alpha = 0.075f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.editorIssues.take(3).forEach { issue ->
                        Text(
                            text = "• ${issue.message}",
                            color = Color(0xFFFFC3CB).copy(alpha = 0.86f),
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
                    onClick = onCancel,
                )
                OperationLearningActionButton(
                    state = state,
                    label = "保存草稿",
                    modifier = Modifier.weight(0.62f),
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
        textStyle = androidx.compose.ui.text.TextStyle(
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
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.88f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(43.dp),
        role = GlassRole.Card,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun LearningTag(
    text: String,
    modifier: Modifier = Modifier,
) {
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
private fun LearningSectionTitle(
    title: String,
    trailing: String,
) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = step.index,
                        color = OperationLearningViolet.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
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
private fun PrincipleRow(
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(OperationLearningAccent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                color = OperationLearningAccent.copy(alpha = 0.80f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
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
}

@Composable
private fun LearnedOperationsEmptyCard(
    state: AssistantUiState,
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
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            Text(
                text = "还没有操作草稿",
                color = Color.White.copy(alpha = 0.91f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
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
    onSelect: () -> Unit,
    onPrepare: () -> Unit,
    onDelete: () -> Unit,
) {
    val report = OperationWorkflowValidator.validate(
        draft = draft,
        stage = WorkflowValidationStage.RecordingIntent,
    )
    val appLabel = draft.appScope.displayNames.firstOrNull()
        ?: draft.appScope.normalizedPackages.firstOrNull()
        ?: "未指定应用"

    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = if (selected) 0.088f else 0.072f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(
                    if (selected) {
                        OperationLearningViolet.copy(alpha = 0.075f)
                    } else {
                        Color(0xFF11163D).copy(alpha = 0.20f)
                    },
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "草",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DraftMeta("应用", appLabel, Modifier.weight(1f))
                DraftMeta("步骤", if (draft.steps.isEmpty()) "待演示" else "${draft.steps.size} 步", Modifier.weight(1f))
                DraftMeta("校验", if (report.canProceed) "可录制" else "需补充", Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OperationLearningActionButton(
                    state = state,
                    label = if (selected) "已选择" else "查看",
                    modifier = Modifier.weight(0.30f),
                    onClick = onSelect,
                )
                OperationLearningActionButton(
                    state = state,
                    label = "检查演示条件",
                    modifier = Modifier.weight(0.52f),
                    onClick = onPrepare,
                )
                OperationLearningActionButton(
                    state = state,
                    label = "删除",
                    modifier = Modifier.weight(0.24f),
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
private fun DraftMeta(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101536).copy(alpha = 0.18f))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(35.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OperationLearningAccent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "盾",
                    color = OperationLearningAccent.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "原始演示不能直接执行",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "密码、验证码和支付确认不会写入流程；只依赖坐标、缺少成功证据或未经审核的草稿都不能进入执行状态。",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
