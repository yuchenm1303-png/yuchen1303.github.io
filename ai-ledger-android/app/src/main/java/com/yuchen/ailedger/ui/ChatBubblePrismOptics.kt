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
    val active = if (sending) 1f else 0.62f
    val cycle = phase * 2f * PI.toFloat()
    val sweep = if (fromUser) 1f - phase else phase
    val sweepX = -0.40f + sweep * 1.80f
    val radiusPx = radiusDp.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val accentA = if (failed) Color(0xFFFF9A9A) else if (fromUser) Color(0xFF9EB7FF) else Color(0xFF8DF9EA)
    val accentB = if (failed) Color(0xFFFFD166) else if (fromUser) Color(0xFFFF8FE7) else Color(0xFFFFF0A8)
    val accentC = if (fromUser) Color(0xFF76FFF1) else Color(0xFFFF72D2)

    val materialLoad = if (fromUser) 1.10f else 1.00f
    drawRoundRect(
        color = Color(0xFF07183F).copy(alpha = 0.100f * materialLoad),
        size = Size(w, h),
        cornerRadius = corner
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.070f * materialLoad),
                Color(0xFF8DF9EA).copy(alpha = 0.032f * materialLoad),
                Color(0xFF6E7BFF).copy(alpha = 0.030f * materialLoad),
                Color(0xFFFF7AD8).copy(alpha = 0.022f * materialLoad),
                Color(0xFF010A24).copy(alpha = 0.050f * materialLoad)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h * 1.05f)
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.078f),
                Color.White.copy(alpha = 0.024f),
                Color.Transparent,
                Color(0xFF00091F).copy(alpha = 0.050f)
            ),
            startY = 0f,
            endY = h
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.SrcOver
    )

    val softGlowCenter = if (fromUser) {
        Offset(w * (0.78f - 0.10f * sin(cycle * 0.83f)), h * 0.36f)
    } else {
        Offset(w * (0.24f + 0.12f * sin(cycle * 0.87f)), h * 0.30f)
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            listOf(
                Color.White.copy(alpha = 0.050f * motion),
                accentA.copy(alpha = 0.070f * motion),
                accentB.copy(alpha = 0.032f * motion),
                Color.Transparent
            ),
            center = softGlowCenter,
            radius = maxOf(w, h) * 0.58f
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    if (sending) {
        val bandX = -0.86f + phase * 2.38f + 0.070f * sin(cycle * 1.70f)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFFF58D2).copy(alpha = 0.070f * motion),
                    Color(0xFFFFF0A8).copy(alpha = 0.130f * motion),
                    Color(0xFF62FFF0).copy(alpha = 0.185f * motion),
                    Color(0xFF8EA2FF).copy(alpha = 0.116f * motion),
                    Color(0xFFFF82E4).copy(alpha = 0.080f * motion),
                    Color.Transparent
                ),
                start = Offset(w * (bandX - 0.70f), h * 1.18f),
                end = Offset(w * (bandX + 0.52f), -h * 0.24f)
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
    }

    drawContent()

    val inset = 0.74.dp.toPx()
    val rimSize = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f))
    val rimCorner = CornerRadius((radiusPx - inset).coerceAtLeast(1f), (radiusPx - inset).coerceAtLeast(1f))
    val pulse = if (sending) ((sin(cycle * 0.92f) + 1f) * 0.50f).coerceIn(0f, 1f) else 0.52f
    val prismPower = (0.38f + 0.45f * active + 0.22f * pulse) * motion

    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (sending) 0.28f else 0.20f),
                accentA.copy(alpha = if (sending) 0.22f else 0.145f),
                Color.Transparent,
                Color(0xFF000A28).copy(alpha = 0.090f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = if (sending) 1.05.dp.toPx() else 0.78.dp.toPx()),
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.Transparent,
                accentC.copy(alpha = 0.34f * prismPower),
                Color(0xFFFFF0A8).copy(alpha = 0.30f * prismPower),
                Color.White.copy(alpha = 0.24f * prismPower),
                accentB.copy(alpha = 0.34f * prismPower),
                accentA.copy(alpha = 0.25f * prismPower),
                Color.Transparent
            ),
            start = Offset(w * (sweepX - 0.44f), -h * 0.18f),
            end = Offset(w * (sweepX + 0.48f), h * 1.18f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = if (sending) 5.20.dp.toPx() else 3.80.dp.toPx()),
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.Transparent,
                accentC.copy(alpha = 0.52f * prismPower),
                Color.White.copy(alpha = 0.42f * prismPower),
                accentB.copy(alpha = 0.52f * prismPower),
                Color.Transparent
            ),
            start = Offset(w * (sweepX - 0.24f), -h * 0.05f),
            end = Offset(w * (sweepX + 0.30f), h * 1.05f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = if (sending) 2.10.dp.toPx() else 1.55.dp.toPx()),
        blendMode = BlendMode.Plus
    )

    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.110f),
                Color.Transparent,
                Color(0xFF000817).copy(alpha = 0.055f)
            ),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset(inset * 1.25f, inset * 1.25f),
        size = Size((w - inset * 2.5f).coerceAtLeast(1f), (h - inset * 2.5f).coerceAtLeast(1f)),
        cornerRadius = CornerRadius((radiusPx - inset * 1.25f).coerceAtLeast(1f), (radiusPx - inset * 1.25f).coerceAtLeast(1f)),
        style = Stroke(width = 0.56.dp.toPx()),
        blendMode = BlendMode.Screen
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
