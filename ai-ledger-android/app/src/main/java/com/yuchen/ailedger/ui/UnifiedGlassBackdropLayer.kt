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
import androidx.compose.ui.graphics.PathFillType
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
                backdropAlpha = item.backdropAlpha,
                border = border
            )
            drawUnifiedGlassLensEdgeItem(
                backdrop = backdrop,
                itemRect = itemRect,
                screenRect = screenRect,
                sampleOffset = sampleOffset,
                radius = item.radius,
                border = border,
                strength = item.edgeStrength
            )
            drawUnifiedGlassHighlightItem(
                itemRect = itemRect,
                radius = item.radius,
                border = border
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

private fun Rect.insetBy(value: Float): Rect = Rect(left + value, top + value, right - value, bottom - value)

private fun DrawScope.drawUnifiedGlassBackdropItem(
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
    val visibleWidth = visibleRect.width.roundToInt().coerceAtLeast(1)
    val visibleHeight = visibleRect.height.roundToInt().coerceAtLeast(1)
    val corner = radius.dp.toPx()
    val path = Path().apply {
        addRoundRect(RoundRect(rect = itemRect, cornerRadius = CornerRadius(corner, corner)))
    }

    val bodyScale = (border.bodyAlpha / 0.20f).coerceIn(0.35f, 2.20f)
    val alpha = (glassIntensity * bodyScale).coerceIn(0.12f, 1.25f)
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
            alpha = (backdropAlpha * (0.76f + border.bodyAlpha.coerceIn(0f, 0.50f))).coerceIn(0.25f, 1f),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            color = Color(0xFF72859A).copy(alpha = baseScrimAlpha * 0.22f),
            topLeft = Offset(visibleRect.left, visibleRect.top),
            size = Size(visibleRect.width, visibleRect.height),
            blendMode = BlendMode.SrcOver
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = milkAlpha * 0.34f),
                    Color(0xFFDCE5EF).copy(alpha = milkAlpha * 0.15f),
                    Color(0xFF172333).copy(alpha = baseScrimAlpha * 0.10f)
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

private fun DrawScope.drawUnifiedGlassLensEdgeItem(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    screenRect: Rect,
    sampleOffset: Offset,
    radius: Int,
    border: GlassBorderStyle,
    strength: Float
) {
    val w = itemRect.width
    val h = itemRect.height
    if (w <= 4f || h <= 4f) return

    val corner = radius.dp.toPx()
    val ringWidth = border.ringWidthDp.dp.toPx().coerceIn(2.dp.toPx(), min(w, h) * 0.30f)
    val pull = border.edgePullDp.dp.toPx().coerceIn(0f, min(w, h) * 0.42f)
    val edgeAlpha = (border.edgeAlpha * (0.42f + strength * 0.42f) * border.edgeBrightness.coerceIn(0.72f, 1.25f)).coerceIn(0f, 0.72f)
    if (edgeAlpha <= 0.01f || pull <= 0.5f) return

    val outerPath = Path().apply {
        addRoundRect(RoundRect(rect = itemRect, cornerRadius = CornerRadius(corner, corner)))
    }

    clipPath(outerPath) {
        drawContinuousLensLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = 0f,
            width = ringWidth * 0.34f,
            sourceInset = pull * 1.05f,
            alpha = edgeAlpha * 0.18f
        )
        drawContinuousLensLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = ringWidth * 0.18f,
            width = ringWidth * 0.42f,
            sourceInset = pull,
            alpha = edgeAlpha * 0.42f
        )
        drawContinuousLensLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = ringWidth * 0.52f,
            width = ringWidth * 0.34f,
            sourceInset = pull * 0.66f,
            alpha = edgeAlpha * 0.18f
        )

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.bodyAlpha * 0.020f),
                    Color.White.copy(alpha = border.bodyAlpha * 0.010f),
                    Color.Transparent
                ),
                center = itemRect.center,
                radius = max(w, h) * 0.82f
            ),
            topLeft = Offset(itemRect.left + ringWidth * 0.35f, itemRect.top + ringWidth * 0.35f),
            size = Size(w - ringWidth * 0.70f, h - ringWidth * 0.70f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = ringWidth * 0.88f),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawUnifiedGlassHighlightItem(
    itemRect: Rect,
    radius: Int,
    border: GlassBorderStyle
) {
    val w = itemRect.width
    val h = itemRect.height
    val corner = radius.dp.toPx()
    fun itemOffset(x: Float, y: Float): Offset = Offset(itemRect.left + x, itemRect.top + y)
    fun itemSize(width: Float, height: Float): Size = Size(width, height)

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = border.topHighlightAlpha * 0.16f),
                Color(0xFFEAF3FF).copy(alpha = border.topHighlightAlpha * 0.028f),
                Color.Transparent
            ),
            startY = itemRect.top,
            endY = itemRect.top + h * 0.20f
        ),
        topLeft = itemOffset(1.0.dp.toPx(), 1.0.dp.toPx()),
        size = itemSize(w - 2.0.dp.toPx(), h - 2.0.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.9.dp.toPx()),
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (border.outerStrokeAlpha * 0.32f).coerceIn(0f, 0.22f)),
                Color(0xFFE8F4FF).copy(alpha = border.outerStrokeAlpha * 0.11f),
                Color.White.copy(alpha = border.outerStrokeAlpha * 0.026f),
                Color(0xFFFFD9E5).copy(alpha = border.outerStrokeAlpha * 0.040f)
            ),
            start = itemOffset(0f, 0f),
            end = itemOffset(w, h)
        ),
        topLeft = itemOffset(0.65.dp.toPx(), 0.65.dp.toPx()),
        size = itemSize(w - 1.3.dp.toPx(), h - 1.3.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 0.70.dp.toPx()),
        blendMode = BlendMode.Screen
    )

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = border.cornerGlintAlpha * 0.55f),
                Color(0xFFEAF5FF).copy(alpha = border.cornerGlintAlpha * 0.16f),
                Color.Transparent
            ),
            center = itemOffset(w * 0.10f, h * 0.08f),
            radius = w * 0.28f
        ),
        topLeft = itemOffset(0f, 0f),
        size = itemSize(w, h),
        blendMode = BlendMode.Screen
    )

    if (border.bottomShadowAlpha > 0.01f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF071225).copy(alpha = border.bottomShadowAlpha * 0.20f)
                ),
                startY = itemRect.top + h * 0.58f,
                endY = itemRect.bottom
            ),
            topLeft = itemOffset(2.0.dp.toPx(), 2.0.dp.toPx()),
            size = itemSize(w - 4.0.dp.toPx(), h - 4.0.dp.toPx()),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.0.dp.toPx()),
            blendMode = BlendMode.Multiply
        )
    }
}

private fun DrawScope.drawContinuousLensLayer(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    screenRect: Rect,
    sampleOffset: Offset,
    corner: Float,
    inset: Float,
    width: Float,
    sourceInset: Float,
    alpha: Float
) {
    val layerRect = itemRect.insetBy(inset)
    if (layerRect.width <= 0f || layerRect.height <= 0f || width <= 0f || alpha <= 0f) return

    val layerCorner = (corner - inset).coerceAtLeast(0f)
    val innerRect = layerRect.insetBy(width)
    val innerCorner = (layerCorner - width).coerceAtLeast(0f)
    val ringPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(rect = layerRect, cornerRadius = CornerRadius(layerCorner, layerCorner)))
        if (innerRect.width > 0f && innerRect.height > 0f) {
            addRoundRect(RoundRect(rect = innerRect, cornerRadius = CornerRadius(innerCorner, innerCorner)))
        }
    }

    val visible = layerRect.intersectionOrNull(screenRect) ?: return
    clipPath(ringPath) {
        drawLensBackdrop(
            backdrop = backdrop,
            itemRect = itemRect,
            visibleRect = visible,
            sampleOffset = sampleOffset,
            sourceInset = sourceInset,
            alpha = alpha
        )
    }
}

private fun DrawScope.drawLensBackdrop(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    visibleRect: Rect,
    sampleOffset: Offset,
    sourceInset: Float,
    alpha: Float
) {
    val w = itemRect.width.coerceAtLeast(1f)
    val h = itemRect.height.coerceAtLeast(1f)
    val insetX = sourceInset.coerceIn(0f, w * 0.38f)
    val insetY = sourceInset.coerceIn(0f, h * 0.38f)
    val sourceW = (w - insetX * 2f).coerceAtLeast(1f)
    val sourceH = (h - insetY * 2f).coerceAtLeast(1f)

    val relLeft = visibleRect.left - itemRect.left
    val relTop = visibleRect.top - itemRect.top
    val sourceLocalX = insetX + relLeft * sourceW / w
    val sourceLocalY = insetY + relTop * sourceH / h
    val sourceLocalW = visibleRect.width * sourceW / w
    val sourceLocalH = visibleRect.height * sourceH / h

    val dstW = visibleRect.width.roundToInt().coerceAtLeast(1)
    val dstH = visibleRect.height.roundToInt().coerceAtLeast(1)
    val srcX = ((sampleOffset.x + sourceLocalX) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = ((sampleOffset.y + sourceLocalY) * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (sourceLocalW * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (sourceLocalH * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(visibleRect.left.roundToInt(), visibleRect.top.roundToInt()),
        dstSize = IntSize(dstW, dstH),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcOver
    )
}