package com.yuchen.ailedger.ui

import kotlin.math.sqrt

internal data class OrdinaryGlassSizeAdaptiveProfile(
    val minSide: Float,
    val maxSide: Float,
    val areaSide: Float,
    val aspect: Float,
    val smallT: Float,
    val largeT: Float,
    val wideT: Float,
    val shapeGain: Float,
    val lightGain: Float,
    val sweepGain: Float,
    val bloomRadiusGain: Float,
    val translationGain: Float,
    val visualPx: Float,
)

internal data class OrdinaryGlassResolvedTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationY: Float,
    val shadowElevation: Float,
)

internal fun ordinaryGlassSizeAdaptiveProfile(
    widthPx: Float,
    heightPx: Float,
    role: GlassRole
): OrdinaryGlassSizeAdaptiveProfile {
    val w = widthPx.coerceAtLeast(1f)
    val h = heightPx.coerceAtLeast(1f)
    val minSide = minOf(w, h)
    val maxSide = maxOf(w, h)
    val aspect = (maxSide / minSide).coerceIn(1f, 14f)
    val areaSide = sqrt(w * h)
    val tuning = ComposeGlassLabState.sizeAdaptiveTuning.normalized()

    val smallThreshold = tuning.smallThresholdPx.coerceAtLeast(1f)
    val largeThreshold = tuning.largeThresholdPx.coerceAtLeast(smallThreshold + 1f)
    val wideStart = tuning.wideAspectStart.coerceAtLeast(1f)
    val wideEnd = tuning.wideAspectEnd.coerceAtLeast(wideStart + 0.01f)

    /*
     * 成熟尺寸归一化不能只有一个 pivot：
     * - smallT：短边越小越强，专门补偿 Chip / 小按钮的 JND 不足；
     * - largeT：面积等效边长越大越强，专门压制大卡片整体 scale 和大面积 bloom；
     * - wideT：宽高比越极端越强，只压横向形变、边缘扫光和 bloom 半径，避免宽卡被短边误判成小件。
     */
    val smallT = ((smallThreshold - minSide) / smallThreshold).coerceIn(0f, 1f)
    val largeT = ((areaSide - largeThreshold) / (largeThreshold * 1.65f)).coerceIn(0f, 1f)
    val wideT = ((aspect - wideStart) / (wideEnd - wideStart)).coerceIn(0f, 1f)

    val roleSmallGain = when (role) {
        GlassRole.Chip -> 1.36f
        GlassRole.Floating -> 1.22f
        GlassRole.Flex -> 1.08f
        GlassRole.Card -> 0.96f
        GlassRole.Nav -> 0.78f
        GlassRole.Shell -> 0f
    }
    val roleLargeDamp = when (role) {
        GlassRole.Card -> 1.18f
        GlassRole.Flex -> 1.08f
        GlassRole.Nav -> 1.00f
        GlassRole.Floating -> 0.82f
        GlassRole.Chip -> 0.62f
        GlassRole.Shell -> 0f
    }
    val rolePixelGain = when (role) {
        GlassRole.Chip -> 1.36f
        GlassRole.Floating -> 1.20f
        GlassRole.Flex -> 1.04f
        GlassRole.Card -> 0.96f
        GlassRole.Nav -> 0.78f
        GlassRole.Shell -> 0f
    }

    val smallBoost = tuning.smallBoost.coerceIn(0f, 24f)
    val largeDamp = tuning.largeDamp.coerceIn(0f, 4f)
    val wideDamp = wideT * largeDamp
    val largeEnvelope = maxOf(largeT, wideT * 0.62f)

    val shapeGain = (
        (1f + smallT * smallBoost * 0.34f * roleSmallGain) *
            (1f - largeEnvelope * largeDamp * 0.22f * roleLargeDamp) *
            (1f - wideDamp * 0.12f)
        ).coerceIn(0.08f, 8.50f)
    val lightGain = (
        (1f + smallT * smallBoost * 0.30f * roleSmallGain) *
            (1f - largeT * largeDamp * 0.16f * roleLargeDamp) *
            (1f - wideT * largeDamp * 0.10f) *
            tuning.lightBoost
        ).coerceIn(0.06f, 12.00f)
    val sweepGain = (
        (1f + smallT * smallBoost * 0.22f * roleSmallGain) *
            (1f - wideT * largeDamp * 0.24f) *
            (1f - largeT * largeDamp * 0.12f * roleLargeDamp)
        ).coerceIn(0.06f, 8.50f)
    val bloomRadiusGain = (
        (1f - smallT * 0.10f) *
            (1f - largeT * largeDamp * 0.18f * roleLargeDamp) *
            (1f - wideT * largeDamp * 0.20f)
        ).coerceIn(0.20f, 1.42f)
    val translationGain = (
        (1f + smallT * 0.38f * roleSmallGain) *
            (1f - largeEnvelope * largeDamp * 0.18f * roleLargeDamp)
        ).coerceIn(0.10f, 2.80f)
    val visualPx = (tuning.visualPx * shapeGain * rolePixelGain).coerceIn(0.10f, 72f)

    return OrdinaryGlassSizeAdaptiveProfile(
        minSide = minSide,
        maxSide = maxSide,
        areaSide = areaSide,
        aspect = aspect,
        smallT = smallT,
        largeT = largeT,
        wideT = wideT,
        shapeGain = shapeGain,
        lightGain = lightGain,
        sweepGain = sweepGain,
        bloomRadiusGain = bloomRadiusGain,
        translationGain = translationGain,
        visualPx = visualPx,
    )
}

internal fun ordinaryGlassResolvedTransform(
    widthPx: Float,
    heightPx: Float,
    role: GlassRole,
    pressProgress: Float,
    reboundProgress: Float,
    grow: Float,
    bounce: Float,
): OrdinaryGlassResolvedTransform {
    val w = widthPx.coerceAtLeast(1f)
    val h = heightPx.coerceAtLeast(1f)
    val profile = ordinaryGlassSizeAdaptiveProfile(w, h, role)
    val press = ordinarySizeAdaptiveSmoothStep(pressProgress.coerceAtLeast(0f).coerceIn(0f, 1.72f) / 1.72f)
    val rebound = ordinarySizeAdaptiveSmoothStep(reboundProgress.coerceAtLeast(0f).coerceIn(0f, 1.40f) / 1.40f)
    val growPx = profile.visualPx * press * (0.70f + grow.coerceIn(0f, 10f) * 0.115f)
    val reboundPx = profile.visualPx * rebound * (0.22f + bounce.coerceIn(0f, 8f) * 0.060f)
    val wideXCompression = (1f - profile.wideT * ComposeGlassLabState.sizeAdaptiveTuning.normalized().largeDamp * 0.16f)
        .coerceIn(0.48f, 1f)
    val xPx = (growPx - reboundPx) * wideXCompression
    val yPx = growPx * 0.86f - reboundPx * 0.78f
    val translation = (growPx * 0.22f - reboundPx * 0.24f) * profile.translationGain

    return OrdinaryGlassResolvedTransform(
        scaleX = 1f + xPx / w,
        scaleY = 1f + yPx / h,
        translationY = translation,
        shadowElevation = (growPx * 0.16f).coerceIn(0f, 5.8f),
    )
}

private fun ordinarySizeAdaptiveSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
