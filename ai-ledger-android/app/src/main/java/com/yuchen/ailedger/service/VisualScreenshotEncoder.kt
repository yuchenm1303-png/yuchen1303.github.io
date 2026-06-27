package com.yuchen.ailedger.service

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

internal data class EncodedVisualScreenshot(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val quality: Int,
    val encodeMs: Long = 0L,
    val compressionPasses: Int = 1,
    val scalePasses: Int = 0,
)

/**
 * High-resolution UI screenshot encoder for GUI Plus.
 *
 * It keeps enough pixels for small icons and text, then adapts JPEG quality and
 * dimensions only when the encoded frame exceeds the transport budget.
 */
internal object VisualScreenshotEncoder {
    private const val TARGET_LONG_SIDE = 1_800
    private const val MIN_LONG_SIDE = 1_280
    private const val INITIAL_JPEG_QUALITY = 88
    private const val MIN_JPEG_QUALITY = 76
    private const val JPEG_QUALITY_STEP = 6
    private const val MAX_ENCODED_BYTES = 1_250_000
    private const val ENCODE_BUFFER_INITIAL_BYTES = 512 * 1024
    private const val SCALE_NUMERATOR = 9
    private const val SCALE_DENOMINATOR = 10

    fun encode(source: Bitmap): EncodedVisualScreenshot {
        val startedAtNanos = System.nanoTime()
        val originalWidth = source.width.coerceAtLeast(1)
        val originalHeight = source.height.coerceAtLeast(1)
        val originalLongSide = maxOf(originalWidth, originalHeight)
        var targetLongSide = minOf(originalLongSide, TARGET_LONG_SIDE)
        var quality = INITIAL_JPEG_QUALITY
        var target = scaledBitmap(source, targetLongSide)
        var scalePasses = if (target !== source) 1 else 0
        var compressionPasses = 1
        var bytes = compress(target, quality)

        while (bytes.size > MAX_ENCODED_BYTES) {
            if (quality > MIN_JPEG_QUALITY) {
                quality = (quality - JPEG_QUALITY_STEP).coerceAtLeast(MIN_JPEG_QUALITY)
            } else if (targetLongSide > MIN_LONG_SIDE) {
                if (target !== source) target.recycle()
                targetLongSide = (targetLongSide * SCALE_NUMERATOR / SCALE_DENOMINATOR)
                    .coerceAtLeast(MIN_LONG_SIDE)
                target = scaledBitmap(source, targetLongSide)
                scalePasses += 1
                quality = INITIAL_JPEG_QUALITY
            } else {
                break
            }
            compressionPasses += 1
            bytes = compress(target, quality)
        }

        val width = target.width
        val height = target.height
        if (target !== source) target.recycle()
        return EncodedVisualScreenshot(
            bytes = bytes,
            width = width,
            height = height,
            quality = quality,
            encodeMs = ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L),
            compressionPasses = compressionPasses,
            scalePasses = scalePasses,
        )
    }

    internal fun targetSize(width: Int, height: Int, longSideLimit: Int = TARGET_LONG_SIDE): Pair<Int, Int> {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val longSide = maxOf(safeWidth, safeHeight)
        if (longSide <= longSideLimit) return safeWidth to safeHeight
        val scale = longSideLimit.toFloat() / longSide.toFloat()
        return (safeWidth * scale).toInt().coerceAtLeast(1) to
            (safeHeight * scale).toInt().coerceAtLeast(1)
    }

    private fun scaledBitmap(source: Bitmap, longSideLimit: Int): Bitmap {
        val (targetWidth, targetHeight) = targetSize(source.width, source.height, longSideLimit)
        return if (targetWidth == source.width && targetHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        return ByteArrayOutputStream(ENCODE_BUFFER_INITIAL_BYTES).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
    }
}
