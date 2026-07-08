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
    val master: Float = 5.82f,
    val deformation: Float = 1.214f,
    val touchLight: Float = 1.532f,
    val prism: Float = 19.114f,
    val sweep: Float = 14.734f,
    val rebound: Float = 6.535f,
    val afterglow: Float = 1.645f,
    val speed: Float = 0.171f,
    val tapImpulse: Float = 0f,
    val releaseCohesion: Float = 0f,
    val fieldContinuity: Float = 0f,
    val sweepMomentum: Float = 0f,
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
    val smallBoost: Float = 24.0f,
    val largeDamp: Float = 4.0f,
    val pivotPx: Float = 520f,
    val visualPx: Float = 18.0f,
    val lightBoost: Float = 24.0f,
    val smallThresholdPx: Float = 520f,
    val largeThresholdPx: Float = 536f,
    val wideAspectStart: Float = 1.55f,
    val wideAspectEnd: Float = 4.80f,
) {
    internal fun normalized(): OrdinaryGlassSizeAdaptiveTuning {
        val small = smallThresholdPx.coerceIn(32f, 520f)
        val large = largeThresholdPx.coerceIn((small + 16f).coerceAtMost(2000f), 2400f)
        val wideStart = wideAspectStart.coerceIn(1.00f, 5.50f)
        val wideEnd = wideAspectEnd.coerceIn((wideStart + 0.20f).coerceAtMost(12f), 14f)
        return copy(
            smallBoost = smallBoost.coerceIn(0f, 96f),
            largeDamp = largeDamp.coerceIn(0f, 16f),
            pivotPx = pivotPx.coerceIn(32f, 2000f),
            visualPx = visualPx.coerceIn(0.2f, 96f),
            lightBoost = lightBoost.coerceIn(0f, 96f),
            smallThresholdPx = small,
            largeThresholdPx = large,
            wideAspectStart = wideStart,
            wideAspectEnd = wideEnd,
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

    fun update(next: ComposeGlassStyle) {
        style = next
    }

    fun updateMotion(next: ComposeGlassMotionStyle) {
        motionStyle = next.storageClamped()
    }

    fun updateCapsuleTuning(next: OrdinaryGlassCapsuleTuning) {
        capsuleTuning = next.normalized()
    }

    fun updatePressureOpticsTuning(next: OrdinaryGlassPressureOpticsTuning) {
        pressureOpticsTuning = next.normalized()
    }

    fun updateSizeAdaptiveTuning(next: OrdinaryGlassSizeAdaptiveTuning) {
        val normalized = next.normalized()
        sizeAdaptiveTuning = normalized
        applySizeAdaptiveTuningToSingleCard(normalized)
    }

    private fun applySizeAdaptiveTuningToSingleCard(size: OrdinaryGlassSizeAdaptiveTuning) {
        capsuleTuning = singleCardCapsuleTuningFor(size)
        pressureOpticsTuning = singleCardPressureOpticsFor(size)
    }

    fun usePreset(preset: ComposeGlassPreset) {
        style = defaultComposeGlassStyle(preset)
    }

    fun reset() {
        style = defaultComposeGlassStyle(style.preset)
    }

    fun resetMotion() {
        motionStyle = defaultComposeGlassMotionStyle()
    }

    fun resetCapsuleTuning() {
        capsuleTuning = singleCardCapsuleTuningFor(sizeAdaptiveTuning)
    }

    fun resetPressureOpticsTuning() {
        pressureOpticsTuning = singleCardPressureOpticsFor(sizeAdaptiveTuning)
    }

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
    val small = normalized.smallBoost.coerceIn(0f, 96f)
    val large = normalized.largeDamp.coerceIn(0f, 16f)
    val visual = normalized.visualPx.coerceIn(0.2f, 96f)
    val light = normalized.lightBoost.coerceIn(0f, 96f)
    val smallBand = ((normalized.smallThresholdPx - 96f) / 240f).coerceIn(0f, 1.60f)
    val largeBand = ((normalized.largeThresholdPx - normalized.smallThresholdPx) / 760f).coerceIn(0.02f, 2.40f)
    val wideBand = ((normalized.wideAspectEnd - normalized.wideAspectStart) / 4.20f).coerceIn(0.18f, 2.80f)
    val wideStartBias = ((1.80f - normalized.wideAspectStart) / 0.80f).coerceIn(-0.40f, 1.00f)
    return defaultOrdinaryGlassCapsuleTuning().copy(
        compactBoost = (1.0f + small * (0.36f + smallBand * 0.16f)).coerceIn(0.20f, 58.00f),
        elongatedX = (large * (1.12f + wideBand * 0.22f + wideStartBias * 0.18f)).coerceIn(0f, 30.00f),
        elongatedY = (0.08f + small * 0.030f + wideBand * 0.035f).coerceIn(0.02f, 8.00f),
        basePx = (0.012f + visual * 0.0048f * (0.88f + smallBand * 0.14f)).coerceIn(0.006f, 1.40f),
        tapPx = (0.038f + visual * 0.0078f * (0.90f + smallBand * 0.16f)).coerceIn(0.018f, 2.40f),
        tapPop = (0.78f + visual * 0.047f * (1.02f + smallBand * 0.18f)).coerceIn(0.30f, 18.00f),
        tapCarry = (0.20f + small * 0.070f + light * 0.032f).coerceIn(0.06f, 10.00f),
        sticky = (0.014f + light * 0.018f + smallBand * 0.022f).coerceIn(0.004f, 2.00f),
        sink = (0.050f + visual * 0.0092f * (0.92f + largeBand * 0.10f)).coerceIn(0.02f, 4.00f),
        settle = (0.16f + large * 0.32f + largeBand * 0.10f).coerceIn(0.08f, 5.60f),
    ).normalized()
}

private fun singleCardPressureOpticsFor(size: OrdinaryGlassSizeAdaptiveTuning): OrdinaryGlassPressureOpticsTuning {
    val normalized = size.normalized()
    val small = normalized.smallBoost.coerceIn(0f, 96f)
    val large = normalized.largeDamp.coerceIn(0f, 16f)
    val visual = normalized.visualPx.coerceIn(0.2f, 96f)
    val light = normalized.lightBoost.coerceIn(0f, 96f)
    val smallBand = ((normalized.smallThresholdPx - 96f) / 240f).coerceIn(0f, 1.60f)
    val largeBand = ((normalized.largeThresholdPx - normalized.smallThresholdPx) / 760f).coerceIn(0.02f, 2.40f)
    val wideBand = ((normalized.wideAspectEnd - normalized.wideAspectStart) / 4.20f).coerceIn(0.18f, 2.80f)
    val pivotScale = (normalized.largeThresholdPx / 520f).coerceIn(0.18f, 5.00f)
    return defaultOrdinaryGlassPressureOpticsTuning().copy(
        fieldIntensity = (4.0f + light * 7.4f + small * (0.58f + smallBand * 0.16f)).coerceIn(0f, 480f),
        fieldSpread = (2.8f + visual * 0.34f + pivotScale * 0.72f - large * 0.78f + largeBand * 0.34f).coerceIn(0f, 240f),
        fieldSoftness = (3.6f + visual * 0.31f + large * 1.58f + largeBand * 0.52f).coerceIn(0f, 240f),
        fieldUniformity = (3.0f + large * 1.90f + wideBand * 0.50f).coerceIn(0f, 180f),
        fieldFollow = (0.08f + small * 0.016f + smallBand * 0.024f).coerceIn(0f, 6.00f),
        edgeIntensity = (3.8f + light * 5.8f + small * 0.52f).coerceIn(0f, 480f),
        edgeWidth = (1.1f + visual * 0.20f + small * 0.18f - large * 0.18f - wideBand * 0.10f).coerceIn(0f, 120f),
        edgeSoftness = (3.4f + large * 1.92f + wideBand * 0.84f).coerceIn(0f, 180f),
        edgeBloom = (3.4f + light * 3.6f + visual * 0.10f + smallBand * 0.42f).coerceIn(0f, 360f),
    ).normalized()
}

private fun defaultComposeGlassStyle(preset: ComposeGlassPreset = ComposeGlassPreset.Frost): ComposeGlassStyle = when (preset) {
    ComposeGlassPreset.Clear -> ComposeGlassStyle(
        preset = preset,
        backdrop = 1.08f,
        backdropBlur = 0.74f,
        backdropDim = 0.18f,
        backdropMilk = 0.42f,
        backdropHighlight = 0.55f,
        quiet = 0.82f,
        bodyAbsorption = 0.22f,
        lowerBodyMass = 0.18f,
        innerTransition = 0.10f,
        topLight = 1.18f,
        topWidthDp = 2.20f,
        topVariation = 0.48f,
        bottomLight = 0.48f,
        bottomWidthDp = 1.80f,
        outerRim = 0.48f,
        bottomMass = 0.52f,
        sideLight = 0.10f,
        radius = 48f,
        ribbon = 0.28f
    )
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
    ComposeGlassPreset.Crystal -> ComposeGlassStyle(
        preset = preset,
        backdrop = 1.00f,
        backdropBlur = 0.86f,
        backdropDim = 0.22f,
        backdropMilk = 0.62f,
        backdropHighlight = 0.92f,
        quiet = 0.90f,
        bodyAbsorption = 0.28f,
        lowerBodyMass = 0.20f,
        innerTransition = 0.18f,
        topLight = 1.62f,
        topWidthDp = 1.40f,
        topVariation = 0.82f,
        bottomLight = 0.72f,
        bottomWidthDp = 1.50f,
        outerRim = 0.82f,
        bottomMass = 0.52f,
        sideLight = 0.08f,
        radius = 48f,
        ribbon = 0.18f
    )
    ComposeGlassPreset.Dense -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.78f,
        backdropBlur = 1.18f,
        backdropDim = 0.72f,
        backdropMilk = 0.86f,
        backdropHighlight = 0.36f,
        quiet = 1.16f,
        bodyAbsorption = 0.72f,
        lowerBodyMass = 0.62f,
        innerTransition = 0.36f,
        topLight = 0.96f,
        topWidthDp = 1.10f,
        topVariation = 0.20f,
        bottomLight = 0.50f,
        bottomWidthDp = 1.40f,
        outerRim = 0.36f,
        bottomMass = 1.05f,
        sideLight = 0.06f,
        radius = 42f,
        ribbon = 0.12f
    )
    ComposeGlassPreset.Aurora -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.94f,
        backdropBlur = 1.02f,
        backdropDim = 0.46f,
        backdropMilk = 0.70f,
        backdropHighlight = 0.82f,
        quiet = 1.12f,
        bodyAbsorption = 0.48f,
        lowerBodyMass = 0.42f,
        innerTransition = 0.26f,
        topLight = 1.18f,
        topWidthDp = 1.20f,
        topVariation = 0.72f,
        bottomLight = 0.62f,
        bottomWidthDp = 1.60f,
        outerRim = 0.58f,
        bottomMass = 0.78f,
        sideLight = 0.08f,
        radius = 46f,
        ribbon = 0.34f
    )
}
