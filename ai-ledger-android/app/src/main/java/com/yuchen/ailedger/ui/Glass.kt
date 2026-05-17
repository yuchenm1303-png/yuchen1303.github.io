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
    Shell(0.72f, 0.95f, 0.62f, 18),
    Card(0.92f, 1.00f, 0.72f, 12),
    Chip(1.08f, 1.12f, 0.82f, 8),
    Nav(0.88f, 1.04f, 0.78f, 16),
    Floating(1.18f, 1.22f, 0.95f, 20)
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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(
                quality = quality,
                radius = radius,
                shimmer = shimmer,
                glassIntensity = glassIntensity,
                role = role
            )
    ) {
        content()
    }
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
            animation = tween(
                durationMillis = (13200 / motionIntensity.coerceAtLeast(0.35f)).toInt(),
                easing = LinearEasing
            ),
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
    val fill = (0.055f * glassIntensity * role.fillScale).coerceIn(0.030f, 0.18f)
    val line = (0.24f * glassIntensity * role.rimScale).coerceIn(0.14f, 0.52f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.30f, 1.35f)

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.18f),
            spotColor = Color(0xFF446CFF).copy(alpha = 0.08f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + 0.020f),
                    Color.White.copy(alpha = fill * 0.88f),
                    Color(0xFFB3F6FF).copy(alpha = fill * 0.26f),
                    Color(0xFF0C1228).copy(alpha = 0.11f)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 1300f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val shineX = (0.18f + 0.62f * shimmer) * w

            val topGlow = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * glow),
                    Color.White.copy(alpha = 0.060f * glow),
                    Color.Transparent
                ),
                center = Offset(w * 0.18f, -h * 0.08f),
                radius = w * 0.42f
            )
            val diagonalPlate = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.13f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFFBED6FF).copy(alpha = 0.075f * glow)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val sideRefraction = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.055f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFFBED6FF).copy(alpha = 0.052f * glow)
                )
            )
            val movingHighlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.080f * glow else 0.040f),
                    Color(0xFFB3F6FF).copy(alpha = if (quality.enableMotion) 0.032f * glow else 0.016f),
                    Color.Transparent
                ),
                center = Offset(shineX, -h * 0.05f),
                radius = w * 0.44f
            )
            val innerBottom = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.018f * glassIntensity),
                    Color(0xFF060A18).copy(alpha = 0.14f * glassIntensity)
                ),
                startY = h * 0.40f,
                endY = h
            )

            onDrawWithContent {
                drawRect(topGlow, blendMode = BlendMode.Screen)
                drawRect(diagonalPlate, blendMode = BlendMode.Screen)
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
                    Color.White.copy(alpha = line + 0.10f),
                    Color.White.copy(alpha = line * 0.42f),
                    Color(0xFF9AE1FF).copy(alpha = line * 0.30f),
                    Color(0xFFBCA8FF).copy(alpha = line * 0.30f),
                    Color.White.copy(alpha = line * 0.68f)
                ),
                start = Offset(0f, 0f),
                end = Offset(850f, 1200f)
            ),
            shape = shape
        )
        .border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.07f * glassIntensity),
            shape = shape
        )
}
