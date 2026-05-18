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
    Shell(1.02f, 1.20f, 1.04f, 19),
    Card(0.92f, 1.12f, 0.96f, 15),
    Chip(0.52f, 0.74f, 0.56f, 5),
    Nav(1.04f, 1.34f, 1.10f, 21),
    Floating(1.04f, 1.34f, 1.12f, 22)
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
                    GlassRole.Nav -> 0.56f
                    GlassRole.Shell -> 0.54f
                    GlassRole.Floating -> 0.52f
                    GlassRole.Card -> 0.48f
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
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.45f else 0f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalGlassBackdrop.current
    val sampledBackdrop = if (role == GlassRole.Nav || role == GlassRole.Floating) backdrop else null
    val pressedIntensity = if (pressed) glassIntensity * 1.04f else glassIntensity

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.25f else 0f
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
                liftAlpha = if (pressed) 0.62f else 0.52f
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer + if (pressed) 0.026f else 0f,
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
            animation = tween((15000 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = LinearEasing),
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
            animation = tween((9200 / motionIntensity.coerceAtLeast(0.35f)).toInt(), easing = FastOutSlowInEasing),
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
    val pulse = 0.92f + breathe * 0.055f
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
                    Color(0xFFF4F7FB).copy(alpha = material.fill + material.haze * 0.76f),
                    Color(0xFFE5ECF5).copy(alpha = material.fill + material.haze * 0.42f),
                    Color.White.copy(alpha = material.fill * 0.52f),
                    Color.Black.copy(alpha = material.depth * 0.010f)
                ),
                start = Offset(0f, 0f),
                end = Offset(820f, 1380f)
            ),
            shape = shape
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val drift = safeShimmer - 0.5f
            val phase = safeShimmer * 6.28318f
            val shineX = (0.06f + 0.88f * safeShimmer) * w
            val shineY = h * (0.010f + 0.038f * sin(phase).toFloat())
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val stripeCount = when {
                !quality.enableMotion -> 0
                role == GlassRole.Chip -> 0
                role == GlassRole.Nav -> 1
                else -> 1
            }

            val neutralMilkyVeil = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.haze * 1.65f * pulse),
                    Color(0xFFEAF0F7).copy(alpha = material.haze * 0.72f),
                    Color.White.copy(alpha = material.haze * 0.24f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.84f
            )
            val colorDesaturationVeil = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF2F5F9).copy(alpha = material.neutralize * 0.92f),
                    Color(0xFFDDE6F0).copy(alpha = material.neutralize * 0.52f),
                    Color(0xFFBAC7D8).copy(alpha = material.neutralize * 0.16f),
                    Color.Black.copy(alpha = material.depth * 0.014f)
                ),
                startY = 0f,
                endY = h
            )
            val centralCompression = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.010f),
                    Color.Black.copy(alpha = material.depth * 0.034f)
                ),
                center = Offset(w * 0.58f, h * 0.72f),
                radius = w * 0.98f
            )
            val upperLeftLens = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.glow * 0.072f * pulse),
                    Color.White.copy(alpha = material.glow * 0.020f),
                    Color.Transparent
                ),
                center = Offset(w * (0.07f + drift * 0.024f), -h * 0.045f),
                radius = w * 0.55f
            )
            val sideLens = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.glow * 0.044f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.014f),
                    Color.White.copy(alpha = material.glow * 0.016f)
                )
            )
            val bottomThickness = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = material.glow * 0.004f),
                    Color.Black.copy(alpha = material.depth * 0.058f)
                ),
                startY = h * 0.46f,
                endY = h
            )
            val mainRefractionBand = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = material.refraction * 0.014f * pulse),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.008f),
                    Color.Transparent
                ),
                start = Offset(-w * 0.14f, h * (0.05f + drift * 0.032f)),
                end = Offset(w * 1.08f, h * (0.58f + drift * 0.048f))
            )
            val movingCaustic = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (richRefraction) material.refraction * 0.014f else material.refraction * 0.003f),
                    Color.White.copy(alpha = if (richRefraction) material.refraction * 0.003f else material.refraction * 0.001f),
                    Color.Transparent
                ),
                center = Offset(shineX, shineY),
                radius = w * 0.42f
            )

            val outerGlowStroke = when (role) {
                GlassRole.Nav -> 2.30.dp.toPx()
                GlassRole.Chip -> 0.78.dp.toPx()
                else -> 1.85.dp.toPx()
            }
            val thicknessStroke = when (role) {
                GlassRole.Nav -> 0.88.dp.toPx()
                GlassRole.Chip -> 0.34.dp.toPx()
                else -> 0.72.dp.toPx()
            }
            val outerStroke = when (role) {
                GlassRole.Nav -> 0.52.dp.toPx()
                GlassRole.Chip -> 0.28.dp.toPx()
                else -> 0.46.dp.toPx()
            }
            val innerStroke = when (role) {
                GlassRole.Chip -> 0.14.dp.toPx()
                else -> 0.28.dp.toPx()
            }
            val hairStroke = when (role) {
                GlassRole.Chip -> 0.10.dp.toPx()
                else -> 0.16.dp.toPx()
            }
            val outerGlowInset = outerGlowStroke / 2f
            val thicknessInset = 0.96.dp.toPx()
            val outerInset = outerStroke / 2f
            val innerInset = if (role == GlassRole.Nav) 2.12.dp.toPx() else 1.78.dp.toPx()
            val hairInset = if (role == GlassRole.Nav) 3.75.dp.toPx() else 3.10.dp.toPx()
            val outerGlowSize = Size(w - outerGlowStroke, h - outerGlowStroke)
            val thicknessSize = Size(w - thicknessInset * 2f, h - thicknessInset * 2f)
            val outerSize = Size(w - outerStroke, h - outerStroke)
            val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
            val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)

            val featherRim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.46f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.10f * material.rimDetail),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = material.rim * 0.030f * material.rimDetail)
                ),
                startY = 0f,
                endY = h
            )
            val thicknessRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.54f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.13f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.026f),
                    Color.White.copy(alpha = material.rim * 0.065f * material.rimDetail)
                ),
                start = Offset(w * 0.03f, 0f),
                end = Offset(w * 0.98f, h)
            )
            val outerRim = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.74f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.18f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.018f),
                    Color.White.copy(alpha = material.rim * 0.075f * material.rimDetail)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            val innerRefraction = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.100f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.028f * material.rimDetail),
                    Color.Transparent,
                    Color.Black.copy(alpha = material.depth * 0.020f),
                    Color.White.copy(alpha = material.rim * 0.024f * material.rimDetail)
                ),
                start = Offset(w * 0.08f, 0f),
                end = Offset(w * 0.94f, h)
            )
            val topHairline = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.86f * material.rimDetail),
                    Color.White.copy(alpha = material.rim * 0.16f * material.rimDetail),
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
                    Color.Black.copy(alpha = material.depth * 0.024f),
                    Color.Black.copy(alpha = material.depth * 0.064f)
                ),
                startY = h * 0.56f,
                endY = h
            )
            val grazingGlint = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = material.glow * 0.030f * material.rimDetail),
                    Color.White.copy(alpha = material.glow * 0.006f * material.rimDetail),
                    Color.Transparent
                ),
                start = Offset(w * (safeShimmer - 0.32f), 0f),
                end = Offset(w * (safeShimmer + 0.18f), h * 0.26f)
            )
            val cornerCatchlight = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = material.rim * 0.28f),
                    Color.White.copy(alpha = material.rim * 0.058f),
                    Color.Transparent
                ),
                center = Offset(w * 0.035f, h * 0.020f),
                radius = w * 0.30f
            )

            onDrawWithContent {
                drawRect(neutralMilkyVeil, blendMode = BlendMode.Screen)
                drawRect(colorDesaturationVeil, blendMode = BlendMode.SrcOver)
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
                    val stripePhase = phase + index * 0.70f
                    val y = h * (0.18f + index * 0.280f + 0.004f * sin(stripePhase).toFloat())
                    val height = h * 0.016f
                    val alpha = (0.0008f + index * 0.0002f) * material.stripe
                    val xOffset = w * 0.018f * sin(stripePhase * 0.68f).toFloat()
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                            start = Offset(xOffset, y),
                            end = Offset(w + xOffset, y + height)
                        ),
                        topLeft = Offset(-w * 0.15f + xOffset, y - height * 1.30f),
                        size = Size(w * 1.30f, height * 2.50f),
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
                    drawRoundRect(brush = grazingGlint, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.20.dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = cornerCatchlight, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.28.dp.toPx()), blendMode = BlendMode.Screen)
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
    val neutralize: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(role: GlassRole, intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.35f, 1.35f)
    val base = when (role) {
        GlassRole.Shell -> GlassMaterial(0.040f, 0.064f, 0.175f, 0.94f, 0.78f, 0.40f, 0.20f, 0.96f, 0.170f, 0.130f, 0.038f)
        GlassRole.Card -> GlassMaterial(0.034f, 0.056f, 0.158f, 0.86f, 0.68f, 0.34f, 0.16f, 0.90f, 0.150f, 0.110f, 0.030f)
        GlassRole.Chip -> GlassMaterial(0.020f, 0.030f, 0.082f, 0.46f, 0.34f, 0.08f, 0.04f, 0.42f, 0.060f, 0.044f, 0.010f)
        GlassRole.Nav -> GlassMaterial(0.044f, 0.068f, 0.198f, 0.98f, 0.82f, 0.36f, 0.12f, 1.02f, 0.175f, 0.140f, 0.044f)
        GlassRole.Floating -> GlassMaterial(0.046f, 0.070f, 0.200f, 1.00f, 0.82f, 0.36f, 0.10f, 1.00f, 0.170f, 0.135f, 0.040f)
    }
    return GlassMaterial(
        fill = (base.fill * safeIntensity * role.fillScale).coerceIn(0.010f, 0.110f),
        haze = (base.haze * safeIntensity * role.fillScale).coerceIn(0.016f, 0.150f),
        rim = (base.rim * safeIntensity * role.rimScale).coerceIn(0.040f, 0.330f),
        glow = (base.glow * safeIntensity * role.glowScale).coerceIn(0.24f, 1.34f),
        depth = (base.depth * safeIntensity * role.rimScale).coerceIn(0.22f, 1.10f),
        refraction = (base.refraction * safeIntensity).coerceIn(0.035f, 0.56f),
        stripe = (base.stripe * safeIntensity).coerceIn(0.010f, 0.36f),
        rimDetail = base.rimDetail,
        neutralize = (base.neutralize * safeIntensity).coerceIn(0.030f, 0.260f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.032f, 0.180f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.005f, 0.058f)
    )
}
