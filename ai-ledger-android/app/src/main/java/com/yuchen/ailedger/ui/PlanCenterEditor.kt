package com.yuchen.ailedger.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
internal fun PlanEditorPanel(
    state: AssistantUiState,
    initial: PlanDraft,
    editing: Boolean,
    exactAlarmReady: Boolean,
    panelRadius: Int,
    containerProgress: Float,
    contentProgress: Float,
    modifier: Modifier = Modifier,
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

    PlanModalGlassSurface(
        state = state,
        radius = panelRadius,
        containerProgress = containerProgress,
        contentProgress = contentProgress,
        compactTitle = if (editing) "编辑计划" else "创建计划",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        if (editing) "编辑计划" else "创建计划",
                        color = Color.White,
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "选择类型、时间和重复方式",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                    )
                }
                PlanEditorAction(
                    state = state,
                    text = "关闭",
                    color = Color.White.copy(alpha = 0.62f),
                    compact = true,
                    onClick = onDismiss,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        top = 2.dp,
                        end = 2.dp,
                        bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        PlanEditorSection("计划名称") {
                            PlanEditorInput(
                                state = state,
                                value = title,
                                onValueChange = { title = it.take(80) },
                                hint = "例如：交实验报告",
                                height = 50.dp,
                            )
                        }
                    }
                    item {
                        PlanEditorSection("类型") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlanEditorChoice(
                                    state = state,
                                    text = "提醒",
                                    selected = type == PlanTaskType.Reminder,
                                    modifier = Modifier.width(76.dp),
                                ) { type = PlanTaskType.Reminder }
                                PlanEditorChoice(
                                    state = state,
                                    text = "闹钟",
                                    selected = type == PlanTaskType.Alarm,
                                    modifier = Modifier.width(76.dp),
                                ) { type = PlanTaskType.Alarm }
                            }
                        }
                    }
                    item {
                        PlanEditorSection("日期与时间") {
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
                        }
                    }
                    item {
                        PlanEditorSection("重复") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    PlanEditorChoice(
                                        state = state,
                                        text = PlanRepeatMode.Once.label,
                                        selected = repeatMode == PlanRepeatMode.Once,
                                        modifier = Modifier.weight(1f),
                                    ) { repeatMode = PlanRepeatMode.Once }
                                    PlanEditorChoice(
                                        state = state,
                                        text = PlanRepeatMode.Daily.label,
                                        selected = repeatMode == PlanRepeatMode.Daily,
                                        modifier = Modifier.weight(1f),
                                    ) { repeatMode = PlanRepeatMode.Daily }
                                    PlanEditorChoice(
                                        state = state,
                                        text = PlanRepeatMode.Weekdays.label,
                                        selected = repeatMode == PlanRepeatMode.Weekdays,
                                        modifier = Modifier.weight(1f),
                                    ) { repeatMode = PlanRepeatMode.Weekdays }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    PlanEditorChoice(
                                        state = state,
                                        text = PlanRepeatMode.Weekly.label,
                                        selected = repeatMode == PlanRepeatMode.Weekly,
                                        modifier = Modifier.weight(1f),
                                    ) { repeatMode = PlanRepeatMode.Weekly }
                                    PlanEditorChoice(
                                        state = state,
                                        text = PlanRepeatMode.Monthly.label,
                                        selected = repeatMode == PlanRepeatMode.Monthly,
                                        modifier = Modifier.weight(1f),
                                    ) { repeatMode = PlanRepeatMode.Monthly }
                                }
                            }
                        }
                    }
                    item {
                        PlanEditorSection("备注") {
                            PlanEditorInput(
                                state = state,
                                value = note,
                                onValueChange = { note = it.take(240) },
                                hint = "可选，写下具体内容",
                                height = 82.dp,
                            )
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        }
                    }
                }
            }

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

@Composable
internal fun PlanDeletePanel(
    state: AssistantUiState,
    task: PlanTask,
    panelRadius: Int,
    containerProgress: Float,
    contentProgress: Float,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    PlanModalGlassSurface(
        state = state,
        radius = panelRadius,
        containerProgress = containerProgress,
        contentProgress = contentProgress,
        compactTitle = "删除计划",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                "删除计划",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
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

@Composable
private fun PlanModalGlassSurface(
    state: AssistantUiState,
    radius: Int,
    containerProgress: Float,
    contentProgress: Float,
    compactTitle: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val safeRadius = radius.coerceAtLeast(18)
    val safeContainerProgress = containerProgress.coerceIn(0f, 1f)
    val safeContentProgress = contentProgress.coerceIn(0f, 1f)
    val compactEntrance = ((safeContainerProgress - 0.04f) / 0.18f).coerceIn(0f, 1f)
    val compactAlpha = (
        compactEntrance * (1f - safeContentProgress * 1.35f)
        ).coerceIn(0f, 1f)
    val contentTranslation = (1f - safeContentProgress) * 10f
    val shape = RoundedCornerShape(safeRadius.dp)
    val motion = state.motionIntensity.coerceIn(0f, 1f)
    val pressAnim = remember { Animatable(0f) }
    val pressScope = rememberCoroutineScope()
    var surfaceSize by remember { mutableStateOf(Size(1f, 1f)) }
    var pressCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val pressValue = pressAnim.value.coerceIn(-0.16f, 1f)
    val compression = pressValue.coerceAtLeast(0f)
    val rebound = (-pressValue).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .onSizeChanged { measured ->
                surfaceSize = Size(
                    measured.width.coerceAtLeast(1).toFloat(),
                    measured.height.coerceAtLeast(1).toFloat(),
                )
            }
            .pointerInput(motion) {
                if (motion <= 0.02f) return@pointerInput
                awaitEachGesture {
                    fun updateCenter(position: Offset) {
                        pressCenter = Offset(
                            x = (position.x / surfaceSize.width.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f),
                            y = (position.y / surfaceSize.height.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f),
                        )
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateCenter(down.position)
                    pressScope.launch {
                        pressAnim.stop()
                        if (pressAnim.value < 0.14f) pressAnim.snapTo(0.14f)
                        pressAnim.animateTo(
                            targetValue = 0.82f,
                            animationSpec = tween(125, easing = FastOutSlowInEasing),
                        )
                        pressAnim.animateTo(
                            targetValue = 0.68f,
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updateCenter(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }

                    pressScope.launch {
                        pressAnim.stop()
                        pressAnim.animateTo(
                            targetValue = -0.10f,
                            animationSpec = tween(115, easing = FastOutSlowInEasing),
                        )
                        pressAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.68f,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    }
                }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + compression * 0.0035f * motion - rebound * 0.0015f * motion
                scaleY = 1f - compression * 0.0055f * motion + rebound * 0.0025f * motion
                translationX = (pressCenter.x - 0.5f) * compression * 1.15.dp.toPx() * motion
                translationY = compression * 1.10.dp.toPx() * motion - rebound * 0.45.dp.toPx() * motion
            }
            .clip(shape),
    ) {
        PlanNativeGlassFrame(
            state = state,
            radius = safeRadius,
            role = GlassRole.Card,
            intensityScale = 1.12f,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(0xFF071126).copy(
                                alpha = 0.16f + safeContentProgress * 0.035f,
                            ),
                        ),
                )

                if (compactAlpha > 0.001f) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = compactAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            "+",
                            color = Color(0xFFB7FFF4),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            compactTitle,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                if (safeContainerProgress > 0.58f && safeContentProgress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = safeContentProgress
                                translationY = contentTranslation.dp.toPx()
                            },
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanEditorSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        content()
    }
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
        intensityScale = 0.94f,
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
    modifier: Modifier,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (selected) 1.14f else 0.88f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(38.dp),
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
        glassIntensity = state.glassIntensity * 0.94f,
        motionIntensity = state.motionIntensity,
        radius = 17,
        modifier = modifier.height(46.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PlanEditorAction(
    state: AssistantUiState,
    text: String,
    color: Color,
    compact: Boolean = false,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (emphasized) 1.10f else 0.88f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier
            .width(if (compact) 62.dp else 76.dp)
            .height(if (compact) 34.dp else 40.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
