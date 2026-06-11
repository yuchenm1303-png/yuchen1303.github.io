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

    fun update(next: ComposeGlassStyle) {
        style = next
    }

    fun usePreset(preset: ComposeGlassPreset) {
        style = defaultComposeGlassStyle(preset)
    }

    fun reset() {
        style = defaultComposeGlassStyle(style.preset)
    }

    fun resetAll() {
        style = defaultComposeGlassStyle()
    }
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
