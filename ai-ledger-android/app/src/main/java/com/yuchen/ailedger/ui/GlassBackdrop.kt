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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
    blurRadiusDp: Int = 18,
    liftAlpha: Float = 1f
) {
    val view = LocalView.current
    val alpha = liftAlpha.coerceIn(0.35f, 1.40f)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .blur(blurRadiusDp.dp)
    ) {
        val rootW = if (view.width > 0) view.width.toFloat() else size.width
        val rootH = if (view.height > 0) view.height.toFloat() else size.height
        withTransform({ translate(left = -globalOffset.x, top = -globalOffset.y) }) {
            drawLauncherLikeBackground(rootW, rootH, alpha)
        }
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.105f * alpha),
                    Color.White.copy(alpha = 0.055f * alpha),
                    Color.White.copy(alpha = 0.026f * alpha),
                    Color.Black.copy(alpha = 0.018f * alpha)
                )
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
    val view = LocalView.current
    val alpha = strength.coerceIn(0f, 1.35f)
    Canvas(modifier = modifier.clip(RoundedCornerShape(radius.dp))) {
        val w = size.width
        val h = size.height
        val rootW = if (view.width > 0) view.width.toFloat() else w
        val rootH = if (view.height > 0) view.height.toFloat() else h
        val corner = radius.dp.toPx()
        val outerRadius = CornerRadius(corner, corner)
        val outerRing = edgeRingPath(
            outerInset = 0.7.dp.toPx(),
            innerInset = 8.5.dp.toPx(),
            radiusPx = corner
        )
        val innerRing = edgeRingPath(
            outerInset = 5.2.dp.toPx(),
            innerInset = 13.0.dp.toPx(),
            radiusPx = corner
        )
        val microRing = edgeRingPath(
            outerInset = 1.4.dp.toPx(),
            innerInset = 4.2.dp.toPx(),
            radiusPx = corner
        )

        clipPath(outerRing) {
            withTransform({ translate(left = -globalOffset.x + 5.5.dp.toPx(), top = -globalOffset.y + 4.5.dp.toPx()) }) {
                drawLauncherLikeBackground(rootW, rootH, 0.36f * alpha)
            }
        }
        clipPath(innerRing) {
            withTransform({ translate(left = -globalOffset.x - 4.0.dp.toPx(), top = -globalOffset.y - 2.5.dp.toPx()) }) {
                drawLauncherLikeBackground(rootW, rootH, 0.22f * alpha)
            }
        }
        clipPath(microRing) {
            withTransform({ translate(left = -globalOffset.x, top = -globalOffset.y + 7.0.dp.toPx()) }) {
                drawLauncherLikeBackground(rootW, rootH, 0.18f * alpha)
            }
        }

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.120f * alpha),
                    Color.White.copy(alpha = 0.030f * alpha),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.24f
            ),
            topLeft = Offset(0.9.dp.toPx(), 0.9.dp.toPx()),
            size = Size(w - 1.8.dp.toPx(), h - 1.8.dp.toPx()),
            cornerRadius = outerRadius,
            style = Stroke(width = 0.72.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.045f * alpha)
                ),
                startY = h * 0.52f,
                endY = h
            ),
            topLeft = Offset(2.6.dp.toPx(), 2.6.dp.toPx()),
            size = Size(w - 5.2.dp.toPx(), h - 5.2.dp.toPx()),
            cornerRadius = outerRadius,
            style = Stroke(width = 0.55.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}

private fun DrawScope.edgeRingPath(
    outerInset: Float,
    innerInset: Float,
    radiusPx: Float
): Path {
    val w = size.width
    val h = size.height
    val outer = RoundRect(
        rect = Rect(outerInset, outerInset, w - outerInset, h - outerInset),
        cornerRadius = CornerRadius((radiusPx - outerInset).coerceAtLeast(0f), (radiusPx - outerInset).coerceAtLeast(0f))
    )
    val inner = RoundRect(
        rect = Rect(innerInset, innerInset, w - innerInset, h - innerInset),
        cornerRadius = CornerRadius((radiusPx - innerInset).coerceAtLeast(0f), (radiusPx - innerInset).coerceAtLeast(0f))
    )
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(outer)
        addRoundRect(inner)
    }
}

fun DrawScope.drawLauncherLikeBackground(w: Float, h: Float, alphaScale: Float = 1f) {
    val a = alphaScale.coerceIn(0f, 1.40f)
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
            listOf(Color(0x552F72AD).copy(alpha = 0.33f * a), Color.Transparent),
            center = Offset(w * 0.74f, h * 0.34f),
            radius = w * 0.58f
        ),
        topLeft = Offset(w * 0.18f, h * 0.02f),
        size = Size(w * 1.12f, h * 0.75f),
        blendMode = BlendMode.Screen
    )
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x33236AA8).copy(alpha = 0.20f * a), Color.Transparent),
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
                    color = colors[k % colors.size].copy(alpha = a),
                    topLeft = Offset(w * x - icon / 2f, h * y - icon / 2f),
                    size = Size(icon, icon),
                    cornerRadius = CornerRadius(icon * 0.22f, icon * 0.22f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f * a), Color.Transparent)),
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
        color = Color(0xFFB9C3CD).copy(alpha = 0.88f * a),
        topLeft = Offset(w * 0.53f, h * 0.21f),
        size = Size(w * 0.40f, h * 0.17f),
        cornerRadius = CornerRadius(w * 0.045f, w * 0.045f)
    )
    drawRoundRect(
        color = Color(0xFF071A2B).copy(alpha = 0.52f * a),
        topLeft = Offset(w * 0.04f, h * 0.885f),
        size = Size(w * 0.92f, h * 0.095f),
        cornerRadius = CornerRadius(h * 0.030f, h * 0.030f)
    )
    repeat(5) { i ->
        drawRoundRect(
            color = colors[(i + 2) % colors.size].copy(alpha = a),
            topLeft = Offset(w * (0.14f + i * 0.18f) - icon * 0.40f, h * 0.932f - icon * 0.40f),
            size = Size(icon * 0.80f, icon * 0.80f),
            cornerRadius = CornerRadius(icon * 0.18f, icon * 0.18f)
        )
    }
}
