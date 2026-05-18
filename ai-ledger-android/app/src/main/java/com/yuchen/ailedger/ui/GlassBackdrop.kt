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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val alpha = liftAlpha.coerceIn(0.04f, 0.38f)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .blur(blurRadiusDp.dp)
    ) {
        // Keep the glass body optically transparent. Do not draw the old fake launcher
        // background here, otherwise colored blocks are baked into every glass card.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.012f * alpha),
                    Color.White.copy(alpha = 0.006f * alpha),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.010f * alpha)
                )
            ),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.010f * alpha),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.58f, size.height * 0.16f),
                radius = size.width * 0.76f
            ),
            blendMode = BlendMode.Screen
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
    val alpha = strength.coerceIn(0f, 0.32f)
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
                Color.White.copy(alpha = 0.060f * alpha),
                Color.White.copy(alpha = 0.014f * alpha),
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.010f * alpha)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topPrism = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.070f * alpha),
                Color.White.copy(alpha = 0.016f * alpha),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.26f
        )
        val sideCompression = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.026f * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.008f * alpha),
                Color.White.copy(alpha = 0.010f * alpha)
            )
        )
        val innerDarkBend = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.004f * alpha),
                Color.Black.copy(alpha = 0.018f * alpha)
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
