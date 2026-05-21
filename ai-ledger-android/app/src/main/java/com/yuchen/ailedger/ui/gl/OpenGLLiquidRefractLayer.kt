package com.yuchen.ailedger.ui.gl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.yuchen.ailedger.ui.GlassCoordinateSource

/**
 * Experimental common entry for liquid-glass refraction.
 *
 * The long-term goal is to make shape, thickness field, normal field and
 * lighting independent from each other:
 *
 * Shape profile  ->  thickness / normal  ->  refracted background sampling
 *                ->  edge reflection / inner shadow / active glow
 *
 * v0 deliberately reuses the existing OpenGL droplet core so the already
 * working magnification/distortion path is preserved while the API is cleaned
 * up for later shader replacement.
 */
enum class LiquidRefractShape {
    Circle,
    Capsule,
    RoundedRect
}

@Immutable
data class LiquidRefractStyle(
    val bodyMagnifyPx: Float = 44f,
    val edgeRefractPx: Float = 46f,
    val edgeWidthPx: Float = 9f,
    val clearMix: Float = 0.92f,
    val edgeColorDrag: Float = 0.36f,
    val bottomCaustic: Float = 0.32f,
    val topHighlight: Float = 0.22f,
    val capHighlight: Float = 0.30f,
    val innerShadow: Float = 0.18f,
    val alpha: Float = 0.72f,
    val activeGlow: Float = 0.62f
)

@Composable
fun OpenGLLiquidRefractLayer(
    shape: LiquidRefractShape,
    coordinateSource: GlassCoordinateSource? = null,
    style: LiquidRefractStyle = LiquidRefractStyle(),
    modifier: Modifier = Modifier
) {
    // v0 adapter: keep the current proven OpenGL texture upload and refraction
    // path, but expose a shape-first API. The shader will be moved into this
    // layer in the next passes.
    val radius = when (shape) {
        LiquidRefractShape.Circle -> 999
        LiquidRefractShape.Capsule -> 999
        LiquidRefractShape.RoundedRect -> 18
    }
    OpenGLDropletGlassLayer(
        radius = radius,
        coordinateSource = coordinateSource,
        style = DropletGlassStyle(
            bodyBulgePx = style.bodyMagnifyPx,
            edgePullPx = style.edgeRefractPx,
            edgeWidthPx = style.edgeWidthPx,
            lensMix = style.clearMix,
            dragStrength = style.edgeColorDrag,
            bottomGlow = style.bottomCaustic,
            topGloss = style.topHighlight,
            cornerGloss = style.capHighlight,
            innerDark = style.innerShadow,
            alpha = style.alpha
        ),
        modifier = modifier
    )
}

fun LiquidRefractStyle.withActiveGlowStrength(value: Float): LiquidRefractStyle = copy(activeGlow = value)
