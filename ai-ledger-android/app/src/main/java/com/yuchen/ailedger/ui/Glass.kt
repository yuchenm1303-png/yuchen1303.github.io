package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun GlassPanel(
    quality: RenderQuality,
    radius: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .glassSkin(quality, radius)
    ) {
        content()
    }
}

@Composable
fun PressableGlass(
    quality: RenderQuality,
    radius: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "press-scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(radius.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(quality, radius)
    ) {
        content()
    }
}

fun Modifier.glassSkin(quality: RenderQuality, radius: Int): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this
        .background(
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = quality.glassAlpha + 0.06f),
                    Color.White.copy(alpha = quality.glassAlpha * 0.52f),
                    Color(0xFF7BA7FF).copy(alpha = quality.glassAlpha * 0.28f)
                )
            ),
            shape = shape
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.24f)
                )
            ),
            shape = shape
        )
}
