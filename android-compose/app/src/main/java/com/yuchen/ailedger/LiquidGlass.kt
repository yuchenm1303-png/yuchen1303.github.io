package com.yuchen.ailedger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    strength: GlassStrength = GlassStrength.Medium,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val baseAlpha = when (strength) {
        GlassStrength.Soft -> 0.052f
        GlassStrength.Medium -> 0.074f
        GlassStrength.Strong -> 0.104f
    }
    val lineAlpha = when (strength) {
        GlassStrength.Soft -> 0.23f
        GlassStrength.Medium -> 0.31f
        GlassStrength.Strong -> 0.40f
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 28.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF92DFFF).copy(alpha = 0.18f),
                spotColor = Color(0xFFBCA8FF).copy(alpha = 0.22f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = baseAlpha + 0.105f),
                        Color(0xFFE9F8FF).copy(alpha = baseAlpha + 0.020f),
                        Color(0xFF7488D9).copy(alpha = baseAlpha * 0.72f),
                        Color(0xFF2D214A).copy(alpha = baseAlpha * 1.35f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, 460f)
                )
            )
            .border(1.2.dp, Color.White.copy(alpha = lineAlpha), shape)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.26f),
                            Color(0xFFDDF7FF).copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(90f, 18f),
                        radius = 340f
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF73E7FF).copy(alpha = 0.12f),
                            Color(0xFFBCA8FF).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(720f, 160f),
                        radius = 460f
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color(0xFF050815).copy(alpha = 0.18f)
                        )
                    )
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(1.5.dp)
                .clip(RoundedCornerShape(corner - 1.5.dp))
                .border(1.dp, Color.White.copy(alpha = lineAlpha * 0.36f), RoundedCornerShape(corner - 1.5.dp))
        )
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun LiquidGlassChip(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val color = accent ?: Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = if (accent == null) 0.15f else 0.22f),
                        Color.White.copy(alpha = 0.055f),
                        Color(0xFFBCA8FF).copy(alpha = 0.075f)
                    )
                )
            )
            .border(1.dp, color.copy(alpha = if (accent == null) 0.28f else 0.48f), RoundedCornerShape(999.dp))
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        content()
    }
}

enum class GlassStrength { Soft, Medium, Strong }
