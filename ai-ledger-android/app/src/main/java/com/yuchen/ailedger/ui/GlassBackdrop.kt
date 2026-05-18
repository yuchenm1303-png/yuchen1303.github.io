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
import kotlin.math.min

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
    val alpha = liftAlpha.coerceIn(0.60f, 1.35f)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .blur(blurRadiusDp.dp)
    ) {
        val rootW = if (view.width > 0) view.width.toFloat() else size.width
        val rootH = if (view.height > 0) view.height.toFloat() else size.height
        withTransform({ translate(left = -globalOffset.x, top = -globalOffset.y) }) {
            drawLauncherLikeBackground(rootW, rootH, alpha * 0.68f)
        }
        drawRect(
            color = Color(0xFFE6EEF6).copy(alpha = 0.180f * alpha),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.330f * alpha),
                    Color(0xFFEAF1F8).copy(alpha = 0.235f * alpha),
                    Color(0xFFD8E2EC).copy(alpha = 0.145f * alpha),
                    Color(0xFFBFCAD6).copy(alpha = 0.070f * alpha)
                )
            ),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.120f * alpha),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.62f, size.height * 0.18f),
                radius = size.width * 0.80f
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
    val alpha = strength.coerceIn(0f, 0.55f)
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
                Color.White.copy(alpha = 0.080f * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent,
                Color.Black.copy(alpha = 0.012f * alpha),
                Color.White.copy(alpha = 0.014f * alpha)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topPrism = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.105f * alpha),
                Color(0xFFDCEEFF).copy(alpha = 0.026f * alpha),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.26f
        )
        val sideCompression = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.035f * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.014f * alpha)
            )
        )
        val innerDarkBend = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.006f * alpha),
                Color.Black.copy(alpha = 0.024f * alpha)
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

fun DrawScope.drawLauncherLikeBackground(w: Float, h: Float, alphaScale: Float = 1f) {
    val a = alphaScale.coerceIn(0f, 0.95f)
    val icon = min(w * 0.145f, h * 0.068f)
    drawRect(
        brush = Brush.linearGradient(
            listOf(
                Color(0xFF061426).copy(alpha = a),
                Color(0xFF0B2947).copy(alpha = a),
                Color(0xFF164166).copy(alpha = a),
                Color(0xFF07111F).copy(alpha = a)
            ),
            start = Offset(w * 0.08f, 0f),
            end = Offset(w * 0.92f, h)
        )
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x552F72AD).copy(alpha = 0.24f * a), Color.Transparent),
            center = Offset(w * 0.74f, h * 0.34f),
            radius = w * 0.58f
        ),
        topLeft = Offset(w * 0.18f, h * 0.02f),
        size = Size(w * 1.12f, h * 0.75f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x33236AA8).copy(alpha = 0.15f * a), Color.Transparent),
            center = Offset(w * 0.20f, h * 0.62f),
            radius = w * 0.44f
        ),
        topLeft = Offset(-w * 0.18f, h * 0.30f),
        size = Size(w * 0.80f, h * 0.58f),
        blendMode = BlendMode.Screen
    )

    val xs = listOf(0.15f, 0.38f, 0.62f, 0.85f)
    val ys = listOf(0.11f, 0.24f, 0.37f, 0.50f, 0.63f, 0.76f)
    val colors = listOf(
        Color(0xFF18AFFF), Color(0xFFFFB51B), Color(0xFF181A28), Color.White,
        Color(0xFFFF5058), Color(0xFFFF941D), Color(0xFFB9C3CD), Color(0xFF1078F8)
    )
    var k = 0
    ys.forEach { y ->
        xs.forEach { x ->
            if (!(y == 0.24f && x > 0.50f) && !(y == 0.76f && x == 0.62f)) {
                drawRoundRect(
                    color = colors[k % colors.size].copy(alpha = 0.72f * a),
                    topLeft = Offset(w * x - icon / 2f, h * y - icon / 2f),
                    size = Size(icon, icon),
                    cornerRadius = CornerRadius(icon * 0.22f, icon * 0.22f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f * a), Color.Transparent)),
                    topLeft = Offset(w * x - icon / 2f, h * y - icon / 2f),
                    size = Size(icon, icon),
                    cornerRadius = CornerRadius(icon * 0.22f, icon * 0.22f),
                    blendMode = BlendMode.Screen
                )
                k++
            }
        }
    }
    drawRoundRect(
        color = Color(0xFFB9C3CD).copy(alpha = 0.62f * a),
        topLeft = Offset(w * 0.53f, h * 0.21f),
        size = Size(w * 0.40f, h * 0.17f),
        cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
    )
    drawRoundRect(
        color = Color(0xFF071A2B).copy(alpha = 0.42f * a),
        topLeft = Offset(w * 0.04f, h * 0.885f),
        size = Size(w * 0.92f, h * 0.095f),
        cornerRadius = CornerRadius(h * 0.030f, h * 0.030f)
    )
    repeat(5) { i ->
        drawRoundRect(
            color = colors[(i + 2) % colors.size].copy(alpha = 0.64f * a),
            topLeft = Offset(w * (0.14f + i * 0.18f) - icon * 0.40f, h * 0.932f - icon * 0.40f),
            size = Size(icon * 0.80f, icon * 0.80f),
            cornerRadius = CornerRadius(icon * 0.18f, icon * 0.18f)
        )
    }
}
