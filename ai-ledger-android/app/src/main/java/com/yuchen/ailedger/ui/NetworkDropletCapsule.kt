package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.ui.gl.DropletGlassStyle

private val NetworkLabDropletStyle = DropletGlassStyle(
    bodyBulgePx = 44f,
    edgePullPx = 120f,
    edgeWidthPx = 32f,
    lensMix = 0.92f,
    dragStrength = 2.0f,
    bottomGlow = 1.48f,
    topGloss = 0.53f,
    cornerGloss = 1.03f,
    innerDark = 0.65f,
    alpha = 0.63f,
    activeGlow = 0.53f,
    activeRefraction = 4.0f,
    activeRimRefraction = 3.16f,
    activeLightX = 1.0f,
    activeLightSpread = 0.70f,
    activeLightY = 1.25f,
    activeEntryHeight = 0.04f,
    activeLightThickness = 0.22f,
    activeHotspot = 1.27f,
    activeEntryPearl = 1.88f,
    activeRimPearl = 1.35f,
    activeCenterClear = 0.42f,
    activeVolumeWarmth = 0.14f,
    activeRimGather = 1.21f,
    activeRimFlow = 0.89f
)

@Composable
fun NetworkDropletCapsule(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: Any
) {
    val clickAction = remember(onClick) { (onClick as? () -> Unit) ?: {} }
    OpenGlLargeDropletPreview(
        style = NetworkLabDropletStyle,
        shadowAlpha = 0.18f,
        shadowOffsetX = 3.0f,
        shadowOffsetY = 7.0f,
        shadowSoftness = 18f,
        activeGlow = 0.53f,
        backgroundGlow = 0.38f,
        outerGlow = 0.46f,
        warmGlow = 0.54f,
        prismStrength = 2.76f,
        purpleWhiteLight = 0.67f,
        modifier = modifier,
        forceLocked = state.onlineEnabled,
        onTap = { if (enabled) clickAction() },
        leadingText = "•",
        mainText = if (state.onlineEnabled) "已联网" else "联网已关闭",
        statusText = ""
    )
}
