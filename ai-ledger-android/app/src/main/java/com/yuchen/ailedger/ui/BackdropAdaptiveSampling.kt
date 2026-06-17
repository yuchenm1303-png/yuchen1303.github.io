package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 普通 Compose 玻璃的自适应背景采样。
 *
 * 白底网页、表格和密集文字属于高亮高频背景。继续叠加固定奶白罩会把文字扩散成灰黄条纹，
 * 因此这里只做极低成本的区域亮度探测：每个可见玻璃最多读取 5 个已预模糊像素，随后选择
 * 低 / 中 / 高模糊纹理，并同步削弱白罩和高光。该链路不注册 OpenGL，不触发几何同步。
 */
internal data class AdaptiveBackdropSample(
    val image: ImageBitmap,
    val scale: Float,
    val veilAlpha: Float,
    val highlightAlpha: Float,
    val dimBoost: Float,
    val luminance: Float
)

internal fun ordinaryBackdropBlurRadiusDp(role: GlassRole): Float {
    val base = when (role) {
        GlassRole.Shell -> 118f
        GlassRole.Card, GlassRole.Floating, GlassRole.Nav -> 76f
        GlassRole.Chip, GlassRole.Flex -> 58f
    }
    return if (role == GlassRole.Shell) {
        base
    } else {
        (base * ComposeGlassLabState.style.blurScale).coerceIn(32f, 128f)
    }
}

internal fun resolveAdaptiveBackdropSample(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    sampleSize: Size,
    requestedBlurDp: Float
): AdaptiveBackdropSample {
    val luminance = estimateBackdropRegionLuminance(backdrop, sampleOffset, sampleSize)
    val bright = smoothStep(0.62f, 0.94f, luminance)
    val nearWhite = smoothStep(0.84f, 0.985f, luminance)

    // 白底内容需要更强的扩散来消除笔画残影；深色壁纸保持原有清透度。
    val effectiveBlurDp = requestedBlurDp + bright * 24f + nearWhite * 22f
    val image = when {
        effectiveBlurDp < 62f -> backdrop.blurLowImage
        effectiveBlurDp < 101f -> backdrop.blurMediumImage
        else -> backdrop.blurHighImage
    }

    return AdaptiveBackdropSample(
        image = image,
        scale = backdrop.scale,
        veilAlpha = (1f - bright * 0.54f - nearWhite * 0.10f).coerceIn(0.28f, 1f),
        highlightAlpha = (1f - bright * 0.70f - nearWhite * 0.16f).coerceIn(0.12f, 1f),
        dimBoost = (bright * 0.018f + nearWhite * 0.030f).coerceIn(0f, 0.050f),
        luminance = luminance
    )
}

private fun estimateBackdropRegionLuminance(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    sampleSize: Size
): Float {
    val bitmap = backdrop.blurMediumImage.asAndroidBitmap()
    if (bitmap.width <= 0 || bitmap.height <= 0) return 0.5f

    val rootWidth = backdrop.fullWidthPx.toFloat().coerceAtLeast(1f)
    val rootHeight = backdrop.fullHeightPx.toFloat().coerceAtLeast(1f)
    val scaleX = bitmap.width.toFloat() / rootWidth
    val scaleY = bitmap.height.toFloat() / rootHeight
    val width = sampleSize.width.coerceAtLeast(1f)
    val height = sampleSize.height.coerceAtLeast(1f)

    var total = 0f
    var count = 0
    var index = 0
    while (index < SAMPLE_FRACTIONS.size) {
        val fraction = SAMPLE_FRACTIONS[index]
        val rootX = (sampleOffset.x + width * fraction.first).coerceIn(0f, rootWidth - 1f)
        val rootY = (sampleOffset.y + height * fraction.second).coerceIn(0f, rootHeight - 1f)
        val x = (rootX * scaleX).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (rootY * scaleY).roundToInt().coerceIn(0, bitmap.height - 1)
        total += linearLuminance(bitmap.getPixel(x, y))
        count += 1
        index += 1
    }
    return if (count == 0) 0.5f else (total / count).coerceIn(0f, 1f)
}

private val SAMPLE_FRACTIONS = arrayOf(
    0.50f to 0.50f,
    0.20f to 0.24f,
    0.80f to 0.24f,
    0.20f to 0.76f,
    0.80f to 0.76f
)

private val SRGB_TO_LINEAR = FloatArray(256) { value ->
    val channel = value / 255f
    if (channel <= 0.04045f) channel / 12.92f
    else ((channel + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearLuminance(color: Int): Float {
    val red = SRGB_TO_LINEAR[(color shr 16) and 0xFF]
    val green = SRGB_TO_LINEAR[(color shr 8) and 0xFF]
    val blue = SRGB_TO_LINEAR[color and 0xFF]
    return red * 0.2126f + green * 0.7152f + blue * 0.0722f
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
