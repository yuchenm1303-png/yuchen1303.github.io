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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun UnifiedGlassSceneBackgroundLayer(modifier: Modifier = Modifier) {
    if (!GlassFeatureFlags.USE_UNIFIED_GLASS_BACKGROUND_LAYER) return

    val registry = LocalGlassSceneRegistry.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val registryVersion = registry?.version ?: 0L

    Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        registry?.snapshot().orEmpty().forEach { node ->
            if (node.kind == GlassKind.Recessed && GlassFeatureFlags.USE_UNIFIED_RECESSED_GLASS) {
                drawUnifiedRecessedBackground(node, cachedBackdrop, backdropOrigin)
            }
        }
    }
}

@Composable
fun UnifiedGlassSceneForegroundLayer(modifier: Modifier = Modifier) {
    if (!GlassFeatureFlags.USE_UNIFIED_GLASS_FOREGROUND_LAYER) return

    val registry = LocalGlassSceneRegistry.current
    val cachedBackdrop = LocalBlurredBackdrop.current
    val backdropOrigin = LocalBackdropOrigin.current
    val frameTicker = LocalBackdropFrameTicker.current
    val registryVersion = registry?.version ?: 0L

    Canvas(modifier = modifier) {
        registryVersion
        frameTicker?.frameNanos
        registry?.snapshot().orEmpty().forEach { node ->
            if (node.kind == GlassKind.Recessed && GlassFeatureFlags.USE_UNIFIED_RECESSED_GLASS) {
                drawUnifiedRecessedForeground(node, cachedBackdrop, backdropOrigin)
            }
        }
    }
}

private fun DrawScope.drawUnifiedRecessedBackground(
    node: GlassSceneNode,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    if (!node.coordinates.isAttached()) return
    val outerSize = node.coordinates.itemSize()
    if (outerSize.width <= 0 || outerSize.height <= 0) return

    val outerTopLeft = node.coordinates.rootOffset()
    val outerWidth = outerSize.width.toFloat()
    val outerHeight = outerSize.height.toFloat()
    if (outerTopLeft.x >= size.width || outerTopLeft.y >= size.height || outerTopLeft.x + outerWidth <= 0f || outerTopLeft.y + outerHeight <= 0f) return

    val depth = node.depth.coerceIn(0f, 1f)
    val radiusPx = node.radiusDp.dp.toPx()
    val floorInsetPx = node.floorInsetDp.dp.toPx()
    val floorRadiusPx = (node.radiusDp - 1.2f).coerceAtLeast(5f).dp.toPx()
    val floorTopLeft = outerTopLeft + Offset(floorInsetPx, floorInsetPx)
    val floorSize = Size((outerWidth - floorInsetPx * 2f).coerceAtLeast(1f), (outerHeight - floorInsetPx * 2f).coerceAtLeast(1f))
    val floorRect = Rect(floorTopLeft, floorSize)
    val floorPath = Path().apply { addRoundRect(RoundRect(floorRect, CornerRadius(floorRadiusPx, floorRadiusPx))) }

    drawUnifiedOuterInsetDepth(outerTopLeft, Size(outerWidth, outerHeight), radiusPx, depth, node.innerShadowAlpha)
    clipPath(floorPath) {
        drawUnifiedBackdropCrop(
            floorRect = floorRect,
            floorCoordinates = node.secondaryCoordinates,
            fallbackCoordinates = node.coordinates,
            backdropAlpha = node.floorAlpha.coerceIn(0f, 1f),
            cachedBackdrop = cachedBackdrop,
            backdropOrigin = backdropOrigin
        )
        drawRect(
            color = Color.Black.copy(alpha = (node.floorDimAlpha + depth * 0.06f).coerceIn(0f, 0.75f)),
            topLeft = floorTopLeft,
            size = floorSize
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = node.innerShadowAlpha * (0.12f + depth * 0.14f)),
                    Color.Transparent,
                    Color.White.copy(alpha = node.rimAlpha * 0.035f)
                ),
                startY = floorTopLeft.y,
                endY = floorTopLeft.y + floorSize.height
            ),
            topLeft = floorTopLeft,
            size = floorSize
        )
    }
}

private fun DrawScope.drawUnifiedRecessedForeground(
    node: GlassSceneNode,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    if (!node.coordinates.isAttached()) return
    val outerSize = node.coordinates.itemSize()
    if (outerSize.width <= 0 || outerSize.height <= 0) return

    val outerTopLeft = node.coordinates.rootOffset()
    val outerWidth = outerSize.width.toFloat()
    val outerHeight = outerSize.height.toFloat()
    if (outerTopLeft.x >= size.width || outerTopLeft.y >= size.height || outerTopLeft.x + outerWidth <= 0f || outerTopLeft.y + outerHeight <= 0f) return

    val depth = node.depth.coerceIn(0f, 1f)
    val radiusPx = node.radiusDp.dp.toPx()
    val floorInsetPx = node.floorInsetDp.dp.toPx()
    val floorRadiusPx = (node.radiusDp - 1.2f).coerceAtLeast(5f).dp.toPx()
    val floorTopLeft = outerTopLeft + Offset(floorInsetPx, floorInsetPx)
    val floorSize = Size((outerWidth - floorInsetPx * 2f).coerceAtLeast(1f), (outerHeight - floorInsetPx * 2f).coerceAtLeast(1f))

    drawUnifiedDynamicInsetRimHighlight(node, outerTopLeft, Size(outerWidth, outerHeight), radiusPx, cachedBackdrop, backdropOrigin)
    drawUnifiedInsetStrokes(node, outerTopLeft, Size(outerWidth, outerHeight), radiusPx, floorTopLeft, floorSize, floorRadiusPx, depth)
}

private fun DrawScope.drawUnifiedOuterInsetDepth(topLeft: Offset, slotSize: Size, radiusPx: Float, depth: Float, innerShadowAlpha: Float) {
    val shadow = (0.30f + depth * 0.70f) * innerShadowAlpha
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = shadow * 0.72f),
                Color(0xFF070C29).copy(alpha = 0.28f + depth * 0.12f),
                Color.Black.copy(alpha = shadow * 0.18f)
            ),
            startY = topLeft.y,
            endY = topLeft.y + slotSize.height
        ),
        topLeft = topLeft,
        size = slotSize,
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        blendMode = BlendMode.Multiply
    )
}

private fun DrawScope.drawUnifiedBackdropCrop(
    floorRect: Rect,
    floorCoordinates: GlassCoordinateSource?,
    fallbackCoordinates: GlassCoordinateSource,
    backdropAlpha: Float,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    val floorSize = Size(floorRect.width, floorRect.height)
    if (cachedBackdrop != null) {
        val sampleOffset = when {
            floorCoordinates?.isAttached() == true -> floorCoordinates.offsetRelativeTo(backdropOrigin)
            fallbackCoordinates.isAttached() -> fallbackCoordinates.offsetRelativeTo(backdropOrigin) + Offset(floorRect.left - fallbackCoordinates.rootOffset().x, floorRect.top - fallbackCoordinates.rootOffset().y)
            else -> Offset(floorRect.left, floorRect.top) - (backdropOrigin?.rootOffset() ?: Offset.Zero)
        }
        val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.width - 1)
        val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, cachedBackdrop.image.height - 1)
        val srcW = (floorSize.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(cachedBackdrop.image.width - srcX)
        val srcH = (floorSize.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(cachedBackdrop.image.height - srcY)
        drawImage(
            image = cachedBackdrop.image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(floorRect.left.roundToInt(), floorRect.top.roundToInt()),
            dstSize = IntSize(floorSize.width.roundToInt().coerceAtLeast(1), floorSize.height.roundToInt().coerceAtLeast(1)),
            alpha = backdropAlpha,
            blendMode = BlendMode.SrcOver
        )
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF1A2B58), Color(0xFF5B4A8E), Color(0xFFB85D78)),
                startY = floorRect.top,
                endY = floorRect.bottom
            ),
            topLeft = Offset(floorRect.left, floorRect.top),
            size = floorSize
        )
    }
}

private fun DrawScope.drawUnifiedDynamicInsetRimHighlight(
    node: GlassSceneNode,
    topLeft: Offset,
    slotSize: Size,
    radiusPx: Float,
    cachedBackdrop: BlurredBackdropBitmap?,
    backdropOrigin: BackdropCoordinateSource?
) {
    val image = cachedBackdrop?.image ?: return
    val depth = node.depth.coerceIn(0f, 1f)
    val alpha = node.rimAlpha * (0.42f + depth * 0.20f)
    if (alpha <= 0.001f) return
    val sampleOffset = node.coordinates.offsetRelativeTo(backdropOrigin)
    val srcX = (sampleOffset.x * cachedBackdrop.scale).roundToInt().coerceIn(0, image.width - 1)
    val srcY = (sampleOffset.y * cachedBackdrop.scale).roundToInt().coerceIn(0, image.height - 1)
    val srcW = (slotSize.width * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.width - srcX)
    val srcH = (slotSize.height * cachedBackdrop.scale).roundToInt().coerceAtLeast(1).coerceAtMost(image.height - srcY)
    val strokePx = 1.20f.dp.toPx()
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(topLeft, slotSize), Paint())
        drawImage(
            image = image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(slotSize.width.roundToInt().coerceAtLeast(1), slotSize.height.roundToInt().coerceAtLeast(1)),
            alpha = alpha.coerceIn(0f, 1f),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            color = Color.White,
            topLeft = topLeft + Offset(strokePx * 0.50f, strokePx * 0.50f),
            size = Size(slotSize.width - strokePx, slotSize.height - strokePx),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = strokePx),
            blendMode = BlendMode.DstIn
        )
        canvas.restore()
    }
}

private fun DrawScope.drawUnifiedInsetStrokes(
    node: GlassSceneNode,
    outerTopLeft: Offset,
    outerSize: Size,
    radiusPx: Float,
    floorTopLeft: Offset,
    floorSize: Size,
    floorRadiusPx: Float,
    depth: Float
) {
    val shadowWidth = (1.2f + depth * 3.8f).dp.toPx()
    val innerShadowAlpha = node.innerShadowAlpha
    val rimHighlightAlpha = node.rimAlpha
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = innerShadowAlpha * (0.58f + depth * 0.36f)),
                Color.Black.copy(alpha = innerShadowAlpha * (0.16f + depth * 0.16f)),
                Color.Transparent
            ),
            startY = floorTopLeft.y,
            endY = floorTopLeft.y + floorSize.height
        ),
        topLeft = floorTopLeft,
        size = floorSize,
        cornerRadius = CornerRadius(floorRadiusPx, floorRadiusPx),
        style = Stroke(width = shadowWidth),
        blendMode = BlendMode.Multiply
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = rimHighlightAlpha * 0.28f),
                Color.White.copy(alpha = rimHighlightAlpha * 0.08f),
                Color.Transparent,
                Color.Black.copy(alpha = innerShadowAlpha * 0.14f)
            ),
            start = outerTopLeft,
            end = outerTopLeft + Offset(outerSize.width, outerSize.height)
        ),
        topLeft = outerTopLeft + Offset(0.65.dp.toPx(), 0.65.dp.toPx()),
        size = Size(outerSize.width - 1.3.dp.toPx(), outerSize.height - 1.3.dp.toPx()),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = 0.72.dp.toPx()),
        blendMode = BlendMode.Screen
    )
}
