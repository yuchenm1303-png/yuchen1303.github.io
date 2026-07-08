package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ComposeGlassPreset {
    Clear,
    Frost,
    Crystal,
    Dense,
    Aurora
}

data class ComposeGlassMotionStyle(
    val master: Float = 6.629f,
    val deformation: Float = 0.351f,
    val touchLight: Float = 24.0f,
    val prism: Float = 24.0f,
    val sweep: Float = 24.0f,
    val rebound: Float = 7.571f,
    val afterglow: Float = 0.815f,
    val speed: Float = 0.171f,
    val tapImpulse: Float = 12.0f,
    val releaseCohesion: Float = 12.0f,
    val fieldContinuity: Float = 2.285f,
    val sweepMomentum: Float = 4.466f,
) {
    internal fun normalized(): ComposeGlassMotionStyle = copy(
        master = normalizedMotionControl(master, rawMax = 12f, internalMax = 1.50f),
        deformation = normalizedMotionControl(deformation, rawMax = 12f, internalMax = 1.50f),
        touchLight = normalizedMotionControl(touchLight, rawMax = 24f, internalMax = 1.06f),
        prism = normalizedMotionControl(prism, rawMax = 24f, internalMax = 1.42f),
        sweep = normalizedMotionControl(sweep, rawMax = 24f, internalMax = 1.32f),
        rebound = normalizedMotionControl(rebound, rawMax = 12f, internalMax = 1.50f),
        afterglow = normalizedMotionControl(afterglow, rawMax = 24f, internalMax = 1.36f),
        speed = speed.coerceIn(0.02f, 40f),
        tapImpulse = normalizedMotionControl(tapImpulse, rawMax = 12f, internalMax = 1.20f),
        releaseCohesion = normalizedMotionControl(releaseCohesion, rawMax = 12f, internalMax = 1.20f),
        fieldContinuity = normalizedMotionControl(fieldContinuity, rawMax = 24f, internalMax = 1.20f),
        sweepMomentum = normalizedMotionControl(sweepMomentum, rawMax = 24f, internalMax = 1.20f),
    )

    internal fun storageClamped(): ComposeGlassMotionStyle = copy(
        master = master.coerceIn(0f, 12f),
        deformation = deformation.coerceIn(0f, 12f),
        touchLight = touchLight.coerceIn(0f, 24f),
        prism = prism.coerceIn(0f, 24f),
        sweep = sweep.coerceIn(0f, 24f),
        rebound = rebound.coerceIn(0f, 12f),
        afterglow = afterglow.coerceIn(0f, 24f),
        speed = speed.coerceIn(0.05f, 8f),
        tapImpulse = tapImpulse.coerceIn(0f, 12f),
        releaseCohesion = releaseCohesion.coerceIn(0f, 12f),
        fieldContinuity = fieldContinuity.coerceIn(0f, 24f),
        sweepMomentum = sweepMomentum.coerceIn(0f, 24f),
    )
}

private fun normalizedMotionControl(value: Float, rawMax: Float, internalMax: Float): Float {
    val clean = value.coerceIn(0f, rawMax)
    if (clean <= 1f) return clean
    val span = (rawMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (internalMax - 1f)
}

data class OrdinaryGlassCapsuleTuning(
    val compactBoost: Float = 0.46f,
    val elongatedX: Float = 0.0f,
    val elongatedY: Float = 0.13f,
    val basePx: Float = 0.038f,
    val tapPx: Float = 0.112f,
    val tapPop: Float = 1.16f,
    val tapCarry: Float = 0.42f,
    val sticky: Float = 0.032f,
    val sink: Float = 0.12f,
    val settle: Float = 0.30f,
) {
    internal fun normalized(): OrdinaryGlassCapsuleTuning = copy(
        compactBoost = compactBoost.coerceIn(0f, 64f),
        elongatedX = elongatedX.coerceIn(0f, 32f),
        elongatedY = elongatedY.coerceIn(0f, 16f),
        basePx = basePx.coerceIn(0.001f, 2.40f),
        tapPx = tapPx.coerceIn(0f, 3.20f),
        tapPop = tapPop.coerceIn(0.05f, 80f),
        tapCarry = tapCarry.coerceIn(0f, 48f),
        sticky = sticky.coerceIn(0f, 8f),
        sink = sink.coerceIn(0f, 48f),
        settle = settle.coerceIn(0f, 24f),
    )
}

data class OrdinaryGlassPressureOpticsTuning(
    val fieldIntensity: Float = 6.80f,
    val fieldSpread: Float = 5.80f,
    val fieldSoftness: Float = 6.10f,
    val fieldUniformity: Float = 5.40f,
    val fieldFollow: Float = 0.14f,
    val edgeIntensity: Float = 6.20f,
    val edgeWidth: Float = 3.15f,
    val edgeSoftness: Float = 5.40f,
    val edgeBloom: Float = 5.70f,
) {
    internal fun normalized(): OrdinaryGlassPressureOpticsTuning = copy(
        fieldIntensity = fieldIntensity.coerceIn(0f, 640f),
        fieldSpread = fieldSpread.coerceIn(0f, 480f),
        fieldSoftness = fieldSoftness.coerceIn(0f, 480f),
        fieldUniformity = fieldUniformity.coerceIn(0f, 320f),
        fieldFollow = fieldFollow.coerceIn(0f, 24f),
        edgeIntensity = edgeIntensity.coerceIn(0f, 640f),
        edgeWidth = edgeWidth.coerceIn(0f, 240f),
        edgeSoftness = edgeSoftness.coerceIn(0f, 480f),
        edgeBloom = edgeBloom.coerceIn(0f, 480f),
    )
}

data class OrdinaryGlassSizeAdaptiveTuning(
    val pressBodyGain: Float = 10.631f,
    val pressOpticsGain: Float = 11.174f,
    val pressLensGain: Float = 6.859f,
    val pressRimGain: Float = 14.413f,
    val pressIntensityGain: Float = 1.00f,
    val pressShadowGain: Float = 1.00f,
    val pressReboundGain: Float = 1.00f,
    val pressSmallBoost: Float = 16.0f,
    val pressRowBoost: Float = 13.427f,
    val pressMinOptics: Float = 3.181f,

    val smallHeightStartPx: Float = 48f,
    val smallHeightRangePx: Float = 150f,
    val smallShortSideStartPx: Float = 52f,
    val smallShortSideRangePx: Float = 170f,
    val smallAreaStartPx: Float = 104f,
    val smallAreaRangePx: Float = 310f,
    val smallHeightWeight: Float = 0.56f,
    val smallShortSideWeight: Float = 0.24f,
    val smallAreaWeight: Float = 0.20f,

    val rowAspectStart: Float = 2.10f,
    val rowAspectRange: Float = 4.30f,
    val compactHeightStartPx: Float = 76f,
    val compactHeightRangePx: Float = 132f,
    val rowBodyDamp: Float = 0.958f,

    val bodyHeightPivotPx: Float = 270f,
    val bodyHeightRangePx: Float = 220f,
    val bodyHeightMin: Float = 0.0457f,
    val bodyAreaPivotPx: Float = 520f,
    val bodyAreaRangePx: Float = 460f,
    val bodyAreaMin: Float = 0.0437f,
    val bodyMin: Float = 0.10f,
    val bodyMax: Float = 0.11f,
    val opticsMin: Float = 0.32f,
    val opticsMax: Float = 2.80f,
    val rimMin: Float = 0.24f,
    val rimMax: Float = 2.60f,

    val smallBoost: Float = 24.0f,
    val largeDamp: Float = 14.615f,
    val pivotPx: Float = 520f,
    val visualPx: Float = 18.0f,
    val lightBoost: Float = 24.0f,
    val smallThresholdPx: Float = 520f,
    val largeThresholdPx: Float = 536f,
    val wideAspectStart: Float = 1.55f,
    val wideAspectEnd: Float = 4.80f,
) {
    internal fun normalized(): OrdinaryGlassSizeAdaptiveTuning {
        val bodyMinClean = bodyMin.coerceIn(0f, 16f)
        val bodyMaxClean = bodyMax.coerceIn((bodyMinClean + 0.01f).coerceAtMost(16f), 24f)
        val opticsMinClean = opticsMin.coerceIn(0f, 16f)
        val opticsMaxClean = opticsMax.coerceIn((opticsMinClean + 0.01f).coerceAtMost(16f), 32f)
        val rimMinClean = rimMin.coerceIn(0f, 16f)
        val rimMaxClean = rimMax.coerceIn((rimMinClean + 0.01f).coerceAtMost(16f), 32f)
        val oldSmall = smallThresholdPx.coerceIn(0f, 2400f)
        val oldLarge = largeThresholdPx.coerceIn((oldSmall + 16f).coerceAtMost(2400f), 4800f)
        val oldWideStart = wideAspectStart.coerceIn(0.2f, 12f)
        val oldWideEnd = wideAspectEnd.coerceIn((oldWideStart + 0.05f).coerceAtMost(12f), 24f)
        return copy(
            pressBodyGain = pressBodyGain.coerceIn(0f, 24f),
            pressOpticsGain = pressOpticsGain.coerceIn(0f, 24f),
            pressLensGain = pressLensGain.coerceIn(0.05f, 12f),
            pressRimGain = pressRimGain.coerceIn(0f, 24f),
            pressIntensityGain = pressIntensityGain.coerceIn(0f, 24f),
            pressShadowGain = pressShadowGain.coerceIn(0f, 24f),
            pressReboundGain = pressReboundGain.coerceIn(0f, 24f),
            pressSmallBoost = pressSmallBoost.coerceIn(0f, 16f),
            pressRowBoost = pressRowBoost.coerceIn(0f, 16f),
            pressMinOptics = pressMinOptics.coerceIn(0f, 8f),
            smallHeightStartPx = smallHeightStartPx.coerceIn(0f, 1200f),
            smallHeightRangePx = smallHeightRangePx.coerceIn(1f, 1200f),
            smallShortSideStartPx = smallShortSideStartPx.coerceIn(0f, 1200f),
            smallShortSideRangePx = smallShortSideRangePx.coerceIn(1f, 1200f),
            smallAreaStartPx = smallAreaStartPx.coerceIn(0f, 1600f),
            smallAreaRangePx = smallAreaRangePx.coerceIn(1f, 1600f),
            smallHeightWeight = smallHeightWeight.coerceIn(0f, 8f),
            smallShortSideWeight = smallShortSideWeight.coerceIn(0f, 8f),
            smallAreaWeight = smallAreaWeight.coerceIn(0f, 8f),
            rowAspectStart = rowAspectStart.coerceIn(0.2f, 12f),
            rowAspectRange = rowAspectRange.coerceIn(0.05f, 24f),
            compactHeightStartPx = compactHeightStartPx.coerceIn(0f, 1200f),
            compactHeightRangePx = compactHeightRangePx.coerceIn(1f, 1200f),
            rowBodyDamp = rowBodyDamp.coerceIn(0f, 2f),
            bodyHeightPivotPx = bodyHeightPivotPx.coerceIn(0f, 2400f),
            bodyHeightRangePx = bodyHeightRangePx.coerceIn(1f, 2400f),
            bodyHeightMin = bodyHeightMin.coerceIn(0f, 2f),
            bodyAreaPivotPx = bodyAreaPivotPx.coerceIn(0f, 3200f),
            bodyAreaRangePx = bodyAreaRangePx.coerceIn(1f, 3200f),
            bodyAreaMin = bodyAreaMin.coerceIn(0f, 2f),
            bodyMin = bodyMinClean,
            bodyMax = bodyMaxClean,
            opticsMin = opticsMinClean,
            opticsMax = opticsMaxClean,
            rimMin = rimMinClean,
            rimMax = rimMaxClean,
            smallBoost = smallBoost.coerceIn(0f, 96f),
            largeDamp = largeDamp.coerceIn(0f, 24f),
            pivotPx = pivotPx.coerceIn(0f, 4800f),
            visualPx = visualPx.coerceIn(0.2f, 240f),
            lightBoost = lightBoost.coerceIn(0f, 240f),
            smallThresholdPx = oldSmall,
            largeThresholdPx = oldLarge,
            wideAspectStart = oldWideStart,
            wideAspectEnd = oldWideEnd,
        )
    }
}

data class ComposeGlassStyle(
    val preset: ComposeGlassPreset,
    val backdrop: Float,
    val backdropBlur: Float,
    val backdropDim: Float,
    val backdropMilk: Float,
    val backdropHighlight: Float,
    val quiet: Float,
    val bodyAbsorption: Float,
    val lowerBodyMass: Float,
    val innerTransition: Float,
    val topLight: Float,
    val topWidthDp: Float,
    val topVariation: Float,
    val bottomLight: Float,
    val bottomWidthDp: Float,
    val outerRim: Float,
    val bottomMass: Float,
    val sideLight: Float,
    val radius: Float,
    val ribbon: Float
) {
    val density: Float get() = ComposeGlassRuntimeDefaults.frost
    val edge: Float get() = topLight
    val backdropAlpha: Float get() = backdrop.coerceIn(0.12f, 1.55f)
    val blurScale: Float get() = backdropBlur.coerceIn(0.35f, 2.20f)
    val frostAlpha: Float get() = ComposeGlassRuntimeDefaults.frost.coerceIn(0.02f, 2.80f)
    val rimAlpha: Float get() = outerRim.coerceIn(0.02f, 3.20f)
    val innerRimAlpha: Float get() = innerTransition.coerceIn(0f, 2.80f)
    val topHighlight: Float get() = topLight.coerceIn(0.02f, 3.40f)
    val bottomShadow: Float get() = (bottomMass + lowerBodyMass * 0.42f).coerceIn(0.02f, 3.00f)
    val cornerGlint: Float get() = topVariation.coerceIn(0f, 3.00f)
    val strokeWidth: Float get() = (0.42f + outerRim * 0.24f).coerceIn(0.10f, 2.30f)
    val shadowAlpha: Float get() = ComposeGlassRuntimeDefaults.shadow.coerceIn(0f, 2.60f)
    val radiusScale: Float get() = (radius / 46f).coerceIn(0.38f, 1.86f)
}

object ComposeGlassLabState {
    var style by mutableStateOf(defaultComposeGlassStyle())
        private set

    var motionStyle by mutableStateOf(defaultComposeGlassMotionStyle())
        private set

    var sizeAdaptiveTuning by mutableStateOf(defaultOrdinaryGlassSizeAdaptiveTuning())
        private set

    var capsuleTuning by mutableStateOf(singleCardCapsuleTuningFor(sizeAdaptiveTuning))
        private set

    var pressureOpticsTuning by mutableStateOf(singleCardPressureOpticsFor(sizeAdaptiveTuning))
        private set

    fun update(next: ComposeGlassStyle) { style = next }
    fun updateMotion(next: ComposeGlassMotionStyle) { motionStyle = next.storageClamped() }
    fun updateCapsuleTuning(next: OrdinaryGlassCapsuleTuning) { capsuleTuning = next.normalized() }
    fun updatePressureOpticsTuning(next: OrdinaryGlassPressureOpticsTuning) { pressureOpticsTuning = next.normalized() }

    fun updateSizeAdaptiveTuning(next: OrdinaryGlassSizeAdaptiveTuning) {
        val normalized = next.normalized()
        sizeAdaptiveTuning = normalized
        applySizeAdaptiveTuningToSingleCard(normalized)
    }

    private fun applySizeAdaptiveTuningToSingleCard(size: OrdinaryGlassSizeAdaptiveTuning) {
        capsuleTuning = singleCardCapsuleTuningFor(size)
        pressureOpticsTuning = singleCardPressureOpticsFor(size)
    }

    fun usePreset(preset: ComposeGlassPreset) { style = defaultComposeGlassStyle(preset) }
    fun reset() { style = defaultComposeGlassStyle(style.preset) }
    fun resetMotion() { motionStyle = defaultComposeGlassMotionStyle() }
    fun resetCapsuleTuning() { capsuleTuning = singleCardCapsuleTuningFor(sizeAdaptiveTuning) }
    fun resetPressureOpticsTuning() { pressureOpticsTuning = singleCardPressureOpticsFor(sizeAdaptiveTuning) }
    fun resetSizeAdaptiveTuning() {
        val defaults = defaultOrdinaryGlassSizeAdaptiveTuning()
        sizeAdaptiveTuning = defaults
        applySizeAdaptiveTuningToSingleCard(defaults)
    }
    fun resetAll() {
        style = defaultComposeGlassStyle()
        motionStyle = defaultComposeGlassMotionStyle()
        val defaults = defaultOrdinaryGlassSizeAdaptiveTuning()
        sizeAdaptiveTuning = defaults
        capsuleTuning = singleCardCapsuleTuningFor(defaults)
        pressureOpticsTuning = singleCardPressureOpticsFor(defaults)
    }
}

private fun defaultComposeGlassMotionStyle(): ComposeGlassMotionStyle = ComposeGlassMotionStyle()
private fun defaultOrdinaryGlassCapsuleTuning(): OrdinaryGlassCapsuleTuning = OrdinaryGlassCapsuleTuning()
private fun defaultOrdinaryGlassPressureOpticsTuning(): OrdinaryGlassPressureOpticsTuning = OrdinaryGlassPressureOpticsTuning()
private fun defaultOrdinaryGlassSizeAdaptiveTuning(): OrdinaryGlassSizeAdaptiveTuning = OrdinaryGlassSizeAdaptiveTuning()

private fun singleCardCapsuleTuningFor(size: OrdinaryGlassSizeAdaptiveTuning): OrdinaryGlassCapsuleTuning {
    val normalized = size.normalized()
    return defaultOrdinaryGlassCapsuleTuning().copy(
        compactBoost = (1.0f + normalized.pressSmallBoost * 0.55f).coerceIn(0.20f, 96.00f),
        elongatedX = (normalized.pressRowBoost * 1.25f).coerceIn(0f, 64.00f),
        elongatedY = (0.08f + normalized.pressSmallBoost * 0.050f + normalized.pressRowBoost * 0.030f).coerceIn(0.01f, 32.00f),
        basePx = (0.012f + normalized.pressBodyGain * 0.010f).coerceIn(0.001f, 4.80f),
        tapPx = (0.035f + normalized.pressBodyGain * 0.018f + normalized.pressSmallBoost * 0.009f).coerceIn(0f, 6.40f),
        tapPop = (0.80f + normalized.pressReboundGain * 0.55f).coerceIn(0.05f, 120.00f),
        tapCarry = (0.18f + normalized.pressOpticsGain * 0.18f + normalized.pressRowBoost * 0.04f).coerceIn(0f, 96.00f),
        sticky = (0.012f + normalized.pressMinOptics * 0.060f + normalized.pressIntensityGain * 0.006f).coerceIn(0f, 16.00f),
        sink = (0.050f + normalized.pressShadowGain * 0.18f).coerceIn(0f, 96.00f),
        settle = (0.16f + normalized.pressReboundGain * 0.34f).coerceIn(0f, 48.00f),
    ).normalized()
}

private fun singleCardPressureOpticsFor(size: OrdinaryGlassSizeAdaptiveTuning): OrdinaryGlassPressureOpticsTuning {
    val normalized = size.normalized()
    return defaultOrdinaryGlassPressureOpticsTuning().copy(
        fieldIntensity = (4.0f + normalized.pressOpticsGain * 10.0f + normalized.pressMinOptics * 24.0f + normalized.pressIntensityGain * 2.0f).coerceIn(0f, 960f),
        fieldSpread = (3.0f + normalized.pressLensGain * 10.0f + normalized.pressRowBoost * 2.0f).coerceIn(0f, 720f),
        fieldSoftness = (4.0f + normalized.pressLensGain * 8.0f + normalized.pressRowBoost * 4.0f).coerceIn(0f, 720f),
        fieldUniformity = (3.0f + normalized.pressRowBoost * 6.0f + normalized.rowBodyDamp * 12.0f).coerceIn(0f, 480f),
        fieldFollow = (0.08f + normalized.pressSmallBoost * 0.050f + normalized.pressRowBoost * 0.035f).coerceIn(0f, 48f),
        edgeIntensity = (3.8f + normalized.pressRimGain * 10.0f + normalized.pressRowBoost * 4.0f).coerceIn(0f, 960f),
        edgeWidth = (1.1f + normalized.pressRimGain * 2.0f + normalized.pressSmallBoost * 0.35f).coerceIn(0f, 360f),
        edgeSoftness = (3.4f + normalized.pressLensGain * 5.0f + normalized.pressRowBoost * 4.0f).coerceIn(0f, 720f),
        edgeBloom = (3.4f + normalized.pressOpticsGain * 4.0f + normalized.pressRowBoost * 4.0f + normalized.pressMinOptics * 10.0f).coerceIn(0f, 720f),
    ).normalized()
}

private fun defaultComposeGlassStyle(preset: ComposeGlassPreset = ComposeGlassPreset.Frost): ComposeGlassStyle = when (preset) {
    ComposeGlassPreset.Clear -> ComposeGlassStyle(preset, 1.08f, 0.74f, 0.18f, 0.42f, 0.55f, 0.82f, 0.22f, 0.18f, 0.10f, 1.18f, 2.20f, 0.48f, 0.48f, 1.80f, 0.48f, 0.52f, 0.10f, 48f, 0.28f)
    ComposeGlassPreset.Frost -> ComposeGlassStyle(
        preset = preset,
        backdrop = ComposeGlassRuntimeDefaults.backdrop,
        backdropBlur = ComposeGlassRuntimeDefaults.backdropBlur,
        backdropDim = ComposeGlassRuntimeDefaults.backdropDim,
        backdropMilk = ComposeGlassRuntimeDefaults.backdropMilk,
        backdropHighlight = ComposeGlassRuntimeDefaults.backdropHighlight,
        quiet = ComposeGlassRuntimeDefaults.quiet,
        bodyAbsorption = ComposeGlassRuntimeDefaults.bodyAbsorption,
        lowerBodyMass = ComposeGlassRuntimeDefaults.lowerBodyMass,
        innerTransition = ComposeGlassRuntimeDefaults.innerTransition,
        topLight = ComposeGlassRuntimeDefaults.topLight,
        topWidthDp = ComposeGlassRuntimeDefaults.topWidthDp,
        topVariation = ComposeGlassRuntimeDefaults.topVariation,
        bottomLight = ComposeGlassRuntimeDefaults.bottomLight,
        bottomWidthDp = ComposeGlassRuntimeDefaults.bottomWidthDp,
        outerRim = ComposeGlassRuntimeDefaults.outerRim,
        bottomMass = ComposeGlassRuntimeDefaults.bottomMass,
        sideLight = ComposeGlassRuntimeDefaults.sideLight,
        radius = ComposeGlassRuntimeDefaults.radius,
        ribbon = ComposeGlassRuntimeDefaults.ribbon
    )
    ComposeGlassPreset.Crystal -> ComposeGlassStyle(preset, 1.00f, 0.86f, 0.22f, 0.62f, 0.92f, 0.90f, 0.28f, 0.20f, 0.18f, 1.62f, 1.40f, 0.82f, 0.72f, 1.50f, 0.82f, 0.52f, 0.08f, 48f, 0.18f)
    ComposeGlassPreset.Dense -> ComposeGlassStyle(preset, 0.78f, 1.18f, 0.72f, 0.86f, 0.36f, 1.16f, 0.72f, 0.62f, 0.36f, 0.96f, 1.10f, 0.20f, 0.50f, 1.40f, 0.36f, 1.05f, 0.06f, 42f, 0.12f)
    ComposeGlassPreset.Aurora -> ComposeGlassStyle(preset, 0.94f, 1.02f, 0.46f, 0.70f, 0.82f, 1.12f, 0.48f, 0.42f, 0.26f, 1.18f, 1.20f, 0.72f, 0.62f, 1.60f, 0.58f, 0.78f, 0.08f, 46f, 0.34f)
}
