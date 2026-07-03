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
import androidx.compose.foundation.layout.weight
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
import com.yuchen.ailedger.model.LearnedVisualSkill
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
        title = "描述想教会助手的任务",
        description = "告诉云端最终目标、适用应用，以及哪些信息在每次运行时可能变化。",
    ),
    LearningFlowStep(
        title = "亲自完成一次视觉演示",
        description = "本地只加密采样授权应用的屏幕关键帧，不保存控件节点，也不编译固定点击路线。",
    ),
    LearningFlowStep(
        title = "云端理解并生成 Skill",
        description = "云端从整次演示提炼触发场景、输入、操作原则、成功标准和安全边界。",
    ),
    LearningFlowStep(
        title = "审核后交给视觉智能执行",
        description = "Replay 时重新观察当前屏幕并完成目标，不机械复读录制坐标。",
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
        if (recordingState.phase == OperationRecordingPhase.Captured) viewModel.refresh()
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
                SkillIntentEditor(
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

        item { LearningSectionTitle("工作方式", "云端理解") }
        item { LearningFlowCard() }
        item { LearningSectionTitle("架构原则", "薄客户端") }
        item { CloudAuthorityCard() }
        item {
            LearningSectionTitle(
                title = "我的 Skill",
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
                SkillDraftCard(
                    state = state,
                    draft = draft,
                    skill = uiState.skillArtifacts[draft.id],
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
                text = if (enabled) "‹ 功能" else "演示中",
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
            text = "TEACH A VISUAL SKILL",
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
            text = "你演示一次，云端理解你的方法并生成 Skill；以后由视觉智能根据当前界面重新完成。",
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
        OperationRecordingPhase.Starting -> "正在准备视觉演示"
        OperationRecordingPhase.Recording -> "正在观看你的演示"
        OperationRecordingPhase.Stopping -> "云端正在理解演示"
        OperationRecordingPhase.Captured -> "Skill 草稿已生成"
        OperationRecordingPhase.Failed -> "演示未完成"
        OperationRecordingPhase.Idle -> "视觉演示状态"
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
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = recordingState.workflowTitle.ifBlank { recordingState.message.orEmpty() },
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (recordingState.capturedEventCount > 0) {
                    Text(
                        text = "${recordingState.capturedEventCount} 帧",
                        color = accent.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            recordingState.message?.takeIf(String::isNotBlank)?.let { message ->
                Text(message, color = Color.White.copy(alpha = 0.56f), fontSize = 11.5.sp, lineHeight = 17.sp)
            }
            Text(
                text = if (active) {
                    "请切到授权应用并完整做一遍。系统只保存加密视觉关键帧，不记录节点树，也不会在本地猜测流程。"
                } else {
                    "云端生成的是任务方法、输入和成功标准，不是固定坐标脚本。"
                },
                color = Color.White.copy(alpha = 0.43f),
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
            )
            if (active) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        label = "结束并生成 Skill",
                        modifier = Modifier.weight(0.58f),
                        enabled = recordingState.phase == OperationRecordingPhase.Recording,
                        onClick = onFinish,
                    )
                }
            } else if (recordingState.phase == OperationRecordingPhase.Captured || recordingState.phase == OperationRecordingPhase.Failed) {
                OperationLearningActionButton(
                    state = state,
                    label = "知道了",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun OperationLearningNotice(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.70f), fontSize = 11.5.sp, lineHeight = 17.sp)
        PressableGlass(
            quality = com.yuchen.ailedger.model.RenderQuality.Smooth,
            glassIntensity = 0.7f,
            motionIntensity = 0f,
            radius = 999,
            modifier = Modifier.height(32.dp),
            role = GlassRole.Chip,
            onClick = onDismiss,
        ) {
            Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                Text("关闭", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
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
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.082f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(OperationLearningViolet.copy(alpha = 0.055f))
                .padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text("教助手一个新 Skill", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(
                "先说明最终目标和允许观察的应用。演示结束后，云端会解释自己学到了什么。",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LearningTag("视觉关键帧", Modifier.weight(1f))
                LearningTag("云端理解", Modifier.weight(1f))
                LearningTag("动态 Replay", Modifier.weight(1f))
            }
            OperationLearningActionButton(
                state = state,
                label = when {
                    !enabled -> "请先结束当前演示"
                    editorVisible -> "收起教学设置"
                    else -> "创建 Skill 教学"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun SkillIntentEditor(
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
            Text("教学目标", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(
                "描述你想教会助手的任务。不要在这里编写点击步骤，云端会从视觉演示中理解方法。",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            OperationLearningTextField(uiState.titleInput, onTitleChange, "Skill 名称", true)
            OperationLearningTextField(uiState.goalInput, onGoalChange, "想完成什么，以及哪些内容可能变化", false)
            OperationLearningTextField(uiState.appNameInput, onAppNameChange, "应用名称（可选）", true)
            OperationLearningTextField(uiState.packageNameInput, onPackageNameChange, "允许观察的应用包名", true)

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
                        Text("• ${issue.message}", color = OperationLearningDanger.copy(alpha = 0.88f), fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OperationLearningActionButton(state, "取消", Modifier.weight(0.38f), true, onClick = onCancel)
                OperationLearningActionButton(state, "保存教学草稿", Modifier.weight(0.62f), true, onClick = { onSave() })
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
        textStyle = TextStyle(color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, lineHeight = 18.sp),
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
        Text(text, color = Color.White.copy(alpha = 0.61f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(trailing, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(step.title, color = Color.White.copy(alpha = 0.93f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(step.description, color = Color.White.copy(alpha = 0.49f), fontSize = 11.5.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun CloudAuthorityCard() {
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
            PrincipleRow("云端负责理解与决策", "本地不复制一套规划、验证和恢复规则去限制视觉模型。")
            PrincipleRow("视觉是唯一感知权威", "节点树、Resource ID 和固定选择器不进入新 Skill 主链。")
            PrincipleRow("本地只负责可靠执行", "提供截图、点击、滑动、返回和输入等基础能力。")
            PrincipleRow("安全边界仍由本地掌握", "授权范围、敏感操作确认、停止和加密存储不会交给模型绕过。")
        }
    }
}

@Composable
private fun PrincipleRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(description, color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, lineHeight = 16.sp)
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
            Text("还没有视觉 Skill", color = Color.White.copy(alpha = 0.91f), fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(
                text = "先描述一个目标，然后让助手观看你完整完成一次。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            OperationLearningActionButton(
                state = state,
                label = "创建第一个 Skill",
                modifier = Modifier.fillMaxWidth(0.62f).padding(top = 5.dp),
                enabled = enabled,
                onClick = onCreate,
            )
        }
    }
}

@Composable
private fun SkillDraftCard(
    state: AssistantUiState,
    draft: LearnedWorkflowDraft,
    skill: LearnedVisualSkill?,
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
        anotherRecording -> "其他 Skill 演示中"
        draft.status == WorkflowDraftStatus.Compiling -> "云端理解中"
        draft.status == WorkflowDraftStatus.ReadyForReview -> "等待审核"
        draft.status == WorkflowDraftStatus.Approved -> "已批准"
        draft.status == WorkflowDraftStatus.Verified -> "已验证"
        canStart -> "开始视觉演示"
        else -> "暂不可演示"
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WorkflowStatusChip(draft.status)
                Text(draft.executionMode.label, color = Color.White.copy(alpha = 0.38f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(draft.title, color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(draft.goal, color = Color.White.copy(alpha = 0.50f), fontSize = 11.5.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DraftMeta("应用", appLabel, Modifier.weight(1f))
                DraftMeta("输入", skill?.inputs?.size?.let { "$it 项" } ?: "待学习", Modifier.weight(1f))
                DraftMeta("置信度", skill?.let { "${(it.confidence * 100).toInt()}%" } ?: "--", Modifier.weight(1f))
            }

            if (selected && skill != null) {
                SkillUnderstandingPanel(skill)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperationLearningActionButton(state, if (selected) "已展开" else "查看", Modifier.weight(0.28f), !recordingState.active, onClick = onSelect)
                OperationLearningActionButton(
                    state = state,
                    label = actionLabel,
                    modifier = Modifier.weight(0.50f),
                    enabled = thisRecording || canStart,
                    onClick = if (thisRecording) onFinishRecording else onStartRecording,
                )
                OperationLearningActionButton(state, "删除", Modifier.weight(0.22f), !recordingState.active, danger = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun SkillUnderstandingPanel(skill: LearnedVisualSkill) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("云端学到的方法", color = OperationLearningAccent.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(
            text = skill.cloudSummary.ifBlank { skill.description },
            color = Color.White.copy(alpha = 0.66f),
            fontSize = 11.sp,
            lineHeight = 16.dp.value.sp,
        )
        skill.operatingPrinciples.take(3).forEach { principle ->
            Text("• $principle", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        if (skill.successCriteria.isNotEmpty()) {
            Text("成功：${skill.successCriteria.first()}", color = OperationLearningViolet.copy(alpha = 0.70f), fontSize = 10.5.sp, lineHeight = 15.sp)
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
        Text(status.label, color = OperationLearningAccent.copy(alpha = 0.78f), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
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
        Text(label, color = Color.White.copy(alpha = 0.34f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White.copy(alpha = 0.74f), fontSize = 10.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text("智能交给云端，边界留在本地", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(
                text = "只采集授权应用画面；视觉证据本机加密短期保存，Skill 生成成功后删除。密码、验证码、支付和不可逆操作仍受本地确认保护。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
