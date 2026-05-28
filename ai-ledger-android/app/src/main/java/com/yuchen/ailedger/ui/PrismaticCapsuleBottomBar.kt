package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
    val animatedIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "bottom-nav-sticky-index"
    )
    val baseGlowIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.94f, stiffness = Spring.StiffnessVeryLow),
        label = "bottom-nav-base-glow-lag-index"
    )
    val indexDelta = abs(animatedIndex - currentIndex.toFloat()).coerceIn(0f, 2f)
    val travelEnergy = (indexDelta / 0.92f).coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val travelDirection = sign(currentIndex.toFloat() - animatedIndex).coerceIn(-1f, 1f)
    val arrivalPulse = remember { Animatable(0f) }
    var edgeSeed by remember { mutableFloatStateOf(0.37f) }

    LaunchedEffect(currentIndex) {
        edgeSeed = Random.nextFloat()
        arrivalPulse.snapTo(0f)
        delay(190)
        arrivalPulse.animateTo(0.58f, tween(150, easing = FastOutSlowInEasing))
        arrivalPulse.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow))
    }

    val interactionSources = remember(tabs) { tabs.map { MutableInteractionSource() } }
    val pressedStates = interactionSources.map { it.collectIsPressedAsState().value }
    val selectedPressed = pressedStates.getOrNull(currentIndex) == true
    val pressEnergy by animateFloatAsState(
        targetValue = if (selectedPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-selected-press"
    )
    val phaseTransition = rememberInfiniteTransition(label = "bottom-nav-prism-phase")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "bottom-nav-prism-phase-value"
    )
    val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val baseHeight = 72.dp + 1.5.dp * stopEnergy - 1.8.dp * travelEnergy

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity * (1.02f + 0.035f * travelEnergy + 0.022f * stopEnergy),
        motionIntensity = motionIntensity,
        radius = 999,
        modifier = modifier
            .zIndex(300f)
            .fillMaxWidth()
            .height(baseHeight)
            .graphicsLayer {
                translationX = travelDirection * 2.0f * travelEnergy
                translationY = 0.9f * pressEnergy - 0.9f * stopEnergy + 0.7f * travelEnergy
                scaleX = 1f + 0.016f * travelEnergy + 0.008f * pressEnergy - 0.006f * stopEnergy
                scaleY = 1f - 0.026f * travelEnergy - 0.012f * pressEnergy + 0.030f * stopEnergy
                shadowElevation = 0.34f + 0.42f * (travelEnergy + pressEnergy + stopEnergy).coerceIn(0f, 1f)
            },
        role = GlassRole.Nav
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .bottomNavBasePrismOptics(
                    phase = phase,
                    animatedIndex = baseGlowIndex,
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
            val stretch = 0.72f + 0.16f * travelEnergy + 0.06f * pressEnergy - 0.018f * stopEnergy
            val selectorWidthPx = slotWidthPx * stretch.coerceIn(0.68f, 0.96f)
            val selectorWidth = with(density) { selectorWidthPx.toDp() }
            val leadPx = travelDirection * slotWidthPx * 0.030f * travelEnergy
            val selectorX = slotWidthPx * animatedIndex + (slotWidthPx - selectorWidthPx) / 2f + leadPx
            val heightDp = 52.dp + 2.4.dp * stopEnergy - 6.4.dp * travelEnergy - 3.0.dp * pressEnergy
            val selectorShape = RoundedCornerShape(999.dp)
            val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(selectorWidth)
                    .height(heightDp)
                    .graphicsLayer {
                        translationX = selectorX
                        translationY = 1.4f * pressEnergy - 2.9f * travelEnergy - 0.9f * stopEnergy
                        scaleX = 1f + 0.095f * travelEnergy + 0.036f * pressEnergy - 0.018f * stopEnergy
                        scaleY = 1f - 0.105f * travelEnergy - 0.056f * pressEnergy + 0.050f * stopEnergy
                        shadowElevation = 0.25f + 0.45f * (travelEnergy + pressEnergy + stopEnergy).coerceIn(0f, 1f)
                    }
                    .clip(selectorShape)
            ) {
                GlassPanel(
                    quality = quality,
                    glassIntensity = glassIntensity * (1.04f + 0.12f * travelEnergy + 0.08f * pressEnergy + 0.04f * stopEnergy),
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
                                energy = (0.26f + 0.56f * travelEnergy + 0.30f * pressEnergy + 0.30f * stopEnergy).coerceIn(0f, 1.22f),
                                drift = selectedDrift,
                                edgeSeed = edgeSeed,
                                stopEnergy = stopEnergy,
                                travelDirection = travelDirection
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
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                        label = "bottom-nav-tab-press-${tab.name}"
                    )
                    val selectedPop by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
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
                                translationY = -2.2f * selectedPop + 1.2f * tabPress - 0.8f * stopEnergy * selectedPop
                                scaleX = 1f + 0.055f * selectedPop + 0.026f * tabPress
                                scaleY = 1f + 0.034f * selectedPop - 0.018f * tabPress
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
    val baseLeadX = selectorCenterX + slot * travelDirection * 0.15f * travelEnergy
    val corner = CornerRadius(h / 2f, h / 2f)
    val energy = (0.28f + travelEnergy * 0.44f + pressEnergy * 0.20f + stopEnergy * 0.44f).coerceIn(0f, 1.18f)
    val edgePhase = ((sin((phase * 0.52f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val rimCenter = w * (0.12f + 0.76f * edgePhase)

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.022f + 0.034f * energy),
                Color(0xFFFF7FD8).copy(alpha = 0.030f * energy),
                Color(0xFF6EFFF0).copy(alpha = 0.038f * energy),
                Color.Transparent
            ),
            center = Offset(baseLeadX, h * (0.50f - 0.055f * travelEnergy + 0.030f * stopEnergy)),
            radius = slot * (1.22f + 0.52f * energy)
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
                Color.White.copy(alpha = 0.050f + 0.045f * energy),
                Color(0xFFFFD872).copy(alpha = 0.030f * energy),
                Color(0xFF76FFF2).copy(alpha = 0.052f * energy),
                Color.Transparent
            ),
            start = Offset(baseLeadX - slot * (0.70f + 0.24f * travelEnergy), -h * 0.08f),
            end = Offset(baseLeadX + slot * (0.70f + 0.24f * travelEnergy), h * 1.08f)
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
                Color(0xFFFF7AD6).copy(alpha = 0.074f * energy),
                Color.White.copy(alpha = 0.102f + 0.048f * stopEnergy),
                Color(0xFF6DFFF0).copy(alpha = 0.098f * energy),
                Color.Transparent
            ),
            start = Offset(rimCenter - slot * (0.74f + 0.18f * travelEnergy), 0f),
            end = Offset(rimCenter + slot * (0.74f + 0.18f * travelEnergy), h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.70.dp.toPx() + 0.28.dp.toPx() * energy + 0.10.dp.toPx() * stopEnergy),
        blendMode = BlendMode.Plus
    )

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF010615).copy(alpha = 0.014f * travelEnergy),
                Color.Transparent
            ),
            center = Offset(baseLeadX, h * 1.12f),
            radius = slot * 0.86f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Multiply
    )
}

private fun Modifier.bottomSliderPrismOptics(
    phase: Float,
    energy: Float,
    drift: Float,
    edgeSeed: Float,
    stopEnergy: Float,
    travelDirection: Float
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val e = energy.coerceIn(0f, 1.22f)
    val corner = CornerRadius(h / 2f, h / 2f)
    val sweep = ((sin((phase * 0.92f + edgeSeed * 0.31f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val sweepCenter = -0.38f + 1.76f * sweep + 0.06f * drift + 0.06f * travelDirection * e
    val edgeA = ((sin((phase * 0.61f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val edgeB = ((sin((phase * 0.49f + edgeSeed + 0.41f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)

    drawContent()

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.038f + 0.050f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.046f * e),
                Color(0xFFFFD86E).copy(alpha = 0.036f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.054f * e),
                Color(0xFFA796FF).copy(alpha = 0.040f * e),
                Color.White.copy(alpha = 0.026f + 0.026f * e)
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
                Color.White.copy(alpha = 0.150f * e),
                Color(0xFFFFE17A).copy(alpha = 0.078f * e),
                Color(0xFF67FFF0).copy(alpha = 0.112f * e),
                Color(0xFFFF75D4).copy(alpha = 0.078f * e),
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
                Color.White.copy(alpha = 0.15f + 0.08f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.076f * e),
                Color(0xFFFFD86E).copy(alpha = 0.060f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.096f * e),
                Color(0xFFA796FF).copy(alpha = 0.070f * e),
                Color.White.copy(alpha = 0.10f + 0.040f * e)
            ),
            start = Offset(w * (edgeA - 0.60f), -h * 0.08f),
            end = Offset(w * (edgeA + 0.60f), h * 1.08f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 1.02.dp.toPx() + 0.46.dp.toPx() * e),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF68D0).copy(alpha = 0.240f * e),
                Color.White.copy(alpha = (0.30f + 0.16f * stopEnergy) * e),
                Color(0xFF6FFFF2).copy(alpha = 0.280f * e),
                Color.Transparent
            ),
            start = Offset(w * (edgeB - 0.34f), h * -0.03f),
            end = Offset(w * (edgeB + 0.30f), h * 0.30f)
        ),
        topLeft = Offset(inset * 1.45f, inset * 1.45f),
        size = Size((w - inset * 2.90f).coerceAtLeast(1f), (h - inset * 2.90f).coerceAtLeast(1f)),
        cornerRadius = rimCorner,
        style = Stroke(width = 0.68.dp.toPx() + 0.32.dp.toPx() * e),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.038f + 0.060f * e + 0.030f * stopEnergy),
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
