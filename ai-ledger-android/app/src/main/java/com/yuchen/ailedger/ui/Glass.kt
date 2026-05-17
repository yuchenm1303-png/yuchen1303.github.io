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
import androidx.compose.ui.draw.shadow
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
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = (18 * glassIntensity).dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF4FA7FF).copy(alpha = 0.12f),
                spotColor = Color(0xFFB7A6FF).copy(alpha = 0.18f)
            )
            .clip(shape)
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
    val shape = RoundedCornerShape(radius.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = (14 * glassIntensity).dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF7BCBFF).copy(alpha = 0.10f),
                spotColor = Color(0xFFC7B8FF).copy(alpha = 0.16f)
            )
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(quality = quality, radius = radius, shimmer = shimmer, glassIntensity = glassIntensity)
    ) {
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion) return 0.30f
    if (motionIntensity <= 0.02f) return 0.30f
    val transition = rememberInfiniteTransition(label = "glass-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (11200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
    val baseAlpha = (quality.glassAlpha * glassIntensity).coerceIn(0.045f, 0.24f)
    val edgeAlpha = (0.34f * glassIntensity).coerceIn(0.18f, 0.48f)

    return this
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = baseAlpha * 0.62f + 0.012f),
                    Color(0xFFD8ECFF).copy(alpha = baseAlpha * 0.24f + 0.008f),
                    Color(0xFF8FA7E8).copy(alpha = baseAlpha * 0.16f),
                    Color(0xFF10172B).copy(alpha = 0.16f)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 1300f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val sheenX = (0.08f + 0.84f * shimmer) * w
            val sheenY = h * 0.08f

            val topRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f * glassIntensity),
                    Color.White.copy(alpha = 0.060f * glassIntensity),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.34f
            )

            val leftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.15f * glassIntensity),
                    Color(0xFFBFEFFF).copy(alpha = 0.055f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * 0.08f, h * 0.15f),
                radius = w * 0.68f
            )

            val violetBody = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFB7A6FF).copy(alpha = 0.10f * glassIntensity),
                    Color(0xFF6087FF).copy(alpha = 0.030f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * 0.92f, h * 0.88f),
                radius = w * 0.82f
            )

            val movingSpecular = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.075f * glassIntensity else 0.035f),
                    Color(0xFFD7F8FF).copy(alpha = if (quality.enableMotion) 0.036f * glassIntensity else 0.018f),
                    Color.Transparent
                ),
                center = Offset(sheenX, sheenY),
                radius = w * 0.42f
            )

            val bottomDepth = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF040713).copy(alpha = 0.15f * glassIntensity)
                ),
                startY = h * 0.54f,
                endY = h
            )

            onDrawWithContent {
                drawRect(topRim, blendMode = BlendMode.Plus)
                drawRect(leftLens, blendMode = BlendMode.Screen)
                drawRect(violetBody, blendMode = BlendMode.Plus)
                drawRect(movingSpecular, blendMode = BlendMode.Screen)
                drawRect(bottomDepth, blendMode = BlendMode.Multiply)
                drawContent()
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = edgeAlpha),
                    Color(0xFFD8F7FF).copy(alpha = 0.16f * glassIntensity),
                    Color(0xFFB7A6FF).copy(alpha = 0.18f * glassIntensity),
                    Color.White.copy(alpha = 0.24f * glassIntensity)
                ),
                start = Offset(0f, 0f),
                end = Offset(800f, 1200f)
            ),
            shape = shape
        )
        .border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.16f * glassIntensity),
            shape = shape
        )
}