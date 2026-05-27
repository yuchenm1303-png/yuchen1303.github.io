package com.yuchen.ailedger.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

fun Modifier.chatBubblePrismSurface(
    phase: Float,
    fromUser: Boolean,
    sending: Boolean,
    failed: Boolean,
    motionIntensity: Float,
    radiusDp: Int
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val active = if (sending) 1f else 0.58f
    val cycle = phase * 2f * PI.toFloat()
    val sweep = if (fromUser) 1f - phase else phase
    val sweepX = -0.30f + sweep * 1.58f
    val radiusPx = radiusDp.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val accentA = if (failed) Color(0xFFFF9A9A) else if (fromUser) Color(0xFF9EB7FF) else Color(0xFF8DF9EA)
    val accentB = if (failed) Color(0xFFFFD166) else if (fromUser) Color(0xFFFF8FE7) else Color(0xFFFFF0A8)
    val accentC = if (fromUser) Color(0xFF76FFF1) else Color(0xFFFF72D2)
    val center = if (fromUser) {
        Offset(w * (0.82f - 0.08f * sin(cycle)), h * 0.70f)
    } else {
        Offset(w * (0.16f + 0.08f * sin(cycle)), h * (0.25f + 0.10f * sin(cycle + 1.4f)))
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = (0.035f + 0.050f * active) * motion),
                accentA.copy(alpha = (0.040f + 0.095f * active) * motion),
                accentB.copy(alpha = (0.018f + 0.062f * active) * motion),
                Color.Transparent
            ),
            center = center,
            radius = maxOf(w, h) * (0.34f + 0.18f * active)
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(
                Color.Transparent,
                Color(0xFF000817).copy(alpha = 0.018f + 0.026f * active),
                Color(0xFF00030A).copy(alpha = 0.025f + 0.036f * if (sending) 1f else 0f)
            ),
            center = Offset(w * 0.50f, h * 0.60f),
            radius = maxOf(w, h) * 0.92f
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Multiply
    )
    if (sending) {
        val bandX = -0.74f + phase * 2.18f + 0.055f * sin(cycle * 1.70f)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF58D2).copy(alpha = 0.040f * motion),
                    Color(0xFFFFF0A8).copy(alpha = 0.086f * motion),
                    Color(0xFF62FFF0).copy(alpha = 0.128f * motion),
                    Color(0xFF8EA2FF).copy(alpha = 0.082f * motion),
                    Color.Transparent
                ),
                start = Offset(w * (bandX - 0.62f), h * 1.16f),
                end = Offset(w * (bandX + 0.44f), -h * 0.22f)
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
    }
    drawContent()
    val inset = 0.68.dp.toPx()
    val rimSize = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f))
    val rimCorner = CornerRadius((radiusPx - inset).coerceAtLeast(1f), (radiusPx - inset).coerceAtLeast(1f))
    val pulse = if (sending) ((sin(cycle * 0.92f) + 1f) * 0.50f).coerceIn(0f, 1f) else 0.42f
    val prismPower = (0.18f + 0.38f * active + 0.12f * pulse) * motion
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.050f * motion),
                accentB.copy(alpha = 0.110f * prismPower),
                accentC.copy(alpha = 0.150f * prismPower),
                Color.Transparent,
                accentA.copy(alpha = 0.116f * prismPower),
                Color.White.copy(alpha = 0.030f * motion)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.58.dp.toPx() + 0.34.dp.toPx() * active),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.Transparent,
                accentC.copy(alpha = (0.18f + 0.30f * active) * motion),
                Color.White.copy(alpha = (0.11f + 0.24f * active) * motion),
                accentB.copy(alpha = (0.15f + 0.24f * active) * motion),
                Color.Transparent
            ),
            start = Offset(w * (sweepX - 0.22f), 0f),
            end = Offset(w * (sweepX + 0.28f), h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.62.dp.toPx() + 0.70.dp.toPx() * active),
        blendMode = BlendMode.Plus
    )
}

fun Modifier.thinkingPearlSurface(color: Color, wave: Float, index: Int): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val corner = CornerRadius(w * 0.50f, h * 0.50f)
    val accent = when (index % 3) {
        0 -> Color(0xFF8DF9EA)
        1 -> Color(0xFFFFF0A8)
        else -> Color(0xFFFF8FE7)
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.42f + 0.20f * wave),
                color.copy(alpha = 0.28f + 0.18f * wave),
                accent.copy(alpha = 0.30f + 0.26f * wave),
                Color.Transparent
            ),
            center = Offset(w * 0.34f, h * 0.26f),
            radius = maxOf(w, h) * 0.86f
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.32f + 0.16f * wave),
                accent.copy(alpha = 0.38f + 0.24f * wave),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(0.45.dp.toPx(), 0.45.dp.toPx()),
        size = Size(w - 0.90.dp.toPx(), h - 0.90.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(width = 0.55.dp.toPx()),
        blendMode = BlendMode.Plus
    )
}
