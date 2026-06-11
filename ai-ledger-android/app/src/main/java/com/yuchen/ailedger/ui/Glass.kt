package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.NewOpenGLGlassCardLayer
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import kotlin.math.roundToInt

enum class GlassRole {
    Shell,
    Card,
    Chip,
    Nav,
    Floating,
    Flex
}

val LocalOpenGLGlassViewportActive = compositionLocalOf { false }

private const val SHELL_GLASS_BLUR_DP = 118
private const val CARD_GLASS_BLUR_DP = 76
private const val CHIP_GLASS_BLUR_DP = 58
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 0.92f
private const val USE_CARD_BOUND_OPENGL_GLASS = true

private fun blurForRole(role: GlassRole): Int {
    val base = when (role) {
        GlassRole.Shell -> SHELL_GLASS_BLUR_DP
        GlassRole.Card, GlassRole.Floating, GlassRole.Nav -> CARD_GLASS_BLUR_DP
        GlassRole.Chip, GlassRole.Flex -> CHIP_GLASS_BLUR_DP
    }
    if (role == GlassRole.Shell) return base
    return (base * ComposeGlassLabState.style.blurScale).roundToInt().coerceIn(32, 128)
}

private fun effectiveGlassRadius(radius: Int, role: GlassRole): Int {
    if (radius >= 999) return radius
    val base = when (role) {
        GlassRole.Shell -> radius.coerceAtLeast(30)
        GlassRole.Card -> radius.coerceAtLeast(28)
        GlassRole.Floating -> radius.coerceAtLeast(26)
        GlassRole.Nav -> radius.coerceAtLeast(999)
        GlassRole.Chip, GlassRole.Flex -> radius
    }
    if (role == GlassRole.Shell) return base
    return (base * ComposeGlassLabState.style.radiusScale).roundToInt().coerceAtLeast(8)
}

private fun ordinaryBackdropAlpha(role: GlassRole, intensity: Float): Float {
    val roleScale = if (role == GlassRole.Shell) UNIFIED_GLASS_BACKDROP_ALPHA else ComposeGlassLabState.style.backdropAlpha
    return roleScale * intensity.coerceIn(0.70f, 1.25f)
}

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
    viewportTopInset: Dp = 0.dp,
    intensity: Float? = null,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val baseIntensity = intensity ?: glassIntensity
    val coordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val safeViewportTopInset = if (role == GlassRole.Shell && viewportTopInset > 0.dp) viewportTopInset else 0.dp
    val density = LocalDensity.current
    val safeViewportTopInsetPx = with(density) { safeViewportTopInset.toPx().toInt().toFloat() }
    val useNewOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && role == GlassRole.Shell && !viewportOwnsShell && cardBackdrop != null

    Box(modifier = modifier.onPlaced { coordinates.coordinates = it }) {
        if (useNewOpenGlBackdrop) {
            NewOpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = baseIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize(),
                viewportTopInsetPx = safeViewportTopInsetPx
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = safeViewportTopInset)
                .glassOuterFrame(radius = effectiveRadius, glassIntensity = baseIntensity, role = role)
        ) {
            if (!useNewOpenGlBackdrop && !viewportOwnsShell && backdrop != null) {
                SampledWeatherGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    radius = effectiveRadius,
                    coordinateSource = coordinates,
                    quality = backdrop.quality,
                    motionIntensity = backdrop.motionIntensity,
                    theme = backdrop.theme,
                    blurRadiusDp = blurForRole(role),
                    liftAlpha = ordinaryBackdropAlpha(role, baseIntensity)
                )
            }
            if (!useNewOpenGlBackdrop) {
                Box(Modifier.matchParentSize().glassSkin(quality, effectiveRadius, glassIntensity = baseIntensity, role = role, includeShadow = false))
            }
            content()
        }
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
    intensity: Float? = null,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val baseIntensity = intensity ?: glassIntensity
    val coordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val useNewOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && role == GlassRole.Shell && !viewportOwnsShell && cardBackdrop != null
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = baseIntensity, role = role)
    ) {
        if (useNewOpenGlBackdrop) {
            NewOpenGLGlassCardLayer(radius = effectiveRadius, glassIntensity = baseIntensity, coordinateSource = coordinates, modifier = Modifier.matchParentSize())
        } else if (!viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(Modifier.matchParentSize(), effectiveRadius, coordinates, backdrop.quality, backdrop.motionIntensity, backdrop.theme, blurForRole(role), ordinaryBackdropAlpha(role, baseIntensity))
        }
        if (!useNewOpenGlBackdrop) {
            Box(Modifier.matchParentSize().glassSkin(quality, effectiveRadius, glassIntensity = baseIntensity, role = role, includeShadow = false))
        }
        content()
    }
}

@Composable
fun LegacyOpenGLGlassPreviewShell(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, GlassRole.Shell)
    val coordinates = remember { GlassCoordinateSource() }
    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = glassIntensity, role = GlassRole.Shell)
    ) {
        OpenGLGlassCardLayer(radius = effectiveRadius, glassIntensity = glassIntensity, coordinateSource = coordinates, modifier = Modifier.matchParentSize())
        content()
    }
}

private fun Modifier.glassOuterFrame(radius: Int, glassIntensity: Float, role: GlassRole = GlassRole.Card): Modifier {
    val alpha = (0.18f + glassIntensity.coerceIn(0f, 1.4f) * 0.10f) * ComposeGlassLabState.style.shadowAlpha
    val elevation = when (role) {
        GlassRole.Shell -> 18.dp
        GlassRole.Card, GlassRole.Floating -> 12.dp
        GlassRole.Nav -> 10.dp
        GlassRole.Chip, GlassRole.Flex -> 8.dp
    }
    return shadow(elevation = elevation, shape = RoundedCornerShape(radius.dp), ambientColor = Color.Black.copy(alpha = alpha * 0.35f), spotColor = Color.Black.copy(alpha = alpha))
        .clip(RoundedCornerShape(radius.dp))
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
    val base = if (includeShadow) this.glassOuterFrame(radius, glassIntensity, role) else this.clip(shape)
    return base.drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val style = ComposeGlassLabState.style
        val intensityScale = glassIntensity.coerceIn(0.25f, 1.45f)
        val topLight = style.topLight * intensityScale
        val bottomLight = style.bottomLight * intensityScale
        val outerRim = style.outerRim * intensityScale
        val bottomMass = style.bottomMass * intensityScale
        val quiet = style.quiet
        val baseField = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.026f * ComposeGlassRuntimeDefaults.frost * intensityScale),
                Color.White.copy(alpha = 0.0f),
                Color(0xFF000000).copy(alpha = 0.080f * quiet)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val edgeBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.120f * topLight),
                Color.White.copy(alpha = 0.018f * outerRim),
                Color.Transparent,
                Color(0xFF00020A).copy(alpha = 0.140f * bottomMass),
                Color.White.copy(alpha = 0.080f * bottomLight)
            ),
            startY = 0f,
            endY = h
        )
        val rimInset = 0.75.dp.toPx()
        onDrawWithContent {
            drawRect(baseField)
            drawContent()
            drawRoundRect(
                brush = edgeBrush,
                topLeft = Offset(rimInset, rimInset),
                size = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f)),
                cornerRadius = cornerRadius,
                style = Stroke((0.34.dp.toPx() + 0.30.dp.toPx() * outerRim).coerceAtLeast(0.34.dp.toPx())),
                blendMode = BlendMode.Screen
            )
        }
    }
}
