package com.yuchen.ailedger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class SavedBackground(
    val originalPath: String,
    val blurPath: String?
)

class CustomBackgroundStore(
    private val context: Context
) {
    private val backgroundDir: File
        get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    val customBackgroundFile: File
        get() = File(backgroundDir, CUSTOM_BACKGROUND_FILE)

    val customBackgroundBlurFile: File
        get() = File(backgroundDir, CUSTOM_BACKGROUND_BLUR_FILE)

    fun saveFromUri(uri: Uri): SavedBackground {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("无法读取图片")

        val rotated = decoded.applyExifRotation(uri)
        val fitted = rotated.scaleToMaxEdge(MAX_STORE_EDGE)
        val target = customBackgroundFile
        FileOutputStream(target).use { output ->
            fitted.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }

        val blurPath = runCatching {
            val blurSource = fitted.scaleToMaxEdge(MAX_BLUR_EDGE)
            val blurred = boxBlur(blurSource, BLUR_RADIUS, BLUR_ITERATIONS)
            val blurTarget = customBackgroundBlurFile
            FileOutputStream(blurTarget).use { output ->
                blurred.compress(Bitmap.CompressFormat.JPEG, BLUR_JPEG_QUALITY, output)
            }
            if (blurred !== blurSource) blurred.recycle()
            if (blurSource !== fitted) blurSource.recycle()
            blurTarget.absolutePath
        }.getOrNull()

        if (fitted !== rotated) fitted.recycle()
        if (rotated !== decoded) rotated.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        return SavedBackground(originalPath = target.absolutePath, blurPath = blurPath)
    }

    fun clearCustomBackground() {
        customBackgroundFile.takeIf { it.exists() }?.delete()
        customBackgroundBlurFile.takeIf { it.exists() }?.delete()
    }

    private fun Bitmap.applyExifRotation(uri: Uri): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.scaleToMaxEdge(maxEdge: Int): Bitmap {
        val currentMax = max(width, height)
        if (currentMax <= maxEdge) return this
        val scale = maxEdge.toFloat() / currentMax.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun calculateSampleSize(width: Int, height: Int, targetEdge: Int): Int {
        var sample = 1
        val maxEdge = max(width, height)
        while (maxEdge / sample > targetEdge * 2) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun boxBlur(input: Bitmap, radius: Int, iterations: Int): Bitmap {
        if (radius <= 0 || iterations <= 0) return input
        var current = input.copy(Bitmap.Config.ARGB_8888, false)
        repeat(iterations) {
            val next = boxBlurOnce(current, radius)
            if (current !== input) current.recycle()
            current = next
        }
        return current
    }

    private fun boxBlurOnce(input: Bitmap, radius: Int): Bitmap {
        val width = input.width
        val height = input.height
        val source = IntArray(width * height)
        val temp = IntArray(width * height)
        val output = IntArray(width * height)
        input.getPixels(source, 0, width, 0, 0, width, height)
        val window = radius * 2 + 1

        for (y in 0 until height) {
            var a = 0; var r = 0; var g = 0; var b = 0
            val row = y * width
            for (i in -radius..radius) {
                val x = i.coerceIn(0, width - 1)
                val c = source[row + x]
                a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (x in 0 until width) {
                temp[row + x] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val remove = source[row + (x - radius).coerceIn(0, width - 1)]
                val add = source[row + (x + radius + 1).coerceIn(0, width - 1)]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }

        for (x in 0 until width) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val y = i.coerceIn(0, height - 1)
                val c = temp[y * width + x]
                a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (y in 0 until height) {
                output[y * width + x] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val remove = temp[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = temp[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val CUSTOM_BACKGROUND_FILE = "custom_wallpaper.jpg"
        const val CUSTOM_BACKGROUND_BLUR_FILE = "custom_wallpaper_blur.jpg"
        const val MAX_DECODE_EDGE = 2400
        const val MAX_STORE_EDGE = 1800
        const val MAX_BLUR_EDGE = 640
        const val BLUR_RADIUS = 16
        const val BLUR_ITERATIONS = 3
        const val JPEG_QUALITY = 92
        const val BLUR_JPEG_QUALITY = 88
    }
}