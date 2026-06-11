package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min

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
): Modifier {
    if (motionIntensity <= 0.02f) return this
    val rawPress = pressProgress.coerceIn(0f, 1f)
    if (rawPress <= 0.001f && rimFlowStrength <= 0.001f) return this

    val roleGain = when (role) {
        GlassRole.Shell -> 0.86f
        GlassRole.Card -> 0.74f
        GlassRole.Floating -> 0.82f
        GlassRole.Chip -> 0.96f
        GlassRole.Nav -> 0.66f
        GlassRole.Flex -> 0.86f
    }
    val qualityGain = if (quality.enableMotion) 1f else 0.72f
    val press = rawPress * roleGain * qualityGain * glassIntensity.coerceIn(0.55f, 1.35f)
    if (press <= 0.001f) return this

    return drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val minSide = min(w, h)
        val maxSide = max(w, h)
        val center = Offset(
            x = (pressCenter.x.coerceIn(0f, 1f) * w).coerceIn(0f, w),
            y = (pressCenter.y.coerceIn(0f, 1f) * h).coerceIn(0f, h)
        )
        val corner = CornerRadius(minSide * 0.34f, minSide * 0.34f)
        val lensRadius = maxSide * (0.34f + press * 0.16f)
        val sinkRadius = maxSide * (0.22f + press * 0.12f)
        val edgeStroke = (1.0f + minSide * 0.010f).coerceIn(1.0f, 6.0f)

        val pressHighlight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.160f * press),
                Color(0xFFEAF7FF).copy(alpha = 0.070f * press),
                Color.Transparent
            ),
            center = Offset(
                x = center.x - w * 0.055f,
                y = center.y - h * 0.090f
            ),
            radius = lensRadius
        )
        val pressSink = Brush.radialGradient(
            colors = listOf(
                Color(0xFF00020A).copy(alpha = 0.125f * press),
                Color(0xFF071127).copy(alpha = 0.050f * press),
                Color.Transparent
            ),
            center = Offset(
                x = center.x + w * 0.035f,
                y = center.y + h * 0.060f
            ),
            radius = sinkRadius
        )
        val localSpecular = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.075f * press),
                Color.Transparent
            ),
            start = Offset(center.x - w * 0.36f, center.y - h * 0.28f),
            end = Offset(center.x + w * 0.22f, center.y + h * 0.08f)
        )
        val rimResponse = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.050f * press),
                Color.White.copy(alpha = 0.014f * press),
                Color.Transparent,
                Color.Black.copy(alpha = 0.038f * press),
                Color.White.copy(alpha = 0.018f * press)
            ),
            start = Offset(w * (rimFlowSeed - 0.32f * rimFlowDirection), 0f),
            end = Offset(w * (rimFlowSeed + 0.72f * rimFlowDirection), h)
        )

        onDrawWithContent {
            drawContent()
            drawRect(pressSink, blendMode = BlendMode.Multiply)
            drawRect(pressHighlight, blendMode = BlendMode.Screen)
            drawRect(localSpecular, blendMode = BlendMode.Screen)
            drawRoundRect(
                brush = rimResponse,
                topLeft = Offset(edgeStroke * 0.42f, edgeStroke * 0.42f),
                size = Size(
                    width = (w - edgeStroke * 0.84f).coerceAtLeast(1f),
                    height = (h - edgeStroke * 0.84f).coerceAtLeast(1f)
                ),
                cornerRadius = corner,
                style = Stroke(edgeStroke),
                blendMode = BlendMode.Screen
            )
        }
    }
}
