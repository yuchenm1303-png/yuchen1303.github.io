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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0.82f, 1.22f, 1.02f, 26),
    Card(0.92f, 1.26f, 1.10f, 18),
    Chip(1.02f, 1.36f, 1.18f, 12),
    Nav(0.92f, 1.30f, 1.12f, 22),
    Floating(1.08f, 1.45f, 1.30f, 28)
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
        modifier = modifier.glassSkin(quality, radius, shimmer, glassIntensity, role)
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
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, tween(220, easing = FastOutSlowInEasing), label = "glass-press-scale")
    val shimmer = rememberGlassShimmer(quality, motionIntensity)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(quality, radius, shimmer, glassIntensity, role)
    ) { content() }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion || motionIntensity <= 0.02f) return 0.18f
    val transition = rememberInfiniteTransition(label = "glass-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((9800 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
    val fill = (0.050f * glassIntensity * role.fillScale).coerceIn(0.032f, 0.145f)
    val milk = (0.030f * glassIntensity * role.fillScale).coerceIn(0.018f, 0.075f)
    val line = (0.33f * glassIntensity * role.rimScale).coerceIn(0.22f, 0.76f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.45f, 1.85f)

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.31f),
            spotColor = Color(0xFF82A8FF).copy(alpha = 0.14f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + milk),
                    Color.White.copy(alpha = fill * 0.72f),
                    Color(0xFFB3F6FF).copy(alpha = fill * 0.24f),
                    Color(0xFF030817).copy(alpha = 0.16f)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 1300f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val shineX = (0.12f + 0.72f * shimmer) * w
            val drift = shimmer - 0.5f

            val cornerLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f * glow),
                    Color.White.copy(alpha = 0.072f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.06f + drift * 0.06f), -h * 0.02f),
                radius = w * 0.42f
            )
            val topRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * glow),
                    Color.White.copy(alpha = 0.045f * glow),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.30f
            )
            val edgeCompression = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.105f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF010514).copy(alpha = 0.12f * glassIntensity),
                    Color.White.copy(alpha = 0.072f * glow)
                )
            )
            val bottomDepth = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF020716).copy(alpha = 0.025f * glassIntensity),
                    Color(0xFF020716).copy(alpha = 0.23f * glassIntensity)
                ),
                startY = h * 0.44f,
                endY = h
            )
            val diagonalShard = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.070f * glow),
                    Color.Transparent,
                    Color(0xFF9FC8FF).copy(alpha = 0.080f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.10f, h * 0.02f),
                end = Offset(w * 1.05f, h * 0.82f)
            )
            val secondShard = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFF7FF).copy(alpha = 0.052f * glow),
                    Color.Transparent,
                    Color(0xFF070D23).copy(alpha = 0.072f * glassIntensity)
                ),
                start = Offset(w * 1.05f, h * 0.02f),
                end = Offset(-w * 0.05f, h * 0.92f)
            )
            val caustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.086f * glow else 0.038f),
                    Color(0xFFB3F6FF).copy(alpha = if (quality.enableMotion) 0.036f * glow else 0.015f),
                    Color.Transparent
                ),
                center = Offset(shineX, -h * 0.02f),
                radius = w * 0.46f
            )
            val localFog = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.034f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.72f - drift * 0.08f), h * 0.22f),
                radius = w * 0.42f
            )

            onDrawWithContent {
                drawRect(cornerLens, blendMode = BlendMode.Screen)
                drawRect(topRim, blendMode = BlendMode.Screen)
                drawRect(edgeCompression, blendMode = BlendMode.Screen)
                drawRect(diagonalShard, blendMode = BlendMode.Screen)
                drawRect(secondShard, blendMode = BlendMode.Screen)
                drawRect(caustic, blendMode = BlendMode.Plus)
                drawRect(localFog, blendMode = BlendMode.Screen)

                val bandCount = if (quality.enableMotion) 4 else 3
                repeat(bandCount) { index ->
                    val y = h * (0.24f + index * 0.18f + 0.008f * sin((shimmer * 6.28f) + index).toFloat())
                    val alpha = (0.008f + index * 0.002f) * glow
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                            start = Offset(0f, y),
                            end = Offset(w, y + h * 0.06f)
                        ),
                        topLeft = Offset(-w * 0.16f, y - h * 0.16f),
                        size = Size(w * 1.32f, h * 0.18f),
                        blendMode = BlendMode.Screen
                    )
                }

                repeat(18) { index ->
                    val x = w * (((index * 37) % 100) / 100f)
                    val y = h * (((index * 53 + 11) % 100) / 100f)
                    drawCircle(Color.White.copy(alpha = 0.010f * glow), if (index % 5 == 0) 1.0f else 0.55f, Offset(x, y), blendMode = BlendMode.Screen)
                }

                drawRect(bottomDepth, blendMode = BlendMode.Multiply)
                drawContent()
            }
        }
        .border(
            width = 1.25.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line + 0.12f),
                    Color.White.copy(alpha = line * 0.52f),
                    Color(0xFFBFF1FF).copy(alpha = line * 0.34f),
                    Color(0xFFBCA8FF).copy(alpha = line * 0.28f),
                    Color.White.copy(alpha = line * 0.76f)
                ),
                start = Offset(0f, 0f),
                end = Offset(850f, 1200f)
            ),
            shape = shape
        )
        .border(
            width = 0.55.dp,
            color = Color.White.copy(alpha = 0.12f * glassIntensity),
            shape = shape
        )
} 