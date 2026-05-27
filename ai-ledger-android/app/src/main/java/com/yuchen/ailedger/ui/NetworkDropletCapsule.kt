package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.ui.gl.DropletGlassStyle
import com.yuchen.ailedger.ui.gl.OpenGLDropletGlassLayer

@Composable
fun NetworkDropletCapsule(state: AssistantUiState, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val coordinates = remember { GlassCoordinateSource() }
    val on = state.onlineEnabled
    val p = if (on) 1f else 0f
    val style = DropletGlassStyle(
        bodyBulgePx = 44f,
        edgePullPx = 120f,
        edgeWidthPx = 32f,
        lensMix = 0.92f,
        dragStrength = 2.0f,
        bottomGlow = 1.48f,
        topGloss = 0.53f + p * 0.22f,
        cornerGloss = 1.03f + p * 0.18f,
        innerDark = 0.65f,
        alpha = if (on) 0.68f else 0.56f,
        activeGlow = 0.53f * p,
        activeRefraction = 4.0f * p,
        activeRimRefraction = 3.16f * p,
        activeLightX = 0.92f,
        activeLightSpread = 0.70f,
        activeLightY = 1.25f,
        activeEntryHeight = 0.04f,
        activeLightThickness = 0.22f,
        activeHotspot = 1.27f * p,
        activeEntryPearl = 1.88f * p,
        activeRimPearl = 1.35f * p,
        activeCenterClear = 0.42f,
        activeVolumeWarmth = 0.14f * p,
        activeRimGather = 1.21f * p,
        activeRimFlow = 0.89f * p,
        accentRed = if (on) 0.95f else 0.52f,
        accentGreen = if (on) 0.86f else 0.78f,
        accentBlue = 1.0f,
        warmRed = if (on) 0.98f else 1.0f,
        warmGreen = if (on) 0.62f else 0.45f,
        warmBlue = if (on) 0.86f else 0.78f
    )
    Box(
        modifier = modifier.height(44.dp).onGloballyPositioned { coordinates.coordinates = it }.clip(RoundedCornerShape(999.dp)).clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        OpenGLDropletGlassLayer(radius = 999, coordinateSource = coordinates, style = style, modifier = Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetworkDropDot(active = on)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("联网", color = Color.White.copy(alpha = if (on) 0.74f else 0.50f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (on) "已开启" else "已关闭", color = Color.White.copy(alpha = if (on) 0.96f else 0.76f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NetworkDropDot(active: Boolean) {
    Canvas(Modifier.width(12.dp).height(12.dp)) {
        drawCircle(Color.White.copy(alpha = if (active) 0.92f else 0.36f), radius = size.minDimension * 0.34f, center = center, blendMode = BlendMode.Screen)
        if (active) drawCircle(Color(0xFF7EFFE7).copy(alpha = 0.46f), radius = size.minDimension * 0.50f, center = center, blendMode = BlendMode.Plus)
    }
}
