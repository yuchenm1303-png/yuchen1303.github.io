package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 使用三次 box pass 逼近高斯模糊。
 *
 * 旧实现把半径 1/2/4 原样重复 12 次，虽然速度稳定，但对白底细字容易留下规则条纹。
 * 现在把现有 radius + iterations 解释为目标 sigma，再用三组最接近的奇数窗口完成高斯逼近：
 * 通道仍只遍历常数次，成本更低，扩散轮廓也更接近真实磨砂玻璃。
 */
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
    val output = runGaussianApproximation(input, radius, iterations, scratch)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }
}

/**
 * 模糊和色调调整共用同一组像素缓冲，避免每一级再做两次完整 Bitmap 往返。
 * 色调处理改为近似线性光空间，并对高亮区域主动收敛饱和度和对比度，减少网页文字的
 * 黄边、灰条和发白塑料感。
 */
internal fun boxBlurAndTune(
    input: Bitmap,
    radius: Int,
    iterations: Int,
    scratch: BackdropPixelScratch,
    brightness: Float,
    contrast: Float,
    saturation: Float
): Bitmap {
    val width = input.width
    val height = input.height
    val output = if (radius <= 0 || iterations <= 0) {
        input.getPixels(scratch.output, 0, width, 0, 0, width, height)
        scratch.output
    } else {
        runGaussianApproximation(input, radius, iterations, scratch)
    }
    tunePixelsInPlace(
        pixels = output,
        pixelCount = width * height,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation
    )
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun runGaussianApproximation(
    input: Bitmap,
    radius: Int,
    iterations: Int,
    scratch: BackdropPixelScratch
): IntArray {
    val width = input.width
    val height = input.height
    var source = scratch.source
    val temp = scratch.temp
    var output = scratch.output
    input.getPixels(source, 0, width, 0, 0, width, height)

    val sigma = targetSigma(radius, iterations)
    val radii = gaussianBoxRadii(sigma, passCount = 3)
    var pass = 0
    while (pass < radii.size) {
        val passRadius = radii[pass].coerceAtLeast(1)
        val window = passRadius * 2 + 1
        boxBlurHorizontal(source, temp, width, height, passRadius, window)
        boxBlurVertical(temp, output, width, height, passRadius, window)
        if (pass < radii.lastIndex) {
            val reusable = source
            source = output
            output = reusable
        }
        pass += 1
    }
    return output
}

private fun targetSigma(radius: Int, iterations: Int): Float {
    val safeRadius = radius.coerceIn(1, 64).toFloat()
    val safeIterations = iterations.coerceIn(1, 12).toFloat()
    return (safeRadius * (2.20f + safeIterations * 0.42f)).coerceIn(1.2f, 46f)
}

private fun gaussianBoxRadii(sigma: Float, passCount: Int): IntArray {
    val count = passCount.coerceAtLeast(1)
    val sigmaDouble = sigma.toDouble()
    val sigmaSquared = sigmaDouble * sigmaDouble
    val countDouble = count.toDouble()
    val idealWidth = sqrt((12.0 * sigmaSquared / countDouble) + 1.0)
    var lowerWidth = floor(idealWidth).toInt()
    if (lowerWidth % 2 == 0) lowerWidth -= 1
    lowerWidth = lowerWidth.coerceAtLeast(3)
    val upperWidth = lowerWidth + 2
    val numerator = 12.0 * sigmaSquared -
        countDouble * lowerWidth * lowerWidth -
        4.0 * countDouble * lowerWidth -
        3.0 * countDouble
    val denominator = -4.0 * lowerWidth - 4.0
    val lowerCount = (numerator / denominator).roundToInt().coerceIn(0, count)
    return IntArray(count) { index ->
        val width = if (index < lowerCount) lowerWidth else upperWidth
        ((width - 1) / 2).coerceAtLeast(1)
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
    var y = 0
    while (y < height) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        val row = y * width
        var i = -radius
        while (i <= radius) {
            val x = i.coerceIn(0, width - 1)
            val color = source[row + x]
            a += color ushr 24
            r += (color shr 16) and 0xFF
            g += (color shr 8) and 0xFF
            b += color and 0xFF
            i += 1
        }
        var x = 0
        while (x < width) {
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
            x += 1
        }
        y += 1
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
    var x = 0
    while (x < width) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        var i = -radius
        while (i <= radius) {
            val y = i.coerceIn(0, height - 1)
            val color = temp[y * width + x]
            a += color ushr 24
            r += (color shr 16) and 0xFF
            g += (color shr 8) and 0xFF
            b += color and 0xFF
            i += 1
        }
        var y = 0
        while (y < height) {
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
            y += 1
        }
        x += 1
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
    tunePixelsInPlace(pixels, width * height, brightness, contrast, saturation)
    input.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun tunePixelsInPlace(
    pixels: IntArray,
    pixelCount: Int,
    brightness: Float,
    contrast: Float,
    saturation: Float
) {
    val safeBrightness = brightness.coerceIn(0.40f, 2.20f)
    val safeContrast = contrast.coerceIn(0.50f, 1.80f)
    val safeSaturation = saturation.coerceIn(0.30f, 1.80f)
    var index = 0
    while (index < pixelCount) {
        val color = pixels[index]
        val alpha = color ushr 24
        val red = SRGB_TO_LINEAR[(color shr 16) and 0xFF]
        val green = SRGB_TO_LINEAR[(color shr 8) and 0xFF]
        val blue = SRGB_TO_LINEAR[color and 0xFF]
        val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
        val highlight = smoothStep(0.58f, 0.96f, luminance)
        val localSaturation = lerp(safeSaturation, minOf(safeSaturation, 0.92f), highlight * 0.82f)
        val localContrast = 1f + (safeContrast - 1f) * (1f - highlight * 0.58f)

        var tunedRed = luminance + (red - luminance) * localSaturation
        var tunedGreen = luminance + (green - luminance) * localSaturation
        var tunedBlue = luminance + (blue - luminance) * localSaturation
        tunedRed = softHighlight((tunedRed - 0.18f) * localContrast + 0.18f, safeBrightness)
        tunedGreen = softHighlight((tunedGreen - 0.18f) * localContrast + 0.18f, safeBrightness)
        tunedBlue = softHighlight((tunedBlue - 0.18f) * localContrast + 0.18f, safeBrightness)

        val r = linearToSrgbByte(tunedRed)
        val g = linearToSrgbByte(tunedGreen)
        val b = linearToSrgbByte(tunedBlue)
        pixels[index] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        index += 1
    }
}

private fun softHighlight(value: Float, brightness: Float): Float {
    val brightened = (value.coerceAtLeast(0f) * brightness).coerceAtMost(2.2f)
    if (brightened <= 0.82f) return brightened
    val excess = brightened - 0.82f
    return (0.82f + excess / (1f + excess * 3.2f)).coerceIn(0f, 1f)
}

private val SRGB_TO_LINEAR = FloatArray(256) { value ->
    val channel = value / 255f
    if (channel <= 0.04045f) channel / 12.92f
    else ((channel + 0.055f) / 1.055f).pow(2.4f)
}

private val LINEAR_TO_SRGB = IntArray(4097) { index ->
    val channel = index / 4096f
    val srgb = if (channel <= 0.0031308f) channel * 12.92f
    else 1.055f * channel.pow(1f / 2.4f) - 0.055f
    (srgb * 255f).roundToInt().coerceIn(0, 255)
}

private fun linearToSrgbByte(value: Float): Int {
    val index = (value.coerceIn(0f, 1f) * 4096f).roundToInt().coerceIn(0, 4096)
    return LINEAR_TO_SRGB[index]
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerp(start: Float, end: Float, amount: Float): Float {
    val t = amount.coerceIn(0f, 1f)
    return start + (end - start) * t
}
