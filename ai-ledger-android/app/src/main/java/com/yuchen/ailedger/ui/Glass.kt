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
    Shell(0.72f, 1.06f, 0.92f, 24),
    Card(0.76f, 1.04f, 0.94f, 18),
    Chip(0.84f, 1.12f, 1.00f, 12),
    Nav(0.68f, 1.08f, 0.94f, 22),
    Floating(0.88f, 1.18f, 1.08f, 28)
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
        targetValue = if (pressed) 0.976f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1.4f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
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
                shimmer = shimmer + if (pressed) 0.05f else 0f,
                breathe = breathe,
                glassIntensity = if (pressed) glassIntensity * 1.05f else glassIntensity,
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
            animation = tween((9800 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((6400 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val fill = (0.030f * glassIntensity * role.fillScale).coerceIn(0.012f, 0.085f)
    val mist = (0.014f * glassIntensity * role.fillScale).coerceIn(0.004f, 0.040f)
    val line = (0.118f * glassIntensity * role.rimScale).coerceIn(0.048f, 0.28f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.32f, 1.25f)
    val pulse = 0.88f + breathe * 0.10f
    val safeShimmer = shimmer - shimmer.toInt()

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.26f),
            spotColor = Color.White.copy(alpha = 0.062f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + mist * 0.36f),
                    Color.White.copy(alpha = fill * 0.34f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.058f * glassIntensity)
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
            val shineX = (0.05f + 0.90f * safeShimmer) * w
            val shineY = h * (0.02f + 0.08f * sin(safeShimmer * 6.28318f).toFloat())
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())

            val backgroundCompression = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.022f * glassIntensity),
                    Color.Black.copy(alpha = 0.072f * glassIntensity)
                ),
                center = Offset(w * 0.54f, h * 0.68f),
                radius = w * 0.96f
            )
            val frostedNeutralLift = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.055f * glow * pulse),
                    Color.White.copy(alpha = 0.016f * glow),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.030f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val sideLens = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.032f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.026f * glassIntensity),
                    Color.White.copy(alpha = 0.020f * glow)
                )
            )
            val upperLeftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.052f * glow * pulse),
                    Color.White.copy(alpha = 0.012f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.08f + drift * 0.04f), -h * 0.04f),
                radius = w * 0.52f
            )
            val lowerRightLens = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.038f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * (0.82f - drift * 0.05f), h * 0.92f),
                radius = w * 0.60f
            )
            val mainRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.020f * glow * pulse),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.012f * glassIntensity),
                    Color.Transparent
                ),
                start = Offset(-w * 0.16f, h * (0.05f + drift * 0.06f)),
                end = Offset(w * 1.10f, h * (0.58f + drift * 0.08f))
            )
            val counterRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.014f * glassIntensity),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.013f * glow),
                    Color.Transparent
                ),
                start = Offset(w * 1.10f, h * 0.05f),
                end = Offset(-w * 0.12f, h * 0.96f)
            )
            val movingNeutralCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.022f * glow else 0.010f),
                    Color.White.copy(alpha = if (quality.enableMotion) 0.007f * glow else 0.003f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.42f
            )
            val bottomThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.004f * glow),
                    Color.Black.copy(alpha = 0.088f * glassIntensity)
                ),
                startY = h * 0.40f,
                endY = h
            )

            val softStroke = 4.8.dp.toPx()
            val outerStroke = 0.92.dp.toPx()
            val innerStroke = 0.48.dp.toPx()
            val hairStroke = 0.26.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = 2.45.dp.toPx()
            val hairInset = 4.10.dp.toPx()
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val softOuterMist = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.54f),
                    Color.White.copy(alpha = line * 0.16f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = line * 0.045f)
                ),
                startY = 0f,
                endY = h
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.38f),
                    Color.White.copy(alpha = line * 0.12f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.034f * glassIntensity),
                    Color.White.copy(alpha = line * 0.080f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val topRimBloom = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.62f),
                    Color.White.copy(alpha = line * 0.15f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.028f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.060f),
                    Color.White.copy(alpha = line * 0.026f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.026f * glassIntensity),
                    Color.White.copy(alpha = line * 0.020f)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.92f, h)
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.052f * glow),
                    Color.White.copy(alpha = 0.010f * glow),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.36f), 0f),
                end = Offset(w * (safeShimmer + 0.22f), h * 0.34f)
            )

            onDrawWithContent {
                drawRect(backgroundCompression, blendMode = BlendMode.Multiply)
                drawRect(frostedNeutralLift, blendMode = BlendMode.Screen)
                drawRect(sideLens, blendMode = BlendMode.Screen)
                drawRect(upperLeftLens, blendMode = BlendMode.Screen)
                drawRect(lowerRightLens, blendMode = BlendMode.Multiply)
                drawRect(mainRefractionBand, blendMode = BlendMode.Screen)
                drawRect(counterRefractionBand, blendMode = BlendMode.Multiply)
                drawRect(movingNeutralCaustic, blendMode = BlendMode.Plus)

                // Neutral pseudo-backdrop refraction: soft displaced bands that simulate a blurred
                // background being compressed through a rounded glass lens. No blue/purple tint here.
                val stripeCount = if (quality.enableMotion) 9 else 6
                repeat(stripeCount) { index ->
                    val phase = safeShimmer * 6.28318f + index * 0.63f
                    val y = h * (0.08f + index * 0.105f + 0.010f * sin(phase).toFloat())
                    val height = h * (0.042f + (index % 3) * 0.006f)
                    val alpha = (0.0042f + (index % 4) * 0.0009f) * glow
                    val xOffset = w * 0.040f * sin(phase * 0.7f).toFloat()
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = alpha),
                                Color.Transparent,
                                Color.Black.copy(alpha = alpha * 0.66f),
                                Color.Transparent
                            ),
                            start = Offset(xOffset, y),
                            end = Offset(w + xOffset, y + height)
                        ),
                        topLeft = Offset(-w * 0.24f + xOffset, y - height * 1.9f),
                        size = Size(w * 1.52f, height * 4.0f),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(bottomThickness, blendMode = BlendMode.Multiply)
                drawContent()

                drawRoundRect(
                    brush = softOuterMist,
                    topLeft = Offset(softStroke / 2f, softStroke / 2f),
                    size = Size(w - softStroke, h - softStroke),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = softStroke),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = outerRim,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = outerStroke),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = topRimBloom,
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
                    style = Stroke(width = 0.56.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
        }
}
