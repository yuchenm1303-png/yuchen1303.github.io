package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.roundToInt

@Composable
fun UnifiedGlassBackdropLayer(modifier: Modifier = Modifier) {
    val registry = LocalGlassItemRegistry.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val spec = LocalGlassBackdrop.current

    Canvas(modifier = modifier) {
        ticker?.frameNanos
        val backdrop = cachedBackdrop ?: return@Canvas
        val items = registry?.snapshot().orEmpty()
        val border = spec?.borderStyle ?: GlassBorderStyle()

        items.forEach { item ->
            if (!item.coordinates.isAttached()) return@forEach
            val itemSize = item.coordinates.itemSize()
            if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach

            val topLeft = item.coordinates.rootOffset()
            if (topLeft.y > size.height || topLeft.y + itemSize.height < 0f) return@forEach
            if (topLeft.x > size.width || topLeft.x + itemSize.width < 0f) return@forEach

            val sampleOffset = item.coordinates.offsetRelativeTo(backdropOrigin)
            drawUnifiedGlassBackdropItem(
                backdrop = backdrop,
                topLeft = topLeft,
                itemSize = itemSize,
                sampleOffset = sampleOffset,
                radius = item.radius,
                quality = item.quality,
                glassIntensity = item.glassIntensity,
                backdropAlpha = item.backdropAlpha
            )
            drawUnifiedGlassEdgeItem(
                topLeft = topLeft,
                itemSize = itemSize,
                sampleOffset = sampleOffset,
                radius = item.radius,
                border = border,
                strength = item.edgeStrength
            )
        }
    }
}

private fun DrawScope.drawUnifiedGlassBackdropItem(
    backdrop: BlurredBackdropBitmap,
    topLeft: Offset,
    itemSize: IntSize,
    sampleOffset: Offset,
    radius: Int,
    quality: RenderQuality,
    glassIntensity: Float,
    backdropAlpha: Float
) {
    val width = itemSize.width
    val height = itemSize.height
    val corner = radius.dp.toPx()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(topLeft, Size(width.toFloat(), height.toFloat())),
                cornerRadius = CornerRadius(corner, corner)
            )
        )
    }

    val alpha = glassIntensity.coerceIn(0.34f, 1.25f)
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

    val srcX = (sampleOffset.x * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = (sampleOffset.y * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (width * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (height * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    clipPath(path) {
        drawImage(
            image = backdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(width, height),
            alpha = backdropAlpha.coerceIn(0.35f, 1f),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE0EAF3).copy(alpha = milkAlpha * 0.48f),
                    Color(0xFF9AADBF).copy(alpha = baseScrimAlpha * 0.28f),
                    Color(0xFF40576D).copy(alpha = baseScrimAlpha * 0.30f)
                ),
                startY = topLeft.y,
                endY = topLeft.y + height
            ),
            topLeft = topLeft,
            size = Size(width.toFloat(), height.toFloat()),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            color = Color(0xFF72859A).copy(alpha = baseScrimAlpha * 0.26f),
            topLeft = topLeft,
            size = Size(width.toFloat(), height.toFloat()),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milkAlpha * 0.46f),
                    Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.22f),
                    Color(0xFF9BAEC1).copy(alpha = milkAlpha * 0.10f),
                    Color(0xFF172333).copy(alpha = baseScrimAlpha * 0.14f)
                ),
                startY = topLeft.y,
                endY = topLeft.y + height
            ),
            topLeft = topLeft,
            size = Size(width.toFloat(), height.toFloat()),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha * 0.42f),
                    Color.White.copy(alpha = highlightAlpha * 0.08f),
                    Color.Transparent
                ),
                center = Offset(topLeft.x + width * 0.42f, topLeft.y + height * 0.08f),
                radius = width * 0.98f
            ),
            topLeft = topLeft,
            size = Size(width.toFloat(), height.toFloat()),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawUnifiedGlassEdgeItem(
    topLeft: Offset,
    itemSize: IntSize,
    sampleOffset: Offset,
    radius: Int,
    border: GlassBorderStyle,
    strength: Float
) {
    val w = itemSize.width.toFloat()
    val h = itemSize.height.toFloat()
    val alpha = strength.coerceIn(0f, 0.34f)
    val corner = radius.dp.toPx()
    val cornerRadius = CornerRadius(corner, corner)
    val outerInset = 0.55.dp.toPx()
    val midInset = 2.70.dp.toPx()
    val innerInset = 7.0.dp.toPx()
    val positionPhase = ((sampleOffset.x + sampleOffset.y) / 900f) % 1f

    fun itemOffset(x: Float, y: Float): Offset = Offset(topLeft.x + x, topLeft.y + y)
    fun itemSize(width: Float, height: Float): Size = Size(width, height)

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.055f * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.010f * alpha)
            ),
            start = itemOffset(w * (positionPhase - 0.18f), 0f),
            end = itemOffset(w * (positionPhase + 0.82f), h)
        ),
        topLeft = itemOffset(outerInset, outerInset),
        size = itemSize(w - outerInset * 2f, h - outerInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 8.5.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.070f * alpha),
                Color.White.copy(alpha = 0.018f * alpha),
                Color.Transparent
            ),
            startY = topLeft.y,
            endY = topLeft.y + h * 0.30f
        ),
        topLeft = itemOffset(midInset, midInset),
        size = itemSize(w - midInset * 2f, h - midInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 5.6.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.030f * alpha),
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = 0.010f * alpha),
                Color.White.copy(alpha = 0.016f * alpha)
            ),
            startX = topLeft.x,
            endX = topLeft.x + w
        ),
        topLeft = itemOffset(midInset, midInset),
        size = itemSize(w - midInset * 2f, h - midInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 4.8.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.004f * alpha),
                Color.Black.copy(alpha = 0.018f * alpha)
            ),
            startY = topLeft.y + h * 0.48f,
            endY = topLeft.y + h
        ),
        topLeft = itemOffset(innerInset, innerInset),
        size = itemSize(w - innerInset * 2f, h - innerInset * 2f),
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
            startY = topLeft.y,
            endY = topLeft.y + h
        ),
        topLeft = itemOffset(outerInset, outerInset),
        size = itemSize(w - outerInset * 2f, h - outerInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 1.15.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = border.innerStrokeAlpha), Color.Transparent, Color.White.copy(alpha = border.innerStrokeAlpha * 0.28f)),
            startY = topLeft.y,
            endY = topLeft.y + h
        ),
        topLeft = itemOffset(midInset, midInset),
        size = itemSize(w - midInset * 2f, h - midInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 0.82.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = border.bottomShadowAlpha)),
            startY = topLeft.y + h * 0.52f,
            endY = topLeft.y + h
        ),
        topLeft = itemOffset(midInset, midInset),
        size = itemSize(w - midInset * 2f, h - midInset * 2f),
        cornerRadius = cornerRadius,
        style = Stroke(width = 1.1.dp.toPx()),
        blendMode = BlendMode.Multiply
    )
}
