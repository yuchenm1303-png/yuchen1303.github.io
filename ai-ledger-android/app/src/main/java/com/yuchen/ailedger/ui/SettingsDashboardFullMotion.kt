package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SettingsCardPressEasing = CubicBezierEasing(0.10f, 0.00f, 0.05f, 1.00f)
private val SettingsCardReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.10f, 1.00f)
private const val SettingsCardTau = (PI * 2.0).toFloat()

/**
 * 设置页八个入口使用普通 Compose 雾面玻璃，完全不接入 OpenGL registry。
 * 选中和按压由几何形变、体积光、明暗边缘、接触热点与释放余辉共同构成。
 */
@Composable
internal fun SettingsDashboardGridFullMotion(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsDetailSection,
    onSelected: (SettingsDetailSection) -> Unit,
) {
    val context = LocalContext.current
    val stickerSizeDp = InlineStickerDisplaySettings.sizeDp(context)
    val accountRepository = remember(context.applicationContext) {
        SupabaseAuthRepository.get(context.applicationContext)
    }
    val accountState by accountRepository.state.collectAsState()
    val motionOn = state.quality.enableMotion && state.motionIntensity > 0.02f
    val serviceValue = when {
        accountState.loading -> "检查登录状态"
        accountState.isLoggedIn -> "已登录 · 云端"
        aiEndpoint.isBlank() -> "登录与本地"
        else -> "登录与云端"
    }
    val memoryValue = when {
        accountState.loading -> "检查登录状态"
        accountState.isLoggedIn -> "账号已登录"
        else -> "登录后使用"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsDashboardRow {
            SettingsPhysicalGlassTile(
                title = "主题",
                subtitle = "背景与主题",
                value = settingsDashboardThemeLabel(state.backgroundTheme),
                selected = selectedPanel == SettingsDetailSection.Appearance,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Appearance) }
            SettingsPhysicalGlassTile(
                title = "玻璃",
                subtitle = "质感与流畅度",
                value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                selected = selectedPanel == SettingsDetailSection.Glass,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Glass) }
        }
        SettingsDashboardRow {
            SettingsPhysicalGlassTile(
                title = "视觉智能",
                subtitle = "边缘光与光标",
                value = "运行 HUD",
                selected = selectedPanel == SettingsDetailSection.Assistant,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Assistant) }
            SettingsPhysicalGlassTile(
                title = "数据偏好",
                subtitle = "预算与账单",
                value = "${state.ledgerRecords.size} 笔",
                selected = selectedPanel == SettingsDetailSection.Data,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Data) }
        }
        SettingsDashboardRow {
            SettingsPhysicalGlassTile(
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsDetailSection.Service,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Service) }
            SettingsPhysicalGlassTile(
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsDetailSection.Advanced,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Advanced) }
        }
        SettingsDashboardRow {
            SettingsPhysicalGlassTile(
                title = "聊天设置",
                subtitle = "消息与表情",
                value = "${stickerSizeDp.roundToInt()} dp",
                selected = selectedPanel == SettingsDetailSection.Chat,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Chat) }
            SettingsPhysicalGlassTile(
                title = "记忆",
                subtitle = "长期上下文",
                value = memoryValue,
                selected = selectedPanel == SettingsDetailSection.Memory,
                motionOn = motionOn,
            ) { onSelected(SettingsDetailSection.Memory) }
        }
    }
}

@Composable
private fun RowScope.SettingsPhysicalGlassTile(
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    motionOn: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val selectionAnim = remember { Animatable(if (selected) 1f else 0f) }
    val pressAnim = remember { Animatable(0f) }
    val afterglowAnim = remember { Animatable(0f) }
    val arrivalAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var tileSize by remember { mutableStateOf(Size(1f, 1f)) }
    var touchCenter by remember { mutableStateOf(Offset(0.50f, 0.50f)) }
    var pressCycleStarted by remember { mutableStateOf(false) }

    LaunchedEffect(selected, motionOn) {
        val target = if (selected) 1f else 0f
        selectionAnim.stop()
        arrivalAnim.stop()
        if (!motionOn) {
            selectionAnim.snapTo(target)
            arrivalAnim.snapTo(0f)
        } else {
            launch {
                selectionAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = if (selected) 0.67f else 0.84f,
                        stiffness = if (selected) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
                    ),
                )
            }
            if (selected) {
                arrivalAnim.snapTo(0f)
                arrivalAnim.animateTo(1f, tween(118, easing = FastOutSlowInEasing))
                arrivalAnim.animateTo(
                    0f,
                    spring(
                        dampingRatio = 0.42f,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
            } else {
                arrivalAnim.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
            }
        }
    }

    LaunchedEffect(pressed, motionOn) {
        if (pressed) {
            pressCycleStarted = true
            afterglowAnim.stop()
            if (motionOn) {
                launch {
                    afterglowAnim.animateTo(0f, tween(70, easing = FastOutSlowInEasing))
                }
                pressAnim.stop()
                if (pressAnim.value < 0.22f) pressAnim.snapTo(0.22f)
                pressAnim.animateTo(1.16f, tween(142, easing = SettingsCardPressEasing))
                pressAnim.animateTo(
                    0.94f,
                    spring(
                        dampingRatio = 0.52f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            } else {
                pressAnim.snapTo(1f)
            }
        } else if (pressCycleStarted) {
            pressCycleStarted = false
            if (motionOn) {
                launch {
                    pressAnim.stop()
                    pressAnim.animateTo(-0.20f, tween(126, easing = SettingsCardReleaseEasing))
                    pressAnim.animateTo(
                        0.065f,
                        spring(
                            dampingRatio = 0.38f,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                    pressAnim.animateTo(0f, tween(205, easing = FastOutSlowInEasing))
                }
                launch {
                    afterglowAnim.stop()
                    afterglowAnim.snapTo(1f)
                    afterglowAnim.animateTo(0f, tween(780, easing = FastOutSlowInEasing))
                }
            } else {
                pressAnim.snapTo(0f)
                afterglowAnim.snapTo(0f)
            }
        }
    }

    val selection = selectionAnim.value.coerceIn(0f, 1f)
    val pressValue = pressAnim.value.coerceIn(-0.24f, 1.20f)
    val pressPositive = pressValue.coerceAtLeast(0f)
    val recoil = (-pressValue).coerceAtLeast(0f)
    val afterglow = afterglowAnim.value.coerceIn(0f, 1f)
    val arrival = arrivalAnim.value.coerceIn(0f, 1f)
    val activeEnergy = (
        selection * 0.72f +
            pressPositive * 0.78f +
            afterglow * 0.48f +
            arrival * 0.66f
        ).coerceIn(0f, 1.45f)

    val sharedClock = LocalSettingsFrostMotionClock.current
    val elapsedNanos = if (motionOn && activeEnergy > 0.002f) sharedClock?.frameNanos ?: 0L else 0L
    val seed = remember(title) { (title.sumOf { it.code } % 997) / 997f }
    val phase = if (elapsedNanos > 0L) {
        seed + (elapsedNanos % 6_200_000_000L).toDouble().div(6_200_000_000.0).toFloat()
    } else {
        seed
    }
    val pulse = ((sin((phase + 0.13f) * SettingsCardTau) + 1f) * 0.5f).coerceIn(0f, 1f)
    val radius = 20f + selection * 38f + pressPositive * 1.8f
    val shape = RoundedCornerShape(radius.dp)
    val parentLayer = LocalSettingsFrostParentLayer.current
    val itemId = remember(title) { "settings-physical-$title" }
    val frostAlpha = 0.078f + selection * 0.030f + pressPositive * 0.020f + arrival * 0.010f

    SettingsFrostParentRegistrationCleanup(parentLayer, itemId)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(116.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .registerSettingsFrostParentItem(
                    id = itemId,
                    layerState = parentLayer,
                    radiusDp = radius,
                    backdropAlpha = 1f,
                    frostAlpha = frostAlpha,
                    dimAlpha = pressPositive * 0.012f,
                )
                .onSizeChanged {
                    tileSize = Size(
                        it.width.coerceAtLeast(1).toFloat(),
                        it.height.coerceAtLeast(1).toFloat(),
                    )
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(
                        touchCenter.x.coerceIn(0.05f, 0.95f),
                        touchCenter.y.coerceIn(0.08f, 0.92f),
                    )
                    scaleX = 1f +
                        selection * 0.007f +
                        arrival * 0.010f +
                        pressPositive * 0.042f -
                        recoil * 0.018f
                    scaleY = 1f +
                        selection * 0.003f +
                        arrival * 0.008f -
                        pressPositive * 0.052f +
                        recoil * 0.027f
                    translationX = (touchCenter.x - 0.5f) * pressPositive * 6.2f +
                        recoil * if (touchCenter.x > 0.5f) 2.4f else -2.4f
                    translationY = pressPositive * 4.5f - recoil * 1.9f - arrival * 1.2f
                    rotationZ = (touchCenter.x - 0.5f) * pressPositive * 0.72f
                }
                .settingsPhysicalGlassOptics(
                    radius = radius,
                    selection = selection,
                    press = pressPositive,
                    recoil = recoil,
                    afterglow = afterglow,
                    arrival = arrival,
                    activeEnergy = activeEnergy,
                    pulse = pulse,
                    phase = phase,
                    touchCenter = touchCenter,
                )
                .clip(shape)
                .pointerInput(title) {
                    awaitEachGesture {
                        fun updateTouch(position: Offset) {
                            touchCenter = Offset(
                                x = (position.x / tileSize.width.coerceAtLeast(1f)).coerceIn(0.04f, 0.96f),
                                y = (position.y / tileSize.height.coerceAtLeast(1f)).coerceIn(0.07f, 0.93f),
                            )
                        }

                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateTouch(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (tracked != null) {
                                updateTouch(tracked.position)
                                if (!tracked.pressed) break
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
                .semantics {
                    contentDescription = "$title，$subtitle，当前$value"
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (parentLayer == null) {
                FrostInfoGlassPanel(
                    radius = radius,
                    backdropAlpha = 1f,
                    frostAlpha = frostAlpha,
                    dimAlpha = pressPositive * 0.012f,
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }

            SettingsTileTextContent(
                title = title,
                subtitle = subtitle,
                value = value,
                selection = selection,
                press = pressPositive,
                arrival = arrival,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsTileTextContent(
    title: String,
    subtitle: String,
    value: String,
    selection: Float,
    press: Float,
    arrival: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 17.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = (0.91f + selection * 0.08f + arrival * 0.01f).coerceIn(0f, 1f)),
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = (0.48f + selection * 0.17f + press * 0.04f).coerceIn(0f, 0.82f)),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsHairline(alpha = 0.095f + selection * 0.105f + press * 0.025f)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前",
                color = Color.White.copy(alpha = 0.34f + selection * 0.12f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                color = Color.White.copy(alpha = (0.66f + selection * 0.28f + press * 0.03f).coerceIn(0f, 0.98f)),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun Modifier.settingsPhysicalGlassOptics(
    radius: Float,
    selection: Float,
    press: Float,
    recoil: Float,
    afterglow: Float,
    arrival: Float,
    activeEnergy: Float,
    pulse: Float,
    phase: Float,
    touchCenter: Offset,
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val radiusPx = radius.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val fullSize = Size(w, h)
    val active = activeEnergy.coerceIn(0f, 1.45f)
    val contactX = touchCenter.x.coerceIn(0.04f, 0.96f) * w
    val contactY = touchCenter.y.coerceIn(0.07f, 0.93f) * h
    val circulationA = ((sin((phase + 0.07f) * SettingsCardTau) + 1f) * 0.5f).coerceIn(0f, 1f)
    val circulationB = ((sin((phase * 0.73f + 0.41f) * SettingsCardTau) + 1f) * 0.5f).coerceIn(0f, 1f)
    val sweepCenter = -0.34f + 1.68f * circulationA
    val rimEnergy = (0.22f + active * 0.78f + pulse * selection * 0.10f).coerceIn(0f, 1.35f)
    val bodyEnergy = (selection * 0.72f + press * 0.90f + arrival * 0.72f).coerceIn(0f, 1.45f)

    val bodyBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.030f + bodyEnergy * 0.030f),
            Color(0xFFBFFBF7).copy(alpha = 0.012f + bodyEnergy * 0.026f),
            Color(0xFF334D84).copy(alpha = 0.018f + bodyEnergy * 0.044f),
            Color(0xFF080C20).copy(alpha = 0.030f + press * 0.050f),
        ),
        start = Offset.Zero,
        end = Offset(w, h),
    )
    val upperVolume = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.105f * active),
            Color(0xFFBFFFF8).copy(alpha = 0.045f * active),
            Color.Transparent,
        ),
        center = Offset(w * (0.46f + (circulationA - 0.5f) * 0.12f), h * 0.10f),
        radius = maxOf(w, h) * 0.68f,
    )
    val lowerVolume = Brush.radialGradient(
        colors = listOf(
            Color(0xFF8B73FF).copy(alpha = 0.070f * selection),
            Color(0xFF4D82FF).copy(alpha = 0.052f * selection),
            Color.Transparent,
        ),
        center = Offset(w * (0.58f + (circulationB - 0.5f) * 0.18f), h * 0.94f),
        radius = maxOf(w, h) * 0.66f,
    )
    val contactLight = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.155f * press + 0.072f * afterglow),
            Color(0xFF72FFF0).copy(alpha = 0.110f * press + 0.060f * afterglow),
            Color(0xFF7B8EFF).copy(alpha = 0.055f * press),
            Color.Transparent,
        ),
        center = Offset(contactX, contactY),
        radius = maxOf(w, h) * (0.29f + press * 0.14f + afterglow * 0.08f),
    )
    val outerRim = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.125f + rimEnergy * 0.165f),
            Color(0xFFDFFFFB).copy(alpha = 0.040f + rimEnergy * 0.070f),
            Color.Transparent,
            Color(0xFF00030B).copy(alpha = 0.095f + rimEnergy * 0.105f),
            Color.White.copy(alpha = 0.032f + rimEnergy * 0.038f),
        ),
        startY = 0f,
        endY = h,
    )
    val innerDarkRim = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF00030C).copy(alpha = 0.095f + rimEnergy * 0.125f),
        ),
        startY = h * 0.36f,
        endY = h,
    )
    val prismSweep = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xFFFF6EC8).copy(alpha = 0.032f * active),
            Color(0xFFFFE27A).copy(alpha = 0.028f * active),
            Color.White.copy(alpha = 0.070f * active),
            Color(0xFF67FFF0).copy(alpha = 0.050f * active),
            Color(0xFF7D91FF).copy(alpha = 0.036f * active),
            Color.Transparent,
        ),
        start = Offset(w * (sweepCenter - 0.50f), -h * 0.34f),
        end = Offset(w * (sweepCenter + 0.50f), h * 1.34f),
    )
    val edgeHotspotX = if (touchCenter.x >= 0.5f) {
        (touchCenter.x + 0.06f).coerceAtMost(0.94f)
    } else {
        (touchCenter.x - 0.06f).coerceAtLeast(0.06f)
    }
    val edgeHotspot = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.105f * press + 0.055f * arrival),
            Color(0xFFFFF2B1).copy(alpha = 0.045f * press),
            Color(0xFF67FFE8).copy(alpha = 0.050f * press + 0.025f * selection),
            Color.Transparent,
        ),
        center = Offset(w * edgeHotspotX, h * (0.38f + (circulationB - 0.5f) * 0.08f)),
        radius = maxOf(w, h) * (0.17f + press * 0.10f + arrival * 0.05f),
    )
    val topCaustic = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.070f * active),
            Color(0xFFBFFFF8).copy(alpha = 0.038f * active),
            Color.Transparent,
        ),
        start = Offset(w * (touchCenter.x - 0.44f), h * 0.01f),
        end = Offset(w * (touchCenter.x + 0.42f), h * 0.30f),
    )

    val outerInset = 0.55.dp.toPx()
    val outerTopLeft = Offset(outerInset, outerInset)
    val outerSize = Size(
        (w - outerInset * 2f).coerceAtLeast(1f),
        (h - outerInset * 2f).coerceAtLeast(1f),
    )
    val innerInset = 2.15.dp.toPx()
    val innerTopLeft = Offset(innerInset, innerInset)
    val innerSize = Size(
        (w - innerInset * 2f).coerceAtLeast(1f),
        (h - innerInset * 2f).coerceAtLeast(1f),
    )
    val waveProgress = 1f - afterglow
    val waveInset = (2.4f + waveProgress * 11.5f).dp.toPx()
    val waveSize = Size(
        (w - waveInset * 2f).coerceAtLeast(1f),
        (h - waveInset * 2f).coerceAtLeast(1f),
    )
    val waveCorner = CornerRadius(
        (radiusPx - waveInset).coerceAtLeast(0f),
        (radiusPx - waveInset).coerceAtLeast(0f),
    )

    onDrawWithContent {
        drawRoundRect(
            brush = bodyBrush,
            size = fullSize,
            cornerRadius = corner,
            blendMode = BlendMode.SrcOver,
        )
        if (active > 0.001f) {
            drawRoundRect(
                brush = upperVolume,
                size = fullSize,
                cornerRadius = corner,
                blendMode = BlendMode.Screen,
            )
            drawRoundRect(
                brush = lowerVolume,
                size = fullSize,
                cornerRadius = corner,
                blendMode = BlendMode.Screen,
            )
            drawRoundRect(
                brush = contactLight,
                size = fullSize,
                cornerRadius = corner,
                blendMode = BlendMode.Screen,
            )
        }

        drawContent()

        drawRoundRect(
            brush = outerRim,
            topLeft = outerTopLeft,
            size = outerSize,
            cornerRadius = corner,
            style = Stroke(width = 0.88.dp.toPx() + rimEnergy * 0.62.dp.toPx()),
            blendMode = BlendMode.Screen,
        )
        drawRoundRect(
            brush = innerDarkRim,
            topLeft = innerTopLeft,
            size = innerSize,
            cornerRadius = CornerRadius(
                (radiusPx - innerInset).coerceAtLeast(0f),
                (radiusPx - innerInset).coerceAtLeast(0f),
            ),
            style = Stroke(width = 2.4.dp.toPx() + rimEnergy * 1.5.dp.toPx()),
            blendMode = BlendMode.Multiply,
        )
        if (active > 0.001f) {
            drawRoundRect(
                brush = prismSweep,
                size = fullSize,
                cornerRadius = corner,
                blendMode = BlendMode.Plus,
            )
            drawRoundRect(
                brush = topCaustic,
                topLeft = Offset(w * 0.04f, h * 0.03f),
                size = Size(w * 0.92f, h * 0.30f),
                cornerRadius = corner,
                blendMode = BlendMode.Screen,
            )
            drawRoundRect(
                brush = edgeHotspot,
                size = fullSize,
                cornerRadius = corner,
                blendMode = BlendMode.Plus,
            )
        }
        if (afterglow > 0.001f) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = afterglow * 0.20f),
                        Color(0xFF76FFF0).copy(alpha = afterglow * 0.16f),
                        Color(0xFF8B8DFF).copy(alpha = afterglow * 0.10f),
                        Color.Transparent,
                    ),
                    start = Offset(waveInset, waveInset),
                    end = Offset(w - waveInset, h - waveInset),
                ),
                topLeft = Offset(waveInset, waveInset),
                size = waveSize,
                cornerRadius = waveCorner,
                style = Stroke(width = (0.8f + afterglow * 1.5f).dp.toPx()),
                blendMode = BlendMode.Screen,
            )
        }
        if (arrival > 0.001f || recoil > 0.001f) {
            val impulse = maxOf(arrival, recoil * 1.4f).coerceIn(0f, 1f)
            drawRoundRect(
                color = Color(0xFF9AFFF3).copy(alpha = impulse * 0.10f),
                topLeft = Offset(1.2.dp.toPx(), 1.2.dp.toPx()),
                size = Size(
                    (w - 2.4.dp.toPx()).coerceAtLeast(1f),
                    (h - 2.4.dp.toPx()).coerceAtLeast(1f),
                ),
                cornerRadius = corner,
                style = Stroke(width = (2.2f + impulse * 2.2f).dp.toPx()),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

@Composable
private fun SettingsDashboardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private fun settingsDashboardQualityLabel(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun settingsDashboardGlassLabel(preset: GlassPreset): String = when (preset) {
    GlassPreset.Basic -> "基础"
    GlassPreset.Blur -> "模糊"
    GlassPreset.Liquid -> "液态"
    GlassPreset.Safe -> "安全"
}

private fun settingsDashboardThemeLabel(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}
