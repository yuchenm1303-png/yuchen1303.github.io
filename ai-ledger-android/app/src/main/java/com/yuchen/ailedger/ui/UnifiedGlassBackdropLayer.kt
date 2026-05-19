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
            drawUnifiedGlassEdgeItem(
                backdrop = backdrop,
                itemRect = itemRect,
                screenRect = screenRect,
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
            alpha = (backdropAlpha * (0.76f + border.bodyAlpha.coerceIn(0f, 0.50f))).coerceIn(0.25f, 1f),
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
    val corner = radius.dp.toPx()
    val ringWidth = border.ringWidthDp.dp.toPx().coerceIn(2.dp.toPx(), min(w, h) * 0.30f)
    val edgePull = border.edgePullDp.dp.toPx().coerceIn(0f, min(w, h) * 0.95f)
    val edgeAlpha = (border.edgeAlpha * (0.54f + strength * 0.74f) * border.edgeBrightness.coerceIn(0.72f, 1.35f)).coerceIn(0f, 1f)
    val softWidth = (border.edgeBlurDp.dp.toPx() * 0.28f).coerceIn(2.dp.toPx(), ringWidth * 0.55f)
    val outerPath = Path().apply {
        addRoundRect(RoundRect(rect = itemRect, cornerRadius = CornerRadius(corner, corner)))
    }

    fun itemOffset(x: Float, y: Float): Offset = Offset(itemRect.left + x, itemRect.top + y)
    fun itemSize(width: Float, height: Float): Size = Size(width, height)

    clipPath(outerPath) {
        // 多层软衰减透镜边缘：外层弱、中层强、内层快速淡出，避免“切下一块”的硬边。
        drawSoftRimLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = 0f,
            width = ringWidth * 0.30f + softWidth * 0.35f,
            edgePull = edgePull * 1.06f,
            alpha = edgeAlpha * 0.28f
        )
        drawSoftRimLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = ringWidth * 0.18f,
            width = ringWidth * 0.34f + softWidth * 0.42f,
            edgePull = edgePull,
            alpha = edgeAlpha * 0.72f
        )
        drawSoftRimLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = ringWidth * 0.46f,
            width = ringWidth * 0.30f + softWidth * 0.32f,
            edgePull = edgePull * 0.82f,
            alpha = edgeAlpha * 0.38f
        )
        drawSoftRimLayer(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            sampleOffset = sampleOffset,
            corner = corner,
            inset = ringWidth * 0.72f,
            width = ringWidth * 0.22f + softWidth * 0.22f,
            edgePull = edgePull * 0.58f,
            alpha = edgeAlpha * 0.13f
        )

        // 用一层很轻的雾面过渡把折射边缘并回主体玻璃，弱化内圈硬边。
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.bodyAlpha * 0.030f),
                    Color.White.copy(alpha = border.bodyAlpha * 0.012f),
                    Color.Transparent
                ),
                startY = itemRect.top,
                endY = itemRect.top + h * 0.42f
            ),
            topLeft = itemOffset(softWidth * 0.35f, softWidth * 0.35f),
            size = itemSize(w - softWidth * 0.70f, h - softWidth * 0.70f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = (ringWidth * 0.70f).coerceAtLeast(3.dp.toPx())),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.topHighlightAlpha * 0.18f),
                    Color(0xFFEAF3FF).copy(alpha = border.topHighlightAlpha * 0.035f),
                    Color.Transparent
                ),
                startY = itemRect.top,
                endY = itemRect.top + h * 0.20f
            ),
            topLeft = itemOffset(1.0.dp.toPx(), 1.0.dp.toPx()),
            size = itemSize(w - 2.0.dp.toPx(), h - 2.0.dp.toPx()),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 2.0.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (border.outerStrokeAlpha * 0.36f).coerceIn(0f, 0.24f)),
                    Color(0xFFE8F4FF).copy(alpha = border.outerStrokeAlpha * 0.13f),
                    Color.White.copy(alpha = border.outerStrokeAlpha * 0.030f),
                    Color(0xFFFFD9E5).copy(alpha = border.outerStrokeAlpha * 0.048f)
                ),
                start = itemOffset(0f, 0f),
                end = itemOffset(w, h)
            ),
            topLeft = itemOffset(0.65.dp.toPx(), 0.65.dp.toPx()),
            size = itemSize(w - 1.3.dp.toPx(), h - 1.3.dp.toPx()),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 0.72.dp.toPx()),
            blendMode = BlendMode.Screen
        )

        val innerInset = ringWidth + softWidth * 0.45f
        val innerRect = itemRect.insetBy(innerInset)
        if (innerRect.width > 0f && innerRect.height > 0f) {
            val innerCorner = (corner - innerInset).coerceAtLeast(0f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = border.innerStrokeAlpha * 0.06f),
                        Color.Transparent,
                        Color.White.copy(alpha = border.innerStrokeAlpha * 0.018f)
                    ),
                    startY = itemRect.top,
                    endY = itemRect.bottom
                ),
                topLeft = Offset(innerRect.left, innerRect.top),
                size = Size(innerRect.width, innerRect.height),
                cornerRadius = CornerRadius(innerCorner, innerCorner),
                style = Stroke(width = 0.46.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = border.cornerGlintAlpha * 0.72f),
                    Color(0xFFEAF5FF).copy(alpha = border.cornerGlintAlpha * 0.20f),
                    Color.Transparent
                ),
                center = itemOffset(w * 0.10f, h * 0.08f),
                radius = w * 0.28f
            ),
            topLeft = itemOffset(0f, 0f),
            size = itemSize(w, h),
            blendMode = BlendMode.Screen
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF071225).copy(alpha = border.bottomShadowAlpha * 0.24f)
                ),
                startY = itemRect.top + h * 0.56f,
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

private fun DrawScope.drawSoftRimLayer(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    screenRect: Rect,
    sampleOffset: Offset,
    corner: Float,
    inset: Float,
    width: Float,
    edgePull: Float,
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
    val layerTop = layerRect.top
    val layerBottom = layerRect.bottom
    val layerLeft = layerRect.left
    val layerRight = layerRect.right
    clipPath(ringPath) {
        drawRefractedBackdropStrip(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            stripRect = Rect(layerLeft, layerTop, layerRight, layerTop + width * 1.72f),
            sampleOffset = sampleOffset,
            refractOffset = Offset(0f, edgePull),
            alpha = alpha
        )
        drawRefractedBackdropStrip(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            stripRect = Rect(layerLeft, layerBottom - width * 1.72f, layerRight, layerBottom),
            sampleOffset = sampleOffset,
            refractOffset = Offset(0f, -edgePull),
            alpha = alpha * 0.80f
        )
        drawRefractedBackdropStrip(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            stripRect = Rect(layerLeft, layerTop, layerLeft + width * 1.58f, layerBottom),
            sampleOffset = sampleOffset,
            refractOffset = Offset(edgePull, 0f),
            alpha = alpha * 0.68f
        )
        drawRefractedBackdropStrip(
            backdrop = backdrop,
            itemRect = itemRect,
            screenRect = screenRect,
            stripRect = Rect(layerRight - width * 1.58f, layerTop, layerRight, layerBottom),
            sampleOffset = sampleOffset,
            refractOffset = Offset(-edgePull, 0f),
            alpha = alpha * 0.68f
        )
    }
}

private fun DrawScope.drawRefractedBackdropStrip(
    backdrop: BlurredBackdropBitmap,
    itemRect: Rect,
    screenRect: Rect,
    stripRect: Rect,
    sampleOffset: Offset,
    refractOffset: Offset,
    alpha: Float
) {
    val visible = stripRect.intersectionOrNull(screenRect) ?: return
    val dstW = visible.width.roundToInt().coerceAtLeast(1)
    val dstH = visible.height.roundToInt().coerceAtLeast(1)
    val sampleX = sampleOffset.x + (visible.left - itemRect.left) + refractOffset.x
    val sampleY = sampleOffset.y + (visible.top - itemRect.top) + refractOffset.y
    val srcX = (sampleX * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.width - 1)
    val srcY = (sampleY * backdrop.scale).roundToInt().coerceIn(0, backdrop.image.height - 1)
    val srcW = (dstW * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.width - srcX)
    val srcH = (dstH * backdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(backdrop.image.height - srcY)

    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(visible.left.roundToInt(), visible.top.roundToInt()),
        dstSize = IntSize(dstW, dstH),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcOver
    )
}