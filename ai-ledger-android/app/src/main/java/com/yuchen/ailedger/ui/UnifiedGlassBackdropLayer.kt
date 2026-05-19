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
    sampleOffset: Offset,
    radius: Int,
    border: GlassBorderStyle,
    strength: Float
) {
    val w = itemRect.width
    val h = itemRect.height
    val baseAlpha = strength.coerceIn(0f, 0.44f)
    val corner = radius.dp.toPx()
    val cornerRadius = CornerRadius(corner, corner)
    val positionPhase = ((sampleOffset.x + sampleOffset.y) / 900f) % 1f
    val path = Path().apply {
        addRoundRect(RoundRect(rect = itemRect, cornerRadius = cornerRadius))
    }

    fun itemOffset(x: Float, y: Float): Offset = Offset(itemRect.left + x, itemRect.top + y)
    fun itemSize(width: Float, height: Float): Size = Size(width, height)

    clipPath(path) {
        // 1. 宽而淡的外部折射晕：先把玻璃边缘做厚，不是一圈硬白线。
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF4F9FF).copy(alpha = 0.18f * baseAlpha),
                    Color(0xFFDDEBFF).copy(alpha = 0.052f * baseAlpha),
                    Color.Transparent,
                    Color(0xFF90A3CA).copy(alpha = 0.020f * baseAlpha),
                    Color(0xFFFFD5E4).copy(alpha = 0.050f * baseAlpha)
                ),
                start = itemOffset(w * (positionPhase - 0.22f), -h * 0.10f),
                end = itemOffset(w * (positionPhase + 0.90f), h * 1.08f)
            ),
            topLeft = itemOffset(1.2.dp.toPx(), 1.2.dp.toPx()),
            size = itemSize(w - 2.4.dp.toPx(), h - 2.4.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 9.5.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // 2. 顶部冷白高光带：图 1 最明显的“玻璃上沿亮边”。
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = (border.topHighlightAlpha * 1.20f).coerceIn(0f, 0.64f)),
                    Color(0xFFEAF3FF).copy(alpha = border.topHighlightAlpha * 0.32f),
                    Color.Transparent
                ),
                startY = itemRect.top,
                endY = itemRect.top + h * 0.24f
            ),
            topLeft = itemOffset(0.8.dp.toPx(), 0.8.dp.toPx()),
            size = itemSize(w - 1.6.dp.toPx(), h - 1.6.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 3.4.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // 3. 细外轮廓：保持锋利边界，但透明度随方向变化。
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (border.outerStrokeAlpha * 1.20f).coerceIn(0f, 0.72f)),
                    Color(0xFFE6F2FF).copy(alpha = border.outerStrokeAlpha * 0.46f),
                    Color.White.copy(alpha = border.outerStrokeAlpha * 0.16f),
                    Color(0xFFB8C7E8).copy(alpha = border.outerStrokeAlpha * 0.10f),
                    Color(0xFFFFD6E1).copy(alpha = border.outerStrokeAlpha * 0.18f)
                ),
                start = itemOffset(0f, 0f),
                end = itemOffset(w, h)
            ),
            topLeft = itemOffset(0.55.dp.toPx(), 0.55.dp.toPx()),
            size = itemSize(w - 1.1.dp.toPx(), h - 1.1.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.08.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // 4. 内侧二次反光线：制造玻璃厚度。
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.innerStrokeAlpha * 0.95f),
                    Color.White.copy(alpha = border.innerStrokeAlpha * 0.20f),
                    Color.Transparent,
                    Color.White.copy(alpha = border.innerStrokeAlpha * 0.12f)
                ),
                startY = itemRect.top,
                endY = itemRect.bottom
            ),
            topLeft = itemOffset(3.0.dp.toPx(), 3.0.dp.toPx()),
            size = itemSize(w - 6.0.dp.toPx(), h - 6.0.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.15.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        // 5. 左上角与右上角局部 glint：让圆角不是平均亮。
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.topHighlightAlpha * 0.26f),
                    Color(0xFFEAF4FF).copy(alpha = border.topHighlightAlpha * 0.10f),
                    Color.Transparent
                ),
                center = itemOffset(w * 0.08f, h * 0.08f),
                radius = w * 0.32f
            ),
            topLeft = itemOffset(0f, 0f),
            size = itemSize(w, h),
            blendMode = BlendMode.Screen
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFE7F0FF).copy(alpha = border.topHighlightAlpha * 0.15f),
                    Color.White.copy(alpha = border.topHighlightAlpha * 0.045f),
                    Color.Transparent
                ),
                center = itemOffset(w * 0.92f, h * 0.10f),
                radius = w * 0.26f
            ),
            topLeft = itemOffset(0f, 0f),
            size = itemSize(w, h),
            blendMode = BlendMode.Screen
        )

        // 6. 底部/右下轻微压暗：形成厚玻璃的折射压缩感。
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF081322).copy(alpha = border.bottomShadowAlpha * 0.40f),
                    Color(0xFF020814).copy(alpha = border.bottomShadowAlpha * 0.92f)
                ),
                startY = itemRect.top + h * 0.54f,
                endY = itemRect.bottom
            ),
            topLeft = itemOffset(2.6.dp.toPx(), 2.6.dp.toPx()),
            size = itemSize(w - 5.2.dp.toPx(), h - 5.2.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 2.4.dp.toPx()),
            blendMode = BlendMode.Multiply
        )

        // 7. 极细移动高光，跟随位置变化，避免边框像贴图一样死。
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = border.topHighlightAlpha * 0.20f),
                    Color.Transparent
                ),
                start = itemOffset(w * (positionPhase - 0.36f), -h * 0.04f),
                end = itemOffset(w * (positionPhase + 0.16f), h * 0.22f)
            ),
            topLeft = itemOffset(0.65.dp.toPx(), 0.65.dp.toPx()),
            size = itemSize(w - 1.3.dp.toPx(), h - 1.3.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 0.82.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
}
