package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PrismaticCapsuleBottomBar(
    currentTab: AppTab,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSceneScope(
        group = GlassSceneGroup.GlobalBottomBar,
        modifier = modifier.zIndex(300f).fillMaxWidth().height(56.dp)
    ) {
        val tabs = remember { AppTab.entries.toList() }
        val density = LocalDensity.current
        val currentIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
        val motion = motionIntensity.coerceIn(0f, 1f)
        val edgeTarget = if (currentIndex == 0 || currentIndex == tabs.lastIndex) 1f else 0f
        val animatedIndex by animateFloatAsState(
            targetValue = currentIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow),
            label = "bottom-nav-capsule-index"
        )
        val travelEnergy = (abs(animatedIndex - currentIndex.toFloat()) / 0.84f).coerceIn(0f, 1f) * motion
        val edgeSafeTravel = travelEnergy * (1f - 0.28f * edgeTarget)
        val travelDirection = sign(currentIndex.toFloat() - animatedIndex).coerceIn(-1f, 1f)
        val arrivalPulse = remember { Animatable(0f) }
        val prismPhase = remember { Animatable(0f) }
        var edgeSeed by remember { mutableFloatStateOf(0.37f) }

        LaunchedEffect(currentIndex) {
            edgeSeed = Random.nextFloat()
            arrivalPulse.snapTo(0f)
            launch { prismPhase.animateTo(prismPhase.value + 1f, tween(620, easing = LinearEasing)) }
            delay(96)
            arrivalPulse.animateTo(0.90f, tween(105, easing = FastOutSlowInEasing))
            arrivalPulse.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow))
        }

        val interactionSources = remember(tabs) { tabs.map { MutableInteractionSource() } }
        val pressedStates = interactionSources.map { it.collectIsPressedAsState().value }
        val selectedPressed = pressedStates.getOrNull(currentIndex) == true
        val pressEnergy by animateFloatAsState(
            targetValue = if (selectedPressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.46f, stiffness = Spring.StiffnessMediumLow),
            label = "bottom-nav-press"
        )
        LaunchedEffect(selectedPressed) {
            if (selectedPressed) prismPhase.animateTo(prismPhase.value + 0.32f, tween(240, easing = FastOutSlowInEasing))
        }

        val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motion
        val activeEnergy = maxOf(edgeSafeTravel, pressEnergy, stopEnergy)
        val phase = prismPhase.value

        GlassPanel(
            quality = quality,
            glassIntensity = glassIntensity * 1.015f,
            motionIntensity = motionIntensity,
            radius = 999,
            modifier = Modifier.fillMaxSize(),
            role = GlassRole.Nav
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                val totalWidthPx = with(density) { maxWidth.toPx() }
                val slotWidthPx = totalWidthPx / tabs.size.coerceAtLeast(1)
                val maxStretch = 1.015f - 0.030f * edgeTarget
                val stretch = 0.72f + 0.275f * edgeSafeTravel + 0.130f * pressEnergy - 0.018f * stopEnergy
                val selectorWidthPx = slotWidthPx * stretch.coerceIn(0.68f, maxStretch)
                val selectorWidth = with(density) { selectorWidthPx.toDp() }
                val leadPx = travelDirection * slotWidthPx * 0.030f * edgeSafeTravel
                val rawX = slotWidthPx * animatedIndex + (slotWidthPx - selectorWidthPx) / 2f + leadPx
                val selectorX = rawX.coerceIn(0f, (totalWidthPx - selectorWidthPx).coerceAtLeast(0f))
                val heightDp = 42.dp + 3.0.dp * stopEnergy - 6.2.dp * edgeSafeTravel - 4.6.dp * pressEnergy
                val selectorShape = RoundedCornerShape(999.dp)
                val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(selectorWidth)
                        .height(heightDp)
                        .graphicsLayer {
                            translationX = selectorX
                            translationY = 1.35f * pressEnergy - 2.35f * edgeSafeTravel - 0.90f * stopEnergy
                            scaleX = 1f + 0.170f * edgeSafeTravel + 0.098f * pressEnergy - 0.024f * stopEnergy
                            scaleY = 1f - 0.176f * edgeSafeTravel - 0.108f * pressEnergy + 0.080f * stopEnergy
                            shadowElevation = 0.18f + 0.42f * activeEnergy.coerceIn(0f, 1f)
                        }
                        .clip(selectorShape)
                ) {
                    GlassPanel(
                        quality = quality,
                        glassIntensity = glassIntensity * (1.035f + 0.08f * edgeSafeTravel + 0.07f * pressEnergy + 0.045f * stopEnergy),
                        motionIntensity = motionIntensity,
                        radius = 999,
                        modifier = Modifier.fillMaxSize(),
                        role = GlassRole.Floating
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(selectorShape)
                                .bottomSliderPrismOptics(
                                    phase = phase,
                                    energy = (0.18f + 0.44f * edgeSafeTravel + 0.27f * pressEnergy + 0.34f * stopEnergy).coerceIn(0f, 1.12f),
                                    drift = selectedDrift,
                                    edgeSeed = edgeSeed,
                                    stopEnergy = stopEnergy,
                                    travelDirection = travelDirection,
                                    activeEnergy = activeEnergy
                                )
                        )
                    }
                }

                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = tab == currentTab
                        val pressed = pressedStates.getOrNull(index) == true
                        val tabPress by animateFloatAsState(
                            targetValue = if (pressed) 1f else 0f,
                            animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMediumLow),
                            label = "bottom-nav-tab-press-${tab.name}"
                        )
                        val selectedPop by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessLow),
                            label = "bottom-nav-tab-selected-${tab.name}"
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(interactionSource = interactionSources[index], indication = null) { onTabChange(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.graphicsLayer {
                                    translationY = -1.4f * selectedPop + 0.8f * tabPress - 0.6f * stopEnergy * selectedPop
                                    scaleX = 1f + 0.046f * selectedPop + 0.026f * tabPress
                                    scaleY = 1f + 0.032f * selectedPop - 0.016f * tabPress
                                    alpha = 0.54f + 0.46f * selectedPop
                                }
                            ) {
                                Text(
                                    tab.icon,
                                    color = Color.White.copy(alpha = 0.72f + 0.26f * selectedPop),
                                    fontSize = if (tab == AppTab.Assistant) 11.sp else 15.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                                Text(
                                    tab.title,
                                    color = Color.White.copy(alpha = 0.70f + 0.28f * selectedPop),
                                    fontSize = 9.sp,
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
}

private fun Modifier.bottomSliderPrismOptics(
    phase: Float,
    energy: Float,
    drift: Float,
    edgeSeed: Float,
    stopEnergy: Float,
    travelDirection: Float,
    activeEnergy: Float
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val active = activeEnergy.coerceIn(0f, 1f)
    val e = energy.coerceIn(0f, 1.12f)
    val corner = CornerRadius(h / 2f, h / 2f)
    val sweep = ((sin((phase * 0.98f + edgeSeed * 0.31f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val sweepCenter = -0.38f + 1.76f * sweep + 0.06f * drift + 0.050f * travelDirection * e
    val glassEnergy = (0.30f + e * 0.70f + stopEnergy * 0.20f).coerceIn(0f, 1.35f)
    val rimEnergy = (0.34f + active * 0.66f + e * 0.18f).coerceIn(0f, 1.25f)

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.055f + 0.042f * glassEnergy),
                Color(0xFFE9FFFF).copy(alpha = 0.030f + 0.026f * glassEnergy),
                Color(0xFF304A76).copy(alpha = 0.032f + 0.030f * glassEnergy),
                Color(0xFF030714).copy(alpha = 0.060f + 0.050f * glassEnergy)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.SrcOver
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.105f * glassEnergy),
                Color(0xFFBFFAF6).copy(alpha = 0.040f * glassEnergy),
                Color.Transparent
            ),
            center = Offset(w * (0.50f + 0.10f * drift), h * 0.18f),
            radius = maxOf(w, h) * 0.72f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    drawContent()

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.145f + 0.125f * rimEnergy),
                Color(0xFFE8FFFF).copy(alpha = 0.060f + 0.055f * rimEnergy),
                Color.Transparent,
                Color(0xFF01030A).copy(alpha = 0.145f + 0.105f * rimEnergy),
                Color.White.copy(alpha = 0.050f + 0.030f * rimEnergy)
            ),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset(0.45f, 0.45f),
        size = Size((w - 0.90f).coerceAtLeast(1f), (h - 0.90f).coerceAtLeast(1f)),
        cornerRadius = corner,
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.15.dp.toPx() + 0.85.dp.toPx() * rimEnergy),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFF00030A).copy(alpha = 0.120f + 0.145f * rimEnergy)
            ),
            startY = h * 0.42f,
            endY = h
        ),
        topLeft = Offset(2.0.dp.toPx(), 2.0.dp.toPx()),
        size = Size((w - 4.0.dp.toPx()).coerceAtLeast(1f), (h - 4.0.dp.toPx()).coerceAtLeast(1f)),
        cornerRadius = corner,
        style = androidx.compose.ui.graphics.drawscope.Stroke(2.8.dp.toPx() + 1.8.dp.toPx() * rimEnergy),
        blendMode = BlendMode.Multiply
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.112f * e),
                Color(0xFFFFE17A).copy(alpha = 0.056f * e),
                Color(0xFF67FFF0).copy(alpha = 0.088f * e),
                Color(0xFFFF75D4).copy(alpha = 0.052f * e),
                Color.Transparent
            ),
            start = Offset(w * (sweepCenter - 0.50f), -h * 0.36f),
            end = Offset(w * (sweepCenter + 0.50f), h * 1.36f)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Plus
    )

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.092f + 0.082f * e + 0.046f * stopEnergy),
                Color(0xFF7FFFF2).copy(alpha = 0.040f + 0.036f * e),
                Color.Transparent
            ),
            center = Offset(w * (0.08f + 0.84f * sweep), h * 0.02f),
            radius = maxOf(w, h) * 0.42f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    if (active < 0.012f) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.030f),
                    Color(0xFF7FFFF2).copy(alpha = 0.018f),
                    Color.White.copy(alpha = 0.024f)
                ),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
    }
}
