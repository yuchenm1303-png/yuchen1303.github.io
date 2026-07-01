package com.yuchen.ailedger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
internal fun PlanEditorDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101A3D),
        tonalElevation = 0.dp,
        title = {
            Text(
                if (editing) "编辑计划" else "创建计划",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                PlanEditorLabel("计划名称")
                PlanEditorInput(title, { title = it.take(80) }, "例如：交实验报告", 48.dp)

                PlanEditorLabel("类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanEditorChoice("提醒", type == PlanTaskType.Reminder) {
                        type = PlanTaskType.Reminder
                    }
                    PlanEditorChoice("闹钟", type == PlanTaskType.Alarm) {
                        type = PlanTaskType.Alarm
                    }
                }

                PlanEditorLabel("日期与时间")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlanEditorDateButton(
                        text = selected.format(planDateFormat),
                        modifier = Modifier.fillMaxWidth(0.62f),
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
                        text = selected.format(planTimeFormat),
                        modifier = Modifier.width(104.dp),
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
                        PlanEditorChoice(mode.label, repeatMode == mode) {
                            repeatMode = mode
                        }
                    }
                }

                PlanEditorLabel("备注")
                PlanEditorInput(note, { note = it.take(240) }, "可选，写下具体内容", 78.dp)

                if (type == PlanTaskType.Alarm && !exactAlarmReady) {
                    Text(
                        "精确闹钟功能尚未开启，系统可能轻微延迟。",
                        color = Color(0xFFFFDFA8),
                        fontSize = 9.5.sp,
                    )
                }
                Text(
                    "AI 定时任务和条件监控入口已预留，将在下一阶段接入。",
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
                Text(
                    if (editing) "保存" else "创建",
                    color = Color(0xFFB7FFF4),
                    fontWeight = FontWeight.Bold,
                )
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
private fun PlanEditorChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) {
            Color(0xFF7D65E8).copy(alpha = 0.28f)
        } else {
            Color.White.copy(alpha = 0.05f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFFC8BCFF).copy(alpha = 0.32f)
            else Color.White.copy(alpha = 0.08f),
        ),
        tonalElevation = 0.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.52f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PlanEditorDateButton(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
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
