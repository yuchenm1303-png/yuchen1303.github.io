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

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0.68f, 1.02f, 0.72f, 20),
    Card(0.88f, 1.04f, 0.80f, 14),
    Chip(1.02f, 1.18f, 0.88f, 9),
    Nav(0.80f, 1.10f, 0.88f, 18),
    Floating(1.12f, 1.28f, 1.05f, 22)
}

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
    content: @Composable () -> Unit
) {
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    Box(
        modifier = modifier.glassSkin(
            quality = quality,
            radius = radius,
            shimmer = shimmer,
            glassIntensity = glassIntensity,
            role = role
        )
    ) { content() }
}

@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Chip,
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
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(
                quality = quality,
                radius = radius,
                shimmer = shimmer,
                glassIntensity = glassIntensity,
                role = role
            )
    ) { content() }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion) return 0.18f
    if (motionIntensity <= 0.02f) return 0.18f
    val transition = rememberInfiniteTransition(label = "glass-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((13200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
    glassIntensity: Float = 1f,
    role: GlassRole = GlassRole.Card
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val fill = (0.052f * glassIntensity * role.fillScale).coerceIn(0.026f, 0.17f)
    val line = (0.25f * glassIntensity * role.rimScale).coerceIn(0.15f, 0.56f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.32f, 1.42f)

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.22f),
            spotColor = Color(0xFF6087FF).copy(alpha = 0.10f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + 0.018f),
                    Color.White.copy(alpha = fill * 0.86f),
                    Color(0xFFB3F6FF).copy(alpha = fill * 0.24f),
                    Color(0xFF050A17).copy(alpha = 0.13f)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 1300f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val shineX = (0.16f + 0.64f * shimmer) * w

            val crownHighlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f * glow),
                    Color.White.copy(alpha = 0.085f * glow),
                    Color.Transparent
                ),
                center = Offset(w * 0.18f, -h * 0.10f),
                radius = w * 0.44f
            )
            val diagonalRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFFBED6FF).copy(alpha = 0.090f * glow)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val verticalLens = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.090f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF020512).copy(alpha = 0.20f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val sideRefraction = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.070f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFFBED6FF).copy(alpha = 0.060f * glow)
                )
            )
            val movingHighlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.090f * glow else 0.042f),
                    Color(0xFFB3F6FF).copy(alpha = if (quality.enableMotion) 0.036f * glow else 0.016f),
                    Color.Transparent
                ),
                center = Offset(shineX, -h * 0.04f),
                radius = w * 0.46f
            )
            val innerBottom = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.016f * glassIntensity),
                    Color(0xFF040713).copy(alpha = 0.18f * glassIntensity)
                ),
                startY = h * 0.38f,
                endY = h
            )

            onDrawWithContent {
                drawRect(crownHighlight, blendMode = BlendMode.Screen)
                drawRect(diagonalRefraction, blendMode = BlendMode.Screen)
                drawRect(verticalLens, blendMode = BlendMode.Screen)
                drawRect(sideRefraction, blendMode = BlendMode.Screen)
                drawRect(movingHighlight, blendMode = BlendMode.Plus)
                drawRect(innerBottom, blendMode = BlendMode.Multiply)
                drawContent()
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line + 0.12f),
                    Color.White.copy(alpha = line * 0.48f),
                    Color(0xFF9AE1FF).copy(alpha = line * 0.34f),
                    Color(0xFFBCA8FF).copy(alpha = line * 0.34f),
                    Color.White.copy(alpha = line * 0.72f)
                ),
                start = Offset(0f, 0f),
                end = Offset(850f, 1200f)
            ),
            shape = shape
        )
        .border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.09f * glassIntensity),
            shape = shape
        )
}