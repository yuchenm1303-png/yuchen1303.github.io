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

    fun update(next: ComposeGlassStyle) { style = next }
    fun usePreset(preset: ComposeGlassPreset) { style = defaultComposeGlassStyle(preset) }
    fun reset() { style = defaultComposeGlassStyle(style.preset) }
    fun resetAll() { style = defaultComposeGlassStyle() }
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
        ComposeGlassPreset.Clear -> ComposeGlassPresetProfile(1.08f, 0.82f, 0.62f, 0.86f, 0.38f, 1.06f, 0.64f, 0.74f, 0.82f, 0.58f, 1.04f)
        ComposeGlassPreset.Frost -> ComposeGlassPresetProfile(0.96f, 0.86f, 1.00f, 0.40f, 0.00f, 1.14f, 0.165f, 0.46f, 0.42f, 0.31f, 0.804f)
        ComposeGlassPreset.Crystal -> ComposeGlassPresetProfile(1.03f, 0.98f, 0.78f, 1.44f, 1.08f, 1.68f, 0.92f, 1.42f, 1.10f, 0.84f, 1.05f)
        ComposeGlassPreset.Dense -> ComposeGlassPresetProfile(0.78f, 1.26f, 1.58f, 1.02f, 0.70f, 0.98f, 1.58f, 0.58f, 1.08f, 1.42f, 0.96f)
        ComposeGlassPreset.Aurora -> ComposeGlassPresetProfile(0.98f, 1.05f, 1.02f, 1.34f, 0.92f, 1.42f, 1.12f, 1.62f, 1.06f, 1.05f, 1.08f)
    }

private fun defaultComposeGlassStyle(preset: ComposeGlassPreset = ComposeGlassPreset.Frost): ComposeGlassStyle = when (preset) {
    ComposeGlassPreset.Clear -> ComposeGlassStyle(preset, 1.12f, 0.74f, 1.08f)
    ComposeGlassPreset.Frost -> ComposeGlassStyle(preset, 0.96f, 0.15f, 1.00f)
    ComposeGlassPreset.Crystal -> ComposeGlassStyle(preset, 1.06f, 0.82f, 1.34f)
    ComposeGlassPreset.Dense -> ComposeGlassStyle(preset, 0.86f, 1.30f, 0.98f)
    ComposeGlassPreset.Aurora -> ComposeGlassStyle(preset, 0.98f, 1.00f, 1.24f)
}
