package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val PlanPageEnterEasing = CubicBezierEasing(0.18f, 0.00f, 0.08f, 1.00f)
private val PlanPageExitEasing = CubicBezierEasing(0.40f, 0.00f, 1.00f, 1.00f)

private sealed interface PlanCenterDestination {
    val depth: Int

    data object Home : PlanCenterDestination {
        override val depth: Int = 0
    }

    data class Editor(
        val generation: Int,
        val editingId: String?,
        val draft: PlanDraft,
    ) : PlanCenterDestination {
        override val depth: Int = 1
    }

    data class Delete(
        val task: PlanTask,
    ) : PlanCenterDestination {
        override val depth: Int = 1
    }
}

@Composable
fun PlanCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onModalVisibilityChange: (Boolean) -> Unit = {},
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val planState = viewModel.uiState
    val transitionScope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()
    val pageAlpha = remember { Animatable(1f) }
    val pageOffsetX = remember { Animatable(0f) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }
    var transitionRunning by remember { mutableStateOf(false) }
    var quickTitle by remember { mutableStateOf("") }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var displayedDestination by remember {
        mutableStateOf<PlanCenterDestination>(PlanCenterDestination.Home)
    }

    fun navigateTo(target: PlanCenterDestination) {
        if (target == displayedDestination || transitionRunning) return
        val forward = target.depth > displayedDestination.depth
        val motionScale = state.motionIntensity.coerceIn(0f, 1f)

        transitionRunning = true
        transitionJob = transitionScope.launch {
            try {
                pageAlpha.stop()
                pageOffsetX.stop()

                if (motionScale <= 0.05f) {
                    displayedDestination = target
                    pageAlpha.snapTo(1f)
                    pageOffsetX.snapTo(0f)
                    return@launch
                }

                val exitOffsetPx = with(density) {
                    (if (forward) (-14).dp else 14.dp).toPx()
                } * motionScale
                val enterOffsetPx = with(density) {
                    (if (forward) 22.dp else (-18).dp).toPx()
                } * motionScale

                // 退出阶段只变换当前页面的根渲染层，不触发布局位移，
                // 子玻璃不会逐帧更新坐标或重新同步背景几何。
                coroutineScope {
                    launch {
                        pageAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 92,
                                easing = PlanPageExitEasing,
                            ),
                        )
                    }
                    launch {
                        pageOffsetX.animateTo(
                            targetValue = exitOffsetPx,
                            animationSpec = tween(
                                durationMillis = 116,
                                easing = PlanPageExitEasing,
                            ),
                        )
                    }
                }

                // 旧页面完全退出后才替换页面树，任何时刻都只有一棵玻璃页面树存在。
                displayedDestination = target
                pageAlpha.snapTo(0f)
                pageOffsetX.snapTo(enterOffsetPx)

                // 给新页面一帧完成组合和测量，再开始显示，避免首帧边布局边移动。
                withFrameNanos { }

                coroutineScope {
                    launch {
                        pageAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 158,
                                easing = PlanPageEnterEasing,
                            ),
                        )
                    }
                    launch {
                        pageOffsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 236,
                                easing = PlanPageEnterEasing,
                            ),
                        )
                    }
                }
            } finally {
                transitionRunning = false
            }
        }
    }

    fun openEditor(task: PlanTask? = null, template: PlanDraft? = null) {
        editorGeneration += 1
        val target = PlanCenterDestination.Editor(
            generation = editorGeneration,
            editingId = task?.id,
            draft = template ?: task?.toPlanDraft() ?: defaultPlanDraft(quickTitle),
        )
        quickTitle = ""
        navigateTo(target)
    }

    fun returnHome() {
        navigateTo(PlanCenterDestination.Home)
    }

    BackHandler {
        if (transitionRunning) return@BackHandler
        if (displayedDestination == PlanCenterDestination.Home) {
            onBack()
        } else {
            returnHome()
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(displayedDestination) {
        onModalVisibilityChange(displayedDestination != PlanCenterDestination.Home)
    }
    DisposableEffect(Unit) {
        onDispose {
            transitionJob?.cancel()
            onModalVisibilityChange(false)
        }
    }

    val transitionBlocker = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = pageOffsetX.value
                    alpha = pageAlpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            when (val page = displayedDestination) {
                PlanCenterDestination.Home -> {
                    PlanCenterHomePage(
                        state = state,
                        listState = homeListState,
                        quickTitle = quickTitle,
                        activeCount = planState.activeCount,
                        exactAlarmReady = planState.exactAlarmReady,
                        filter = planState.filter,
                        tasks = planState.tasks,
                        visibleTasks = planState.visibleTasks,
                        onBack = onBack,
                        onQuickTitleChange = { quickTitle = it.take(80) },
                        onOpenEditor = { task, template -> openEditor(task, template) },
                        onRequestExactAlarm = {
                            if (!viewModel.requestExactAlarmAccess()) {
                                Toast.makeText(
                                    context,
                                    "当前系统没有可用的精确闹钟设置页面",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onFilterChange = viewModel::setFilter,
                        onToggleTask = { task, enabled ->
                            val result = viewModel.toggleTask(task.id, enabled)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        },
                        onDeleteTask = { task ->
                            navigateTo(PlanCenterDestination.Delete(task))
                        },
                    )
                }

                is PlanCenterDestination.Editor -> {
                    key(page.generation) {
                        PlanEditorPage(
                            state = state,
                            initial = page.draft,
                            editing = page.editingId != null,
                            exactAlarmReady = planState.exactAlarmReady,
                            onBack = ::returnHome,
                            onSave = { draft ->
                                val result = viewModel.saveTask(page.editingId, draft)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                if (result.ok) returnHome()
                            },
                        )
                    }
                }

                is PlanCenterDestination.Delete -> {
                    PlanDeletePage(
                        state = state,
                        task = page.task,
                        onBack = ::returnHome,
                        onConfirm = {
                            val result = viewModel.deleteTask(page.task.id)
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            if (result.ok) returnHome()
                        },
                    )
                }
            }
        }

        if (transitionRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = transitionBlocker,
                        indication = null,
                        onClick = {},
                    ),
            )
        }
    }
}

@Composable
private fun PlanCenterHomePage(
    state: AssistantUiState,
    listState: LazyListState,
    quickTitle: String,
    activeCount: Int,
    exactAlarmReady: Boolean,
    filter: PlanTaskFilter,
    tasks: List<PlanTask>,
    visibleTasks: List<PlanTask>,
    onBack: () -> Unit,
    onQuickTitleChange: (String) -> Unit,
    onOpenEditor: (PlanTask?, PlanDraft?) -> Unit,
    onRequestExactAlarm: () -> Unit,
    onFilterChange: (PlanTaskFilter) -> Unit,
    onToggleTask: (PlanTask, Boolean) -> Unit,
    onDeleteTask: (PlanTask) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PlanHeader(
                state = state,
                activeCount = activeCount,
                onBack = onBack,
            )
        }
        item {
            PlanQuickComposer(
                state = state,
                value = quickTitle,
                onValueChange = onQuickTitleChange,
                onCreate = { onOpenEditor(null, null) },
            )
        }
        item {
            PlanTemplateGrid(state = state) { template ->
                onOpenEditor(null, template)
            }
        }
        if (!exactAlarmReady) {
            item {
                PlanInfoBanner(
                    state = state,
                    onAction = onRequestExactAlarm,
                )
            }
        }
        item {
            PlanFilterBar(
                state = state,
                selected = filter,
                onSelect = onFilterChange,
            )
        }
        item { PlanSectionTitle(filter, visibleTasks.size) }

        if (visibleTasks.isEmpty()) {
            item {
                PlanEmptyCard(
                    state = state,
                    filtered = tasks.isNotEmpty(),
                    onCreate = { onOpenEditor(null, null) },
                )
            }
        } else {
            items(visibleTasks, key = { it.id }) { task ->
                PlanTaskCard(
                    state = state,
                    task = task,
                    onEdit = { onOpenEditor(task, null) },
                    onDelete = { onDeleteTask(task) },
                    onToggle = { enabled -> onToggleTask(task, enabled) },
                )
            }
        }
    }
}
