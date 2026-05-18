package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.RenderQuality

data class GlassBackdropSpec(
    val quality: RenderQuality,
    val motionIntensity: Float,
    val theme: BackgroundTheme
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
    val alpha = liftAlpha.coerceIn(0.34f, 1.00f)
    val baseScrimAlpha = when (quality) {
        RenderQuality.Smooth -> 0.24f
        RenderQuality.Balanced -> 0.30f
        RenderQuality.Experimental -> 0.34f
    } * alpha
    val milkAlpha = when (quality) {
        RenderQuality.Smooth -> 0.090f
        RenderQuality.Balanced -> 0.112f
        RenderQuality.Experimental -> 0.132f
    } * alpha
    val highlightAlpha = when (quality) {
        RenderQuality.Smooth -> 0.048f
        RenderQuality.Balanced -> 0.064f
        RenderQuality.Experimental -> 0.078f
    } * alpha
    val actualBlur = when (quality) {
        RenderQuality.Smooth -> blurRadiusDp * 0.24f
        RenderQuality.Balanced -> blurRadiusDp * 0.30f
        RenderQuality.Experimental -> blurRadiusDp * 0.36f
    }.coerceIn(14f, 46f)
    val spreadPx = when (quality) {
        RenderQuality.Smooth -> blurRadiusDp * 0.46f
        RenderQuality.Balanced -> blurRadiusDp * 0.58f
        RenderQuality.Experimental -> blurRadiusDp * 0.70f
    }.coerceIn(30f, 92f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .blur(actualBlur.dp)
    ) {
        val rootW = if (view.width > 0) view.width.toFloat() else size.width + globalOffset.x
        val rootH = if (view.height > 0) view.height.toFloat() else size.height + globalOffset.y

        drawSpreadBackdropSamples(
            rootW = rootW,
            rootH = rootH,
            theme = theme,
            globalOffset = globalOffset,
            spreadPx = spreadPx
        )
        drawRect(
            color = Color(0xFF5F748B).copy(alpha = baseScrimAlpha),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milkAlpha * 1.18f),
                    Color(0xFFD9E3EF).copy(alpha = milkAlpha * 0.76f),
                    Color(0xFF8EA1B7).copy(alpha = milkAlpha * 0.34f),
                    Color(0xFF172333).copy(alpha = baseScrimAlpha * 0.46f)
                )
            ),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha * 0.86f),
                    Color.White.copy(alpha = highlightAlpha * 0.18f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.42f, size.height * 0.08f),
                radius = size.width * 0.98f
            ),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7FB6FF).copy(alpha = highlightAlpha * 0.18f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.12f, size.height * 0.82f),
                radius = size.width * 0.84f
            ),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawSpreadBackdropSamples(
    rootW: Float,
    rootH: Float,
    theme: BackgroundTheme,
    globalOffset: Offset,
    spreadPx: Float
) {
    val samples = listOf(
        Offset(0f, 0f) to 0.22f,
        Offset(-0.55f, 0f) to 0.095f,
        Offset(0.55f, 0f) to 0.095f,
        Offset(0f, -0.55f) to 0.095f,
        Offset(0f, 0.55f) to 0.095f,
        Offset(-0.78f, -0.78f) to 0.066f,
        Offset(0.78f, -0.78f) to 0.066f,
        Offset(-0.78f, 0.78f) to 0.066f,
        Offset(0.78f, 0.78f) to 0.066f,
        Offset(-1.18f, 0.36f) to 0.046f,
        Offset(1.18f, -0.36f) to 0.046f,
        Offset(-0.36f, -1.18f) to 0.046f,
        Offset(0.36f, 1.18f) to 0.046f
    )

    samples.forEach { (unitOffset, sampleAlpha) ->
        withTransform({
            translate(
                left = -globalOffset.x + unitOffset.x * spreadPx,
                top = -globalOffset.y + unitOffset.y * spreadPx
            )
        }) {
            drawWeatherNightBackground(
                w = rootW,
                h = rootH,
                theme = theme,
                alphaScale = sampleAlpha
            )
        }
    }
    withTransform({ translate(left = -globalOffset.x, top = -globalOffset.y) }) {
        drawWeatherNightBackgroundGlow(
            w = rootW,
            h = rootH,
            theme = theme,
            alphaScale = 0.44f
        )
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
    val alpha = strength.coerceIn(0f, 0.34f)
    Canvas(modifier = modifier.clip(RoundedCornerShape(radius.dp))) {
        val w = size.width
        val h = size.height
        val corner = radius.dp.toPx()
        val outerInset = 0.75.dp.toPx()
        val midInset = 3.6.dp.toPx()
        val innerInset = 8.5.dp.toPx()
        val outerSize = Size(w - outerInset * 2f, h - outerInset * 2f)
        val midSize = Size(w - midInset * 2f, h - midInset * 2f)
        val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
        val cornerRadius = CornerRadius(corner, corner)

        val broadLens = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.072f * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent,
                Color.Black.copy(alpha = 0.012f * alpha),
                Color.White.copy(alpha = 0.012f * alpha)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topPrism = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.082f * alpha),
                Color.White.copy(alpha = 0.020f * alpha),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.26f
        )
        val sideCompression = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.032f * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.012f * alpha)
            )
        )
        val innerDarkBend = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.004f * alpha),
                Color.Black.copy(alpha = 0.020f * alpha)
            ),
            startY = h * 0.45f,
            endY = h
        )

        drawRoundRect(
            brush = broadLens,
            topLeft = Offset(outerInset, outerInset),
            size = outerSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 7.5.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = topPrism,
            topLeft = Offset(midInset, midInset),
            size = midSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 5.2.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = sideCompression,
            topLeft = Offset(midInset, midInset),
            size = midSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 4.6.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = innerDarkBend,
            topLeft = Offset(innerInset, innerInset),
            size = innerSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 2.6.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}
