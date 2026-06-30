package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal fun DrawScope.drawPageFrostBackdrop(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    sampleSize: Size,
    destinationSize: Size,
    alpha: Float,
) {
    if (backdrop.image.width <= 0 || backdrop.image.height <= 0) return
    val srcX = (sampleOffset.x * backdrop.scale)
        .roundToInt()
        .coerceIn(0, backdrop.image.width - 1)
    val srcY = (sampleOffset.y * backdrop.scale)
        .roundToInt()
        .coerceIn(0, backdrop.image.height - 1)
    val srcW = (sampleSize.width * backdrop.scale)
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(backdrop.image.width - srcX)
    val srcH = (sampleSize.height * backdrop.scale)
        .roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(backdrop.image.height - srcY)

    drawImage(
        image = backdrop.image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(
            destinationSize.width.roundToInt().coerceAtLeast(1),
            destinationSize.height.roundToInt().coerceAtLeast(1),
        ),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.SrcOver,
    )
}
