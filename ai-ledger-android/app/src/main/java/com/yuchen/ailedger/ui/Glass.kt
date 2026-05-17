package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .glassSkin(quality = quality, radius = radius, shimmer = shimmer, glassIntensity = glassIntensity)
    ) {
        content()
    }
}

@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(radius.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(quality = quality, radius = radius, shimmer = shimmer, glassIntensity = glassIntensity)
    ) {
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion) return 0.36f
    if (motionIntensity <= 0.02f) return 0.36f
    val transition = rememberInfiniteTransition(label = "glass-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (9200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glass-shimmer-value"
    )
    return shimmer
}

fun Modifier.glassSkin(
    quality: RenderQuality,
    radius: Int,
    shimmer: Float = 0f,
    glassIntensity: Float = 1f
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val baseAlpha = (quality.glassAlpha * glassIntensity).coerceIn(0.08f, 0.42f)

    return this
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = baseAlpha * 0.64f + 0.020f),
                    Color(0xFFDCEBFF).copy(alpha = baseAlpha * 0.30f + 0.012f),
                    Color(0xFF9FB8E8).copy(alpha = baseAlpha * 0.22f),
                    Color(0xFF111B36).copy(alpha = 0.08f)
                ),
                start = Offset.Zero,
                end = Offset(1200f, 1200f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val sheenX = (0.12f + 0.76f * shimmer) * w
            val ridgeY = h * 0.06f

            val topSheen = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * glassIntensity),
                    Color.White.copy(alpha = 0.060f * glassIntensity),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(0f, h * 0.46f)
            )

            val leftRefraction = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * 0.08f, h * 0.24f),
                radius = w * 0.58f
            )

            val rightGlow = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF9BC1FF).copy(alpha = 0.11f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * 0.90f, h * 0.88f),
                radius = w * 0.72f
            )

            val movingSheen = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.11f * glassIntensity else 0.05f),
                    Color.Transparent
                ),
                center = Offset(sheenX, ridgeY),
                radius = w * 0.46f
            )

            onDrawWithContent {
                drawRect(topSheen, blendMode = BlendMode.Plus)
                drawRect(leftRefraction, blendMode = BlendMode.Screen)
                drawRect(rightGlow, blendMode = BlendMode.Plus)
                drawRect(movingSheen, blendMode = BlendMode.Screen)
                drawContent()
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.38f * glassIntensity),
                    Color.White.copy(alpha = 0.10f * glassIntensity),
                    Color.White.copy(alpha = 0.24f * glassIntensity)
                )
            ),
            shape = shape
        )
}
