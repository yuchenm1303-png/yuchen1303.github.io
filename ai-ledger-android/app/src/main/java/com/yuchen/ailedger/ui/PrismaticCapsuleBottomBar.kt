package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.LocalNewOpenGlGlassStyleOverride
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

private val BottomNavTabs = AppTab.entries.toList()
private val BottomNavCapsuleShape = RoundedCornerShape(999.dp)
private val BottomNavIndexSpring = spring<Float>(
    dampingRatio = 0.56f,
    stiffness = Spring.StiffnessMediumLow
)
private val BottomNavPressSpring = spring<Float>(
    dampingRatio = 0.46f,
    stiffness = Spring.StiffnessMediumLow
)
private val BottomNavTabPressSpring = spring<Float>(
    dampingRatio = 0.52f,
    stiffness = Spring.StiffnessMediumLow
)
private val BottomNavSelectedSpring = spring<Float>(
    dampingRatio = 0.56f,
    stiffness = Spring.StiffnessLow
)
private val BottomNavArrivalSpring = spring<Float>(
    dampingRatio = 0.52f,
    stiffness = Spring.StiffnessMediumLow
)
private val BottomNavArrivalTween = tween<Float>(105, easing = FastOutSlowInEasing)
private val BottomNavTravelPhaseTween = tween<Float>(620, easing = LinearEasing)
private val BottomNavPressPhaseTween = tween<Float>(240, easing = FastOutSlowInEasing)
private val BottomNavSnap = snap<Float>()
private val DisableBottomNavBaseDispersion: (GlassBorderStyle) -> GlassBorderStyle = { style ->
    style.copy(
        newOpenGlDispersionStrength = 0f,
        newOpenGlDispersionDistanceDp = 0f
    )
}

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
        val tabs = BottomNavTabs
        val density = LocalDensity.current
        val currentIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
        val motion = motionIntensity.coerceIn(0f, 1f)
        val motionEnabled = motion > 0.02f
        val animatedIndex = animateFloatAsState(
            targetValue = currentIndex.toFloat(),
            animationSpec = if (motionEnabled) BottomNavIndexSpring else BottomNavSnap,
            label = "bottom-nav-capsule-index"
        )
        val arrivalPulse = remember { Animatable(0f) }
        val prismPhase = remember { Animatable(0f) }
        var edgeSeed by remember { mutableFloatStateOf(0.37f) }

        LaunchedEffect(currentIndex, motionEnabled) {
            edgeSeed = Random.nextFloat()
            arrivalPulse.snapTo(0f)
            if (!motionEnabled) return@LaunchedEffect

            launch {
                prismPhase.animateTo(
                    targetValue = prismPhase.value + 1f,
                    animationSpec = BottomNavTravelPhaseTween
                )
            }
            delay(96)
            arrivalPulse.animateTo(0.90f, BottomNavArrivalTween)
            arrivalPulse.animateTo(0f, BottomNavArrivalSpring)
        }

        val interactionSources = remember(tabs) {
            List(tabs.size) { MutableInteractionSource() }
        }
        val selectedPressed = interactionSources[currentIndex].collectIsPressedAsState()
        val pressEnergy = animateFloatAsState(
            targetValue = if (selectedPressed.value) 1f else 0f,
            animationSpec = if (motionEnabled) BottomNavPressSpring else BottomNavSnap,
            label = "bottom-nav-press"
        )

        LaunchedEffect(selectedPressed.value, motionEnabled) {
            if (selectedPressed.value && motionEnabled) {
                prismPhase.animateTo(
                    targetValue = prismPhase.value + 0.32f,
                    animationSpec = BottomNavPressPhaseTween
                )
            }
        }

        CompositionLocalProvider(
            LocalNewOpenGlGlassStyleOverride provides DisableBottomNavBaseDispersion
        ) {
            OpenGlShellGlass(
                quality = quality,
                glassIntensity = glassIntensity * 1.015f,
                motionIntensity = motionIntensity,
                radius = 999,
                modifier = Modifier.fillMaxSize(),
                mood = OpenGlShellMood.List,
                forceOpenGl = true
            ) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    val totalWidthPx = with(density) { maxWidth.toPx() }
                    val slotWidthPx = totalWidthPx / tabs.size.coerceAtLeast(1)

                    BottomBarSelector(
                        currentIndex = currentIndex,
                        tabCount = tabs.size,
                        totalWidthPx = totalWidthPx,
                        slotWidthPx = slotWidthPx,
                        density = density,
                        quality = quality,
                        glassIntensity = glassIntensity,
                        motionIntensity = motionIntensity,
                        motion = motion,
                        animatedIndex = animatedIndex,
                        pressEnergy = pressEnergy,
                        arrivalPulse = arrivalPulse,
                        prismPhase = prismPhase,
                        edgeSeed = edgeSeed
                    )

                    BottomBarTabsRow(
                        tabs = tabs,
                        currentTab = currentTab,
                        interactionSources = interactionSources,
                        motionEnabled = motionEnabled,
                        motion = motion,
                        arrivalPulse = arrivalPulse,
                        onTabChange = onTabChange
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.BottomBarSelector(
    currentIndex: Int,
    tabCount: Int,
    totalWidthPx: Float,
    slotWidthPx: Float,
    density: androidx.compose.ui.unit.Density,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    motion: Float,
    animatedIndex: State<Float>,
    pressEnergy: State<Float>,
    arrivalPulse: Animatable<Float, AnimationVector1D>,
    prismPhase: Animatable<Float, AnimationVector1D>,
    edgeSeed: Float
) {
    val animatedIndexValue = animatedIndex.value
    val pressEnergyValue = pressEnergy.value
    val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motion
    val phase = prismPhase.value
    val edgeTarget = if (currentIndex == 0 || currentIndex == tabCount - 1) 1f else 0f
    val travelEnergy = (
        abs(animatedIndexValue - currentIndex.toFloat()) / 0.84f
        ).coerceIn(0f, 1f) * motion
    val edgeSafeTravel = travelEnergy * (1f - 0.28f * edgeTarget)
    val travelDirection = sign(currentIndex.toFloat() - animatedIndexValue).coerceIn(-1f, 1f)
    val activeEnergy = maxOf(edgeSafeTravel, pressEnergyValue, stopEnergy)
    val maxStretch = 1.015f - 0.030f * edgeTarget
    val stretch = 0.72f +
        0.275f * edgeSafeTravel +
        0.130f * pressEnergyValue -
        0.018f * stopEnergy
    val selectorWidthPx = slotWidthPx * stretch.coerceIn(0.68f, maxStretch)
    val selectorWidth = with(density) { selectorWidthPx.toDp() }
    val leadPx = travelDirection * slotWidthPx * 0.030f * edgeSafeTravel
    val rawX = slotWidthPx * animatedIndexValue + (slotWidthPx - selectorWidthPx) / 2f + leadPx
    val selectorX = rawX.coerceIn(0f, (totalWidthPx - selectorWidthPx).coerceAtLeast(0f))
    val heightDp = 42.dp +
        3.0.dp * stopEnergy -
        6.2.dp * edgeSafeTravel -
        4.6.dp * pressEnergyValue
    val selectedDrift = sin((phase + currentIndex * 0.17f) * 2f * PI.toFloat())

    Box(
        Modifier
            .align(Alignment.CenterStart)
            .width(selectorWidth)
            .height(heightDp)
            .graphicsLayer {
                translationX = selectorX
                translationY = 1.35f * pressEnergyValue -
                    2.35f * edgeSafeTravel -
                    0.90f * stopEnergy
                scaleX = 1f +
                    0.170f * edgeSafeTravel +
                    0.098f * pressEnergyValue -
                    0.024f * stopEnergy
                scaleY = 1f -
                    0.176f * edgeSafeTravel -
                    0.108f * pressEnergyValue +
                    0.080f * stopEnergy
                shadowElevation = 0.18f + 0.42f * activeEnergy.coerceIn(0f, 1f)
            }
            .clip(BottomNavCapsuleShape)
    ) {
        CompositionLocalProvider(
            LocalNewOpenGlGlassStyleOverride provides null
        ) {
            OpenGlShellGlass(
                quality = quality,
                glassIntensity = glassIntensity * (
                    1.035f +
                        0.08f * edgeSafeTravel +
                        0.07f * pressEnergyValue +
                        0.045f * stopEnergy
                    ),
                motionIntensity = motionIntensity,
                radius = 999,
                modifier = Modifier.fillMaxSize(),
                mood = OpenGlShellMood.List,
                forceOpenGl = true
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(BottomNavCapsuleShape)
                        .bottomSliderPrismOptics(
                            phase = phase,
                            energy = (
                                0.18f +
                                    0.44f * edgeSafeTravel +
                                    0.27f * pressEnergyValue +
                                    0.34f * stopEnergy
                                ).coerceIn(0f, 1.12f),
                            drift = selectedDrift,
                            edgeSeed = edgeSeed,
                            stopEnergy = stopEnergy,
                            travelDirection = travelDirection,
                            activeEnergy = activeEnergy
                        )
                )
            }
        }
    }
}

@Composable
private fun BottomBarTabsRow(
    tabs: List<AppTab>,
    currentTab: AppTab,
    interactionSources: List<MutableInteractionSource>,
    motionEnabled: Boolean,
    motion: Float,
    arrivalPulse: Animatable<Float, AnimationVector1D>,
    onTabChange: (AppTab) -> Unit
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            BottomBarTabItem(
                tab = tab,
                selected = tab == currentTab,
                interactionSource = interactionSources[index],
                motionEnabled = motionEnabled,
                motion = motion,
                arrivalPulse = arrivalPulse,
                onClick = { onTabChange(tab) }
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomBarTabItem(
    tab: AppTab,
    selected: Boolean,
    interactionSource: MutableInteractionSource,
    motionEnabled: Boolean,
    motion: Float,
    arrivalPulse: Animatable<Float, AnimationVector1D>,
    onClick: () -> Unit
) {
    val pressed = interactionSource.collectIsPressedAsState()
    val tabPress = animateFloatAsState(
        targetValue = if (pressed.value) 1f else 0f,
        animationSpec = if (motionEnabled) BottomNavTabPressSpring else BottomNavSnap,
        label = "bottom-nav-tab-press-${tab.name}"
    )
    val selectedPop = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motionEnabled) BottomNavSelectedSpring else BottomNavSnap,
        label = "bottom-nav-tab-selected-${tab.name}"
    )
    val selectedPopValue = selectedPop.value

    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(BottomNavCapsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                val stopEnergy = arrivalPulse.value.coerceIn(0f, 1f) * motion
                translationY = -1.4f * selectedPop.value +
                    0.8f * tabPress.value -
                    0.6f * stopEnergy * selectedPop.value
                scaleX = 1f + 0.046f * selectedPop.value + 0.026f * tabPress.value
                scaleY = 1f + 0.032f * selectedPop.value - 0.016f * tabPress.value
                alpha = 0.54f + 0.46f * selectedPop.value
            }
        ) {
            Text(
                text = tab.icon,
                color = Color.White.copy(alpha = 0.72f + 0.26f * selectedPopValue),
                fontSize = if (tab == AppTab.Assistant) 11.sp else 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = tab.title,
                color = Color.White.copy(alpha = 0.70f + 0.28f * selectedPopValue),
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val active = activeEnergy.coerceIn(0f, 1f)
    val e = energy.coerceIn(0f, 1.12f)
    val corner = CornerRadius(h / 2f, h / 2f)
    val sweep = (
        (sin((phase * 0.98f + edgeSeed * 0.31f) * 2f * PI.toFloat()) + 1f) * 0.50f
        ).coerceIn(0f, 1f)
    val sweepCenter = -0.38f +
        1.76f * sweep +
        0.06f * drift +
        0.050f * travelDirection * e
    val glassEnergy = (0.30f + e * 0.70f + stopEnergy * 0.20f).coerceIn(0f, 1.35f)
    val rimEnergy = (0.34f + active * 0.66f + e * 0.18f).coerceIn(0f, 1.25f)
    val fullSize = Size(w, h)
    val baseBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.055f + 0.042f * glassEnergy),
            Color(0xFFE9FFFF).copy(alpha = 0.030f + 0.026f * glassEnergy),
            Color(0xFF304A76).copy(alpha = 0.032f + 0.030f * glassEnergy),
            Color(0xFF030714).copy(alpha = 0.060f + 0.050f * glassEnergy)
        ),
        start = Offset.Zero,
        end = Offset(w, h)
    )
    val centerLightBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.105f * glassEnergy),
            Color(0xFFBFFAF6).copy(alpha = 0.040f * glassEnergy),
            Color.Transparent
        ),
        center = Offset(w * (0.50f + 0.10f * drift), h * 0.18f),
        radius = maxOf(w, h) * 0.72f
    )
    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.145f + 0.125f * rimEnergy),
            Color(0xFFE8FFFF).copy(alpha = 0.060f + 0.055f * rimEnergy),
            Color.Transparent,
            Color(0xFF01030A).copy(alpha = 0.145f + 0.105f * rimEnergy),
            Color.White.copy(alpha = 0.050f + 0.030f * rimEnergy)
        ),
        startY = 0f,
        endY = h
    )
    val darkRimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF00030A).copy(alpha = 0.120f + 0.145f * rimEnergy)
        ),
        startY = h * 0.42f,
        endY = h
    )
    val sweepBrush = Brush.linearGradient(
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
    )
    val travelingLightBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.092f + 0.082f * e + 0.046f * stopEnergy),
            Color(0xFF7FFFF2).copy(alpha = 0.040f + 0.036f * e),
            Color.Transparent
        ),
        center = Offset(w * (0.08f + 0.84f * sweep), h * 0.02f),
        radius = maxOf(w, h) * 0.42f
    )
    val idleBrush = if (active < 0.012f) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.030f),
                Color(0xFF7FFFF2).copy(alpha = 0.018f),
                Color.White.copy(alpha = 0.024f)
            ),
            start = Offset.Zero,
            end = Offset(w, h)
        )
    } else {
        null
    }
    val outerStroke = Stroke(1.15.dp.toPx() + 0.85.dp.toPx() * rimEnergy)
    val innerStroke = Stroke(2.8.dp.toPx() + 1.8.dp.toPx() * rimEnergy)
    val outerTopLeft = Offset(0.45f, 0.45f)
    val outerSize = Size(
        (w - 0.90f).coerceAtLeast(1f),
        (h - 0.90f).coerceAtLeast(1f)
    )
    val innerInset = 2.0.dp.toPx()
    val innerTopLeft = Offset(innerInset, innerInset)
    val innerSize = Size(
        (w - innerInset * 2f).coerceAtLeast(1f),
        (h - innerInset * 2f).coerceAtLeast(1f)
    )

    onDrawWithContent {
        drawRoundRect(
            brush = baseBrush,
            topLeft = Offset.Zero,
            size = fullSize,
            cornerRadius = corner,
            blendMode = BlendMode.SrcOver
        )
        drawRoundRect(
            brush = centerLightBrush,
            topLeft = Offset.Zero,
            size = fullSize,
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )

        drawContent()

        drawRoundRect(
            brush = rimBrush,
            topLeft = outerTopLeft,
            size = outerSize,
            cornerRadius = corner,
            style = outerStroke,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = darkRimBrush,
            topLeft = innerTopLeft,
            size = innerSize,
            cornerRadius = corner,
            style = innerStroke,
            blendMode = BlendMode.Multiply
        )
        drawRoundRect(
            brush = sweepBrush,
            topLeft = Offset.Zero,
            size = fullSize,
            cornerRadius = corner,
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = travelingLightBrush,
            topLeft = Offset.Zero,
            size = fullSize,
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
        idleBrush?.let { brush ->
            drawRoundRect(
                brush = brush,
                topLeft = Offset.Zero,
                size = fullSize,
                cornerRadius = corner,
                blendMode = BlendMode.Screen
            )
        }
    }
}
