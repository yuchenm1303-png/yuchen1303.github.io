package com.yuchen.ailedger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import com.yuchen.ailedger.model.PlanTaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val planDateFormat = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
private val planTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val planNextFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

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
        editorDraft = template ?: task?.let {
            PlanDraft(
                title = it.title,
                note = it.note,
                type = it.type,
                repeatMode = it.repeatMode,
                scheduledAtMillis = it.scheduledAtMillis,
            )
        } ?: PlanDraft(
            title = quickTitle.trim(),
            scheduledAtMillis = ZonedDateTime.now()
                .plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli(),
        )
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
        item { PlanHeader(activeCount = state.activeCount, onBack = onBack) }
        item {
            PlanQuickComposer(
                value = quickTitle,
                onValueChange = { quickTitle = it.take(80) },
                onCreate = { openEditor() },
            )
        }
        item { PlanTemplateStrip(onSelect = { openEditor(template = it) }) }
        if (!state.exactAlarmReady) {
            item {
                PlanInfoBanner(
                    title = "精确闹钟尚未授权",
                    detail = "普通提醒仍可运行，但系统可能稍晚触发。",
                    action = "去授权",
                    onAction = {
                        if (!viewModel.requestExactAlarmAccess()) {
                            Toast.makeText(context, "当前系统没有可用的授权页面", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
        item {
            PlanFilterBar(
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (state.filter) {
                        PlanTaskFilter.All -> "全部计划"
                        PlanTaskFilter.Active -> "活动计划"
                        PlanTaskFilter.Paused -> "暂停与已完成"
                    },
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.visibleTasks.size} 项",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 10.sp,
                )
            }
        }
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
            title = { PlanDialogTitle("删除计划") },
            text = {
                Text(
                    text = "确定删除“${task.title}”吗？删除后不会再触发提醒。",
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

@Composable
private fun PlanHeader(activeCount: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp).clickable(onClick = onBack),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.075f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Light)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("计划", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(
                "安排提醒、闹钟与周期任务",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 11.5.sp,
            )
        }
        PlanTag("$activeCount 个活动", Color(0xFF8DF9EA))
    }
}

@Composable
private fun PlanQuickComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    PlanGlassCard(radius = 27.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f).height(46.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        "安排一个新计划…",
                        color = Color.White.copy(alpha = 0.43f),
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(Color(0xFFB7FFF4)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape)
                    .background(Color(0xFF7654D8))
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) {
                Text("→", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun PlanTemplateStrip(onSelect: (PlanDraft) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlanTemplateChip("明早 7:00", "起床闹钟") {
            onSelect(
                PlanDraft(
                    title = "起床",
                    note = "新的一天开始了",
                    type = PlanTaskType.Alarm,
                    repeatMode = PlanRepeatMode.Once,
                    scheduledAtMillis = tomorrowAt(7, 0),
                ),
            )
        }
        PlanTemplateChip("今晚 22:30", "学习提醒") {
            onSelect(
                PlanDraft(
                    title = "复习今天的内容",
                    note = "完成今日学习计划",
                    type = PlanTaskType.Reminder,
                    repeatMode = PlanRepeatMode.Once,
                    scheduledAtMillis = nextAt(22, 30),
                ),
            )
        }
        PlanTemplateChip("工作日 7:30", "上课闹钟") {
            onSelect(
                PlanDraft(
                    title = "准备上课",
                    type = PlanTaskType.Alarm,
                    repeatMode = PlanRepeatMode.Weekdays,
                    scheduledAtMillis = nextAt(7, 30),
                ),
            )
        }
        PlanTemplateChip("每天 20:00", "每日复盘") {
            onSelect(
                PlanDraft(
                    title = "整理今日事项",
                    note = "回顾完成情况并安排明天",
                    type = PlanTaskType.Reminder,
                    repeatMode = PlanRepeatMode.Daily,
                    scheduledAtMillis = nextAt(20, 0),
                ),
            )
        }
    }
}

@Composable
private fun PlanTemplateChip(top: String, bottom: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text(top, color = Color(0xFFB7FFF4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(bottom, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlanInfoBanner(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    PlanGlassCard(radius = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFD38A).copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("准", color = Color(0xFFFFDFA8), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(detail, color = Color.White.copy(alpha = 0.48f), fontSize = 9.5.sp)
            }
            TextButton(onClick = onAction) {
                Text(action, color = Color(0xFFFFDFA8), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PlanFilterBar(
    selected: PlanTaskFilter,
    onSelect: (PlanTaskFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanTaskFilter.entries.forEach { filter ->
            val active = selected == filter
            Surface(
                modifier = Modifier.clickable { onSelect(filter) },
                shape = RoundedCornerShape(999.dp),
                color = if (active) {
                    Color(0xFF8DF9EA).copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                },
                border = BorderStroke(
                    1.dp,
                    if (active) Color(0xFF8DF9EA).copy(alpha = 0.28f)
                    else Color.White.copy(alpha = 0.08f),
                ),
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (active) Color(0xFFB7FFF4) else Color.White.copy(alpha = 0.60f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun PlanEmptyCard(filtered: Boolean, onCreate: () -> Unit) {
    PlanGlassCard(radius = 25.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("计", color = Color(0xFFC8BCFF), fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                text = if (filtered) "这里暂时没有计划" else "还没有安排计划",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (filtered) "切换筛选条件查看其他计划" else "创建提醒、闹钟或周期任务。",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.5.sp,
            )
            if (!filtered) {
                TextButton(onClick = onCreate) {
                    Text("创建第一个计划", color = Color(0xFFB7FFF4))
                }
            }
        }
    }
}

@Composable
private fun PlanTaskCard(
    task: PlanTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PlanGlassCard(
        radius = 24.dp,
        modifier = Modifier.clickable(onClick = onEdit),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val accent = if (task.type == PlanTaskType.Alarm) Color(0xFFC8BCFF) else Color(0xFFB7FFF4)
                Box(
                    modifier = Modifier.size(43.dp).clip(RoundedCornerShape(15.dp))
                        .background(accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(task.type.shortLabel, color = accent, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlanTag(
                            text = when {
                                task.isFinished -> "已完成"
                                task.enabled -> "活动"
                                else -> "已暂停"
                            },
                            color = if (task.enabled && !task.isFinished) {
                                Color(0xFF8DF9EA)
                            } else {
                                Color(0xFFFFD38A)
                            },
                        )
                        Text(
                            task.scheduleLabel(),
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 9.5.sp,
                        )
                    }
                }
                Switch(
                    checked = task.enabled && !task.isFinished,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF55CDBC),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )
            }
            if (task.note.isNotBlank()) {
                Text(
                    text = task.note,
                    color = Color.White.copy(alpha = 0.57f),
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.nextLabel(),
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.39f),
                    fontSize = 9.5.sp,
                )
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 7.dp)) {
                    Text("编辑", color = Color(0xFFB7FFF4), fontSize = 10.sp)
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 7.dp)) {
                    Text("删除", color = Color(0xFFFFA8A8), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PlanTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = color.copy(alpha = 0.84f),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PlanEditorDialog(
    initial: PlanDraft,
    editing: Boolean,
    exactAlarmReady: Boolean,
    onDismiss: () -> Unit,
    onSave: (PlanDraft) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var note by remember { mutableStateOf(initial.note) }
    var type by remember { mutableStateOf(initial.type) }
    var repeatMode by remember { mutableStateOf(initial.repeatMode) }
    var scheduledAt by remember { mutableStateOf(initial.scheduledAtMillis) }
    val selectedDateTime = remember(scheduledAt) {
        Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101A3D),
        tonalElevation = 0.dp,
        title = { PlanDialogTitle(if (editing) "编辑计划" else "创建计划") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                PlanFieldLabel("计划名称")
                PlanTextInput(title, { title = it.take(80) }, "例如：交实验报告", 48.dp)

                PlanFieldLabel("类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanChoice("提醒", type == PlanTaskType.Reminder) { type = PlanTaskType.Reminder }
                    PlanChoice("闹钟", type == PlanTaskType.Alarm) { type = PlanTaskType.Alarm }
                    PlanTag("AI / 监控待接入", Color.White.copy(alpha = 0.45f))
                }

                PlanFieldLabel("日期与时间")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanDateButton(
                        text = selectedDateTime.format(planDateFormat),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    scheduledAt = replaceDate(
                                        scheduledAt,
                                        LocalDate.of(year, month + 1, day),
                                    )
                                },
                                selectedDateTime.year,
                                selectedDateTime.monthValue - 1,
                                selectedDateTime.dayOfMonth,
                            ).show()
                        },
                    )
                    PlanDateButton(
                        text = selectedDateTime.format(planTimeFormat),
                        modifier = Modifier.width(104.dp),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    scheduledAt = replaceTime(scheduledAt, LocalTime.of(hour, minute))
                                },
                                selectedDateTime.hour,
                                selectedDateTime.minute,
                                true,
                            ).show()
                        },
                    )
                }

                PlanFieldLabel("重复")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PlanRepeatMode.entries.forEach { mode ->
                        PlanChoice(mode.label, repeatMode == mode) { repeatMode = mode }
                    }
                }

                PlanFieldLabel("备注")
                PlanTextInput(note, { note = it.take(240) }, "可选，写下具体内容", 78.dp)

                if (type == PlanTaskType.Alarm && !exactAlarmReady) {
                    Text(
                        "未获得精确闹钟权限，系统可能轻微延迟。",
                        color = Color(0xFFFFDFA8),
                        fontSize = 9.5.sp,
                    )
                }
                Text(
                    "计划保存在本机，重启或更新应用后会自动恢复。",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 9.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PlanDraft(
                            title = title,
                            note = note,
                            type = type,
                            repeatMode = repeatMode,
                            scheduledAtMillis = scheduledAt,
                        ),
                    )
                },
            ) {
                Text(if (editing) "保存" else "创建", color = Color(0xFFB7FFF4), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.60f))
            }
        },
    )
}

@Composable
private fun PlanDialogTitle(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun PlanFieldLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.58f),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun PlanTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    height: Dp,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(13.dp),
    ) {
        if (value.isBlank()) {
            Text(hint, color = Color.White.copy(alpha = 0.30f), fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = height < 60.dp,
            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
            cursorBrush = SolidColor(Color(0xFFB7FFF4)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFF7D65E8).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFFC8BCFF).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.08f),
        ),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.52f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PlanDateButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(45.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.80f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlanGlassCard(
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = Color.White.copy(alpha = 0.052f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.105f)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.035f),
                        Color.Transparent,
                        Color(0xFF6C5ED4).copy(alpha = 0.028f),
                    ),
                ),
            ),
        ) {
            content()
        }
    }
}

private fun tomorrowAt(hour: Int, minute: Int): Long = ZonedDateTime.now()
    .plusDays(1)
    .withHour(hour)
    .withMinute(minute)
    .withSecond(0)
    .withNano(0)
    .toInstant()
    .toEpochMilli()

private fun nextAt(hour: Int, minute: Int): Long {
    val now = ZonedDateTime.now()
    var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}

private fun replaceDate(source: Long, date: LocalDate): Long {
    val old = Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault())
    return LocalDateTime.of(date, old.toLocalTime())
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun replaceTime(source: Long, time: LocalTime): Long {
    val old = Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault())
    return LocalDateTime.of(old.toLocalDate(), time)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun PlanTask.scheduleLabel(): String {
    val anchor = Instant.ofEpochMilli(scheduledAtMillis).atZone(ZoneId.systemDefault())
    val time = anchor.format(planTimeFormat)
    return when (repeatMode) {
        PlanRepeatMode.Once -> "${anchor.format(planDateFormat)} · $time"
        PlanRepeatMode.Daily -> "每天 $time"
        PlanRepeatMode.Weekdays -> "工作日 $time"
        PlanRepeatMode.Weekly -> "每周 ${anchor.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINA)} $time"
        PlanRepeatMode.Monthly -> "每月${anchor.dayOfMonth}日 $time"
    }
}

private fun PlanTask.nextLabel(): String = when {
    isFinished -> lastRunAtMillis?.let { "已于 ${formatPlanTime(it)} 完成" } ?: "单次计划已完成"
    !enabled -> nextRunAtMillis?.let { "暂停中 · 原定 ${formatPlanTime(it)}" } ?: "计划已暂停"
    nextRunAtMillis != null -> "下次运行：${formatPlanTime(nextRunAtMillis)}"
    else -> "暂无下次运行时间"
}

private fun formatPlanTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(planNextFormat)
