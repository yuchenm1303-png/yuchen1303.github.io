package com.yuchen.ailedger.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlin.math.max

internal data class StorageOrganizationSignature(
    val dHash: Long,
    val averageRed: Int,
    val averageGreen: Int,
    val averageBlue: Int,
    val sharpnessVariance: Double,
)

internal class StorageOrganizationSignatureEngine(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun create(file: StorageOrganizationFile): StorageOrganizationSignature? {
        val source = runCatching {
            val uri = Uri.parse(file.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(96, 96), null)
            } else {
                decodeLegacy(uri, 96)
            }
        }.getOrNull() ?: return null
        val sharpness = estimateSharpness(source)
        val scaled = runCatching { Bitmap.createScaledBitmap(source, 9, 8, true) }
            .getOrElse {
                source.recycle()
                return null
            }
        if (scaled !== source) source.recycle()
        return try {
            var hash = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 9) {
                    val pixel = scaled.getPixel(x, y)
                    red += pixel shr 16 and 0xFF
                    green += pixel shr 8 and 0xFF
                    blue += pixel and 0xFF
                    if (x < 8) {
                        val next = scaled.getPixel(x + 1, y)
                        if (grayscale(pixel) > grayscale(next)) hash = hash or (1L shl bitIndex)
                        bitIndex += 1
                    }
                }
            }
            StorageOrganizationSignature(
                dHash = hash,
                averageRed = (red / 72L).toInt(),
                averageGreen = (green / 72L).toInt(),
                averageBlue = (blue / 72L).toInt(),
                sharpnessVariance = sharpness,
            )
        } finally {
            scaled.recycle()
        }
    }

    private fun estimateSharpness(source: Bitmap): Double {
        val bitmap = if (source.width == 64 && source.height == 64) source else {
            runCatching { Bitmap.createScaledBitmap(source, 64, 64, true) }.getOrNull() ?: return Double.MAX_VALUE
        }
        return try {
            val pixels = IntArray(64 * 64)
            bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            var sum = 0.0
            var sumSquares = 0.0
            var count = 0
            for (y in 1 until 63) {
                for (x in 1 until 63) {
                    val index = y * 64 + x
                    val center = grayscale(pixels[index])
                    val laplacian = 4 * center - grayscale(pixels[index - 1]) - grayscale(pixels[index + 1]) -
                        grayscale(pixels[index - 64]) - grayscale(pixels[index + 64])
                    val value = laplacian.toDouble()
                    sum += value
                    sumSquares += value * value
                    count += 1
                }
            }
            if (count == 0) Double.MAX_VALUE else {
                val mean = sum / count.toDouble()
                (sumSquares / count.toDouble()) - mean * mean
            }
        } finally {
            if (bitmap !== source) bitmap.recycle()
        }
    }

    private fun grayscale(pixel: Int): Int =
        ((pixel shr 16 and 0xFF) * 30 + (pixel shr 8 and 0xFF) * 59 + (pixel and 0xFF) * 11) / 100

    private fun decodeLegacy(uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxEdge * 2) sample *= 2
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
