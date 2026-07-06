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
) {
    internal fun normalized(): ComposeGlassMotionStyle = copy(
        master = master.coerceIn(0f, 1.5f),
        deformation = deformation.coerceIn(0f, 1.5f),
        touchLight = touchLight.coerceIn(0f, 1.8f),
        prism = prism.coerceIn(0f, 1.5f),
        sweep = sweep.coerceIn(0f, 1.5f),
        rebound = rebound.coerceIn(0f, 1.5f),
        afterglow = afterglow.coerceIn(0f, 1.5f),
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
    private var baseStyle: ComposeGlassStyle = defaultComposeGlassStyle()

    var style by mutableStateOf(motionAppliedComposeGlassStyle(baseStyle, defaultComposeGlassMotionStyle()))
        private set

    var motionStyle by mutableStateOf(defaultComposeGlassMotionStyle())
        private set

    fun update(next: ComposeGlassStyle) {
        baseStyle = next
        style = motionAppliedComposeGlassStyle(baseStyle, motionStyle)
    }

    fun updateMotion(next: ComposeGlassMotionStyle) {
        motionStyle = next.normalized()
        style = motionAppliedComposeGlassStyle(baseStyle, motionStyle)
    }

    fun usePreset(preset: ComposeGlassPreset) {
        baseStyle = defaultComposeGlassStyle(preset)
        style = motionAppliedComposeGlassStyle(baseStyle, motionStyle)
    }

    fun reset() {
        baseStyle = defaultComposeGlassStyle(baseStyle.preset)
        style = motionAppliedComposeGlassStyle(baseStyle, motionStyle)
    }

    fun resetMotion() {
        motionStyle = defaultComposeGlassMotionStyle()
        style = motionAppliedComposeGlassStyle(baseStyle, motionStyle)
    }

    fun resetAll() {
        baseStyle = defaultComposeGlassStyle()
        motionStyle = defaultComposeGlassMotionStyle()
        style = motionAppliedComposeGlassStyle(baseStyle, motionStyle)
    }
}

private fun defaultComposeGlassMotionStyle(): ComposeGlassMotionStyle = ComposeGlassMotionStyle()

private fun motionAppliedComposeGlassStyle(
    base: ComposeGlassStyle,
    motion: ComposeGlassMotionStyle
): ComposeGlassStyle {
    val normalized = motion.normalized()
    val master = normalized.master.coerceIn(0f, 1.5f)
    val deformation = normalized.deformation.coerceIn(0f, 1.5f) * master
    val touchLight = normalized.touchLight.coerceIn(0f, 1.8f) * master
    val prism = normalized.prism.coerceIn(0f, 1.5f) * master
    val sweep = normalized.sweep.coerceIn(0f, 1.5f) * master
    val rebound = normalized.rebound.coerceIn(0f, 1.5f) * master
    val afterglow = normalized.afterglow.coerceIn(0f, 1.5f) * master
    val quietLift = (1.08f - master * 0.16f).coerceIn(0.74f, 1.18f)
    val lightLift = (0.42f + touchLight * 0.58f).coerceIn(0f, 1.85f)
    val prismLift = (0.18f + prism * 0.82f).coerceIn(0f, 1.65f)
    val sweepLift = (0.10f + sweep * 0.90f).coerceIn(0f, 1.70f)
    val afterglowLift = (0.34f + afterglow * 0.66f).coerceIn(0f, 1.70f)
    val reboundLift = (0.88f + rebound * 0.10f).coerceIn(0.82f, 1.10f)
    val deformationLift = (0.92f + deformation * 0.12f).coerceIn(0.88f, 1.14f)

    return base.copy(
        backdropHighlight = (base.backdropHighlight * (0.54f + touchLight * 0.34f)).coerceIn(0f, 1.85f),
        quiet = (base.quiet * quietLift).coerceIn(0f, 1.45f),
        bodyAbsorption = (base.bodyAbsorption * (0.90f + deformation * 0.10f)).coerceIn(0f, 1.40f),
        lowerBodyMass = (base.lowerBodyMass * (0.92f + deformation * 0.12f)).coerceIn(0f, 1.50f),
        innerTransition = (base.innerTransition * (0.82f + touchLight * 0.16f + prism * 0.10f)).coerceIn(0f, 1.40f),
        topLight = (base.topLight * lightLift).coerceIn(0f, 3.20f),
        topWidthDp = (base.topWidthDp * (0.92f + touchLight * 0.10f)).coerceIn(0.04f, 6.00f),
        topVariation = ((base.topVariation + 0.52f * prismLift + 0.46f * sweepLift) * master.coerceIn(0f, 1.25f)).coerceIn(0f, 3.00f),
        bottomLight = (base.bottomLight * afterglowLift).coerceIn(0f, 2.80f),
        bottomWidthDp = (base.bottomWidthDp * (0.92f + afterglow * 0.12f)).coerceIn(0.04f, 5.50f),
        outerRim = (base.outerRim * (0.58f + prism * 0.30f + sweep * 0.18f)).coerceIn(0f, 2.50f),
        bottomMass = (base.bottomMass * (0.88f + afterglow * 0.12f + deformation * 0.08f)).coerceIn(0f, 2.20f),
        sideLight = (base.sideLight + prism * 0.055f + sweep * 0.035f).coerceIn(0f, 0.70f),
        radius = (base.radius * deformationLift * reboundLift).coerceIn(16f, 76f),
        ribbon = (base.ribbon + prismLift * 0.16f + sweepLift * 0.12f).coerceIn(0f, 1.00f),
    )
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
        bottomWidthDp = 1.45f,
        outerRim = 0.42f,
        bottomMass = 0.82f,
        sideLight = 0.06f,
        radius = 44f,
        ribbon = 0.08f
    )
}
