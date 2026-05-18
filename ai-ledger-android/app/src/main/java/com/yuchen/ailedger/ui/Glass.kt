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
    Shell(0f, 1.10f, 0.92f, 15),
    Card(0f, 1.00f, 0.84f, 12),
    Chip(0f, 0.62f, 0.50f, 4),
    Nav(0f, 1.18f, 0.96f, 16),
    Floating(0f, 1.16f, 0.98f, 17)
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
                    GlassRole.Shell -> 0.46f
                    GlassRole.Card -> 0.38f
                    GlassRole.Nav -> 0.34f
                    GlassRole.Floating -> 0.42f
                    GlassRole.Chip -> 0f
                } * glassIntensity.coerceIn(0.65f, 1.25f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = when (role) {
                    GlassRole.Shell -> 1.00f
                    GlassRole.Card -> 0.90f
                    GlassRole.Nav -> 1.06f
                    GlassRole.Floating -> 0.96f
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
                liftAlpha = (if (pressed) 0.48f else 0.38f) * pressedIntensity.coerceIn(0.65f, 1.25f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = radius,
                globalOffset = globalOffset,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = if (pressed) 1.04f else 0.88f
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
        val rimInset = 0.72.dp.toPx()
        val innerInset = 2.40.dp.toPx()
        val bottomInset = 2.80.dp.toPx()
        val rimSize = Size(w - rimInset * 2f, h - rimInset * 2f)
        val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
        val bottomSize = Size(w - bottomInset * 2f, h - bottomInset * 2f)

        val frostedNeutralVeil = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.frost * pulse),
                Color(0xFFE8EDF3).copy(alpha = material.frost * 0.62f),
                Color(0xFFC7D0DC).copy(alpha = material.frost * 0.20f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h
        )
        val topLens = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.topHighlight * pulse),
                Color.White.copy(alpha = material.topHighlight * 0.20f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.26f
        )
        val lowerShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow)
            ),
            startY = h * 0.60f,
            endY = h
        )
        val mainRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.72f),
                Color.White.copy(alpha = material.rim * 0.10f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.36f),
                Color.White.copy(alpha = material.rim * 0.040f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topHairline = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.82f),
                Color.White.copy(alpha = material.rim * 0.10f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.22f
        )
        val innerSoftRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.075f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.30f),
                Color.White.copy(alpha = material.rim * 0.018f)
            ),
            start = Offset(w * 0.10f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val bottomShadow = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.86f)
            ),
            startY = h * 0.55f,
            endY = h
        )
        val movingEdgeGlint = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = material.motionGlint),
                Color.Transparent
            ),
            start = Offset(w * (safeShimmer - 0.28f), 0f),
            end = Offset(w * (safeShimmer + 0.16f), h * 0.20f)
        )
        val cornerCatchlight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = material.cornerHighlight),
                Color.White.copy(alpha = material.cornerHighlight * 0.18f),
                Color.Transparent
            ),
            center = Offset(w * (0.035f + drift * 0.010f), h * 0.020f),
            radius = w * 0.30f
        )

        onDrawWithContent {
            drawRect(frostedNeutralVeil, blendMode = BlendMode.Screen)
            drawRect(topLens, blendMode = BlendMode.Screen)
            drawRect(lowerShade, blendMode = BlendMode.Multiply)
            drawContent()

            drawRoundRect(
                brush = mainRim,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = if (role == GlassRole.Chip) 0.28.dp.toPx() else 0.48.dp.toPx()),
                blendMode = BlendMode.Screen
            )
            if (role != GlassRole.Chip) {
                drawRoundRect(
                    brush = topHairline,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.16.dp.toPx()),
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = innerSoftRim,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.18.dp.toPx()),
                    blendMode = BlendMode.SrcOver
                )
                drawRoundRect(
                    brush = bottomShadow,
                    topLeft = Offset(bottomInset, bottomInset),
                    size = bottomSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.16.dp.toPx()),
                    blendMode = BlendMode.Multiply
                )
                if (quality.enableMotion) {
                    drawRoundRect(
                        brush = movingEdgeGlint,
                        topLeft = Offset(rimInset, rimInset),
                        size = rimSize,
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 0.12.dp.toPx()),
                        blendMode = BlendMode.Plus
                    )
                }
                drawRoundRect(
                    brush = cornerCatchlight,
                    topLeft = Offset(rimInset, rimInset),
                    size = rimSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.24.dp.toPx()),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}

private data class GlassMaterial(
    val frost: Float,
    val rim: Float,
    val topHighlight: Float,
    val cornerHighlight: Float,
    val motionGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(role: GlassRole, intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = when (role) {
        GlassRole.Shell -> GlassMaterial(0.068f, 0.175f, 0.064f, 0.070f, 0.018f, 0.032f, 0.090f, 0.024f)
        GlassRole.Card -> GlassMaterial(0.052f, 0.150f, 0.052f, 0.058f, 0.014f, 0.028f, 0.070f, 0.018f)
        GlassRole.Chip -> GlassMaterial(0.024f, 0.075f, 0.024f, 0.022f, 0.006f, 0.012f, 0.030f, 0.006f)
        GlassRole.Nav -> GlassMaterial(0.060f, 0.185f, 0.062f, 0.068f, 0.016f, 0.030f, 0.080f, 0.022f)
        GlassRole.Floating -> GlassMaterial(0.062f, 0.180f, 0.060f, 0.066f, 0.016f, 0.030f, 0.076f, 0.020f)
    }
    return GlassMaterial(
        frost = (base.frost * safeIntensity).coerceIn(0.010f, 0.110f),
        rim = (base.rim * safeIntensity * role.rimScale).coerceIn(0.025f, 0.290f),
        topHighlight = (base.topHighlight * safeIntensity * role.glowScale).coerceIn(0.004f, 0.100f),
        cornerHighlight = (base.cornerHighlight * safeIntensity * role.glowScale).coerceIn(0.004f, 0.110f),
        motionGlint = (base.motionGlint * safeIntensity * role.glowScale).coerceIn(0.002f, 0.035f),
        depthShadow = (base.depthShadow * safeIntensity * role.rimScale).coerceIn(0.003f, 0.058f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.010f, 0.110f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.002f, 0.032f)
    )
}
