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
    Shell(0.92f, 1.20f, 1.04f, 24),
    Card(0.84f, 1.12f, 0.96f, 18),
    Chip(0.46f, 0.72f, 0.58f, 7),
    Nav(1.00f, 1.34f, 1.12f, 24),
    Floating(1.04f, 1.36f, 1.18f, 26)
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
                    GlassRole.Shell, GlassRole.Floating -> 22
                    GlassRole.Card -> 20
                    GlassRole.Nav -> 16
                    GlassRole.Chip -> 0
                },
                liftAlpha = when (role) {
                    GlassRole.Nav -> 1.16f
                    GlassRole.Shell -> 1.10f
                    GlassRole.Floating -> 1.12f
                    else -> 1.00f
                }
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = sampledBackdrop.quality,
                motionIntensity = sampledBackdrop.motionIntensity,
                theme = sampledBackdrop.theme,
                strength = when (role) {
                    GlassRole.Nav -> 1.32f
                    GlassRole.Shell -> 1.18f
                    GlassRole.Floating -> 1.22f
                    GlassRole.Card -> 1.08f
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
        targetValue = if (pressed) 0.976f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1.15f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalGlassBackdrop.current
    val sampledBackdrop = if (role == GlassRole.Nav || role == GlassRole.Floating) backdrop else null
    val pressedIntensity = if (pressed) glassIntensity * 1.10f else glassIntensity

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.4f else 0f
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
                blurRadiusDp = if (role == GlassRole.Nav) 16 else 18,
                liftAlpha = if (pressed) 1.22f else 1.08f
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = sampledBackdrop.quality,
                motionIntensity = sampledBackdrop.motionIntensity,
                theme = sampledBackdrop.theme,
                strength = if (role == GlassRole.Nav) 1.28f else 1.16f
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer + if (pressed) 0.055f else 0f,
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
            animation = tween((14600 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((8600 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val pulse = 0.90f + breathe * 0.09f
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
                    Color.White.copy(alpha = material.fill + material.haze * 0.46f),
                    Color.White.copy(alpha = material.fill + material.haze * 0.20f),
                    Color.White.copy(alpha = material.fill * 0.40f),
                    Color.Black.copy(alpha = material.depth * 0.020f)
                ),
                start = Offset(0f, 0f),
                end = Offset(760f, 1320f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val drift = safeShimmer - 0.5f
            val phase = safeShimmer * 6.28318f
            val shineX = (0.04f + 0.92f * safeShimmer) * w
            val shineY = h * (0.012f + 0.052f * sin(phase).toFloat())
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val stripeCount = when {
                !quality.enableMotion -> 0
                role == GlassRole.Chip -> 0
                role == GlassRole.Nav -> 4
                role == GlassRole.Floating -> 5
                else -> 6
            }

            val upperFrost = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.haze * 1.35f * pulse),
                    Color.White.copy(alpha = material.haze * 0.46f),
                    Color.White.copy(alpha = material.haze * 0.12f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.86f
            )
            val centralCompression = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.016f),
                    Color.Black.copy(alpha = material.depth * 0.050f)
                ),
                center = Offset(w * 0.58f, h * 0.70f),
                radius = w * 0.98f
            )
            val upperLeftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.glow * 0.082f * pulse),
                    Color.White.copy(alpha = material.glow * 0.022f),
                    Color.Transparent
                ),
                center = Offset(w * (0.07f + drift * 0.035f), -h * 0.05f),
                radius = w * 0.58f
            )
            val sideLens = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.glow * 0.044f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.020f),
                    Color.White.copy(alpha = material.glow * 0.026f)
                )
            )
            val bottomThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = material.glow * 0.006f),
                    Color.Black.copy(alpha = material.depth * 0.074f)
                ),
                startY = h * 0.42f,
                endY = h
            )
            val mainRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = material.refraction * 0.032f * pulse),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.018f),
                    Color.White.copy(alpha = material.refraction * 0.018f),
                    Color.Transparent
                ),
                start = Offset(-w * 0.18f, h * (0.02f + drift * 0.052f)),
                end = Offset(w * 1.12f, h * (0.60f + drift * 0.078f))
            )
            val counterRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.016f),
                    Color.Transparent,
                    Color.White.copy(alpha = material.refraction * 0.014f),
                    Color.Transparent
                ),
                start = Offset(w * 1.10f, h * 0.04f),
                end = Offset(-w * 0.10f, h * 0.95f)
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (richRefraction) material.refraction * 0.032f else material.refraction * 0.006f),
                    Color.White.copy(alpha = if (richRefraction) material.refraction * 0.010f else material.refraction * 0.002f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.48f
            )

            val outerGlowStroke = when (role) {
                GlassRole.Nav -> 4.20.dp.toPx()
                GlassRole.Chip -> 1.35.dp.toPx()
                else -> 3.35.dp.toPx()
            }
            val thicknessStroke = when (role) {
                GlassRole.Nav -> 1.28.dp.toPx()
                GlassRole.Chip -> 0.54.dp.toPx()
                else -> 1.06.dp.toPx()
            }
            val outerStroke = when (role) {
                GlassRole.Nav -> 0.78.dp.toPx()
                GlassRole.Chip -> 0.38.dp.toPx()
                else -> 0.64.dp.toPx()
            }
            val innerStroke = when (role) {
                GlassRole.Chip -> 0.22.dp.toPx()
                else -> 0.42.dp.toPx()
            }
            val hairStroke = when (role) {
                GlassRole.Chip -> 0.16.dp.toPx()
                else -> 0.24.dp.toPx()
            }
            val outerGlowInset = outerGlowStroke / 2f
            val thicknessInset = 1.10.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = if (role == GlassRole.Nav) 2.60.dp.toPx() else 2.15.dp.toPx()
            val hairInset = if (role == GlassRole.Nav) 4.70.dp.toPx() else 3.85.dp.toPx()
            val outerGlowSize = Size(w - outerGlowStroke, h - outerGlowStroke)
            val thicknessSize = Size(w - thicknessInset * 2f, h - thicknessInset * 2f)
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val featherRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.58f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.16f * material.rimDetail),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = material.rim * 0.055f * material.rimDetail)
                ),
                startY = 0f,
                endY = h
            )
            val thicknessRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.64f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.18f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.048f),
                    Color.White.copy(alpha = material.rim * 0.095f * material.rimDetail)
                ),
                start = Offset(w * 0.03f, 0f),
                end = Offset(w * 0.98f, h)
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.78f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.22f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.030f),
                    Color.White.copy(alpha = material.rim * 0.12f * material.rimDetail)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.15f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.050f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.034f),
                    Color.White.copy(alpha = material.rim * 0.052f * material.rimDetail)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.94f, h)
            )
            val topHairline = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.86f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.18f * material.rimDetail),
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
                    Color.Black.copy(alpha = material.depth * 0.035f),
                    Color.Black.copy(alpha = material.depth * 0.088f)
                ),
                startY = h * 0.56f,
                endY = h
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = material.glow * 0.058f * material.rimDetail),
                    Color.White.copy(alpha = material.glow * 0.014f * material.rimDetail),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.38f), 0f),
                end = Offset(w * (safeShimmer + 0.22f), h * 0.32f)
            )
            val cornerCatchlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.42f),
                    Color.White.copy(alpha = material.rim * 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.035f, h * 0.022f),
                radius = w * 0.34f
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
                    drawRect(counterRefraction, blendMode = BlendMode.Multiply)
                    drawRect(movingCaustic, blendMode = BlendMode.Plus)
                }

                repeat(stripeCount) { index ->
                    val stripePhase = phase + index * 0.62f
                    val y = h * (0.070f + index * 0.118f + 0.010f * sin(stripePhase).toFloat())
                    val height = h * (0.034f + (index % 3) * 0.006f)
                    val alpha = (0.0026f + (index % 4) * 0.0007f) * material.stripe
                    val xOffset = w * 0.038f * sin(stripePhase * 0.72f).toFloat()
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = alpha),
                                Color.Transparent,
                                Color.Black.copy(alpha = alpha * 0.62f),
                                Color.Transparent
                            ),
                            start = Offset(xOffset, y),
                            end = Offset(w + xOffset, y + height)
                        ),
                        topLeft = Offset(-w * 0.24f + xOffset, y - height * 1.80f),
                        size = Size(w * 1.52f, height * 3.80f),
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
                    drawRoundRect(brush = grazingGlint, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.34.dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = cornerCatchlight, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.46.dp.toPx()), blendMode = BlendMode.Screen)
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
    val safeIntensity = intensity.coerceIn(0.35f, 1.55f)
    val base = when (role) {
        GlassRole.Shell -> GlassMaterial(0.030f, 0.046f, 0.170f, 0.98f, 0.92f, 0.92f, 0.90f, 1.00f, 0.190f, 0.058f)
        GlassRole.Card -> GlassMaterial(0.026f, 0.040f, 0.154f, 0.92f, 0.84f, 0.82f, 0.78f, 0.94f, 0.165f, 0.048f)
        GlassRole.Chip -> GlassMaterial(0.018f, 0.026f, 0.100f, 0.62f, 0.48f, 0.24f, 0.16f, 0.54f, 0.080f, 0.020f)
        GlassRole.Nav -> GlassMaterial(0.034f, 0.052f, 0.205f, 1.08f, 0.98f, 1.02f, 0.86f, 1.10f, 0.220f, 0.070f)
        GlassRole.Floating -> GlassMaterial(0.036f, 0.056f, 0.210f, 1.14f, 1.04f, 1.08f, 0.82f, 1.08f, 0.210f, 0.068f)
    }
    return GlassMaterial(
        fill = (base.fill * safeIntensity * role.fillScale).coerceIn(0.006f, 0.078f),
        haze = (base.haze * safeIntensity * role.fillScale).coerceIn(0.010f, 0.118f),
        rim = (base.rim * safeIntensity * role.rimScale).coerceIn(0.040f, 0.360f),
        glow = (base.glow * safeIntensity * role.glowScale).coerceIn(0.34f, 1.58f),
        depth = (base.depth * safeIntensity * role.rimScale).coerceIn(0.32f, 1.46f),
        refraction = (base.refraction * safeIntensity).coerceIn(0.12f, 1.42f),
        stripe = (base.stripe * safeIntensity).coerceIn(0.08f, 1.24f),
        rimDetail = base.rimDetail,
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.052f, 0.280f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.012f, 0.092f)
    )
}
