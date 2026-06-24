package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.io.DataInput
import java.io.DataOutput
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

private const val LUMINANCE_GRID_COLUMNS = 64
private const val LUMINANCE_GRID_MIN_ROWS = 64
private const val LUMINANCE_GRID_MAX_ROWS = 160
private const val INVALID_LUMINANCE_REGION = Long.MIN_VALUE
private const val LUMINANCE_CACHE_MAGIC = 0x424C4D31
private const val LUMINANCE_CACHE_VERSION = 1
private const val MAX_LUMINANCE_CACHE_DIMENSION = 512
private const val MAX_LUMINANCE_CACHE_VALUES = 263169

/**
 * 背景亮度积分表。
 *
 * 纹理构建时只生成一次，并随背景纹理一起持久化。运行时查询任意玻璃区域只需要
 * 四次数组读取，不再通过 Bitmap.getPixel() 跨 JNI 逐点采样，也不会在滚动帧创建
 * 临时像素对象；磁盘缓存命中时也无需重新缩放 Bitmap 和重建积分表。
 */
class BackdropLuminanceMap private constructor(
    private val columns: Int,
    private val rows: Int,
    private val fullWidthPx: Int,
    private val fullHeightPx: Int,
    private val integral: FloatArray
) {
    private val stride = columns + 1

    fun regionKey(sampleOffset: Offset, sampleSize: Size): Long {
        val rootWidth = fullWidthPx.toFloat().coerceAtLeast(1f)
        val rootHeight = fullHeightPx.toFloat().coerceAtLeast(1f)
        val left = sampleOffset.x.coerceAtLeast(0f)
        val top = sampleOffset.y.coerceAtLeast(0f)
        val right = (sampleOffset.x + sampleSize.width).coerceAtMost(rootWidth)
        val bottom = (sampleOffset.y + sampleSize.height).coerceAtMost(rootHeight)
        if (right <= left || bottom <= top) return INVALID_LUMINANCE_REGION

        val x0 = floor(left / rootWidth * columns).toInt().coerceIn(0, columns - 1)
        val y0 = floor(top / rootHeight * rows).toInt().coerceIn(0, rows - 1)
        val x1 = ceil(right / rootWidth * columns).toInt().coerceIn(x0 + 1, columns)
        val y1 = ceil(bottom / rootHeight * rows).toInt().coerceIn(y0 + 1, rows)
        return packRegion(x0, y0, x1, y1)
    }

    fun averageLuminance(regionKey: Long): Float {
        if (regionKey == INVALID_LUMINANCE_REGION) return 0.5f
        val x0 = (regionKey and 0xFFFFL).toInt()
        val y0 = ((regionKey ushr 16) and 0xFFFFL).toInt()
        val x1 = ((regionKey ushr 32) and 0xFFFFL).toInt()
        val y1 = ((regionKey ushr 48) and 0xFFFFL).toInt()
        val area = (x1 - x0) * (y1 - y0)
        if (area <= 0) return 0.5f

        val bottomRight = integral[y1 * stride + x1]
        val topRight = integral[y0 * stride + x1]
        val bottomLeft = integral[y1 * stride + x0]
        val topLeft = integral[y0 * stride + x0]
        return ((bottomRight - topRight - bottomLeft + topLeft) / area)
            .coerceIn(0f, 1f)
    }

    internal fun writeTo(output: DataOutput) {
        output.writeInt(LUMINANCE_CACHE_MAGIC)
        output.writeInt(LUMINANCE_CACHE_VERSION)
        output.writeInt(columns)
        output.writeInt(rows)
        output.writeInt(fullWidthPx)
        output.writeInt(fullHeightPx)
        output.writeInt(integral.size)
        integral.forEach(output::writeFloat)
    }

    companion object {
        val Neutral = BackdropLuminanceMap(
            columns = 1,
            rows = 1,
            fullWidthPx = 1,
            fullHeightPx = 1,
            integral = floatArrayOf(0f, 0f, 0f, 0.5f)
        )

        fun build(source: Bitmap, fullWidthPx: Int, fullHeightPx: Int): BackdropLuminanceMap {
            val safeWidth = fullWidthPx.coerceAtLeast(1)
            val safeHeight = fullHeightPx.coerceAtLeast(1)
            val columns = LUMINANCE_GRID_COLUMNS
            val rows = (columns * safeHeight.toFloat() / safeWidth.toFloat())
                .roundToInt()
                .coerceIn(LUMINANCE_GRID_MIN_ROWS, LUMINANCE_GRID_MAX_ROWS)
            val scaled = Bitmap.createScaledBitmap(source, columns, rows, true)
            val pixels = IntArray(columns * rows)
            scaled.getPixels(pixels, 0, columns, 0, 0, columns, rows)
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()

            val stride = columns + 1
            val integral = FloatArray(stride * (rows + 1))
            var y = 1
            while (y <= rows) {
                var rowSum = 0f
                var x = 1
                val sourceRow = (y - 1) * columns
                val integralRow = y * stride
                val previousRow = (y - 1) * stride
                while (x <= columns) {
                    rowSum += linearLuminance(pixels[sourceRow + x - 1])
                    integral[integralRow + x] = integral[previousRow + x] + rowSum
                    x += 1
                }
                y += 1
            }
            return BackdropLuminanceMap(
                columns = columns,
                rows = rows,
                fullWidthPx = safeWidth,
                fullHeightPx = safeHeight,
                integral = integral
            )
        }

        internal fun readFrom(input: DataInput): BackdropLuminanceMap? = runCatching {
            require(input.readInt() == LUMINANCE_CACHE_MAGIC)
            require(input.readInt() == LUMINANCE_CACHE_VERSION)
            val columns = input.readInt()
            val rows = input.readInt()
            val fullWidthPx = input.readInt()
            val fullHeightPx = input.readInt()
            val valueCount = input.readInt()
            require(columns in 1..MAX_LUMINANCE_CACHE_DIMENSION)
            require(rows in 1..MAX_LUMINANCE_CACHE_DIMENSION)
            require(fullWidthPx > 0 && fullHeightPx > 0)
            val expectedValueCount = (columns + 1) * (rows + 1)
            require(valueCount == expectedValueCount)
            require(valueCount in 4..MAX_LUMINANCE_CACHE_VALUES)
            val integral = FloatArray(valueCount) { input.readFloat() }
            require(integral.all(Float::isFinite))
            BackdropLuminanceMap(
                columns = columns,
                rows = rows,
                fullWidthPx = fullWidthPx,
                fullHeightPx = fullHeightPx,
                integral = integral
            )
        }.getOrNull()
    }
}

private fun packRegion(x0: Int, y0: Int, x1: Int, y1: Int): Long =
    (x0.toLong() and 0xFFFFL) or
        ((y0.toLong() and 0xFFFFL) shl 16) or
        ((x1.toLong() and 0xFFFFL) shl 32) or
        ((y1.toLong() and 0xFFFFL) shl 48)

private val LUMINANCE_SRGB_TO_LINEAR = FloatArray(256) { value ->
    val channel = value / 255f
    if (channel <= 0.04045f) channel / 12.92f
    else ((channel + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearLuminance(color: Int): Float {
    val red = LUMINANCE_SRGB_TO_LINEAR[(color shr 16) and 0xFF]
    val green = LUMINANCE_SRGB_TO_LINEAR[(color shr 8) and 0xFF]
    val blue = LUMINANCE_SRGB_TO_LINEAR[color and 0xFF]
    return red * 0.2126f + green * 0.7152f + blue * 0.0722f
}
