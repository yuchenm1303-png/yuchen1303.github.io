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
    Shell(0.76f, 1.30f, 1.12f, 28),
    Card(0.82f, 1.32f, 1.14f, 20),
    Chip(0.95f, 1.44f, 1.22f, 13),
    Nav(0.84f, 1.42f, 1.20f, 24),
    Floating(1.00f, 1.54f, 1.38f, 30)
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
    val breathe = rememberGlassBreath(quality, motionIntensity)
    Box(
        modifier = modifier.glassSkin(
            quality = quality,
            radius = radius,
            shimmer = shimmer,
            breathe = breathe,
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
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = tween(190, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 2.4f else 0f,
        animationSpec = tween(190, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 2f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(
                quality = quality,
                radius = radius,
                shimmer = shimmer,
                breathe = breathe,
                glassIntensity = if (pressed) glassIntensity * 1.08f else glassIntensity,
                role = role
            )
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
            animation = tween((7600 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glass-shimmer-value"
    )
    return shimmer
}

@Composable
private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion || motionIntensity <= 0.02f) return 0.42f
    val transition = rememberInfiniteTransition(label = "glass-breath")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((4300 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass-breath-value"
    )
    return breathe
}

fun Modifier.glassSkin(
    quality: RenderQuality,
    radius: Int,
    shimmer: Float = 0f,
    breathe: Float = 0.42f,
    glassIntensity: Float = 1f,
    role: GlassRole = GlassRole.Card
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val fill = (0.038f * glassIntensity * role.fillScale).coerceIn(0.024f, 0.115f)
    val mist = (0.020f * glassIntensity * role.fillScale).coerceIn(0.010f, 0.052f)
    val line = (0.36f * glassIntensity * role.rimScale).coerceIn(0.24f, 0.82f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.48f, 1.95f)
    val pulse = 0.86f + breathe * 0.18f

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.34f),
            spotColor = Color(0xFF8FB2FF).copy(alpha = 0.18f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + mist * 0.72f),
                    Color(0xFFEAF7FF).copy(alpha = fill * 0.58f),
                    Color(0xFF2B355F).copy(alpha = 0.048f * glassIntensity),
                    Color(0xFF020716).copy(alpha = 0.18f * glassIntensity)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 1300f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val drift = shimmer - 0.5f
            val shineX = (0.06f + 0.88f * shimmer) * w
            val shineY = h * (0.02f + 0.12f * sin(shimmer * 6.28318f).toFloat())

            val deepCore = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF08112E).copy(alpha = 0.00f),
                    Color(0xFF061026).copy(alpha = 0.08f * glassIntensity),
                    Color(0xFF020613).copy(alpha = 0.24f * glassIntensity)
                ),
                center = Offset(w * 0.58f, h * 0.68f),
                radius = w * 0.82f
            )
            val edgeLift = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF000515).copy(alpha = 0.15f * glassIntensity),
                    Color.White.copy(alpha = 0.10f * glow)
                )
            )
            val crown = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.23f * glow * pulse),
                    Color.White.copy(alpha = 0.060f * glow),
                    Color.Transparent,
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.34f
            )
            val leftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f * glow * pulse),
                    Color(0xFFE8F8FF).copy(alpha = 0.056f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.06f + drift * 0.08f), -h * 0.03f),
                radius = w * 0.46f
            )
            val rightLens = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFEEDCFF).copy(alpha = 0.055f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.86f - drift * 0.10f), h * 0.78f),
                radius = w * 0.46f
            )
            val diagonalA = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.100f * glow * pulse),
                    Color.Transparent,
                    Color(0xFF9EDCFF).copy(alpha = 0.070f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.18f, h * (0.10f + drift * 0.10f)),
                end = Offset(w * 1.08f, h * (0.68f + drift * 0.12f))
            )
            val diagonalB = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFF7FF).copy(alpha = 0.050f * glow),
                    Color.Transparent,
                    Color(0xFF060B20).copy(alpha = 0.082f * glassIntensity)
                ),
                start = Offset(w * 1.08f, h * 0.06f),
                end = Offset(-w * 0.12f, h * 0.92f)
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.105f * glow else 0.042f),
                    Color(0xFFB8F7FF).copy(alpha = if (quality.enableMotion) 0.042f * glow else 0.016f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.36f
            )
            val bottomGlassThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.022f * glow),
                    Color(0xFF010512).copy(alpha = 0.26f * glassIntensity)
                ),
                startY = h * 0.34f,
                endY = h
            )

            onDrawWithContent {
                drawRect(deepCore, blendMode = BlendMode.Multiply)
                drawRect(edgeLift, blendMode = BlendMode.Screen)
                drawRect(crown, blendMode = BlendMode.Screen)
                drawRect(leftLens, blendMode = BlendMode.Screen)
                drawRect(rightLens, blendMode = BlendMode.Screen)
                drawRect(diagonalA, blendMode = BlendMode.Screen)
                drawRect(diagonalB, blendMode = BlendMode.Screen)
                drawRect(movingCaustic, blendMode = BlendMode.Plus)

                val waveCount = if (quality.enableMotion) 5 else 3
                repeat(waveCount) { index ->
                    val y = h * (0.18f + index * 0.17f + 0.014f * sin(shimmer * 6.28318f + index * 1.2f).toFloat())
                    val alpha = (0.012f + index * 0.0022f) * glow
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = alpha),
                                Color.Transparent
                            ),
                            start = Offset(0f, y),
                            end = Offset(w, y + h * 0.07f)
                        ),
                        topLeft = Offset(-w * 0.20f, y - h * 0.17f),
                        size = Size(w * 1.42f, h * 0.18f),
                        blendMode = BlendMode.Screen
                    )
                }

                repeat(20) { index ->
                    val x = w * (((index * 41 + 7) % 100) / 100f)
                    val y = h * (((index * 59 + 13) % 100) / 100f)
                    val r = if (index % 6 == 0) 0.95f else 0.48f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.008f * glow),
                        radius = r,
                        center = Offset(x, y),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(bottomGlassThickness, blendMode = BlendMode.Multiply)
                drawContent()
            }
        }
        .border(
            width = 1.25.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line + 0.12f),
                    Color.White.copy(alpha = line * 0.46f),
                    Color(0xFFBFF7FF).copy(alpha = line * 0.38f),
                    Color(0xFFB7A2FF).copy(alpha = line * 0.30f),
                    Color.White.copy(alpha = line * 0.82f)
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
