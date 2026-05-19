package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import kotlin.math.roundToInt

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
    globalOffset: Offset,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    blurRadiusDp: Int = 112,
    liftAlpha: Float = 1f
) {
    val view = LocalView.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val spec = LocalGlassBackdrop.current
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
    val fallbackBlur = when (quality) {
        RenderQuality.Smooth -> blurRadiusDp * 0.24f
        RenderQuality.Balanced -> blurRadiusDp * 0.32f
        RenderQuality.Experimental -> blurRadiusDp * 0.40f
    }.coerceIn(14f, 46f)
    val spreadPx = when (quality) {
        RenderQuality.Smooth -> blurRadiusDp * 0.46f
        RenderQuality.Balanced -> blurRadiusDp * 0.62f
        RenderQuality.Experimental -> blurRadiusDp * 0.76f
    }.coerceIn(34f, 96f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .then(if (cachedBackdrop == null) Modifier.blur(fallbackBlur.dp) else Modifier)
    ) {
        if (cachedBackdrop != null) {
            val srcX = (globalOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
            val srcY = (globalOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
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
            val rootW = if (view.width > 0) view.width.toFloat() else size.width + globalOffset.x
            val rootH = if (view.height > 0) view.height.toFloat() else size.height + globalOffset.y
            drawSpreadBackdropSamples(rootW, rootH, theme, params, globalOffset, spreadPx)
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
    globalOffset: Offset,
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
        withTransform({ translate(left = -globalOffset.x + unitOffset.x * spreadPx, top = -globalOffset.y + unitOffset.y * spreadPx) }) {
            drawWeatherNightBackground(rootW, rootH, theme, sampleAlpha, params)
        }
    }
    withTransform({ translate(left = -globalOffset.x, top = -globalOffset.y) }) {
        drawWeatherNightBackgroundGlow(rootW, rootH, theme, 0.82f, params)
    }
}

@Composable
fun SampledWeatherEdgeRefraction(
    modifier: Modifier = Modifier,
    radius: Int,
    globalOffset: Offset,
    quality: RenderQuality,
    motionIntensity: Float,
    theme: BackgroundTheme,
    strength: Float = 1f
) {
    val spec = LocalGlassBackdrop.current
    val border = spec?.borderStyle ?: GlassBorderStyle()
    val transition = rememberInfiniteTransition(label = "ios-edge-flow")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((6200 / motionIntensity.coerceAtLeast(0.35f)).roundToInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ios-edge-flow-value"
    )
    val alpha = strength.coerceIn(0f, 0.34f)
    Canvas(modifier = modifier.clip(RoundedCornerShape(radius.dp))) {
        val w = size.width
        val h = size.height
        val corner = radius.dp.toPx()
        val outerInset = 0.55.dp.toPx()
        val midInset = 2.70.dp.toPx()
        val innerInset = 7.0.dp.toPx()
        val cornerRadius = CornerRadius(corner, corner)
        val positionPhase = ((globalOffset.x + globalOffset.y) / 900f + flow) % 1f

        val broadLens = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.055f * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.010f * alpha)
            ),
            start = Offset(w * (positionPhase - 0.18f), 0f),
            end = Offset(w * (positionPhase + 0.82f), h)
        )
        val topPrism = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.070f * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.30f
        )
        val sideCompression = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.030f * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.016f * alpha)
            )
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

        val movingGlint = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = border.topHighlightAlpha * 0.55f), Color.Transparent),
            start = Offset(w * (positionPhase - 0.32f), 0f),
            end = Offset(w * (positionPhase + 0.18f), h * 0.18f)
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
            brush = movingGlint,
            topLeft = Offset(outerInset, outerInset),
            size = Size(w - outerInset * 2f, h - outerInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.0.dp.toPx()),
            blendMode = BlendMode.Plus
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
