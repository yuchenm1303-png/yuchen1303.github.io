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
    val density: Float,
    val edge: Float
) {
    val backdropAlpha: Float get() = (backdrop * preset.profile.backdropScale).coerceIn(0.32f, 1.55f)
    val blurScale: Float get() = (preset.profile.blurScale * (0.78f + density * 0.20f)).coerceIn(0.42f, 1.90f)
    val frostAlpha: Float get() = (preset.profile.frostScale * density).coerceIn(0.02f, 2.80f)
    val rimAlpha: Float get() = (preset.profile.rimScale * edge).coerceIn(0.02f, 3.20f)
    val innerRimAlpha: Float get() = (preset.profile.innerRimScale * edge * (0.72f + density * 0.12f)).coerceIn(0f, 2.80f)
    val topHighlight: Float get() = (preset.profile.highlightScale * edge).coerceIn(0.02f, 3.40f)
    val bottomShadow: Float get() = (preset.profile.depthScale * (0.84f + density * 0.10f)).coerceIn(0.02f, 3.00f)
    val cornerGlint: Float get() = (preset.profile.glintScale * edge).coerceIn(0f, 3.00f)
    val strokeWidth: Float get() = (preset.profile.strokeScale * (0.72f + edge * 0.24f + density * 0.08f)).coerceIn(0.10f, 2.30f)
    val shadowAlpha: Float get() = (preset.profile.shadowScale * (0.88f + density * 0.08f)).coerceIn(0f, 2.60f)
    val radiusScale: Float get() = preset.profile.radiusScale
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

private data class ComposeGlassPresetProfile(
    val backdropScale: Float,
    val blurScale: Float,
    val frostScale: Float,
    val rimScale: Float,
    val innerRimScale: Float,
    val highlightScale: Float,
    val depthScale: Float,
    val glintScale: Float,
    val strokeScale: Float,
    val shadowScale: Float,
    val radiusScale: Float
)

private val ComposeGlassPreset.profile: ComposeGlassPresetProfile
    get() = when (this) {
        ComposeGlassPreset.Clear -> ComposeGlassPresetProfile(
            backdropScale = 1.08f,
            blurScale = 0.82f,
            frostScale = 0.62f,
            rimScale = 0.86f,
            innerRimScale = 0.38f,
            highlightScale = 1.06f,
            depthScale = 0.64f,
            glintScale = 0.74f,
            strokeScale = 0.82f,
            shadowScale = 0.58f,
            radiusScale = 1.04f
        )
        ComposeGlassPreset.Frost -> ComposeGlassPresetProfile(
            backdropScale = 0.96f,
            blurScale = 0.86f,
            frostScale = 1.00f,
            rimScale = 0.40f,
            innerRimScale = 0.00f,
            highlightScale = 1.14f,
            depthScale = 0.165f,
            glintScale = 0.46f,
            strokeScale = 0.42f,
            shadowScale = 0.31f,
            radiusScale = 0.92f
        )
        ComposeGlassPreset.Crystal -> ComposeGlassPresetProfile(
            backdropScale = 1.03f,
            blurScale = 0.98f,
            frostScale = 0.78f,
            rimScale = 1.44f,
            innerRimScale = 1.08f,
            highlightScale = 1.68f,
            depthScale = 0.92f,
            glintScale = 1.42f,
            strokeScale = 1.10f,
            shadowScale = 0.84f,
            radiusScale = 1.05f
        )
        ComposeGlassPreset.Dense -> ComposeGlassPresetProfile(
            backdropScale = 0.78f,
            blurScale = 1.26f,
            frostScale = 1.58f,
            rimScale = 1.02f,
            innerRimScale = 0.70f,
            highlightScale = 0.98f,
            depthScale = 1.58f,
            glintScale = 0.58f,
            strokeScale = 1.08f,
            shadowScale = 1.42f,
            radiusScale = 0.96f
        )
        ComposeGlassPreset.Aurora -> ComposeGlassPresetProfile(
            backdropScale = 0.98f,
            blurScale = 1.05f,
            frostScale = 1.02f,
            rimScale = 1.34f,
            innerRimScale = 0.92f,
            highlightScale = 1.42f,
            depthScale = 1.12f,
            glintScale = 1.62f,
            strokeScale = 1.06f,
            shadowScale = 1.05f,
            radiusScale = 1.08f
        )
    }

private fun defaultComposeGlassStyle(preset: ComposeGlassPreset = ComposeGlassPreset.Frost): ComposeGlassStyle = when (preset) {
    ComposeGlassPreset.Clear -> ComposeGlassStyle(
        preset = preset,
        backdrop = 1.12f,
        density = 0.74f,
        edge = 1.08f
    )
    ComposeGlassPreset.Frost -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.96f,
        density = 0.15f,
        edge = 1.00f
    )
    ComposeGlassPreset.Crystal -> ComposeGlassStyle(
        preset = preset,
        backdrop = 1.06f,
        density = 0.82f,
        edge = 1.34f
    )
    ComposeGlassPreset.Dense -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.86f,
        density = 1.30f,
        edge = 0.98f
    )
    ComposeGlassPreset.Aurora -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.98f,
        density = 1.00f,
        edge = 1.24f
    )
}
