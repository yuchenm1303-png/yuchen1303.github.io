package com.yuchen.ailedger.service

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

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
 * Resolution and JPEG-quality boundaries remain unchanged. When the quality floor is still above
 * the transport budget, the next scale is estimated from the measured byte ratio instead of
 * repeatedly shrinking the bitmap by ten percent and re-encoding every intermediate size.
 */
internal object VisualScreenshotEncoder {
    private const val TARGET_LONG_SIDE = 1_800
    private const val MIN_LONG_SIDE = 1_280
    private const val INITIAL_JPEG_QUALITY = 88
    private const val MIN_JPEG_QUALITY = 76
    private const val JPEG_QUALITY_STEP = 6
    private const val MAX_ENCODED_BYTES = 1_250_000
    private const val ENCODE_BUFFER_INITIAL_BYTES = 512 * 1024
    private const val ESTIMATE_SAFETY_FACTOR = 0.96

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
                val nextLongSide = estimatedLongSide(
                    currentLongSide = targetLongSide,
                    encodedBytes = bytes.size,
                )
                if (target !== source) target.recycle()
                targetLongSide = nextLongSide
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

    internal fun targetSize(
        width: Int,
        height: Int,
        longSideLimit: Int = TARGET_LONG_SIDE,
    ): Pair<Int, Int> {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val longSide = maxOf(safeWidth, safeHeight)
        if (longSide <= longSideLimit) return safeWidth to safeHeight
        val scale = longSideLimit.toFloat() / longSide.toFloat()
        return (safeWidth * scale).toInt().coerceAtLeast(1) to
            (safeHeight * scale).toInt().coerceAtLeast(1)
    }

    internal fun estimatedLongSide(
        currentLongSide: Int,
        encodedBytes: Int,
        maxEncodedBytes: Int = MAX_ENCODED_BYTES,
        minLongSide: Int = MIN_LONG_SIDE,
    ): Int {
        val current = currentLongSide.coerceAtLeast(1)
        val minimum = minLongSide.coerceIn(1, current)
        if (encodedBytes <= maxEncodedBytes || maxEncodedBytes <= 0) return current
        val byteRatio = maxEncodedBytes.toDouble() / encodedBytes.toDouble().coerceAtLeast(1.0)
        val estimated = (current * sqrt(byteRatio) * ESTIMATE_SAFETY_FACTOR).toInt()
        return estimated.coerceAtMost(current - 1).coerceAtLeast(minimum)
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
