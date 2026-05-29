package com.yuchen.ailedger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuchen.ailedger.model.ModelCardGlassStyle

object ModelCardGlassLabState {
    var style by mutableStateOf(defaultModelCardGlassStyle())
        private set

    fun update(next: ModelCardGlassStyle) {
        style = next
    }

    fun reset() {
        style = defaultModelCardGlassStyle()
    }
}

private fun defaultModelCardGlassStyle() = ModelCardGlassStyle(
    bodyAlpha = 0.35f,
    innerMist = 2.50f,
    topHairline = 0.14f,
    outerRim = 0.42f,
    innerDepth = 1.72f,
    bottomShadow = 0.50f,
    selectedRainbowRim = 7.30f,
    selectedOuterHalo = 4.99f,
    selectedAura = 8.00f,
    edgeGlint = 5.56f,
    edgeGlintRadius = 0.88f,
    edgeGlintCenterX = 0.38f,
    edgeGlintCenterY = 0.53f,
    dotGlow = 4.42f,
    unselectedEnergy = 4.92f,
    radiusScale = 0.72f
)
