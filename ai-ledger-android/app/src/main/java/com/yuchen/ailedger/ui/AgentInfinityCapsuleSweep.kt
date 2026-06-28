package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun AgentInfinityCapsuleSweep(
    enabled: Boolean,
    sweep: Float,
    modifier: Modifier = Modifier
) {
    val wave = sin(PI.toFloat() * sweep).coerceAtLeast(0f)
    if (wave <= 0.001f) return
    val position = if (enabled) sweep else 1f - sweep
    Canvas(modifier) {
        val centerX = size.width * (-0.35f + position * 1.70f)
        val sweepWidth = size.width * 0.42f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.54f * wave),
                    Color(0xFF74FFF0).copy(alpha = 0.28f * wave),
                    Color.Transparent
                ),
                start = Offset(centerX - sweepWidth, 0f),
                end = Offset(centerX + sweepWidth, size.height)
            ),
            cornerRadius = CornerRadius(size.height / 2f)
        )
    }
}
