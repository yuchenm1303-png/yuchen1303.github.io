package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun AgentInfinityCapsuleBackground(
    enabled: Boolean,
    active: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val radius = size.height / 2f
        drawRoundRect(
            color = if (enabled) Color(0x40121A3A) else Color(0x2C121A3A),
            cornerRadius = CornerRadius(radius)
        )
        val capsuleColors = if (enabled) {
            listOf(Color(0x388DFFF4), Color(0x309B73FF), Color(0x284FB6FF))
        } else {
            listOf(Color.White.copy(alpha = 0.055f), Color.White.copy(alpha = 0.020f))
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(capsuleColors),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.065f),
                    0.42f to Color.White.copy(alpha = 0.012f),
                    0.72f to Color(0xFF8DFFF4).copy(alpha = 0.035f * active),
                    1f to Color.Transparent
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.09f + 0.04f * active),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 0.7.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
