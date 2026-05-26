package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

internal data class FrostSettingMetric(val label: String, val value: String)

@Composable
internal fun SettingsFrostOverviewGlass(
    title: String,
    subtitle: String,
    badge: String,
    metrics: List<FrostSettingMetric>,
    modifier: Modifier = Modifier
) {
    FrostInfoGlassPanel(
        radius = 28f,
        backdropAlpha = 0.96f,
        frostAlpha = 0.045f,
        dimAlpha = 0.06f,
        modifier = modifier
            .fillMaxWidth()
            .frostGlassEdgeHighlight(radius = 28f, active = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    androidx.compose.material3.Text(title, color = Color.White, fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    androidx.compose.material3.Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                androidx.compose.material3.Text(badge, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                metrics.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { metric -> SettingsFrostOverviewMetric(metric.label, metric.value, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsFrostOverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.material3.Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        androidx.compose.material3.Text(value, color = Color.White.copy(alpha = 0.94f), fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SettingsFrostTileGlass(
    icon: String,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.018f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-frost-tile-scale-$title"
    )
    val interaction = remember { MutableInteractionSource() }
    FrostInfoGlassPanel(
        radius = 30f,
        backdropAlpha = if (selected) 0.98f else 0.90f,
        frostAlpha = if (selected) 0.055f else 0.032f,
        dimAlpha = if (selected) 0.045f else 0.075f,
        modifier = modifier
            .height(134.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .frostGlassEdgeHighlight(radius = 30f, active = selected)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsFrostIconBadge(icon, selected)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    androidx.compose.material3.Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    androidx.compose.material3.Text(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SettingsFrostValueStrip(value, selected, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsFrostIconBadge(text: String, selected: Boolean) {
    FrostInfoGlassPanel(
        radius = 20f,
        backdropAlpha = if (selected) 0.92f else 0.78f,
        frostAlpha = if (selected) 0.06f else 0.035f,
        dimAlpha = if (selected) 0.035f else 0.08f,
        modifier = Modifier.size(50.dp).frostGlassEdgeHighlight(radius = 20f, active = selected)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(text, color = Color.White.copy(alpha = if (selected) 0.92f else 0.66f), fontSize = if (text.length > 1) 15.sp else 20.sp, fontWeight = FontWeight.Black, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsFrostValueStrip(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    FrostInfoGlassPanel(
        radius = 999f,
        backdropAlpha = if (selected) 0.86f else 0.72f,
        frostAlpha = if (selected) 0.045f else 0.022f,
        dimAlpha = if (selected) 0.055f else 0.10f,
        modifier = modifier.height(34.dp).frostGlassEdgeHighlight(radius = 999f, active = selected)
    ) {
        Box(Modifier.padding(horizontal = 13.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(text, color = Color.White.copy(alpha = if (selected) 0.84f else 0.56f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

internal fun Modifier.frostGlassEdgeHighlight(radius: Float, active: Boolean): Modifier = drawWithContent {
    drawContent()
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val r = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
    val inset = 0.9.dp.toPx()
    val alphaBase = if (active) 1f else 0.62f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f * alphaBase),
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2f, h - inset * 2f),
        cornerRadius = r,
        style = Stroke(width = 1.05.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.28f * alphaBase), Color(0xFF8DF9EA).copy(alpha = 0.08f * alphaBase), Color.Transparent),
            start = Offset(w * 0.10f, 0f),
            end = Offset(w * 0.92f, h * 0.42f)
        ),
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2f, h - inset * 2f),
        cornerRadius = r,
        style = Stroke(width = 1.25.dp.toPx()),
        blendMode = BlendMode.Plus
    )
    if (active) {
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.18f), Color(0xFF91F6FF).copy(alpha = 0.055f), Color.Transparent),
                center = Offset(w * 0.88f, h * 0.10f),
                radius = w * 0.34f
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
    }
}
