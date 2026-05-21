package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GlassBackdropSpec(
    val quality: RenderQuality,
    val motionIntensity: Float,
    val theme: BackgroundTheme,
    val params: BackdropDebugParams = BackdropDebugParams(),
    val borderStyle: GlassBorderStyle = GlassBorderStyle()
)

val LocalGlassBackdrop = compositionLocalOf<GlassBackdropSpec?> { null }

@Composable
fun SampledWeatherGlassBackdrop(
    modifier: Modifier = Modifier,
    radius: Int,
    coordinateSource: GlassCoordinateSource,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    blurRadiusDp: Int = 112,
    liftAlpha: Float = 1f
) {
    val view = LocalView.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val spec = LocalGlassBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val params = spec?.params ?: BackdropDebugParams()
    val alpha = liftAlpha.coerceIn(0.34f, 1.00f)
    val baseScrimAlpha = when (quality) {
        RenderQuality.Smooth -> 0.15f
        RenderQuality.Balanced -> 0.18f
        RenderQuality.Experimental -> 0.21f
    } * alpha
    val milkAlpha = when (quality) {
        RenderQuality.Smooth -> 0.040f
        RenderQuality.Balanced -> 0.052f
        RenderQuality.Experimental -> 0.064f
    } * alpha
    val highlightAlpha = when (quality) {
        RenderQuality.Smooth -> 0.036f
        RenderQuality.Balanced -> 0.046f
        RenderQuality.Experimental -> 0.056f
    } * alpha
    val backdropAlpha = when (quality) {
        RenderQuality.Smooth -> 0.90f
        RenderQuality.Balanced -> 0.94f
        RenderQuality.Experimental -> 0.98f
    }
    val spreadPx = when (quality) {
        RenderQuality.Smooth -> blurRadiusDp * 0.46f
        RenderQuality.Balanced -> blurRadiusDp * 0.62f
        RenderQuality.Experimental -> blurRadiusDp * 0.76f
    }.coerceIn(34f, 96f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
    ) {
        frameTicker?.frameNanos
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        if (cachedBackdrop != null) {
            val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
            val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
            val srcW = (size.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
                .coerceAtMost(cachedBackdrop.image.width - srcX)
            val srcH = (size.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1)
                .coerceAtMost(cachedBackdrop.image.height - srcY)
            drawImage(
                image = cachedBackdrop.image,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = backdropAlpha,
                blendMode = BlendMode.SrcOver
            )
        } else {
            val rootW = if (view.width > 0) view.width.toFloat() else size.width + sampleOffset.x
            val rootH = if (view.height > 0) view.height.toFloat() else size.height + sampleOffset.y
            drawSpreadBackdropSamples(rootW, rootH, theme, params, sampleOffset, spreadPx)
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE0EAF3).copy(alpha = milkAlpha * 0.48f),
                    Color(0xFF9AADBF).copy(alpha = baseScrimAlpha * 0.28f),
                    Color(0xFF40576D).copy(alpha = baseScrimAlpha * 0.30f)
                )
            ),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            color = Color(0xFF72859A).copy(alpha = baseScrimAlpha * 0.26f),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milkAlpha * 0.46f),
                    Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.22f),
                    Color(0xFF9BAEC1).copy(alpha = milkAlpha * 0.10f),
                    Color(0xFF172333).copy(alpha = baseScrimAlpha * 0.14f)
                )
            ),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha * 0.42f),
                    Color.White.copy(alpha = highlightAlpha * 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.42f, size.height * 0.08f),
                radius = size.width * 0.98f
            ),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawSpreadBackdropSamples(
    rootW: Float,
    rootH: Float,
    theme: BackgroundTheme,
    params: BackdropDebugParams,
    sampleOffset: Offset,
    spreadPx: Float
) {
    val samples = listOf(
        Offset(0f, 0f) to 0.090f,
        Offset(-0.55f, 0f) to 0.070f,
        Offset(0.55f, 0f) to 0.070f,
        Offset(0f, -0.55f) to 0.070f,
        Offset(0f, 0.55f) to 0.070f,
        Offset(-1.05f, -0.72f) to 0.055f,
        Offset(1.05f, 0.72f) to 0.055f,
        Offset(-0.72f, 1.05f) to 0.048f,
        Offset(0.72f, -1.05f) to 0.048f
    )
    samples.forEach { (unitOffset, sampleAlpha) ->
        withTransform({ translate(left = -sampleOffset.x + unitOffset.x * spreadPx, top = -sampleOffset.y + unitOffset.y * spreadPx) }) {
            drawWeatherNightBackground(rootW, rootH, theme, sampleAlpha, params)
        }
    }
    withTransform({ translate(left = -sampleOffset.x, top = -sampleOffset.y) }) {
        drawWeatherNightBackgroundGlow(rootW, rootH, theme, 0.82f, params)
    }
}

@Composable
fun SampledWeatherEdgeRefraction(
    modifier: Modifier = Modifier,
    radius: Int,
    coordinateSource: GlassCoordinateSource,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    strength: Float = 1f
) {
    val view = LocalView.current
    val spec = LocalGlassBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val border = spec?.borderStyle ?: GlassBorderStyle()
    val alpha = strength.coerceIn(0f, 0.34f)
    Canvas(modifier = modifier.clip(RoundedCornerShape(radius.dp))) {
        frameTicker?.frameNanos
        val sampleOffset = coordinateSource.offsetRelativeTo(backdropOrigin)
        val w = size.width
        val h = size.height
        val corner = radius.dp.toPx()
        val outerInset = 0.55.dp.toPx()
        val midInset = 2.70.dp.toPx()
        val innerInset = 7.0.dp.toPx()
        val cornerRadius = CornerRadius(corner, corner)

        val rootW = if (view.width > 0) view.width.toFloat() else max(w + sampleOffset.x, w)
        val rootH = if (view.height > 0) view.height.toFloat() else max(h + sampleOffset.y, h)
        val dominantLight = dominantBackdropLight(theme, rootW, rootH)
        val localLight = Offset(dominantLight.x - sampleOffset.x, dominantLight.y - sampleOffset.y)
        val nearestX = localLight.x.coerceIn(0f, w)
        val nearestY = localLight.y.coerceIn(0f, h)
        val dx = localLight.x - nearestX
        val dy = localLight.y - nearestY
        val lightDistance = sqrt(dx * dx + dy * dy)
        val edgeInfluence = 1f - smoothStep(0f, max(w, h) * 0.62f, lightDistance)
        val verticalAffinity = 1f - smoothStep(0f, h * 1.15f, abs(localLight.y - h * 0.5f))
        val horizontalAffinity = 1f - smoothStep(0f, w * 1.05f, abs(localLight.x - w * 0.5f))
        val lightBoost = (edgeInfluence * (0.48f + 0.32f * verticalAffinity + 0.20f * horizontalAffinity)).coerceIn(0f, 1f)
        val lightPhase = (localLight.x / max(w, 1f)).coerceIn(-0.35f, 1.35f)

        val broadLens = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.032f + 0.035f * lightBoost) * alpha),
                Color.White.copy(alpha = (0.012f + 0.018f * lightBoost) * alpha),
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = (0.006f + 0.014f * lightBoost) * alpha)
            ),
            start = Offset(w * (lightPhase - 0.42f), 0f),
            end = Offset(w * (lightPhase + 0.64f), h)
        )
        val topPrism = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.040f + 0.040f * lightBoost) * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.30f
        )
        val sideCompression = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.016f + 0.020f * lightBoost) * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = (0.010f + 0.022f * lightBoost) * alpha)
            )
        )
        val edgeLight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = border.topHighlightAlpha * 0.22f * lightBoost),
                Color.White.copy(alpha = border.topHighlightAlpha * 0.050f * lightBoost),
                Color.Transparent
            ),
            center = localLight,
            radius = max(w, h) * 0.58f
        )
        val innerDarkBend = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.004f * alpha),
                Color.Black.copy(alpha = 0.018f * alpha)
            ),
            startY = h * 0.48f,
            endY = h
        )

        drawRoundRect(
            brush = broadLens,
            topLeft = Offset(outerInset, outerInset),
            size = Size(w - outerInset * 2f, h - outerInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 8.5.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = topPrism,
            topLeft = Offset(midInset, midInset),
            size = Size(w - midInset * 2f, h - midInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 5.6.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = sideCompression,
            topLeft = Offset(midInset, midInset),
            size = Size(w - midInset * 2f, h - midInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 4.8.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = innerDarkBend,
            topLeft = Offset(innerInset, innerInset),
            size = Size(w - innerInset * 2f, h - innerInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 2.4.dp.toPx()),
            blendMode = BlendMode.Multiply
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.outerStrokeAlpha),
                    Color.White.copy(alpha = border.outerStrokeAlpha * 0.34f),
                    Color.White.copy(alpha = border.outerStrokeAlpha * 0.12f)
                ),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset(outerInset, outerInset),
            size = Size(w - outerInset * 2f, h - outerInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.15.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = border.innerStrokeAlpha), Color.Transparent, Color.White.copy(alpha = border.innerStrokeAlpha * 0.28f)),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset(midInset, midInset),
            size = Size(w - midInset * 2f, h - midInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 0.82.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = edgeLight,
            topLeft = Offset(outerInset, outerInset),
            size = Size(w - outerInset * 2f, h - outerInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.20.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = border.bottomShadowAlpha)),
                startY = h * 0.52f,
                endY = h
            ),
            topLeft = Offset(midInset, midInset),
            size = Size(w - midInset * 2f, h - midInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.1.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val span = (edge1 - edge0).takeIf { abs(it) > 0.0001f } ?: return 0f
    val t = ((value - edge0) / span).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun dominantBackdropLight(theme: BackgroundTheme, rootW: Float, rootH: Float): Offset {
    val x = when (theme) {
        BackgroundTheme.Aurora -> 0.80f
        BackgroundTheme.Jade -> 0.76f
        BackgroundTheme.Sunset -> 0.82f
        BackgroundTheme.Dawn -> 0.78f
    }
    val y = when (theme) {
        BackgroundTheme.Aurora -> 0.30f
        BackgroundTheme.Jade -> 0.34f
        BackgroundTheme.Sunset -> 0.31f
        BackgroundTheme.Dawn -> 0.35f
    }
    return Offset(rootW * x, rootH * y)
}
