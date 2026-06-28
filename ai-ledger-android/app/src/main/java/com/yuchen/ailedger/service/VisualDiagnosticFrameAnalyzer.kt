package com.yuchen.ailedger.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.security.MessageDigest

internal data class VisualDiagnosticFrameAnalysis(
    val byteSize: Int,
    val sha256: String,
    val differenceHash: String,
)

/** 仅在诊断后台线程分析已存在的 JPEG，不会触发新截图，也不参与视觉决策。 */
internal object VisualDiagnosticFrameAnalyzer {
    fun analyze(bytes: ByteArray): VisualDiagnosticFrameAnalysis {
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        return VisualDiagnosticFrameAnalysis(
            byteSize = bytes.size,
            sha256 = sha,
            differenceHash = computeDifferenceHash(bytes),
        )
    }

    fun hammingDistance(first: String, second: String): Int? {
        if (first.length != 16 || second.length != 16) return null
        val left = runCatching { java.lang.Long.parseUnsignedLong(first, 16) }.getOrNull() ?: return null
        val right = runCatching { java.lang.Long.parseUnsignedLong(second, 16) }.getOrNull() ?: return null
        return java.lang.Long.bitCount(left xor right)
    }

    private fun computeDifferenceHash(bytes: ByteArray): String {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 8
        }
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return ""
        val scaled = runCatching {
            Bitmap.createScaledBitmap(decoded, 9, 8, false)
        }.getOrNull()
        if (scaled == null) {
            decoded.recycle()
            return ""
        }
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit += 1
            }
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return java.lang.Long.toUnsignedString(hash, 16).padStart(16, '0')
    }

    private fun luminance(color: Int): Int {
        val red = (color shr 16) and 0xff
        val green = (color shr 8) and 0xff
        val blue = color and 0xff
        return (red * 299 + green * 587 + blue * 114) / 1000
    }
}
