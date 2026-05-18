package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0f, 1.24f, 0.95f, 15),
    Card(0f, 1.14f, 0.86f, 12),
    Chip(0f, 0.72f, 0.54f, 4),
    Nav(0f, 1.36f, 1.00f, 16),
    Floating(0f, 1.32f, 1.02f, 17)
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

    Box(
        modifier = modifier
            .onGloballyPositioned { globalOffset = it.localToWindow(Offset.Zero) }
            .glassOuterFrame(radius = radius, role = role, glassIntensity = glassIntensity)
    ) {
        if (backdrop != null && role != GlassRole.Chip) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = when (role) {
                    GlassRole.Shell -> 22
                    GlassRole.Card -> 18
                    GlassRole.Nav -> 16
                    GlassRole.Floating -> 18
                    GlassRole.Chip -> 0
                },
                liftAlpha = when (role) {
                    GlassRole.Shell -> 1.05f
                    GlassRole.Card -> 0.92f
                    GlassRole.Nav -> 0.96f
                    GlassRole.Floating -> 0.98f
                    GlassRole.Chip -> 0f
                }
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = when (role) {
                    GlassRole.Shell -> 0.86f
                    GlassRole.Card -> 0.72f
                    GlassRole.Nav -> 0.90f
                    GlassRole.Floating -> 0.82f
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
        targetValue = if (pressed) 0.40f else 0f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    var globalOffset by remember { mutableStateOf(Offset.Zero) }
    val backdrop = LocalGlassBackdrop.current
    val pressedIntensity = if (pressed) glassIntensity * 1.08f else glassIntensity

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
        if (backdrop != null && role != GlassRole.Chip) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = if (role == GlassRole.Nav) 16 else 18,
                liftAlpha = if (pressed) 1.06f else 0.94f
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = if (pressed) 0.92f else 0.76f
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = radius,
                    shimmer = shimmer + if (pressed) 0.030f else 0f,
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
    val base = if (includeShadow) {
        this.glassOuterFrame(radius = radius, role = role, glassIntensity = glassIntensity)
    } else {
        this.clip(shape)
    }

    return base.drawWithCache {
        val w = size.width
        val h = size.height
        val drift = safeShimmer - 0.5f
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())

        val outerGlowStroke = when (role) {
            GlassRole.Nav -> 2.60.dp.toPx()
            GlassRole.Chip -> 0.82.dp.toPx()
            else -> 2.05.dp.toPx()
        }
        val thicknessStroke = when (role) {
            GlassRole.Nav -> 1.02.dp.toPx()
            GlassRole.Chip -> 0.38.dp.toPx()
            else -> 0.84.dp.toPx()
        }
        val outerStroke = when (role) {
            GlassRole.Nav -> 0.62.dp.toPx()
            GlassRole.Chip -> 0.32.dp.toPx()
            else -> 0.54.dp.toPx()
        }
        val crispStroke = when (role) {
            GlassRole.Chip -> 0.20.dp.toPx()
            else -> 0.34.dp.toPx()
        }
        val innerStroke = when (role) {
            GlassRole.Chip -> 0.16.dp.toPx()
            else -> 0.34.dp.toPx()
        }
        val hairStroke = when (role) {
            GlassRole.Chip -> 0.12.dp.toPx()
            else -> 0.22.dp.toPx()
        }

        val outerGlowInset = outerGlowStroke / 2f
        val thicknessInset = 0.94.dp.toPx()
        val outerInset = outerStroke / 2f
        val crispInset = 0.72.dp.toPx()
        val innerInset = if (role == GlassRole.Nav) 2.05.dp.toPx() else 1.70.dp.toPx()
        val hairInset = if (role == GlassRole.Nav) 3.55.dp.toPx() else 2.95.dp.toPx()
        val bottomInset = if (role == GlassRole.Nav) 2.70.dp.toPx() else 2.20.dp.toPx()
        val outerGlowSize = Size(w - outerGlowStroke, h - outerGlowStroke)
        val thicknessSize = Size(w - thicknessInset * 2f, h - thicknessInset * 2f)
        val outerSize = Size(w - outerStroke, h - outerStroke)
        val crispSize = Size(w - crispInset * 2f, h - crispInset * 2f)
        val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
        val hairSize = Size(w - hairInset * 2f, h - hairInset * 2f)
        val bottomSize = Size(w - bottomInset * 2f, h - bottomInset * 2f)

        val frostedNeutralVeil = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.frost * pulse),
                Color(0xFFE8EDF3).copy(alpha = material.frost * 0.70f),
                Color(0xFFC7D0DC).copy(alpha = material.frost * 0.28f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h
        )
        val topLens = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.topHighlight * pulse),
                Color.White.copy(alpha = material.topHighlight * 0.24f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.30f
        )
        val lowerShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow)
            ),
            startY = h * 0.58f,
            endY = h
        )
        val sideCatch = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.sideHighlight),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = material.sideHighlight * 0.40f)
            )
        )
        val movingEdgeGlint = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = material.motionGlint),
                Color.White.copy(alpha = material.motionGlint * 0.20f),
                Color.Transparent
            ),
            start = Offset(w * (safeShimmer - 0.30f), 0f),
            end = Offset(w * (safeShimmer + 0.18f), h * 0.24f)
        )
        val cornerCatchlight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = material.cornerHighlight),
                Color.White.copy(alpha = material.cornerHighlight * 0.28f),
                Color.Transparent
            ),
            center = Offset(w * (0.035f + drift * 0.010f), h * 0.020f),
            radius = w * 0.32f
        )

        val featherRim = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.50f),
                Color.White.copy(alpha = material.rim * 0.080f),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = material.rim * 0.030f)
            ),
            startY = 0f,
            endY = h
        )
        val thicknessRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.58f),
                Color.White.copy(alpha = material.rim * 0.125f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.62f),
                Color.White.copy(alpha = material.rim * 0.060f)
            ),
            start = Offset(w * 0.03f, 0f),
            end = Offset(w * 0.98f, h)
        )
        val outerRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.82f),
                Color.White.copy(alpha = material.rim * 0.20f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.46f),
                Color.White.copy(alpha = material.rim * 0.070f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val crispTopLine = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 1.08f),
                Color.White.copy(alpha = material.rim * 0.18f),
                Color.Transparent,
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.46f
        )
        val innerRefraction = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.120f),
                Color.White.copy(alpha = material.rim * 0.035f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.48f),
                Color.White.copy(alpha = material.rim * 0.030f)
            ),
            start = Offset(w * 0.08f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val topHairline = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 1.05f),
                Color.White.copy(alpha = material.rim * 0.16f),
                Color.Transparent,
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.36f
        )
        val bottomHairShadow = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.78f),
                Color.Black.copy(alpha = material.depthShadow * 1.20f)
            ),
            startY = h * 0.50f,
            endY = h
        )
        val bottomThinLight = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = material.rim * 0.035f),
                Color.Transparent
            ),
            startY = h * 0.42f,
            endY = h
        )

        onDrawWithContent {
            drawRect(frostedNeutralVeil, blendMode = BlendMode.Screen)
            drawRect(topLens, blendMode = BlendMode.Screen)
            drawRect(sideCatch, blendMode = BlendMode.Screen)
            drawRect(lowerShade, blendMode = BlendMode.Multiply)
            drawContent()

            if (role != GlassRole.Chip) {
                drawRoundRect(brush = featherRim, topLeft = Offset(outerGlowInset, outerGlowInset), size = outerGlowSize, cornerRadius = cornerRadius, style = Stroke(width = outerGlowStroke), blendMode = BlendMode.Screen)
                drawRoundRect(brush = thicknessRim, topLeft = Offset(thicknessInset, thicknessInset), size = thicknessSize, cornerRadius = cornerRadius, style = Stroke(width = thicknessStroke), blendMode = BlendMode.Screen)
            }
            drawRoundRect(brush = outerRim, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = outerStroke), blendMode = BlendMode.Screen)
            drawRoundRect(brush = crispTopLine, topLeft = Offset(crispInset, crispInset), size = crispSize, cornerRadius = cornerRadius, style = Stroke(width = crispStroke), blendMode = BlendMode.Screen)
            if (role != GlassRole.Chip) {
                drawRoundRect(brush = innerRefraction, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = innerStroke), blendMode = BlendMode.SrcOver)
                drawRoundRect(brush = topHairline, topLeft = Offset(hairInset, hairInset), size = hairSize, cornerRadius = cornerRadius, style = Stroke(width = hairStroke), blendMode = BlendMode.Screen)
                drawRoundRect(brush = bottomHairShadow, topLeft = Offset(bottomInset, bottomInset), size = bottomSize, cornerRadius = cornerRadius, style = Stroke(width = hairStroke), blendMode = BlendMode.Multiply)
                drawRoundRect(brush = bottomThinLight, topLeft = Offset(bottomInset, bottomInset), size = bottomSize, cornerRadius = cornerRadius, style = Stroke(width = 0.16.dp.toPx()), blendMode = BlendMode.Screen)
                if (quality.enableMotion) {
                    drawRoundRect(brush = movingEdgeGlint, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.18.dp.toPx()), blendMode = BlendMode.Plus)
                }
                drawRoundRect(brush = cornerCatchlight, topLeft = Offset(outerInset, outerInset), size = outerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.36.dp.toPx()), blendMode = BlendMode.Screen)
            }
        }
    }
}

private data class GlassMaterial(
    val frost: Float,
    val rim: Float,
    val topHighlight: Float,
    val sideHighlight: Float,
    val cornerHighlight: Float,
    val motionGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(role: GlassRole, intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = when (role) {
        GlassRole.Shell -> GlassMaterial(0.075f, 0.245f, 0.078f, 0.030f, 0.082f, 0.024f, 0.034f, 0.095f, 0.026f)
        GlassRole.Card -> GlassMaterial(0.058f, 0.215f, 0.062f, 0.024f, 0.068f, 0.020f, 0.030f, 0.076f, 0.020f)
        GlassRole.Chip -> GlassMaterial(0.026f, 0.105f, 0.028f, 0.010f, 0.026f, 0.008f, 0.014f, 0.032f, 0.007f)
        GlassRole.Nav -> GlassMaterial(0.066f, 0.260f, 0.074f, 0.030f, 0.080f, 0.022f, 0.032f, 0.085f, 0.024f)
        GlassRole.Floating -> GlassMaterial(0.068f, 0.250f, 0.072f, 0.028f, 0.078f, 0.022f, 0.032f, 0.082f, 0.022f)
    }
    return GlassMaterial(
        frost = (base.frost * safeIntensity).coerceIn(0.010f, 0.120f),
        rim = (base.rim * safeIntensity * role.rimScale).coerceIn(0.040f, 0.420f),
        topHighlight = (base.topHighlight * safeIntensity * role.glowScale).coerceIn(0.006f, 0.125f),
        sideHighlight = (base.sideHighlight * safeIntensity * role.glowScale).coerceIn(0.002f, 0.060f),
        cornerHighlight = (base.cornerHighlight * safeIntensity * role.glowScale).coerceIn(0.006f, 0.135f),
        motionGlint = (base.motionGlint * safeIntensity * role.glowScale).coerceIn(0.002f, 0.050f),
        depthShadow = (base.depthShadow * safeIntensity * role.rimScale).coerceIn(0.004f, 0.070f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.012f, 0.130f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.002f, 0.038f)
    )
}
