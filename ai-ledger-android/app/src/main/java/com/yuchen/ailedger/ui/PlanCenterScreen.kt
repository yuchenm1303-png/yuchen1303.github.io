package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask

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
    val planState = viewModel.uiState
    var quickTitle by remember { mutableStateOf("") }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var destination by remember {
        mutableStateOf<PlanCenterDestination>(PlanCenterDestination.Home)
    }

    fun openEditor(task: PlanTask? = null, template: PlanDraft? = null) {
        editorGeneration += 1
        destination = PlanCenterDestination.Editor(
            generation = editorGeneration,
            editingId = task?.id,
            draft = template ?: task?.toPlanDraft() ?: defaultPlanDraft(quickTitle),
        )
        quickTitle = ""
    }

    fun returnHome() {
        destination = PlanCenterDestination.Home
    }

    BackHandler {
        if (destination == PlanCenterDestination.Home) {
            onBack()
        } else {
            returnHome()
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(destination) {
        onModalVisibilityChange(destination != PlanCenterDestination.Home)
    }
    DisposableEffect(Unit) {
        onDispose { onModalVisibilityChange(false) }
    }

    AnimatedContent(
        targetState = destination,
        modifier = Modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            planPageTransform(
                forward = targetState.depth > initialState.depth,
                motionEnabled = state.motionIntensity > 0.05f,
            )
        },
        contentKey = { target ->
            when (target) {
                PlanCenterDestination.Home -> "plan-home"
                is PlanCenterDestination.Editor -> "plan-editor-${target.generation}"
                is PlanCenterDestination.Delete -> "plan-delete-${target.task.id}"
            }
        },
        label = "plan-page-transition",
    ) { page ->
        when (page) {
            PlanCenterDestination.Home -> {
                PlanCenterHomePage(
                    state = state,
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
                        destination = PlanCenterDestination.Delete(task)
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
}

@Composable
private fun PlanCenterHomePage(
    state: AssistantUiState,
    quickTitle: String,
    activeCount: Int,
    exactAlarmReady: Boolean,
    filter: com.yuchen.ailedger.model.PlanTaskFilter,
    tasks: List<PlanTask>,
    visibleTasks: List<PlanTask>,
    onBack: () -> Unit,
    onQuickTitleChange: (String) -> Unit,
    onOpenEditor: (PlanTask?, PlanDraft?) -> Unit,
    onRequestExactAlarm: () -> Unit,
    onFilterChange: (com.yuchen.ailedger.model.PlanTaskFilter) -> Unit,
    onToggleTask: (PlanTask, Boolean) -> Unit,
    onDeleteTask: (PlanTask) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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

private fun planPageTransform(
    forward: Boolean,
    motionEnabled: Boolean,
): ContentTransform {
    if (!motionEnabled) {
        return fadeIn(animationSpec = tween(1)) togetherWith
            fadeOut(animationSpec = tween(1))
    }

    return if (forward) {
        (
            slideInHorizontally(
                animationSpec = tween(
                    durationMillis = 350,
                    delayMillis = 55,
                    easing = PlanPageEnterEasing,
                ),
                initialOffsetX = { fullWidth -> fullWidth / 4 },
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 205,
                    delayMillis = 85,
                    easing = PlanPageEnterEasing,
                ),
            )
            ) togetherWith (
            slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = 185,
                    easing = PlanPageExitEasing,
                ),
                targetOffsetX = { fullWidth -> -fullWidth / 10 },
            ) + fadeOut(
                animationSpec = tween(durationMillis = 120),
            )
            )
    } else {
        (
            slideInHorizontally(
                animationSpec = tween(
                    durationMillis = 330,
                    delayMillis = 45,
                    easing = PlanPageEnterEasing,
                ),
                initialOffsetX = { fullWidth -> -fullWidth / 7 },
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 190,
                    delayMillis = 75,
                    easing = PlanPageEnterEasing,
                ),
            )
            ) togetherWith (
            slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = 210,
                    easing = PlanPageExitEasing,
                ),
                targetOffsetX = { fullWidth -> fullWidth / 4 },
            ) + fadeOut(
                animationSpec = tween(durationMillis = 125),
            )
            )
    }
}
