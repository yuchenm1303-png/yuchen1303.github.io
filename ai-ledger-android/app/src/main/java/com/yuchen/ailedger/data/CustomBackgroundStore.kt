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

class CustomBackgroundStore(
    private val context: Context
) {
    private val backgroundDir: File
        get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    val customBackgroundFile: File
        get() = File(backgroundDir, CUSTOM_BACKGROUND_FILE)

    private val customBackgroundSourceFile: File
        get() = File(backgroundDir, CustomBackgroundToneProcessor.SOURCE_FILE_NAME)

    fun saveFromUri(uri: Uri): String {
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

        // 原图只压缩一次，展示文件直接复制同一份 JPEG，避免上传时重复执行昂贵编码。
        writeJpeg(fitted, customBackgroundSourceFile)
        copyFileAtomically(customBackgroundSourceFile, target)
        CustomBackgroundToneProcessor.invalidate(target)

        if (fitted !== rotated) fitted.recycle()
        if (rotated !== decoded) rotated.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        return target.absolutePath
    }

    fun clearCustomBackground() {
        backgroundDir.listFiles()
            .orEmpty()
            .filter {
                it.name.startsWith("custom_wallpaper") ||
                    it.name.startsWith(".${CUSTOM_BACKGROUND_FILE}.")
            }
            .forEach { it.delete() }
    }

    private fun writeJpeg(bitmap: Bitmap, target: File) {
        val temporary = File(target.parentFile, ".${target.name}.write-${System.nanoTime()}")
        try {
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            }
            replaceFile(temporary, target)
        } finally {
            temporary.delete()
        }
    }

    private fun copyFileAtomically(source: File, target: File) {
        val temporary = File(target.parentFile, ".${target.name}.copy-${System.nanoTime()}")
        try {
            source.copyTo(temporary, overwrite = true)
            replaceFile(temporary, target)
        } finally {
            temporary.delete()
        }
    }

    private fun replaceFile(source: File, target: File) {
        if (target.exists() && !target.delete()) {
            error("无法替换背景图片")
        }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
        }
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

    private companion object {
        const val CUSTOM_BACKGROUND_FILE = "custom_wallpaper.jpg"
        const val MAX_DECODE_EDGE = 2400
        const val MAX_STORE_EDGE = 1800
        const val JPEG_QUALITY = 92
    }
}
