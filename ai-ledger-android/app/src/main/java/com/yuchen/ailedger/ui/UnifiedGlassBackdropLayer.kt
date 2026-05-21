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
    val backdrop = LocalBlurredBackdrop.current
    val origin = LocalBackdropOrigin.current
    val ticker = LocalBackdropFrameTicker.current
    val spec = LocalGlassBackdrop.current

    Canvas(modifier = modifier) {
        val items = registry?.snapshot().orEmpty()
        // 没有统一玻璃项时，不读取 ticker，避免空 Canvas 也被每帧重绘。
        if (items.isEmpty()) return@Canvas

        // 只有确实存在需要跟随滚动的统一玻璃项时，才订阅帧时钟刷新坐标。
        ticker?.frameNanos
        val cached = backdrop ?: return@Canvas
        val border = spec?.borderStyle ?: GlassBorderStyle()
        val screen = Rect(0f, 0f, size.width, size.height)

        items.forEach { item ->
            if (!item.coordinates.isAttached()) return@forEach
            val itemSize = item.coordinates.itemSize()
            if (itemSize.width <= 0 || itemSize.height <= 0) return@forEach
            val topLeft = item.coordinates.rootOffset()
            val rect = Rect(topLeft, Size(itemSize.width.toFloat(), itemSize.height.toFloat()))
            val visible = rect.intersectionOrNull(screen) ?: return@forEach
            val sampleOffset = item.coordinates.offsetRelativeTo(origin)

            drawGlassBody(
                backdrop = cached,
                itemRect = rect,
                visibleRect = visible,
                sampleOffset = sampleOffset,
                radius = item.radius,
                quality = item.quality,
                glassIntensity = item.glassIntensity,
                backdropAlpha = item.backdropAlpha,
                border = border
            )
            drawGlassHighlights(rect, item.radius, border)
        }
    }
}

private fun Rect.intersectionOrNull(other: Rect): Rect? {
    val left = max(left, other.left)
    val top = max(top, other.top)
    val right = min(right, other.right)
    val bottom = min(bottom, other.bottom)
    return if (right > left && bottom > top) Rect(left, top, right, bottom) else null
}

private fun DrawScope.drawGlassBody(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    radius: Int,
    quality: RenderQuality,
    glassIntensity: Float,
    backdropAlpha: Float,
    border: GlassBorderStyle
) {
    val corner = radius.dp.toPx()
    val bodyScale = (border.bodyAlpha / 0.20f).coerceIn(0.35f, 2.20f)
    val alpha = (glassIntensity * bodyScale).coerceIn(0.12f, 1.25f)
    val base = when (quality) {
        RenderQuality.Smooth -> 0.13f
        RenderQuality.Balanced -> 0.16f
        RenderQuality.Experimental -> 0.19f
    } * alpha
    val milk = when (quality) {
        RenderQuality.Smooth -> 0.034f
        RenderQuality.Balanced -> 0.044f
        RenderQuality.Experimental -> 0.054f
    } * alpha

    val path = Path().apply { addRoundRect(RoundRect(itemRect, CornerRadius(corner, corner))) }
    val srcX = ((sampleOffset.x + visibleRect.left - itemRect.left) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + visibleRect.top - itemRect.top) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val dstW = visibleRect.width.roundToInt().coerceAtLeast(1)
    val dstH = visibleRect.height.roundToInt().coerceAtLeast(1)
    val srcW = (dstW * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (dstH * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    clipPath(path) {
        drawImage(
            image = backdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()),
            dstSize = IntSize(dstW, dstH),
            alpha = (backdropAlpha * (0.72f + border.bodyAlpha.coerceIn(0f, 0.42f))).coerceIn(0.22f, 0.96f),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            color = Color(0xFF72859A).copy(alpha = base * 0.18f),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milk * 0.28f),
                    Color(0xFFDCE5EF).copy(alpha = milk * 0.12f),
                    Color(0xFF172333).copy(alpha = base * 0.08f)
                ),
                startY = itemRect.top,
                endY = itemRect.bottom
            ),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
            blendMode = BlendMode.SrcOver
        )
    }
}

private fun DrawScope.drawGlassHighlights(itemRect: Rect, radius: Int, border: GlassBorderStyle) {
    val w = itemRect.width
    val h = itemRect.height
    if (w <= 2f || h <= 2f) return

    val corner = radius.dp.toPx()
    val one = 1.dp.toPx()
    val outerInset = 0.65.dp.toPx()
    val topAlpha = border.topHighlightAlpha.coerceIn(0f, 2f)
    val outerAlpha = border.outerStrokeAlpha.coerceIn(0f, 2f)

    fun p(x: Float, y: Float) = Offset(itemRect.left + x, itemRect.top + y)
    fun s(width: Float, height: Float) = Size(width, height)

    if (topAlpha > 0.01f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = topAlpha * 0.12f),
                    Color(0xFFEAF3FF).copy(alpha = topAlpha * 0.022f),
                    Color.Transparent
                ),
                startY = itemRect.top,
                endY = itemRect.top + h * 0.20f
            ),
            topLeft = p(one, one),
            size = s(w - one * 2f, h - one * 2f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.35.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }

    if (outerAlpha > 0.01f) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (outerAlpha * 0.24f).coerceIn(0f, 0.18f)),
                    Color(0xFFE8F4FF).copy(alpha = outerAlpha * 0.08f),
                    Color.White.copy(alpha = outerAlpha * 0.020f),
                    Color(0xFFFFD9E5).copy(alpha = outerAlpha * 0.030f)
                ),
                start = p(0f, 0f),
                end = p(w, h)
            ),
            topLeft = p(outerInset, outerInset),
            size = s(w - outerInset * 2f, h - outerInset * 2f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 0.58.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }

    if (border.cornerGlintAlpha > 0.01f) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.cornerGlintAlpha.coerceIn(0f, 2f) * 0.36f),
                    Color(0xFFEAF5FF).copy(alpha = border.cornerGlintAlpha.coerceIn(0f, 2f) * 0.10f),
                    Color.Transparent
                ),
                center = p(w * 0.10f, h * 0.08f),
                radius = min(w, h) * 0.44f
            ),
            topLeft = p(0f, 0f),
            size = s(w, h),
            blendMode = BlendMode.Screen
        )
    }

    if (border.bottomShadowAlpha > 0.01f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF071225).copy(alpha = border.bottomShadowAlpha.coerceIn(0f, 2f) * 0.16f)
                ),
                startY = itemRect.top + h * 0.58f,
                endY = itemRect.bottom
            ),
            topLeft = p(2.dp.toPx(), 2.dp.toPx()),
            size = s(w - 4.dp.toPx(), h - 4.dp.toPx()),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 0.82.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}
