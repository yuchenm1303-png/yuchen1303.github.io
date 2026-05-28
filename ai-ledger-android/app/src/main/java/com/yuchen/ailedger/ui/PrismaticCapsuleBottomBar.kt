package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun PrismaticCapsuleBottomBar(
    currentTab: AppTab,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { AppTab.entries.toList() }
    val density = LocalDensity.current
    val currentIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-capsule-index"
    )
    val indexDelta = abs(animatedIndex - currentIndex.toFloat()).coerceIn(0f, 2f)
    val travelEnergy = (indexDelta / 0.92f).coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val interactionSources = remember(tabs) { tabs.map { MutableInteractionSource() } }
    val pressedStates = interactionSources.map { it.collectIsPressedAsState().value }
    val selectedPressed = pressedStates.getOrNull(currentIndex) == true
    val pressEnergy by animateFloatAsState(
        targetValue = if (selectedPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-selected-press"
    )
    val phaseTransition = rememberInfiniteTransition(label = "bottom-nav-prism-phase")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(3600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "bottom-nav-prism-phase-value"
    )

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity * 1.02f,
        motionIntensity = motionIntensity,
        radius = 999,
        modifier = modifier.fillMaxWidth().height(72.dp),
        role = GlassRole.Nav
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 7.dp)) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val slotWidthPx = totalWidthPx / tabs.size.coerceAtLeast(1)
            val selectorWidthPx = slotWidthPx * (0.74f + 0.13f * travelEnergy + 0.08f * pressEnergy).coerceIn(0.70f, 0.96f)
            val selectorWidth = with(density) { selectorWidthPx.toDp() }
            val selectorX = slotWidthPx * animatedIndex + (slotWidthPx - selectorWidthPx) / 2f
            val selectorShape = RoundedCornerShape(999.dp)
            val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(selectorWidth)
                    .height(52.dp)
                    .graphicsLayer {
                        translationX = selectorX
                        translationY = 1.2f * pressEnergy - 4.2f * travelEnergy
                        scaleX = 1f + 0.18f * travelEnergy + 0.055f * pressEnergy
                        scaleY = 1f - 0.16f * travelEnergy - 0.075f * pressEnergy
                        shadowElevation = 0.28f + 0.52f * (travelEnergy + pressEnergy).coerceIn(0f, 1f)
                    }
                    .clip(selectorShape)
            ) {
                GlassPanel(
                    quality = quality,
                    glassIntensity = glassIntensity * (1.05f + 0.18f * travelEnergy + 0.10f * pressEnergy),
                    motionIntensity = motionIntensity,
                    radius = 999,
                    modifier = Modifier.fillMaxSize(),
                    role = GlassRole.Floating
                ) {
                    Box(Modifier.fillMaxSize().clip(selectorShape)) {
                        PrismaticBottomSliderOptics(
                            phase = phase,
                            energy = (0.34f + 0.66f * travelEnergy + 0.42f * pressEnergy).coerceIn(0f, 1.36f),
                            drift = selectedDrift,
                            shape = selectorShape
                        )
                    }
                }
            }

            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    val selected = tab == currentTab
                    val pressed = pressedStates.getOrNull(index) == true
                    val tabPress by animateFloatAsState(
                        targetValue = if (pressed) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.54f, stiffness = Spring.StiffnessMediumLow),
                        label = "bottom-nav-tab-press-${tab.name}"
                    )
                    val selectedPop by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessMediumLow),
                        label = "bottom-nav-tab-selected-${tab.name}"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = interactionSources[index],
                                indication = null,
                                enabled = true
                            ) { onTabChange(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.graphicsLayer {
                                translationY = -2.8f * selectedPop + 1.5f * tabPress
                                scaleX = 1f + 0.08f * selectedPop + 0.035f * tabPress
                                scaleY = 1f + 0.05f * selectedPop - 0.025f * tabPress
                                alpha = 0.50f + 0.50f * selectedPop
                            }
                        ) {
                            Text(
                                tab.icon,
                                color = Color.White.copy(alpha = 0.72f + 0.26f * selectedPop),
                                fontSize = if (tab == AppTab.Assistant) 13.sp else 18.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                tab.title,
                                color = Color.White.copy(alpha = 0.70f + 0.28f * selectedPop),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PrismaticBottomSliderOptics(
    phase: Float,
    energy: Float,
    drift: Float,
    shape: RoundedCornerShape
) {
    val safeEnergy = energy.coerceIn(0f, 1.42f)
    val sweep = ((sin((phase * 1.15f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val sweepX = -56f + 122f * sweep + 12f * drift
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.060f + 0.060f * safeEnergy),
                        Color(0xFFFF8AD8).copy(alpha = 0.060f * safeEnergy),
                        Color(0xFFFFD76A).copy(alpha = 0.044f * safeEnergy),
                        Color(0xFF6DFFF0).copy(alpha = 0.066f * safeEnergy),
                        Color(0xFF9CA8FF).copy(alpha = 0.050f * safeEnergy),
                        Color.White.copy(alpha = 0.028f + 0.040f * safeEnergy)
                    )
                )
            )
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(9.dp)
            .padding(horizontal = 8.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFF75D8).copy(alpha = 0.16f * safeEnergy),
                        Color.White.copy(alpha = 0.24f * safeEnergy),
                        Color(0xFF76FFF2).copy(alpha = 0.20f * safeEnergy),
                        Color.Transparent
                    )
                )
            )
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(8.dp)
            .padding(horizontal = 10.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF63FFF0).copy(alpha = 0.16f * safeEnergy),
                        Color(0xFFA794FF).copy(alpha = 0.14f * safeEnergy),
                        Color(0xFFFF82D4).copy(alpha = 0.12f * safeEnergy),
                        Color.Transparent
                    )
                )
            )
    )
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .width(42.dp)
            .height(110.dp)
            .graphicsLayer {
                translationX = sweepX
                rotationZ = -18f
                alpha = 0.24f + 0.60f * safeEnergy.coerceIn(0f, 1f)
                scaleX = 0.82f + 0.20f * safeEnergy.coerceIn(0f, 1f)
            }
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.28f * safeEnergy),
                        Color(0xFFFFDA76).copy(alpha = 0.18f * safeEnergy),
                        Color(0xFF6DFFF0).copy(alpha = 0.24f * safeEnergy),
                        Color(0xFFFF72D8).copy(alpha = 0.16f * safeEnergy),
                        Color.Transparent
                    )
                )
            )
    )
}
