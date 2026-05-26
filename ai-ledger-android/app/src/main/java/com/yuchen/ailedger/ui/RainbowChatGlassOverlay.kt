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
            animation = tween(9200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-continuous-phase"
    )
    val phase2 by transition.animateFloat(
        initialValue = 0.37f,
        targetValue = 1.37f,
        animationSpec = infiniteRepeatable(
            animation = tween(13700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-continuous-phase-2"
    )
    val phase3 by transition.animateFloat(
        initialValue = 0.71f,
        targetValue = 1.71f,
        animationSpec = infiniteRepeatable(
            animation = tween(11100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbow-continuous-phase-3"
    )

    val motionOn = quality.enableMotion && motionIntensity > 0.02f
    val t1 = if (motionOn) phase else 0.42f
    val t2 = if (motionOn) phase2 else 0.58f
    val t3 = if (motionOn) phase3 else 0.30f
    val radians1 = (t1 * 2f * PI).toFloat()
    val radians2 = (t2 * 2f * PI).toFloat()
    val radians3 = (t3 * 2f * PI).toFloat()
    val base = (0.88f + motionIntensity.coerceIn(0f, 1.4f) * 0.16f).coerceIn(0.76f, 1.12f) * style.overall.coerceIn(0f, 2f)
    val minSweep = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val maxSweep = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val breath01 = ((sin(radians3) + 1f) * 0.5f).coerceIn(0f, 1f)
    val sweep = minSweep + (maxSweep - minSweep) * breath01
    val halo = style.rainbowHalo.coerceIn(0f, 2f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val c = CornerRadius(30f, 30f)
        val slowWave = ((sin(radians1 + radians2 * 0.37f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val ax = 0.50f + 0.34f * cos(radians1)
        val ay = 0.28f + 0.18f * sin(radians1 * 0.73f + 0.80f)
        val bx = 0.54f + 0.32f * cos(radians2 + 1.70f)
        val by = 0.56f + 0.30f * sin(radians2 * 0.82f + 2.20f)
        val cx = 0.50f + 0.38f * cos(radians1 * 0.58f + radians2 * 0.32f)
        val cy = 0.50f + 0.34f * sin(radians2 * 0.64f + 1.10f)

        fun film(brush: Brush, alpha: Float = 1f) {
            if (alpha > 0.001f) {
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
                    Color(0xFFFF62D8).copy(alpha = 0.070f * base * sweep),
                    Color(0xFFFFD86E).copy(alpha = 0.050f * base * sweep),
                    Color(0xFF55FFF0).copy(alpha = 0.066f * base * sweep),
                    Color.Transparent
                ),
                center = Offset(w * ax.coerceIn(0.08f, 0.92f), h * ay.coerceIn(0.06f, 0.70f)),
                radius = maxOf(w, h) * (0.78f + 0.22f * slowWave)
            ),
            sweep
        )
        film(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF62FFF0).copy(alpha = 0.052f * base * sweep),
                    Color(0xFF7F95FF).copy(alpha = 0.050f * base * sweep),
                    Color(0xFFFF78E4).copy(alpha = 0.035f * base * sweep),
                    Color.Transparent
                ),
                center = Offset(w * bx.coerceIn(0.10f, 0.90f), h * by.coerceIn(0.18f, 0.92f)),
                radius = maxOf(w, h) * 0.88f
            ),
            sweep
        )
        film(
            Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFE58A).copy(alpha = 0.030f * base * sweep),
                    Color(0xFF76FFF2).copy(alpha = 0.038f * base * sweep),
                    Color(0xFFFF7BE5).copy(alpha = 0.026f * base * sweep),
                    Color.Transparent
                ),
                start = Offset(w * (cx - 0.44f).coerceIn(-0.20f, 0.80f), h * (cy - 0.42f).coerceIn(-0.16f, 0.72f)),
                end = Offset(w * (cx + 0.46f).coerceIn(0.20f, 1.20f), h * (cy + 0.46f).coerceIn(0.26f, 1.18f))
            ),
            sweep
        )

        film(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.045f * base * halo),
                    Color(0xFF72FFF2).copy(alpha = 0.074f * base * halo),
                    Color(0xFFFF76DE).copy(alpha = 0.054f * base * halo),
                    Color(0xFF7B95FF).copy(alpha = 0.044f * base * halo),
                    Color.Transparent
                ),
                center = Offset(w * (0.74f + 0.05f * cos(radians3)), h * (0.62f + 0.08f * slowWave)),
                radius = maxOf(w, h) * (0.62f + 0.08f * ((sin(radians3) + 1f) * 0.5f))
            ),
            halo
        )
    }
}
