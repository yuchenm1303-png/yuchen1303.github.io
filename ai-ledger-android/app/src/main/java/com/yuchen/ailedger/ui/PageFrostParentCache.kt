package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal fun DrawScope.ensurePageFrostCache(
    item: PageFrostParentItem,
    itemSize: Size,
): PageFrostDrawCache {
    val cache = item.drawCache
    val width = itemSize.width.coerceAtLeast(1f)
    val height = itemSize.height.coerceAtLeast(1f)
    val radiusPx = item.radiusDp.dp.toPx()
        .coerceAtMost(min(width, height) * 0.5f)
        .coerceAtLeast(0f)
    val signature = pageFrostGeometrySignature(width, height, radiusPx)
    if (cache.geometrySignature == signature) return cache

    cache.localSize = Size(width, height)
    cache.mask = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                radiusX = radiusPx,
                radiusY = radiusPx,
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

private fun pageFrostGeometrySignature(width: Float, height: Float, radius: Float): Long {
    var result = 1125899906842597L
    result = result * 31L + width.toBits()
    result = result * 31L + height.toBits()
    result = result * 31L + radius.toBits()
    return result
}
