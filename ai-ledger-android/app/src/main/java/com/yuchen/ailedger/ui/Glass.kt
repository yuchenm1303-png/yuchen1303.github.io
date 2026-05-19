package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0f, 1.00f, 1.00f, 14),
    Card(0f, 1.00f, 1.00f, 14),
    Chip(0f, 1.00f, 1.00f, 14),
    Nav(0f, 1.00f, 1.00f, 14),
    Floating(0f, 1.00f, 1.00f, 14)
}

private const val STRONG_GLASS_BLUR_DP = 132
private const val MEDIUM_GLASS_BLUR_DP = 96
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 1.00f
private const val UNIFIED_EDGE_STRENGTH = 0.24f

private fun blurForRole(role: GlassRole): Int = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Floating -> STRONG_GLASS_BLUR_DP
    GlassRole.Nav -> 118
    GlassRole.Chip -> MEDIUM_GLASS_BLUR_DP
}

private fun effectiveGlassRadius(radius: Int, role: GlassRole): Int {
    if (radius >= 999) return radius
    return when (role) {
        GlassRole.Shell -> radius.coerceAtLeast(38)
        GlassRole.Card -> radius.coerceAtLeast(42)
        GlassRole.Floating -> radius.coerceAtLeast(36)
        GlassRole.Nav -> radius.coerceAtLeast(999)
        GlassRole.Chip -> radius
    }
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
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalGlassBackdrop.current

    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = glassIntensity)
    ) {
        if (backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(role),
                liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * glassIntensity.coerceIn(0.70f, 1.25f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = UNIFIED_EDGE_STRENGTH
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = effectiveRadius,
                    shimmer = shimmer,
                    breathe = breathe,
                    glassIntensity = glassIntensity,
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
    val effectiveRadius = effectiveGlassRadius(radius, role)
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
    val coordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalGlassBackdrop.current
    val pressedIntensity = if (pressed) glassIntensity * 1.08f else glassIntensity

    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.25f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedIntensity)
    ) {
        if (backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(role),
                liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f)
            )
            SampledWeatherEdgeRefraction(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                strength = UNIFIED_EDGE_STRENGTH
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassSkin(
                    quality = quality,
                    radius = effectiveRadius,
                    shimmer = shimmer + if (pressed) 0.030f else 0f,
                    breathe = breathe,
                    glassIntensity = pressedIntensity,
                    includeShadow = false
                )
        )
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    return if (quality.enableMotion && motionIntensity > 0.02f) 0.22f else 0.18f
}

@Composable
private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float {
    return if (quality.enableMotion && motionIntensity > 0.02f) 0.46f else 0.42f
}

private fun Modifier.glassOuterFrame(
    radius: Int,
    glassIntensity: Float
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(glassIntensity)
    return this
        .shadow(
            elevation = 8.dp,
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
    val material = glassMaterial(glassIntensity)
    val pulse = 0.92f + breathe * 0.055f
    val safeShimmer = shimmer - shimmer.toInt()
    val base = if (includeShadow) {
        this.glassOuterFrame(radius = radius, glassIntensity = glassIntensity)
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
                Color.White.copy(alpha = material.frost * 0.34f * pulse),
                Color.White.copy(alpha = material.frost * 0.13f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.14f)
            ),
            startY = 0f,
            endY = h
        )
        val topLens = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.topHighlight * 0.56f * pulse),
                Color.White.copy(alpha = material.topHighlight * 0.07f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.24f
        )
        val lowerShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.62f)
            ),
            startY = h * 0.62f,
            endY = h
        )
        val mainRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.66f),
                Color.White.copy(alpha = material.rim * 0.060f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.18f),
                Color.White.copy(alpha = material.rim * 0.020f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topHairline = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.50f),
                Color.White.copy(alpha = material.rim * 0.040f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.20f
        )
        val innerSoftRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.030f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.13f),
                Color.White.copy(alpha = material.rim * 0.010f)
            ),
            start = Offset(w * 0.10f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val bottomShadow = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.42f)
            ),
            startY = h * 0.56f,
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
                Color.White.copy(alpha = material.cornerHighlight * 0.10f),
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
                style = Stroke(width = 0.38.dp.toPx()),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = topHairline,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 0.12.dp.toPx()),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = innerSoftRim,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 0.12.dp.toPx()),
                blendMode = BlendMode.SrcOver
            )
            drawRoundRect(
                brush = bottomShadow,
                topLeft = Offset(bottomInset, bottomInset),
                size = bottomSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 0.12.dp.toPx()),
                blendMode = BlendMode.Multiply
            )
            if (quality.enableMotion) {
                drawRoundRect(
                    brush = movingEdgeGlint,
                    topLeft = Offset(rimInset, rimInset),
                    size = rimSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 0.08.dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
            drawRoundRect(
                brush = cornerCatchlight,
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = 0.16.dp.toPx()),
                blendMode = BlendMode.Screen
            )
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

private fun glassMaterial(intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = GlassMaterial(
        frost = 0.050f,
        rim = 0.122f,
        topHighlight = 0.042f,
        cornerHighlight = 0.026f,
        motionGlint = 0.004f,
        depthShadow = 0.018f,
        shadowAmbient = 0.038f,
        shadowSpot = 0.005f
    )
    return GlassMaterial(
        frost = (base.frost * safeIntensity).coerceIn(0.006f, 0.060f),
        rim = (base.rim * safeIntensity).coerceIn(0.020f, 0.176f),
        topHighlight = (base.topHighlight * safeIntensity).coerceIn(0.002f, 0.054f),
        cornerHighlight = (base.cornerHighlight * safeIntensity).coerceIn(0.002f, 0.046f),
        motionGlint = (base.motionGlint * safeIntensity).coerceIn(0.001f, 0.010f),
        depthShadow = (base.depthShadow * safeIntensity).coerceIn(0.002f, 0.032f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.004f, 0.058f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.001f, 0.010f)
    )
}
