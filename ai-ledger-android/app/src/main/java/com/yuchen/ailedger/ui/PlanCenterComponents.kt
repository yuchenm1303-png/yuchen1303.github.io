package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskFilter
import com.yuchen.ailedger.model.PlanTaskType

@Composable
internal fun PlanHeader(
    state: AssistantUiState,
    activeCount: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanPressableGlass(
            state = state,
            radius = 999,
            role = GlassRole.Chip,
            modifier = Modifier.size(44.dp),
            onClick = onBack,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "计划",
                color = Color.White,
                fontSize = 29.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "安排提醒、闹钟与周期任务",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        PlanNativePill(
            state = state,
            text = "$activeCount 个活动",
            selected = activeCount > 0,
        )
    }
}

@Composable
internal fun PlanQuickComposer(
    state: AssistantUiState,
    value: String,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    PlanNativeGlassFrame(
        state = state,
        radius = 27,
        role = GlassRole.Card,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanNativeGlassFrame(
                state = state,
                radius = 999,
                role = GlassRole.Chip,
                modifier = Modifier.size(40.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White.copy(alpha = 0.90f), fontSize = 21.sp)
                }
            }
            Spacer(Modifier.width(11.dp))
            Box(
                modifier = Modifier.weight(1f).height(44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        "安排一个新计划…",
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
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
            Spacer(Modifier.width(8.dp))
            PlanPressableGlass(
                state = state,
                radius = 999,
                role = GlassRole.Floating,
                intensityScale = 1.08f,
                modifier = Modifier.size(48.dp),
                onClick = onCreate,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("→", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
internal fun PlanTemplateGrid(
    state: AssistantUiState,
    onSelect: (PlanDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PlanSectionEyebrow("快捷计划")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PlanTemplateChip(
                state = state,
                top = "明早 7:00",
                bottom = "起床闹钟",
                modifier = Modifier.weight(1f),
            ) {
                onSelect(
                    PlanDraft(
                        title = "起床",
                        note = "新的一天开始了",
                        type = PlanTaskType.Alarm,
                        scheduledAtMillis = tomorrowAt(7, 0),
                    ),
                )
            }
            PlanTemplateChip(
                state = state,
                top = "今晚 22:30",
                bottom = "学习提醒",
                modifier = Modifier.weight(1f),
            ) {
                onSelect(
                    PlanDraft(
                        title = "复习今天的内容",
                        note = "完成今日学习计划",
                        scheduledAtMillis = nextAt(22, 30),
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PlanTemplateChip(
                state = state,
                top = "工作日 7:30",
                bottom = "上课闹钟",
                modifier = Modifier.weight(1f),
            ) {
                onSelect(
                    PlanDraft(
                        title = "准备上课",
                        type = PlanTaskType.Alarm,
                        repeatMode = PlanRepeatMode.Weekdays,
                        scheduledAtMillis = nextAt(7, 30),
                    ),
                )
            }
            PlanTemplateChip(
                state = state,
                top = "每天 20:00",
                bottom = "每日复盘",
                modifier = Modifier.weight(1f),
            ) {
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
}

@Composable
private fun PlanTemplateChip(
    state: AssistantUiState,
    top: String,
    bottom: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PlanPressableGlass(
        state = state,
        radius = 21,
        role = GlassRole.Chip,
        intensityScale = 0.94f,
        modifier = modifier.height(72.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                top,
                color = Color(0xFFB7FFF4).copy(alpha = 0.90f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                bottom,
                color = Color.White.copy(alpha = 0.91f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
internal fun PlanInfoBanner(
    state: AssistantUiState,
    onAction: () -> Unit,
) {
    PlanNativeGlassFrame(
        state = state,
        radius = 22,
        role = GlassRole.Card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanNativeGlassFrame(
                state = state,
                radius = 14,
                role = GlassRole.Chip,
                modifier = Modifier.size(38.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("准", color = Color(0xFFFFDFA8), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "精确闹钟尚未授权",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "普通提醒仍可运行，但系统可能稍晚触发。",
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 9.5.sp,
                )
            }
            PlanPressableGlass(
                state = state,
                radius = 999,
                role = GlassRole.Chip,
                modifier = Modifier.width(72.dp).height(36.dp),
                onClick = onAction,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("去授权", color = Color(0xFFFFDFA8), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun PlanFilterBar(
    state: AssistantUiState,
    selected: PlanTaskFilter,
    onSelect: (PlanTaskFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PlanSectionEyebrow("查看范围")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanTaskFilter.entries.forEach { filter ->
                PlanPressableGlass(
                    state = state,
                    radius = 999,
                    role = GlassRole.Chip,
                    intensityScale = if (selected == filter) 1.14f else 0.88f,
                    modifier = Modifier.weight(1f).height(42.dp),
                    onClick = { onSelect(filter) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            filter.label,
                            color = if (selected == filter) {
                                Color(0xFFB7FFF4)
                            } else {
                                Color.White.copy(alpha = 0.62f)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when (filter) {
                    PlanTaskFilter.All -> "全部计划"
                    PlanTaskFilter.Active -> "活动计划"
                    PlanTaskFilter.Paused -> "暂停与已完成"
                },
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "按下一次执行时间排序",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 9.5.sp,
            )
        }
        Text("$count 项", color = Color.White.copy(alpha = 0.36f), fontSize = 10.sp)
    }
}

@Composable
internal fun PlanEmptyCard(
    state: AssistantUiState,
    filtered: Boolean,
    onCreate: () -> Unit,
) {
    PlanNativeGlassFrame(
        state = state,
        radius = 28,
        role = GlassRole.Card,
        modifier = Modifier.fillMaxWidth().height(196.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PlanNativeGlassFrame(
                state = state,
                radius = 17,
                role = GlassRole.Chip,
                modifier = Modifier.size(48.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("计", color = Color(0xFFC8BCFF), fontSize = 21.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(11.dp))
            Text(
                if (filtered) "这里暂时没有计划" else "还没有安排计划",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (filtered) "切换筛选条件查看其他计划" else "从上方快捷模板开始，或创建自己的计划。",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.5.sp,
            )
            if (!filtered) {
                Spacer(Modifier.height(13.dp))
                PlanPressableGlass(
                    state = state,
                    radius = 999,
                    role = GlassRole.Chip,
                    intensityScale = 1.08f,
                    modifier = Modifier.width(154.dp).height(40.dp),
                    onClick = onCreate,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "创建第一个计划",
                            color = Color(0xFFB7FFF4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlanTaskCard(
    state: AssistantUiState,
    task: PlanTask,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PlanNativeGlassFrame(
        state = state,
        radius = 25,
        role = GlassRole.Card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val accent = if (task.type == PlanTaskType.Alarm) Color(0xFFC8BCFF) else Color(0xFFB7FFF4)
                PlanNativeGlassFrame(
                    state = state,
                    radius = 16,
                    role = GlassRole.Chip,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(task.type.shortLabel, color = accent, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        task.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlanNativePill(
                            state = state,
                            text = when {
                                task.isFinished -> "已完成"
                                task.enabled -> "活动"
                                else -> "已暂停"
                            },
                            selected = task.enabled && !task.isFinished,
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
                    task.note,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.nextLabel(),
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.39f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                PlanTaskActionChip(state, "编辑", Color(0xFFB7FFF4), onEdit)
                Spacer(Modifier.width(6.dp))
                PlanTaskActionChip(state, "删除", Color(0xFFFFA8A8), onDelete)
            }
        }
    }
}

@Composable
private fun PlanTaskActionChip(
    state: AssistantUiState,
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    PlanPressableGlass(
        state = state,
        radius = 999,
        role = GlassRole.Chip,
        intensityScale = 0.88f,
        modifier = Modifier.width(58.dp).height(34.dp),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PlanSectionEyebrow(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun PlanNativePill(
    state: AssistantUiState,
    text: String,
    selected: Boolean,
) {
    PlanNativeGlassFrame(state = state, radius = 999, role = GlassRole.Chip) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selected) Color(0xFFB7FFF4) else Color.White.copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun PlanPressableGlass(
    state: AssistantUiState,
    radius: Int,
    role: GlassRole,
    modifier: Modifier,
    intensityScale: Float = 1f,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * intensityScale,
        motionIntensity = state.motionIntensity,
        radius = radius,
        modifier = modifier,
        role = role,
        onClick = onClick,
        content = content,
    )
}

@Composable
internal fun PlanNativeGlassFrame(
    state: AssistantUiState,
    radius: Int,
    role: GlassRole,
    modifier: Modifier = Modifier,
    intensityScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier = modifier.clip(shape)) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity * intensityScale,
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = Modifier.matchParentSize(),
            role = role,
        ) {}
        Box { content() }
    }
}
