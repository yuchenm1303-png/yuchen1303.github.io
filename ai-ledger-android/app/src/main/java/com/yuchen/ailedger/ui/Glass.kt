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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.sin

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0.58f, 1.00f, 0.86f, 18),
    Card(0.60f, 0.98f, 0.84f, 14),
    Chip(0.60f, 0.88f, 0.72f, 7),
    Nav(0.54f, 0.96f, 0.80f, 16),
    Floating(0.68f, 1.08f, 0.96f, 20)
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
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalGlassBackdrop.current
    val sampledBackdrop = if (quality.enableMotion && role != GlassRole.Chip) backdrop else null
    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .glassSkin(
                quality = quality,
                radius = radius,
                shimmer = shimmer,
                breathe = breathe,
                glassIntensity = glassIntensity,
                role = role
            )
    ) {
        if (sampledBackdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = sampledBackdrop.quality,
                motionIntensity = sampledBackdrop.motionIntensity,
                theme = sampledBackdrop.theme,
                blurRadiusDp = when (role) {
                    GlassRole.Shell, GlassRole.Floating -> 18
                    GlassRole.Card -> 16
                    GlassRole.Nav -> 12
                    GlassRole.Chip -> 0
                }
            )
        }
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
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 0f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalGlassBackdrop.current
    val sampledBackdrop = if (role == GlassRole.Nav) backdrop else null

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.6f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassSkin(
                quality = quality,
                radius = radius,
                shimmer = shimmer + if (pressed) 0.035f else 0f,
                breathe = breathe,
                glassIntensity = if (pressed) glassIntensity * 1.04f else glassIntensity,
                role = role
            )
    ) {
        if (sampledBackdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = sampledBackdrop.quality,
                motionIntensity = sampledBackdrop.motionIntensity,
                theme = sampledBackdrop.theme,
                blurRadiusDp = 14
            )
        }
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    if (!quality.enableMotion || motionIntensity <= 0.02f) return 0.18f
    val transition = rememberInfiniteTransition(label = "glass-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((12800 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((7600 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val fill = (0.010f * glassIntensity * role.fillScale).coerceIn(0.004f, 0.030f)
    val haze = (0.020f * glassIntensity * role.fillScale).coerceIn(0.008f, 0.044f)
    val line = (0.085f * glassIntensity * role.rimScale).coerceIn(0.030f, 0.135f)
    val glow = (glassIntensity * role.glowScale).coerceIn(0.34f, 1.08f)
    val depth = (glassIntensity * role.rimScale).coerceIn(0.48f, 1.18f)
    val pulse = 0.92f + breathe * 0.06f
    val safeShimmer = shimmer - shimmer.toInt()
    val richRefraction = quality.enableMotion && role != GlassRole.Chip
    val rimDetail = when (role) {
        GlassRole.Chip -> 0.62f
        GlassRole.Nav -> 0.82f
        else -> 1f
    }

    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.135f),
            spotColor = Color.White.copy(alpha = 0.036f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = fill + haze * 0.35f),
                    Color.White.copy(alpha = fill + haze * 0.13f),
                    Color.White.copy(alpha = fill * 0.42f),
                    Color.White.copy(alpha = fill * 0.14f)
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
            val shineX = (0.05f + 0.90f * safeShimmer) * w
            val shineY = h * (0.015f + 0.060f * sin(safeShimmer * 6.28318f).toFloat())
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val stripeCount = when {
                !quality.enableMotion -> 0
                role == GlassRole.Chip -> 0
                role == GlassRole.Nav -> 3
                role == GlassRole.Floating -> 4
                else -> 5
            }

            val clearFrost = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.044f * glow * pulse),
                    Color.White.copy(alpha = 0.015f * glow),
                    Color.White.copy(alpha = 0.006f * glow),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
            val softBackdropBlur = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.022f * glow),
                    Color.White.copy(alpha = 0.006f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.26f + drift * 0.035f), h * 0.02f),
                radius = w * 0.92f
            )
            val sideLens = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.020f * glow),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.008f * glow)
                )
            )
            val upperLeftLift = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.038f * glow * pulse),
                    Color.White.copy(alpha = 0.009f * glow),
                    Color.Transparent
                ),
                center = Offset(w * (0.08f + drift * 0.03f), -h * 0.04f),
                radius = w * 0.54f
            )
            val lowerThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.004f * glow),
                    Color.Black.copy(alpha = 0.018f * depth)
                ),
                startY = h * 0.48f,
                endY = h
            )
            val mainRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.008f * glow * pulse),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.004f * glow),
                    Color.Transparent
                ),
                start = Offset(-w * 0.16f, h * (0.04f + drift * 0.04f)),
                end = Offset(w * 1.08f, h * (0.58f + drift * 0.06f))
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (richRefraction) 0.010f * glow else 0.003f * glow),
                    Color.White.copy(alpha = if (richRefraction) 0.002f * glow else 0.001f * glow),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.42f
            )

            val outerGlowStroke = 2.30.dp.toPx()
            val thicknessStroke = 0.84.dp.toPx()
            val outerStroke = 0.46.dp.toPx()
            val innerStroke = 0.30.dp.toPx()
            val hairStroke = 0.18.dp.toPx()
            val outerGlowInset = outerGlowStroke / 2f
            val thicknessInset = 1.05.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = 1.85.dp.toPx()
            val hairInset = 3.45.dp.toPx()
            val outerGlowSize = Size(w - outerGlowStroke, h - outerGlowStroke)
            val thicknessSize = Size(w - thicknessInset * 2f, h - thicknessInset * 2f)
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val featherRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.14f * rimDetail),
                    Color.White.copy(alpha = line * 0.040f * rimDetail),
                    Color.Transparent,
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
            val thicknessRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.13f * rimDetail),
                    Color.White.copy(alpha = line * 0.040f * rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.008f * depth),
                    Color.White.copy(alpha = line * 0.022f * rimDetail)
                ),
                start = Offset(w * 0.04f, 0f),
                end = Offset(w * 0.96f, h)
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.30f * rimDetail),
                    Color.White.copy(alpha = line * 0.074f * rimDetail),
                    Color.Transparent,
                    Color.White.copy(alpha = line * 0.024f * rimDetail)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.050f * rimDetail),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.010f * depth),
                    Color.White.copy(alpha = line * 0.020f * rimDetail)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.94f, h)
            )
            val topHairline = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.28f * rimDetail),
                    Color.White.copy(alpha = line * 0.045f * rimDetail),
                    Color.Transparent,
                    Color.Transparent
                ),
                startY = 0f,
                endY = h
            )
            val bottomHairShadow = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.008f * depth),
                    Color.Black.copy(alpha = 0.024f * depth)
                ),
                startY = h * 0.58f,
                endY = h
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.012f * glow * rimDetail),
                    Color.White.copy(alpha = 0.002f * glow * rimDetail),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.34f), 0f),
                end = Offset(w * (safeShimmer + 0.22f), h * 0.30f)
            )
            val cornerCatchlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = line * 0.075f),
                    Color.White.copy(alpha = line * 0.018f),
                    Color.Transparent
                ),
                center = Offset(w * 0.03f, h * 0.02f),
                radius = w * 0.30f
            )

            onDrawWithContent {
                if (role != GlassRole.Chip) {
                    drawRect(softBackdropBlur, blendMode = BlendMode.Screen)
                }
                drawRect(clearFrost, blendMode = BlendMode.Screen)
                drawRect(sideLens, blendMode = BlendMode.Screen)
                drawRect(upperLeftLift, blendMode = BlendMode.Screen)
                if (richRefraction) {
                    drawRect(mainRefractionBand, blendMode = BlendMode.Screen)
                    drawRect(movingCaustic, blendMode = BlendMode.Plus)
                }

                repeat(stripeCount) { index ->
                    val phase = safeShimmer * 6.28318f + index * 0.58f
                    val y = h * (0.080f + index * 0.150f + 0.006f * sin(phase).toFloat())
                    val height = h * (0.026f + (index % 3) * 0.004f)
                    val alpha = (0.0014f + (index % 4) * 0.0003f) * glow
                    val xOffset = w * 0.026f * sin(phase * 0.72f).toFloat()
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                            start = Offset(xOffset, y),
                            end = Offset(w + xOffset, y + height)
                        ),
                        topLeft = Offset(-w * 0.20f + xOffset, y - height * 1.7f),
                        size = Size(w * 1.40f, height * 3.4f),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(lowerThickness, blendMode = BlendMode.Multiply)
                drawContent()

                if (role != GlassRole.Chip) {
                    drawRoundRect(brush = featherRim, topLeft = Offset(outerGlowInset, outerGlowInset), size = outerGlowSize, cornerRadius = cornerRadius, style = Stroke(width = outerGlowStroke), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = thicknessRim, topLeft = Offset(thicknessInset, thicknessInset), size = thicknessSize, cornerRadius = cornerRadius, style = Stroke(width = thicknessStroke), blendMode = BlendMode.Screen)
                }
                drawRoundRect(brush = outerRim, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = outerStroke), blendMode = BlendMode.Screen)
                if (role != GlassRole.Chip) {
                    drawRoundRect(brush = innerRefraction, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = innerStroke), blendMode = BlendMode.SrcOver)
                    drawRoundRect(brush = topHairline, topLeft = Offset(hairInset, hairInset), size = hairSize, cornerRadius = cornerRadius, style = Stroke(width = hairStroke), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = bottomHairShadow, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = innerStroke), blendMode = BlendMode.Multiply)
                    drawRoundRect(brush = grazingGlint, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.24.dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = cornerCatchlight, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.28.dp.toPx()), blendMode = BlendMode.Screen)
                }
            }
        }
}
