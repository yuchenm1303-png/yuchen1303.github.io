package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.AgentOverlayProgress
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private const val WEB_FAMILY_BLEND = 0.05f
private const val WEB_ASPECT = 0.71f
private const val WEB_OVERALL_SIZE = 1.65f
private const val WEB_WAIST = 0.97f
private const val WEB_CROSS_ANGLE = 1.80f
private const val WEB_SHOULDER = 0.64f
private const val WEB_TIP_ROUNDNESS = 1.12f
private const val WEB_LOBE_BALANCE = 0.60f
private const val WEB_VERTICAL_BALANCE = 0.26f
private const val WEB_ASYMMETRY = 0.26f
private const val WEB_TILT_DEGREES = -10f
private const val WEB_CENTER_PINCH = 0.20f
private const val WEB_RIBBON_WIDTH = 1.58f
private const val WEB_GLOW = 1.70f
private const val WEB_DISPERSION = 0.85f
private const val WEB_LOOP_SPEED = 1.69f
private const val WEB_HIGHLIGHT_LENGTH = 0.56f
private const val WEB_SPARK_INTENSITY = 3.20f
private const val WEB_SPARK_HORIZONTAL = 2.30f
private const val WEB_SPARK_VERTICAL = 2.10f
private const val WEB_SPARK_CORE = 1.90f
private const val WEB_SPARK_TWINKLE = 0.79f
private const val WEB_SPARK_SOFTNESS = 1.15f

private enum class AgentInfinityVisualState {
    Off,
    Standby,
    Running,
    Paused,
    Error
}

private data class AgentRibbonTheme(
    val start: Color,
    val middle: Color,
    val end: Color,
    val alpha: Float,
    val speed: Float
)

private val AgentOffTheme = AgentRibbonTheme(
    start = Color(0xFF687597),
    middle = Color(0xFF5C6685),
    end = Color(0xFF7F8BA9),
    alpha = 0.34f,
    speed = 0f
)

private val AgentStandbyTheme = AgentRibbonTheme(
    start = Color(0xFF66FFF0),
    middle = Color(0xFF5D84FF),
    end = Color(0xFFA469FF),
    alpha = 0.83f,
    speed = 0.42f
)

private val AgentRunningTheme = AgentRibbonTheme(
    start = Color(0xFF69FFF1),
    middle = Color(0xFF5784FF),
    end = Color(0xFFBA65FF),
    alpha = 1f,
    speed = 1f
)

private val AgentPausedTheme = AgentRibbonTheme(
    start = Color(0xFFFFDA89),
    middle = Color(0xFFA173FF),
    end = Color(0xFF6F8FFF),
    alpha = 0.88f,
    speed = 0f
)

private val AgentErrorTheme = AgentRibbonTheme(
    start = Color(0xFFFF7A97),
    middle = Color(0xFFD154FF),
    end = Color(0xFF7174FF),
    alpha = 0.90f,
    speed = 0.18f
)

@Composable
internal fun AgentChatHeaderOverlay(modifier: Modifier = Modifier) {
    // 外层绝对定位入口保留为空，避免按钮脱离聊天 OpenGL 大玻璃独立漂浮。
    // 实际标题栏控件由 AgentChatGlassTitleControls 在聊天玻璃内部绘制。
}

@Composable
internal fun AgentChatGlassTitleControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val agentEnabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    var overlayVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(overlayVisible, progress.updatedAt) {
        if (overlayVisible) {
            if (AgentOverlayService.canDrawOverlays(context)) {
                AgentOverlayService.ensureStarted(context.applicationContext)
            } else {
                overlayVisible = false
            }
        }
    }

    Row(
        modifier = modifier.height(39.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentInfinitySwitchCapsule(
            enabled = agentEnabled,
            progress = progress,
            onClick = { AgentRuntimeController.setEnabled(!agentEnabled) }
        )
        AgentHeaderSwitchPill(
            label = "浮窗",
            enabled = overlayVisible,
            activeColors = listOf(Color(0xEE8DFFF4), Color(0xCC9B73FF), Color(0xAA4FB6FF)),
            onClick = {
                val next = !overlayVisible
                if (next) {
                    val allowed = AgentOverlayService.requestPermissionIfNeeded(context.applicationContext)
                    if (allowed) {
                        overlayVisible = true
                        AgentOverlayService.ensureStarted(context.applicationContext)
                    } else {
                        Toast.makeText(context, "请开启悬浮窗权限，用来显示智能体执行进展", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    overlayVisible = false
                    AgentOverlayService.stop(context.applicationContext)
                }
            }
        )
    }
}

@Composable
private fun AgentInfinitySwitchCapsule(
    enabled: Boolean,
    progress: AgentOverlayProgress,
    onClick: () -> Unit
) {
    val visualState = when {
        !enabled -> AgentInfinityVisualState.Off
        progress.userTakeoverPaused -> AgentInfinityVisualState.Paused
        progress.running -> AgentInfinityVisualState.Running
        progress.status.contains("失败") ||
            progress.status.contains("错误") ||
            progress.status.contains("异常") -> AgentInfinityVisualState.Error
        else -> AgentInfinityVisualState.Standby
    }
    val targetTheme = visualState.theme()
    val activation by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (enabled) 560 else 420,
            easing = if (enabled) CubicBezierEasing(0.20f, 0.70f, 0.20f, 1f) else FastOutSlowInEasing
        ),
        label = "agent-infinity-activation"
    )
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val lightEnergy = remember { Animatable(if (enabled) 1f else 0f) }
    val trailReveal = remember { Animatable(if (enabled) 1f else 0f) }
    val sweep = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                scaleX.snapTo(1f)
                scaleX.animateTo(
                    targetValue = 1f,
                    animationSpec = if (enabled) {
                        keyframes {
                            durationMillis = 560
                            0.974f at 78
                            1.036f at 235
                            0.992f at 358
                            1.010f at 459
                            1f at 560
                        }
                    } else {
                        keyframes {
                            durationMillis = 420
                            1.015f at 76
                            0.981f at 202
                            1.006f at 302
                            1f at 420
                        }
                    }
                )
            }
            launch {
                scaleY.snapTo(1f)
                scaleY.animateTo(
                    targetValue = 1f,
                    animationSpec = if (enabled) {
                        keyframes {
                            durationMillis = 560
                            1.026f at 78
                            0.984f at 235
                            1.010f at 358
                            0.996f at 459
                            1f at 560
                        }
                    } else {
                        keyframes {
                            durationMillis = 420
                            0.988f at 76
                            1.017f at 202
                            0.996f at 302
                            1f at 420
                        }
                    }
                )
            }
            launch {
                if (enabled) {
                    lightEnergy.snapTo(0.08f)
                    lightEnergy.animateTo(1f, tween(560, easing = CubicBezierEasing(0.20f, 0.70f, 0.20f, 1f)))
                } else {
                    lightEnergy.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                }
            }
            launch {
                if (enabled) {
                    trailReveal.snapTo(0f)
                    trailReveal.animateTo(1f, tween(420, delayMillis = 100, easing = FastOutSlowInEasing))
                } else {
                    trailReveal.animateTo(0f, tween(310, easing = FastOutSlowInEasing))
                }
            }
            launch {
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(if (enabled) 520 else 360, easing = FastOutSlowInEasing))
            }
        }
    }

    var phase by remember { mutableFloatStateOf(0f) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled, visualState) {
        if (!enabled) return@LaunchedEffect
        var previousNanos = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previousNanos != 0L) {
                    val deltaSeconds = ((now - previousNanos).coerceAtMost(40_000_000L)) / 1_000_000_000f
                    phase = (phase + deltaSeconds * 0.285f * targetTheme.speed * WEB_LOOP_SPEED) % 1f
                }
                frameNanos = now
                previousNanos = now
            }
        }
    }

    val currentTheme = blendTheme(AgentOffTheme, targetTheme, activation)
    val glassShape = RoundedCornerShape(999.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val sweepWave = sin(PI.toFloat() * sweep.value).coerceAtLeast(0f)
    val sweepDirection = if (enabled) sweep.value else 1f - sweep.value

    Box(
        modifier = Modifier
            .width(128.dp)
            .height(39.dp)
            .graphicsLayer {
                this.scaleX = scaleX.value
                this.scaleY = scaleY.value
            }
            .drawBehind {
                val radius = size.height / 2f
                val aura = activation * (0.30f + 0.22f * lightEnergy.value)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF72FFF0).copy(alpha = 0.10f * aura),
                            Color(0xFF6574FF).copy(alpha = 0.16f * aura),
                            Color(0xFFA45AFF).copy(alpha = 0.10f * aura)
                        )
                    ),
                    topLeft = Offset(-6.dp.toPx(), -5.dp.toPx()),
                    size = Size(size.width + 12.dp.toPx(), size.height + 10.dp.toPx()),
                    cornerRadius = CornerRadius(radius + 8.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF7390FF).copy(alpha = 0.055f * activation),
                    topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                    size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                    cornerRadius = CornerRadius(radius + 3.dp.toPx())
                )
            }
            .clip(glassShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.055f + activation * 0.035f),
                        Color(0xFF52FFEA).copy(alpha = activation * 0.030f),
                        Color(0xFF8558FF).copy(alpha = 0.035f + activation * 0.060f),
                        Color(0xFF0A1132).copy(alpha = 0.80f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f + activation * 0.10f),
                shape = glassShape
            )
            .toggleable(
                value = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onValueChange = { onClick() }
            )
            .padding(start = 12.dp, end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "Agent",
                color = Color.White.copy(alpha = 0.62f + activation * 0.34f),
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            AgentInfinityRibbon(
                modifier = Modifier
                    .width(66.dp)
                    .height(31.dp),
                visualState = visualState,
                theme = currentTheme,
                phase = phase,
                timeSeconds = frameNanos / 1_000_000_000f,
                activation = activation,
                energy = lightEnergy.value,
                trailReveal = trailReveal.value
            )
        }
        if (sweepWave > 0.001f) {
            Canvas(Modifier.fillMaxSize()) {
                val centerX = size.width * (-0.35f + sweepDirection * 1.70f)
                val sweepWidth = size.width * 0.42f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.24f * sweepWave),
                            Color(0xFF74FFF0).copy(alpha = 0.15f * sweepWave),
                            Color.Transparent
                        ),
                        start = Offset(centerX - sweepWidth, 0f),
                        end = Offset(centerX + sweepWidth, size.height)
                    ),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun AgentInfinityRibbon(
    modifier: Modifier,
    visualState: AgentInfinityVisualState,
    theme: AgentRibbonTheme,
    phase: Float,
    timeSeconds: Float,
    activation: Float,
    energy: Float,
    trailReveal: Float
) {
    val geometryCache = remember { AgentInfinityGeometryCache() }
    Canvas(modifier = modifier) {
        val geometry = geometryCache.obtain(size.width, size.height)
        val base = min(size.width, size.height) * 0.090f * WEB_RIBBON_WIDTH
        val glow = 0.30f + (WEB_GLOW - 0.30f) * activation
        val dispersion = 0.10f + (WEB_DISPERSION - 0.10f) * activation
        drawStaticInfinityRibbon(
            geometry = geometry,
            base = base,
            theme = theme,
            glow = glow,
            dispersion = dispersion
        )

        val trailFraction = when (visualState) {
            AgentInfinityVisualState.Running -> min(0.54f, WEB_HIGHLIGHT_LENGTH / 0.80f)
            AgentInfinityVisualState.Standby -> min(0.24f, WEB_HIGHLIGHT_LENGTH / 1.80f)
            AgentInfinityVisualState.Paused -> 0.08f
            AgentInfinityVisualState.Error -> 0.09f
            AgentInfinityVisualState.Off -> 0f
        } * trailReveal
        val trailEnergy = when (visualState) {
            AgentInfinityVisualState.Running -> energy
            AgentInfinityVisualState.Standby -> energy * 0.72f
            AgentInfinityVisualState.Paused -> energy * 0.72f
            AgentInfinityVisualState.Error -> {
                val beat = max(0f, sin(timeSeconds * 3.2f)).pow(9)
                energy * (0.66f + 0.30f * beat)
            }
            AgentInfinityVisualState.Off -> 0f
        }
        if (trailFraction > 0.005f && trailEnergy > 0.005f) {
            val trailHead = if (visualState == AgentInfinityVisualState.Paused) 0.985f else phase
            drawInfinityTrail(
                geometry = geometry,
                headProgress = trailHead,
                lengthFraction = trailFraction,
                width = base * if (visualState == AgentInfinityVisualState.Running) 1.04f else 0.92f,
                theme = theme,
                intensity = trailEnergy
            )
        }

        if (visualState != AgentInfinityVisualState.Off && energy > 0.01f) {
            drawInfinitySpark(
                base = base,
                theme = theme,
                strength = 0.18f * energy,
                phase = phase,
                timeSeconds = timeSeconds
            )
        }
    }
}

private fun DrawScope.drawStaticInfinityRibbon(
    geometry: AgentInfinityGeometry,
    base: Float,
    theme: AgentRibbonTheme,
    glow: Float,
    dispersion: Float
) {
    val path = geometry.path()
    val gradient = ribbonGradient(theme)
    val strokeRound = { width: Float -> Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    drawPath(path, gradient, alpha = theme.alpha * 0.28f, style = strokeRound(base * 4.2f * glow.coerceAtLeast(0.75f)))
    drawPath(path, gradient, alpha = theme.alpha * 0.45f, style = strokeRound(base * 2.5f))
    drawPath(path, Color(0xFF0E1743), alpha = theme.alpha * 0.70f, style = strokeRound(base * 1.46f))
    drawPath(path, gradient, alpha = theme.alpha * 0.89f, style = strokeRound(base * 1.28f))
    drawPath(path, gradient, alpha = theme.alpha * 0.93f, style = strokeRound(base * 0.88f))
    drawPath(path, Color(0xFFE5FDFF), alpha = theme.alpha * 0.61f, style = strokeRound(base * 0.40f))
    drawPath(
        geometry.path(offset = -base * 0.36f * dispersion),
        Color(0xFF49FFF7),
        alpha = 0.40f * dispersion * theme.alpha,
        style = strokeRound(max(1f, base * 0.095f))
    )
    drawPath(
        geometry.path(offset = base * 0.38f * dispersion),
        Color(0xFFF268FF),
        alpha = 0.32f * dispersion * theme.alpha,
        style = strokeRound(max(1f, base * 0.095f))
    )
}

private fun DrawScope.drawInfinityTrail(
    geometry: AgentInfinityGeometry,
    headProgress: Float,
    lengthFraction: Float,
    width: Float,
    theme: AgentRibbonTheme,
    intensity: Float
) {
    val points = geometry.segmentPoints(
        headProgress = headProgress,
        lengthFraction = lengthFraction,
        sampleCount = max(42, (lengthFraction * 220f).roundToInt())
    )
    val gradient = ribbonGradient(theme)
    drawFadedTrail(points, width * 1.58f, gradient, 0.56f * intensity, layers = 5, fadeFraction = 0.34f)
    drawFadedTrail(points, width * 0.88f, gradient, 0.98f * intensity, layers = 9, fadeFraction = 0.34f)
    drawFadedTrail(points, width * 0.22f, Brush.linearGradient(listOf(Color.White, Color(0xFFE8FFFF))), 0.82f * intensity, layers = 9, fadeFraction = 0.34f)

    val head = points.lastOrNull() ?: return
    val radius = width * 1.11f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = intensity.coerceIn(0f, 1f)),
                themeColor(theme, headProgress, 0.76f * intensity),
                Color.Transparent
            ),
            center = head,
            radius = radius * 2.7f
        ),
        radius = radius * 2.7f,
        center = head
    )
}

private fun DrawScope.drawFadedTrail(
    points: List<Offset>,
    width: Float,
    brush: Brush,
    targetAlpha: Float,
    layers: Int,
    fadeFraction: Float
) {
    if (points.size < 2 || targetAlpha <= 0f) return
    val fadeSamples = max(2, ((points.size - 1) * fadeFraction).roundToInt())
    repeat(layers) { index ->
        val u0 = index / layers.toFloat()
        val u1 = (index + 1) / layers.toFloat()
        val smooth0 = u0 * u0 * (3f - 2f * u0)
        val smooth1 = u1 * u1 * (3f - 2f * u1)
        val alpha = targetAlpha * (smooth1 - smooth0)
        val start = min(points.size - 2, (fadeSamples * u0).roundToInt())
        val path = Path().apply {
            moveTo(points[start].x, points[start].y)
            for (i in start + 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = path,
            brush = brush,
            alpha = alpha.coerceIn(0f, 1f),
            style = Stroke(width = width, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawInfinitySpark(
    base: Float,
    theme: AgentRibbonTheme,
    strength: Float,
    phase: Float,
    timeSeconds: Float
) {
    val twinkle = max(
        0.05f,
        1f + WEB_SPARK_TWINKLE * (
            0.18f * sin(timeSeconds * 7.8f) +
                0.07f * sin(timeSeconds * 15.6f + phase * PI.toFloat() * 2f)
            )
    )
    val spark = min(4f, strength * twinkle * WEB_SPARK_INTENSITY)
    if (spark <= 0.01f) return
    val visible = min(1.6f, spark)
    val center = Offset(size.width * 0.5f, size.height * 0.52f)
    val horizontalLength = base * 5.3f * WEB_SPARK_HORIZONTAL * (1f + 0.12f * min(spark, 2f))
    val verticalLength = base * 4.0f * WEB_SPARK_VERTICAL * (1f + 0.10f * min(spark, 2f))
    val haloRadius = base * (0.58f + 0.78f * min(spark, 2f)) * WEB_SPARK_CORE

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = min(1f, 0.96f * visible)),
                Color(0xFFE1FFFF).copy(alpha = min(1f, 0.68f * visible)),
                themeColor(theme, 0.08f, min(1f, 0.34f * visible)),
                themeColor(theme, 0.72f, min(1f, 0.18f * visible)),
                Color.Transparent
            ),
            center = center,
            radius = haloRadius * 3.2f
        ),
        center = center,
        radius = haloRadius * 3.2f
    )
    drawSparkBeam(
        center = center,
        length = horizontalLength,
        thickness = base * 0.40f,
        vertical = false,
        theme = theme,
        alpha = 0.52f * spark,
        softness = WEB_SPARK_SOFTNESS
    )
    drawSparkBeam(
        center = center,
        length = verticalLength,
        thickness = base * 0.36f,
        vertical = true,
        theme = theme,
        alpha = 0.50f * spark,
        softness = WEB_SPARK_SOFTNESS
    )
    drawSparkBeam(
        center = center,
        length = horizontalLength * 0.82f,
        thickness = max(0.65f, base * 0.09f),
        vertical = false,
        theme = theme,
        alpha = 1.16f * spark,
        softness = 0.18f
    )
    drawSparkBeam(
        center = center,
        length = verticalLength * 0.80f,
        thickness = max(0.65f, base * 0.085f),
        vertical = true,
        theme = theme,
        alpha = 1.10f * spark,
        softness = 0.18f
    )
}

private fun DrawScope.drawSparkBeam(
    center: Offset,
    length: Float,
    thickness: Float,
    vertical: Boolean,
    theme: AgentRibbonTheme,
    alpha: Float,
    softness: Float
) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if (safeAlpha <= 0.002f) return
    val start: Offset
    val end: Offset
    val topLeft: Offset
    val beamSize: Size
    if (vertical) {
        start = Offset(center.x, center.y - length / 2f)
        end = Offset(center.x, center.y + length / 2f)
        topLeft = Offset(center.x - thickness / 2f, center.y - length / 2f)
        beamSize = Size(thickness, length)
    } else {
        start = Offset(center.x - length / 2f, center.y)
        end = Offset(center.x + length / 2f, center.y)
        topLeft = Offset(center.x - length / 2f, center.y - thickness / 2f)
        beamSize = Size(length, thickness)
    }
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                themeColor(theme, 0.05f, 0.06f * safeAlpha),
                Color(0xFFDAFFFF).copy(alpha = 0.38f * safeAlpha),
                Color.White.copy(alpha = safeAlpha),
                Color(0xFFFFF2FF).copy(alpha = 0.46f * safeAlpha),
                themeColor(theme, 0.72f, 0.07f * safeAlpha),
                Color.Transparent
            ),
            start = start,
            end = end
        ),
        topLeft = topLeft,
        size = beamSize
    )
    if (softness > 0.25f) {
        val softThickness = thickness * (1f + softness * 1.8f)
        val softTopLeft = if (vertical) {
            Offset(center.x - softThickness / 2f, center.y - length / 2f)
        } else {
            Offset(center.x - length / 2f, center.y - softThickness / 2f)
        }
        val softSize = if (vertical) Size(softThickness, length) else Size(length, softThickness)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    themeColor(theme, 0.10f, 0.035f * safeAlpha),
                    Color.White.copy(alpha = 0.09f * safeAlpha),
                    themeColor(theme, 0.70f, 0.035f * safeAlpha),
                    Color.Transparent
                ),
                start = start,
                end = end
            ),
            topLeft = softTopLeft,
            size = softSize
        )
    }
}

private fun DrawScope.ribbonGradient(theme: AgentRibbonTheme): Brush {
    return Brush.linearGradient(
        colors = listOf(
            lerp(theme.start, theme.middle, 0.08f),
            lerp(theme.start, theme.middle, 0.88f),
            lerp(theme.middle, theme.end, 0.72f),
            lerp(theme.end, theme.start, 0.52f)
        ),
        start = Offset(size.width * 0.13f, size.height * 0.18f),
        end = Offset(size.width * 0.88f, size.height * 0.82f)
    )
}

private fun AgentInfinityVisualState.theme(): AgentRibbonTheme = when (this) {
    AgentInfinityVisualState.Off -> AgentOffTheme
    AgentInfinityVisualState.Standby -> AgentStandbyTheme
    AgentInfinityVisualState.Running -> AgentRunningTheme
    AgentInfinityVisualState.Paused -> AgentPausedTheme
    AgentInfinityVisualState.Error -> AgentErrorTheme
}

private fun blendTheme(from: AgentRibbonTheme, to: AgentRibbonTheme, fraction: Float): AgentRibbonTheme {
    val t = fraction.coerceIn(0f, 1f)
    return AgentRibbonTheme(
        start = lerp(from.start, to.start, t),
        middle = lerp(from.middle, to.middle, t),
        end = lerp(from.end, to.end, t),
        alpha = from.alpha + (to.alpha - from.alpha) * t,
        speed = to.speed
    )
}

private fun themeColor(theme: AgentRibbonTheme, progress: Float, alpha: Float): Color {
    val normalized = ((progress % 1f) + 1f) % 1f
    val color = if (normalized < 0.48f) {
        lerp(theme.start, theme.middle, normalized / 0.48f)
    } else {
        lerp(theme.middle, theme.end, (normalized - 0.48f) / 0.52f)
    }
    return color.copy(alpha = alpha.coerceIn(0f, 1f))
}

private data class AgentInfinityGeometry(val points: List<Offset>) {
    fun path(offset: Float = 0f): Path {
        val result = Path()
        if (points.isEmpty()) return result
        points.forEachIndexed { index, point ->
            val shifted = if (offset == 0f) point else offsetPoint(index, offset)
            if (index == 0) result.moveTo(shifted.x, shifted.y) else result.lineTo(shifted.x, shifted.y)
        }
        result.close()
        return result
    }

    fun segmentPoints(headProgress: Float, lengthFraction: Float, sampleCount: Int): List<Offset> {
        if (points.isEmpty()) return emptyList()
        val count = points.size
        val headIndex = (((headProgress % 1f) + 1f) % 1f) * count
        val lengthSamples = max(8f, min(count * 0.58f, lengthFraction * count))
        return List(sampleCount + 1) { index ->
            val q = index / sampleCount.toFloat()
            pointAtIndex(headIndex - lengthSamples + lengthSamples * q)
        }
    }

    private fun pointAtIndex(index: Float): Offset {
        val count = points.size
        val wrapped = ((index % count) + count) % count
        val firstIndex = floor(wrapped).toInt().coerceIn(0, count - 1)
        val fraction = wrapped - firstIndex
        val first = points[firstIndex]
        val second = points[(firstIndex + 1) % count]
        return Offset(
            x = first.x + (second.x - first.x) * fraction,
            y = first.y + (second.y - first.y) * fraction
        )
    }

    private fun offsetPoint(index: Int, offset: Float): Offset {
        val count = points.size
        val previous = points[(index - 1 + count) % count]
        val next = points[(index + 1) % count]
        val dx = next.x - previous.x
        val dy = next.y - previous.y
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        val point = points[index]
        return Offset(
            x = point.x - dy / length * offset,
            y = point.y + dx / length * offset
        )
    }
}

private class AgentInfinityGeometryCache {
    private var width = Float.NaN
    private var height = Float.NaN
    private var geometry = AgentInfinityGeometry(emptyList())

    fun obtain(width: Float, height: Float): AgentInfinityGeometry {
        if (this.width == width && this.height == height && geometry.points.isNotEmpty()) return geometry
        this.width = width
        this.height = height
        geometry = buildAgentInfinityGeometry(width, height)
        return geometry
    }
}

private fun buildAgentInfinityGeometry(width: Float, height: Float): AgentInfinityGeometry {
    val rawCount = 260
    val raw = List(rawCount + 1) { index ->
        val t = index / rawCount.toFloat() * PI.toFloat() * 2f
        agentInfinityPoint(t, width, height)
    }
    val cumulative = FloatArray(raw.size)
    for (index in 1 until raw.size) {
        val dx = raw[index].x - raw[index - 1].x
        val dy = raw[index].y - raw[index - 1].y
        cumulative[index] = cumulative[index - 1] + sqrt(dx * dx + dy * dy)
    }
    val total = cumulative.last().coerceAtLeast(0.0001f)
    val uniformCount = 340
    var segmentIndex = 0
    val uniform = List(uniformCount) { sampleIndex ->
        val distance = total * sampleIndex / uniformCount.toFloat()
        while (segmentIndex < raw.size - 2 && cumulative[segmentIndex + 1] < distance) segmentIndex++
        val span = (cumulative[segmentIndex + 1] - cumulative[segmentIndex]).coerceAtLeast(0.0001f)
        val fraction = ((distance - cumulative[segmentIndex]) / span).coerceIn(0f, 1f)
        val first = raw[segmentIndex]
        val second = raw[segmentIndex + 1]
        Offset(
            x = first.x + (second.x - first.x) * fraction,
            y = first.y + (second.y - first.y) * fraction
        )
    }
    return AgentInfinityGeometry(uniform)
}

private fun agentInfinityPoint(t: Float, width: Float, height: Float): Offset {
    val sine = sin(t)
    val cosine = kotlin.math.cos(t)
    val absoluteSine = abs(sine)
    val side = tanh(sine * 4.6f)
    val left = (1f - side) / 2f
    val right = (1f + side) / 2f
    val denominator = 1f + WEB_FAMILY_BLEND * (0.30f + 0.85f * WEB_WAIST) * cosine * cosine
    var normalizedX = sine / denominator
    var normalizedY = sine * cosine / denominator
    val pinchCurve = 0.52f + 0.48f * absoluteSine.pow(0.42f + WEB_CENTER_PINCH * 1.12f)
    val shoulderCurve = 0.74f + 0.26f * absoluteSine.pow(0.30f + WEB_SHOULDER * 1.25f)
    val crossLocal = 1f + (WEB_CROSS_ANGLE - 1f) * (1f - absoluteSine).pow(2.3f)
    val tipLocal = 1f + (WEB_TIP_ROUNDNESS - 1f) * absoluteSine.pow(4f)
    normalizedY *= pinchCurve * shoulderCurve * crossLocal * tipLocal
    val xAmplitude = width * 0.335f * WEB_ASPECT * WEB_OVERALL_SIZE *
        (1f + left * WEB_ASYMMETRY * 0.48f - right * WEB_ASYMMETRY * 0.22f) *
        (1f + side * WEB_LOBE_BALANCE * 0.22f)
    val yAmplitude = height * 0.205f * WEB_OVERALL_SIZE *
        (1f + right * WEB_ASYMMETRY * 0.28f - left * WEB_ASYMMETRY * 0.11f) *
        (1f + side * WEB_LOBE_BALANCE * 0.10f)
    var x = normalizedX * xAmplitude
    var y = normalizedY * yAmplitude
    y *= 1f + (if (y >= 0f) WEB_VERTICAL_BALANCE else -WEB_VERTICAL_BALANCE) * 0.25f
    y += side * (-height * 0.034f * WEB_ASYMMETRY) * WEB_OVERALL_SIZE
    x += sin(2f * t) * width * 0.024f * (0.35f + 0.65f * WEB_SHOULDER) * WEB_OVERALL_SIZE
    val angle = WEB_TILT_DEGREES * PI.toFloat() / 180f
    val cosineAngle = kotlin.math.cos(angle)
    val sineAngle = sin(angle)
    normalizedX = x * cosineAngle - y * sineAngle
    normalizedY = x * sineAngle + y * cosineAngle
    return Offset(width * 0.5f + normalizedX, height * 0.52f + normalizedY)
}

@Composable
private fun AgentHeaderSwitchPill(
    label: String,
    enabled: Boolean,
    activeColors: List<Color>,
    onClick: () -> Unit
) {
    val active by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "$label-header-switch-active"
    )
    val knobOffset by animateFloatAsState(
        targetValue = if (enabled) 10f else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "$label-header-switch-knob"
    )

    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) activeColors else listOf(
                        Color.White.copy(alpha = 0.075f),
                        Color.White.copy(alpha = 0.030f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.58f + active * 0.38f),
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(23.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.16f - active * 0.035f)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (2f + knobOffset).dp)
                    .size(9.dp)
                    .graphicsLayer {
                        scaleX = 0.92f + active * 0.12f
                        scaleY = 0.92f + active * 0.12f
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (enabled) Color(0xFFF3FFFC) else Color(0xFF9EA8C5))
            )
        }
        Text(
            text = if (enabled) "开" else "关",
            color = Color.White.copy(alpha = 0.50f + active * 0.36f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
