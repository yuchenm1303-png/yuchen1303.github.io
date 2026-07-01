package com.yuchen.ailedger.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import com.yuchen.ailedger.model.PlanTaskType

@Composable
internal fun PlanHeader(activeCount: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Column {
                Text("计划", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(
                    "安排提醒、闹钟与周期任务",
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 11.5.sp,
                )
            }
        }
        PlanTag("$activeCount 个活动", Color(0xFF8DF9EA))
    }
}

@Composable
internal fun PlanQuickComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    PlanGlassCard(radius = 27.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, fontSize = 22.sp)
            }
            Box(
                modifier = Modifier.fillMaxWidth(0.78f).height(46.dp),
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
internal fun PlanTemplateStrip(onSelect: (PlanDraft) -> Unit) {
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
                    scheduledAtMillis = tomorrowAt(7, 0),
                ),
            )
        }
        PlanTemplateChip("今晚 22:30", "学习提醒") {
            onSelect(
                PlanDraft(
                    title = "复习今天的内容",
                    note = "完成今日学习计划",
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
internal fun PlanInfoBanner(onAction: () -> Unit) {
    PlanGlassCard(radius = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFD38A).copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("准", color = Color(0xFFFFDFA8), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "精确闹钟尚未授权",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "普通提醒仍可运行，但可能稍晚触发。",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 9.5.sp,
                    )
                }
            }
            TextButton(onClick = onAction) {
                Text("去授权", color = Color(0xFFFFDFA8), fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun PlanFilterBar(
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
                    filter.label,
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
internal fun PlanSectionTitle(filter: PlanTaskFilter, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (filter) {
                PlanTaskFilter.All -> "全部计划"
                PlanTaskFilter.Active -> "活动计划"
                PlanTaskFilter.Paused -> "暂停与已完成"
            },
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text("$count 项", color = Color.White.copy(alpha = 0.34f), fontSize = 10.sp)
    }
}

@Composable
internal fun PlanEmptyCard(filtered: Boolean, onCreate: () -> Unit) {
    PlanGlassCard(radius = 25.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("计", color = Color(0xFFC8BCFF), fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                if (filtered) "这里暂时没有计划" else "还没有安排计划",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (filtered) "切换筛选条件查看其他计划" else "创建提醒、闹钟或周期任务。",
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
internal fun PlanTaskCard(
    task: PlanTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PlanGlassCard(radius = 24.dp, modifier = Modifier.clickable(onClick = onEdit)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                    Column(Modifier.fillMaxWidth(0.68f)) {
                        Text(
                            task.title,
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
                                when {
                                    task.isFinished -> "已完成"
                                    task.enabled -> "活动"
                                    else -> "已暂停"
                                },
                                if (task.enabled && !task.isFinished) Color(0xFF8DF9EA)
                                else Color(0xFFFFD38A),
                            )
                            Text(
                                task.scheduleLabel(),
                                color = Color.White.copy(alpha = 0.48f),
                                fontSize = 9.5.sp,
                            )
                        }
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
                    task.note,
                    color = Color.White.copy(alpha = 0.57f),
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    task.nextLabel(),
                    color = Color.White.copy(alpha = 0.39f),
                    fontSize = 9.5.sp,
                )
                Row {
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
}

@Composable
internal fun PlanTag(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f),
        tonalElevation = 0.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = color.copy(alpha = 0.84f),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
internal fun PlanGlassCard(
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
