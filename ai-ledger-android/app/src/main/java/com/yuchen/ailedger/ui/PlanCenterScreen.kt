package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.SubcomposeSlotReusePolicy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PlanWipeEnterEasing = CubicBezierEasing(0.22f, 0.00f, 0.08f, 1.00f)
private val PlanWipeExitEasing = CubicBezierEasing(0.34f, 0.00f, 0.76f, 1.00f)

private enum class PlanPageSlot {
    Home,
    Editor,
    Delete,
}

private sealed interface PlanCenterDestination {
    val depth: Int
    val slot: PlanPageSlot

    data object Home : PlanCenterDestination {
        override val depth: Int = 0
        override val slot: PlanPageSlot = PlanPageSlot.Home
    }

    data class Editor(
        val generation: Int,
        val editingId: String?,
        val draft: PlanDraft,
    ) : PlanCenterDestination {
        override val depth: Int = 1
        override val slot: PlanPageSlot = PlanPageSlot.Editor
    }

    data class Delete(
        val task: PlanTask,
    ) : PlanCenterDestination {
        override val depth: Int = 1
        override val slot: PlanPageSlot = PlanPageSlot.Delete
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
    val pageHostState = remember {
        SubcomposeLayoutState(SubcomposeSlotReusePolicy(maxSlotsToRetainForReuse = 3))
    }
    val wipeOffsetX = remember { Animatable(0f) }
    val warmEditorDraft = remember { defaultPlanDraft("") }
    val warmEditorState = remember(
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
    ) { state }
    val warmHandleHolder = remember {
        arrayOfNulls<SubcomposeLayoutState.PrecomposedSlotHandle>(1)
    }

    var transitionJob by remember { mutableStateOf<Job?>(null) }
    var transitionRunning by remember { mutableStateOf(false) }
    var wipeVisible by remember { mutableStateOf(false) }
    var wipeForward by remember { mutableStateOf(true) }
    var hostWidthPx by remember { mutableStateOf(0f) }
    var quickTitle by remember { mutableStateOf("") }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var displayedDestination by remember {
        mutableStateOf<PlanCenterDestination>(PlanCenterDestination.Home)
    }

    LaunchedEffect(pageHostState) {
        // 首页稳定后再预组合编辑页。预组合不测量、不放置，也不会绘制第二套页面，
        // 但会提前建立表单、状态和玻璃节点，消除点击后的首次组合尖峰。
        withFrameNanos { }
        delay(320)
        if (warmHandleHolder[0] == null) {
            warmHandleHolder[0] = pageHostState.precompose(PlanPageSlot.Editor) {
                PlanEditorPage(
                    state = warmEditorState,
                    initial = warmEditorDraft,
                    editing = false,
                    exactAlarmReady = true,
                    onBack = {},
                    onSave = {},
                )
            }
        }
    }

    fun navigateTo(target: PlanCenterDestination) {
        if (target == displayedDestination || transitionRunning) return
        val forward = target.depth > displayedDestination.depth
        val motionScale = state.motionIntensity.coerceIn(0f, 1f)

        transitionRunning = true
        transitionJob = transitionScope.launch {
            try {
                wipeOffsetX.stop()

                if (motionScale <= 0.05f) {
                    displayedDestination = target
                    wipeVisible = false
                    return@launch
                }

                val widthPx = hostWidthPx.takeIf { it > 1f }
                    ?: with(density) { 360.dp.toPx() }
                val startOffset = if (forward) widthPx else -widthPx
                val endOffset = -startOffset

                wipeForward = forward
                wipeVisible = true
                wipeOffsetX.snapTo(startOffset)

                // 只移动一张不透明遮片，首页和编辑页的玻璃卡片完全静止。
                wipeOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 132,
                        easing = PlanWipeEnterEasing,
                    ),
                )

                // 在遮片完全覆盖时切换到已经预组合/保留的页面槽。
                displayedDestination = target
                withFrameNanos { }
                withFrameNanos { }

                wipeOffsetX.animateTo(
                    targetValue = endOffset,
                    animationSpec = tween(
                        durationMillis = 178,
                        easing = PlanWipeExitEasing,
                    ),
                )
                wipeVisible = false
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
    DisposableEffect(pageHostState) {
        onDispose {
            transitionJob?.cancel()
            warmHandleHolder[0]?.dispose()
            warmHandleHolder[0] = null
            onModalVisibilityChange(false)
        }
    }

    val transitionBlocker = remember { MutableInteractionSource() }
    val wipeBrush = remember(wipeForward) {
        if (wipeForward) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF11234A),
                    Color(0xFF071128),
                    Color(0xFF050B1D),
                    Color(0xFF111839),
                ),
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF111839),
                    Color(0xFF050B1D),
                    Color(0xFF071128),
                    Color(0xFF11234A),
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { hostWidthPx = it.width.toFloat() },
    ) {
        PlanRetainedPageHost(
            state = pageHostState,
            destination = displayedDestination,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
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

        if (wipeVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = wipeOffsetX.value }
                    .background(wipeBrush),
            )
        }
    }
}

@Composable
private fun PlanRetainedPageHost(
    state: SubcomposeLayoutState,
    destination: PlanCenterDestination,
    modifier: Modifier = Modifier,
    content: @Composable (PlanCenterDestination) -> Unit,
) {
    SubcomposeLayout(
        state = state,
        modifier = modifier,
    ) { constraints ->
        val placeables = subcompose(destination.slot) {
            content(destination)
        }.map { measurable ->
            measurable.measure(constraints)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { placeable ->
                placeable.placeRelative(0, 0)
            }
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
