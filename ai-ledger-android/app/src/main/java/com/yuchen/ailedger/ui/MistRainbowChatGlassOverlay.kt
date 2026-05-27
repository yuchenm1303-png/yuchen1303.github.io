package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MistRainbowChatGlassOverlay(
    quality: RenderQuality,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    val style = LocalRainbowPrismStyle.current
    val transition = rememberInfiniteTransition(label = "chat-rainbow-mist-overlay")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(13200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "chat-rainbow-mist-phase"
    )
    val driftPhase by transition.animateFloat(
        initialValue = 0.23f,
        targetValue = 1.23f,
        animationSpec = infiniteRepeatable(animation = tween(21400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "chat-rainbow-mist-drift"
    )
    val breathPhase by transition.animateFloat(
        initialValue = 0.61f,
        targetValue = 1.61f,
        animationSpec = infiniteRepeatable(animation = tween(18600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "chat-rainbow-mist-breath"
    )

    val motionOn = quality.enableMotion && motionIntensity > 0.02f
    val t = if (motionOn) phase else 0.18f
    val d = if (motionOn) driftPhase else 0.37f
    val b = if (motionOn) breathPhase else 0.52f
    val cycle = t * 2f * PI.toFloat()
    val drift = d * 2f * PI.toFloat()
    val breath = smoothMistStep(((sin(b * 2f * PI.toFloat()) + 1f) * 0.5f).coerceIn(0f, 1f))
    val motion = motionIntensity.coerceIn(0f, 1f)
    val visualPresence = (0.46f + motion * 0.54f).coerceIn(0.46f, 1f)
    val overall = style.overall.coerceIn(0f, 2f)
    val edge = style.edgeHighlight.coerceIn(0f, 2f)
    val minSweep = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val maxSweep = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val sweep = minSweep + (maxSweep - minSweep) * breath
    val sweepPower = smoothMistStep((sweep / 2f).coerceIn(0f, 1f))
    val halo = style.rainbowHalo.coerceIn(0f, 2f)
    val mistLoad = (visualPresence * overall * (0.70f + 0.18f * sweepPower)).coerceIn(0f, 1.78f)
    val veilLoad = (visualPresence * overall * (0.48f + 0.34f * sweepPower)).coerceIn(0f, 1.68f)
    val haloLoad = (visualPresence * overall * halo * (0.50f + 0.18f * sweepPower)).coerceIn(0f, 1.80f)
    val rimLoad = (visualPresence * overall * (0.36f + edge * 0.28f + sweepPower * 0.28f)).coerceIn(0f, 1.72f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val maxSide = maxOf(w, h)
        val corner = CornerRadius(30.dp.toPx(), 30.dp.toPx())
        val mistA = Offset(
            x = w * (-0.22f + 0.08f * sin(cycle * 0.61f + drift * 0.27f)),
            y = h * (0.18f + 0.20f * cos(drift * 0.73f + 0.90f))
        )
        val mistB = Offset(
            x = w * (1.18f + 0.07f * cos(cycle * 0.53f + 1.70f)),
            y = h * (0.76f + 0.22f * sin(drift * 0.67f + 2.10f))
        )
        val haloCenter = Offset(
            x = w * (0.42f + 0.18f * sin(drift * 0.47f + 0.40f)),
            y = h * (1.18f + 0.08f * cos(cycle * 0.41f + 1.20f))
        )
        val sweepA = 0.46f + 0.28f * sin(cycle * 0.71f + drift * 0.21f)
        val sweepB = 0.54f + 0.34f * cos(cycle * 0.43f + drift * 0.33f)

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF74D9).copy(alpha = 0.038f * mistLoad),
                    Color(0xFFFFE59A).copy(alpha = 0.024f * mistLoad),
                    Color(0xFF79FFF1).copy(alpha = 0.034f * mistLoad),
                    Color.Transparent
                ),
                center = mistA,
                radius = maxSide * 1.22f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.SrcOver
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF76FFF1).copy(alpha = 0.034f * mistLoad),
                    Color(0xFF93A3FF).copy(alpha = 0.035f * mistLoad),
                    Color(0xFFFF8FE7).copy(alpha = 0.022f * mistLoad),
                    Color.Transparent
                ),
                center = mistB,
                radius = maxSide * 1.30f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.SrcOver
        )
        if (haloLoad > 0.001f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.024f * haloLoad),
                        Color(0xFFFFB2EA).copy(alpha = 0.036f * haloLoad),
                        Color(0xFF8FFFF4).copy(alpha = 0.034f * haloLoad),
                        Color(0xFFA9B1FF).copy(alpha = 0.024f * haloLoad),
                        Color.Transparent
                    ),
                    center = haloCenter,
                    radius = maxSide * 1.10f
                ),
                size = Size(w, h),
                cornerRadius = corner,
                blendMode = BlendMode.SrcOver
            )
        }
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFEAA8).copy(alpha = 0.014f * veilLoad),
                    Color(0xFF79FFF2).copy(alpha = 0.026f * veilLoad),
                    Color(0xFFFF82DC).copy(alpha = 0.016f * veilLoad),
                    Color.Transparent
                ),
                start = Offset(w * (sweepA - 0.78f), -h * 0.34f),
                end = Offset(w * (sweepA + 0.76f), h * 1.30f)
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.SrcOver
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF9BA8FF).copy(alpha = 0.012f * veilLoad),
                    Color(0xFFFF70D7).copy(alpha = 0.020f * veilLoad),
                    Color(0xFF7CFFF1).copy(alpha = 0.018f * veilLoad),
                    Color.Transparent
                ),
                start = Offset(w * (sweepB + 0.50f), -h * 0.20f),
                end = Offset(w * (sweepB - 0.58f), h * 1.20f)
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.SrcOver
        )
        val inset = 0.74.dp.toPx()
        val rimSize = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f))
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF64D8).copy(alpha = 0.090f * rimLoad),
                    Color(0xFFFFE27A).copy(alpha = 0.062f * rimLoad),
                    Color(0xFF67FFF0).copy(alpha = 0.100f * rimLoad),
                    Color(0xFF9CA7FF).copy(alpha = 0.066f * rimLoad),
                    Color.Transparent
                ),
                start = Offset(w * (sweepA - 0.40f), h * -0.06f),
                end = Offset(w * (sweepA + 0.46f), h * 1.04f)
            ),
            topLeft = Offset(inset, inset),
            size = rimSize,
            cornerRadius = CornerRadius((30.dp.toPx() - inset).coerceAtLeast(1f), (30.dp.toPx() - inset).coerceAtLeast(1f)),
            style = Stroke(width = 0.76.dp.toPx() + 0.34.dp.toPx() * sweepPower),
            blendMode = BlendMode.Plus
        )
    }
}

private fun smoothMistStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
