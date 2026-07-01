package com.yuchen.ailedger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
internal fun PlanEditorDialog(
    state: AssistantUiState,
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
    val selected = remember(scheduledAt) {
        Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PlanNativeGlassFrame(
            state = state,
            radius = 30,
            role = GlassRole.Card,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 17.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (editing) "编辑计划" else "创建计划",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "选择类型、时间和重复方式",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 11.sp,
                )

                PlanEditorLabel("计划名称")
                PlanEditorInput(
                    state = state,
                    value = title,
                    onValueChange = { title = it.take(80) },
                    hint = "例如：交实验报告",
                    height = 50.dp,
                )

                PlanEditorLabel("类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanEditorChoice(
                        state = state,
                        text = "提醒",
                        selected = type == PlanTaskType.Reminder,
                    ) { type = PlanTaskType.Reminder }
                    PlanEditorChoice(
                        state = state,
                        text = "闹钟",
                        selected = type == PlanTaskType.Alarm,
                    ) { type = PlanTaskType.Alarm }
                }

                PlanEditorLabel("日期与时间")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlanEditorDateButton(
                        state = state,
                        text = selected.format(planDateFormat),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    scheduledAt = replacePlanDate(
                                        scheduledAt,
                                        LocalDate.of(year, month + 1, day),
                                    )
                                },
                                selected.year,
                                selected.monthValue - 1,
                                selected.dayOfMonth,
                            ).show()
                        },
                    )
                    PlanEditorDateButton(
                        state = state,
                        text = selected.format(planTimeFormat),
                        modifier = Modifier.width(108.dp),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    scheduledAt = replacePlanTime(
                                        scheduledAt,
                                        LocalTime.of(hour, minute),
                                    )
                                },
                                selected.hour,
                                selected.minute,
                                true,
                            ).show()
                        },
                    )
                }

                PlanEditorLabel("重复")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PlanRepeatMode.entries.forEach { mode ->
                        PlanEditorChoice(
                            state = state,
                            text = mode.label,
                            selected = repeatMode == mode,
                        ) { repeatMode = mode }
                    }
                }

                PlanEditorLabel("备注")
                PlanEditorInput(
                    state = state,
                    value = note,
                    onValueChange = { note = it.take(240) },
                    hint = "可选，写下具体内容",
                    height = 82.dp,
                )

                if (type == PlanTaskType.Alarm && !exactAlarmReady) {
                    Text(
                        "精确闹钟功能尚未开启，系统可能轻微延迟。",
                        color = Color(0xFFFFDFA8),
                        fontSize = 9.5.sp,
                    )
                }
                Text(
                    "计划保存在本机，重启和应用更新后会自动恢复。",
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 9.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    PlanEditorAction(
                        state = state,
                        text = "取消",
                        color = Color.White.copy(alpha = 0.62f),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    PlanEditorAction(
                        state = state,
                        text = if (editing) "保存" else "创建",
                        color = Color(0xFFB7FFF4),
                        emphasized = true,
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
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanDeleteDialog(
    state: AssistantUiState,
    task: PlanTask,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PlanNativeGlassFrame(
            state = state,
            radius = 28,
            role = GlassRole.Card,
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("删除计划", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    "确定删除“${task.title}”吗？删除后不会再触发提醒。",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    PlanEditorAction(
                        state = state,
                        text = "取消",
                        color = Color.White.copy(alpha = 0.62f),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    PlanEditorAction(
                        state = state,
                        text = "删除",
                        color = Color(0xFFFFA8A8),
                        emphasized = true,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanEditorLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.58f),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun PlanEditorInput(
    state: AssistantUiState,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    height: Dp,
) {
    PlanNativeGlassFrame(
        state = state,
        radius = 17,
        role = GlassRole.Card,
        modifier = Modifier.fillMaxWidth().height(height),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopStart,
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
}

@Composable
private fun PlanEditorChoice(
    state: AssistantUiState,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (selected) 1.14f else 0.88f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.width(if (text.length > 3) 82.dp else 68.dp).height(38.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = if (selected) Color(0xFFB7FFF4) else Color.White.copy(alpha = 0.56f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun PlanEditorDateButton(
    state: AssistantUiState,
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.92f,
        motionIntensity = state.motionIntensity,
        radius = 17,
        modifier = modifier.height(46.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlanEditorAction(
    state: AssistantUiState,
    text: String,
    color: Color,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (emphasized) 1.10f else 0.88f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.width(76.dp).height(40.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
