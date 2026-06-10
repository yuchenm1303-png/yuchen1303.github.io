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
    val frost: Float,
    val tint: Float,
    val quiet: Float,
    val topLight: Float,
    val topWidthDp: Float,
    val topVariation: Float,
    val bottomLight: Float,
    val bottomWidthDp: Float,
    val edgeDepthDp: Float,
    val innerBevel: Float,
    val outerRim: Float,
    val bottomMass: Float,
    val sideBevel: Float,
    val sideLight: Float,
    val radius: Float,
    val shadow: Float,
    val ribbon: Float
) {
    val density: Float get() = frost
    val edge: Float get() = topLight
    val backdropAlpha: Float get() = backdrop.coerceIn(0.32f, 1.55f)
    val blurScale: Float get() = (0.78f + frost * 0.20f).coerceIn(0.42f, 1.90f)
    val frostAlpha: Float get() = frost.coerceIn(0.02f, 2.80f)
    val rimAlpha: Float get() = outerRim.coerceIn(0.02f, 3.20f)
    val innerRimAlpha: Float get() = innerBevel.coerceIn(0f, 2.80f)
    val topHighlight: Float get() = topLight.coerceIn(0.02f, 3.40f)
    val bottomShadow: Float get() = bottomMass.coerceIn(0.02f, 3.00f)
    val cornerGlint: Float get() = topVariation.coerceIn(0f, 3.00f)
    val strokeWidth: Float get() = (0.42f + outerRim * 0.24f).coerceIn(0.10f, 2.30f)
    val shadowAlpha: Float get() = shadow.coerceIn(0f, 2.60f)
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
        backdrop = 1.12f,
        frost = 0.48f,
        tint = 0.30f,
        quiet = 0.80f,
        topLight = 1.18f,
        topWidthDp = 2.60f,
        topVariation = 0.70f,
        bottomLight = 0.45f,
        bottomWidthDp = 2.40f,
        edgeDepthDp = 2.40f,
        innerBevel = 0.55f,
        outerRim = 0.62f,
        bottomMass = 0.75f,
        sideBevel = 0.35f,
        sideLight = 0.14f,
        radius = 48f,
        shadow = 0.55f,
        ribbon = 0.35f
    )
    ComposeGlassPreset.Frost -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.96f,
        frost = ComposeGlassRuntimeDefaults.frost,
        tint = ComposeGlassRuntimeDefaults.tint,
        quiet = ComposeGlassRuntimeDefaults.quiet,
        topLight = ComposeGlassRuntimeDefaults.topLight,
        topWidthDp = ComposeGlassRuntimeDefaults.topWidthDp,
        topVariation = ComposeGlassRuntimeDefaults.topVariation,
        bottomLight = ComposeGlassRuntimeDefaults.bottomLight,
        bottomWidthDp = ComposeGlassRuntimeDefaults.bottomWidthDp,
        edgeDepthDp = ComposeGlassRuntimeDefaults.edgeDepthDp,
        innerBevel = ComposeGlassRuntimeDefaults.innerBevel,
        outerRim = ComposeGlassRuntimeDefaults.outerRim,
        bottomMass = ComposeGlassRuntimeDefaults.bottomMass,
        sideBevel = ComposeGlassRuntimeDefaults.sideBevel,
        sideLight = ComposeGlassRuntimeDefaults.sideLight,
        radius = ComposeGlassRuntimeDefaults.radius,
        shadow = ComposeGlassRuntimeDefaults.shadow,
        ribbon = ComposeGlassRuntimeDefaults.ribbon
    )
    ComposeGlassPreset.Crystal -> ComposeGlassStyle(
        preset = preset,
        backdrop = 1.06f,
        frost = 0.68f,
        tint = 0.40f,
        quiet = 0.86f,
        topLight = 1.86f,
        topWidthDp = 2.30f,
        topVariation = 1.05f,
        bottomLight = 0.68f,
        bottomWidthDp = 2.10f,
        edgeDepthDp = 2.20f,
        innerBevel = 0.48f,
        outerRim = 1.06f,
        bottomMass = 0.78f,
        sideBevel = 0.28f,
        sideLight = 0.12f,
        radius = 48f,
        shadow = 0.70f,
        ribbon = 0.26f
    )
    ComposeGlassPreset.Dense -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.86f,
        frost = 1.38f,
        tint = 0.94f,
        quiet = 1.13f,
        topLight = 1.08f,
        topWidthDp = 4.00f,
        topVariation = 0.52f,
        bottomLight = 0.38f,
        bottomWidthDp = 3.70f,
        edgeDepthDp = 4.80f,
        innerBevel = 1.18f,
        outerRim = 0.54f,
        bottomMass = 1.45f,
        sideBevel = 0.70f,
        sideLight = 0.10f,
        radius = 42f,
        shadow = 0.95f,
        ribbon = 0.20f
    )
    ComposeGlassPreset.Aurora -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.98f,
        frost = 0.82f,
        tint = 0.82f,
        quiet = 1.22f,
        topLight = 1.30f,
        topWidthDp = 2.15f,
        topVariation = 1.05f,
        bottomLight = 0.48f,
        bottomWidthDp = 2.00f,
        edgeDepthDp = 3.80f,
        innerBevel = 1.05f,
        outerRim = 0.46f,
        bottomMass = 1.32f,
        sideBevel = 0.48f,
        sideLight = 0.07f,
        radius = 44f,
        shadow = 0.62f,
        ribbon = 0.10f
    )
}
