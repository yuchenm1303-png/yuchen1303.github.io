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
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6800, easing = LinearEasing), RepeatMode.Restart), label = "rainbow-phase")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "rainbow-sweep")
    val motionOn = quality.enableMotion && motionIntensity > 0.02f
    val p = if (motionOn) phase else 0.22f
    val s = if (motionOn) sweep else 0.48f
    val base = (0.98f + motionIntensity.coerceIn(0f, 1.4f) * 0.22f).coerceIn(0.82f, 1.26f) * style.overall.coerceIn(0f, 2f)
    val edge = style.edgeHighlight.coerceIn(0f, 2f)
    val diag = style.diagonalSweep.coerceIn(0f, 2f)
    val top = style.topCoating.coerceIn(0f, 2f)
    val halo = style.rainbowHalo.coerceIn(0f, 2f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val c = CornerRadius(30f, 30f)
        val wave = ((sin(p * 6.28318f + 0.72f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val x = -0.36f + s * 1.72f
        val x2 = 1.20f - s * 1.58f
        fun rr(brush: Brush) = drawRoundRect(brush = brush, size = Size(w, h), cornerRadius = c, blendMode = BlendMode.Screen)

        rr(Brush.radialGradient(
            listOf(
                Color(0xFFFF66DF).copy(alpha = 0.118f * base * halo),
                Color(0xFFFFD75F).copy(alpha = 0.082f * base * halo),
                Color(0xFF42FFF0).copy(alpha = 0.112f * base * halo),
                Color(0xFF7C95FF).copy(alpha = 0.084f * base * halo),
                Color.Transparent
            ),
            center = Offset(w * (0.14f + 0.14f * wave), h * 0.07f),
            radius = maxOf(w, h) * 1.02f
        ))
        rr(Brush.linearGradient(
            listOf(Color.Transparent, Color(0xFFFF4EE1).copy(alpha = 0.112f * base * diag), Color(0xFFFFD05D).copy(alpha = 0.094f * base * diag), Color(0xFF42FFF0).copy(alpha = 0.126f * base * diag), Color(0xFF85A0FF).copy(alpha = 0.098f * base * diag), Color.Transparent),
            start = Offset(w * (x - 0.42f), h * -0.14f),
            end = Offset(w * (x + 0.54f), h * 1.12f)
        ))
        rr(Brush.linearGradient(
            listOf(Color.Transparent, Color(0xFF54FFF0).copy(alpha = 0.070f * base * edge), Color(0xFFFFF09A).copy(alpha = 0.058f * base * edge), Color(0xFFFF67D8).copy(alpha = 0.064f * base * edge), Color.Transparent),
            start = Offset(w * (x2 - 0.26f), h * 0.04f),
            end = Offset(w * (x2 + 0.34f), h * 0.90f)
        ))
        rr(Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.086f * base * top), Color(0xFFFFE88B).copy(alpha = 0.058f * base * top), Color(0xFF7CFFF3).copy(alpha = 0.074f * base * top), Color.Transparent),
            startY = 0f,
            endY = h * 0.30f
        ))
        rr(Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.055f * base * halo), Color(0xFF70FFF1).copy(alpha = 0.082f * base * halo), Color(0xFFFF78DF).copy(alpha = 0.058f * base * halo), Color(0xFF7C96FF).copy(alpha = 0.048f * base * halo), Color.Transparent),
            center = Offset(w * 0.78f, h * (0.68f + 0.08f * wave)),
            radius = maxOf(w, h) * 0.74f
        ))
    }
}
