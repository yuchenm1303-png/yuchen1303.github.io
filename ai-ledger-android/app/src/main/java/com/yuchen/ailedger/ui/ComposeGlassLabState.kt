package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class ComposeGlassStyle(
    val backdropAlpha: Float,
    val blurScale: Float,
    val frostAlpha: Float,
    val coolTint: Float,
    val rimAlpha: Float,
    val innerRimAlpha: Float,
    val topHighlight: Float,
    val bottomShadow: Float,
    val cornerGlint: Float,
    val motionGlint: Float,
    val strokeWidth: Float,
    val shadowAlpha: Float,
    val radiusScale: Float
)

object ComposeGlassLabState {
    var style by mutableStateOf(defaultComposeGlassStyle())
        private set

    fun update(next: ComposeGlassStyle) {
        style = next
    }

    fun reset() {
        style = defaultComposeGlassStyle()
    }
}

private fun defaultComposeGlassStyle() = ComposeGlassStyle(
    backdropAlpha = 0.96f,
    blurScale = 1.06f,
    frostAlpha = 1.18f,
    coolTint = 1.10f,
    rimAlpha = 1.36f,
    innerRimAlpha = 0.82f,
    topHighlight = 1.54f,
    bottomShadow = 1.22f,
    cornerGlint = 1.08f,
    motionGlint = 0.86f,
    strokeWidth = 1.18f,
    shadowAlpha = 1.12f,
    radiusScale = 1.00f
)
