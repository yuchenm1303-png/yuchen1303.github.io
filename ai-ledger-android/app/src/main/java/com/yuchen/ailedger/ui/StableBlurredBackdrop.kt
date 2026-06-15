package com.yuchen.ailedger.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BUILTIN_THEME_BACKGROUND_PATH
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Keeps Shell glass on the same rendering branch from the first composition.
 *
 * Image-backed sources use a two-by-two neutral texture until their real textures are available.
 * The generated theme placeholder is created only when the user has explicitly selected the built-in
 * theme source. This preserves the stable OpenGL Shell mount without drawing or uploading an unused
 * theme wallpaper during normal startup.
 */
@Composable
fun rememberStableBlurredBackdropBitmap(
    theme: BackgroundTheme,
    quality: RenderQuality,
    params: BackdropDebugParams = BackdropDebugParams(),
    customBackgroundPath: String? = null
): BlurredBackdropBitmap {
    val source = remember(customBackgroundPath) { resolveBackdropSource(customBackgroundPath) }
    val sourcePath = when (source.kind) {
        BackdropSourceKind.DefaultWallpaper -> null
        BackdropSourceKind.BuiltInTheme -> BUILTIN_THEME_BACKGROUND_PATH
        BackdropSourceKind.CustomImage -> source.customImagePath
    }
    val realBackdrop = rememberBlurredBackdropBitmap(
        theme = theme,
        quality = quality,
        params = params,
        customBackgroundPath = sourcePath
    )

    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val fallbackWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val fullWidth = max(view.width, fallbackWidth).coerceAtLeast(320)
    val fullHeight = max(view.height, fallbackHeight).coerceAtLeast(640)
    val relevantTheme = theme.takeIf { source.kind == BackdropSourceKind.BuiltInTheme }

    val placeholder = remember(fullWidth, fullHeight, source.kind, relevantTheme) {
        if (source.kind == BackdropSourceKind.BuiltInTheme) {
            buildThemeBackdropPlaceholder(fullWidth, fullHeight, theme)
        } else {
            buildImageBackdropPlaceholder(fullWidth, fullHeight)
        }
    }
    return realBackdrop ?: placeholder
}

/** Minimal non-theme texture shared by all four OpenGL sampler slots during image decoding. */
private fun buildImageBackdropPlaceholder(
    fullWidth: Int,
    fullHeight: Int
): BlurredBackdropBitmap {
    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(0x07, 0x13, 0x2D))
    }
    val image = bitmap.asImageBitmap()
    return BlurredBackdropBitmap(
        image = image,
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight,
        scale = 2f / fullWidth.toFloat(),
        lensImage = image,
        blurLowImage = image,
        blurMediumImage = image,
        blurHighImage = image
    )
}

private fun buildThemeBackdropPlaceholder(
    fullWidth: Int,
    fullHeight: Int,
    theme: BackgroundTheme
): BlurredBackdropBitmap {
    val smallWidth = (fullWidth * 0.055f).roundToInt().coerceIn(24, 72)
    val smallHeight = (fullHeight * 0.055f).roundToInt().coerceIn(40, 128)
    val bitmap = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    val palette = stableBackdropPalette(theme)
    val w = smallWidth.toFloat()
    val h = smallHeight.toFloat()

    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        h,
        intArrayOf(palette.top, palette.middle, palette.bottom),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null

    drawStableGlow(canvas, paint, w * 0.78f, h * 0.15f, w * 0.58f, h * 0.34f, palette.coldGlow, 0.34f)
    drawStableGlow(canvas, paint, w * 0.28f, h * 0.82f, w * 0.72f, h * 0.28f, palette.warmGlow, 0.28f)
    drawStableGlow(canvas, paint, w * 0.30f, h * 0.45f, w * 0.46f, h * 0.18f, palette.cloudGlow, 0.18f)

    val image = bitmap.asImageBitmap()
    return BlurredBackdropBitmap(
        image = image,
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight,
        scale = smallWidth.toFloat() / fullWidth.toFloat(),
        lensImage = image,
        blurLowImage = image,
        blurMediumImage = image,
        blurHighImage = image
    )
}

private fun drawStableGlow(
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
        intArrayOf(stableAlpha(color, alpha), stableAlpha(color, alpha * 0.28f), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    paint.shader = null
}

private data class StableBackdropPalette(
    val top: Int,
    val middle: Int,
    val bottom: Int,
    val coldGlow: Int,
    val warmGlow: Int,
    val cloudGlow: Int
)

private fun stableBackdropPalette(theme: BackgroundTheme): StableBackdropPalette = when (theme) {
    BackgroundTheme.Aurora -> StableBackdropPalette(
        rgbStable(0x06, 0x14, 0x26), rgbStable(0x59, 0x6B, 0x99), rgbStable(0xB7, 0x83, 0x69),
        rgbStable(0xB7, 0x9A, 0xFF), rgbStable(0xFF, 0xA0, 0x6E), rgbStable(0xB7, 0xB6, 0xE8)
    )
    BackgroundTheme.Jade -> StableBackdropPalette(
        rgbStable(0x07, 0x1A, 0x22), rgbStable(0x5E, 0x7E, 0x95), rgbStable(0xB5, 0x9B, 0x79),
        rgbStable(0x8E, 0xC2, 0xDD), rgbStable(0xE8, 0xB3, 0x7F), rgbStable(0xAE, 0xC7, 0xD8)
    )
    BackgroundTheme.Sunset -> StableBackdropPalette(
        rgbStable(0x20, 0x18, 0x2D), rgbStable(0x73, 0x5C, 0x83), rgbStable(0xD1, 0x97, 0x6B),
        rgbStable(0xC0, 0x98, 0xFF), rgbStable(0xFF, 0x9A, 0x64), rgbStable(0xC6, 0xB3, 0xE6)
    )
    BackgroundTheme.Dawn -> StableBackdropPalette(
        rgbStable(0x16, 0x25, 0x3C), rgbStable(0x89, 0xA5, 0xBE), rgbStable(0xD8, 0xB2, 0x87),
        rgbStable(0xE2, 0xCC, 0xFF), rgbStable(0xFF, 0xC2, 0x8A), rgbStable(0xD7, 0xD6, 0xF0)
    )
}

private fun rgbStable(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
private fun stableAlpha(color: Int, alpha: Float): Int =
    ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (color and 0x00FFFFFF)
