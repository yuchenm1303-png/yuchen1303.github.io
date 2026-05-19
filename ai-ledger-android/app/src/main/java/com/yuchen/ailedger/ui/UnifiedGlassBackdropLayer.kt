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
import kotlin.math.max
import kotlin.math.min
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
        val screenRect = Rect(0f, 0f, size.width, size.height)

        items.forEach { item ->
            if (!item.coordinates.isAttached()) return@forEach
            val itemSize = item.coordinates.itemSize()
            if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach

            val topLeft = item.coordinates.rootOffset()
            val itemRect = Rect(topLeft, Size(itemSize.width.toFloat(), itemSize.height.toFloat()))
            val visibleRect = itemRect.intersectionOrNull(screenRect) ?: return@forEach

            val sampleOffset = item.coordinates.offsetRelativeTo(backdropOrigin)
            drawUnifiedGlassBackdropItem(
                backdrop = backdrop,
                itemRect = itemRect,
                visibleRect = visibleRect,
                sampleOffset = sampleOffset,
                radius = item.radius,
                quality = item.quality,
                glassIntensity = item.glassIntensity,
                backdropAlpha = item.backdropAlpha
            )
            drawUnifiedGlassEdgeItem(
                itemRect = itemRect,
                visibleRect = visibleRect,
                sampleOffset = sampleOffset,
                radius = item.radius,
                border = border,
                strength = item.edgeStrength
            )
        }
    }
}

private fun Rect.intersectionOrNull(other: Rect): Rect? {
    val left = max(this.left, other.left)
    val top = max(this.top, other.top)
    val right = min(this.right, other.right)
    val bottom = min(this.bottom, other.bottom)
    return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
}

private fun DrawScope.drawUnifiedGlassBackdropItem(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    radius: Int,
    quality: RenderQuality,
    glassIntensity: Float,
    backdropAlpha: Float
) {
    val itemWidth = itemRect.width
    val itemHeight = itemRect.height
    val visibleWidth = visibleRect.width.roundToInt().coerceAtLeast(1)
    val visibleHeight = visibleRect.height.roundToInt().coerceAtLeast(1)
    val corner = radius.dp.toPx()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = itemRect,
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

    val visibleDeltaX = visibleRect.left - itemRect.left
    val visibleDeltaY = visibleRect.top - itemRect.top
    val visibleSampleX = sampleOffset.x + visibleDeltaX
    val visibleSampleY = sampleOffset.y + visibleDeltaY
    val srcX = (visibleSampleX * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = (visibleSampleY * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (visibleWidth * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (visibleHeight * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    clipPath(path) {
        drawImage(
            image = backdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()),
            dstSize = IntSize(visibleWidth, visibleHeight),
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
                startY = itemRect.top,
                endY = itemRect.bottom
            ),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            color = Color(0xFF72859A).copy(alpha = baseScrimAlpha * 0.26f),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
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
                startY = itemRect.top,
                endY = itemRect.bottom
            ),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha * 0.42f),
                    Color.White.copy(alpha = highlightAlpha * 0.08f),
                    Color.Transparent
                ),
                center = Offset(itemRect.left + itemWidth * 0.42f, itemRect.top + itemHeight * 0.08f),
                radius = itemWidth * 0.98f
            ),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawUnifiedGlassEdgeItem(
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    radius: Int,
    border: GlassBorderStyle,
    strength: Float
) {
    val w = itemRect.width
    val h = itemRect.height
    val alpha = strength.coerceIn(0f, 0.34f)
    val corner = radius.dp.toPx()
    val cornerRadius = CornerRadius(corner, corner)
    val outerInset = 0.55.dp.toPx()
    val midInset = 2.70.dp.toPx()
    val innerInset = 7.0.dp.toPx()
    val positionPhase = ((sampleOffset.x + sampleOffset.y) / 900f) % 1f

    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = itemRect,
                cornerRadius = cornerRadius
            )
        )
    }

    fun itemOffset(x: Float, y: Float): Offset = Offset(itemRect.left + x, itemRect.top + y)
    fun itemSize(width: Float, height: Float): Size = Size(width, height)

    clipPath(path) {
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
                startY = itemRect.top,
                endY = itemRect.top + h * 0.30f
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
                startX = itemRect.left,
                endX = itemRect.right
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
                startY = itemRect.top + h * 0.48f,
                endY = itemRect.bottom
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
                startY = itemRect.top,
                endY = itemRect.bottom
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
                startY = itemRect.top,
                endY = itemRect.bottom
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
                startY = itemRect.top + h * 0.52f,
                endY = itemRect.bottom
            ),
            topLeft = itemOffset(midInset, midInset),
            size = itemSize(w - midInset * 2f, h - midInset * 2f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.1.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}
