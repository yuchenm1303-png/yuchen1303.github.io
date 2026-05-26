package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
    val driftA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rainbow-ambient-drift-a"
    )
    val driftB by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(13200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rainbow-ambient-drift-b"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(8600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rainbow-ambient-pulse"
    )

    val motionOn = quality.enableMotion && motionIntensity > 0.02f
    val a = if (motionOn) driftA else 0.42f
    val b = if (motionOn) driftB else 0.58f
    val p = if (motionOn) pulse else 0.46f
    val base = (0.88f + motionIntensity.coerceIn(0f, 1.4f) * 0.16f).coerceIn(0.76f, 1.12f) * style.overall.coerceIn(0f, 2f)
    val sweep = style.diagonalSweep.coerceIn(0f, 2f)
    val halo = style.rainbowHalo.coerceIn(0f, 2f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val c = CornerRadius(30f, 30f)
        val slowWave = ((sin((a + b) * 3.14159f) + 1f) * 0.5f).coerceIn(0f, 1f)
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

        // Large-area random-looking coating sweep. No hard diagonal edge, no jump-to-start.
        film(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF62D8).copy(alpha = 0.070f * base * sweep),
                    Color(0xFFFFD86E).copy(alpha = 0.050f * base * sweep),
                    Color(0xFF55FFF0).copy(alpha = 0.066f * base * sweep),
                    Color.Transparent
                ),
                center = Offset(w * (0.10f + 0.72f * a), h * (0.10f + 0.32f * b)),
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
                center = Offset(w * (0.82f - 0.50f * b), h * (0.72f - 0.44f * a)),
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
                start = Offset(w * (0.06f + 0.28f * a), h * (0.08f + 0.10f * b)),
                end = Offset(w * (0.88f - 0.22f * b), h * (0.92f - 0.18f * a))
            ),
            sweep
        )

        // Lens-like rainbow halo remains independent from the edge prism.
        film(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.045f * base * halo),
                    Color(0xFF72FFF2).copy(alpha = 0.074f * base * halo),
                    Color(0xFFFF76DE).copy(alpha = 0.054f * base * halo),
                    Color(0xFF7B95FF).copy(alpha = 0.044f * base * halo),
                    Color.Transparent
                ),
                center = Offset(w * (0.74f + 0.05f * p), h * (0.62f + 0.08f * slowWave)),
                radius = maxOf(w, h) * (0.62f + 0.08f * p)
            ),
            halo
        )
    }
}
