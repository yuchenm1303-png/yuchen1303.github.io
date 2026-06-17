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
import androidx.compose.ui.graphics.asImageBitmap
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_BLUR_SOURCE_SCALE = 0.36f
private const val MIN_BLUR_SOURCE_SCALE = 0.28f
private const val MAX_BLUR_SOURCE_SCALE = 0.72f

/**
 * 构建清晰镜片纹理和完整的低 / 中 / 高三档模糊金字塔。
 *
 * 三档始终一次性生成，避免普通 Compose 玻璃在亮底区域自适应切换到高档时拿到中档别名。
 * 背景变化时仍只执行一次，随后沿用现有内存与磁盘缓存，不增加滚动期计算。
 */
@Suppress("UNUSED_PARAMETER")
internal fun buildBackdropTextureSet(
    fullWidth: Int,
    fullHeight: Int,
    theme: BackgroundTheme,
    params: BackdropDebugParams,
    customBackgroundPath: String?,
    presetBitmap: Bitmap?,
    blurLevelCount: Int = 3
): BackdropTextureSet {
    val useDefaultWallpaper = customBackgroundPath == null
    val useThemePreset = customBackgroundPath == BUILTIN_THEME_BACKGROUND_PATH
    val useCustomImage = !useDefaultWallpaper && !useThemePreset

    val clearSource = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888)
    val drewCustom = useCustomImage && drawCustomImageBackdropSource(clearSource, customBackgroundPath)
    if (!drewCustom) {
        when {
            useThemePreset -> drawAndroidBackdropSource(clearSource, theme, params)
            useDefaultWallpaper && presetBitmap != null -> drawBitmapCoverIntoTarget(presetBitmap, clearSource)
            else -> clearSource.eraseColor(Color.rgb(0x07, 0x13, 0x2D))
        }
    }

    val blurScale = if (useDefaultWallpaper) {
        DEFAULT_BLUR_SOURCE_SCALE
    } else {
        params.scale.coerceIn(MIN_BLUR_SOURCE_SCALE, MAX_BLUR_SOURCE_SCALE)
    }
    val blurWidth = (fullWidth * blurScale).roundToInt().coerceAtLeast(192)
    val blurHeight = (fullHeight * blurScale).roundToInt().coerceAtLeast(320)
    val effectiveScale = blurWidth.toFloat() / fullWidth.toFloat()
    val blurSource = createPrefilteredBlurSource(clearSource, blurWidth, blurHeight)

    val iterations = params.iterations.roundToInt().coerceIn(1, 12)
    val scratch = BackdropPixelScratch(blurWidth * blurHeight)
    val low = buildTunedBlurLevel(
        source = blurSource,
        radius = 1,
        iterations = iterations,
        params = params,
        scratch = scratch
    )
    Thread.yield()
    val medium = buildTunedBlurLevel(
        source = blurSource,
        radius = 2,
        iterations = iterations,
        params = params,
        scratch = scratch
    )
    val luminanceMap = BackdropLuminanceMap.build(
        source = medium,
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight
    )
    Thread.yield()
    val high = buildTunedBlurLevel(
        source = blurSource,
        radius = 4,
        iterations = iterations,
        params = params,
        scratch = scratch
    )

    if (!blurSource.isRecycled) blurSource.recycle()

    return BackdropTextureSet(
        clearImage = clearSource.asImageBitmap(),
        blurLowImage = low.asImageBitmap(),
        blurMediumImage = medium.asImageBitmap(),
        blurHighImage = high.asImageBitmap(),
        luminanceMap = luminanceMap,
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight,
        blurScale = effectiveScale
    )
}

private fun buildTunedBlurLevel(
    source: Bitmap,
    radius: Int,
    iterations: Int,
    params: BackdropDebugParams,
    scratch: BackdropPixelScratch
): Bitmap = boxBlurAndTune(
    input = source,
    radius = radius,
    iterations = iterations,
    scratch = scratch,
    brightness = params.brightness.coerceIn(0.40f, 2.20f),
    contrast = params.contrast.coerceIn(0.50f, 1.80f),
    saturation = params.saturation.coerceIn(0.30f, 1.80f)
)

/**
 * 大图先逐级减半，再落到最终模糊尺寸。相比一次性双线性缩小，逐级面积近似能先消除
 * 网页细字和 1px 线条的采样混叠，随后高斯模糊不会再把混叠结果放大成规则横条。
 */
private fun createPrefilteredBlurSource(
    source: Bitmap,
    targetWidth: Int,
    targetHeight: Int
): Bitmap {
    var current = source
    var ownsCurrent = false
    while (
        current.width / 2 >= targetWidth &&
        current.height / 2 >= targetHeight
    ) {
        val nextWidth = (current.width / 2).coerceAtLeast(targetWidth)
        val nextHeight = (current.height / 2).coerceAtLeast(targetHeight)
        val next = Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888)
        drawBitmapCoverIntoTarget(current, next)
        if (ownsCurrent && !current.isRecycled) current.recycle()
        current = next
        ownsCurrent = true
    }

    if (current.width == targetWidth && current.height == targetHeight) {
        return current
    }
    val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    drawBitmapCoverIntoTarget(current, result)
    if (ownsCurrent && !current.isRecycled) current.recycle()
    return result
}

private fun drawCustomImageBackdropSource(target: Bitmap, path: String?): Boolean {
    val file = path?.let(::File) ?: return false
    if (!file.isFile) return false
    val source = decodeCustomBitmapForTarget(file, target.width, target.height) ?: return false
    drawBitmapCoverIntoTarget(source, target)
    source.recycle()
    return true
}

private fun decodeCustomBitmapForTarget(
    file: File,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val targetMax = max(targetWidth, targetHeight).coerceAtLeast(1)
    val sourceMax = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (sourceMax / (sample * 2) >= targetMax * 2) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
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
    canvas.drawBitmap(
        source,
        Rect(cropX, cropY, cropX + cropW, cropY + cropH),
        RectF(0f, 0f, dstW.toFloat(), dstH.toFloat()),
        paint
    )
}

private val ANDROID_BACKDROP_CLOUD_Y_FRACTIONS =
    floatArrayOf(0.12f, 0.28f, 0.48f, 0.68f)

private fun drawAndroidBackdropSource(
    bitmap: Bitmap,
    theme: BackgroundTheme,
    params: BackdropDebugParams
) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val palette = androidWeatherPalette(theme)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        h,
        intArrayOf(
            palette.top,
            palette.upper,
            palette.mid,
            palette.horizon,
            palette.bottom
        ),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    drawGlow(canvas, paint, w * 0.82f, h * 0.12f, w * 0.52f, h * 0.26f, palette.violet, 0.38f)
    drawGlow(canvas, paint, w * 0.34f, h * 0.82f, w * 0.70f, h * 0.34f, palette.warm, 0.34f)
    drawGlow(canvas, paint, w * 0.24f, h * 0.44f, w * 0.46f, h * 0.28f, palette.blue, 0.22f)
    val cloudAlpha = params.cloudAlpha.coerceIn(0.25f, 2.2f)
    for (index in ANDROID_BACKDROP_CLOUD_Y_FRACTIONS.indices) {
        val y = ANDROID_BACKDROP_CLOUD_Y_FRACTIONS[index]
        drawGlow(
            canvas,
            paint,
            w * (0.12f + index * 0.24f),
            h * y,
            w * 0.42f,
            h * 0.12f,
            palette.cloudLight,
            0.22f * cloudAlpha
        )
        drawGlow(
            canvas,
            paint,
            w * (0.32f + index * 0.18f),
            h * (y + 0.05f),
            w * 0.36f,
            h * 0.10f,
            palette.cloudWarm,
            0.16f * cloudAlpha
        )
    }
}

private fun drawGlow(
    canvas: Canvas,
    paint: Paint,
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    color: Int,
    alpha: Float
) {
    paint.shader = RadialGradient(
        cx,
        cy,
        max(rx, ry),
        intArrayOf(
            withAlpha(color, alpha),
            withAlpha(color, alpha * 0.28f),
            Color.TRANSPARENT
        ),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    paint.shader = null
}

private data class AndroidWeatherPalette(
    val top: Int,
    val upper: Int,
    val mid: Int,
    val horizon: Int,
    val bottom: Int,
    val violet: Int,
    val warm: Int,
    val blue: Int,
    val cloudLight: Int,
    val cloudWarm: Int
)

private fun androidWeatherPalette(theme: BackgroundTheme): AndroidWeatherPalette = when (theme) {
    BackgroundTheme.Aurora -> AndroidWeatherPalette(
        rgb(0x06, 0x14, 0x26), rgb(0x26, 0x3A, 0x68), rgb(0x59, 0x6B, 0x99),
        rgb(0x8B, 0x71, 0x86), rgb(0xB7, 0x83, 0x69), rgb(0xB7, 0x9A, 0xFF),
        rgb(0xFF, 0xA0, 0x6E), rgb(0x5C, 0xA9, 0xE6), rgb(0xB7, 0xB6, 0xE8),
        rgb(0xD4, 0xA1, 0x9A)
    )
    BackgroundTheme.Jade -> AndroidWeatherPalette(
        rgb(0x07, 0x1A, 0x22), rgb(0x24, 0x46, 0x5F), rgb(0x5E, 0x7E, 0x95),
        rgb(0x83, 0xA3, 0x94), rgb(0xB5, 0x9B, 0x79), rgb(0x8E, 0xC2, 0xDD),
        rgb(0xE8, 0xB3, 0x7F), rgb(0x58, 0xC0, 0xBC), rgb(0xAE, 0xC7, 0xD8),
        rgb(0xC7, 0xAE, 0x92)
    )
    BackgroundTheme.Sunset -> AndroidWeatherPalette(
        rgb(0x20, 0x18, 0x2D), rgb(0x49, 0x36, 0x5E), rgb(0x73, 0x5C, 0x83),
        rgb(0xA8, 0x75, 0x86), rgb(0xD1, 0x97, 0x6B), rgb(0xC0, 0x98, 0xFF),
        rgb(0xFF, 0x9A, 0x64), rgb(0x75, 0x87, 0xD5), rgb(0xC6, 0xB3, 0xE6),
        rgb(0xE0, 0xA1, 0x8D)
    )
    BackgroundTheme.Dawn -> AndroidWeatherPalette(
        rgb(0x16, 0x25, 0x3C), rgb(0x52, 0x6A, 0x91), rgb(0x89, 0xA5, 0xBE),
        rgb(0xC1, 0xA6, 0xA4), rgb(0xD8, 0xB2, 0x87), rgb(0xE2, 0xCC, 0xFF),
        rgb(0xFF, 0xC2, 0x8A), rgb(0x9E, 0xD4, 0xFF), rgb(0xD7, 0xD6, 0xF0),
        rgb(0xE2, 0xC0, 0xA6)
    )
}

private fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)

private fun withAlpha(color: Int, alpha: Float): Int =
    ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or
        (color and 0x00FFFFFF)
