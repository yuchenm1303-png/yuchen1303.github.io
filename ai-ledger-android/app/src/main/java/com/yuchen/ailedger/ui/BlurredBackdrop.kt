package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class BlurredBackdropBitmap(
    val image: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val scale: Float,
    val lensImage: ImageBitmap = image
)

val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdropBitmap?> { null }

@Composable
fun rememberBlurredBackdropBitmap(
    theme: BackgroundTheme,
    quality: RenderQuality,
    params: BackdropDebugParams = BackdropDebugParams(),
    customBackgroundPath: String? = null
): BlurredBackdropBitmap? {
    val view = LocalView.current
    val context = view.context
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val fallbackWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val width = max(view.width, fallbackWidth).coerceAtLeast(320)
    val height = max(view.height, fallbackHeight).coerceAtLeast(640)
    val key = params.cacheKey()
    val customKey = when (customBackgroundPath) {
        null -> "default_wallpaper_lowres"
        BUILTIN_THEME_BACKGROUND_PATH -> "theme:${theme.storageValue}"
        else -> {
            val file = File(customBackgroundPath)
            if (file.exists()) "${file.absolutePath}:${file.lastModified()}:${file.length()}" else "missing:$customBackgroundPath"
        }
    }
    var bitmap by remember(width, height, theme, quality, customKey) { mutableStateOf<BlurredBackdropBitmap?>(null) }

    LaunchedEffect(width, height, theme, quality, key, customKey) {
        if (bitmap != null) delay(120)
        val next = withContext(Dispatchers.Default) {
            runCatching {
                buildBlurredBackdropBitmap(
                    fullWidth = width,
                    fullHeight = height,
                    theme = theme,
                    params = params.quantized(),
                    customBackgroundPath = customBackgroundPath,
                    presetBitmap = decodePresetNightSkyBitmap(context)
                )
            }.getOrNull()
        }
        if (next != null) bitmap = next
    }
    return bitmap
}

private fun BackdropDebugParams.cacheKey(): String = buildString {
    append(scale.round2()).append('|')
    append(radius.roundToInt()).append('|')
    append(iterations.roundToInt()).append('|')
    append(brightness.round2()).append('|')
    append(contrast.round2()).append('|')
    append(saturation.round2()).append('|')
    append(cloudAlpha.round2()).append('|')
    append(cloudSoftness.round2()).append('|')
    append(cloudStretchX.round2()).append('|')
    append(cloudStretchY.round2()).append('|')
    append(cloudHighlightAlpha.round2()).append('|')
    append(moonScale.round2()).append('|')
    append(moonHaloAlpha.round2()).append('|')
    append(moonRimAlpha.round2())
}

private fun BackdropDebugParams.quantized(): BackdropDebugParams = copy(
    scale = scale.round2(),
    radius = radius.roundToInt().toFloat(),
    iterations = iterations.roundToInt().toFloat(),
    brightness = brightness.round2(),
    contrast = contrast.round2(),
    saturation = saturation.round2(),
    cloudAlpha = cloudAlpha.round2(),
    cloudSoftness = cloudSoftness.round2(),
    cloudStretchX = cloudStretchX.round2(),
    cloudStretchY = cloudStretchY.round2(),
    cloudHighlightAlpha = cloudHighlightAlpha.round2(),
    moonScale = moonScale.round2(),
    moonHaloAlpha = moonHaloAlpha.round2(),
    moonRimAlpha = moonRimAlpha.round2()
)

private fun Float.round2(): Float = (this * 100f).roundToInt() / 100f

private fun buildBlurredBackdropBitmap(
    fullWidth: Int,
    fullHeight: Int,
    theme: BackgroundTheme,
    params: BackdropDebugParams,
    customBackgroundPath: String?,
    presetBitmap: Bitmap?
): BlurredBackdropBitmap {
    val useDefaultWallpaper = customBackgroundPath == null
    val useThemePreset = customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH
    val sourceScale = if (useDefaultWallpaper) 0.24f else params.scale.coerceIn(0.18f, 0.72f)
    val smallWidth = (fullWidth * sourceScale).roundToInt().coerceAtLeast(128)
    val smallHeight = (fullHeight * sourceScale).roundToInt().coerceAtLeast(216)
    val effectiveScale = smallWidth.toFloat() / fullWidth.toFloat()

    val source = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
    val drewCustom = if (useThemePreset) false else drawCustomImageBackdropSource(source, customBackgroundPath)
    if (!drewCustom) {
        if (useThemePreset) drawAndroidBackdropSource(source, theme, params)
        else if (presetBitmap != null) drawBitmapCoverIntoTarget(presetBitmap, source)
        else drawAndroidBackdropSource(source, theme, params)
    }
    presetBitmap?.recycle()

    val lensTuned = tuneBitmapTone(
        input = source,
        brightness = params.brightness.coerceIn(0.70f, 1.35f),
        contrast = params.contrast.coerceIn(0.70f, 1.35f),
        saturation = params.saturation.coerceIn(0.50f, 1.60f)
    )

    val blurRadius = if (useDefaultWallpaper) params.radius.roundToInt().coerceIn(1, 18) else params.radius.roundToInt().coerceIn(1, 32)
    val blurIterations = if (useDefaultWallpaper) params.iterations.roundToInt().coerceIn(1, 3) else params.iterations.roundToInt().coerceIn(1, 8)
    val blurred = boxBlur(input = source, radius = blurRadius, iterations = blurIterations)
    val tuned = tuneBitmapTone(
        input = blurred,
        brightness = params.brightness.coerceIn(0.70f, 1.35f),
        contrast = params.contrast.coerceIn(0.70f, 1.35f),
        saturation = params.saturation.coerceIn(0.50f, 1.60f)
    )

    return BlurredBackdropBitmap(
        image = tuned.asImageBitmap(),
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight,
        scale = effectiveScale,
        lensImage = lensTuned.asImageBitmap()
    )
}

private fun drawCustomImageBackdropSource(target: Bitmap, path: String?): Boolean {
    val file = path?.let(::File) ?: return false
    if (!file.exists()) return false
    val source = BitmapFactory.decodeFile(file.absolutePath) ?: return false
    drawBitmapCoverIntoTarget(source, target)
    source.recycle()
    return true
}

private fun drawBitmapCoverIntoTarget(source: Bitmap, target: Bitmap) {
    val canvas = Canvas(target)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    val srcW = source.width
    val srcH = source.height
    val dstW = target.width
    val dstH = target.height
    val dstAspect = dstW.toFloat() / dstH.toFloat()
    val srcAspect = srcW.toFloat() / srcH.toFloat()
    val cropW: Int
    val cropH: Int
    val cropX: Int
    val cropY: Int
    if (srcAspect > dstAspect) {
        cropH = srcH
        cropW = (srcH * dstAspect).roundToInt().coerceIn(1, srcW)
        cropX = ((srcW - cropW) / 2f).roundToInt().coerceAtLeast(0)
        cropY = 0
    } else {
        cropW = srcW
        cropH = (srcW / dstAspect).roundToInt().coerceIn(1, srcH)
        cropX = 0
        cropY = ((srcH - cropH) / 2f).roundToInt().coerceAtLeast(0)
    }
    canvas.drawBitmap(source, Rect(cropX, cropY, cropX + cropW, cropY + cropH), RectF(0f, 0f, dstW.toFloat(), dstH.toFloat()), paint)
}

private fun drawAndroidBackdropSource(bitmap: Bitmap, theme: BackgroundTheme, params: BackdropDebugParams) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val p = androidWeatherPalette(theme)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(p.top, p.upper, p.mid, p.horizon, p.bottom), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null

    drawAndroidGlow(canvas, paint, w * 0.82f, h * 0.12f, w * 0.52f, h * 0.26f, p.violet, 0.38f)
    drawAndroidGlow(canvas, paint, w * 0.34f, h * 0.82f, w * 0.70f, h * 0.34f, p.warm, 0.34f)
    drawAndroidGlow(canvas, paint, w * 0.24f, h * 0.44f, w * 0.46f, h * 0.28f, p.blue, 0.22f)

    val cloudAlpha = params.cloudAlpha.coerceIn(0.25f, 2.2f)
    drawAndroidCloudBand(canvas, paint, w, h, 0.10f, 0.18f, p.cloudLight, 0.40f * cloudAlpha, -0.12f, params)
    drawAndroidCloudBand(canvas, paint, w, h, 0.22f, 0.21f, p.cloudBlue, 0.38f * cloudAlpha, 0.06f, params)
    drawAndroidCloudBand(canvas, paint, w, h, 0.44f, 0.27f, p.cloudWarm, 0.34f * cloudAlpha, -0.04f, params)
    drawAndroidCloudBand(canvas, paint, w, h, 0.66f, 0.26f, p.cloudRose, 0.28f * cloudAlpha, 0.14f, params)

    drawAndroidStars(canvas, paint, w, h)
    drawAndroidCrescent(canvas, paint, w, h, p, params)

    paint.shader = LinearGradient(0f, h * 0.60f, 0f, h, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(Color.rgb(0x07, 0x0B, 0x18), 0.16f)), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
}

private fun drawAndroidGlow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
    paint.shader = RadialGradient(cx, cy, max(rx, ry), intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha * 0.28f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    paint.shader = null
}

private fun drawAndroidCloudBand(canvas: Canvas, paint: Paint, w: Float, h: Float, y: Float, bandHeight: Float, color: Int, alpha: Float, drift: Float, params: BackdropDebugParams) {
    val cy = h * y
    val bh = h * bandHeight
    val positions = floatArrayOf(-0.10f, 0.08f, 0.24f, 0.41f, 0.60f, 0.78f, 0.96f, 1.12f)
    positions.forEachIndexed { index, x ->
        val centerX = w * (x + drift)
        val centerY = cy + bh * if (index % 2 == 0) 0.09f else -0.05f
        val blockW = w * (0.18f + (index % 3) * 0.035f) * params.cloudStretchX.coerceIn(0.8f, 3.8f)
        val blockH = bh * (0.38f + (index % 2) * 0.10f) * params.cloudStretchY.coerceIn(0.35f, 1.4f)
        drawAndroidCloudBlob(canvas, paint, centerX, centerY, blockW, blockH, color, alpha * (0.78f + (index % 4) * 0.055f), params)
    }
}

private fun drawAndroidCloudBlob(canvas: Canvas, paint: Paint, cx: Float, cy: Float, width: Float, height: Float, color: Int, alpha: Float, params: BackdropDebugParams) {
    val softness = params.cloudSoftness.coerceIn(0.65f, 2.4f)
    drawAndroidGlow(canvas, paint, cx, cy, width * 0.62f * softness, height * 0.56f, color, alpha * 0.42f)
    drawAndroidGlow(canvas, paint, cx, cy - height * 0.04f, width * 0.48f * softness, height * 0.42f, color, alpha)
    drawAndroidGlow(canvas, paint, cx, cy - height * 0.25f, width * 0.30f, height * 0.14f, Color.WHITE, alpha * params.cloudHighlightAlpha.coerceIn(0f, 0.8f))
}

private fun drawAndroidStars(canvas: Canvas, paint: Paint, w: Float, h: Float) {
    val points = arrayOf(0.12f to 0.17f, 0.21f to 0.10f, 0.31f to 0.19f, 0.47f to 0.12f, 0.63f to 0.16f, 0.74f to 0.09f, 0.88f to 0.20f, 0.18f to 0.30f, 0.52f to 0.27f, 0.82f to 0.34f, 0.70f to 0.43f)
    val radius = min(w, h) * 0.0028f
    points.forEachIndexed { index, point ->
        paint.shader = null
        paint.color = withAlpha(Color.WHITE, 0.18f + (index % 3) * 0.08f)
        canvas.drawCircle(w * point.first, h * point.second, radius * (0.72f + (index % 4) * 0.18f), paint)
    }
}

private fun drawAndroidCrescent(canvas: Canvas, paint: Paint, w: Float, h: Float, p: AndroidWeatherPalette, params: BackdropDebugParams) {
    val radius = min(w, h) * 0.024f * params.moonScale.coerceIn(0.45f, 1.8f)
    val cx = w * 0.82f
    val cy = h * 0.21f
    drawAndroidGlow(canvas, paint, cx, cy, radius * 2.35f, radius * 2.35f, Color.rgb(0xFF, 0xF3, 0xD6), params.moonHaloAlpha.coerceIn(0f, 0.8f))
    paint.shader = null
    paint.color = withAlpha(Color.rgb(0xFF, 0xF3, 0xD6), 0.62f)
    canvas.drawCircle(cx, cy, radius, paint)
    paint.color = withAlpha(p.upper, 0.97f)
    canvas.drawCircle(cx + radius * 0.46f, cy - radius * 0.12f, radius * 1.05f, paint)
    paint.color = withAlpha(Color.WHITE, params.moonRimAlpha.coerceIn(0f, 1f))
    canvas.drawCircle(cx - radius * 0.42f, cy + radius * 0.02f, radius * 0.20f, paint)
}

private fun boxBlur(input: Bitmap, radius: Int, iterations: Int): Bitmap {
    if (radius <= 0 || iterations <= 0) return input
    var current = input.copy(Bitmap.Config.ARGB_8888, false)
    repeat(iterations) { current = boxBlurOnce(current, radius) }
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

private fun tuneBitmapTone(input: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
    val width = input.width
    val height = input.height
    val pixels = IntArray(width * height)
    val output = IntArray(width * height)
    input.getPixels(pixels, 0, width, 0, 0, width, height)
    pixels.forEachIndexed { index, color ->
        val a = color ushr 24
        val r0 = ((color shr 16) and 0xFF).toFloat()
        val g0 = ((color shr 8) and 0xFF).toFloat()
        val b0 = (color and 0xFF).toFloat()
        val gray = r0 * 0.2126f + g0 * 0.7152f + b0 * 0.0722f
        val sr = gray + (r0 - gray) * saturation
        val sg = gray + (g0 - gray) * saturation
        val sb = gray + (b0 - gray) * saturation
        val r = (((sr - 128f) * contrast + 128f) * brightness).roundToInt().coerceIn(0, 255)
        val g = (((sg - 128f) * contrast + 128f) * brightness).roundToInt().coerceIn(0, 255)
        val b = (((sb - 128f) * contrast + 128f) * brightness).roundToInt().coerceIn(0, 255)
        output[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

private data class AndroidWeatherPalette(val top: Int, val upper: Int, val mid: Int, val horizon: Int, val bottom: Int, val violet: Int, val warm: Int, val blue: Int, val cloudLight: Int, val cloudBlue: Int, val cloudWarm: Int, val cloudRose: Int)

private fun androidWeatherPalette(theme: BackgroundTheme): AndroidWeatherPalette = when (theme) {
    BackgroundTheme.Aurora -> AndroidWeatherPalette(rgb(0x06,0x14,0x26), rgb(0x26,0x3A,0x68), rgb(0x59,0x6B,0x99), rgb(0x8B,0x71,0x86), rgb(0xB7,0x83,0x69), rgb(0xB7,0x9A,0xFF), rgb(0xFF,0xA0,0x6E), rgb(0x5C,0xA9,0xE6), rgb(0xB7,0xB6,0xE8), rgb(0x88,0xA7,0xCE), rgb(0xD4,0xA1,0x9A), rgb(0xC0,0x81,0x94))
    BackgroundTheme.Jade -> AndroidWeatherPalette(rgb(0x07,0x1A,0x22), rgb(0x24,0x46,0x5F), rgb(0x5E,0x7E,0x95), rgb(0x83,0xA3,0x94), rgb(0xB5,0x9B,0x79), rgb(0x8E,0xC2,0xDD), rgb(0xE8,0xB3,0x7F), rgb(0x58,0xC0,0xBC), rgb(0xAE,0xC7,0xD8), rgb(0x80,0xAF,0xC1), rgb(0xC7,0xAE,0x92), rgb(0xA6,0x8F,0x97))
    BackgroundTheme.Sunset -> AndroidWeatherPalette(rgb(0x20,0x18,0x2D), rgb(0x49,0x36,0x5E), rgb(0x73,0x5C,0x83), rgb(0xA8,0x75,0x86), rgb(0xD1,0x97,0x6B), rgb(0xC0,0x98,0xFF), rgb(0xFF,0x9A,0x64), rgb(0x75,0x87,0xD5), rgb(0xC6,0xB3,0xE6), rgb(0x9C,0xA2,0xC8), rgb(0xE0,0xA1,0x8D), rgb(0xD0,0x80,0x9A))
    BackgroundTheme.Dawn -> AndroidWeatherPalette(rgb(0x16,0x25,0x3C), rgb(0x52,0x6A,0x91), rgb(0x89,0xA5,0xBE), rgb(0xC1,0xA6,0xA4), rgb(0xD8,0xB2,0x87), rgb(0xE2,0xCC,0xFF), rgb(0xFF,0xC2,0x8A), rgb(0x9E,0xD4,0xFF), rgb(0xD7,0xD6,0xF0), rgb(0xAA,0xC5,0xDA), rgb(0xE2,0xC0,0xA6), rgb(0xD5,0xA0,0xAD))
}

private fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
private fun withAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0x00FFFFFF)