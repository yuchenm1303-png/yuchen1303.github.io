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
    Shell(0.78f, 1.00f, 0.88f, 22),
    Card(0.82f, 1.00f, 0.88f, 16),
    Chip(0.90f, 1.06f, 0.94f, 10),
    Nav(0.74f, 1.02f, 0.88f, 20),
    Floating(0.94f, 1.12f, 1.02f, 24)
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
            animation = tween((9600 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((6200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val fill = (0.040f * glassIntensity * role.fillScale).coerceIn(0.018f, 0.105f)
    val mist = (0.020f * glassIntensity * role.fillScale).coerceIn(0.008f, 0.052f)
    val line = (0.130f * glassIntensity * role.rimScale).coerceIn(0.055f, 0.30f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.34f, 1.25f)
    val pulse = 0.88f + breathe * 0.10f
    val safeShimmer = shimmer - shimmer.toInt()

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.28f),
            spotColor = Color.White.copy(alpha = 0.070f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + mist * 0.48f),
                    Color.White.copy(alpha = fill * 0.50f),
                    Color.White.copy(alpha = fill * 0.18f),
                    Color.Black.copy(alpha = 0.090f * glassIntensity)
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

            val transparentCore = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.030f * glassIntensity),
                    Color.Black.copy(alpha = 0.105f * glassIntensity)
                ),
                center = Offset(w * 0.56f, h * 0.72f),
                radius = w * 0.94f
            )
            val frostedLift = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.074f * glow * pulse),
                    Color.White.copy(alpha = 0.024f * glow),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.044f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val sideCompression = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.048f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.040f * glassIntensity),
                    Color.White.copy(alpha = 0.030f * glow)
                )
            )
            val cornerBloom = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.062f * glow * pulse),
                    Color.White.copy(alpha = 0.018f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.08f + drift * 0.04f), -h * 0.04f),
                radius = w * 0.50f
            )
            val lowerCompression = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.046f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * (0.82f - drift * 0.05f), h * 0.92f),
                radius = w * 0.58f
            )
            val neutralRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.025f * glow * pulse),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.014f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.14f, h * (0.08f + drift * 0.06f)),
                end = Offset(w * 1.06f, h * (0.56f + drift * 0.08f))
            )
            val movingNeutralHighlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.030f * glow else 0.014f),
                    Color.White.copy(alpha = if (quality.enableMotion) 0.010f * glow else 0.004f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.40f
            )
            val bottomThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.006f * glow),
                    Color.Black.copy(alpha = 0.105f * glassIntensity)
                ),
                startY = h * 0.38f,
                endY = h
            )

            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val softStroke = 4.0.dp.toPx()
            val outerStroke = 1.0.dp.toPx()
            val innerStroke = 0.54.dp.toPx()
            val hairStroke = 0.30.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = 2.15.dp.toPx()
            val hairInset = 3.65.dp.toPx()
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val softOuterMist = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.66f),
                    Color.White.copy(alpha = line * 0.20f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = line * 0.070f)
                ),
                startY = 0f,
                endY = h
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.46f),
                    Color.White.copy(alpha = line * 0.18f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.050f * glassIntensity),
                    Color.White.copy(alpha = line * 0.120f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val topRimBloom = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.72f),
                    Color.White.copy(alpha = line * 0.18f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.036f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.090f),
                    Color.White.copy(alpha = line * 0.040f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.038f * glassIntensity),
                    Color.White.copy(alpha = line * 0.032f)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.92f, h)
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.070f * glow),
                    Color.White.copy(alpha = 0.016f * glow),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.36f), 0f),
                end = Offset(w * (safeShimmer + 0.22f), h * 0.34f)
            )

            onDrawWithContent {
                drawRect(transparentCore, blendMode = BlendMode.Multiply)
                drawRect(frostedLift, blendMode = BlendMode.Screen)
                drawRect(sideCompression, blendMode = BlendMode.Screen)
                drawRect(cornerBloom, blendMode = BlendMode.Screen)
                drawRect(lowerCompression, blendMode = BlendMode.Multiply)
                drawRect(neutralRefractionBand, blendMode = BlendMode.Screen)
                drawRect(movingNeutralHighlight, blendMode = BlendMode.Plus)

                val waveCount = if (quality.enableMotion) 3 else 1
                repeat(waveCount) { index ->
                    val y = h * (0.20f + index * 0.21f + 0.010f * sin(safeShimmer * 6.28318f + index * 1.2f).toFloat())
                    val alpha = (0.0038f + index * 0.0008f) * glow
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                            start = Offset(0f, y),
                            end = Offset(w, y + h * 0.06f)
                        ),
                        topLeft = Offset(-w * 0.18f, y - h * 0.16f),
                        size = Size(w * 1.36f, h * 0.16f),
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
                    style = Stroke(width = 0.62.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
        }
}
