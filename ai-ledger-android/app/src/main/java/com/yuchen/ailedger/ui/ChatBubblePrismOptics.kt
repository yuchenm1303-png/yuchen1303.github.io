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
    val active = if (sending) 1f else 0.52f
    val cycle = phase * 2f * PI.toFloat()
    val sweep = if (fromUser) 1f - phase else phase
    val sweepX = -0.32f + sweep * 1.62f
    val radiusPx = radiusDp.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val accentA = if (failed) Color(0xFFFF9A9A) else if (fromUser) Color(0xFF9EB7FF) else Color(0xFF8DF9EA)
    val accentB = if (failed) Color(0xFFFFD166) else if (fromUser) Color(0xFFFF8FE7) else Color(0xFFFFF0A8)
    val accentC = if (fromUser) Color(0xFF76FFF1) else Color(0xFFFF72D2)

    val mistAlpha = if (fromUser) 0.060f else 0.048f
    drawRoundRect(
        color = Color.White.copy(alpha = mistAlpha * (0.72f + 0.28f * motion)),
        size = Size(w, h),
        cornerRadius = corner
    )

    if (sending) {
        val bandX = -0.76f + phase * 2.22f + 0.055f * sin(cycle * 1.70f)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF58D2).copy(alpha = 0.034f * motion),
                    Color(0xFFFFF0A8).copy(alpha = 0.078f * motion),
                    Color(0xFF62FFF0).copy(alpha = 0.112f * motion),
                    Color(0xFF8EA2FF).copy(alpha = 0.070f * motion),
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
    val borderAlpha = if (sending) 0.225f else 0.145f
    drawRoundRect(
        color = Color.White.copy(alpha = borderAlpha),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = if (sending) 0.88.dp.toPx() else 0.62.dp.toPx())
    )

    val pulse = if (sending) ((sin(cycle * 0.92f) + 1f) * 0.50f).coerceIn(0f, 1f) else 0.44f
    val prismPower = (0.24f + 0.38f * active + 0.14f * pulse) * motion
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.Transparent,
                accentC.copy(alpha = (0.16f + 0.24f * active) * prismPower),
                Color.White.copy(alpha = (0.10f + 0.18f * active) * prismPower),
                accentB.copy(alpha = (0.14f + 0.22f * active) * prismPower),
                accentA.copy(alpha = (0.08f + 0.12f * active) * prismPower),
                Color.Transparent
            ),
            start = Offset(w * (sweepX - 0.30f), 0f),
            end = Offset(w * (sweepX + 0.34f), h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = if (sending) 1.30.dp.toPx() else 0.86.dp.toPx()),
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
