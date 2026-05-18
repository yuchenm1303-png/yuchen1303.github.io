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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    Shell(0.58f, 1.46f, 1.02f, 30),
    Card(0.62f, 1.48f, 1.04f, 22),
    Chip(0.72f, 1.58f, 1.10f, 14),
    Nav(0.60f, 1.60f, 1.10f, 26),
    Floating(0.78f, 1.72f, 1.28f, 32)
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
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 2.8f else 0f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
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
                shadowElevation = if (pressed) 1f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(
                quality = quality,
                radius = radius,
                shimmer = shimmer + if (pressed) 0.08f else 0f,
                breathe = breathe,
                glassIntensity = if (pressed) glassIntensity * 1.12f else glassIntensity,
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
            animation = tween((8200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((4800 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val fill = (0.024f * glassIntensity * role.fillScale).coerceIn(0.012f, 0.070f)
    val mist = (0.010f * glassIntensity * role.fillScale).coerceIn(0.004f, 0.028f)
    val line = (0.30f * glassIntensity * role.rimScale).coerceIn(0.18f, 0.66f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.44f, 1.80f)
    val pulse = 0.82f + breathe * 0.16f
    val safeShimmer = shimmer - shimmer.toInt()

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.42f),
            spotColor = Color(0xFF6FA8FF).copy(alpha = 0.16f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + mist * 0.44f),
                    Color(0xFFEAF7FF).copy(alpha = fill * 0.42f),
                    Color(0xFF18294E).copy(alpha = 0.070f * glassIntensity),
                    Color(0xFF030715).copy(alpha = 0.215f * glassIntensity)
                ),
                start = Offset(0f, 0f),
                end = Offset(900f, 1300f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val drift = safeShimmer - 0.5f
            val shineX = (0.04f + 0.92f * safeShimmer) * w
            val shineY = h * (0.03f + 0.10f * sin(safeShimmer * 6.28318f).toFloat())

            val deepCore = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF08112E).copy(alpha = 0.00f),
                    Color(0xFF061026).copy(alpha = 0.11f * glassIntensity),
                    Color(0xFF020612).copy(alpha = 0.32f * glassIntensity)
                ),
                center = Offset(w * 0.58f, h * 0.66f),
                radius = w * 0.86f
            )
            val topInnerShadow = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.150f * glow * pulse),
                    Color.White.copy(alpha = 0.030f * glow),
                    Color.Transparent,
                    Color(0xFF010512).copy(alpha = 0.17f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val edgeLift = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF000515).copy(alpha = 0.19f * glassIntensity),
                    Color.White.copy(alpha = 0.13f * glow)
                )
            )
            val leftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * glow * pulse),
                    Color(0xFFE8F8FF).copy(alpha = 0.045f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.05f + drift * 0.06f), -h * 0.02f),
                radius = w * 0.42f
            )
            val rightDepth = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF01030B).copy(alpha = 0.17f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * (0.86f - drift * 0.06f), h * 0.86f),
                radius = w * 0.55f
            )
            val diagonalA = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.074f * glow * pulse),
                    Color.Transparent,
                    Color(0xFF9EDCFF).copy(alpha = 0.050f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.18f, h * (0.08f + drift * 0.08f)),
                end = Offset(w * 1.08f, h * (0.62f + drift * 0.10f))
            )
            val diagonalB = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFF7FF).copy(alpha = 0.034f * glow),
                    Color.Transparent,
                    Color(0xFF020616).copy(alpha = 0.11f * glassIntensity)
                ),
                start = Offset(w * 1.08f, h * 0.06f),
                end = Offset(-w * 0.12f, h * 0.92f)
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.072f * glow else 0.030f),
                    Color(0xFFB8F7FF).copy(alpha = if (quality.enableMotion) 0.030f * glow else 0.012f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.34f
            )
            val bottomGlassThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.016f * glow),
                    Color(0xFF00030C).copy(alpha = 0.34f * glassIntensity)
                ),
                startY = h * 0.32f,
                endY = h
            )

            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val outerStroke = 1.10.dp.toPx()
            val innerStroke = 0.72.dp.toPx()
            val hairStroke = 0.46.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = 1.85.dp.toPx()
            val hairInset = 3.15.dp.toPx()
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.92f),
                    Color(0xFFDBF7FF).copy(alpha = line * 0.42f),
                    Color.Transparent,
                    Color(0xFF050A18).copy(alpha = 0.30f * glassIntensity),
                    Color.White.copy(alpha = line * 0.36f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val topCutRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.88f),
                    Color.White.copy(alpha = line * 0.28f),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF00030C).copy(alpha = 0.34f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = line * 0.20f),
                    Color(0xFF7EE9FF).copy(alpha = line * 0.12f),
                    Color.Transparent,
                    Color(0xFF000614).copy(alpha = 0.20f * glassIntensity),
                    Color.White.copy(alpha = line * 0.12f)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.92f, h)
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.28f * glow),
                    Color.White.copy(alpha = 0.07f * glow),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.34f), 0f),
                end = Offset(w * (safeShimmer + 0.18f), h * 0.38f)
            )

            onDrawWithContent {
                drawRect(deepCore, blendMode = BlendMode.Multiply)
                drawRect(topInnerShadow, blendMode = BlendMode.Screen)
                drawRect(edgeLift, blendMode = BlendMode.Screen)
                drawRect(leftLens, blendMode = BlendMode.Screen)
                drawRect(rightDepth, blendMode = BlendMode.Multiply)
                drawRect(diagonalA, blendMode = BlendMode.Screen)
                drawRect(diagonalB, blendMode = BlendMode.Screen)
                drawRect(movingCaustic, blendMode = BlendMode.Plus)

                val waveCount = if (quality.enableMotion) 4 else 2
                repeat(waveCount) { index ->
                    val y = h * (0.18f + index * 0.18f + 0.012f * sin(safeShimmer * 6.28318f + index * 1.2f).toFloat())
                    val alpha = (0.008f + index * 0.0016f) * glow
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

                repeat(14) { index ->
                    val x = w * (((index * 41 + 7) % 100) / 100f)
                    val y = h * (((index * 59 + 13) % 100) / 100f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.005f * glow),
                        radius = if (index % 6 == 0) 0.75f else 0.42f,
                        center = Offset(x, y),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(bottomGlassThickness, blendMode = BlendMode.Multiply)
                drawContent()

                drawRoundRect(
                    brush = outerRim,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = outerStroke),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = topCutRim,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = innerStroke),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = innerRefraction,
                    topLeft = Offset(hairInset, hairInset),
                    size = hairSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = hairStroke),
                    blendMode = BlendMode.SrcOver
                )
                drawRoundRect(
                    brush = grazingGlint,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.92.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.12f * glow), Color.Transparent),
                        center = Offset(w * 0.08f, h * 0.08f),
                        radius = w * 0.22f
                    ),
                    radius = w * 0.22f,
                    center = Offset(w * 0.08f, h * 0.08f),
                    blendMode = BlendMode.Screen
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF00030C).copy(alpha = 0.18f * glassIntensity), Color.Transparent),
                        center = Offset(w * 0.92f, h * 0.90f),
                        radius = w * 0.24f
                    ),
                    radius = w * 0.24f,
                    center = Offset(w * 0.92f, h * 0.90f),
                    blendMode = BlendMode.Multiply
                )
            }
        }
}
