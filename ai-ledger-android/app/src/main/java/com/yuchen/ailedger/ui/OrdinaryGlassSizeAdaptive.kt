package com.yuchen.ailedger.ui

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
    val tuning = ComposeGlassLabState.sizeAdaptiveTuning.normalized()
    val pivot = tuning.pivotPx.coerceAtLeast(1f)
    val smallT = ((pivot - minSide) / pivot).coerceIn(0f, 1f)
    val largeT = ((minSide - pivot) / (pivot * 2.25f)).coerceIn(0f, 1f)

    val roleSmallGain = when (role) {
        GlassRole.Chip -> 1.18f
        GlassRole.Floating -> 1.08f
        GlassRole.Flex -> 0.96f
        GlassRole.Card -> 0.88f
        GlassRole.Nav -> 0.76f
        GlassRole.Shell -> 0f
    }
    val roleLargeDamp = when (role) {
        GlassRole.Card -> 1.06f
        GlassRole.Flex -> 0.96f
        GlassRole.Nav -> 0.90f
        GlassRole.Floating -> 0.78f
        GlassRole.Chip -> 0.68f
        GlassRole.Shell -> 0f
    }
    val rolePixelGain = when (role) {
        GlassRole.Chip -> 1.18f
        GlassRole.Floating -> 1.10f
        GlassRole.Flex -> 0.98f
        GlassRole.Card -> 0.92f
        GlassRole.Nav -> 0.78f
        GlassRole.Shell -> 0f
    }

    val shapeGain = (
        (1f + smallT * tuning.smallBoost * roleSmallGain) *
            (1f - largeT * tuning.largeDamp * roleLargeDamp)
        ).coerceIn(0.24f, 5.20f)
    val lightGain = (
        (1f + smallT * tuning.smallBoost * 0.72f * roleSmallGain) *
            (1f - largeT * tuning.largeDamp * 0.62f * roleLargeDamp) *
            tuning.lightBoost
        ).coerceIn(0.18f, 6.20f)
    val sweepGain = (
        (1f + smallT * tuning.smallBoost * 0.54f * roleSmallGain) *
            (1f - largeT * tuning.largeDamp * 0.52f * roleLargeDamp)
        ).coerceIn(0.22f, 5.20f)
    val bloomRadiusGain = (
        (1f - smallT * 0.16f) *
            (1f - largeT * tuning.largeDamp * 0.38f * roleLargeDamp)
        ).coerceIn(0.42f, 1.18f)
    val translationGain = (
        (1f + smallT * 0.34f * roleSmallGain) *
            (1f - largeT * tuning.largeDamp * 0.58f * roleLargeDamp)
        ).coerceIn(0.22f, 1.68f)
    val visualPx = (tuning.visualPx * shapeGain * rolePixelGain).coerceIn(0.30f, 26f)

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
    val growPx = profile.visualPx * press * (0.68f + grow.coerceIn(0f, 10f) * 0.10f)
    val reboundPx = profile.visualPx * rebound * (0.22f + bounce.coerceIn(0f, 8f) * 0.055f)
    val xPx = growPx - reboundPx
    val yPx = growPx * 0.82f - reboundPx * 0.78f
    val translation = (growPx * 0.18f - reboundPx * 0.24f) * profile.translationGain

    return OrdinaryGlassResolvedTransform(
        scaleX = 1f + xPx / w,
        scaleY = 1f + yPx / h,
        translationY = translation,
        shadowElevation = (growPx * 0.12f).coerceIn(0f, 3.8f),
    )
}

private fun ordinarySizeAdaptiveSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
