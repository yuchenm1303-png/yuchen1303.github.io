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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class BlurredBackdropBitmap(
    val image: ImageBitmap,
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val scale: Float
)

val LocalBlurredBackdrop = compositionLocalOf<BlurredBackdropBitmap?> { null }

@Composable
fun rememberBlurredBackdropBitmap(
    theme: BackgroundTheme,
    quality: RenderQuality
): BlurredBackdropBitmap? {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val fallbackWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val width = max(view.width, fallbackWidth).coerceAtLeast(320)
    val height = max(view.height, fallbackHeight).coerceAtLeast(640)

    return remember(width, height, theme, quality) {
        runCatching { buildBlurredBackdropBitmap(width, height, theme, quality) }.getOrNull()
    }
}

private data class BackdropTuning(
    val scale: Float,
    val radius: Int,
    val iterations: Int,
    val spread: Float,
    val iconAlpha: Float,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
)

private fun tuningFor(quality: RenderQuality): BackdropTuning {
    return when (quality) {
        RenderQuality.Smooth -> BackdropTuning(
            scale = 0.420f,
            radius = 4,
            iterations = 4,
            spread = 1.02f,
            iconAlpha = 1.02f,
            brightness = 1.08f,
            contrast = 1.06f,
            saturation = 1.06f
        )
        RenderQuality.Balanced -> BackdropTuning(
            scale = 0.600f,
            radius = 4,
            iterations = 5,
            spread = 1.03f,
            iconAlpha = 1.10f,
            brightness = 1.12f,
            contrast = 1.11f,
            saturation = 1.12f
        )
        RenderQuality.Experimental -> BackdropTuning(
            scale = 0.600f,
            radius = 5,
            iterations = 5,
            spread = 1.06f,
            iconAlpha = 1.12f,
            brightness = 1.13f,
            contrast = 1.12f,
            saturation = 1.13f
        )
    }
}

private fun buildBlurredBackdropBitmap(
    fullWidth: Int,
    fullHeight: Int,
    theme: BackgroundTheme,
    quality: RenderQuality
): BlurredBackdropBitmap {
    val tuning = tuningFor(quality)
    val smallWidth = (fullWidth * tuning.scale).roundToInt().coerceAtLeast(128)
    val smallHeight = (fullHeight * tuning.scale).roundToInt().coerceAtLeast(216)
    val effectiveScale = smallWidth.toFloat() / fullWidth.toFloat()

    val source = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
    drawAndroidBackdropSource(
        bitmap = source,
        theme = theme,
        spread = tuning.spread,
        iconAlpha = tuning.iconAlpha
    )

    val blurred = boxBlur(source, tuning.radius, tuning.iterations)
    val tuned = tuneBitmapTone(
        input = blurred,
        brightness = tuning.brightness,
        contrast = tuning.contrast,
        saturation = tuning.saturation
    )

    return BlurredBackdropBitmap(
        image = tuned.asImageBitmap(),
        fullWidthPx = fullWidth,
        fullHeightPx = fullHeight,
        scale = effectiveScale
    )
}

private fun drawAndroidBackdropSource(
    bitmap: Bitmap,
    theme: BackgroundTheme,
    spread: Float,
    iconAlpha: Float
) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val palette = androidPalette(theme)
    val icon = min(w * 0.145f, h * 0.068f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        w * 0.08f,
        0f,
        w * 0.92f,
        h,
        intArrayOf(palette.deep, palette.mid, palette.glow, palette.bottom),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null

    paint.shader = RadialGradient(
        w * 0.74f,
        h * 0.34f,
        w * 0.58f,
        intArrayOf(withAlpha(palette.primaryAura, 0.34f), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawOval(RectF(w * 0.18f, h * 0.02f, w * 1.30f, h * 0.77f), paint)

    paint.shader = RadialGradient(
        w * 0.20f,
        h * 0.62f,
        w * 0.44f,
        intArrayOf(withAlpha(palette.secondaryAura, 0.22f), Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawOval(RectF(-w * 0.18f, h * 0.30f, w * 0.62f, h * 0.88f), paint)
    paint.shader = null

    val xs = floatArrayOf(0.15f, 0.38f, 0.62f, 0.85f)
    val ys = floatArrayOf(0.11f, 0.24f, 0.37f, 0.50f, 0.63f, 0.76f)
    var index = 0
    for (y in ys) {
        for (x in xs) {
            if (!((y == 0.24f && x > 0.50f) || (y == 0.76f && x == 0.62f))) {
                drawSoftBlock(
                    canvas = canvas,
                    paint = paint,
                    color = palette.icons[index % palette.icons.size],
                    cx = w * x,
                    cy = h * y,
                    base = icon,
                    alpha = 0.92f * iconAlpha,
                    spread = spread
                )
                index++
            }
        }
    }

    drawSoftBlock(
        canvas = canvas,
        paint = paint,
        color = palette.widget,
        cx = w * 0.73f,
        cy = h * 0.295f,
        base = min(w * 0.40f, h * 0.17f),
        alpha = 0.72f * iconAlpha,
        aspect = 2.35f,
        spread = spread
    )

    repeat(5) { i ->
        drawSoftBlock(
            canvas = canvas,
            paint = paint,
            color = palette.icons[(i + 2) % palette.icons.size],
            cx = w * (0.14f + i * 0.18f),
            cy = h * 0.932f,
            base = icon * 0.80f,
            alpha = 0.74f * iconAlpha,
            spread = spread
        )
    }
}

private fun drawSoftBlock(
    canvas: Canvas,
    paint: Paint,
    color: Int,
    cx: Float,
    cy: Float,
    base: Float,
    alpha: Float,
    aspect: Float = 1f,
    spread: Float = 1f
) {
    val layers = arrayOf(
        3.15f to 0.030f,
        2.78f to 0.045f,
        2.45f to 0.060f,
        2.14f to 0.080f,
        1.86f to 0.105f,
        1.60f to 0.135f,
        1.36f to 0.170f,
        1.14f to 0.215f,
        0.96f to 0.270f,
        0.82f to 0.340f,
        0.68f to 0.430f
    )
    layers.forEach { (scale, weight) ->
        val blockW = base * scale * spread * aspect
        val blockH = base * scale * spread
        paint.color = withAlpha(color, alpha * weight)
        paint.shader = null
        canvas.drawRoundRect(
            RectF(cx - blockW / 2f, cy - blockH / 2f, cx + blockW / 2f, cy + blockH / 2f),
            blockH * 0.30f,
            blockH * 0.30f,
            paint
        )
    }
}

private fun boxBlur(input: Bitmap, radius: Int, iterations: Int): Bitmap {
    if (radius <= 0 || iterations <= 0) return input
    var current = input.copy(Bitmap.Config.ARGB_8888, false)
    repeat(iterations) {
        current = boxBlurOnce(current, radius)
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
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        val row = y * width
        for (i in -radius..radius) {
            val x = i.coerceIn(0, width - 1)
            val c = source[row + x]
            a += c ushr 24
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        for (x in 0 until width) {
            temp[row + x] = ((a / window) shl 24) or
                ((r / window) shl 16) or
                ((g / window) shl 8) or
                (b / window)
            val removeX = (x - radius).coerceIn(0, width - 1)
            val addX = (x + radius + 1).coerceIn(0, width - 1)
            val remove = source[row + removeX]
            val add = source[row + addX]
            a += (add ushr 24) - (remove ushr 24)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }

    for (x in 0 until width) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val y = i.coerceIn(0, height - 1)
            val c = temp[y * width + x]
            a += c ushr 24
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        for (y in 0 until height) {
            output[y * width + x] = ((a / window) shl 24) or
                ((r / window) shl 16) or
                ((g / window) shl 8) or
                (b / window)
            val removeY = (y - radius).coerceIn(0, height - 1)
            val addY = (y + radius + 1).coerceIn(0, height - 1)
            val remove = temp[removeY * width + x]
            val add = temp[addY * width + x]
            a += (add ushr 24) - (remove ushr 24)
            r += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }

    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

private fun tuneBitmapTone(
    input: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float
): Bitmap {
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

private data class AndroidBackdropPalette(
    val deep: Int,
    val mid: Int,
    val glow: Int,
    val bottom: Int,
    val primaryAura: Int,
    val secondaryAura: Int,
    val widget: Int,
    val icons: List<Int>
)

private fun androidPalette(theme: BackgroundTheme): AndroidBackdropPalette {
    return when (theme) {
        BackgroundTheme.Aurora -> AndroidBackdropPalette(
            deep = Color.rgb(0x06, 0x14, 0x26),
            mid = Color.rgb(0x0B, 0x29, 0x47),
            glow = Color.rgb(0x16, 0x41, 0x66),
            bottom = Color.rgb(0x07, 0x11, 0x1F),
            primaryAura = Color.rgb(0x2F, 0x72, 0xAD),
            secondaryAura = Color.rgb(0x23, 0x6A, 0xA8),
            widget = Color.rgb(0xB9, 0xC3, 0xCD),
            icons = listOf(
                Color.rgb(0x18, 0xAF, 0xFF), Color.rgb(0xFF, 0xB5, 0x1B), Color.rgb(0x18, 0x1A, 0x28), Color.WHITE,
                Color.rgb(0xFF, 0x50, 0x58), Color.rgb(0xFF, 0x94, 0x1D), Color.rgb(0xB9, 0xC3, 0xCD), Color.rgb(0x10, 0x78, 0xF8)
            )
        )
        BackgroundTheme.Jade -> AndroidBackdropPalette(
            deep = Color.rgb(0x07, 0x1B, 0x21),
            mid = Color.rgb(0x0B, 0x3A, 0x43),
            glow = Color.rgb(0x0C, 0x5B, 0x66),
            bottom = Color.rgb(0x06, 0x14, 0x19),
            primaryAura = Color.rgb(0x22, 0xC7, 0xA7),
            secondaryAura = Color.rgb(0x40, 0xDC, 0xA8),
            widget = Color.rgb(0xC8, 0xD8, 0xD2),
            icons = listOf(
                Color.rgb(0x20, 0xD3, 0xB2), Color.rgb(0xFF, 0xC9, 0x5C), Color.rgb(0x1D, 0x26, 0x30), Color.WHITE,
                Color.rgb(0xFF, 0x6B, 0x7C), Color.rgb(0x50, 0xB7, 0xFF), Color.rgb(0xBF, 0xD5, 0xCE), Color.rgb(0x0D, 0x8E, 0x7B)
            )
        )
        BackgroundTheme.Sunset -> AndroidBackdropPalette(
            deep = Color.rgb(0x22, 0x13, 0x27),
            mid = Color.rgb(0x4B, 0x21, 0x38),
            glow = Color.rgb(0x7E, 0x3D, 0x4F),
            bottom = Color.rgb(0x14, 0x0E, 0x1E),
            primaryAura = Color.rgb(0xFF, 0x7A, 0x6E),
            secondaryAura = Color.rgb(0xFF, 0xB3, 0x5B),
            widget = Color.rgb(0xD8, 0xC6, 0xC8),
            icons = listOf(
                Color.rgb(0xFF, 0x6E, 0x82), Color.rgb(0xFF, 0xB8, 0x4A), Color.rgb(0x24, 0x22, 0x33), Color.WHITE,
                Color.rgb(0xFF, 0x4F, 0x6D), Color.rgb(0xFF, 0x8B, 0x2C), Color.rgb(0xCD, 0xC1, 0xD2), Color.rgb(0x6A, 0x79, 0xFF)
            )
        )
        BackgroundTheme.Dawn -> AndroidBackdropPalette(
            deep = Color.rgb(0x1A, 0x26, 0x34),
            mid = Color.rgb(0x52, 0x65, 0x7A),
            glow = Color.rgb(0x93, 0xA8, 0xB7),
            bottom = Color.rgb(0x10, 0x18, 0x22),
            primaryAura = Color.rgb(0xEA, 0xF2, 0xFF),
            secondaryAura = Color.rgb(0x9E, 0xD4, 0xFF),
            widget = Color.rgb(0xE7, 0xE9, 0xEE),
            icons = listOf(
                Color.rgb(0x45, 0xB8, 0xFF), Color.rgb(0xFF, 0xC8, 0x61), Color.rgb(0x28, 0x30, 0x3A), Color.WHITE,
                Color.rgb(0xFF, 0x6F, 0x83), Color.rgb(0xFF, 0xA1, 0x5C), Color.rgb(0xD9, 0xE0, 0xE9), Color.rgb(0x35, 0x8C, 0xFF)
            )
        )
    }
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val safeAlpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    return (safeAlpha shl 24) or (color and 0x00FFFFFF)
}
