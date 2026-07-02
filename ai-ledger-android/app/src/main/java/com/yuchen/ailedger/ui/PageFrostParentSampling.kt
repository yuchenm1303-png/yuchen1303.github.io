package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 背景纹理与玻璃目标区域真正相交后的绘制映射。
 *
 * 源区域和目标区域必须一起裁剪。只钳制源纹理起点会让玻璃滑出屏幕时持续采样同一条
 * 边缘像素，并把剩余纹理重新拉伸到整张玻璃，表现为模糊背景冻结和错位。
 */
internal data class VisibleBackdropDrawRegion(
    val sourceOffset: IntOffset,
    val sourceSize: IntSize,
    val destinationOffset: IntOffset,
    val destinationSize: IntSize,
)

internal fun resolveVisibleBackdropDrawRegion(
    sampleOffset: Offset,
    sampleSize: Size,
    destinationSize: Size,
    backdropWidthPx: Float,
    backdropHeightPx: Float,
    textureScale: Float,
    textureWidthPx: Int,
    textureHeightPx: Int,
): VisibleBackdropDrawRegion? {
    if (
        !sampleOffset.x.isFinite() ||
        !sampleOffset.y.isFinite() ||
        sampleSize.width <= 0f ||
        sampleSize.height <= 0f ||
        destinationSize.width <= 0f ||
        destinationSize.height <= 0f ||
        backdropWidthPx <= 0f ||
        backdropHeightPx <= 0f ||
        textureScale <= 0f ||
        textureWidthPx <= 0 ||
        textureHeightPx <= 0
    ) {
        return null
    }

    val localLeft = maxOf(0f, -sampleOffset.x).coerceAtMost(sampleSize.width)
    val localTop = maxOf(0f, -sampleOffset.y).coerceAtMost(sampleSize.height)
    val localRight = minOf(sampleSize.width, backdropWidthPx - sampleOffset.x)
        .coerceAtLeast(localLeft)
    val localBottom = minOf(sampleSize.height, backdropHeightPx - sampleOffset.y)
        .coerceAtLeast(localTop)
    if (localRight <= localLeft || localBottom <= localTop) return null

    val sourceLeft = floor((sampleOffset.x + localLeft) * textureScale)
        .toInt()
        .coerceIn(0, textureWidthPx - 1)
    val sourceTop = floor((sampleOffset.y + localTop) * textureScale)
        .toInt()
        .coerceIn(0, textureHeightPx - 1)
    val sourceRight = ceil((sampleOffset.x + localRight) * textureScale)
        .toInt()
        .coerceIn(sourceLeft + 1, textureWidthPx)
    val sourceBottom = ceil((sampleOffset.y + localBottom) * textureScale)
        .toInt()
        .coerceIn(sourceTop + 1, textureHeightPx)

    val destinationWidthPx = ceil(destinationSize.width).toInt().coerceAtLeast(1)
    val destinationHeightPx = ceil(destinationSize.height).toInt().coerceAtLeast(1)
    val destinationScaleX = destinationSize.width / sampleSize.width
    val destinationScaleY = destinationSize.height / sampleSize.height
    val destinationLeft = floor(localLeft * destinationScaleX)
        .toInt()
        .coerceIn(0, destinationWidthPx - 1)
    val destinationTop = floor(localTop * destinationScaleY)
        .toInt()
        .coerceIn(0, destinationHeightPx - 1)
    val destinationRight = ceil(localRight * destinationScaleX)
        .toInt()
        .coerceIn(destinationLeft + 1, destinationWidthPx)
    val destinationBottom = ceil(localBottom * destinationScaleY)
        .toInt()
        .coerceIn(destinationTop + 1, destinationHeightPx)

    return VisibleBackdropDrawRegion(
        sourceOffset = IntOffset(sourceLeft, sourceTop),
        sourceSize = IntSize(sourceRight - sourceLeft, sourceBottom - sourceTop),
        destinationOffset = IntOffset(destinationLeft, destinationTop),
        destinationSize = IntSize(
            destinationRight - destinationLeft,
            destinationBottom - destinationTop,
        ),
    )
}

internal fun DrawScope.drawPageFrostBackdrop(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    sampleSize: Size,
    destinationSize: Size,
    alpha: Float,
) {
    val region = resolveVisibleBackdropDrawRegion(
        sampleOffset = sampleOffset,
        sampleSize = sampleSize,
        destinationSize = destinationSize,
        backdropWidthPx = backdrop.fullWidthPx.toFloat(),
        backdropHeightPx = backdrop.fullHeightPx.toFloat(),
        textureScale = backdrop.scale,
        textureWidthPx = backdrop.image.width,
        textureHeightPx = backdrop.image.height,
    ) ?: return

    drawImage(
        image = backdrop.image,
        srcOffset = region.sourceOffset,
        srcSize = region.sourceSize,
        dstOffset = region.destinationOffset,
        dstSize = region.destinationSize,
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcOver,
    )
}
