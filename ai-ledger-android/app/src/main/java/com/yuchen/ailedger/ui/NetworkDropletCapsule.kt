package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.ui.gl.DropletGlassStyle
import com.yuchen.ailedger.ui.gl.OpenGLDropletGlassLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

private const val NetworkDropletPrismTau = 6.2831855f
private const val NetworkStaticBreath = 0.50f
private const val NetworkStaticShimmer = 0.52f

private val NetworkDropletPressEasing = CubicBezierEasing(0.10f, 0.00f, 0.05f, 1.00f)
private val NetworkDropletLightEasing = CubicBezierEasing(0.18f, 0.00f, 0.10f, 1.00f)
private val NetworkDropletReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.10f, 1.00f)
private val NetworkDropletShape = RoundedCornerShape(999.dp)

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

private val NetworkIdleDropletStyle = buildNetworkDropletStyle(
    style = NetworkLabDropletStyle,
    pressPositive = 0f,
    latchValue = 0f,
    afterglowValue = 0f,
    breathValue = NetworkStaticBreath,
    shimmerValue = NetworkStaticShimmer,
    effectiveLightX = 0.96f,
    prismAmount = 2.76f,
    purpleMix = 0.67f / 1.5f
)

@Composable
fun NetworkDropletCapsule(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: Any
) {
    val clickAction = remember(onClick) { (onClick as? () -> Unit) ?: {} }
    val pageVisible = LocalPageVisible.current
    val heavyEffectsEnabled = LocalPageHeavyEffectsEnabled.current
    val motionEnabled = state.motionIntensity > 0.02f && pageVisible && heavyEffectsEnabled

    OptimizedNetworkDroplet(
        online = state.onlineEnabled,
        enabled = enabled,
        motionEnabled = motionEnabled,
        modifier = modifier,
        onClick = clickAction
    )
}

@Composable
private fun OptimizedNetworkDroplet(
    online: Boolean,
    enabled: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val coordinates = remember { GlassCoordinateSource() }
    val pressAnim = remember { Animatable(0f) }
    val latchAnim = remember { Animatable(0f) }
    val afterglowAnim = remember { Animatable(0f) }
    val motionSeconds = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val settleJobHolder = remember { arrayOfNulls<Job>(1) }

    var interactionAnimating by remember { mutableStateOf(false) }
    var capsuleSize by remember { mutableStateOf(Size(1f, 1f)) }
    var interactionLightX by remember {
        mutableFloatStateOf(NetworkLabDropletStyle.activeLightX.coerceIn(0.04f, 0.96f))
    }

    LaunchedEffect(online) {
        if (!online && latchAnim.value <= 0.001f) return@LaunchedEffect
        interactionAnimating = true
        latchAnim.stop()
        if (online) {
            if (latchAnim.value < 0.18f) latchAnim.snapTo(0.18f)
            latchAnim.animateTo(
                targetValue = 0.72f,
                animationSpec = tween(170, easing = NetworkDropletLightEasing)
            )
            latchAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.58f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            latchAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(560, easing = FastOutSlowInEasing)
            )
            interactionAnimating = false
        }
    }

    val needsMotionFrames = motionEnabled && (online || interactionAnimating)
    LaunchedEffect(needsMotionFrames) {
        if (!needsMotionFrames) {
            motionSeconds.floatValue = 0f
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                motionSeconds.floatValue = nanos / 1_000_000_000f
            }
        }
    }

    NetworkDropletVisual(
        online = online,
        enabled = enabled,
        motionEnabled = motionEnabled,
        motionSeconds = motionSeconds,
        pressAnim = pressAnim,
        latchAnim = latchAnim,
        afterglowAnim = afterglowAnim,
        interactionAnimating = interactionAnimating,
        interactionLightX = interactionLightX,
        coordinates = coordinates,
        capsuleSize = capsuleSize,
        onCapsuleSizeChange = { next ->
            if (next != capsuleSize) capsuleSize = next
        },
        onLightXChange = { next ->
            if (next != interactionLightX) interactionLightX = next
        },
        onInteractionStart = {
            interactionAnimating = true
            settleJobHolder[0]?.cancel()
        },
        onInteractionSettled = {
            if (!online) interactionAnimating = false
        },
        onClick = onClick,
        scope = scope,
        settleJobHolder = settleJobHolder,
        modifier = modifier
    )
}

@Composable
private fun NetworkDropletVisual(
    online: Boolean,
    enabled: Boolean,
    motionEnabled: Boolean,
    motionSeconds: State<Float>,
    pressAnim: Animatable<Float, *>,
    latchAnim: Animatable<Float, *>,
    afterglowAnim: Animatable<Float, *>,
    interactionAnimating: Boolean,
    interactionLightX: Float,
    coordinates: GlassCoordinateSource,
    capsuleSize: Size,
    onCapsuleSizeChange: (Size) -> Unit,
    onLightXChange: (Float) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionSettled: () -> Unit,
    onClick: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    settleJobHolder: Array<Job?>,
    modifier: Modifier
) {
    val pressValue = pressAnim.value.coerceIn(-0.28f, 1.35f)
    val pressPositive = pressValue.coerceAtLeast(0f)
    val recoilValue = (-pressValue).coerceAtLeast(0f)
    val latchValue = latchAnim.value.coerceIn(0f, 1f)
    val afterglowValue = afterglowAnim.value.coerceIn(0f, 1f)
    val dynamicMotionActive = motionEnabled && (online || interactionAnimating)
    val t = if (dynamicMotionActive) motionSeconds.value else 0f

    val breathValue = if (dynamicMotionActive) {
        (0.50f + 0.50f * sin((t * 2.05f).toDouble()).toFloat()).coerceIn(0f, 1f)
    } else {
        NetworkStaticBreath
    }
    val driftValue = if (dynamicMotionActive) {
        (
            sin((t * 0.78f + 1.10f).toDouble()).toFloat() * 0.17f +
                sin((t * 0.33f + 2.40f).toDouble()).toFloat() * 0.06f
            ).coerceIn(-0.24f, 0.24f)
    } else {
        0f
    }
    val shimmerValue = if (dynamicMotionActive) {
        (
            0.52f +
                sin((t * 1.72f + 0.70f).toDouble()).toFloat() * 0.33f +
                sin((t * 3.18f + 2.10f).toDouble()).toFloat() * 0.13f
            ).coerceIn(0f, 1f)
    } else {
        NetworkStaticShimmer
    }

    val prismAmount = 2.76f
    val purpleAmount = 0.67f
    val purpleMix = (purpleAmount / 1.5f).coerceIn(0f, 1f)
    val breathEnergy = 0.88f + breathValue * 0.14f + shimmerValue * 0.08f
    val holdEnergy = maxOf(pressPositive, latchValue)
    val lightEnergy = (
        holdEnergy * breathEnergy + afterglowValue * 0.55f
        ).coerceIn(0f, 1.30f)
    val refractionEnergy = (
        holdEnergy * (0.92f + breathValue * 0.10f + shimmerValue * 0.08f) +
            afterglowValue * 0.40f
        ).coerceIn(0f, 1.18f)
    val effectiveLightX = (
        interactionLightX + driftValue * holdEnergy
        ).coerceIn(0.04f, 0.96f)
    val prismPhase = effectiveLightX * 0.80f +
        breathValue * 0.21f +
        shimmerValue * 0.19f +
        afterglowValue * 0.17f

    val completelyIdle = !online &&
        !interactionAnimating &&
        pressPositive <= 0.001f &&
        recoilValue <= 0.001f &&
        latchValue <= 0.001f &&
        afterglowValue <= 0.001f

    val animatedStyle = if (completelyIdle) {
        NetworkIdleDropletStyle
    } else {
        buildNetworkDropletStyle(
            style = NetworkLabDropletStyle,
            pressPositive = pressPositive,
            latchValue = latchValue,
            afterglowValue = afterglowValue,
            breathValue = breathValue,
            shimmerValue = shimmerValue,
            effectiveLightX = effectiveLightX,
            prismAmount = prismAmount,
            purpleMix = purpleMix,
            lightEnergyOverride = lightEnergy,
            refractionEnergyOverride = refractionEnergy
        )
    }

    val animatedPurpleWhiteGlow = 0.53f * lightEnergy * purpleAmount
    val animatedPrismGlow = 0.53f * lightEnergy * (
        0.34f + prismAmount * 0.64f
        ).coerceIn(0f, 2.2f)
    val animatedBackgroundGlow = 0.38f * (
        lightEnergy + recoilValue * 0.35f + prismAmount * lightEnergy * 0.16f
        ).coerceIn(0f, 1.8f)
    val animatedOuterGlow = 0.46f * (
        lightEnergy +
            afterglowValue * 0.28f +
            recoilValue * 0.30f +
            prismAmount * lightEnergy * 0.22f
        ).coerceIn(0f, 1.9f)
    val animatedWarmGlow = 0.54f * (
        lightEnergy * (0.20f + purpleMix * 0.46f + prismAmount * 0.18f) +
            pressPositive * 0.12f
        ).coerceIn(0f, 1.8f)
    val contentAlpha = (
        0.50f + lightEnergy * 0.36f + recoilValue * 0.10f
        ).coerceIn(0.44f, 0.98f)

    Box(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (animatedPrismGlow > 0.001f) {
            NetworkDropletBackgroundGlow(
                activeGlow = animatedPrismGlow,
                backgroundGlow = animatedBackgroundGlow,
                outerGlow = animatedOuterGlow,
                warmGlow = animatedWarmGlow,
                prismStrength = prismAmount,
                phase = prismPhase,
                modifier = Modifier.fillMaxWidth().height(90.dp)
            )
        }

        NetworkDropletContactShadow(
            alpha = 0.18f * (
                0.72f + pressPositive * 0.62f + afterglowValue * 0.32f
                ),
            offsetX = 3.0f +
                pressPositive * 2.2f +
                recoilValue * if (effectiveLightX > 0.5f) 1.2f else -1.2f,
            offsetY = 7.0f + pressPositive * 4.4f - recoilValue * 1.1f,
            softness = 18f + lightEnergy * 5.5f + recoilValue * 4.0f,
            modifier = Modifier.fillMaxWidth().height(82.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(vertical = 2.dp)
                .onSizeChanged { size ->
                    onCapsuleSizeChange(
                        Size(
                            width = size.width.coerceAtLeast(1).toFloat(),
                            height = size.height.coerceAtLeast(1).toFloat()
                        )
                    )
                }
                .onGloballyPositioned { coordinates.coordinates = it }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(effectiveLightX, 0.50f)
                    scaleX = 1f +
                        pressPositive * 0.066f +
                        latchValue * 0.018f -
                        afterglowValue * 0.006f -
                        recoilValue * 0.020f
                    scaleY = 1f -
                        pressPositive * 0.074f +
                        afterglowValue * 0.014f +
                        recoilValue * 0.026f
                    translationX = (effectiveLightX - 0.5f) * pressPositive * 5.8f +
                        recoilValue * if (effectiveLightX > 0.5f) 3.0f else -3.0f
                    translationY = pressPositive * 5.2f -
                        afterglowValue * 1.1f -
                        recoilValue * 1.8f
                    rotationZ = (effectiveLightX - 0.5f) * pressPositive * 0.72f +
                        recoilValue * if (effectiveLightX > 0.5f) 0.36f else -0.36f
                }
                .pointerInput(online, enabled, motionEnabled, onClick) {
                    awaitEachGesture {
                        fun updateLight(position: Offset) {
                            onLightXChange(
                                (position.x / capsuleSize.width.coerceAtLeast(1f))
                                    .coerceIn(0.04f, 0.96f)
                            )
                        }

                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateLight(down.position)

                        if (motionEnabled) {
                            onInteractionStart()
                            scope.launch {
                                afterglowAnim.stop()
                                afterglowAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(80, easing = FastOutSlowInEasing)
                                )
                            }
                            scope.launch {
                                pressAnim.stop()
                                if (pressAnim.value < 0.24f) pressAnim.snapTo(0.24f)
                                pressAnim.animateTo(
                                    targetValue = 1.20f,
                                    animationSpec = tween(
                                        145,
                                        easing = NetworkDropletPressEasing
                                    )
                                )
                                pressAnim.animateTo(
                                    targetValue = 0.96f,
                                    animationSpec = spring(
                                        dampingRatio = 0.52f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }

                        var releasedAt = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (tracked != null) {
                                updateLight(tracked.position)
                                if (!tracked.pressed) {
                                    releasedAt = tracked.uptimeMillis
                                    break
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }

                        val wasTap = releasedAt - down.uptimeMillis < 260L
                        if (wasTap && enabled) onClick()

                        if (motionEnabled) {
                            scope.launch {
                                pressAnim.stop()
                                pressAnim.animateTo(
                                    targetValue = -0.22f,
                                    animationSpec = tween(
                                        130,
                                        easing = NetworkDropletReleaseEasing
                                    )
                                )
                                pressAnim.animateTo(
                                    targetValue = 0.08f,
                                    animationSpec = spring(
                                        dampingRatio = 0.38f,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                                pressAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(210, easing = FastOutSlowInEasing)
                                )
                            }

                            val settleJob = scope.launch {
                                afterglowAnim.stop()
                                val afterglowTarget = if (wasTap && online) 0.42f else 0.78f
                                if (afterglowAnim.value < afterglowTarget) {
                                    afterglowAnim.animateTo(
                                        targetValue = afterglowTarget,
                                        animationSpec = tween(
                                            95,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                                afterglowAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        if (online) 460 else 820,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                onInteractionSettled()
                            }
                            settleJobHolder[0] = settleJob
                        }
                    }
                }
                .clip(NetworkDropletShape),
            contentAlignment = Alignment.Center
        ) {
            OpenGLDropletGlassLayer(
                radius = 999,
                coordinateSource = coordinates,
                style = animatedStyle,
                modifier = Modifier.fillMaxSize()
            )

            if (animatedPurpleWhiteGlow > 0.001f || animatedWarmGlow > 0.001f) {
                NetworkDropletActiveOverlay(
                    activeGlow = animatedPurpleWhiteGlow,
                    warmGlow = animatedWarmGlow,
                    purpleMix = purpleMix,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (animatedPrismGlow > 0.001f && lightEnergy > 0.001f) {
                NetworkDropletPrismOverlay(
                    activeGlow = animatedPrismGlow,
                    energy = lightEnergy,
                    phase = prismPhase,
                    prismStrength = prismAmount,
                    lightX = effectiveLightX,
                    shimmer = shimmerValue,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .graphicsLayer { alpha = contentAlpha }
            ) {
                Text(
                    text = "•",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (online) "在线" else "联网",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildNetworkDropletStyle(
    style: DropletGlassStyle,
    pressPositive: Float,
    latchValue: Float,
    afterglowValue: Float,
    breathValue: Float,
    shimmerValue: Float,
    effectiveLightX: Float,
    prismAmount: Float,
    purpleMix: Float,
    lightEnergyOverride: Float? = null,
    refractionEnergyOverride: Float? = null
): DropletGlassStyle {
    val holdEnergy = maxOf(pressPositive, latchValue)
    val breathEnergy = 0.88f + breathValue * 0.14f + shimmerValue * 0.08f
    val lightEnergy = lightEnergyOverride ?: (
        holdEnergy * breathEnergy + afterglowValue * 0.55f
        ).coerceIn(0f, 1.30f)
    val refractionEnergy = refractionEnergyOverride ?: (
        holdEnergy * (0.92f + breathValue * 0.10f + shimmerValue * 0.08f) +
            afterglowValue * 0.40f
        ).coerceIn(0f, 1.18f)
    val prismPhase = effectiveLightX * 0.80f +
        breathValue * 0.21f +
        shimmerValue * 0.19f +
        afterglowValue * 0.17f
    val prismMix = (lightEnergy * prismAmount * 0.80f).coerceIn(0f, 1f)
    val prismRed = 0.36f + networkDropletPrismChannel(prismPhase) * 0.64f
    val prismGreen = 0.38f + networkDropletPrismChannel(prismPhase + 0.34f) * 0.62f
    val prismBlue = 0.50f + networkDropletPrismChannel(prismPhase + 0.68f) * 0.50f
    val warmRed = 0.70f + networkDropletPrismChannel(prismPhase + 0.08f) * 0.30f
    val warmGreen = 0.30f + networkDropletPrismChannel(prismPhase + 0.28f) * 0.62f
    val warmBlue = 0.58f + networkDropletPrismChannel(prismPhase + 0.58f) * 0.42f

    return style.copy(
        bodyBulgePx = style.bodyBulgePx +
            pressPositive * 14.0f +
            latchValue * 5.0f -
            afterglowValue * 3.0f,
        edgePullPx = style.edgePullPx +
            pressPositive * 32.0f +
            latchValue * 12.0f +
            afterglowValue * 9.0f,
        edgeWidthPx = style.edgeWidthPx +
            pressPositive * 5.8f +
            latchValue * 2.8f,
        bottomGlow = style.bottomGlow * (
            0.68f + lightEnergy * (
                0.20f + purpleMix * 0.18f + prismAmount * 0.08f
                )
            ),
        topGloss = style.topGloss + lightEnergy * (
            0.12f + purpleMix * 0.22f + prismAmount * 0.10f
            ),
        cornerGloss = style.cornerGloss +
            lightEnergy * (0.10f + purpleMix * 0.18f) +
            prismAmount * shimmerValue * lightEnergy * 0.12f,
        innerDark = (
            style.innerDark + pressPositive * 0.10f - lightEnergy * 0.08f
            ).coerceIn(0f, 1f),
        alpha = (style.alpha + lightEnergy * 0.13f).coerceIn(0f, 1f),
        activeGlow = style.activeGlow * lightEnergy * (
            0.50f + purpleMix * 0.28f + prismAmount * 0.16f
            ),
        activeRefraction = style.activeRefraction * refractionEnergy,
        activeRimRefraction = style.activeRimRefraction *
            refractionEnergy *
            (1f + prismAmount * 0.12f),
        activeLightX = networkDropletLerp(
            style.activeLightX.coerceIn(0f, 1f),
            effectiveLightX,
            holdEnergy.coerceIn(0f, 1f)
        ),
        activeLightSpread = (
            style.activeLightSpread * (0.50f + lightEnergy * 0.52f) +
                pressPositive * 0.12f +
                prismAmount * 0.035f
            ).coerceIn(0f, 1f),
        activeLightThickness = (
            style.activeLightThickness * (
                0.58f + lightEnergy * 0.46f + prismAmount * 0.06f
                )
            ).coerceIn(0.015f, 0.42f),
        activeHotspot = style.activeHotspot * (
            lightEnergy * (0.35f + purpleMix * 0.45f + prismAmount * 0.16f) +
                shimmerValue * holdEnergy * 0.18f
            ),
        activeEntryPearl = style.activeEntryPearl * (
            lightEnergy * (0.30f + purpleMix * 0.44f + prismAmount * 0.16f) +
                pressPositive * 0.12f
            ),
        activeRimPearl = style.activeRimPearl * (
            lightEnergy * (0.25f + purpleMix * 0.32f + prismAmount * 0.24f) +
                shimmerValue * holdEnergy * 0.12f
            ),
        activeCenterClear = (
            style.activeCenterClear + lightEnergy * 0.24f
            ).coerceIn(0f, 1f),
        activeVolumeWarmth = style.activeVolumeWarmth * (
            0.24f + lightEnergy * (
                purpleMix * 0.36f + prismAmount * 0.22f
                )
            ),
        activeRimGather = style.activeRimGather * (
            lightEnergy +
                afterglowValue * 0.22f +
                prismAmount * shimmerValue * 0.07f
            ),
        activeRimFlow = style.activeRimFlow * (
            0.42f + lightEnergy * 0.88f + prismAmount * 0.08f
            ),
        accentRed = networkDropletLerp(style.accentRed, prismRed, prismMix),
        accentGreen = networkDropletLerp(style.accentGreen, prismGreen, prismMix),
        accentBlue = networkDropletLerp(style.accentBlue, prismBlue, prismMix),
        warmRed = networkDropletLerp(style.warmRed, warmRed, prismMix),
        warmGreen = networkDropletLerp(style.warmGreen, warmGreen, prismMix),
        warmBlue = networkDropletLerp(style.warmBlue, warmBlue, prismMix)
    )
}

private fun networkDropletLerp(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}

private fun networkDropletPrismChannel(phase: Float): Float {
    val wrapped = phase - phase.toInt()
    return (
        0.5f + 0.5f * sin((wrapped * NetworkDropletPrismTau).toDouble()).toFloat()
        ).coerceIn(0f, 1f)
}

@Composable
private fun NetworkDropletBackgroundGlow(
    activeGlow: Float,
    backgroundGlow: Float,
    outerGlow: Float,
    warmGlow: Float,
    prismStrength: Float,
    phase: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val active = activeGlow.coerceIn(0f, 2f)
            val bg = backgroundGlow.coerceIn(0f, 2f)
            val outer = outerGlow.coerceIn(0f, 2f)
            val warm = warmGlow.coerceIn(0f, 2f)
            val prism = prismStrength.coerceIn(0f, 3f)
            val radius = size.height * 0.46f
            val drift = networkDropletPrismChannel(phase)
            val corner = CornerRadius(radius, radius)
            val coldBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFBFEAFF).copy(alpha = bg * active * 0.18f),
                    Color(0xFF6CCBFF).copy(alpha = bg * active * 0.06f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.50f, size.height * 0.42f),
                radius = size.width * 0.62f
            )
            val warmBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF72B7).copy(alpha = warm * active * 0.08f),
                    Color(0xFFFFB56F).copy(alpha = warm * active * 0.025f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.48f, size.height * 0.78f),
                radius = size.width * 0.44f
            )
            val prismBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF4FA7).copy(alpha = outer * active * prism * 0.035f),
                    Color(0xFF80FFD8).copy(alpha = outer * active * prism * 0.028f),
                    Color(0xFF78A8FF).copy(alpha = outer * active * prism * 0.020f),
                    Color.Transparent
                ),
                center = Offset(
                    size.width * (0.34f + drift * 0.32f),
                    size.height * 0.34f
                ),
                radius = size.width * 0.68f
            )

            onDrawBehind {
                drawRoundRect(
                    brush = coldBrush,
                    topLeft = Offset(size.width * 0.02f, size.height * 0.08f),
                    size = Size(size.width * 0.96f, size.height * 0.80f),
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = warmBrush,
                    topLeft = Offset(size.width * 0.08f, size.height * 0.42f),
                    size = Size(size.width * 0.84f, size.height * 0.42f),
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = prismBrush,
                    topLeft = Offset(size.width * 0.03f, size.height * 0.10f),
                    size = Size(size.width * 0.94f, size.height * 0.74f),
                    cornerRadius = corner,
                    blendMode = BlendMode.Plus
                )
            }
        }
    )
}

@Composable
private fun NetworkDropletActiveOverlay(
    activeGlow: Float,
    warmGlow: Float,
    purpleMix: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val active = activeGlow.coerceIn(0f, 2f)
            val warm = warmGlow.coerceIn(0f, 2f)
            val purple = purpleMix.coerceIn(0f, 1f)
            val radius = size.height / 2f
            val corner = CornerRadius(radius, radius)
            val topBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = active * purple * 0.16f),
                    Color.White.copy(alpha = active * purple * 0.024f),
                    Color.Transparent
                )
            )
            val bottomBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF73C5).copy(alpha = active * warm * purple * 0.10f),
                    Color(0xFFFFB06C).copy(alpha = active * warm * purple * 0.032f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.50f, size.height * 1.03f),
                radius = size.width * 0.42f
            )

            onDrawBehind {
                drawRoundRect(
                    brush = topBrush,
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = bottomBrush,
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
            }
        }
    )
}

@Composable
private fun NetworkDropletPrismOverlay(
    activeGlow: Float,
    energy: Float,
    phase: Float,
    prismStrength: Float,
    lightX: Float,
    shimmer: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val active = activeGlow.coerceIn(0f, 2.4f)
            val e = energy.coerceIn(0f, 1.35f)
            val prism = prismStrength.coerceIn(0f, 3f)
            val radius = size.height / 2f
            val corner = CornerRadius(radius, radius)
            val sweep = (phase - phase.toInt()) * size.width * 0.66f
            val alpha = (active * e * prism).coerceIn(0f, 4.2f)
            val edgeX = if (lightX > 0.5f) {
                (lightX + 0.06f).coerceAtMost(0.92f)
            } else {
                (lightX - 0.06f).coerceAtLeast(0.08f)
            }
            val edgeY = 0.42f + (shimmer - 0.5f) * 0.08f
            val spectrumBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF2E93).copy(alpha = alpha * 0.045f),
                    Color(0xFFFFD84D).copy(alpha = alpha * 0.038f),
                    Color(0xFF55FFD6).copy(alpha = alpha * 0.050f),
                    Color(0xFF4F89FF).copy(alpha = alpha * 0.046f),
                    Color(0xFFC05CFF).copy(alpha = alpha * 0.052f)
                ),
                start = Offset(-size.width * 0.42f + sweep, 0f),
                end = Offset(size.width * 1.04f + sweep, size.height)
            )
            val topLightBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = alpha * (0.05f + shimmer * 0.02f)),
                    Color(0xFF9EFFF0).copy(alpha = alpha * 0.03f),
                    Color.Transparent
                ),
                start = Offset(size.width * (lightX - 0.42f), size.height * 0.02f),
                end = Offset(size.width * (lightX + 0.40f), size.height * 0.28f)
            )
            val edgePearlBrush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha * 0.060f),
                    Color(0xFFFFF4B5).copy(alpha = alpha * 0.045f),
                    Color(0xFF63FFE4).copy(alpha = alpha * 0.040f),
                    Color.Transparent
                ),
                center = Offset(size.width * edgeX, size.height * edgeY),
                radius = size.width * (0.18f + shimmer * 0.10f)
            )
            val diagonalBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFF0A8).copy(alpha = alpha * 0.020f),
                    Color(0xFF69FFE8).copy(alpha = alpha * 0.028f),
                    Color.Transparent
                ),
                start = Offset(size.width * 0.10f, size.height * 0.88f),
                end = Offset(size.width * 0.90f, size.height * 0.18f)
            )

            onDrawBehind {
                drawRoundRect(
                    brush = spectrumBrush,
                    cornerRadius = corner,
                    blendMode = BlendMode.Plus
                )
                drawRoundRect(
                    brush = topLightBrush,
                    topLeft = Offset(size.width * 0.06f, size.height * 0.06f),
                    size = Size(size.width * 0.88f, size.height * 0.24f),
                    cornerRadius = corner,
                    blendMode = BlendMode.Screen
                )
                drawRoundRect(
                    brush = edgePearlBrush,
                    cornerRadius = corner,
                    blendMode = BlendMode.Plus
                )
                drawRoundRect(
                    brush = diagonalBrush,
                    cornerRadius = corner,
                    blendMode = BlendMode.Plus
                )
            }
        }
    )
}

@Composable
private fun NetworkDropletContactShadow(
    alpha: Float,
    offsetX: Float,
    offsetY: Float,
    softness: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val dx = offsetX.dp.toPx()
            val dy = offsetY.dp.toPx()
            val blur = softness.dp.toPx()
            val coreAlpha = alpha.coerceIn(0f, 1f)
            val shadowHeight = (
                size.height * 0.44f + blur * 0.30f
                ).coerceAtLeast(6f)
            val shadowBrush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = coreAlpha * 0.45f),
                    Color.Black.copy(alpha = coreAlpha * 0.13f),
                    Color.Transparent
                ),
                center = Offset(
                    size.width * 0.52f + dx,
                    size.height * 0.62f + dy
                ),
                radius = size.width * 0.62f + blur
            )

            onDrawBehind {
                drawOval(
                    brush = shadowBrush,
                    topLeft = Offset(
                        dx + size.width * 0.08f,
                        dy + size.height * 0.40f - blur * 0.12f
                    ),
                    size = Size(size.width * 0.84f, shadowHeight),
                    blendMode = BlendMode.Multiply
                )
            }
        }
    )
}
