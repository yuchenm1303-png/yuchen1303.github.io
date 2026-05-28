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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val tabs = remember { AppTab.entries.toList() }
    val density = LocalDensity.current
    val currentIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val animatedIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-optimized-index"
    )
    val indexDelta = abs(animatedIndex - currentIndex.toFloat()).coerceIn(0f, 2f)
    val travelEnergy = (indexDelta / 0.84f).coerceIn(0f, 1f) * motion
    val travelDirection = sign(currentIndex.toFloat() - animatedIndex).coerceIn(-1f, 1f)
    val arrivalPulse = remember { Animatable(0f) }
    val prismPhase = remember { Animatable(0f) }
    var edgeSeed by remember { mutableFloatStateOf(0.37f) }

    LaunchedEffect(currentIndex) {
        edgeSeed = Random.nextFloat()
        arrivalPulse.snapTo(0f)
        launch {
            prismPhase.animateTo(
                targetValue = prismPhase.value + 1f,
                animationSpec = tween(620, easing = LinearEasing)
            )
        }
        delay(118)
        arrivalPulse.animateTo(0.74f, tween(110, easing = FastOutSlowInEasing))
        arrivalPulse.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = Spring.StiffnessMediumLow))
    }

    val interactionSources = remember(tabs) { tabs.map { MutableInteractionSource() } }
    val pressedStates = interactionSources.map { it.collectIsPressedAsState().value }
    val selectedPressed = pressedStates.getOrNull(currentIndex) == true
    val pressEnergy by animateFloatAsState(
        targetValue = if (selectedPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-selected-press"
    )
    LaunchedEffect(selectedPressed) {
        if (selectedPressed) {
            prismPhase.animateTo(
                targetValue = prismPhase.value + 0.32f,
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            )
        }
    }

    val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motion
    val activeEnergy = maxOf(travelEnergy, pressEnergy, stopEnergy)
    val phase = prismPhase.value

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity * (1.015f + 0.026f * activeEnergy),
        motionIntensity = motionIntensity,
        radius = 999,
        modifier = modifier
            .zIndex(300f)
            .fillMaxWidth()
            .height(72.dp),
        role = GlassRole.Nav
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .bottomNavBasePrismOptics(
                    phase = phase,
                    animatedIndex = animatedIndex,
                    tabCount = tabs.size,
                    edgeSeed = edgeSeed,
                    travelEnergy = travelEnergy,
                    pressEnergy = pressEnergy,
                    stopEnergy = stopEnergy,
                    travelDirection = travelDirection
                )
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val slotWidthPx = totalWidthPx / tabs.size.coerceAtLeast(1)
            val stretch = 0.72f + 0.16f * travelEnergy + 0.055f * pressEnergy - 0.026f * stopEnergy
            val selectorWidthPx = slotWidthPx * stretch.coerceIn(0.68f, 0.98f)
            val selectorWidth = with(density) { selectorWidthPx.toDp() }
            val leadPx = travelDirection * slotWidthPx * 0.032f * travelEnergy
            val selectorX = slotWidthPx * animatedIndex + (slotWidthPx - selectorWidthPx) / 2f + leadPx
            val heightDp = 52.dp + 3.2.dp * stopEnergy - 6.8.dp * travelEnergy - 3.0.dp * pressEnergy
            val selectorShape = RoundedCornerShape(999.dp)
            val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(selectorWidth)
                    .height(heightDp)
                    .graphicsLayer {
                        translationX = selectorX
                        translationY = 1.25f * pressEnergy - 2.9f * travelEnergy - 1.00f * stopEnergy
                        scaleX = 1f + 0.105f * travelEnergy + 0.036f * pressEnergy - 0.022f * stopEnergy
                        scaleY = 1f - 0.114f * travelEnergy - 0.052f * pressEnergy + 0.060f * stopEnergy
                        shadowElevation = 0.24f + 0.42f * activeEnergy.coerceIn(0f, 1f)
                    }
                    .clip(selectorShape)
            ) {
                GlassPanel(
                    quality = quality,
                    glassIntensity = glassIntensity * (1.035f + 0.11f * travelEnergy + 0.07f * pressEnergy + 0.045f * stopEnergy),
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
                                energy = (0.18f + 0.58f * travelEnergy + 0.27f * pressEnergy + 0.34f * stopEnergy).coerceIn(0f, 1.22f),
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
                        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
                        label = "bottom-nav-tab-press-${tab.name}"
                    )
                    val selectedPop by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.64f, stiffness = Spring.StiffnessLow),
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
                                translationY = -2.4f * selectedPop + 1.2f * tabPress - 0.8f * stopEnergy * selectedPop
                                scaleX = 1f + 0.060f * selectedPop + 0.028f * tabPress
                                scaleY = 1f + 0.038f * selectedPop - 0.018f * tabPress
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

private fun Modifier.bottomNavBasePrismOptics(
    phase: Float,
    animatedIndex: Float,
    tabCount: Int,
    edgeSeed: Float,
    travelEnergy: Float,
    pressEnergy: Float,
    stopEnergy: Float,
    travelDirection: Float
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val count = tabCount.coerceAtLeast(1)
    val slot = w / count
    val selectorCenterX = slot * (animatedIndex + 0.5f).coerceIn(0f, count.toFloat())
    val active = maxOf(travelEnergy, pressEnergy, stopEnergy)
    val corner = CornerRadius(h / 2f, h / 2f)

    if (active < 0.012f) {
        drawContent()
        val inset = 1.0.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.052f),
                    Color(0xFF7FFFF2).copy(alpha = 0.034f),
                    Color.Transparent
                ),
                start = Offset(w * 0.12f, 0f),
                end = Offset(w * 0.88f, h)
            ),
            topLeft = Offset(inset, inset),
            size = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f)),
            cornerRadius = CornerRadius((h - inset * 2f) / 2f, (h - inset * 2f) / 2f),
            style = Stroke(width = 0.62.dp.toPx()),
            blendMode = BlendMode.Screen
        )
        return@drawWithContent
    }

    val baseLeadX = selectorCenterX + slot * travelDirection * 0.14f * travelEnergy
    val energy = (0.22f + travelEnergy * 0.46f + pressEnergy * 0.18f + stopEnergy * 0.46f).coerceIn(0f, 1.12f)
    val edgePhase = ((sin((phase * 0.58f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val rimCenter = w * (0.12f + 0.76f * edgePhase)

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.022f + 0.034f * energy),
                Color(0xFFFF7FD8).copy(alpha = 0.030f * energy),
                Color(0xFF6EFFF0).copy(alpha = 0.038f * energy),
                Color.Transparent
            ),
            center = Offset(baseLeadX, h * (0.50f - 0.054f * travelEnergy + 0.035f * stopEnergy)),
            radius = slot * (1.12f + 0.46f * energy)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.044f + 0.040f * energy),
                Color(0xFFFFD872).copy(alpha = 0.028f * energy),
                Color(0xFF76FFF2).copy(alpha = 0.048f * energy),
                Color.Transparent
            ),
            start = Offset(baseLeadX - slot * (0.68f + 0.20f * travelEnergy), -h * 0.08f),
            end = Offset(baseLeadX + slot * (0.68f + 0.20f * travelEnergy), h * 1.08f)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    drawContent()

    val inset = 1.0.dp.toPx()
    val rimSize = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f))
    val rimCorner = CornerRadius((h - inset * 2f) / 2f, (h - inset * 2f) / 2f)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF7AD6).copy(alpha = 0.070f * energy),
                Color.White.copy(alpha = 0.096f + 0.045f * stopEnergy),
                Color(0xFF6DFFF0).copy(alpha = 0.092f * energy),
                Color.Transparent
            ),
            start = Offset(rimCenter - slot * (0.70f + 0.16f * travelEnergy), 0f),
            end = Offset(rimCenter + slot * (0.70f + 0.16f * travelEnergy), h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.66.dp.toPx() + 0.24.dp.toPx() * energy + 0.10.dp.toPx() * stopEnergy),
        blendMode = BlendMode.Plus
    )
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
    val e = energy.coerceIn(0f, 1.22f)
    val corner = CornerRadius(h / 2f, h / 2f)

    drawContent()

    if (active < 0.012f) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.040f),
                    Color(0xFF7FFFF2).copy(alpha = 0.026f),
                    Color.White.copy(alpha = 0.028f)
                ),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
        return@drawWithContent
    }

    val sweep = ((sin((phase * 0.98f + edgeSeed * 0.31f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val sweepCenter = -0.38f + 1.76f * sweep + 0.06f * drift + 0.060f * travelDirection * e
    val edgeA = ((sin((phase * 0.64f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val edgeB = ((sin((phase * 0.52f + edgeSeed + 0.41f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.036f + 0.048f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.044f * e),
                Color(0xFFFFD86E).copy(alpha = 0.034f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.052f * e),
                Color(0xFFA796FF).copy(alpha = 0.038f * e),
                Color.White.copy(alpha = 0.024f + 0.026f * e)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.142f * e),
                Color(0xFFFFE17A).copy(alpha = 0.074f * e),
                Color(0xFF67FFF0).copy(alpha = 0.108f * e),
                Color(0xFFFF75D4).copy(alpha = 0.074f * e),
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

    val inset = 0.72.dp.toPx()
    val rimSize = Size((w - inset * 2f).coerceAtLeast(1f), (h - inset * 2f).coerceAtLeast(1f))
    val rimCorner = CornerRadius((h - inset * 2f) / 2f, (h - inset * 2f) / 2f)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.142f + 0.080f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.072f * e),
                Color(0xFFFFD86E).copy(alpha = 0.058f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.092f * e),
                Color(0xFFA796FF).copy(alpha = 0.066f * e),
                Color.White.copy(alpha = 0.092f + 0.040f * e)
            ),
            start = Offset(w * (edgeA - 0.60f), -h * 0.08f),
            end = Offset(w * (edgeA + 0.60f), h * 1.08f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.96.dp.toPx() + 0.44.dp.toPx() * e),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF68D0).copy(alpha = 0.230f * e),
                Color.White.copy(alpha = (0.285f + 0.165f * stopEnergy) * e),
                Color(0xFF6FFFF2).copy(alpha = 0.272f * e),
                Color.Transparent
            ),
            start = Offset(w * (edgeB - 0.34f), h * -0.03f),
            end = Offset(w * (edgeB + 0.30f), h * 0.30f)
        ),
        topLeft = Offset(inset * 1.45f, inset * 1.45f),
        size = Size((w - inset * 2.90f).coerceAtLeast(1f), (h - inset * 2.90f).coerceAtLeast(1f)),
        cornerRadius = rimCorner,
        style = Stroke(width = 0.64.dp.toPx() + 0.30.dp.toPx() * e),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.036f + 0.058f * e + 0.034f * stopEnergy),
                Color(0xFF7FFFF2).copy(alpha = 0.030f * e),
                Color.Transparent
            ),
            center = Offset(w * (0.50f + 0.10f * drift), h * 0.36f),
            radius = maxOf(w, h) * 0.46f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
}
