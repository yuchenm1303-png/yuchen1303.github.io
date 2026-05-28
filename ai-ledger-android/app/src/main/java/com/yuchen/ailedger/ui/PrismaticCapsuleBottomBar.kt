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
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow),
        label = "bottom-nav-sticky-index"
    )
    val baseGlowIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessVeryLow),
        label = "bottom-nav-base-glow-lag-index"
    )
    val indexDelta = abs(animatedIndex - currentIndex.toFloat()).coerceIn(0f, 2f)
    val travelEnergy = (indexDelta / 0.90f).coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val travelDirection = sign(currentIndex.toFloat() - animatedIndex).coerceIn(-1f, 1f)
    val arrivalPulse = remember { Animatable(0f) }
    var edgeSeed by remember { mutableFloatStateOf(0.37f) }

    LaunchedEffect(currentIndex) {
        edgeSeed = Random.nextFloat()
        arrivalPulse.snapTo(0f)
        delay(170)
        arrivalPulse.animateTo(0.72f, tween(120, easing = FastOutSlowInEasing))
        arrivalPulse.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow))
    }

    val interactionSources = remember(tabs) { tabs.map { MutableInteractionSource() } }
    val pressedStates = interactionSources.map { it.collectIsPressedAsState().value }
    val selectedPressed = pressedStates.getOrNull(currentIndex) == true
    val pressEnergy by animateFloatAsState(
        targetValue = if (selectedPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-selected-press"
    )
    val phaseTransition = rememberInfiniteTransition(label = "bottom-nav-prism-phase")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "bottom-nav-prism-phase-value"
    )
    val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val baseHeight = 72.dp + 2.1.dp * stopEnergy - 2.0.dp * travelEnergy

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity * (1.02f + 0.044f * travelEnergy + 0.030f * stopEnergy),
        motionIntensity = motionIntensity,
        radius = 999,
        modifier = modifier
            .zIndex(300f)
            .fillMaxWidth()
            .height(baseHeight)
            .graphicsLayer {
                translationX = travelDirection * 2.5f * travelEnergy
                translationY = 1.0f * pressEnergy - 1.4f * stopEnergy + 0.9f * travelEnergy
                scaleX = 1f + 0.022f * travelEnergy + 0.010f * pressEnergy - 0.010f * stopEnergy
                scaleY = 1f - 0.034f * travelEnergy - 0.014f * pressEnergy + 0.046f * stopEnergy
                shadowElevation = 0.34f + 0.50f * (travelEnergy + pressEnergy + stopEnergy).coerceIn(0f, 1f)
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
            val stretch = 0.72f + 0.18f * travelEnergy + 0.060f * pressEnergy - 0.026f * stopEnergy
            val selectorWidthPx = slotWidthPx * stretch.coerceIn(0.68f, 0.99f)
            val selectorWidth = with(density) { selectorWidthPx.toDp() }
            val leadPx = travelDirection * slotWidthPx * 0.034f * travelEnergy
            val selectorX = slotWidthPx * animatedIndex + (slotWidthPx - selectorWidthPx) / 2f + leadPx
            val heightDp = 52.dp + 3.5.dp * stopEnergy - 7.4.dp * travelEnergy - 3.1.dp * pressEnergy
            val selectorShape = RoundedCornerShape(999.dp)
            val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(selectorWidth)
                    .height(heightDp)
                    .graphicsLayer {
                        translationX = selectorX
                        translationY = 1.4f * pressEnergy - 3.4f * travelEnergy - 1.25f * stopEnergy
                        scaleX = 1f + 0.120f * travelEnergy + 0.040f * pressEnergy - 0.026f * stopEnergy
                        scaleY = 1f - 0.132f * travelEnergy - 0.060f * pressEnergy + 0.070f * stopEnergy
                        shadowElevation = 0.25f + 0.50f * (travelEnergy + pressEnergy + stopEnergy).coerceIn(0f, 1f)
                    }
                    .clip(selectorShape)
            ) {
                GlassPanel(
                    quality = quality,
                    glassIntensity = glassIntensity * (1.04f + 0.14f * travelEnergy + 0.08f * pressEnergy + 0.055f * stopEnergy),
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
                                energy = (0.26f + 0.62f * travelEnergy + 0.30f * pressEnergy + 0.38f * stopEnergy).coerceIn(0f, 1.28f),
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
                                translationY = -2.4f * selectedPop + 1.3f * tabPress - 1.0f * stopEnergy * selectedPop
                                scaleX = 1f + 0.062f * selectedPop + 0.030f * tabPress
                                scaleY = 1f + 0.040f * selectedPop - 0.020f * tabPress
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
    val baseLeadX = selectorCenterX + slot * travelDirection * 0.16f * travelEnergy
    val corner = CornerRadius(h / 2f, h / 2f)
    val energy = (0.28f + travelEnergy * 0.48f + pressEnergy * 0.20f + stopEnergy * 0.52f).coerceIn(0f, 1.24f)
    val edgePhase = ((sin((phase * 0.56f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val rimCenter = w * (0.12f + 0.76f * edgePhase)

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.024f + 0.038f * energy),
                Color(0xFFFF7FD8).copy(alpha = 0.032f * energy),
                Color(0xFF6EFFF0).copy(alpha = 0.040f * energy),
                Color.Transparent
            ),
            center = Offset(baseLeadX, h * (0.50f - 0.062f * travelEnergy + 0.040f * stopEnergy)),
            radius = slot * (1.20f + 0.50f * energy)
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
                Color.White.copy(alpha = 0.052f + 0.050f * energy),
                Color(0xFFFFD872).copy(alpha = 0.032f * energy),
                Color(0xFF76FFF2).copy(alpha = 0.056f * energy),
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
                Color(0xFFFF7AD6).copy(alpha = 0.078f * energy),
                Color.White.copy(alpha = 0.106f + 0.058f * stopEnergy),
                Color(0xFF6DFFF0).copy(alpha = 0.104f * energy),
                Color.Transparent
            ),
            start = Offset(rimCenter - slot * (0.74f + 0.18f * travelEnergy), 0f),
            end = Offset(rimCenter + slot * (0.74f + 0.18f * travelEnergy), h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.70.dp.toPx() + 0.30.dp.toPx() * energy + 0.14.dp.toPx() * stopEnergy),
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
    val e = energy.coerceIn(0f, 1.28f)
    val corner = CornerRadius(h / 2f, h / 2f)
    val sweep = ((sin((phase * 0.98f + edgeSeed * 0.31f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val sweepCenter = -0.38f + 1.76f * sweep + 0.06f * drift + 0.065f * travelDirection * e
    val edgeA = ((sin((phase * 0.65f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val edgeB = ((sin((phase * 0.52f + edgeSeed + 0.41f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)

    drawContent()

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.038f + 0.052f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.048f * e),
                Color(0xFFFFD86E).copy(alpha = 0.038f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.056f * e),
                Color(0xFFA796FF).copy(alpha = 0.042f * e),
                Color.White.copy(alpha = 0.026f + 0.028f * e)
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
                Color.White.copy(alpha = 0.158f * e),
                Color(0xFFFFE17A).copy(alpha = 0.084f * e),
                Color(0xFF67FFF0).copy(alpha = 0.120f * e),
                Color(0xFFFF75D4).copy(alpha = 0.084f * e),
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
                Color.White.copy(alpha = 0.15f + 0.09f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.080f * e),
                Color(0xFFFFD86E).copy(alpha = 0.064f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.102f * e),
                Color(0xFFA796FF).copy(alpha = 0.074f * e),
                Color.White.copy(alpha = 0.10f + 0.044f * e)
            ),
            start = Offset(w * (edgeA - 0.60f), -h * 0.08f),
            end = Offset(w * (edgeA + 0.60f), h * 1.08f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 1.02.dp.toPx() + 0.50.dp.toPx() * e),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF68D0).copy(alpha = 0.255f * e),
                Color.White.copy(alpha = (0.31f + 0.19f * stopEnergy) * e),
                Color(0xFF6FFFF2).copy(alpha = 0.300f * e),
                Color.Transparent
            ),
            start = Offset(w * (edgeB - 0.34f), h * -0.03f),
            end = Offset(w * (edgeB + 0.30f), h * 0.30f)
        ),
        topLeft = Offset(inset * 1.45f, inset * 1.45f),
        size = Size((w - inset * 2.90f).coerceAtLeast(1f), (h - inset * 2.90f).coerceAtLeast(1f)),
        cornerRadius = rimCorner,
        style = Stroke(width = 0.68.dp.toPx() + 0.34.dp.toPx() * e),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.038f + 0.064f * e + 0.040f * stopEnergy),
                Color(0xFF7FFFF2).copy(alpha = 0.032f * e),
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
