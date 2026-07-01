package com.yuchen.ailedger.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yuchen.ailedger.model.BackdropDebugParams
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 对用户上传的原图做一次缓存阶段的亮度保护。处理结果写回固定展示文件，原图单独保留，
 * 因此参数重复调整不会产生累计压暗，也不会给正常帧渲染增加任何像素运算。
 */
internal object CustomBackgroundToneProcessor {
    internal const val SOURCE_FILE_NAME = "custom_wallpaper_source.jpg"

    private const val METADATA_FILE_NAME = "custom_wallpaper_tone.txt"
    private const val PROCESSOR_VERSION = 2
    private const val JPEG_QUALITY = 94
    private const val MIN_HIGHLIGHT_GAP = 0.02f
    private const val PROCESSING_ROW_CHUNK = 64

    /**
     * 处理和消费固定展示文件必须处于同一把锁内。这样快速拖动参数时，旧纹理任务不会在
     * 新参数覆盖文件后误读新图并写入旧缓存键。
     */
    @Synchronized
    fun <T> withProcessedFile(
        displayPath: String,
        params: BackdropDebugParams,
        block: (File) -> T,
    ): T? = runCatching {
        val displayFile = ensureProcessedLocked(displayPath, params) ?: return@runCatching null
        block(displayFile)
    }.getOrNull()

    fun invalidate(displayFile: File) {
        metadataFileFor(displayFile).delete()
        displayFile.parentFile
            ?.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(".${displayFile.name}.tone-") }
            .forEach { it.delete() }
    }

    fun sourceIdentityFile(displayFile: File): File =
        File(displayFile.parentFile, SOURCE_FILE_NAME)

    private fun ensureProcessedLocked(
        displayPath: String,
        params: BackdropDebugParams,
    ): File? {
        val displayFile = File(displayPath)
        if (!displayFile.isFile) return null

        val sourceFile = sourceIdentityFile(displayFile)
        if (!sourceFile.isFile) {
            displayFile.copyTo(sourceFile, overwrite = true)
        }

        val normalized = params.normalizedCustomImageTone()
        val fingerprint = buildString {
            append(PROCESSOR_VERSION).append('|')
            append(sourceFile.lastModified()).append('|')
            append(sourceFile.length()).append('|')
            append(normalized.cacheKey)
        }
        val metadataFile = metadataFileFor(displayFile)
        if (displayFile.isFile && metadataFile.readTextOrNull() == fingerprint) {
            return displayFile
        }

        val decoded = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            },
        ) ?: return null
        val sourceBitmap = if (decoded.isMutable) {
            decoded
        } else {
            val mutableCopy = decoded.copy(Bitmap.Config.ARGB_8888, true)
            decoded.recycle()
            mutableCopy ?: return null
        }
        val temporary = File(
            displayFile.parentFile,
            ".${displayFile.name}.tone-${System.nanoTime()}",
        )

        try {
            sourceBitmap.applyToneProtectionInPlace(normalized)
            FileOutputStream(temporary).use { output ->
                check(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            }
            replaceFile(temporary, displayFile)
            metadataFile.writeText(fingerprint)
            displayFile.setLastModified(System.currentTimeMillis())
            return displayFile
        } finally {
            temporary.delete()
            if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
        }
    }

    private fun metadataFileFor(displayFile: File): File =
        File(displayFile.parentFile, METADATA_FILE_NAME)

    private fun replaceFile(source: File, target: File) {
        val backup = File(target.parentFile, ".${target.name}.backup")
        backup.delete()
        if (target.exists() && !target.renameTo(backup)) {
            source.copyTo(target, overwrite = true)
            return
        }
        if (!source.renameTo(target)) {
            runCatching { source.copyTo(target, overwrite = true) }
                .onFailure {
                    target.delete()
                    backup.renameTo(target)
                    throw it
                }
        }
        backup.delete()
    }

    private fun Bitmap.applyToneProtectionInPlace(tone: NormalizedCustomImageTone) {
        val rowsPerChunk = PROCESSING_ROW_CHUNK.coerceAtMost(height).coerceAtLeast(1)
        val pixels = IntArray(width * rowsPerChunk)
        val start = tone.highlightStart
        val limit = tone.highlightLimit
        val normalizedSpan = ((limit - start) / (1f - start)).coerceIn(0.001f, 1f)
        val shoulderK = 1f / normalizedSpan - 1f
        var top = 0

        while (top < height) {
            val rowCount = minOf(rowsPerChunk, height - top)
            val pixelCount = width * rowCount
            getPixels(pixels, 0, width, 0, top, width, rowCount)

            for (index in 0 until pixelCount) {
                val color = pixels[index]
                val alpha = color ushr 24 and 0xFF
                var red = (color ushr 16 and 0xFF) / 255f * tone.brightness
                var green = (color ushr 8 and 0xFF) / 255f * tone.brightness
                var blue = (color and 0xFF) / 255f * tone.brightness
                val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue

                if (luminance > start) {
                    val t = ((luminance - start) / (1f - start)).coerceIn(0f, 1f)
                    val compressedT = t / (1f + shoulderK * t)
                    val targetLuminance = start + (1f - start) * compressedT
                    val ratio = targetLuminance / max(luminance, 0.0001f)
                    red *= ratio
                    green *= ratio
                    blue *= ratio
                }

                val outRed = (red.coerceIn(0f, 1f) * 255f).roundToInt()
                val outGreen = (green.coerceIn(0f, 1f) * 255f).roundToInt()
                val outBlue = (blue.coerceIn(0f, 1f) * 255f).roundToInt()
                pixels[index] =
                    (alpha shl 24) or (outRed shl 16) or (outGreen shl 8) or outBlue
            }

            setPixels(pixels, 0, width, 0, top, width, rowCount)
            top += rowCount
        }
    }

    private fun BackdropDebugParams.normalizedCustomImageTone(): NormalizedCustomImageTone {
        val start = customImageHighlightStart.coerceIn(0.35f, 0.85f)
        val limit = customImageHighlightLimit.coerceIn(
            (start + MIN_HIGHLIGHT_GAP).coerceAtMost(0.92f),
            0.92f,
        )
        return NormalizedCustomImageTone(
            brightness = customImageBrightness.coerceIn(0.50f, 1.10f),
            highlightStart = start,
            highlightLimit = limit,
        )
    }

    private data class NormalizedCustomImageTone(
        val brightness: Float,
        val highlightStart: Float,
        val highlightLimit: Float,
    ) {
        val cacheKey: String
            get() = listOf(brightness, highlightStart, highlightLimit)
                .joinToString("|") { it.toneRound2().toString() }
    }
}

internal fun BackdropDebugParams.customImageToneCacheKey(): String =
    listOf(customImageBrightness, customImageHighlightStart, customImageHighlightLimit)
        .joinToString("|") { it.toneRound2().toString() }

private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

private fun Float.toneRound2(): Float = (this * 100f).roundToInt() / 100f
