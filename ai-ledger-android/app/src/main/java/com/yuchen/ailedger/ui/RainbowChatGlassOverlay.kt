package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalRainbowPrismStyle = compositionLocalOf { RainbowPrismStyle() }

@Composable
fun RainbowChatGlassOverlay(
    quality: RenderQuality,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    style: RainbowPrismStyle = LocalRainbowPrismStyle.current
) {
    val transition = rememberInfiniteTransition(label = "rainbow-chat-glass")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-continuous-phase"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0.37f,
        targetValue = 1.37f,
        animationSpec = infiniteRepeatable(
            animation = tween(14800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-continuous-phase-2"
    )
    val sweepPhase by transition.animateFloat(
        initialValue = 0.71f,
        targetValue = 1.71f,
        animationSpec = infiniteRepeatable(
            animation = tween(18600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-sweep-breath-phase"
    )

    val motionOn = quality.enableMotion && motionIntensity > 0.02f
    val t1 = if (motionOn) phase else 0.42f
    val t2 = if (motionOn) phase2 else 0.58f
    val t3 = if (motionOn) sweepPhase else 0.30f
    val radians1 = (t1 * 2f * PI).toFloat()
    val radians2 = (t2 * 2f * PI).toFloat()
    val radians3 = (t3 * 2f * PI).toFloat()

    val motion = motionIntensity.coerceIn(0f, 1.25f)
    val overall = style.overall.coerceIn(0f, 2f)
    val edge = style.edgeHighlight.coerceIn(0f, 2f)
    val minSweep = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val maxSweep = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val breath01 = rainbowSmoothStep(((sin(radians3) + 1f) * 0.5f).coerceIn(0f, 1f))
    val sweep = (minSweep + (maxSweep - minSweep) * breath01).coerceIn(0f, 1.46f)
    val sweepPower = rainbowSmoothStep(sweep / 1.46f)
    val base = (0.58f + motion * 0.15f).coerceIn(0.52f, 0.82f) * overall
    val bodyLoad = (base * (0.52f + sweepPower * 0.72f)).coerceIn(0f, 1.72f)
    val beamLoad = (base * (0.55f + sweepPower * 0.86f) * (0.72f + edge * 0.16f)).coerceIn(0f, 1.92f)
    val halo = style.rainbowHalo.coerceIn(0f, 2f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val c = CornerRadius(30f, 30f)
        val slowWave = rainbowSmoothStep(((sin(radians1 + radians2 * 0.37f) + 1f) * 0.5f).coerceIn(0f, 1f))
        val ax = 0.50f + 0.32f * cos(radians1)
        val ay = 0.28f + 0.17f * sin(radians1 * 0.73f + 0.80f)
        val bx = 0.54f + 0.30f * cos(radians2 + 1.70f)
        val by = 0.56f + 0.28f * sin(radians2 * 0.82f + 2.20f)
        val cx = 0.50f + 0.38f * cos(radians1 * 0.58f + radians2 * 0.32f)
        val cy = 0.50f + 0.32f * sin(radians2 * 0.64f + 1.10f)

        fun film(brush: Brush, enabled: Boolean = true) {
            if (enabled) {
                drawRoundRect(
                    brush = brush,
                    size = Size(w, h),
                    cornerRadius = c,
                    blendMode = BlendMode.Screen
                )
            }
        }

        film(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF62D8).copy(alpha = 0.064f * bodyLoad),
                    Color(0xFFFFD86E).copy(alpha = 0.044f * bodyLoad),
                    Color(0xFF55FFF0).copy(alpha = 0.060f * bodyLoad),
                    Color.Transparent
                ),
                center = Offset(w * ax.coerceIn(0.08f, 0.92f), h * ay.coerceIn(0.06f, 0.70f)),
                radius = maxOf(w, h) * (0.78f + 0.18f * slowWave)
            ),
            enabled = bodyLoad > 0.001f
        )
        film(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF62FFF0).copy(alpha = 0.046f * bodyLoad),
                    Color(0xFF7F95FF).copy(alpha = 0.043f * bodyLoad),
                    Color(0xFFFF78E4).copy(alpha = 0.030f * bodyLoad),
                    Color.Transparent
                ),
                center = Offset(w * bx.coerceIn(0.10f, 0.90f), h * by.coerceIn(0.18f, 0.92f)),
                radius = maxOf(w, h) * 0.82f
            ),
            enabled = bodyLoad > 0.001f
        )
        film(
            Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFE58A).copy(alpha = 0.032f * beamLoad),
                    Color(0xFF76FFF2).copy(alpha = 0.048f * beamLoad),
                    Color(0xFFFF7BE5).copy(alpha = 0.030f * beamLoad),
                    Color.Transparent
                ),
                start = Offset(w * (cx - 0.46f).coerceIn(-0.22f, 0.78f), h * (cy - 0.42f).coerceIn(-0.16f, 0.72f)),
                end = Offset(w * (cx + 0.48f).coerceIn(0.22f, 1.22f), h * (cy + 0.46f).coerceIn(0.26f, 1.18f))
            ),
            enabled = beamLoad > 0.001f
        )

        if (halo > 0.001f) {
            val haloLoad = (base * halo * (0.56f + sweepPower * 0.24f)).coerceIn(0f, 1.68f)
            film(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.040f * haloLoad),
                        Color(0xFF72FFF2).copy(alpha = 0.064f * haloLoad),
                        Color(0xFFFF76DE).copy(alpha = 0.046f * haloLoad),
                        Color(0xFF7B95FF).copy(alpha = 0.038f * haloLoad),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.74f + 0.05f * cos(radians3)), h * (0.62f + 0.08f * slowWave)),
                    radius = maxOf(w, h) * (0.60f + 0.08f * breath01)
                )
            )
        }
    }
}

private fun rainbowSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
