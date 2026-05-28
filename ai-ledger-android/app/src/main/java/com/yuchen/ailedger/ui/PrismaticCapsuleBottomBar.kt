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
        animationSpec = spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMediumLow),
        label = "bottom-nav-droplet-index"
    )
    val indexDelta = abs(animatedIndex - currentIndex.toFloat()).coerceIn(0f, 2f)
    val travelEnergy = (indexDelta / 0.82f).coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val travelDirection = sign(currentIndex.toFloat() - animatedIndex).coerceIn(-1f, 1f)
    val arrivalPulse = remember { Animatable(0f) }
    var edgeSeed by remember { mutableFloatStateOf(0.37f) }

    LaunchedEffect(currentIndex) {
        edgeSeed = Random.nextFloat()
        arrivalPulse.snapTo(0f)
        delay(150)
        arrivalPulse.animateTo(1f, tween(88, easing = FastOutSlowInEasing))
        arrivalPulse.animateTo(0f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessLow))
    }

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
    val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motionIntensity.coerceIn(0f, 1f)
    val baseHeight = 72.dp + 3.dp * stopEnergy - 3.dp * travelEnergy

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity * (1.02f + 0.06f * travelEnergy + 0.04f * stopEnergy),
        motionIntensity = motionIntensity,
        radius = 999,
        modifier = modifier
            .zIndex(300f)
            .fillMaxWidth()
            .height(baseHeight)
            .graphicsLayer {
                translationX = travelDirection * 3.6f * travelEnergy
                translationY = 1.2f * pressEnergy - 2.8f * stopEnergy + 1.4f * travelEnergy
                scaleX = 1f + 0.030f * travelEnergy + 0.010f * pressEnergy - 0.012f * stopEnergy
                scaleY = 1f - 0.050f * travelEnergy - 0.018f * pressEnergy + 0.072f * stopEnergy
                shadowElevation = 0.36f + 0.72f * (travelEnergy + pressEnergy + stopEnergy).coerceIn(0f, 1f)
            },
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
            val stretch = 0.72f + 0.23f * travelEnergy + 0.08f * pressEnergy - 0.04f * stopEnergy
            val selectorWidthPx = slotWidthPx * stretch.coerceIn(0.66f, 1.04f)
            val selectorWidth = with(density) { selectorWidthPx.toDp() }
            val leadPx = travelDirection * slotWidthPx * 0.040f * travelEnergy
            val selectorX = slotWidthPx * animatedIndex + (slotWidthPx - selectorWidthPx) / 2f + leadPx
            val heightDp = 52.dp + 6.dp * stopEnergy - 10.dp * travelEnergy - 4.dp * pressEnergy
            val selectorShape = RoundedCornerShape(999.dp)
            val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(selectorWidth)
                    .height(heightDp)
                    .graphicsLayer {
                        translationX = selectorX
                        translationY = 1.8f * pressEnergy - 4.8f * travelEnergy - 2.2f * stopEnergy
                        scaleX = 1f + 0.17f * travelEnergy + 0.045f * pressEnergy - 0.045f * stopEnergy
                        scaleY = 1f - 0.18f * travelEnergy - 0.070f * pressEnergy + 0.135f * stopEnergy
                        shadowElevation = 0.25f + 0.62f * (travelEnergy + pressEnergy + stopEnergy).coerceIn(0f, 1f)
                    }
                    .clip(selectorShape)
            ) {
                GlassPanel(
                    quality = quality,
                    glassIntensity = glassIntensity * (1.04f + 0.18f * travelEnergy + 0.10f * pressEnergy + 0.08f * stopEnergy),
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
                                energy = (0.28f + 0.72f * travelEnergy + 0.36f * pressEnergy + 0.48f * stopEnergy).coerceIn(0f, 1.45f),
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
                                translationY = -2.8f * selectedPop + 1.5f * tabPress - 1.6f * stopEnergy * selectedPop
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
    val baseLeadX = selectorCenterX + slot * travelDirection * 0.20f * travelEnergy
    val corner = CornerRadius(h / 2f, h / 2f)
    val energy = (0.30f + travelEnergy * 0.52f + pressEnergy * 0.24f + stopEnergy * 0.72f).coerceIn(0f, 1.42f)
    val edgePhase = ((sin((phase * 0.64f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val rimCenter = w * (0.12f + 0.76f * edgePhase)

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.026f + 0.040f * energy),
                Color(0xFFFF7FD8).copy(alpha = 0.036f * energy),
                Color(0xFF6EFFF0).copy(alpha = 0.044f * energy),
                Color.Transparent
            ),
            center = Offset(baseLeadX, h * (0.49f - 0.08f * travelEnergy + 0.06f * stopEnergy)),
            radius = slot * (1.18f + 0.46f * energy)
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
                Color.White.copy(alpha = 0.056f + 0.050f * energy),
                Color(0xFFFFD872).copy(alpha = 0.034f * energy),
                Color(0xFF76FFF2).copy(alpha = 0.058f * energy),
                Color.Transparent
            ),
            start = Offset(baseLeadX - slot * (0.62f + 0.20f * travelEnergy), -h * 0.08f),
            end = Offset(baseLeadX + slot * (0.62f + 0.20f * travelEnergy), h * 1.08f)
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
                Color(0xFFFF7AD6).copy(alpha = 0.082f * energy),
                Color.White.copy(alpha = 0.112f + 0.070f * stopEnergy),
                Color(0xFF6DFFF0).copy(alpha = 0.110f * energy),
                Color.Transparent
            ),
            start = Offset(rimCenter - slot * (0.70f + 0.16f * travelEnergy), 0f),
            end = Offset(rimCenter + slot * (0.70f + 0.16f * travelEnergy), h)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.72.dp.toPx() + 0.34.dp.toPx() * energy + 0.20.dp.toPx() * stopEnergy),
        blendMode = BlendMode.Plus
    )

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF010615).copy(alpha = 0.020f * travelEnergy),
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
    val e = energy.coerceIn(0f, 1.45f)
    val corner = CornerRadius(h / 2f, h / 2f)
    val sweep = ((sin((phase * 1.08f + edgeSeed * 0.31f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val sweepCenter = -0.38f + 1.76f * sweep + 0.06f * drift + 0.08f * travelDirection * e
    val edgeA = ((sin((phase * 0.73f + edgeSeed) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)
    val edgeB = ((sin((phase * 0.57f + edgeSeed + 0.41f) * 2f * PI.toFloat()) + 1f) * 0.50f).coerceIn(0f, 1f)

    drawContent()

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.040f + 0.056f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.052f * e),
                Color(0xFFFFD86E).copy(alpha = 0.040f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.060f * e),
                Color(0xFFA796FF).copy(alpha = 0.044f * e),
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
                Color.White.copy(alpha = 0.18f * e),
                Color(0xFFFFE17A).copy(alpha = 0.092f * e),
                Color(0xFF67FFF0).copy(alpha = 0.135f * e),
                Color(0xFFFF75D4).copy(alpha = 0.090f * e),
                Color.Transparent
            ),
            start = Offset(w * (sweepCenter - 0.44f), -h * 0.36f),
            end = Offset(w * (sweepCenter + 0.44f), h * 1.36f)
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
                Color.White.copy(alpha = 0.16f + 0.10f * e),
                Color(0xFFFF7AD6).copy(alpha = 0.09f * e),
                Color(0xFFFFD86E).copy(alpha = 0.07f * e),
                Color(0xFF6DFFF0).copy(alpha = 0.11f * e),
                Color(0xFFA796FF).copy(alpha = 0.08f * e),
                Color.White.copy(alpha = 0.10f + 0.05f * e)
            ),
            start = Offset(w * (edgeA - 0.55f), -h * 0.08f),
            end = Offset(w * (edgeA + 0.55f), h * 1.08f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 1.10.dp.toPx() + 0.62.dp.toPx() * e),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF68D0).copy(alpha = 0.30f * e),
                Color.White.copy(alpha = (0.36f + 0.28f * stopEnergy) * e),
                Color(0xFF6FFFF2).copy(alpha = 0.34f * e),
                Color.Transparent
            ),
            start = Offset(w * (edgeB - 0.30f), h * -0.03f),
            end = Offset(w * (edgeB + 0.26f), h * 0.30f)
        ),
        topLeft = Offset(inset * 1.45f, inset * 1.45f),
        size = Size((w - inset * 2.90f).coerceAtLeast(1f), (h - inset * 2.90f).coerceAtLeast(1f)),
        cornerRadius = rimCorner,
        style = Stroke(width = 0.72.dp.toPx() + 0.42.dp.toPx() * e),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.040f + 0.072f * e + 0.060f * stopEnergy),
                Color(0xFF7FFFF2).copy(alpha = 0.036f * e),
                Color.Transparent
            ),
            center = Offset(w * (0.50f + 0.10f * drift), h * 0.36f),
            radius = maxOf(w, h) * 0.42f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
}
