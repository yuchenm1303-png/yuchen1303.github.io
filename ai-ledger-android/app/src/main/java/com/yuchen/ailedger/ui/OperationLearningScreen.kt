package com.yuchen.ailedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AiLedgerApplication
import com.yuchen.ailedger.data.OperationSkillAssetSyncRepository
import com.yuchen.ailedger.data.OperationSkillAssetSyncStatusStore
import com.yuchen.ailedger.data.SupabaseAccountState
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LearnedVisualSkill
import com.yuchen.ailedger.model.LearnedWorkflowDraft
import com.yuchen.ailedger.model.OperationSkillAssetSyncReason
import com.yuchen.ailedger.model.OperationSkillAssetSyncSource
import com.yuchen.ailedger.model.OperationSkillAssetSyncStatusSnapshot
import com.yuchen.ailedger.model.WorkflowDraftStatus
import com.yuchen.ailedger.service.InstalledLaunchableApp
import com.yuchen.ailedger.service.InstalledLaunchableAppCatalog
import com.yuchen.ailedger.service.OperationLearningRecordingCoordinator
import com.yuchen.ailedger.service.OperationRecordingPhase
import com.yuchen.ailedger.service.OperationRecordingState
import com.yuchen.ailedger.service.OperationWorkflowValidator
import com.yuchen.ailedger.service.WorkflowValidationStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    var appPickerVisible by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledLaunchableApp>>(emptyList()) }
    var installedAppsLoading by remember { mutableStateOf(false) }
    var installedAppsError by remember { mutableStateOf<String?>(null) }
    val applicationContext = AiLedgerApplication.contextOrNull()
    val authRepository = remember(applicationContext) { applicationContext?.let(SupabaseAuthRepository::get) }
    val accountState by (authRepository?.state?.collectAsState()
        ?: remember { mutableStateOf(SupabaseAccountState(loading = false, message = "账号状态不可用")) })
    val skillSyncRepository = remember(applicationContext) { applicationContext?.let(OperationSkillAssetSyncRepository::get) }
    val skillSyncStatusStore = remember(applicationContext) { applicationContext?.let(OperationSkillAssetSyncStatusStore::get) }
    var skillSyncStatus by remember(skillSyncStatusStore) {
        mutableStateOf(skillSyncStatusStore?.read() ?: OperationSkillAssetSyncStatusSnapshot())
    }
    var skillSyncing by remember { mutableStateOf(false) }
    var skillSyncMessage by remember { mutableStateOf<String?>(null) }
    val skillSyncScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(recordingState.phase, recordingState.demonstrationId) {
        if (recordingState.phase == OperationRecordingPhase.Captured) viewModel.refresh()
    }

    LaunchedEffect(uiState.drafts.size, uiState.skillArtifacts.size, accountState.isLoggedIn) {
        skillSyncStatus = skillSyncStatusStore?.read() ?: skillSyncStatus
    }

    LaunchedEffect(appPickerVisible) {
        if (!appPickerVisible || installedApps.isNotEmpty() || installedAppsLoading) return@LaunchedEffect
        val context = applicationContext
        if (context == null) {
            installedAppsError = "应用列表暂时不可用，请重新进入页面。"
            return@LaunchedEffect
        }
        installedAppsLoading = true
        installedAppsError = null
        runCatching { InstalledLaunchableAppCatalog.load(context) }
            .onSuccess { apps ->
                installedApps = apps
                if (apps.isEmpty()) installedAppsError = "没有找到可从桌面启动的应用。"
            }
            .onFailure { error ->
                installedAppsError = "应用列表读取失败：${error.message ?: "未知错误"}"
            }
        installedAppsLoading = false
    }

    BackHandler {
        when {
            appPickerVisible -> appPickerVisible = false
            uiState.editorVisible -> viewModel.closeIntentEditor()
            recordingState.active -> Unit
            else -> onBack()
        }
    }

    if (appPickerVisible) {
        InstalledAppPickerDialog(
            apps = installedApps,
            loading = installedAppsLoading,
            error = installedAppsError,
            query = appSearchQuery,
            onQueryChange = { appSearchQuery = it.take(60) },
            onDismiss = { appPickerVisible = false },
            onSelect = { app ->
                viewModel.updateAppName(app.displayName)
                viewModel.updatePackageName(app.packageName)
                appSearchQuery = ""
                appPickerVisible = false
            },
        )
    }

    SecondaryRouteEntrance(motionIntensity = state.motionIntensity) {
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
                enabled = !recordingState.active && uiState.runningSkillId == null,
                onClick = {
                    if (uiState.editorVisible) viewModel.closeIntentEditor() else viewModel.openIntentEditor()
                },
            )
        }

        item(key = "skill-intent-editor") {
            SkillIntentEditorSlot(
                visible = uiState.editorVisible && !recordingState.active && uiState.runningSkillId == null,
                state = state,
                uiState = uiState,
                onTitleChange = viewModel::updateTitle,
                onGoalChange = viewModel::updateGoal,
                onChooseApp = { appPickerVisible = true },
                onCancel = viewModel::closeIntentEditor,
                onSave = { viewModel.createIntentDraft() },
            )
        }

        item { LearningSectionTitle("工作方式", "云端理解") }
        item { LearningFlowCard() }
        item { LearningSectionTitle("架构原则", "薄客户端") }
        item { CloudAuthorityCard() }
        item {
            OperationSkillCloudSyncCard(
                state = state,
                accountState = accountState,
                syncStatus = skillSyncStatus,
                syncing = skillSyncing,
                syncMessage = skillSyncMessage,
                localSkillCount = uiState.drafts.size,
                approvedSkillCount = uiState.drafts.count { it.status == WorkflowDraftStatus.Approved || it.status == WorkflowDraftStatus.Verified },
                reviewSkillCount = uiState.drafts.count { it.status == WorkflowDraftStatus.ReadyForReview },
                onManualSync = {
                    val repository = skillSyncRepository
                    if (repository != null && !skillSyncing) {
                        skillSyncing = true
                        skillSyncMessage = "正在同步本机和云端 Skill…"
                        skillSyncScope.launch {
                            val result = repository.syncAllVisibleAssets(OperationSkillAssetSyncReason.Manual)
                            skillSyncStatus = skillSyncStatusStore?.read() ?: skillSyncStatus
                            skillSyncMessage = when (result.source) {
                                OperationSkillAssetSyncSource.Failed -> result.errorMessage ?: "Skill 云同步失败，本机功能不受影响。"
                                OperationSkillAssetSyncSource.Skipped -> result.errorMessage ?: "Skill 云同步已跳过。"
                                OperationSkillAssetSyncSource.Network -> "同步完成：上传 ${result.uploadedCount} 个，拉取 ${result.downloadedCount} 个。"
                            }
                            skillSyncing = false
                            viewModel.refresh()
                        }
                    }
                },
            )
        }
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
                    enabled = !recordingState.active && uiState.runningSkillId == null,
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
                    runningSkillId = uiState.runningSkillId,
                    replayInputValues = uiState.replayInputValues[draft.id].orEmpty(),
                    onSelect = { viewModel.selectDraft(draft.id) },
                    onStartRecording = { viewModel.startRecording(draft.id) },
                    onFinishRecording = viewModel::finishRecording,
                    onApproveSkill = { viewModel.approveSkill(draft.id) },
                    onRunSkill = { viewModel.runSkill(draft.id) },
                    onReplayInputChange = { key, value ->
                        viewModel.updateReplayInput(draft.id, key, value)
                    },
                    onDelete = { viewModel.deleteDraft(draft.id) },
                )
            }
        }

        item { SafetyBoundaryCard() }
        }
    }
}

@Composable
private fun InstalledAppPickerDialog(
    apps: List<InstalledLaunchableApp>,
    loading: Boolean,
    error: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (InstalledLaunchableApp) -> Unit,
) {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val filteredApps = remember(apps, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.displayName.lowercase(Locale.getDefault()).contains(normalizedQuery)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF111633))
                .padding(horizontal = 16.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("选择应用", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(
                        "只显示已安装且可以从桌面打开的应用。",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("关闭", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            OperationLearningTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "搜索应用",
                singleLine = true,
                maxChars = 60,
            )

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在读取已安装应用…", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            error,
                            color = OperationLearningDanger.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("没有找到匹配的应用", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                        ) { app ->
                            InstalledAppRow(app = app, onClick = { onSelect(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledLaunchableApp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(OperationLearningViolet.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.displayName.firstOrNull()?.toString().orEmpty(),
                color = OperationLearningViolet.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = app.displayName,
            modifier = Modifier.weight(1f),
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("选择", color = OperationLearningAccent.copy(alpha = 0.68f), fontSize = 10.5.sp, fontWeight = FontWeight.Black)
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
        modifier = Modifier.fillMaxWidth(0.28f).height(40.dp),
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
        Text("操作学习", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("关闭", color = Color.White.copy(alpha = 0.58f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                "先说明最终目标并选择允许观察的应用。演示结束后，云端会解释自己学到了什么。",
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
                    !enabled -> "请先结束当前任务"
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
private fun SkillIntentEditorSlot(
    visible: Boolean,
    state: AssistantUiState,
    uiState: OperationLearningUiState,
    onTitleChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onChooseApp: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Boolean,
) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    var lastMeasuredHeight by remember { mutableStateOf(0) }
    var frozenClosingHeight by remember { mutableStateOf(0) }

    LaunchedEffect(visible) {
        progress.stop()
        if (!visible) frozenClosingHeight = lastMeasuredHeight.coerceAtLeast(frozenClosingHeight)
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) 286 else 214,
                easing = FastOutSlowInEasing,
            ),
        )
        if (visible) frozenClosingHeight = 0
    }

    val p = progress.value.coerceIn(0f, 1f)
    if (visible || p > 0.001f) {
        SubcomposeLayout(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) { constraints ->
            val visualAlpha = if (visible) {
                ((p - 0.06f) / 0.94f).coerceIn(0f, 1f)
            } else {
                (p * p).coerceIn(0f, 1f)
            }
            val placeables = subcompose("skill-intent-editor") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = visualAlpha
                            translationY = if (visible) (1f - p) * 14f else (1f - p) * -6f
                            clip = true
                        },
                ) {
                    SkillIntentEditor(
                        state = state,
                        uiState = uiState,
                        onTitleChange = onTitleChange,
                        onGoalChange = onGoalChange,
                        onChooseApp = onChooseApp,
                        onCancel = onCancel,
                        onSave = onSave,
                    )
                }
            }.map { measurable ->
                measurable.measure(constraints.copy(minHeight = 0))
            }
            val measuredHeight = placeables.maxOfOrNull { it.height } ?: 0
            if (measuredHeight > 0 && visible) lastMeasuredHeight = measuredHeight
            val stableHeight = when {
                visible -> measuredHeight.coerceAtLeast(lastMeasuredHeight)
                frozenClosingHeight > 0 -> frozenClosingHeight
                else -> lastMeasuredHeight.coerceAtLeast(measuredHeight)
            }
            val animatedHeight = (stableHeight * p).roundToInt().coerceAtLeast(0)

            layout(constraints.maxWidth, animatedHeight) {
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }
}

@Composable
private fun SkillIntentEditor(
    state: AssistantUiState,
    uiState: OperationLearningUiState,
    onTitleChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onChooseApp: () -> Unit,
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
                "描述你想教会助手的任务，再从已安装应用中选择演示对象。应用包名由系统自动处理。",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
            OperationLearningTextField(
                value = uiState.titleInput,
                onValueChange = onTitleChange,
                label = "Skill 名称",
                singleLine = true,
                maxChars = 60,
            )
            OperationLearningTextField(
                value = uiState.goalInput,
                onValueChange = onGoalChange,
                label = "想完成什么，以及哪些内容可能变化",
                singleLine = false,
                maxChars = 240,
            )
            SelectedAppField(
                appName = uiState.appNameInput,
                onClick = onChooseApp,
            )

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
private fun SelectedAppField(
    appName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(OperationLearningViolet.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = appName.firstOrNull()?.toString() ?: "应",
                color = OperationLearningViolet.copy(alpha = 0.84f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("演示应用", color = Color.White.copy(alpha = 0.40f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                text = appName.ifBlank { "点击选择已安装应用" },
                color = Color.White.copy(alpha = if (appName.isBlank()) 0.58f else 0.88f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (appName.isBlank()) "选择" else "更换",
            color = OperationLearningAccent.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun OperationLearningTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    maxChars: Int = Int.MAX_VALUE,
) {
    var fieldValue by remember(label) {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            )
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { next ->
            val cleanText = if (singleLine) {
                next.text.replace("\n", "").take(maxChars)
            } else {
                next.text.take(maxChars)
            }
            val fixed = next.copy(
                text = cleanText,
                selection = next.selection.constrainTo(cleanText.length),
            )
            fieldValue = fixed
            if (cleanText != value) onValueChange(cleanText)
        },
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

private fun TextRange.constrainTo(textLength: Int): TextRange {
    return TextRange(
        start.coerceIn(0, textLength),
        end.coerceIn(0, textLength),
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
private fun OperationSkillCloudSyncCard(
    state: AssistantUiState,
    accountState: SupabaseAccountState,
    syncStatus: OperationSkillAssetSyncStatusSnapshot,
    syncing: Boolean,
    syncMessage: String?,
    localSkillCount: Int,
    approvedSkillCount: Int,
    reviewSkillCount: Int,
    onManualSync: () -> Unit,
) {
    val loggedIn = accountState.isLoggedIn
    val title = when {
        !loggedIn -> "Skill 云同步未开启"
        syncing -> "正在同步 Skill 资产"
        syncStatus.hasError -> "Skill 云同步需要处理"
        syncStatus.hasSyncedBefore -> "Skill 云同步已接入"
        else -> "Skill 云同步待首次完成"
    }
    val subtitle = when {
        !loggedIn -> "当前 ${localSkillCount} 个 Skill 只保存在本机；登录账号后才会上传待审核 Skill 和已批准版本。"
        syncing -> "账号：${accountState.email.orEmpty().ifBlank { "已登录" }}。正在同步本机和云端 Skill 资产。"
        syncMessage != null -> syncMessage
        syncStatus.hasError -> "上次同步失败：${syncStatus.lastError}。本机 Skill 仍可正常使用。"
        syncStatus.hasSyncedBefore -> "账号：${accountState.email.orEmpty().ifBlank { "已登录" }}。上次成功同步：${formatSkillSyncTime(syncStatus.lastSuccessAtMillis)}。"
        else -> "账号：${accountState.email.orEmpty().ifBlank { "已登录" }}。点击立即同步，或在学习完成、审核批准后自动同步。"
    }
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = if (loggedIn) 0.086f else 0.072f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(OperationLearningAccent.copy(alpha = if (loggedIn) 0.060f else 0.038f))
                .padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(subtitle, color = Color.White.copy(alpha = 0.54f), fontSize = 11.5.sp, lineHeight = 17.sp)
                }
                WorkflowStatusPill(
                    text = when {
                        !loggedIn -> "本机"
                        syncing -> "同步中"
                        syncStatus.hasError -> "失败"
                        syncStatus.hasSyncedBefore -> "已同步"
                        else -> "待同步"
                    },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DraftMeta("本机 Skill", "$localSkillCount 个", Modifier.weight(1f))
                DraftMeta("已批准", "$approvedSkillCount 个", Modifier.weight(1f))
                DraftMeta("待审核", "$reviewSkillCount 个", Modifier.weight(1f))
            }
            OperationLearningActionButton(
                state = state,
                label = when {
                    !loggedIn -> "登录后同步云端 Skill"
                    syncing -> "正在同步…"
                    else -> "立即同步云端 Skill"
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = loggedIn && !syncing,
                onClick = onManualSync,
            )
            Text(
                text = "同步范围：Skill 语义、工作流安全边界和审核快照；不会上传原始演示截图、节点树、坐标脚本或运行时敏感输入。",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun WorkflowStatusPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = OperationLearningAccent.copy(alpha = 0.76f), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
    }
}

private fun formatSkillSyncTime(millis: Long): String {
    if (millis <= 0L) return "尚未成功同步"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun LearningSectionTitle(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, start = 2.dp, end = 2.dp),
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
    runningSkillId: String?,
    replayInputValues: Map<String, String>,
    onSelect: () -> Unit,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onApproveSkill: () -> Unit,
    onRunSkill: () -> Unit,
    onReplayInputChange: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val report = OperationWorkflowValidator.validate(draft, WorkflowValidationStage.RecordingIntent)
    val appLabel = draft.appScope.displayNames.firstOrNull()
        ?: draft.appScope.normalizedPackages.firstOrNull()
        ?: "未指定应用"
    val thisRecording = recordingState.active && recordingState.workflowId == draft.id
    val anotherRecording = recordingState.active && recordingState.workflowId != draft.id
    val thisRunning = runningSkillId == draft.id
    val anotherRunning = runningSkillId != null && !thisRunning
    val canStart = draft.status == WorkflowDraftStatus.Intent && report.canProceed && !recordingState.active && runningSkillId == null
    val canApprove = draft.status == WorkflowDraftStatus.ReadyForReview && skill != null && !recordingState.active && runningSkillId == null
    val canRun = draft.status in setOf(WorkflowDraftStatus.Approved, WorkflowDraftStatus.Verified) &&
        skill != null && !recordingState.active && runningSkillId == null
    val actionLabel = when {
        thisRecording -> "结束演示"
        anotherRecording -> "其他 Skill 演示中"
        thisRunning -> "运行中"
        anotherRunning -> "其他 Skill 运行中"
        draft.status == WorkflowDraftStatus.Compiling -> "云端理解中"
        canApprove -> "批准 Skill"
        draft.status == WorkflowDraftStatus.ReadyForReview -> "等待完整草稿"
        canRun -> "运行 Skill"
        draft.status == WorkflowDraftStatus.Approved -> "已批准"
        draft.status == WorkflowDraftStatus.Verified -> "已验证"
        canStart -> "开始视觉演示"
        else -> "暂不可演示"
    }

    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = if (selected || thisRecording || thisRunning) 0.088f else 0.072f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(
                    if (selected || thisRecording || thisRunning) OperationLearningViolet.copy(alpha = 0.075f)
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

            AnimatedVisibility(
                visible = selected && skill != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(durationMillis = 118, delayMillis = 18)),
                exit = fadeOut(animationSpec = tween(durationMillis = 42)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(durationMillis = 168),
                    ),
            ) {
                skill?.let { learnedSkill ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds(),
                    ) {
                        SkillUnderstandingPanel(
                            skill = learnedSkill,
                            inputValues = replayInputValues,
                            editableInputs = draft.status in setOf(WorkflowDraftStatus.Approved, WorkflowDraftStatus.Verified) && !thisRunning,
                            onInputChange = onReplayInputChange,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperationLearningActionButton(
                    state = state,
                    label = if (selected) "已展开" else "查看",
                    modifier = Modifier.weight(0.28f),
                    enabled = !recordingState.active && runningSkillId == null,
                    onClick = onSelect,
                )
                OperationLearningActionButton(
                    state = state,
                    label = actionLabel,
                    modifier = Modifier.weight(0.50f),
                    enabled = thisRecording || canStart || canApprove || canRun,
                    onClick = when {
                        thisRecording -> onFinishRecording
                        canApprove -> onApproveSkill
                        canRun -> onRunSkill
                        else -> onStartRecording
                    },
                )
                OperationLearningActionButton(
                    state = state,
                    label = "删除",
                    modifier = Modifier.weight(0.22f),
                    enabled = !recordingState.active && runningSkillId == null,
                    danger = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SkillUnderstandingPanel(
    skill: LearnedVisualSkill,
    inputValues: Map<String, String>,
    editableInputs: Boolean,
    onInputChange: (String, String) -> Unit,
) {
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
            lineHeight = 16.sp,
        )
        skill.operatingPrinciples.take(3).forEach { principle ->
            Text("• $principle", color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        if (skill.successCriteria.isNotEmpty()) {
            Text("成功：${skill.successCriteria.first()}", color = OperationLearningViolet.copy(alpha = 0.70f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        if (skill.safetyRules.isNotEmpty()) {
            Text("边界：${skill.safetyRules.first()}", color = OperationLearningDanger.copy(alpha = 0.64f), fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        if (skill.inputs.isNotEmpty()) {
            Text(
                text = if (editableInputs) "本次运行输入" else "云端识别的输入",
                color = Color.White.copy(alpha = 0.74f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
            skill.inputs.forEach { input ->
                if (input.sensitive) {
                    Text(
                        text = "${input.label}：运行时由你亲自完成，不保存也不发送给云端。",
                        color = OperationLearningDanger.copy(alpha = 0.62f),
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                } else if (editableInputs) {
                    OperationLearningTextField(
                        value = inputValues[input.key].orEmpty(),
                        onValueChange = { value -> onInputChange(input.key, value) },
                        label = if (input.required) "${input.label}（必填）" else input.label,
                        singleLine = true,
                        maxChars = 500,
                    )
                } else {
                    Text(
                        text = "• ${input.label}${input.description.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()}",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
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
                text = "只采集所选应用画面；视觉证据本机加密短期保存，Skill 生成成功后删除。密码、验证码、支付和不可逆操作仍受本地确认保护。",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
