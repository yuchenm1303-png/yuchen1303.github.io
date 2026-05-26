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
import kotlin.math.sin

@Composable
fun RainbowChatGlassOverlay(
    quality: RenderQuality,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    val motionEnabled = quality.enableMotion && motionIntensity > 0.02f
    val transition = rememberInfiniteTransition(label = "rainbow-chat-glass")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6800, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbow-chat-glass-phase"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbow-chat-glass-sweep"
    )
    val p = if (motionEnabled) phase else 0.22f
    val s = if (motionEnabled) sweep else 0.48f
    val strength = (0.98f + motionIntensity.coerceIn(0f, 1.4f) * 0.22f).coerceIn(0.82f, 1.26f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val corner = CornerRadius(30.dp.toPx(), 30.dp.toPx())
        val inset = 1.0.dp.toPx()
        val inner = 4.2.dp.toPx()
        val wave = ((sin(p * 6.28318f + 0.72f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val x = -0.36f + s * 1.72f
        val x2 = 1.20f - s * 1.58f

        // Broad rainbow coating: visible even on busy backgrounds.
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFFF66DF).copy(alpha = 0.118f * strength),
                    Color(0xFFFFD75F).copy(alpha = 0.082f * strength),
                    Color(0xFF42FFF0).copy(alpha = 0.112f * strength),
                    Color(0xFF7C95FF).copy(alpha = 0.084f * strength),
                    Color.Transparent
                ),
                center = Offset(w * (0.14f + 0.14f * wave), h * 0.07f),
                radius = maxOf(w, h) * 1.02f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )

        // Main diagonal prism sweep.
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF4EE1).copy(alpha = 0.230f * strength),
                    Color(0xFFFFD05D).copy(alpha = 0.184f * strength),
                    Color(0xFF42FFF0).copy(alpha = 0.246f * strength),
                    Color(0xFF85A0FF).copy(alpha = 0.188f * strength),
                    Color.Transparent
                ),
                start = Offset(w * (x - 0.40f), h * -0.12f),
                end = Offset(w * (x + 0.46f), h * 1.12f)
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2f, h - inset * 2f),
            cornerRadius = corner,
            style = Stroke(width = 1.75.dp.toPx()),
            blendMode = BlendMode.Plus
        )

        // Soft film band across the glass body, not only the edge.
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF77E6).copy(alpha = 0.070f * strength),
                    Color(0xFFFFE58A).copy(alpha = 0.058f * strength),
                    Color(0xFF68FFF0).copy(alpha = 0.076f * strength),
                    Color.Transparent
                ),
                start = Offset(w * (x - 0.18f), h * 0.02f),
                end = Offset(w * (x + 0.44f), h * 0.88f)
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )

        // Counter-sweep inner prism line.
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF54FFF0).copy(alpha = 0.128f * strength),
                    Color(0xFFFFF09A).copy(alpha = 0.104f * strength),
                    Color(0xFFFF67D8).copy(alpha = 0.112f * strength),
                    Color.Transparent
                ),
                start = Offset(w * (x2 - 0.28f), h * 0.02f),
                end = Offset(w * (x2 + 0.30f), h * 0.86f)
            ),
            topLeft = Offset(inner, inner),
            size = Size(w - inner * 2f, h - inner * 2f),
            cornerRadius = corner,
            style = Stroke(width = 1.15.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // Top coated edge: cold white + cyan + gold.
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.152f * strength),
                    Color(0xFFFFE88B).copy(alpha = 0.094f * strength),
                    Color(0xFF7CFFF3).copy(alpha = 0.128f * strength),
                    Color.Transparent,
                    Color(0xFFFF6EDC).copy(alpha = 0.046f * strength)
                ),
                startY = 0f,
                endY = h * 0.46f
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2f, h - inset * 2f),
            cornerRadius = corner,
            style = Stroke(width = 1.25.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // Lower-right lens halo, to make the rainbow readable at rest.
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.055f * strength),
                    Color(0xFF70FFF1).copy(alpha = 0.082f * strength),
                    Color(0xFFFF78DF).copy(alpha = 0.058f * strength),
                    Color(0xFF7C96FF).copy(alpha = 0.048f * strength),
                    Color.Transparent
                ),
                center = Offset(w * 0.78f, h * (0.68f + 0.08f * wave)),
                radius = maxOf(w, h) * 0.74f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
    }
}
