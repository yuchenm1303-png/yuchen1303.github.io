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
        animationSpec = infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbow-chat-glass-phase"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4300, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbow-chat-glass-sweep"
    )
    val p = if (motionEnabled) phase else 0.18f
    val s = if (motionEnabled) sweep else 0.42f
    val strength = (0.58f + motionIntensity.coerceIn(0f, 1.4f) * 0.16f).coerceIn(0.42f, 0.80f)

    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val corner = CornerRadius(30.dp.toPx(), 30.dp.toPx())
        val inset = 1.1.dp.toPx()
        val inner = 4.8.dp.toPx()
        val wave = ((sin(p * 6.28318f + 0.72f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val x = -0.34f + s * 1.68f
        val x2 = 1.18f - s * 1.54f

        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFFF7AE5).copy(alpha = 0.040f * strength),
                    Color(0xFFFFE27A).copy(alpha = 0.026f * strength),
                    Color(0xFF62FFF0).copy(alpha = 0.036f * strength),
                    Color(0xFF7EA2FF).copy(alpha = 0.028f * strength),
                    Color.Transparent
                ),
                center = Offset(w * (0.16f + 0.12f * wave), h * 0.08f),
                radius = maxOf(w, h) * 0.92f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF5FE0).copy(alpha = 0.100f * strength),
                    Color(0xFFFFD766).copy(alpha = 0.078f * strength),
                    Color(0xFF65FFF1).copy(alpha = 0.106f * strength),
                    Color(0xFF8CA5FF).copy(alpha = 0.082f * strength),
                    Color.Transparent
                ),
                start = Offset(w * (x - 0.32f), h * -0.06f),
                end = Offset(w * (x + 0.38f), h * 1.06f)
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2f, h - inset * 2f),
            cornerRadius = corner,
            style = Stroke(width = 0.95.dp.toPx()),
            blendMode = BlendMode.Plus
        )

        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF68FFF0).copy(alpha = 0.050f * strength),
                    Color(0xFFFFF0A8).copy(alpha = 0.040f * strength),
                    Color(0xFFFF76DB).copy(alpha = 0.044f * strength),
                    Color.Transparent
                ),
                start = Offset(w * (x2 - 0.26f), h * 0.04f),
                end = Offset(w * (x2 + 0.28f), h * 0.82f)
            ),
            topLeft = Offset(inner, inner),
            size = Size(w - inner * 2f, h - inner * 2f),
            cornerRadius = corner,
            style = Stroke(width = 0.55.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.052f * strength),
                    Color(0xFFFFEFA2).copy(alpha = 0.028f * strength),
                    Color(0xFF9DFFF4).copy(alpha = 0.035f * strength),
                    Color.Transparent,
                    Color(0xFFFF7EE0).copy(alpha = 0.016f * strength)
                ),
                startY = 0f,
                endY = h * 0.42f
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2f, h - inset * 2f),
            cornerRadius = corner,
            style = Stroke(width = 0.72.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}
