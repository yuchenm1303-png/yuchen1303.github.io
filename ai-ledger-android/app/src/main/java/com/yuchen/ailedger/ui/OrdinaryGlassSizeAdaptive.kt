package com.yuchen.ailedger.ui

import kotlin.math.sqrt

internal data class OrdinaryGlassSizeAdaptiveProfile(
    val minSide: Float,
    val maxSide: Float,
    val smallT: Float,
    val largeT: Float,
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
    val aspect = (maxSide / minSide).coerceIn(1f, 8f)
    val areaSide = sqrt(w * h)
    val tuning = ComposeGlassLabState.sizeAdaptiveTuning.normalized()
    val pivot = tuning.pivotPx.coerceAtLeast(1f)

    /*
     * 不能只用 minSide 判断大/小。设置页和首页里很多“大玻璃”是宽而不高的卡片：
     * minSide 仍然很小，旧逻辑会把它们永远判成 small，导致“大玻璃压制”几乎无效。
     *
     * 这里拆成两条感知尺度：
     * - smallT 看短边，保证 Chip / Floating 这种小组件增强；
     * - largeT 同时看面积等效边长与长边，保证宽卡片也能被大玻璃压制命中。
     */
    val smallT = (((pivot - minSide) / pivot) * (1f + (aspect - 1f) * 0.08f)).coerceIn(0f, 1f)
    val areaLargeT = ((areaSide - pivot) / (pivot * 1.65f)).coerceIn(0f, 1f)
    val widthLargeT = ((maxSide - pivot * 1.30f) / (pivot * 2.10f)).coerceIn(0f, 1f)
    val largeT = maxOf(areaLargeT, widthLargeT * (0.72f + (aspect - 1f) * 0.08f).coerceIn(0.72f, 1.12f))
        .coerceIn(0f, 1f)

    val roleSmallGain = when (role) {
        GlassRole.Chip -> 1.35f
        GlassRole.Floating -> 1.22f
        GlassRole.Flex -> 1.08f
        GlassRole.Card -> 0.98f
        GlassRole.Nav -> 0.82f
        GlassRole.Shell -> 0f
    }
    val roleLargeDamp = when (role) {
        GlassRole.Card -> 1.18f
        GlassRole.Flex -> 1.08f
        GlassRole.Nav -> 1.00f
        GlassRole.Floating -> 0.82f
        GlassRole.Chip -> 0.66f
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

    val smallBoost = tuning.smallBoost.coerceIn(0f, 8f)
    val largeDamp = tuning.largeDamp.coerceIn(0f, 1.6f)
    val shapeGain = (
        (1f + smallT * smallBoost * roleSmallGain) *
            (1f - largeT * largeDamp * 0.82f * roleLargeDamp)
        ).coerceIn(0.10f, 8.50f)
    val lightGain = (
        (1f + smallT * smallBoost * 0.90f * roleSmallGain) *
            (1f - largeT * largeDamp * 0.70f * roleLargeDamp) *
            tuning.lightBoost
        ).coerceIn(0.08f, 10.00f)
    val sweepGain = (
        (1f + smallT * smallBoost * 0.74f * roleSmallGain) *
            (1f - largeT * largeDamp * 0.62f * roleLargeDamp)
        ).coerceIn(0.10f, 8.50f)
    val bloomRadiusGain = (
        (1f - smallT * 0.12f) *
            (1f - largeT * largeDamp * 0.56f * roleLargeDamp)
        ).coerceIn(0.24f, 1.32f)
    val translationGain = (
        (1f + smallT * 0.54f * roleSmallGain) *
            (1f - largeT * largeDamp * 0.72f * roleLargeDamp)
        ).coerceIn(0.12f, 2.40f)
    val visualPx = (tuning.visualPx * shapeGain * rolePixelGain).coerceIn(0.12f, 38f)

    return OrdinaryGlassSizeAdaptiveProfile(
        minSide = minSide,
        maxSide = maxSide,
        smallT = smallT,
        largeT = largeT,
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
    val growPx = profile.visualPx * press * (0.72f + grow.coerceIn(0f, 10f) * 0.12f)
    val reboundPx = profile.visualPx * rebound * (0.24f + bounce.coerceIn(0f, 8f) * 0.065f)
    val xPx = growPx - reboundPx
    val yPx = growPx * 0.86f - reboundPx * 0.78f
    val translation = (growPx * 0.22f - reboundPx * 0.24f) * profile.translationGain

    return OrdinaryGlassResolvedTransform(
        scaleX = 1f + xPx / w,
        scaleY = 1f + yPx / h,
        translationY = translation,
        shadowElevation = (growPx * 0.16f).coerceIn(0f, 5.2f),
    )
}

private fun ordinarySizeAdaptiveSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
