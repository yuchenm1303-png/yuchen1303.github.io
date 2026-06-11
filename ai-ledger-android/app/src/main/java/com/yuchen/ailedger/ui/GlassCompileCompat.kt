package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.yuchen.ailedger.model.RenderQuality

val RenderQuality.Companion.Low: RenderQuality
    get() = RenderQuality.Smooth

@Composable
fun SampledWeatherGlassBackdrop(
    modifier: Modifier = Modifier,
    radius: Int,
    coordinateSource: GlassCoordinateSource,
    backdrop: GlassBackdropSpec,
    blurRadiusDp: Int = 112,
    alpha: Float = 1f,
    includeEdgeVignette: Boolean = false,
    edgeStrength: Float = 0f
) {
    SampledWeatherGlassBackdrop(
        modifier = modifier,
        radius = radius,
        coordinateSource = coordinateSource,
        quality = backdrop.quality,
        motionIntensity = backdrop.motionIntensity,
        theme = backdrop.theme,
        blurRadiusDp = blurRadiusDp,
        liftAlpha = alpha
    )
}

fun Modifier.ordinaryPressSurfaceOptics(
    role: GlassRole,
    quality: RenderQuality,
    motionIntensity: Float,
    glassIntensity: Float,
    pressProgress: Float,
    pressCenter: Offset,
    rimFlowSeed: Float = 0.50f,
    rimFlowDirection: Float = 1f,
    rimFlowBand: Int = 0,
    rimFlowStrength: Float = 1f
): Modifier = this
