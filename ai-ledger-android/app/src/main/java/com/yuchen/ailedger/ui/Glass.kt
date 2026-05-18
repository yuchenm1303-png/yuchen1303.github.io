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
    Shell(0.84f, 1.10f, 0.96f, 20),
    Card(0.72f, 1.00f, 0.88f, 15),
    Chip(0.44f, 0.66f, 0.54f, 6),
    Nav(0.92f, 1.22f, 1.04f, 22),
    Floating(0.94f, 1.24f, 1.08f, 24)
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
            .glassOuterFrame(radius = radius, role = role, glassIntensity = glassIntensity)
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
                    GlassRole.Shell, GlassRole.Floating -> 16
                    GlassRole.Card -> 12
                    GlassRole.Nav -> 12
                    GlassRole.Chip -> 0
                },
                liftAlpha = when (role) {
                    GlassRole.Nav -> 0.92f
                    GlassRole.Shell -> 0.86f
                    GlassRole.Floating -> 0.90f
                    GlassRole.Card -> 0.76f
                    GlassRole.Chip -> 0f
                }
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer,
                    breathe = breathe,
                    glassIntensity = glassIntensity,
                    role = role,
                    includeShadow = false
                )
        )
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
        targetValue = if (pressed) 0.980f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.7f else 0f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalGlassBackdrop.current
    val sampledBackdrop = if (role == GlassRole.Nav || role == GlassRole.Floating) backdrop else null
    val pressedIntensity = if (pressed) glassIntensity * 1.06f else glassIntensity

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.35f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = radius, role = role, glassIntensity = pressedIntensity)
    ) {
        if (sampledBackdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = sampledBackdrop.quality,
                motionIntensity = sampledBackdrop.motionIntensity,
                theme = sampledBackdrop.theme,
                blurRadiusDp = if (role == GlassRole.Nav) 12 else 14,
                liftAlpha = if (pressed) 0.98f else 0.84f
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer + if (pressed) 0.035f else 0f,
                    breathe = breathe,
                    glassIntensity = pressedIntensity,
                    role = role,
                    includeShadow = false
                )
        )
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
            animation = tween((13200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((7800 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass-breath-value"
    )
    return breathe
}

private fun Modifier.glassOuterFrame(
    radius: Int,
    role: GlassRole,
    glassIntensity: Float
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(role, glassIntensity)
    return this
        .shadow(
            elevation = role.shadowDp.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = material.shadowAmbient),
            spotColor = Color.White.copy(alpha = material.shadowSpot)
        )
        .clip(shape)
}

fun Modifier.glassSkin(
    quality: RenderQuality,
    radius: Int,
    shimmer: Float = 0f,
    breathe: Float = 0.42f,
    glassIntensity: Float = 1f,
    role: GlassRole = GlassRole.Card,
    includeShadow: Boolean = true
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(role, glassIntensity)
    val pulse = 0.91f + breathe * 0.075f
    val safeShimmer = shimmer - shimmer.toInt()
    val richRefraction = quality.enableMotion && role != GlassRole.Chip
    val base = if (includeShadow) {
        this.glassOuterFrame(radius = radius, role = role, glassIntensity = glassIntensity)
    } else {
        this.clip(shape)
    }

    return base
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.fill + material.haze * 0.38f),
                    Color.White.copy(alpha = material.fill + material.haze * 0.14f),
                    Color.White.copy(alpha = material.fill * 0.34f),
                    Color.Black.copy(alpha = material.depth * 0.014f)
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
            val phase = safeShimmer * 6.28318f
            val shineX = (0.05f + 0.90f * safeShimmer) * w
            val shineY = h * (0.014f + 0.050f * sin(phase).toFloat())
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val stripeCount = when {
                !quality.enableMotion -> 0
                role == GlassRole.Chip -> 0
                role == GlassRole.Nav -> 2
                role == GlassRole.Floating -> 3
                else -> 3
            }

            val upperFrost = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.haze * 1.05f * pulse),
                    Color.White.copy(alpha = material.haze * 0.34f),
                    Color.White.copy(alpha = material.haze * 0.070f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.84f
            )
            val centralCompression = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.010f),
                    Color.Black.copy(alpha = material.depth * 0.034f)
                ),
                center = Offset(w * 0.58f, h * 0.70f),
                radius = w * 0.96f
            )
            val upperLeftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.glow * 0.054f * pulse),
                    Color.White.copy(alpha = material.glow * 0.014f),
                    Color.Transparent
                ),
                center = Offset(w * (0.07f + drift * 0.030f), -h * 0.04f),
                radius = w * 0.55f
            )
            val sideLens = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.glow * 0.026f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.012f),
                    Color.White.copy(alpha = material.glow * 0.014f)
                )
            )
            val bottomThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = material.glow * 0.004f),
                    Color.Black.copy(alpha = material.depth * 0.048f)
                ),
                startY = h * 0.45f,
                endY = h
            )
            val mainRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = material.refraction * 0.018f * pulse),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.010f),
                    Color.Transparent
                ),
                start = Offset(-w * 0.14f, h * (0.04f + drift * 0.040f)),
                end = Offset(w * 1.08f, h * (0.58f + drift * 0.060f))
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (richRefraction) material.refraction * 0.018f else material.refraction * 0.004f),
                    Color.White.copy(alpha = if (richRefraction) material.refraction * 0.004f else material.refraction * 0.001f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.46f
            )

            val outerGlowStroke = when (role) {
                GlassRole.Nav -> 3.10.dp.toPx()
                GlassRole.Chip -> 1.10.dp.toPx()
                else -> 2.45.dp.toPx()
            }
            val thicknessStroke = when (role) {
                GlassRole.Nav -> 1.00.dp.toPx()
                GlassRole.Chip -> 0.46.dp.toPx()
                else -> 0.82.dp.toPx()
            }
            val outerStroke = when (role) {
                GlassRole.Nav -> 0.64.dp.toPx()
                GlassRole.Chip -> 0.34.dp.toPx()
                else -> 0.52.dp.toPx()
            }
            val innerStroke = when (role) {
                GlassRole.Chip -> 0.18.dp.toPx()
                else -> 0.34.dp.toPx()
            }
            val hairStroke = when (role) {
                GlassRole.Chip -> 0.14.dp.toPx()
                else -> 0.20.dp.toPx()
            }
            val outerGlowInset = outerGlowStroke / 2f
            val thicknessInset = 1.05.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = if (role == GlassRole.Nav) 2.35.dp.toPx() else 1.95.dp.toPx()
            val hairInset = if (role == GlassRole.Nav) 4.20.dp.toPx() else 3.45.dp.toPx()
            val outerGlowSize = Size(w - outerGlowStroke, h - outerGlowStroke)
            val thicknessSize = Size(w - thicknessInset * 2f, h - thicknessInset * 2f)
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val featherRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.34f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.090f * material.rimDetail),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = material.rim * 0.034f * material.rimDetail)
                ),
                startY = 0f,
                endY = h
            )
            val thicknessRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.36f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.12f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.030f),
                    Color.White.copy(alpha = material.rim * 0.060f * material.rimDetail)
                ),
                start = Offset(w * 0.03f, 0f),
                end = Offset(w * 0.98f, h)
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.48f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.14f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.020f),
                    Color.White.copy(alpha = material.rim * 0.075f * material.rimDetail)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.090f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.030f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.022f),
                    Color.White.copy(alpha = material.rim * 0.030f * material.rimDetail)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.94f, h)
            )
            val topHairline = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.56f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.12f * material.rimDetail),
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
                    Color.Black.copy(alpha = material.depth * 0.022f),
                    Color.Black.copy(alpha = material.depth * 0.056f)
                ),
                startY = h * 0.56f,
                endY = h
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = material.glow * 0.034f * material.rimDetail),
                    Color.White.copy(alpha = material.glow * 0.008f * material.rimDetail),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.36f), 0f),
                end = Offset(w * (safeShimmer + 0.22f), h * 0.30f)
            )
            val cornerCatchlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.24f),
                    Color.White.copy(alpha = material.rim * 0.060f),
                    Color.Transparent
                ),
                center = Offset(w * 0.035f, h * 0.022f),
                radius = w * 0.32f
            )

            onDrawWithContent {
                drawRect(upperFrost, blendMode = BlendMode.Screen)
                if (role != GlassRole.Chip) {
                    drawRect(centralCompression, blendMode = BlendMode.Multiply)
                    drawRect(sideLens, blendMode = BlendMode.Screen)
                    drawRect(upperLeftLens, blendMode = BlendMode.Screen)
                }
                if (richRefraction) {
                    drawRect(mainRefractionBand, blendMode = BlendMode.Screen)
                    drawRect(movingCaustic, blendMode = BlendMode.Plus)
                }

                repeat(stripeCount) { index ->
                    val stripePhase = phase + index * 0.62f
                    val y = h * (0.095f + index * 0.220f + 0.006f * sin(stripePhase).toFloat())
                    val height = h * (0.020f + (index % 2) * 0.004f)
                    val alpha = (0.0012f + (index % 3) * 0.0003f) * material.stripe
                    val xOffset = w * 0.024f * sin(stripePhase * 0.72f).toFloat()
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                            start = Offset(xOffset, y),
                            end = Offset(w + xOffset, y + height)
                        ),
                        topLeft = Offset(-w * 0.18f + xOffset, y - height * 1.50f),
                        size = Size(w * 1.36f, height * 3.00f),
                        blendMode = BlendMode.Screen
                    )
                }

                drawRect(bottomThickness, blendMode = BlendMode.Multiply)
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
                    drawRoundRect(brush = grazingGlint, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.26.dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = cornerCatchlight, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.34.dp.toPx()), blendMode = BlendMode.Screen)
                }
            }
        }
}

private data class GlassMaterial(
    val fill: Float,
    val haze: Float,
    val rim: Float,
    val glow: Float,
    val depth: Float,
    val refraction: Float,
    val stripe: Float,
    val rimDetail: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(role: GlassRole, intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.35f, 1.35f)
    val base = when (role) {
        GlassRole.Shell -> GlassMaterial(0.024f, 0.036f, 0.138f, 0.86f, 0.76f, 0.64f, 0.56f, 0.92f, 0.150f, 0.044f)
        GlassRole.Card -> GlassMaterial(0.021f, 0.032f, 0.125f, 0.78f, 0.68f, 0.52f, 0.44f, 0.86f, 0.130f, 0.036f)
        GlassRole.Chip -> GlassMaterial(0.014f, 0.020f, 0.080f, 0.48f, 0.38f, 0.16f, 0.10f, 0.46f, 0.058f, 0.014f)
        GlassRole.Nav -> GlassMaterial(0.027f, 0.040f, 0.165f, 0.92f, 0.82f, 0.66f, 0.46f, 0.98f, 0.170f, 0.050f)
        GlassRole.Floating -> GlassMaterial(0.028f, 0.042f, 0.168f, 0.96f, 0.84f, 0.70f, 0.44f, 0.98f, 0.160f, 0.048f)
    }
    return GlassMaterial(
        fill = (base.fill * safeIntensity * role.fillScale).coerceIn(0.005f, 0.060f),
        haze = (base.haze * safeIntensity * role.fillScale).coerceIn(0.008f, 0.086f),
        rim = (base.rim * safeIntensity * role.rimScale).coerceIn(0.030f, 0.260f),
        glow = (base.glow * safeIntensity * role.glowScale).coerceIn(0.28f, 1.28f),
        depth = (base.depth * safeIntensity * role.rimScale).coerceIn(0.26f, 1.14f),
        refraction = (base.refraction * safeIntensity).coerceIn(0.08f, 0.95f),
        stripe = (base.stripe * safeIntensity).coerceIn(0.04f, 0.82f),
        rimDetail = base.rimDetail,
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.040f, 0.210f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.008f, 0.070f)
    )
}
