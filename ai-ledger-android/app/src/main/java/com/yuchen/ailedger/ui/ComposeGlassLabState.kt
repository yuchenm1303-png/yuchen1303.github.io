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
    val tint: Float,
    val edge: Float,
    val depth: Float,
    val grain: Float
) {
    val backdropAlpha: Float get() = (backdrop * preset.profile.backdropScale).coerceIn(0.32f, 1.55f)
    val blurScale: Float get() = (preset.profile.blurScale * (0.78f + density * 0.20f + grain * 0.035f)).coerceIn(0.42f, 1.90f)
    val frostAlpha: Float get() = (preset.profile.frostScale * density).coerceIn(0.05f, 2.80f)
    val coolTint: Float get() = (preset.profile.tintScale * tint).coerceIn(0f, 3.00f)
    val rimAlpha: Float get() = (preset.profile.rimScale * edge * (0.92f + grain * 0.035f)).coerceIn(0.10f, 3.20f)
    val innerRimAlpha: Float get() = (preset.profile.innerRimScale * edge * (0.72f + density * 0.12f)).coerceIn(0f, 2.80f)
    val topHighlight: Float get() = (preset.profile.highlightScale * edge * (0.85f + tint * 0.08f)).coerceIn(0.08f, 3.40f)
    val bottomShadow: Float get() = (preset.profile.depthScale * depth).coerceIn(0.08f, 3.00f)
    val cornerGlint: Float get() = (preset.profile.glintScale * edge * (0.72f + tint * 0.10f)).coerceIn(0f, 3.00f)
    val motionGlint: Float get() = (preset.profile.motionScale * (0.40f + grain * 0.55f + edge * 0.08f)).coerceIn(0f, 2.60f)
    val strokeWidth: Float get() = (preset.profile.strokeScale * (0.72f + edge * 0.24f + density * 0.08f)).coerceIn(0.42f, 2.30f)
    val shadowAlpha: Float get() = (preset.profile.shadowScale * depth).coerceIn(0f, 2.60f)
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
    val tintScale: Float,
    val rimScale: Float,
    val innerRimScale: Float,
    val highlightScale: Float,
    val depthScale: Float,
    val glintScale: Float,
    val motionScale: Float,
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
            tintScale = 0.54f,
            rimScale = 0.86f,
            innerRimScale = 0.38f,
            highlightScale = 1.06f,
            depthScale = 0.64f,
            glintScale = 0.74f,
            motionScale = 0.48f,
            strokeScale = 0.82f,
            shadowScale = 0.58f,
            radiusScale = 1.04f
        )
        ComposeGlassPreset.Frost -> ComposeGlassPresetProfile(
            backdropScale = 0.94f,
            blurScale = 1.10f,
            frostScale = 1.22f,
            tintScale = 0.82f,
            rimScale = 1.10f,
            innerRimScale = 0.82f,
            highlightScale = 1.18f,
            depthScale = 1.04f,
            glintScale = 0.82f,
            motionScale = 0.62f,
            strokeScale = 1.00f,
            shadowScale = 0.92f,
            radiusScale = 1.00f
        )
        ComposeGlassPreset.Crystal -> ComposeGlassPresetProfile(
            backdropScale = 1.03f,
            blurScale = 0.98f,
            frostScale = 0.78f,
            tintScale = 1.05f,
            rimScale = 1.44f,
            innerRimScale = 1.08f,
            highlightScale = 1.68f,
            depthScale = 0.92f,
            glintScale = 1.42f,
            motionScale = 0.92f,
            strokeScale = 1.10f,
            shadowScale = 0.84f,
            radiusScale = 1.05f
        )
        ComposeGlassPreset.Dense -> ComposeGlassPresetProfile(
            backdropScale = 0.78f,
            blurScale = 1.26f,
            frostScale = 1.58f,
            tintScale = 1.12f,
            rimScale = 1.02f,
            innerRimScale = 0.70f,
            highlightScale = 0.98f,
            depthScale = 1.58f,
            glintScale = 0.58f,
            motionScale = 0.42f,
            strokeScale = 1.08f,
            shadowScale = 1.42f,
            radiusScale = 0.96f
        )
        ComposeGlassPreset.Aurora -> ComposeGlassPresetProfile(
            backdropScale = 0.98f,
            blurScale = 1.05f,
            frostScale = 1.02f,
            tintScale = 1.72f,
            rimScale = 1.34f,
            innerRimScale = 0.92f,
            highlightScale = 1.42f,
            depthScale = 1.12f,
            glintScale = 1.62f,
            motionScale = 1.28f,
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
        tint = 0.72f,
        edge = 1.08f,
        depth = 0.78f,
        grain = 0.32f
    )
    ComposeGlassPreset.Frost -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.96f,
        density = 1.12f,
        tint = 0.88f,
        edge = 1.12f,
        depth = 1.08f,
        grain = 0.42f
    )
    ComposeGlassPreset.Crystal -> ComposeGlassStyle(
        preset = preset,
        backdrop = 1.06f,
        density = 0.82f,
        tint = 0.90f,
        edge = 1.34f,
        depth = 0.88f,
        grain = 0.28f
    )
    ComposeGlassPreset.Dense -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.86f,
        density = 1.30f,
        tint = 0.96f,
        edge = 0.98f,
        depth = 1.36f,
        grain = 0.54f
    )
    ComposeGlassPreset.Aurora -> ComposeGlassStyle(
        preset = preset,
        backdrop = 0.98f,
        density = 1.00f,
        tint = 1.26f,
        edge = 1.24f,
        depth = 1.08f,
        grain = 0.72f
    )
}
