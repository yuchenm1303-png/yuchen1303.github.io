package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

internal fun DrawScope.ensurePageFrostCache(
    item: PageFrostParentItem,
    destinationSize: Size,
    sourceSize: Size,
): PageFrostDrawCache {
    val cache = item.drawCache
    val width = destinationSize.width.coerceAtLeast(1f)
    val height = destinationSize.height.coerceAtLeast(1f)
    val sourceWidth = sourceSize.width.coerceAtLeast(1f)
    val sourceHeight = sourceSize.height.coerceAtLeast(1f)
    val scaleX = width / sourceWidth
    val scaleY = height / sourceHeight
    val baseRadiusPx = item.radiusDp.dp.toPx().coerceAtLeast(0f)
    val radiusX = (baseRadiusPx * scaleX).coerceAtMost(width * 0.5f)
    val radiusY = (baseRadiusPx * scaleY).coerceAtMost(height * 0.5f)
    val signature = pageFrostGeometrySignature(width, height, radiusX, radiusY)
    if (cache.geometrySignature == signature) return cache

    cache.localSize = Size(width, height)
    cache.mask = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                radiusX = radiusX,
                radiusY = radiusY,
            )
        )
    }
    cache.fallbackBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A2B58),
            Color(0xFF5B4A8E),
            Color(0xFFB85D78),
        ),
        startY = 0f,
        endY = height,
    )
    cache.geometrySignature = signature
    return cache
}

private fun pageFrostGeometrySignature(
    width: Float,
    height: Float,
    radiusX: Float,
    radiusY: Float,
): Long {
    var result = 1125899906842597L
    result = result * 31L + width.toBits()
    result = result * 31L + height.toBits()
    result = result * 31L + radiusX.toBits()
    result = result * 31L + radiusY.toBits()
    return result
}
