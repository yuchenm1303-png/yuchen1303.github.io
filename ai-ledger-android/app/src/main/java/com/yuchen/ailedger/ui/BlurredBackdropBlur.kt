package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import kotlin.math.roundToInt

internal fun boxBlur(
    input: Bitmap,
    radius: Int,
    iterations: Int,
    scratch: BackdropPixelScratch
): Bitmap {
    if (radius <= 0 || iterations <= 0) {
        return input.copy(Bitmap.Config.ARGB_8888, true)
    }
    val width = input.width
    val height = input.height
    var source = scratch.source
    val temp = scratch.temp
    var output = scratch.output
    input.getPixels(source, 0, width, 0, 0, width, height)
    val window = radius * 2 + 1
    repeat(iterations) { iteration ->
        boxBlurHorizontal(source, temp, width, height, radius, window)
        boxBlurVertical(temp, output, width, height, radius, window)
        if (iteration < iterations - 1) {
            val reusable = source
            source = output
            output = reusable
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun boxBlurHorizontal(
    source: IntArray,
    temp: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    window: Int
) {
    for (y in 0 until height) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        val row = y * width
        for (i in -radius..radius) {
            val x = i.coerceIn(0, width - 1)
            val color = source[row + x]
            a += color ushr 24
            r += (color shr 16) and 0xFF
            g += (color shr 8) and 0xFF
            b += color and 0xFF
        }
        for (x in 0 until width) {
            temp[row + x] =
                ((a / window) shl 24) or
                    ((r / window) shl 16) or
                    ((g / window) shl 8) or
                    (b / window)
            val remove = source[row + (x - radius).coerceIn(0, width - 1)]
            val add = source[row + (x + radius + 1).coerceIn(0, width - 1)]
            a += (add ushr 24) - (remove ushr 24)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun boxBlurVertical(
    temp: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    window: Int
) {
    for (x in 0 until width) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val y = i.coerceIn(0, height - 1)
            val color = temp[y * width + x]
            a += color ushr 24
            r += (color shr 16) and 0xFF
            g += (color shr 8) and 0xFF
            b += color and 0xFF
        }
        for (y in 0 until height) {
            output[y * width + x] =
                ((a / window) shl 24) or
                    ((r / window) shl 16) or
                    ((g / window) shl 8) or
                    (b / window)
            val remove = temp[(y - radius).coerceIn(0, height - 1) * width + x]
            val add = temp[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            a += (add ushr 24) - (remove ushr 24)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

internal fun tuneBitmapToneInPlace(
    input: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    pixels: IntArray
) {
    val width = input.width
    val height = input.height
    input.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in 0 until width * height) {
        val color = pixels[index]
        val a = color ushr 24
        val r0 = ((color shr 16) and 0xFF).toFloat()
        val g0 = ((color shr 8) and 0xFF).toFloat()
        val b0 = (color and 0xFF).toFloat()
        val gray = r0 * 0.2126f + g0 * 0.7152f + b0 * 0.0722f
        val saturatedR = gray + (r0 - gray) * saturation
        val saturatedG = gray + (g0 - gray) * saturation
        val saturatedB = gray + (b0 - gray) * saturation
        val r = (((saturatedR - 128f) * contrast + 128f) * brightness)
            .roundToInt()
            .coerceIn(0, 255)
        val g = (((saturatedG - 128f) * contrast + 128f) * brightness)
            .roundToInt()
            .coerceIn(0, 255)
        val b = (((saturatedB - 128f) * contrast + 128f) * brightness)
            .roundToInt()
            .coerceIn(0, 255)
        pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    input.setPixels(pixels, 0, width, 0, 0, width, height)
}
