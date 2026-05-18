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
    Shell(0.90f, 1.00f, 0.86f, 24),
    Card(0.94f, 1.00f, 0.88f, 18),
    Chip(1.00f, 1.08f, 0.94f, 12),
    Nav(0.86f, 1.04f, 0.90f, 22),
    Floating(1.04f, 1.14f, 1.04f, 26)
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
                shimmer = shimmer + if (pressed) 0.06f else 0f,
                breathe = breathe,
                glassIntensity = if (pressed) glassIntensity * 1.06f else glassIntensity,
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
            animation = tween((9200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((5600 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val fill = (0.052f * glassIntensity * role.fillScale).coerceIn(0.030f, 0.135f)
    val mist = (0.030f * glassIntensity * role.fillScale).coerceIn(0.014f, 0.075f)
    val line = (0.145f * glassIntensity * role.rimScale).coerceIn(0.070f, 0.34f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.36f, 1.35f)
    val pulse = 0.88f + breathe * 0.10f
    val safeShimmer = shimmer - shimmer.toInt()

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color(0xFFB8D7FF).copy(alpha = 0.11f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + mist * 0.60f),
                    Color(0xFFDCEAFF).copy(alpha = fill * 0.72f),
                    Color(0xFF6D7895).copy(alpha = 0.032f * glassIntensity),
                    Color(0xFF071226).copy(alpha = 0.125f * glassIntensity)
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

            val deepCore = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF071126).copy(alpha = 0.00f),
                    Color(0xFF081530).copy(alpha = 0.038f * glassIntensity),
                    Color(0xFF020714).copy(alpha = 0.145f * glassIntensity)
                ),
                center = Offset(w * 0.58f, h * 0.70f),
                radius = w * 0.92f
            )
            val frostedBody = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.090f * glow * pulse),
                    Color.White.copy(alpha = 0.028f * glow),
                    Color.Transparent,
                    Color(0xFF071126).copy(alpha = 0.055f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val edgeAir = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.070f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF000515).copy(alpha = 0.055f * glassIntensity),
                    Color.White.copy(alpha = 0.050f * glow)
                )
            )
            val leftBloom = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.075f * glow * pulse),
                    Color(0xFFE9F6FF).copy(alpha = 0.020f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.08f + drift * 0.05f), -h * 0.04f),
                radius = w * 0.48f
            )
            val lowerDepth = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF01040D).copy(alpha = 0.060f * glassIntensity),
                    Color.Transparent
                ),
                center = Offset(w * (0.82f - drift * 0.05f), h * 0.92f),
                radius = w * 0.58f
            )
            val softDiagonal = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.030f * glow * pulse),
                    Color.Transparent,
                    Color(0xFFBDEAFF).copy(alpha = 0.020f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.14f, h * (0.08f + drift * 0.06f)),
                end = Offset(w * 1.06f, h * (0.56f + drift * 0.08f))
            )
            val caustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (quality.enableMotion) 0.036f * glow else 0.018f),
                    Color(0xFFD7F7FF).copy(alpha = if (quality.enableMotion) 0.014f * glow else 0.006f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.38f
            )
            val bottomFade = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.008f * glow),
                    Color(0xFF00040E).copy(alpha = 0.145f * glassIntensity)
                ),
                startY = h * 0.36f,
                endY = h
            )

            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val softStroke = 3.4.dp.toPx()
            val outerStroke = 1.15.dp.toPx()
            val innerStroke = 0.62.dp.toPx()
            val hairStroke = 0.34.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = 2.0.dp.toPx()
            val hairInset = 3.45.dp.toPx()
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val softOuterMist = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.70f),
                    Color.White.copy(alpha = line * 0.22f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = line * 0.12f)
                ),
                startY = 0f,
                endY = h
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.52f),
                    Color(0xFFE8F7FF).copy(alpha = line * 0.22f),
                    Color.Transparent,
                    Color(0xFF000616).copy(alpha = 0.085f * glassIntensity),
                    Color.White.copy(alpha = line * 0.18f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val topRimBloom = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.78f),
                    Color.White.copy(alpha = line * 0.22f),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF00040E).copy(alpha = 0.055f * glassIntensity)
                ),
                startY = 0f,
                endY = h
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.12f),
                    Color(0xFFB9F2FF).copy(alpha = line * 0.052f),
                    Color.Transparent,
                    Color(0xFF000612).copy(alpha = 0.060f * glassIntensity),
                    Color.White.copy(alpha = line * 0.050f)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.92f, h)
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.090f * glow),
                    Color.White.copy(alpha = 0.020f * glow),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.36f), 0f),
                end = Offset(w * (safeShimmer + 0.22f), h * 0.34f)
            )

            onDrawWithContent {
                drawRect(deepCore, blendMode = BlendMode.Multiply)
                drawRect(frostedBody, blendMode = BlendMode.Screen)
                drawRect(edgeAir, blendMode = BlendMode.Screen)
                drawRect(leftBloom, blendMode = BlendMode.Screen)
                drawRect(lowerDepth, blendMode = BlendMode.Multiply)
                drawRect(softDiagonal, blendMode = BlendMode.Screen)
                drawRect(caustic, blendMode = BlendMode.Plus)

                val waveCount = if (quality.enableMotion) 3 else 1
                repeat(waveCount) { index ->
                    val y = h * (0.20f + index * 0.21f + 0.010f * sin(safeShimmer * 6.28318f + index * 1.2f).toFloat())
                    val alpha = (0.0048f + index * 0.0010f) * glow
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

                drawRect(bottomFade, blendMode = BlendMode.Multiply)
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
                    style = Stroke(width = 0.70.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
        }
}
