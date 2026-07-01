package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask

@Composable
fun PlanCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val planState = viewModel.uiState
    var quickTitle by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editorDraft by remember { mutableStateOf<PlanDraft?>(null) }
    var editorGeneration by remember { mutableIntStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<PlanTask?>(null) }

    fun openEditor(task: PlanTask? = null, template: PlanDraft? = null) {
        editingId = task?.id
        editorDraft = template ?: task?.toPlanDraft() ?: defaultPlanDraft(quickTitle)
        quickTitle = ""
        editorGeneration += 1
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            PlanTemplateStrip(state = state) { template ->
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
                    onDelete = { deleteCandidate = task },
                    onToggle = { enabled ->
                        val result = viewModel.toggleTask(task.id, enabled)
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    editorDraft?.let { initial ->
        key(editorGeneration) {
            PlanEditorDialog(
                state = state,
                initial = initial,
                editing = editingId != null,
                exactAlarmReady = planState.exactAlarmReady,
                onDismiss = {
                    editorDraft = null
                    editingId = null
                },
                onSave = { draft ->
                    val result = viewModel.saveTask(editingId, draft)
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    if (result.ok) {
                        editorDraft = null
                        editingId = null
                    }
                },
            )
        }
    }

    deleteCandidate?.let { task ->
        PlanDeleteDialog(
            state = state,
            task = task,
            onDismiss = { deleteCandidate = null },
            onConfirm = {
                val result = viewModel.deleteTask(task.id)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                deleteCandidate = null
            },
        )
    }
}
