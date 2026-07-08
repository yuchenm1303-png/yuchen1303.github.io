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
    val master: Float = 3.8f,
    val deformation: Float = 0.86f,
    val touchLight: Float = 4.6f,
    val prism: Float = 0f,
    val sweep: Float = 4.2f,
    val rebound: Float = 1.85f,
    val afterglow: Float = 5.4f,
    val speed: Float = 4.85f,
    val tapImpulse: Float = 3.25f,
    val releaseCohesion: Float = 2.20f,
    val fieldContinuity: Float = 1.65f,
    val sweepMomentum: Float = 4.10f,
) {
    internal fun normalized(): ComposeGlassMotionStyle = copy(
        master = master.coerceIn(0f, 120f),
        deformation = deformation.coerceIn(0f, 120f),
        touchLight = touchLight.coerceIn(0f, 160f),
        prism = 0f,
        sweep = sweep.coerceIn(0f, 160f),
        rebound = rebound.coerceIn(0f, 120f),
        afterglow = afterglow.coerceIn(0f, 160f),
        speed = speed.coerceIn(0.02f, 40f),
        tapImpulse = tapImpulse.coerceIn(0f, 120f),
        releaseCohesion = releaseCohesion.coerceIn(0f, 120f),
        fieldContinuity = fieldContinuity.coerceIn(0f, 120f),
        sweepMomentum = sweepMomentum.coerceIn(0f, 120f),
    )
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

    var capsuleTuning by mutableStateOf(defaultOrdinaryGlassCapsuleTuning())
        private set

    var pressureOpticsTuning by mutableStateOf(defaultOrdinaryGlassPressureOpticsTuning())
        private set

    fun update(next: ComposeGlassStyle) {
        style = next
    }

    fun updateMotion(next: ComposeGlassMotionStyle) {
        motionStyle = next.normalized()
    }

    fun updateCapsuleTuning(next: OrdinaryGlassCapsuleTuning) {
        capsuleTuning = next.normalized()
    }

    fun updatePressureOpticsTuning(next: OrdinaryGlassPressureOpticsTuning) {
        pressureOpticsTuning = next.normalized()
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
        capsuleTuning = defaultOrdinaryGlassCapsuleTuning()
    }

    fun resetPressureOpticsTuning() {
        pressureOpticsTuning = defaultOrdinaryGlassPressureOpticsTuning()
    }

    fun resetAll() {
        style = defaultComposeGlassStyle()
        motionStyle = defaultComposeGlassMotionStyle()
        capsuleTuning = defaultOrdinaryGlassCapsuleTuning()
        pressureOpticsTuning = defaultOrdinaryGlassPressureOpticsTuning()
    }
}

private fun defaultComposeGlassMotionStyle(): ComposeGlassMotionStyle = ComposeGlassMotionStyle()

private fun defaultOrdinaryGlassCapsuleTuning(): OrdinaryGlassCapsuleTuning = OrdinaryGlassCapsuleTuning()

private fun defaultOrdinaryGlassPressureOpticsTuning(): OrdinaryGlassPressureOpticsTuning = OrdinaryGlassPressureOpticsTuning()

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
