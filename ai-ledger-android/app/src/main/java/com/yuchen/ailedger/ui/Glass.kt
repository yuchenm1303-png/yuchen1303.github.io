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
    Shell(1.08f, 1.10f, 0.95f, 26),
    Card(1.20f, 1.12f, 1.02f, 18),
    Chip(1.28f, 1.24f, 1.10f, 12),
    Nav(1.10f, 1.18f, 1.08f, 22),
    Floating(1.36f, 1.34f, 1.24f, 28)
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
    val fill = (0.090f * glassIntensity * role.fillScale).coerceIn(0.070f, 0.30f)
    val milk = (0.115f * glassIntensity * role.fillScale).coerceIn(0.075f, 0.32f)
    val line = (0.28f * glassIntensity * role.rimScale).coerceIn(0.18f, 0.64f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.42f, 1.70f)

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.28f),
            spotColor = Color(0xFF82A8FF).copy(alpha = 0.16f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = milk + 0.045f),
                    Color.White.copy(alpha = fill),
                    Color(0xFFE8F6FF).copy(alpha = milk * 0.42f),
                    Color(0xFF11182E).copy(alpha = 0.18f)
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
            val drift = (shimmer - 0.5f)

            val frostedMilk = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f * glow),
                    Color.White.copy(alpha = 0.080f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.22f + drift * 0.08f), h * 0.10f),
                radius = w * 0.82f
            )
            val magnifiedPatchA = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.135f * glow),
                    Color(0xFFBFD9FF).copy(alpha = 0.045f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.08f + drift * 0.10f), h * 0.12f),
                radius = w * 0.36f
            )
            val magnifiedPatchB = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE5D8FF).copy(alpha = 0.080f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.90f - drift * 0.08f), h * 0.76f),
                radius = w * 0.50f
            )
            val thickCrown = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f * glow),
                    Color.White.copy(alpha = 0.070f * glow),
                    Color.Transparent,
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.48f
            )
            val lowerCompression = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF020716).copy(alpha = 0.04f * glassIntensity),
                    Color(0xFF020716).copy(alpha = 0.24f * glassIntensity)
                ),
                startY = h * 0.40f,
                endY = h
            )
            val sideCompression = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF030712).copy(alpha = 0.11f * glassIntensity),
                    Color.White.copy(alpha = 0.070f * glow)
                )
            )
            val diagonalRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF9FC8FF).copy(alpha = 0.115f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.08f, h * 0.05f),
                end = Offset(w * 1.05f, h * 0.82f)
            )
            val oppositeRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFF7FF).copy(alpha = 0.090f * glow),
                    Color.Transparent,
                    Color(0xFF0B1028).copy(alpha = 0.070f * glassIntensity)
                ),
                start = Offset(w * 0.95f, h * 0.02f),
                end = Offset(-w * 0.05f, h * 0.86f)
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.115f * glow else 0.052f),
                    Color(0xFFB3F6FF).copy(alpha = if (quality.enableMotion) 0.052f * glow else 0.022f),
                    Color.Transparent
                ),
                center = Offset(shineX, -h * 0.03f),
                radius = w * 0.52f
            )

            onDrawWithContent {
                drawRect(frostedMilk, blendMode = BlendMode.Screen)
                drawRect(magnifiedPatchA, blendMode = BlendMode.Screen)
                drawRect(magnifiedPatchB, blendMode = BlendMode.Screen)
                drawRect(thickCrown, blendMode = BlendMode.Screen)
                drawRect(sideCompression, blendMode = BlendMode.Screen)
                drawRect(diagonalRefraction, blendMode = BlendMode.Screen)
                drawRect(oppositeRefraction, blendMode = BlendMode.Screen)
                drawRect(movingCaustic, blendMode = BlendMode.Plus)

                val bandCount = if (quality.enableMotion) 6 else 4
                repeat(bandCount) { index ->
                    val y = h * (0.18f + index * 0.13f + 0.010f * sin((shimmer * 6.28f) + index).toFloat())
                    val alpha = (0.020f + index * 0.004f) * glow
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                            start = Offset(0f, y),
                            end = Offset(w, y + h * 0.08f)
                        ),
                        topLeft = Offset(-w * 0.18f, y - h * 0.18f),
                        size = Size(w * 1.36f, h * 0.22f),
                        blendMode = BlendMode.Screen
                    )
                }

                repeat(24) { index ->
                    val x = w * (((index * 37) % 100) / 100f)
                    val y = h * (((index * 53 + 11) % 100) / 100f)
                    val r = if (index % 5 == 0) 1.15f else 0.65f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.018f * glow),
                        radius = r,
                        center = Offset(x, y),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(lowerCompression, blendMode = BlendMode.Multiply)
                drawContent()
            }
        }
        .border(
            width = 1.35.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line + 0.16f),
                    Color.White.copy(alpha = line * 0.58f),
                    Color(0xFFBFF1FF).copy(alpha = line * 0.42f),
                    Color(0xFFBCA8FF).copy(alpha = line * 0.34f),
                    Color.White.copy(alpha = line * 0.82f)
                ),
                start = Offset(0f, 0f),
                end = Offset(850f, 1200f)
            ),
            shape = shape
        )
        .border(
            width = 0.65.dp,
            color = Color.White.copy(alpha = 0.14f * glassIntensity),
            shape = shape
        )
}