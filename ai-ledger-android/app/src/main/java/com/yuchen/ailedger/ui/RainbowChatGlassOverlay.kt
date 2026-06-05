package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

val LocalRainbowPrismStyle = compositionLocalOf { RainbowPrismStyle() }

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val RAINBOW_PHASE_A_SECONDS = 9.2
private const val RAINBOW_PHASE_B_SECONDS = 13.7
private const val RAINBOW_PHASE_C_SECONDS = 11.1
private const val RAINBOW_TAU = PI * 2.0
private const val RAINBOW_FRAME_DELAY_MS = 33L

@Composable
fun RainbowChatGlassOverlay(
    quality: RenderQuality,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    style: RainbowPrismStyle = LocalRainbowPrismStyle.current
) {
    val motionOn = quality.enableMotion && motionIntensity > 0.02f
    var elapsedNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(motionOn) {
        if (!motionOn) {
            elapsedNanos = 0L
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            elapsedNanos = (frameNanos - startNanos).coerceAtLeast(0L)
            delay(RAINBOW_FRAME_DELAY_MS)
        }
    }

    val elapsedSeconds = elapsedNanos / NANOS_PER_SECOND
    val radians1 = if (motionOn) {
        elapsedSeconds / RAINBOW_PHASE_A_SECONDS * RAINBOW_TAU
    } else {
        0.42 * RAINBOW_TAU
    }
    val radians2 = if (motionOn) {
        (elapsedSeconds / RAINBOW_PHASE_B_SECONDS + 0.37) * RAINBOW_TAU
    } else {
        0.58 * RAINBOW_TAU
    }
    val radians3 = if (motionOn) {
        (elapsedSeconds / RAINBOW_PHASE_C_SECONDS + 0.71) * RAINBOW_TAU
    } else {
        0.30 * RAINBOW_TAU
    }

    val base = (0.88f + motionIntensity.coerceIn(0f, 1.4f) * 0.16f).coerceIn(0.76f, 1.12f) * style.overall.coerceIn(0f, 2f)
    val minSweep = minOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val maxSweep = maxOf(style.sweepMin, style.sweepMax).coerceIn(0f, 2f)
    val breath01 = unitWave(radians3)
    val sweep = minSweep + (maxSweep - minSweep) * breath01
    val halo = style.rainbowHalo.coerceIn(0f, 2f)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val c = CornerRadius(30f, 30f)
            val slowWave = unitWave(radians1 + radians2 * 0.37)
            val ax = 0.50f + 0.34f * cosFloat(radians1)
            val ay = 0.28f + 0.18f * sinFloat(radians1 * 0.73 + 0.80)
            val bx = 0.54f + 0.32f * cosFloat(radians2 + 1.70)
            val by = 0.56f + 0.30f * sinFloat(radians2 * 0.82 + 2.20)
            val cx = 0.50f + 0.38f * cosFloat(radians1 * 0.58 + radians2 * 0.32)
            val cy = 0.50f + 0.34f * sinFloat(radians2 * 0.64 + 1.10)

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
                    center = Offset(w * (0.74f + 0.05f * cosFloat(radians3)), h * (0.62f + 0.08f * slowWave)),
                    radius = maxOf(w, h) * (0.62f + 0.08f * unitWave(radians3))
                ),
                halo
            )
        }

        AgentChatGlassTitleControls(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 54.dp, top = 7.dp)
        )
    }
}

private fun sinFloat(value: Double): Float = sin(value).toFloat()

private fun cosFloat(value: Double): Float = cos(value).toFloat()

private fun unitWave(value: Double): Float = ((sin(value) + 1.0) * 0.5).toFloat().coerceIn(0f, 1f)
