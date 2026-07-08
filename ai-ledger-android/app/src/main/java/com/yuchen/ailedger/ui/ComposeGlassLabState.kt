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
    val master: Float = 1f,
    val deformation: Float = 0.92f,
    val touchLight: Float = 1f,
    val prism: Float = 0.68f,
    val sweep: Float = 0.90f,
    val rebound: Float = 0.90f,
    val afterglow: Float = 0.86f,
    val speed: Float = 1.12f,
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
        compactBoost = compactBoost.coerceIn(0f, 12f),
        elongatedX = elongatedX.coerceIn(0f, 8f),
        elongatedY = elongatedY.coerceIn(0f, 8f),
        basePx = basePx.coerceIn(0.001f, 1.20f),
        tapPx = tapPx.coerceIn(0f, 1.20f),
        tapPop = tapPop.coerceIn(0.05f, 40f),
        tapCarry = tapCarry.coerceIn(0f, 24f),
        sticky = sticky.coerceIn(0f, 4f),
        sink = sink.coerceIn(0f, 24f),
        settle = settle.coerceIn(0f, 12f),
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
        fieldIntensity = fieldIntensity.coerceIn(0f, 160f),
        fieldSpread = fieldSpread.coerceIn(0f, 160f),
        fieldSoftness = fieldSoftness.coerceIn(0f, 160f),
        fieldUniformity = fieldUniformity.coerceIn(0f, 160f),
        fieldFollow = fieldFollow.coerceIn(0f, 8f),
        edgeIntensity = edgeIntensity.coerceIn(0f, 160f),
        edgeWidth = edgeWidth.coerceIn(0f, 80f),
        edgeSoftness = edgeSoftness.coerceIn(0f, 160f),
        edgeBloom = edgeBloom.coerceIn(0f, 160f),
    )
}

data class OrdinaryGlassSizeAdaptiveTuning(
    val smallBoost: Float = 1.75f,
    val largeDamp: Float = 0.62f,
    val pivotPx: Float = 180f,
    val visualPx: Float = 5.40f,
    val lightBoost: Float = 1.08f,
) {
    internal fun normalized(): OrdinaryGlassSizeAdaptiveTuning = copy(
        smallBoost = smallBoost.coerceIn(0f, 8f),
        largeDamp = largeDamp.coerceIn(0f, 1.6f),
        pivotPx = pivotPx.coerceIn(48f, 720f),
        visualPx = visualPx.coerceIn(0.4f, 32f),
        lightBoost = lightBoost.coerceIn(0f, 8f),
    )
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
        /*
         * 单卡绘制路径不再依赖 ParentDraw。PressableGlass 已经在本卡片内按 size 计算
         * capsuleCompact / capsuleElongated，并在 ordinaryPressSurfaceOptics 内按 DrawScope.size
         * 计算光场半径；这里把“尺寸归一化”滑杆转换成 PressableGlass 真正读取的
         * capsuleTuning / pressureOpticsTuning，使小卡增强、大卡压制直接作用到单卡链路。
         */
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
    val small = normalized.smallBoost.coerceIn(0f, 8f)
    val large = normalized.largeDamp.coerceIn(0f, 1.6f)
    val visual = normalized.visualPx.coerceIn(0.4f, 32f)
    val light = normalized.lightBoost.coerceIn(0f, 8f)
    return defaultOrdinaryGlassCapsuleTuning().copy(
        compactBoost = (1.0f + small * 0.86f).coerceIn(0.20f, 8.40f),
        elongatedX = (large * 1.62f).coerceIn(0f, 4.80f),
        elongatedY = (0.10f + small * 0.08f).coerceIn(0.02f, 2.20f),
        basePx = (0.016f + visual * 0.0066f).coerceIn(0.006f, 0.32f),
        tapPx = (0.045f + visual * 0.0138f).coerceIn(0.018f, 0.62f),
        tapPop = (0.86f + visual * 0.064f).coerceIn(0.30f, 6.40f),
        tapCarry = (0.22f + small * 0.105f + light * 0.035f).coerceIn(0.06f, 2.20f),
        sticky = (0.018f + light * 0.026f).coerceIn(0.004f, 0.48f),
        sink = (0.058f + visual * 0.014f).coerceIn(0.02f, 1.20f),
        settle = (0.18f + large * 0.42f).coerceIn(0.08f, 1.20f),
    ).normalized()
}

private fun singleCardPressureOpticsFor(size: OrdinaryGlassSizeAdaptiveTuning): OrdinaryGlassPressureOpticsTuning {
    val normalized = size.normalized()
    val small = normalized.smallBoost.coerceIn(0f, 8f)
    val large = normalized.largeDamp.coerceIn(0f, 1.6f)
    val visual = normalized.visualPx.coerceIn(0.4f, 32f)
    val light = normalized.lightBoost.coerceIn(0f, 8f)
    val pivotScale = (normalized.pivotPx / 180f).coerceIn(0.35f, 4.00f)
    return defaultOrdinaryGlassPressureOpticsTuning().copy(
        fieldIntensity = (4.2f + light * 8.8f + small * 0.72f).coerceIn(0f, 96f),
        fieldSpread = (3.2f + visual * 0.34f + pivotScale * 0.80f - large * 1.20f).coerceIn(0f, 42f),
        fieldSoftness = (3.8f + visual * 0.30f + large * 2.10f).coerceIn(0f, 48f),
        fieldUniformity = (3.2f + large * 2.40f).coerceIn(0f, 40f),
        fieldFollow = (0.10f + small * 0.018f).coerceIn(0f, 1.20f),
        edgeIntensity = (4.0f + light * 6.6f + small * 0.58f).coerceIn(0f, 96f),
        edgeWidth = (1.4f + visual * 0.20f + small * 0.20f - large * 0.34f).coerceIn(0f, 24f),
        edgeSoftness = (3.6f + large * 2.40f).coerceIn(0f, 48f),
        edgeBloom = (3.8f + light * 4.2f + visual * 0.10f).coerceIn(0f, 80f),
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
