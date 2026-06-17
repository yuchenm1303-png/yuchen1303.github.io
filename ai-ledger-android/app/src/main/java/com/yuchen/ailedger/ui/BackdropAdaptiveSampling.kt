package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

/** 单渲染线程复用，避免每块玻璃在每个绘制帧创建采样结果对象。 */
private val AdaptiveSampleThreadLocal = ThreadLocal.withInitial { AdaptiveBackdropSample() }

internal class AdaptiveBackdropSample {
    lateinit var image: ImageBitmap
    var scale: Float = 1f
    var veilAlpha: Float = 1f
    var highlightAlpha: Float = 1f
    var dimBoost: Float = 0f
    var luminance: Float = 0.5f

    internal var cachedLuminanceMap: BackdropLuminanceMap? = null
    internal var cachedRegionKey: Long = Long.MIN_VALUE
    internal var cachedLuminance: Float = 0.5f
}

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
): AdaptiveBackdropSample = resolveAdaptiveBackdropSample(
    backdrop = backdrop,
    sampleOffset = sampleOffset,
    sampleSize = sampleSize,
    requestedBlurDp = requestedBlurDp,
    out = AdaptiveSampleThreadLocal.get()
)

internal fun resolveAdaptiveBackdropSample(
    backdrop: BlurredBackdropBitmap,
    sampleOffset: Offset,
    sampleSize: Size,
    requestedBlurDp: Float,
    out: AdaptiveBackdropSample
): AdaptiveBackdropSample {
    val luminanceMap = backdrop.luminanceMap
    val regionKey = luminanceMap.regionKey(sampleOffset, sampleSize)
    val luminance = if (
        out.cachedLuminanceMap === luminanceMap &&
        out.cachedRegionKey == regionKey
    ) {
        out.cachedLuminance
    } else {
        luminanceMap.averageLuminance(regionKey).also { resolved ->
            out.cachedLuminanceMap = luminanceMap
            out.cachedRegionKey = regionKey
            out.cachedLuminance = resolved
        }
    }

    val bright = smoothStep(0.62f, 0.94f, luminance)
    val nearWhite = smoothStep(0.84f, 0.985f, luminance)
    val effectiveBlurDp = requestedBlurDp + bright * 24f + nearWhite * 22f

    out.image = when {
        effectiveBlurDp < 62f -> backdrop.blurLowImage
        effectiveBlurDp < 101f -> backdrop.blurMediumImage
        else -> backdrop.blurHighImage
    }
    out.scale = backdrop.scale
    out.veilAlpha = (1f - bright * 0.54f - nearWhite * 0.10f).coerceIn(0.28f, 1f)
    out.highlightAlpha = (1f - bright * 0.70f - nearWhite * 0.16f).coerceIn(0.12f, 1f)
    out.dimBoost = (bright * 0.018f + nearWhite * 0.030f).coerceIn(0f, 0.050f)
    out.luminance = luminance
    return out
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge1 <= edge0) return if (value >= edge1) 1f else 0f
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
