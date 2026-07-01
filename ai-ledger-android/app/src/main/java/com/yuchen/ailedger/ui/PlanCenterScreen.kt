package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask

@Composable
fun PlanCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onModalVisibilityChange: (Boolean) -> Unit = {},
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val planState = viewModel.uiState
    var quickTitle by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editorDraft by remember { mutableStateOf<PlanDraft?>(null) }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<PlanTask?>(null) }
    val modalVisible = editorDraft != null || deleteCandidate != null

    fun openEditor(task: PlanTask? = null, template: PlanDraft? = null) {
        editingId = task?.id
        editorDraft = template ?: task?.toPlanDraft() ?: defaultPlanDraft(quickTitle)
        deleteCandidate = null
        quickTitle = ""
        editorGeneration += 1
    }

    fun closeModal() {
        editorDraft = null
        editingId = null
        deleteCandidate = null
    }

    BackHandler(enabled = modalVisible, onBack = ::closeModal)
    BackHandler(enabled = !modalVisible, onBack = onBack)
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(modalVisible) { onModalVisibilityChange(modalVisible) }
    DisposableEffect(Unit) {
        onDispose { onModalVisibilityChange(false) }
    }

    val pageBlur by animateDpAsState(
        targetValue = if (modalVisible) 14.dp else 0.dp,
        label = "plan-page-blur",
    )
    val pageAlpha by animateFloatAsState(
        targetValue = if (modalVisible) 0.40f else 1f,
        label = "plan-page-alpha",
    )
    val pageScale by animateFloatAsState(
        targetValue = if (modalVisible) 0.985f else 1f,
        label = "plan-page-scale",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blur(pageBlur)
                .graphicsLayer {
                    alpha = pageAlpha
                    scaleX = pageScale
                    scaleY = pageScale
                },
            contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PlanHeader(
                    state = state,
                    activeCount = planState.activeCount,
                    onBack = onBack,
                )
            }
            item {
                PlanQuickComposer(
                    state = state,
                    value = quickTitle,
                    onValueChange = { quickTitle = it.take(80) },
                    onCreate = { openEditor() },
                )
            }
            item {
                PlanTemplateGrid(state = state) { template ->
                    openEditor(template = template)
                }
            }
            if (!planState.exactAlarmReady) {
                item {
                    PlanInfoBanner(state = state) {
                        if (!viewModel.requestExactAlarmAccess()) {
                            Toast.makeText(
                                context,
                                "当前系统没有可用的精确闹钟设置页面",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
            item {
                PlanFilterBar(
                    state = state,
                    selected = planState.filter,
                    onSelect = viewModel::setFilter,
                )
            }
            item { PlanSectionTitle(planState.filter, planState.visibleTasks.size) }

            if (planState.visibleTasks.isEmpty()) {
                item {
                    PlanEmptyCard(
                        state = state,
                        filtered = planState.tasks.isNotEmpty(),
                        onCreate = { openEditor() },
                    )
                }
            } else {
                items(planState.visibleTasks, key = { it.id }) { task ->
                    PlanTaskCard(
                        state = state,
                        task = task,
                        onEdit = { openEditor(task = task) },
                        onDelete = {
                            editorDraft = null
                            editingId = null
                            deleteCandidate = task
                        },
                        onToggle = { enabled ->
                            val result = viewModel.toggleTask(task.id, enabled)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }

        if (modalVisible) {
            val backdropBlocker = remember { MutableInteractionSource() }
            val panelBlocker = remember { MutableInteractionSource() }
            FrostInfoGlassPanel(
                radius = 0f,
                backdropAlpha = 1f,
                frostAlpha = 0.035f,
                dimAlpha = 0.38f,
                modifier = Modifier
                    .zIndex(100f)
                    .fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = backdropBlocker,
                            indication = null,
                            onClick = ::closeModal,
                        ),
                )
            }

            editorDraft?.let { initial ->
                key(editorGeneration) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .zIndex(101f)
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.86f)
                            .clickable(
                                interactionSource = panelBlocker,
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        PlanEditorPanel(
                            state = state,
                            initial = initial,
                            editing = editingId != null,
                            exactAlarmReady = planState.exactAlarmReady,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = ::closeModal,
                            onSave = { draft ->
                                val result = viewModel.saveTask(editingId, draft)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                if (result.ok) closeModal()
                            },
                        )
                    }
                }
            }

            deleteCandidate?.let { task ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(101f)
                        .fillMaxWidth(0.86f)
                        .clickable(
                            interactionSource = panelBlocker,
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    PlanDeletePanel(
                        state = state,
                        task = task,
                        modifier = Modifier.fillMaxWidth(),
                        onDismiss = ::closeModal,
                        onConfirm = {
                            val result = viewModel.deleteTask(task.id)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            closeModal()
                        },
                    )
                }
            }
        }
    }
}
