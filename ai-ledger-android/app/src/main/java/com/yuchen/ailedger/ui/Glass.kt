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
    Shell(0.62f, 1.20f, 1.08f, 30),
    Card(0.66f, 1.16f, 1.06f, 24),
    Chip(0.72f, 1.24f, 1.10f, 18),
    Nav(0.58f, 1.26f, 1.12f, 30),
    Floating(0.76f, 1.34f, 1.22f, 36)
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
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1.8f else 0f,
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
                shimmer = shimmer + if (pressed) 0.055f else 0f,
                breathe = breathe,
                glassIntensity = if (pressed) glassIntensity * 1.10f else glassIntensity,
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
            animation = tween((10400 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((6800 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val fill = (0.022f * glassIntensity * role.fillScale).coerceIn(0.010f, 0.070f)
    val mist = (0.030f * glassIntensity * role.fillScale).coerceIn(0.010f, 0.065f)
    val line = (0.180f * glassIntensity * role.rimScale).coerceIn(0.075f, 0.42f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.42f, 1.55f)
    val depth = (glassIntensity * role.rimScale).coerceIn(0.55f, 1.55f)
    val pulse = 0.88f + breathe * 0.13f
    val safeShimmer = shimmer - shimmer.toInt()

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.38f),
            spotColor = Color.White.copy(alpha = 0.12f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + mist * 0.54f),
                    Color.White.copy(alpha = fill * 0.62f),
                    Color.White.copy(alpha = fill * 0.22f),
                    Color.Black.copy(alpha = 0.090f * glassIntensity)
                ),
                start = Offset(0f, 0f),
                end = Offset(780f, 1320f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val drift = safeShimmer - 0.5f
            val shineX = (0.04f + 0.92f * safeShimmer) * w
            val shineY = h * (0.015f + 0.075f * sin(safeShimmer * 6.28318f).toFloat())
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())

            val blurredBackdropVeil = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.100f * glow),
                    Color.White.copy(alpha = 0.040f * glow),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.078f * glassIntensity)
                ),
                center = Offset(w * (0.28f + drift * 0.05f), h * 0.06f),
                radius = w * 1.08f
            )
            val frostedSheet = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.115f * glow * pulse),
                    Color.White.copy(alpha = 0.038f * glow),
                    Color.White.copy(alpha = 0.012f * glow),
                    Color.Black.copy(alpha = 0.070f * glassIntensity),
                    Color.Black.copy(alpha = 0.112f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val lensCompression = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.018f * glassIntensity),
                    Color.Black.copy(alpha = 0.116f * glassIntensity)
                ),
                center = Offset(w * 0.56f, h * 0.72f),
                radius = w * 1.00f
            )
            val leftEdgeLift = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.078f * glow),
                    Color.White.copy(alpha = 0.020f * glow),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.038f * glassIntensity),
                    Color.White.copy(alpha = 0.040f * glow)
                )
            )
            val upperLeftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.090f * glow * pulse),
                    Color.White.copy(alpha = 0.028f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.06f + drift * 0.04f), -h * 0.05f),
                radius = w * 0.58f
            )
            val lowerRightWeight = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.074f * glassIntensity),
                    Color.Black.copy(alpha = 0.020f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * (0.82f - drift * 0.05f), h * 0.98f),
                radius = w * 0.68f
            )
            val mainRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.040f * glow * pulse),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.022f * glassIntensity),
                    Color.Transparent
                ),
                start = Offset(-w * 0.18f, h * (0.02f + drift * 0.05f)),
                end = Offset(w * 1.12f, h * (0.60f + drift * 0.08f))
            )
            val counterRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.026f * glassIntensity),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.030f * glow),
                    Color.Transparent
                ),
                start = Offset(w * 1.08f, h * 0.03f),
                end = Offset(-w * 0.14f, h * 0.96f)
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.052f * glow else 0.022f * glow),
                    Color.White.copy(alpha = if (quality.enableMotion) 0.014f * glow else 0.006f * glow),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.48f
            )
            val bottomThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.010f * glow),
                    Color.Black.copy(alpha = 0.145f * glassIntensity),
                    Color.Black.copy(alpha = 0.188f * glassIntensity)
                ),
                startY = h * 0.36f,
                endY = h
            )

            val softStroke = 8.2.dp.toPx()
            val haloStroke = 5.4.dp.toPx()
            val outerStroke = 1.45.dp.toPx()
            val darkStroke = 1.15.dp.toPx()
            val innerStroke = 0.84.dp.toPx()
            val hairStroke = 0.38.dp.toPx()
            val haloInset = haloStroke / 2f
            val outerInset = outerStroke / 2f
            val darkInset = 1.55.dp.toPx()
            val innerInset = 3.00.dp.toPx()
            val hairInset = 5.10.dp.toPx()
            val haloSize = Size(w - haloStroke, h - haloStroke)
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val darkSize = Size(w - darkInset * 2f, h - darkInset * 2f)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val outerGlow = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.80f),
                    Color.White.copy(alpha = line * 0.26f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = line * 0.090f)
                ),
                startY = 0f,
                endY = h
            )
            val thickHalo = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.48f),
                    Color.White.copy(alpha = line * 0.18f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.048f * depth),
                    Color.White.copy(alpha = line * 0.12f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.82f),
                    Color.White.copy(alpha = line * 0.22f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.070f * depth),
                    Color.White.copy(alpha = line * 0.22f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val innerDarkRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.12f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.120f * depth),
                    Color.Black.copy(alpha = 0.052f * depth),
                    Color.White.copy(alpha = line * 0.08f)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.96f, h)
            )
            val topRimBloom = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 1.08f),
                    Color.White.copy(alpha = line * 0.30f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.052f * depth)
                ),
                startY = 0f,
                endY = h
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.20f),
                    Color.White.copy(alpha = line * 0.065f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.054f * depth),
                    Color.White.copy(alpha = line * 0.065f)
                ),
                start = Offset(w * 0.06f, 0f),
                end = Offset(w * 0.92f, h)
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.105f * glow),
                    Color.White.copy(alpha = 0.024f * glow),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.36f), 0f),
                end = Offset(w * (safeShimmer + 0.24f), h * 0.36f)
            )
            val bottomInnerShadow = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.040f * depth),
                    Color.Black.copy(alpha = 0.130f * depth)
                ),
                startY = h * 0.42f,
                endY = h
            )

            onDrawWithContent {
                drawRect(blurredBackdropVeil, blendMode = BlendMode.Screen)
                drawRect(frostedSheet, blendMode = BlendMode.Screen)
                drawRect(lensCompression, blendMode = BlendMode.Multiply)
                drawRect(leftEdgeLift, blendMode = BlendMode.Screen)
                drawRect(upperLeftLens, blendMode = BlendMode.Screen)
                drawRect(lowerRightWeight, blendMode = BlendMode.Multiply)
                drawRect(mainRefractionBand, blendMode = BlendMode.Screen)
                drawRect(counterRefractionBand, blendMode = BlendMode.Multiply)
                drawRect(movingCaustic, blendMode = BlendMode.Plus)

                // Heavy frosted backdrop illusion: stacked soft strips imitate a blurred scene
                // being squeezed through a rounded liquid-glass lens on Android Compose.
                val stripeCount = if (quality.enableMotion) 14 else 9
                repeat(stripeCount) { index ->
                    val phase = safeShimmer * 6.28318f + index * 0.52f
                    val y = h * (0.045f + index * 0.073f + 0.012f * sin(phase).toFloat())
                    val height = h * (0.060f + (index % 4) * 0.010f)
                    val alpha = (0.0085f + (index % 5) * 0.0016f) * glow
                    val xOffset = w * 0.060f * sin(phase * 0.72f).toFloat()
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = alpha),
                                Color.Transparent,
                                Color.Black.copy(alpha = alpha * 0.74f),
                                Color.Transparent
                            ),
                            start = Offset(xOffset, y),
                            end = Offset(w + xOffset, y + height)
                        ),
                        topLeft = Offset(-w * 0.30f + xOffset, y - height * 2.15f),
                        size = Size(w * 1.62f, height * 4.8f),
                        blendMode = BlendMode.Screen
                    )
                }

                repeat(18) { index ->
                    val phase = index * 1.37f + safeShimmer * 6.28318f
                    val x = w * (0.08f + (index % 6) * 0.165f + 0.010f * sin(phase).toFloat())
                    val y = h * (0.10f + (index / 6) * 0.28f + 0.012f * sin(phase * 0.8f).toFloat())
                    val r = w * (0.050f + (index % 3) * 0.018f)
                    val alpha = (0.0045f + (index % 4) * 0.0012f) * glow
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = alpha),
                                Color.White.copy(alpha = alpha * 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(x, y),
                            radius = r
                        ),
                        topLeft = Offset(x - r, y - r * 0.60f),
                        size = Size(r * 2.0f, r * 1.2f),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(bottomThickness, blendMode = BlendMode.Multiply)
                drawContent()

                drawRoundRect(
                    brush = outerGlow,
                    topLeft = Offset(softStroke / 2f, softStroke / 2f),
                    size = Size(w - softStroke, h - softStroke),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = softStroke),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = thickHalo,
                    topLeft = Offset(haloInset, haloInset),
                    size = haloSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = haloStroke),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = outerRim,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = outerStroke),
                    blendMode = BlendMode.Plus
                )
                drawRoundRect(
                    brush = innerDarkRim,
                    topLeft = Offset(darkInset, darkInset),
                    size = darkSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = darkStroke),
                    blendMode = BlendMode.Multiply
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
                    brush = bottomInnerShadow,
                    topLeft = Offset(darkInset, darkInset),
                    size = darkSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = darkStroke),
                    blendMode = BlendMode.Multiply
                )
                drawRoundRect(
                    brush = grazingGlint,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.72.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
        }
}
