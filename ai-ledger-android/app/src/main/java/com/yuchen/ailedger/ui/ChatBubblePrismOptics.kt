package com.yuchen.ailedger.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    drawChatBubblePrismMaterial(
        rect = Rect(Offset.Zero, size),
        phase = phase,
        fromUser = fromUser,
        sending = sending,
        failed = failed,
        motionIntensity = motionIntensity,
        radiusDp = radiusDp,
        layerAlpha = 1f,
        thinkingSweepStrength = if (sending) 1f else 0f
    )
    drawContent()
}

fun DrawScope.drawChatBubblePrismMaterial(
    rect: Rect,
    phase: Float,
    fromUser: Boolean,
    sending: Boolean,
    failed: Boolean,
    motionIntensity: Float,
    radiusDp: Int,
    layerAlpha: Float = 1f,
    thinkingSweepStrength: Float = if (sending) 1f else 0f
) {
    val a = layerAlpha.coerceIn(0f, 1f)
    if (a <= 0.001f || rect.width <= 1f || rect.height <= 1f) return

    val l = rect.left
    val t = rect.top
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val sweepStrength = if (sending) thinkingSweepStrength.coerceIn(0f, 1f) else 0f
    val active = 0.58f + 0.42f * sweepStrength
    val phase01 = phase.coerceIn(0f, 1f)
    val cycle = phase01 * 2f * PI.toFloat()
    val sendingSweep = -0.28f + phase01 * 1.62f
    val sendingSatin = -0.20f + phase01 * 1.44f
    val sweepWave = if (sweepStrength > 0.001f) sendingSweep else 0.50f + 0.42f * sin(cycle)
    val satinWave = if (sweepStrength > 0.001f) sendingSatin else 0.50f + 0.28f * sin(cycle + 1.35f)
    val glowWave = sin(cycle + if (fromUser) 0.72f else 1.08f)
    val radiusPx = radiusDp.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val accentA = if (failed) Color(0xFFFF9A9A) else if (fromUser) Color(0xFF9EB7FF) else Color(0xFF8DF9EA)
    val accentB = if (failed) Color(0xFFFFD166) else if (fromUser) Color(0xFFFF8FE7) else Color(0xFFFFF0A8)
    val accentC = if (fromUser) Color(0xFF76FFF1) else Color(0xFFFF72D2)
    val topLeft = Offset(l, t)
    val size = Size(w, h)

    val materialLoad = if (fromUser) 1.08f else 1.0f
    drawRoundRect(
        color = Color(0xFF07183F).copy(alpha = 0.090f * materialLoad * a),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.058f * materialLoad * a),
                Color(0xFF86FFF0).copy(alpha = 0.026f * materialLoad * a),
                Color(0xFF6675FF).copy(alpha = 0.026f * materialLoad * a),
                Color(0xFFFF7AD8).copy(alpha = 0.018f * materialLoad * a),
                Color(0xFF010A24).copy(alpha = 0.046f * materialLoad * a)
            ),
            start = Offset(l, t),
            end = Offset(l + w, t + h * 1.10f)
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.064f * a),
                Color.White.copy(alpha = 0.020f * a),
                Color.Transparent,
                Color(0xFF00091F).copy(alpha = 0.046f * a)
            ),
            startY = t,
            endY = t + h
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        blendMode = BlendMode.SrcOver
    )

    val glowCenter = if (fromUser) {
        Offset(l + w * (0.72f - 0.08f * glowWave), t + h * 0.30f)
    } else {
        Offset(l + w * (0.28f + 0.08f * glowWave), t + h * 0.30f)
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.036f * motion * a),
                accentA.copy(alpha = 0.052f * motion * a),
                accentB.copy(alpha = 0.024f * motion * a),
                Color.Transparent
            ),
            center = glowCenter,
            radius = maxOf(w, h) * 0.56f
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    if (sweepStrength > 0.001f) {
        val bandX = -0.72f + phase01 * 2.34f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF58D2).copy(alpha = 0.115f * motion * a * sweepStrength),
                    Color(0xFFFFD66E).copy(alpha = 0.190f * motion * a * sweepStrength),
                    Color.White.copy(alpha = 0.285f * motion * a * sweepStrength),
                    Color(0xFF62FFF0).copy(alpha = 0.255f * motion * a * sweepStrength),
                    Color(0xFF8EA2FF).copy(alpha = 0.165f * motion * a * sweepStrength),
                    Color.Transparent
                ),
                start = Offset(l + w * (bandX - 0.64f), t + h * 1.16f),
                end = Offset(l + w * (bandX + 0.54f), t - h * 0.22f)
            ),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.030f * motion * a * sweepStrength),
                    Color.White.copy(alpha = 0.210f * motion * a * sweepStrength),
                    Color.White.copy(alpha = 0.360f * motion * a * sweepStrength),
                    Color(0xFFB9FFF7).copy(alpha = 0.250f * motion * a * sweepStrength),
                    Color.Transparent
                ),
                start = Offset(l + w * (bandX - 0.30f), t + h * 1.03f),
                end = Offset(l + w * (bandX + 0.20f), t - h * 0.08f)
            ),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
            blendMode = BlendMode.Plus
        )
    }

    val inset = 0.74.dp.toPx()
    val rimTopLeft = Offset(l + inset, t + inset)
    val rimSize = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f))
    val rimCorner = CornerRadius((radiusPx - inset).coerceAtLeast(1f), (radiusPx - inset).coerceAtLeast(1f))
    val prismPower = (0.44f + 0.46f * active) * motion

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = mixBubbleValue(0.155f, 0.260f, sweepStrength) * a),
                accentA.copy(alpha = mixBubbleValue(0.090f, 0.170f, sweepStrength) * a),
                Color(0xFF000A28).copy(alpha = 0.070f * a),
                Color.White.copy(alpha = mixBubbleValue(0.058f, 0.135f, sweepStrength) * a)
            ),
            start = Offset(l, t),
            end = Offset(l + w, t + h)
        ),
        topLeft = rimTopLeft,
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = mixBubbleValue(0.72f, 1.10f, sweepStrength).dp.toPx()),
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                accentC.copy(alpha = mixBubbleValue(0.190f, 0.320f, sweepStrength) * prismPower * a),
                Color(0xFFFFF0A8).copy(alpha = mixBubbleValue(0.165f, 0.285f, sweepStrength) * prismPower * a),
                Color.White.copy(alpha = mixBubbleValue(0.150f, 0.260f, sweepStrength) * prismPower * a),
                accentB.copy(alpha = mixBubbleValue(0.190f, 0.320f, sweepStrength) * prismPower * a),
                accentA.copy(alpha = mixBubbleValue(0.125f, 0.210f, sweepStrength) * prismPower * a),
                Color.Transparent
            ),
            start = Offset(l + w * (sweepWave - 0.38f), t - h * 0.12f),
            end = Offset(l + w * (sweepWave + 0.42f), t + h * 1.12f)
        ),
        topLeft = rimTopLeft,
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = mixBubbleValue(2.05f, 3.20f, sweepStrength).dp.toPx()),
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                accentC.copy(alpha = mixBubbleValue(0.340f, 0.500f, sweepStrength) * prismPower * a),
                Color.White.copy(alpha = mixBubbleValue(0.410f, 0.610f, sweepStrength) * prismPower * a),
                accentB.copy(alpha = mixBubbleValue(0.350f, 0.520f, sweepStrength) * prismPower * a),
                Color.Transparent
            ),
            start = Offset(l + w * (sweepWave - 0.20f), t - h * 0.03f),
            end = Offset(l + w * (sweepWave + 0.25f), t + h * 1.03f)
        ),
        topLeft = rimTopLeft,
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = mixBubbleValue(0.92f, 1.50f, sweepStrength).dp.toPx()),
        blendMode = BlendMode.Plus
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = mixBubbleValue(0.112f, 0.190f, sweepStrength) * a),
                Color(0xFF8DF9EA).copy(alpha = mixBubbleValue(0.064f, 0.125f, sweepStrength) * a),
                Color.Transparent
            ),
            start = Offset(l + w * (satinWave - 0.30f), t),
            end = Offset(l + w * (satinWave + 0.20f), t + h * 0.36f)
        ),
        topLeft = Offset(l + inset * 1.15f, t + inset * 1.15f),
        size = Size((w - inset * 2.3f).coerceAtLeast(1f), (h - inset * 2.3f).coerceAtLeast(1f)),
        cornerRadius = CornerRadius((radiusPx - inset * 1.15f).coerceAtLeast(1f), (radiusPx - inset * 1.15f).coerceAtLeast(1f)),
        style = Stroke(width = mixBubbleValue(0.50f, 0.70f, sweepStrength).dp.toPx()),
        blendMode = BlendMode.Screen
    )
}

private fun mixBubbleValue(base: Float, active: Float, amount: Float): Float {
    val t = amount.coerceIn(0f, 1f)
    return base + (active - base) * t
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
            colors = listOf(
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
            colors = listOf(
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
