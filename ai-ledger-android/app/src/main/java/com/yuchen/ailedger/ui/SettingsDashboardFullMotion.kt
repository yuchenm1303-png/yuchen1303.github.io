package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

private val SettingsShellPressPreloadEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val SettingsShellPressSinkEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val SettingsShellPressReleaseEasing = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val SettingsShellPressPulseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)

/**
 * 八个入口沿用个人空间 Shell 的按压时间线、形变参数和表面光学。
 * 底材仍是普通 Compose Card，完全不调用或注册 OpenGL。
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
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "主题",
                subtitle = "背景与主题",
                value = settingsDashboardThemeLabel(state.backgroundTheme),
                selected = selectedPanel == SettingsDetailSection.Appearance,
            ) { onSelected(SettingsDetailSection.Appearance) }
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "玻璃",
                subtitle = "质感与流畅度",
                value = "${settingsDashboardQualityLabel(state.quality)} · ${settingsDashboardGlassLabel(state.glassPreset)}",
                selected = selectedPanel == SettingsDetailSection.Glass,
            ) { onSelected(SettingsDetailSection.Glass) }
        }
        SettingsDashboardRow {
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "视觉智能",
                subtitle = "边缘光与光标",
                value = "运行 HUD",
                selected = selectedPanel == SettingsDetailSection.Assistant,
            ) { onSelected(SettingsDetailSection.Assistant) }
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "数据偏好",
                subtitle = "预算与账单",
                value = "${state.ledgerRecords.size} 笔",
                selected = selectedPanel == SettingsDetailSection.Data,
            ) { onSelected(SettingsDetailSection.Data) }
        }
        SettingsDashboardRow {
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "账号设置",
                subtitle = "账号 / Worker",
                value = serviceValue,
                selected = selectedPanel == SettingsDetailSection.Service,
            ) { onSelected(SettingsDetailSection.Service) }
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "系统信息",
                subtitle = "渲染边界",
                value = "OpenGL 隔离",
                selected = selectedPanel == SettingsDetailSection.Advanced,
            ) { onSelected(SettingsDetailSection.Advanced) }
        }
        SettingsDashboardRow {
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "聊天设置",
                subtitle = "消息与表情",
                value = "${stickerSizeDp.roundToInt()} dp",
                selected = selectedPanel == SettingsDetailSection.Chat,
            ) { onSelected(SettingsDetailSection.Chat) }
            SettingsPersonalSpaceMotionTile(
                state = state,
                title = "记忆",
                subtitle = "长期上下文",
                value = memoryValue,
                selected = selectedPanel == SettingsDetailSection.Memory,
            ) { onSelected(SettingsDetailSection.Memory) }
        }
    }
}

@Composable
private fun RowScope.SettingsPersonalSpaceMotionTile(
    state: AssistantUiState,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val radius = 30
    val motionEnabled = state.quality.enableMotion && state.motionIntensity > 0.02f
    val shellPress = remember { Animatable(0f) }
    val shellLens = remember { Animatable(0f) }
    val pressScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var pressSize by remember { mutableStateOf(Size(1f, 1f)) }
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.42f)) }
    var rimFlowSeed by remember { mutableStateOf(0.50f) }
    var rimFlowDirection by remember { mutableStateOf(1f) }
    var rimFlowBand by remember { mutableStateOf(0) }
    var rimFlowStrength by remember { mutableStateOf(1f) }

    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) {
            shellPress.stop()
            shellLens.stop()
            shellPress.snapTo(0f)
            shellLens.snapTo(0f)
        }
    }

    val pressValue = shellPress.value.coerceIn(-0.14f, 1.08f)
    val lensValue = shellLens.value.coerceIn(0f, 1f)
    val pressCompression = settingsGlassSmoothStep(
        (pressValue.coerceAtLeast(0f) / 0.72f).coerceIn(0f, 1f)
    )
    val pressRebound = settingsGlassSmoothStep(
        (-pressValue / 0.10f).coerceIn(0f, 1f)
    )
    val surfaceOpticsPress = maxOf(
        pressValue.coerceAtLeast(0f),
        maxOf(lensValue * 0.62f, pressRebound * 0.24f),
    )
    val glassIntensityScale = 1f + pressCompression * 0.10f
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motionEnabled) {
            spring(
                dampingRatio = 0.88f,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "settings-selected-$title",
    )
    val prismEdgeHighlight = LocalRainbowPrismStyle.current.edgeHighlight.coerceIn(0f, 2f)
    val baseIntensity = (state.glassIntensity * 1.08f).coerceIn(0.78f, 1.30f)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(116.dp)
            .onSizeChanged { size ->
                pressSize = Size(
                    size.width.coerceAtLeast(1).toFloat(),
                    size.height.coerceAtLeast(1).toFloat(),
                )
            }
            .pointerInput(motionEnabled, title) {
                if (!motionEnabled) return@pointerInput
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        pressCenter = Offset(
                            x = (position.x / pressSize.width).coerceIn(0f, 1f),
                            y = (position.y / pressSize.height).coerceIn(0f, 1f),
                        )
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePressCenter(down.position)
                    rimFlowSeed = Random.nextFloat()
                    rimFlowDirection = if (Random.nextBoolean()) 1f else -1f
                    rimFlowBand = Random.nextInt(0, 4)
                    rimFlowStrength = 0.86f + Random.nextFloat() * 0.52f

                    pressScope.launch {
                        shellPress.stop()
                        if (shellPress.value < 0.18f) shellPress.snapTo(0.18f)
                        shellPress.animateTo(0.42f, tween(150, easing = SettingsShellPressPulseEasing))
                        shellPress.animateTo(0.62f, tween(360, easing = SettingsShellPressSinkEasing))
                        shellPress.animateTo(0.76f, tween(620, easing = FastOutSlowInEasing))
                        shellPress.animateTo(0.62f, tween(680, easing = FastOutSlowInEasing))
                        shellPress.animateTo(
                            0.70f,
                            spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessVeryLow,
                            ),
                        )
                    }
                    pressScope.launch {
                        shellLens.stop()
                        shellLens.animateTo(0.26f, tween(230, easing = SettingsShellPressPreloadEasing))
                        shellLens.animateTo(0.72f, tween(520, easing = SettingsShellPressSinkEasing))
                        shellLens.animateTo(0.88f, tween(620, easing = FastOutSlowInEasing))
                        shellLens.animateTo(0.74f, tween(680, easing = FastOutSlowInEasing))
                        shellLens.animateTo(
                            0.80f,
                            spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessVeryLow,
                            ),
                        )
                    }

                    var releasedInsideGesture = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updatePressCenter(tracked.position)
                            if (!tracked.pressed) {
                                releasedInsideGesture = true
                                break
                            }
                        }
                        if (event.changes.none { it.pressed }) {
                            releasedInsideGesture = true
                            break
                        }
                    }

                    pressScope.launch {
                        shellLens.stop()
                        val currentLens = shellLens.value.coerceIn(0f, 1f)
                        if (releasedInsideGesture && currentLens < 0.24f) {
                            shellLens.animateTo(0.34f, tween(120, easing = SettingsShellPressPulseEasing))
                        }
                        shellLens.animateTo(
                            0f,
                            tween(
                                if (releasedInsideGesture) 560 else 380,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    pressScope.launch {
                        shellPress.stop()
                        if (releasedInsideGesture) {
                            val current = shellPress.value.coerceIn(0f, 1.08f)
                            if (current < 0.46f) {
                                shellPress.animateTo(0.52f, tween(105, easing = SettingsShellPressPulseEasing))
                                shellPress.animateTo(-0.060f, tween(150, easing = SettingsShellPressReleaseEasing))
                            } else {
                                shellPress.animateTo(-0.065f, tween(220, easing = SettingsShellPressReleaseEasing))
                            }
                            shellPress.animateTo(
                                0f,
                                spring(
                                    dampingRatio = 0.66f,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        } else {
                            shellPress.animateTo(0f, tween(430, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + pressCompression * 0.014f - pressRebound * 0.004f
                scaleY = 1f - pressCompression * 0.022f + pressRebound * 0.008f
                translationY = pressCompression * 2.10f - pressRebound * 0.80f
                shadowElevation = pressCompression * 0.45f
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
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = Modifier.fillMaxSize(),
            role = GlassRole.Card,
            intensity = baseIntensity * glassIntensityScale,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .settingsRestrainedSelectedAccent(
                            progress = selectedProgress,
                            radius = radius,
                        )
                )
                SettingsTileTextContent(
                    title = title,
                    subtitle = subtitle,
                    value = value,
                    selectedProgress = selectedProgress,
                    modifier = Modifier.fillMaxSize(),
                )
                if (motionEnabled && surfaceOpticsPress > 0.001f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .settingsPersonalSpacePressOptics(
                                safePress = surfaceOpticsPress,
                                pressCenter = pressCenter,
                                rimFlowSeed = rimFlowSeed,
                                rimFlowDirection = rimFlowDirection,
                                rimFlowBand = rimFlowBand,
                                rimFlowStrength = rimFlowStrength,
                                radius = radius,
                                prismEdgeHighlight = prismEdgeHighlight,
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTileTextContent(
    title: String,
    subtitle: String,
    value: String,
    selectedProgress: Float,
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
                color = Color.White.copy(alpha = 0.91f + selectedProgress * 0.07f),
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.48f + selectedProgress * 0.10f),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsHairline(alpha = 0.095f + selectedProgress * 0.050f)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前",
                color = Color.White.copy(alpha = 0.34f + selectedProgress * 0.07f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                color = Color.White.copy(alpha = 0.66f + selectedProgress * 0.22f),
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

private fun Modifier.settingsPersonalSpacePressOptics(
    safePress: Float,
    pressCenter: Offset,
    rimFlowSeed: Float,
    rimFlowDirection: Float,
    rimFlowBand: Int,
    rimFlowStrength: Float,
    radius: Int,
    prismEdgeHighlight: Float,
): Modifier = drawWithContent {
    drawContent()
    val normalizedPress = safePress.coerceIn(0f, 1.08f)
    if (normalizedPress < 0.001f) return@drawWithContent
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val raw = (normalizedPress / 0.72f).coerceIn(0f, 1f)
    val p = settingsGlassSmoothStep(raw)
    val breath = settingsGlassSmoothStep((normalizedPress / 0.50f).coerceIn(0f, 1f)) *
        (1f - 0.11f * settingsGlassSmoothStep(((normalizedPress - 0.58f) / 0.28f).coerceIn(0f, 1f)))
    val compression = p * p
    val centerNorm = Offset(
        pressCenter.x.coerceIn(0f, 1f),
        pressCenter.y.coerceIn(0f, 1f),
    )
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
    val rimInset = 0.56.dp.toPx()
    val rimRadius = (radius.dp.toPx() - rimInset).coerceAtLeast(0f)
    val cornerRadius = CornerRadius(rimRadius, rimRadius)
    val rimSize = Size(
        (w - rimInset * 2f).coerceAtLeast(1f),
        (h - rimInset * 2f).coerceAtLeast(1f),
    )
    val maxSide = maxOf(w, h)
    val pressGlow = p

    fun nearEdge(distance: Float): Float =
        (1f - distance / 0.42f).coerceIn(0f, 1f) * pressGlow

    val topNear = nearEdge(centerNorm.y)
    val bottomNear = nearEdge(1f - centerNorm.y)
    val leftNear = nearEdge(centerNorm.x)
    val rightNear = nearEdge(1f - centerNorm.x)
    val edgeStroke = (0.74.dp + (0.26f * p).dp).toPx()
    val localEdgeStroke = (1.18.dp + (0.48f * p).dp).toPx()
    val flow = settingsGlassSmoothStep((normalizedPress / 0.62f).coerceIn(0f, 1f))
    val seedShift = (rimFlowSeed - 0.5f) * 0.36f
    val sweepX = if (rimFlowDirection >= 0f) {
        -0.24f + seedShift + flow * 1.42f
    } else {
        1.24f + seedShift - flow * 1.42f
    }
    val bandStartY = when (rimFlowBand % 4) {
        0 -> 0.02f
        1 -> 0.74f
        2 -> 0.10f
        else -> 0.18f
    }
    val bandEndY = when (rimFlowBand % 4) {
        0 -> 0.26f
        1 -> 0.98f
        2 -> 0.92f
        else -> 0.58f
    }
    val bandAlpha = breath * rimFlowStrength.coerceIn(0.70f, 1.45f)
    val prism = prismEdgeHighlight.coerceIn(0f, 2f)
    val prismSoft = prism * 0.55f

    val pressureField = Brush.radialGradient(
        listOf(
            Color(0xFFEFFFFF).copy(alpha = 0.066f * breath),
            Color(0xFFB8F7FF).copy(alpha = 0.032f * breath),
            Color(0xFF82E8FF).copy(alpha = 0.010f * breath),
            Color.Transparent,
        ),
        center,
        maxSide * (0.86f + 0.06f * p),
    )
    val broadHalo = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.021f * breath),
            Color(0xFFD8FFFF).copy(alpha = 0.014f * breath),
            Color.Transparent,
        ),
        Offset(w * 0.50f, h * 0.40f),
        maxSide * 1.18f,
    )
    val elasticSurfaceField = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color(0xFF102C66).copy(alpha = 0.006f * p),
            Color(0xFF030B1A).copy(alpha = 0.034f * compression),
        ),
        center,
        maxSide * (1.00f + 0.035f * p),
    )
    val lowerWeight = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF020815).copy(alpha = 0.044f * compression),
        ),
        h * 0.44f,
        h,
    )
    val ambientRim = Brush.radialGradient(
        listOf(
            Color(0xFFEFFFFF).copy(alpha = 0.052f * breath),
            Color(0xFF92FFF1).copy(alpha = (0.018f + 0.020f * prismSoft) * breath),
            Color(0xFFFF8BE8).copy(alpha = 0.014f * prismSoft * breath),
            Color.Transparent,
        ),
        center,
        maxSide * 0.74f,
    )
    val flowingRim = Brush.linearGradient(
        listOf(
            Color.Transparent,
            Color(0xFFFF6ADB).copy(alpha = 0.20f * prism * bandAlpha),
            Color.White.copy(alpha = 0.34f * bandAlpha),
            Color(0xFFFFE08A).copy(alpha = 0.18f * prism * bandAlpha),
            Color(0xFF62FFF0).copy(alpha = (0.14f + 0.16f * prism) * bandAlpha),
            Color(0xFF92A6FF).copy(alpha = 0.12f * prism * bandAlpha),
            Color.Transparent,
        ),
        Offset(w * (sweepX - 0.26f), h * bandStartY),
        Offset(w * (sweepX + 0.22f), h * bandEndY),
    )

    fun prismHalo(power: Float, white: Float, cyan: Float) = listOf(
        Color.White.copy(alpha = white * power),
        Color(0xFFFF7DE2).copy(alpha = 0.050f * prism * power),
        Color(0xFFFFE28A).copy(alpha = 0.036f * prism * power),
        Color(0xFF80FFF2).copy(alpha = cyan * power * (0.65f + prism * 0.35f)),
        Color.Transparent,
    )

    val topEdgeHalo = Brush.radialGradient(
        prismHalo(topNear, 0.23f, 0.072f),
        Offset(center.x, rimInset),
        maxSide * 0.38f,
    )
    val bottomEdgeHalo = Brush.radialGradient(
        prismHalo(bottomNear, 0.16f, 0.054f),
        Offset(center.x, h - rimInset),
        maxSide * 0.36f,
    )
    val leftEdgeHalo = Brush.radialGradient(
        prismHalo(leftNear, 0.18f, 0.060f),
        Offset(rimInset, center.y),
        maxSide * 0.34f,
    )
    val rightEdgeHalo = Brush.radialGradient(
        prismHalo(rightNear, 0.18f, 0.060f),
        Offset(w - rimInset, center.y),
        maxSide * 0.34f,
    )

    drawRect(broadHalo, blendMode = BlendMode.Screen)
    drawRect(pressureField, blendMode = BlendMode.Screen)
    drawRect(elasticSurfaceField, blendMode = BlendMode.Multiply)
    drawRect(lowerWeight, blendMode = BlendMode.Multiply)
    drawRoundRect(
        brush = ambientRim,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(edgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = flowingRim,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(0.82.dp.toPx() + 0.20.dp.toPx() * prism),
        blendMode = BlendMode.Plus,
    )
    drawRoundRect(
        brush = topEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = bottomEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = leftEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = rightEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
}

private fun Modifier.settingsRestrainedSelectedAccent(
    progress: Float,
    radius: Int,
): Modifier = drawWithCache {
    val p = progress.coerceIn(0f, 1f)
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val radiusPx = radius.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val outerInset = 0.72.dp.toPx()
    val innerInset = 2.30.dp.toPx()
    val outerSize = Size(
        (w - outerInset * 2f).coerceAtLeast(1f),
        (h - outerInset * 2f).coerceAtLeast(1f),
    )
    val innerSize = Size(
        (w - innerInset * 2f).coerceAtLeast(1f),
        (h - innerInset * 2f).coerceAtLeast(1f),
    )
    val surfaceLift = Brush.linearGradient(
        listOf(
            Color(0xFFBFFFF8).copy(alpha = 0.022f * p),
            Color(0xFF78CFFF).copy(alpha = 0.014f * p),
            Color.Transparent,
        ),
        Offset.Zero,
        Offset(w, h),
    )
    val outerRim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.18f * p),
            Color(0xFF8DFFF3).copy(alpha = 0.12f * p),
            Color.Transparent,
            Color(0xFF7E9DFF).copy(alpha = 0.07f * p),
        ),
        0f,
        h,
    )
    val innerRim = Brush.linearGradient(
        listOf(
            Color(0xFF8DFFF3).copy(alpha = 0.075f * p),
            Color.White.copy(alpha = 0.040f * p),
            Color.Transparent,
        ),
        Offset.Zero,
        Offset(w, h),
    )

    onDrawWithContent {
        drawRoundRect(
            brush = surfaceLift,
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen,
        )
        drawContent()
        if (p > 0.001f) {
            drawRoundRect(
                brush = outerRim,
                topLeft = Offset(outerInset, outerInset),
                size = outerSize,
                cornerRadius = CornerRadius(
                    (radiusPx - outerInset).coerceAtLeast(0f),
                    (radiusPx - outerInset).coerceAtLeast(0f),
                ),
                style = Stroke(0.82.dp.toPx()),
                blendMode = BlendMode.Screen,
            )
            drawRoundRect(
                brush = innerRim,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                cornerRadius = CornerRadius(
                    (radiusPx - innerInset).coerceAtLeast(0f),
                    (radiusPx - innerInset).coerceAtLeast(0f),
                ),
                style = Stroke(0.62.dp.toPx()),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

private fun settingsGlassSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
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
