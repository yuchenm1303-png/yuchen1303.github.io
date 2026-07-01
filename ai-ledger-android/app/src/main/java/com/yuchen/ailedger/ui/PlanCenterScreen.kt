package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PlanCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onModalVisibilityChange: (Boolean) -> Unit = {},
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val planState = viewModel.uiState
    val animationScope = rememberCoroutineScope()
    val modalProgress = remember { Animatable(0f) }
    var modalAnimationJob by remember { mutableStateOf<Job?>(null) }
    var quickTitle by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editorDraft by remember { mutableStateOf<PlanDraft?>(null) }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<PlanTask?>(null) }
    val modalMounted = editorDraft != null || deleteCandidate != null
    val motionEnabled = state.motionIntensity > 0.05f

    fun launchModalEntrance() {
        modalAnimationJob?.cancel()
        modalAnimationJob = animationScope.launch {
            modalProgress.stop()
            modalProgress.snapTo(0f)
            if (motionEnabled) {
                modalProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            } else {
                modalProgress.snapTo(1f)
            }
        }
    }

    fun openEditor(task: PlanTask? = null, template: PlanDraft? = null) {
        editingId = task?.id
        editorDraft = template ?: task?.toPlanDraft() ?: defaultPlanDraft(quickTitle)
        deleteCandidate = null
        quickTitle = ""
        editorGeneration += 1
        launchModalEntrance()
    }

    fun openDelete(task: PlanTask) {
        editingId = null
        editorDraft = null
        deleteCandidate = task
        launchModalEntrance()
    }

    fun closeModal() {
        if (!modalMounted) return
        modalAnimationJob?.cancel()
        modalAnimationJob = animationScope.launch {
            modalProgress.stop()
            if (motionEnabled) {
                modalProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 245,
                        easing = FastOutSlowInEasing,
                    ),
                )
            } else {
                modalProgress.snapTo(0f)
            }
            editorDraft = null
            editingId = null
            deleteCandidate = null
        }
    }

    BackHandler(enabled = modalMounted, onBack = ::closeModal)
    BackHandler(enabled = !modalMounted, onBack = onBack)
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(modalMounted) { onModalVisibilityChange(modalMounted) }
    DisposableEffect(Unit) {
        onDispose {
            modalAnimationJob?.cancel()
            onModalVisibilityChange(false)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 弹窗存在期间直接卸载首页玻璃和文字，而不是依赖遮罩或 zIndex 压制。
        // 这样父级玻璃绘制层不会越过弹窗，把底下的文字重新画到主体之上。
        if (!modalMounted) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            onDelete = { openDelete(task) },
                            onToggle = { enabled ->
                                val result = viewModel.toggleTask(task.id, enabled)
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }

        if (modalMounted) {
            val backdropBlocker = remember { MutableInteractionSource() }
            val panelBlocker = remember { MutableInteractionSource() }
            val rawProgress = modalProgress.value.coerceIn(0f, 1f)
            val easedProgress = FastOutSlowInEasing.transform(rawProgress)
            val panelCorner = (30f + (1f - easedProgress) * 90f).dp

            // 透明命中层只负责阻止点击穿透，不绘制黑色或额外模糊背景。
            Box(
                modifier = Modifier
                    .zIndex(100f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = backdropBlocker,
                        indication = null,
                        onClick = ::closeModal,
                    ),
            )

            editorDraft?.let { initial ->
                key(editorGeneration) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .zIndex(101f)
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.80f)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.50f, 0.04f)
                                alpha = (rawProgress * 1.12f).coerceIn(0f, 1f)
                                scaleX = 0.86f + easedProgress * 0.14f
                                scaleY = 0.10f + easedProgress * 0.90f
                                translationY = -(1f - easedProgress) * 112.dp.toPx()
                            }
                            .clip(RoundedCornerShape(panelCorner))
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
                            revealProgress = rawProgress,
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
                        .fillMaxWidth(0.84f)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.50f, 0.20f)
                            alpha = (rawProgress * 1.12f).coerceIn(0f, 1f)
                            scaleX = 0.84f + easedProgress * 0.16f
                            scaleY = 0.18f + easedProgress * 0.82f
                            translationY = -(1f - easedProgress) * 72.dp.toPx()
                        }
                        .clip(RoundedCornerShape(panelCorner))
                        .clickable(
                            interactionSource = panelBlocker,
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    PlanDeletePanel(
                        state = state,
                        task = task,
                        revealProgress = rawProgress,
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
