package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.SubcomposeSlotReusePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import kotlinx.coroutines.delay

private enum class PlanPageSlot {
    Home,
    Editor,
    Delete,
}

private sealed interface PlanCenterDestination {
    data object Home : PlanCenterDestination

    data class Editor(
        val generation: Int,
        val editingId: String?,
        val draft: PlanDraft,
    ) : PlanCenterDestination

    data class Delete(
        val task: PlanTask,
    ) : PlanCenterDestination
}

private val PlanCenterDestination.slot: PlanPageSlot
    get() = when (this) {
        PlanCenterDestination.Home -> PlanPageSlot.Home
        is PlanCenterDestination.Editor -> PlanPageSlot.Editor
        is PlanCenterDestination.Delete -> PlanPageSlot.Delete
    }

@Composable
fun PlanCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onModalVisibilityChange: (Boolean) -> Unit = {},
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val planState = viewModel.uiState
    val homeListState = rememberLazyListState()
    val pageHostState = remember {
        SubcomposeLayoutState(SubcomposeSlotReusePolicy(maxSlotsToRetainForReuse = 3))
    }
    val warmEditorDraft = remember { defaultPlanDraft("") }
    val warmEditorState = remember(
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
    ) { state }
    val warmHandleHolder = remember {
        arrayOfNulls<SubcomposeLayoutState.PrecomposedSlotHandle>(1)
    }

    var quickTitle by remember { mutableStateOf("") }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var displayedDestination by remember {
        mutableStateOf<PlanCenterDestination>(PlanCenterDestination.Home)
    }
    var pageDirection by remember {
        mutableStateOf(SecondaryMotionDirection.Forward)
    }
    var pageMotionType by remember {
        mutableStateOf(SecondaryMotionType.Capsule)
    }

    LaunchedEffect(pageHostState) {
        withFrameNanos { }
        delay(96)
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
        if (target == displayedDestination) return

        pageDirection = if (target == PlanCenterDestination.Home) {
            SecondaryMotionDirection.Backward
        } else {
            SecondaryMotionDirection.Forward
        }
        pageMotionType = when (target) {
            PlanCenterDestination.Home -> SecondaryMotionType.Push
            is PlanCenterDestination.Editor -> SecondaryMotionType.Capsule
            is PlanCenterDestination.Delete -> SecondaryMotionType.Modal
        }
        displayedDestination = target
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
            warmHandleHolder[0]?.dispose()
            warmHandleHolder[0] = null
            onModalVisibilityChange(false)
        }
    }

    SecondaryRouteEntrance(
        motionIntensity = state.motionIntensity,
        motionType = SecondaryMotionType.Capsule,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            SecondaryMotionContainer(
                transitionKey = displayedDestination,
                motionIntensity = state.motionIntensity,
                motionType = pageMotionType,
                direction = pageDirection,
                modifier = Modifier.fillMaxSize(),
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
            }
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
        item("plan_header") {
            SecondaryStageReveal(
                role = SecondaryStageRole.Header,
                motionIntensity = state.motionIntensity,
            ) {
                PlanHeader(
                    state = state,
                    activeCount = activeCount,
                    onBack = onBack,
                )
            }
        }
        item("plan_quick_composer") {
            SecondaryStageReveal(
                role = SecondaryStageRole.Capsule,
                motionIntensity = state.motionIntensity,
            ) {
                PlanQuickComposer(
                    state = state,
                    value = quickTitle,
                    onValueChange = onQuickTitleChange,
                    onCreate = { onOpenEditor(null, null) },
                )
            }
        }
        item("plan_templates") {
            SecondaryStageReveal(
                role = SecondaryStageRole.Primary,
                motionIntensity = state.motionIntensity,
            ) {
                PlanTemplateGrid(state = state) { template ->
                    onOpenEditor(null, template)
                }
            }
        }
        if (!exactAlarmReady) {
            item("plan_alarm_banner") {
                SecondaryStageReveal(
                    role = SecondaryStageRole.Supporting,
                    index = 0,
                    motionIntensity = state.motionIntensity,
                ) {
                    PlanInfoBanner(
                        state = state,
                        onAction = onRequestExactAlarm,
                    )
                }
            }
        }
        item("plan_filter") {
            SecondaryStageReveal(
                role = SecondaryStageRole.Supporting,
                index = if (exactAlarmReady) 0 else 1,
                motionIntensity = state.motionIntensity,
            ) {
                PlanFilterBar(
                    state = state,
                    selected = filter,
                    onSelect = onFilterChange,
                )
            }
        }
        item("plan_section_title") {
            SecondaryStageReveal(
                role = SecondaryStageRole.List,
                motionIntensity = state.motionIntensity,
            ) {
                PlanSectionTitle(filter, visibleTasks.size)
            }
        }

        if (visibleTasks.isEmpty()) {
            item("plan_empty_${filter.name}") {
                SecondaryStageReveal(
                    role = SecondaryStageRole.List,
                    index = 1,
                    motionIntensity = state.motionIntensity,
                    transitionKey = filter,
                ) {
                    PlanEmptyCard(
                        state = state,
                        filtered = tasks.isNotEmpty(),
                        onCreate = { onOpenEditor(null, null) },
                    )
                }
            }
        } else {
            itemsIndexed(visibleTasks, key = { _, task -> task.id }) { index, task ->
                val taskContent: @Composable () -> Unit = {
                    PlanTaskCard(
                        state = state,
                        task = task,
                        onEdit = { onOpenEditor(task, null) },
                        onDelete = { onDeleteTask(task) },
                        onToggle = { enabled -> onToggleTask(task, enabled) },
                    )
                }
                if (index < 3) {
                    SecondaryStageReveal(
                        role = SecondaryStageRole.List,
                        index = index,
                        motionIntensity = state.motionIntensity,
                        transitionKey = filter,
                        content = taskContent,
                    )
                } else {
                    taskContent()
                }
            }
        }
    }
}
