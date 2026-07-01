package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanTask

@Composable
fun PlanCenterScreen(
    onBack: () -> Unit,
    viewModel: PlanCenterViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 94.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PlanHeader(state.activeCount, onBack) }
        item {
            PlanQuickComposer(
                value = quickTitle,
                onValueChange = { quickTitle = it.take(80) },
                onCreate = { openEditor() },
            )
        }
        item {
            PlanTemplateStrip { template ->
                openEditor(template = template)
            }
        }
        if (!state.exactAlarmReady) {
            item {
                PlanInfoBanner {
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
        item { PlanFilterBar(state.filter, viewModel::setFilter) }
        item { PlanSectionTitle(state.filter, state.visibleTasks.size) }

        if (state.visibleTasks.isEmpty()) {
            item {
                PlanEmptyCard(
                    filtered = state.tasks.isNotEmpty(),
                    onCreate = { openEditor() },
                )
            }
        } else {
            items(state.visibleTasks, key = { it.id }) { task ->
                PlanTaskCard(
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
                initial = initial,
                editing = editingId != null,
                exactAlarmReady = state.exactAlarmReady,
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
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = Color(0xFF101A3D),
            tonalElevation = 0.dp,
            title = {
                Text(
                    "删除计划",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Text(
                    "确定删除“${task.title}”吗？删除后不会再触发提醒。",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val result = viewModel.deleteTask(task.id)
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        deleteCandidate = null
                    },
                ) {
                    Text("删除", color = Color(0xFFFF9A9A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("取消", color = Color.White.copy(alpha = 0.62f))
                }
            },
        )
    }
}
