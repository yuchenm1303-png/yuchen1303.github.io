package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassCardLayer
import kotlin.random.Random
import kotlinx.coroutines.launch

enum class GlassRole(
    val fillScale: Float,
    val rimScale: Float,
    val glowScale: Float,
    val shadowDp: Int
) {
    Shell(0f, 1.00f, 1.00f, 10),
    Card(0f, 1.00f, 1.00f, 10),
    Chip(0f, 1.00f, 1.00f, 8),
    Nav(0f, 1.00f, 1.00f, 10),
    Floating(0f, 1.00f, 1.00f, 10),
    Flex(0f, 1.00f, 1.00f, 8)
}

val LocalOpenGLGlassViewportActive = compositionLocalOf { false }

private const val STRONG_GLASS_BLUR_DP = 118
private const val MEDIUM_GLASS_BLUR_DP = 82
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 0.96f
private const val UNIFIED_EDGE_STRENGTH = 0.22f
private const val USE_CARD_BOUND_OPENGL_GLASS = true

private val ShellPressPreloadEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val ShellPressSinkEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val ShellPressReleaseEasing = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val ShellPressPulseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)
private val OrdinaryPressEasing = CubicBezierEasing(0.12f, 0.00f, 0.08f, 1.00f)
private val OrdinarySinkEasing = CubicBezierEasing(0.10f, 0.00f, 0.08f, 1.00f)
private val OrdinaryReleaseEasing = CubicBezierEasing(0.14f, 0.00f, 0.12f, 1.00f)

private fun blurForRole(role: GlassRole): Int = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Floating -> STRONG_GLASS_BLUR_DP
    GlassRole.Nav -> 104
    GlassRole.Chip, GlassRole.Flex -> MEDIUM_GLASS_BLUR_DP
}

private fun roleUsesUnifiedBackdrop(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell -> true
    GlassRole.Card, GlassRole.Nav, GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

private fun roleUsesCardBoundOpenGl(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell -> true
    GlassRole.Card, GlassRole.Nav, GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

private fun effectiveGlassRadius(radius: Int, role: GlassRole): Int {
    if (radius >= 999) return radius
    return when (role) {
        GlassRole.Shell -> radius.coerceAtLeast(30)
        GlassRole.Card -> radius.coerceAtLeast(28)
        GlassRole.Floating -> radius.coerceAtLeast(26)
        GlassRole.Nav -> radius.coerceAtLeast(999)
        GlassRole.Chip, GlassRole.Flex -> radius
    }
}

private fun glassSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun ordinaryGlassElasticity(role: GlassRole, size: Size): Float {
    if (role == GlassRole.Shell) return 0f
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val maxSideDamp = ((720f - maxSide) / 520f).coerceIn(0.24f, 1f)
    val heightDamp = ((360f - h) / 260f).coerceIn(0.50f, 1f)
    val roleGain = when (role) {
        GlassRole.Chip -> 1.00f
        GlassRole.Floating -> 0.94f
        GlassRole.Flex -> 0.82f
        GlassRole.Card -> 0.68f
        GlassRole.Nav -> 0.54f
        GlassRole.Shell -> 0f
    }
    return (maxSideDamp * heightDamp * roleGain).coerceIn(0.16f, 1f)
}

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
    viewportTopInset: Dp = 0.dp,
    intensity: Float? = null,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val baseIntensity = intensity ?: glassIntensity
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val prismEdgeHighlight = LocalRainbowPrismStyle.current.edgeHighlight.coerceIn(0f, 2f)
    val coordinates = remember { GlassCoordinateSource() }
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val density = LocalDensity.current
    val safeViewportTopInset = if (role == GlassRole.Shell && viewportTopInset > 0.dp) viewportTopInset else 0.dp
    val safeViewportTopInsetPx = with(density) { safeViewportTopInset.toPx().toInt().toFloat() }
    val useCardOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && !viewportOwnsShell && roleUsesCardBoundOpenGl(role) && cardBackdrop != null
    val useUnifiedBackdrop = registry != null && roleUsesUnifiedBackdrop(role) && !useCardOpenGlBackdrop && !viewportOwnsShell
    val key = remember { Any() }

    val shellPressEnabled = role == GlassRole.Shell && motionIntensity > 0.02f
    val shellPress = remember { Animatable(0f) }
    val shellOpenGlPressAnim = remember { Animatable(0f) }
    val shellPressScope = rememberCoroutineScope()
    var shellPressSize by remember { mutableStateOf(Size(1f, 1f)) }
    var shellPressCenter by remember { mutableStateOf(Offset(0.50f, 0.42f)) }
    var shellRimFlowSeed by remember { mutableStateOf(0.50f) }
    var shellRimFlowDirection by remember { mutableStateOf(1f) }
    var shellRimFlowBand by remember { mutableStateOf(0) }
    var shellRimFlowStrength by remember { mutableStateOf(1f) }

    val shellPressValue = if (shellPressEnabled) shellPress.value.coerceIn(-0.14f, 1.08f) else 0f
    val shellPressCompression = glassSmoothStep((shellPressValue.coerceAtLeast(0f) / 0.72f).coerceIn(0f, 1f))
    val shellOpenGlPress = if (shellPressEnabled) shellOpenGlPressAnim.value.coerceIn(0f, 1f) else 0f
    val shellPressRebound = glassSmoothStep((-shellPressValue / 0.10f).coerceIn(0f, 1f))
    val shellSurfaceOpticsPress = if (shellPressEnabled) {
        maxOf(shellPressValue.coerceAtLeast(0f), shellOpenGlPress * 0.62f, shellPressRebound * 0.24f)
    } else 0f
    val pressedGlassIntensity = baseIntensity * (1f + shellPressCompression * 0.10f)

    val shellPressModifier = if (shellPressEnabled) {
        Modifier
            .onSizeChanged { size ->
                shellPressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
            }
            .pointerInput(motionIntensity) {
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        shellPressCenter = Offset(
                            x = (position.x / shellPressSize.width).coerceIn(0f, 1f),
                            y = (position.y / shellPressSize.height).coerceIn(0f, 1f)
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePressCenter(down.position)
                    shellRimFlowSeed = Random.nextFloat()
                    shellRimFlowDirection = if (Random.nextBoolean()) 1f else -1f
                    shellRimFlowBand = Random.nextInt(0, 4)
                    shellRimFlowStrength = 0.86f + Random.nextFloat() * 0.52f
                    shellPressScope.launch {
                        shellPress.stop()
                        if (shellPress.value < 0.18f) shellPress.snapTo(0.18f)
                        shellPress.animateTo(0.42f, tween(150, easing = ShellPressPulseEasing))
                        shellPress.animateTo(0.62f, tween(360, easing = ShellPressSinkEasing))
                        shellPress.animateTo(0.76f, tween(620, easing = FastOutSlowInEasing))
                        shellPress.animateTo(0.62f, tween(680, easing = FastOutSlowInEasing))
                        shellPress.animateTo(0.70f, spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessVeryLow))
                    }
                    shellPressScope.launch {
                        shellOpenGlPressAnim.stop()
                        shellOpenGlPressAnim.animateTo(0.26f, tween(230, easing = ShellPressPreloadEasing))
                        shellOpenGlPressAnim.animateTo(0.72f, tween(520, easing = ShellPressSinkEasing))
                        shellOpenGlPressAnim.animateTo(0.88f, tween(620, easing = FastOutSlowInEasing))
                        shellOpenGlPressAnim.animateTo(0.74f, tween(680, easing = FastOutSlowInEasing))
                        shellOpenGlPressAnim.animateTo(0.80f, spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessVeryLow))
                    }
                    var releasedInsideGesture = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updatePressCenter(tracked.position)
                            if (!tracked.pressed) {
                                releasedInsideGesture = true
                                break
                            }
                        }
                        if (event.changes.none { it.pressed }) {
                            releasedInsideGesture = true
                            break
                        }
                    }
                    shellPressScope.launch {
                        shellOpenGlPressAnim.stop()
                        val currentLens = shellOpenGlPressAnim.value.coerceIn(0f, 1f)
                        if (releasedInsideGesture && currentLens < 0.24f) {
                            shellOpenGlPressAnim.animateTo(0.34f, tween(120, easing = ShellPressPulseEasing))
                        }
                        shellOpenGlPressAnim.animateTo(0f, tween(if (releasedInsideGesture) 560 else 380, easing = FastOutSlowInEasing))
                    }
                    shellPressScope.launch {
                        shellPress.stop()
                        if (releasedInsideGesture) {
                            val current = shellPress.value.coerceIn(0f, 1.08f)
                            if (current < 0.46f) {
                                shellPress.animateTo(0.52f, tween(105, easing = ShellPressPulseEasing))
                                shellPress.animateTo(-0.060f, tween(150, easing = ShellPressReleaseEasing))
                            } else {
                                shellPress.animateTo(-0.065f, tween(220, easing = ShellPressReleaseEasing))
                            }
                            shellPress.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow))
                        } else {
                            shellPress.animateTo(0f, tween(430, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
    } else Modifier

    if (useUnifiedBackdrop) {
        SideEffect {
            registry?.upsert(
                GlassRenderItem(
                    key = key,
                    coordinates = coordinates,
                    radius = effectiveRadius,
                    role = role,
                    quality = quality,
                    glassIntensity = pressedGlassIntensity,
                    edgeStrength = UNIFIED_EDGE_STRENGTH,
                    backdropAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedGlassIntensity.coerceIn(0.70f, 1.25f)
                )
            )
        }
    }
    DisposableEffect(registry, key, useUnifiedBackdrop) { onDispose { registry?.remove(key) } }

    Box(
        modifier = modifier
            .then(shellPressModifier)
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                if (shellPressEnabled) {
                    transformOrigin = TransformOrigin(shellPressCenter.x, shellPressCenter.y)
                    scaleX = 1f + shellPressCompression * 0.014f - shellPressRebound * 0.004f
                    scaleY = 1f - shellPressCompression * 0.022f + shellPressRebound * 0.008f
                    translationY = shellPressCompression * 2.10f - shellPressRebound * 0.80f
                    shadowElevation = shellPressCompression * 0.45f
                }
            }
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = pressedGlassIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize(),
                pressProgress = shellOpenGlPress,
                pressCenter = shellPressCenter,
                viewportTopInsetPx = safeViewportTopInsetPx
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = safeViewportTopInset)
                .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedGlassIntensity)
        ) {
            if (!useCardOpenGlBackdrop && !useUnifiedBackdrop && !viewportOwnsShell && backdrop != null) {
                SampledWeatherGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    radius = effectiveRadius,
                    coordinateSource = coordinates,
                    quality = backdrop.quality,
                    motionIntensity = backdrop.motionIntensity,
                    theme = backdrop.theme,
                    blurRadiusDp = blurForRole(role),
                    liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedGlassIntensity.coerceIn(0.70f, 1.25f)
                )
                SampledWeatherEdgeRefraction(
                    modifier = Modifier.matchParentSize(),
                    radius = effectiveRadius,
                    coordinateSource = coordinates,
                    quality = backdrop.quality,
                    motionIntensity = backdrop.motionIntensity,
                    theme = backdrop.theme,
                    strength = UNIFIED_EDGE_STRENGTH
                )
            }
            if (!useCardOpenGlBackdrop) {
                Box(
                    Modifier
                        .matchParentSize()
                        .glassSkin(quality, effectiveRadius, shimmer + shellPressCompression * 0.030f, breathe, pressedGlassIntensity, includeShadow = false)
                )
            }
            content()
            if (shellPressEnabled) {
                Box(
                    Modifier
                        .matchParentSize()
                        .shellPressSurfaceOptics(
                            press = shellSurfaceOpticsPress,
                            radius = effectiveRadius,
                            pressCenter = shellPressCenter,
                            rimFlowSeed = shellRimFlowSeed,
                            rimFlowDirection = shellRimFlowDirection,
                            rimFlowBand = shellRimFlowBand,
                            rimFlowStrength = shellRimFlowStrength,
                            prismEdgeHighlight = prismEdgeHighlight
                        )
                )
            }
        }
    }
}

@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Chip,
    onClick: () -> Unit = {},
    intensity: Float? = null,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val baseIntensity = intensity ?: glassIntensity
    val interaction = remember { MutableInteractionSource() }
    val pressScope = rememberCoroutineScope()
    val ordinaryPress = remember { Animatable(0f) }
    val ordinaryLens = remember { Animatable(0f) }
    val ordinarySweep = remember { Animatable(0f) }
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.50f)) }
    var pressSize by remember { mutableStateOf(Size(1f, 1f)) }
    val motion = motionIntensity.coerceIn(0f, 1f)
    val ordinaryPressEnabled = role != GlassRole.Shell && motion > 0.02f
    val elasticity = if (ordinaryPressEnabled) ordinaryGlassElasticity(role, pressSize) * motion else 0f
    val pressValue = if (ordinaryPressEnabled) ordinaryPress.value.coerceIn(-0.20f, 1.32f) else 0f
    val positivePress = pressValue.coerceAtLeast(0f)
    val rebound = glassSmoothStep((-pressValue / 0.18f).coerceIn(0f, 1f))
    val pressCompression = glassSmoothStep((positivePress / 0.94f).coerceIn(0f, 1f))
    val lensValue = if (ordinaryPressEnabled) ordinaryLens.value.coerceIn(0f, 1.12f) else 0f
    val sweepValue = if (ordinaryPressEnabled) ordinarySweep.value.coerceIn(0f, 1.18f) else 0f
    val opticsPress = maxOf(positivePress, lensValue * 0.86f, rebound * 0.28f)
    val pressedIntensity = baseIntensity * (1f + pressCompression * (0.040f + 0.150f * elasticity))
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val key = remember { Any() }
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val useCardOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && !viewportOwnsShell && roleUsesCardBoundOpenGl(role) && cardBackdrop != null
    val useUnifiedBackdrop = registry != null && roleUsesUnifiedBackdrop(role) && !useCardOpenGlBackdrop && !viewportOwnsShell

    if (useUnifiedBackdrop) {
        SideEffect {
            registry?.upsert(
                GlassRenderItem(
                    key = key,
                    coordinates = coordinates,
                    radius = effectiveRadius,
                    role = role,
                    quality = quality,
                    glassIntensity = pressedIntensity,
                    edgeStrength = UNIFIED_EDGE_STRENGTH,
                    backdropAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f)
                )
            )
        }
    }
    DisposableEffect(registry, key, useUnifiedBackdrop) { onDispose { registry?.remove(key) } }

    Box(
        modifier = modifier
            .onSizeChanged { size -> pressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat()) }
            .pointerInput(ordinaryPressEnabled, motionIntensity, role) {
                if (!ordinaryPressEnabled) return@pointerInput
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        pressCenter = Offset((position.x / pressSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f), (position.y / pressSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f))
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePressCenter(down.position)
                    pressScope.launch {
                        ordinaryPress.stop()
                        if (ordinaryPress.value < 0.22f) ordinaryPress.snapTo(0.22f)
                        ordinaryPress.animateTo(0.92f, tween(132, easing = OrdinaryPressEasing))
                        ordinaryPress.animateTo(1.10f, tween(210, easing = OrdinarySinkEasing))
                        ordinaryPress.animateTo(0.94f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                    }
                    pressScope.launch {
                        ordinaryLens.stop()
                        if (ordinaryLens.value < 0.18f) ordinaryLens.snapTo(0.18f)
                        ordinaryLens.animateTo(0.78f, tween(150, easing = OrdinaryPressEasing))
                        ordinaryLens.animateTo(1.04f, tween(330, easing = FastOutSlowInEasing))
                    }
                    pressScope.launch {
                        ordinarySweep.stop()
                        ordinarySweep.snapTo(0f)
                        ordinarySweep.animateTo(1.18f, tween(520, easing = FastOutSlowInEasing))
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updatePressCenter(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    pressScope.launch {
                        ordinaryPress.stop()
                        ordinaryPress.animateTo(-0.145f, tween(130, easing = OrdinaryReleaseEasing))
                        ordinaryPress.animateTo(0.060f, spring(dampingRatio = 0.50f, stiffness = Spring.StiffnessMediumLow))
                        ordinaryPress.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
                    }
                    pressScope.launch {
                        ordinaryLens.stop()
                        ordinaryLens.animateTo(0.42f, tween(180, easing = OrdinaryReleaseEasing))
                        ordinaryLens.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
                    }
                    pressScope.launch {
                        ordinarySweep.stop()
                        ordinarySweep.animateTo(0.18f, tween(260, easing = FastOutSlowInEasing))
                        ordinarySweep.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                    }
                }
            }
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + pressCompression * (0.006f + 0.049f * elasticity) - rebound * 0.018f * elasticity
                scaleY = 1f - pressCompression * (0.010f + 0.064f * elasticity) + rebound * 0.030f * elasticity
                translationY = pressCompression * (0.70f + 3.90f * elasticity) - rebound * 1.55f * elasticity
                shadowElevation = pressCompression * (0.16f + 0.66f * elasticity)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedIntensity)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(radius = effectiveRadius, glassIntensity = pressedIntensity, coordinateSource = coordinates, modifier = Modifier.matchParentSize())
        } else if (!useUnifiedBackdrop && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(Modifier.matchParentSize(), effectiveRadius, coordinates, backdrop.quality, backdrop.motionIntensity, backdrop.theme, blurForRole(role), UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f))
            SampledWeatherEdgeRefraction(Modifier.matchParentSize(), effectiveRadius, coordinates, backdrop.quality, backdrop.motionIntensity, backdrop.theme, UNIFIED_EDGE_STRENGTH)
        }
        if (!useCardOpenGlBackdrop) {
            Box(Modifier.matchParentSize().glassSkin(quality, effectiveRadius, shimmer + 0.030f * pressCompression, breathe, pressedIntensity, includeShadow = false))
        }
        content()
        if (ordinaryPressEnabled && opticsPress > 0.001f) {
            Box(Modifier.matchParentSize().ordinaryPressSurfaceOptics(opticsPress, sweepValue, effectiveRadius, pressCenter, role, elasticity))
        }
    }
}

private fun Modifier.ordinaryPressSurfaceOptics(
    press: Float,
    sweep: Float,
    radius: Int,
    pressCenter: Offset,
    role: GlassRole,
    elasticity: Float
): Modifier = drawWithContent {
    val e = elasticity.coerceIn(0.08f, 1f)
    val safePress = press.coerceIn(0f, 1.28f)
    if (safePress < 0.001f || role == GlassRole.Shell) {
        drawContent()
        return@drawWithContent
    }
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val centerNorm = Offset(pressCenter.x.coerceIn(0f, 1f), pressCenter.y.coerceIn(0f, 1f))
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
    val p = glassSmoothStep((safePress / 0.92f).coerceIn(0f, 1f)) * e
    val rimFlow = glassSmoothStep(sweep.coerceIn(0f, 1.18f) / 1.18f)
    val chroma = when (role) {
        GlassRole.Chip -> 1.00f
        GlassRole.Floating -> 0.95f
        GlassRole.Flex -> 0.82f
        GlassRole.Card -> 0.66f
        GlassRole.Nav -> 0.58f
        GlassRole.Shell -> 0f
    }
    val optical = (0.10f + p * 1.10f).coerceIn(0f, 1.18f)
    val rainbowAlpha = (0.040f + p * 0.170f).coerceIn(0f, 0.24f) * chroma
    val rimInset = 0.62.dp.toPx()
    val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val sweepX = -0.36f + rimFlow * 1.66f

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.080f * optical), Color(0xFF8DFFF3).copy(alpha = 0.044f * optical * chroma), Color(0xFFFF8FE7).copy(alpha = 0.026f * optical * chroma), Color.Transparent),
            center = center,
            radius = maxSide * (0.48f + 0.28f * p)
        ),
        size = Size(w, h),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFF7AD9).copy(alpha = rainbowAlpha * 0.70f), Color(0xFFFFD166).copy(alpha = rainbowAlpha * 0.52f), Color(0xFF7CFFEA).copy(alpha = rainbowAlpha * 0.80f), Color(0xFF8EA2FF).copy(alpha = rainbowAlpha * 0.66f), Color.Transparent),
            start = Offset(w * (sweepX - 0.46f), h * -0.10f),
            end = Offset(w * (sweepX + 0.58f), h * 1.08f)
        ),
        size = Size(w, h),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF04112A).copy(alpha = 0.018f * optical), Color(0xFF00030A).copy(alpha = 0.072f * p)),
            center = center,
            radius = maxSide * (0.76f + 0.20f * p)
        ),
        size = Size(w, h),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Multiply
    )
    drawContent()
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.160f * p), Color(0xFF9DFFF1).copy(alpha = 0.080f * p * chroma), Color(0xFFFF8FE7).copy(alpha = 0.052f * p * chroma), Color.Transparent),
            center = center,
            radius = maxSide * (0.32f + 0.12f * p)
        ),
        size = Size(w, h),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color(0xFFFF72D2).copy(alpha = 0.32f * p * chroma), Color(0xFFFFF0A8).copy(alpha = 0.30f * p * chroma), Color(0xFF76FFF1).copy(alpha = 0.34f * p * chroma), Color(0xFF9AA8FF).copy(alpha = 0.26f * p * chroma), Color.Transparent),
            start = Offset(w * (sweepX - 0.24f), 0f),
            end = Offset(w * (sweepX + 0.30f), h * 0.98f)
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(0.64.dp.toPx() + 1.16.dp.toPx() * p),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.105f + 0.090f * p), Color(0xFFE9FFFF).copy(alpha = 0.020f + 0.056f * p), Color.Transparent, Color(0xFF000819).copy(alpha = 0.030f + 0.070f * p)),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(0.48.dp.toPx() + 0.72.dp.toPx() * p),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF7FF).copy(alpha = 0.100f * p), Color(0xFFFF8BD9).copy(alpha = 0.038f * p * chroma), Color.Transparent),
            center = Offset(w * 0.14f, h * 0.08f),
            radius = maxSide * 0.32f
        ),
        topLeft = Offset(1.15.dp.toPx(), 1.15.dp.toPx()),
        size = Size(w - 2.30.dp.toPx(), h - 2.30.dp.toPx()),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Screen
    )
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float = if (quality.enableMotion && motionIntensity > 0.02f) 0.20f else 0.16f

@Composable
private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float = if (quality.enableMotion && motionIntensity > 0.02f) 0.38f else 0.34f

private fun Modifier.glassOuterFrame(radius: Int, glassIntensity: Float): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(glassIntensity)
    return this.shadow(elevation = 5.dp, shape = shape, clip = false, ambientColor = Color.Black.copy(alpha = material.shadowAmbient), spotColor = Color.White.copy(alpha = material.shadowSpot)).clip(shape)
}

private fun Modifier.shellPressSurfaceOptics(
    press: Float,
    radius: Int,
    pressCenter: Offset,
    rimFlowSeed: Float,
    rimFlowDirection: Float,
    rimFlowBand: Int,
    rimFlowStrength: Float,
    prismEdgeHighlight: Float
): Modifier = drawWithContent {
    drawContent()
    val safePress = press.coerceIn(0f, 1.08f)
    if (safePress < 0.001f) return@drawWithContent
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val raw = (safePress / 0.72f).coerceIn(0f, 1f)
    val p = glassSmoothStep(raw)
    val breath = glassSmoothStep((safePress / 0.50f).coerceIn(0f, 1f)) * (1f - 0.11f * glassSmoothStep(((safePress - 0.58f) / 0.28f).coerceIn(0f, 1f)))
    val compression = p * p
    val centerNorm = Offset(pressCenter.x.coerceIn(0f, 1f), pressCenter.y.coerceIn(0f, 1f))
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
    val rimInset = 0.56.dp.toPx()
    val rimRadius = (radius.dp.toPx() - rimInset).coerceAtLeast(0f)
    val cornerRadius = CornerRadius(rimRadius, rimRadius)
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val maxSide = maxOf(w, h)
    val pressGlow = p
    fun nearEdge(distance: Float): Float = (1f - distance / 0.42f).coerceIn(0f, 1f) * pressGlow
    val topNear = nearEdge(centerNorm.y)
    val bottomNear = nearEdge(1f - centerNorm.y)
    val leftNear = nearEdge(centerNorm.x)
    val rightNear = nearEdge(1f - centerNorm.x)
    val edgeStroke = (0.74.dp + (0.26f * p).dp).toPx()
    val localEdgeStroke = (1.18.dp + (0.48f * p).dp).toPx()
    val flow = glassSmoothStep((safePress / 0.62f).coerceIn(0f, 1f))
    val seedShift = (rimFlowSeed - 0.5f) * 0.36f
    val sweepX = if (rimFlowDirection >= 0f) -0.24f + seedShift + flow * 1.42f else 1.24f + seedShift - flow * 1.42f
    val bandStartY = when (rimFlowBand % 4) { 0 -> 0.02f; 1 -> 0.74f; 2 -> 0.10f; else -> 0.18f }
    val bandEndY = when (rimFlowBand % 4) { 0 -> 0.26f; 1 -> 0.98f; 2 -> 0.92f; else -> 0.58f }
    val bandAlpha = breath * rimFlowStrength.coerceIn(0.70f, 1.45f)
    val prism = prismEdgeHighlight.coerceIn(0f, 2f)
    val prismSoft = prism * 0.55f

    val pressureField = Brush.radialGradient(listOf(Color(0xFFEFFFFF).copy(alpha = 0.066f * breath), Color(0xFFB8F7FF).copy(alpha = 0.032f * breath), Color(0xFF82E8FF).copy(alpha = 0.010f * breath), Color.Transparent), center, maxSide * (0.86f + 0.06f * p))
    val broadHalo = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.021f * breath), Color(0xFFD8FFFF).copy(alpha = 0.014f * breath), Color.Transparent), Offset(w * 0.50f, h * 0.40f), maxSide * 1.18f)
    val elasticSurfaceField = Brush.radialGradient(listOf(Color.Transparent, Color(0xFF102C66).copy(alpha = 0.006f * p), Color(0xFF030B1A).copy(alpha = 0.034f * compression)), center, maxSide * (1.00f + 0.035f * p))
    val lowerWeight = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF020815).copy(alpha = 0.044f * compression)), h * 0.44f, h)
    val ambientRim = Brush.radialGradient(listOf(Color(0xFFEFFFFF).copy(alpha = 0.052f * breath), Color(0xFF92FFF1).copy(alpha = (0.018f + 0.020f * prismSoft) * breath), Color(0xFFFF8BE8).copy(alpha = 0.014f * prismSoft * breath), Color.Transparent), center, maxSide * 0.74f)
    val flowingRim = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFF6ADB).copy(alpha = 0.20f * prism * bandAlpha), Color.White.copy(alpha = 0.34f * bandAlpha), Color(0xFFFFE08A).copy(alpha = 0.18f * prism * bandAlpha), Color(0xFF62FFF0).copy(alpha = (0.14f + 0.16f * prism) * bandAlpha), Color(0xFF92A6FF).copy(alpha = 0.12f * prism * bandAlpha), Color.Transparent), Offset(w * (sweepX - 0.26f), h * bandStartY), Offset(w * (sweepX + 0.22f), h * bandEndY))
    fun prismHalo(power: Float, white: Float, cyan: Float) = listOf(Color.White.copy(alpha = white * power), Color(0xFFFF7DE2).copy(alpha = 0.050f * prism * power), Color(0xFFFFE28A).copy(alpha = 0.036f * prism * power), Color(0xFF80FFF2).copy(alpha = cyan * power * (0.65f + prism * 0.35f)), Color.Transparent)
    val topEdgeHalo = Brush.radialGradient(prismHalo(topNear, 0.23f, 0.072f), Offset(center.x, rimInset), maxSide * 0.38f)
    val bottomEdgeHalo = Brush.radialGradient(prismHalo(bottomNear, 0.16f, 0.054f), Offset(center.x, h - rimInset), maxSide * 0.36f)
    val leftEdgeHalo = Brush.radialGradient(prismHalo(leftNear, 0.18f, 0.060f), Offset(rimInset, center.y), maxSide * 0.34f)
    val rightEdgeHalo = Brush.radialGradient(prismHalo(rightNear, 0.18f, 0.060f), Offset(w - rimInset, center.y), maxSide * 0.34f)

    drawRect(broadHalo, blendMode = BlendMode.Screen)
    drawRect(pressureField, blendMode = BlendMode.Screen)
    drawRect(elasticSurfaceField, blendMode = BlendMode.Multiply)
    drawRect(lowerWeight, blendMode = BlendMode.Multiply)
    drawRoundRect(brush = ambientRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(edgeStroke), blendMode = BlendMode.Screen)
    drawRoundRect(brush = flowingRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(0.82.dp.toPx() + 0.20.dp.toPx() * prism), blendMode = BlendMode.Plus)
    drawRoundRect(brush = topEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
    drawRoundRect(brush = bottomEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
    drawRoundRect(brush = leftEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
    drawRoundRect(brush = rightEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
}

fun Modifier.glassSkin(
    quality: RenderQuality,
    radius: Int,
    shimmer: Float = 0f,
    breathe: Float = 0.42f,
    glassIntensity: Float = 1f,
    role: GlassRole = GlassRole.Card,
    includeShadow: Boolean = true
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(glassIntensity)
    val pulse = 0.92f + breathe * 0.045f
    val safeShimmer = shimmer - shimmer.toInt()
    val base = if (includeShadow) this.glassOuterFrame(radius, glassIntensity) else this.clip(shape)
    return base.drawWithCache {
        val w = size.width
        val h = size.height
        val drift = safeShimmer - 0.5f
        val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val rimInset = 0.62.dp.toPx()
        val innerInset = 1.85.dp.toPx()
        val bottomInset = 2.10.dp.toPx()
        val rimSize = Size(w - rimInset * 2f, h - rimInset * 2f)
        val innerSize = Size(w - innerInset * 2f, h - innerInset * 2f)
        val bottomSize = Size(w - bottomInset * 2f, h - bottomInset * 2f)
        val frostedNeutralVeil = Brush.verticalGradient(listOf(Color.White.copy(alpha = material.frost * 0.28f * pulse), Color.White.copy(alpha = material.frost * 0.10f), Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.11f)), 0f, h)
        val topLens = Brush.verticalGradient(listOf(Color.White.copy(alpha = material.topHighlight * 0.48f * pulse), Color.White.copy(alpha = material.topHighlight * 0.055f), Color.Transparent), 0f, h * 0.22f)
        val lowerShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.52f)), h * 0.64f, h)
        val mainRim = Brush.linearGradient(listOf(Color.White.copy(alpha = material.rim * 0.58f), Color.White.copy(alpha = material.rim * 0.048f), Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.14f), Color.White.copy(alpha = material.rim * 0.016f)), Offset.Zero, Offset(w, h))
        val topHairline = Brush.verticalGradient(listOf(Color.White.copy(alpha = material.rim * 0.42f), Color.White.copy(alpha = material.rim * 0.030f), Color.Transparent), 0f, h * 0.18f)
        val innerSoftRim = Brush.linearGradient(listOf(Color.White.copy(alpha = material.rim * 0.022f), Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.10f), Color.White.copy(alpha = material.rim * 0.008f)), Offset(w * 0.10f, 0f), Offset(w * 0.94f, h))
        val bottomShadow = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = material.depthShadow * 0.34f)), h * 0.58f, h)
        val movingEdgeGlint = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = material.motionGlint), Color.Transparent), Offset(w * (safeShimmer - 0.28f), 0f), Offset(w * (safeShimmer + 0.16f), h * 0.20f))
        val cornerCatchlight = Brush.radialGradient(listOf(Color.White.copy(alpha = material.cornerHighlight), Color.White.copy(alpha = material.cornerHighlight * 0.08f), Color.Transparent), Offset(w * (0.035f + drift * 0.010f), h * 0.020f), w * 0.26f)
        onDrawWithContent {
            drawRect(frostedNeutralVeil, blendMode = BlendMode.Screen)
            drawRect(topLens, blendMode = BlendMode.Screen)
            drawRect(lowerShade, blendMode = BlendMode.Multiply)
            drawContent()
            drawRoundRect(brush = mainRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(0.32.dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(0.10.dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(brush = innerSoftRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(0.10.dp.toPx()), blendMode = BlendMode.SrcOver)
            drawRoundRect(brush = bottomShadow, topLeft = Offset(bottomInset, bottomInset), size = bottomSize, cornerRadius = cornerRadius, style = Stroke(0.10.dp.toPx()), blendMode = BlendMode.Multiply)
            if (quality.enableMotion) {
                drawRoundRect(brush = movingEdgeGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(0.07.dp.toPx()), blendMode = BlendMode.Plus)
            }
            drawRoundRect(brush = cornerCatchlight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(0.13.dp.toPx()), blendMode = BlendMode.Screen)
        }
    }
}

private data class GlassMaterial(
    val frost: Float,
    val rim: Float,
    val topHighlight: Float,
    val cornerHighlight: Float,
    val motionGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float
)

private fun glassMaterial(intensity: Float): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val base = GlassMaterial(0.044f, 0.104f, 0.036f, 0.021f, 0.0035f, 0.015f, 0.028f, 0.0035f)
    return GlassMaterial(
        frost = (base.frost * safeIntensity).coerceIn(0.006f, 0.052f),
        rim = (base.rim * safeIntensity).coerceIn(0.018f, 0.154f),
        topHighlight = (base.topHighlight * safeIntensity).coerceIn(0.002f, 0.048f),
        cornerHighlight = (base.cornerHighlight * safeIntensity).coerceIn(0.002f, 0.038f),
        motionGlint = (base.motionGlint * safeIntensity).coerceIn(0.001f, 0.008f),
        depthShadow = (base.depthShadow * safeIntensity).coerceIn(0.002f, 0.027f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.004f, 0.046f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.001f, 0.008f)
    )
}
