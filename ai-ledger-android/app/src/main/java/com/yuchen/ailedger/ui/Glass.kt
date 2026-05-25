package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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

/**
 * When a page-level OpenGL viewport is active, Shell glass is painted by that single
 * viewport layer instead of creating one TextureView per Shell card. Ordinary roles
 * stay fully Compose based and remain isolated from OpenGL.
 */
val LocalOpenGLGlassViewportActive = compositionLocalOf { false }

private const val STRONG_GLASS_BLUR_DP = 118
private const val MEDIUM_GLASS_BLUR_DP = 82
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 0.96f
private const val UNIFIED_EDGE_STRENGTH = 0.22f
private val ShellPressPreloadEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val ShellPressSinkEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val ShellPressReleaseEasing = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val ShellPressPulseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)

/**
 * Root OpenGL gate.
 *
 * Keep this enabled so deliberately promoted Shell glass can use the card-bound
 * TextureView/OpenGL path. Ordinary glass is still protected by roleUsesCardBoundOpenGl(),
 * so Card, Chip, Floating, Nav and Flex cannot enter OpenGL even when this gate is on.
 */
private const val USE_CARD_BOUND_OPENGL_GLASS = true

private fun blurForRole(role: GlassRole): Int = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Floating -> STRONG_GLASS_BLUR_DP
    GlassRole.Nav -> 104
    GlassRole.Chip, GlassRole.Flex -> MEDIUM_GLASS_BLUR_DP
}

private fun roleUsesUnifiedBackdrop(role: GlassRole): Boolean = when (role) {
    GlassRole.Shell, GlassRole.Card, GlassRole.Nav -> true
    GlassRole.Chip, GlassRole.Floating, GlassRole.Flex -> false
}

/**
 * OpenGL is intentionally fenced at the root role policy.
 *
 * The rolled-back app uses GlassRole.Card for many ordinary surfaces: message bubbles,
 * text inputs, list rows, hint cards and compact tool entries. Letting every Card enter
 * OpenGL makes the boundary depend on naming accidents rather than architecture.
 *
 * For this branch, only Shell is allowed to create a card-bound OpenGL layer. Ordinary
 * cards keep the Compose/unified backdrop path. If a future screen needs a truly large
 * OpenGL card, promote that container deliberately instead of reusing Card for small UI.
 */
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

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float = 1f,
    motionIntensity: Float = 1f,
    radius: Int,
    modifier: Modifier = Modifier,
    role: GlassRole = GlassRole.Card,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
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
    val pressedGlassIntensity = glassIntensity * (1f + shellPressCompression * 0.10f)
    val shellPressModifier = if (shellPressEnabled) {
        Modifier
            .onSizeChanged { size ->
                shellPressSize = Size(
                    width = size.width.coerceAtLeast(1).toFloat(),
                    height = size.height.coerceAtLeast(1).toFloat()
                )
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
                        shellPress.animateTo(
                            targetValue = 0.42f,
                            animationSpec = tween(durationMillis = 150, easing = ShellPressPulseEasing)
                        )
                        shellPress.animateTo(
                            targetValue = 0.62f,
                            animationSpec = tween(durationMillis = 360, easing = ShellPressSinkEasing)
                        )
                        shellPress.animateTo(
                            targetValue = 0.76f,
                            animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
                        )
                        shellPress.animateTo(
                            targetValue = 0.62f,
                            animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
                        )
                        shellPress.animateTo(
                            targetValue = 0.70f,
                            animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessVeryLow)
                        )
                    }
                    shellPressScope.launch {
                        shellOpenGlPressAnim.stop()
                        shellOpenGlPressAnim.animateTo(
                            targetValue = 0.26f,
                            animationSpec = tween(durationMillis = 230, easing = ShellPressPreloadEasing)
                        )
                        shellOpenGlPressAnim.animateTo(
                            targetValue = 0.72f,
                            animationSpec = tween(durationMillis = 520, easing = ShellPressSinkEasing)
                        )
                        shellOpenGlPressAnim.animateTo(
                            targetValue = 0.88f,
                            animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
                        )
                        shellOpenGlPressAnim.animateTo(
                            targetValue = 0.74f,
                            animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
                        )
                        shellOpenGlPressAnim.animateTo(
                            targetValue = 0.80f,
                            animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessVeryLow)
                        )
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
                            shellOpenGlPressAnim.animateTo(
                                targetValue = 0.34f,
                                animationSpec = tween(durationMillis = 120, easing = ShellPressPulseEasing)
                            )
                        }
                        shellOpenGlPressAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = if (releasedInsideGesture) 560 else 380,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    shellPressScope.launch {
                        shellPress.stop()
                        if (releasedInsideGesture) {
                            val current = shellPress.value.coerceIn(0f, 1.08f)
                            if (current < 0.46f) {
                                shellPress.animateTo(
                                    targetValue = 0.52f,
                                    animationSpec = tween(durationMillis = 105, easing = ShellPressPulseEasing)
                                )
                                shellPress.animateTo(
                                    targetValue = -0.060f,
                                    animationSpec = tween(durationMillis = 150, easing = ShellPressReleaseEasing)
                                )
                            } else {
                                shellPress.animateTo(
                                    targetValue = -0.065f,
                                    animationSpec = tween(durationMillis = 220, easing = ShellPressReleaseEasing)
                                )
                            }
                            shellPress.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow)
                            )
                        } else {
                            shellPress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 430, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            }
    } else {
        Modifier
    }

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
    DisposableEffect(registry, key, useUnifiedBackdrop) {
        onDispose { registry?.remove(key) }
    }

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
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedGlassIntensity)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = glassIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize(),
                pressProgress = shellOpenGlPress,
                pressCenter = shellPressCenter
            )
        } else if (!useUnifiedBackdrop && !viewportOwnsShell && backdrop != null) {
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
                modifier = Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality = quality,
                        radius = effectiveRadius,
                        shimmer = shimmer + shellPressCompression * 0.030f,
                        breathe = breathe,
                        glassIntensity = pressedGlassIntensity,
                        includeShadow = false
                    )
            )
        }
        content()
        if (shellPressEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shellPressSurfaceOptics(
                        press = shellPressValue,
                        radius = effectiveRadius,
                        pressCenter = shellPressCenter,
                        rimFlowSeed = shellRimFlowSeed,
                        rimFlowDirection = shellRimFlowDirection,
                        rimFlowBand = shellRimFlowBand,
                        rimFlowStrength = shellRimFlowStrength
                    )
            )
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
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 0.28f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "glass-press-lift"
    )
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val registry = LocalGlassItemRegistry.current
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val key = remember { Any() }
    val pressedIntensity = if (pressed) glassIntensity * 1.06f else glassIntensity
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
    DisposableEffect(registry, key, useUnifiedBackdrop) {
        onDispose { registry?.remove(key) }
    }

    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift
                shadowElevation = if (pressed) 0.18f else 0f
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .glassOuterFrame(radius = effectiveRadius, glassIntensity = pressedIntensity)
    ) {
        if (useCardOpenGlBackdrop) {
            OpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = pressedIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize()
            )
        } else if (!useUnifiedBackdrop && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(role),
                liftAlpha = UNIFIED_GLASS_BACKDROP_ALPHA * pressedIntensity.coerceIn(0.70f, 1.25f)
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
                modifier = Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality = quality,
                        radius = effectiveRadius,
                        shimmer = shimmer + if (pressed) 0.024f else 0f,
                        breathe = breathe,
                        glassIntensity = pressedIntensity,
                        includeShadow = false
                    )
            )
        }
        content()
    }
}

@Composable
private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float {
    return if (quality.enableMotion && motionIntensity > 0.02f) 0.20f else 0.16f
}

@Composable
private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float {
    return if (quality.enableMotion && motionIntensity > 0.02f) 0.38f else 0.34f
}

private fun Modifier.glassOuterFrame(radius: Int, glassIntensity: Float): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(glassIntensity)
    return this
        .shadow(
            elevation = 5.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = material.shadowAmbient),
            spotColor = Color.White.copy(alpha = material.shadowSpot)
        )
        .clip(shape)
}

private fun Modifier.shellPressSurfaceOptics(
    press: Float,
    radius: Int,
    pressCenter: Offset,
    rimFlowSeed: Float,
    rimFlowDirection: Float,
    rimFlowBand: Int,
    rimFlowStrength: Float
): Modifier {
    return this.drawWithContent {
        drawContent()
        val safePress = press.coerceIn(0f, 1.08f)
        if (safePress < 0.001f) return@drawWithContent

        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val raw = (safePress / 0.72f).coerceIn(0f, 1f)
        val p = glassSmoothStep(raw)
        val breath = glassSmoothStep((safePress / 0.50f).coerceIn(0f, 1f)) *
            (1f - 0.11f * glassSmoothStep(((safePress - 0.58f) / 0.28f).coerceIn(0f, 1f)))
        val compression = p * p
        val centerNorm = Offset(
            x = pressCenter.x.coerceIn(0f, 1f),
            y = pressCenter.y.coerceIn(0f, 1f)
        )
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
        val sweepX = if (rimFlowDirection >= 0f) {
            -0.24f + seedShift + flow * 1.42f
        } else {
            1.24f + seedShift - flow * 1.42f
        }
        val bandStartY = when (rimFlowBand % 4) {
            0 -> 0.02f
            1 -> 0.74f
            2 -> 0.10f
            else -> 0.18f
        }
        val bandEndY = when (rimFlowBand % 4) {
            0 -> 0.26f
            1 -> 0.98f
            2 -> 0.92f
            else -> 0.58f
        }
        val bandAlpha = breath * rimFlowStrength.coerceIn(0.70f, 1.45f)

        val pressureField = Brush.radialGradient(
            colors = listOf(
                Color(0xFFEFFFFF).copy(alpha = 0.066f * breath),
                Color(0xFFB8F7FF).copy(alpha = 0.032f * breath),
                Color(0xFF82E8FF).copy(alpha = 0.010f * breath),
                Color.Transparent
            ),
            center = center,
            radius = maxSide * (0.86f + 0.06f * p)
        )
        val broadHalo = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.021f * breath),
                Color(0xFFD8FFFF).copy(alpha = 0.014f * breath),
                Color.Transparent
            ),
            center = Offset(w * 0.50f, h * 0.40f),
            radius = maxSide * 1.18f
        )
        val elasticSurfaceField = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF102C66).copy(alpha = 0.006f * p),
                Color(0xFF030B1A).copy(alpha = 0.034f * compression)
            ),
            center = center,
            radius = maxSide * (1.00f + 0.035f * p)
        )
        val topSoftLoad = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.037f * breath),
                Color(0xFFE2FFFF).copy(alpha = 0.014f * breath),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.38f
        )
        val lowerWeight = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFF020815).copy(alpha = 0.044f * compression)
            ),
            startY = h * 0.44f,
            endY = h
        )
        val ambientRim = Brush.radialGradient(
            colors = listOf(
                Color(0xFFEFFFFF).copy(alpha = 0.056f * breath),
                Color(0xFFA8F6FF).copy(alpha = 0.022f * breath),
                Color.Transparent
            ),
            center = center,
            radius = maxSide * 0.74f
        )
        val flowingRim = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.42f * bandAlpha),
                Color(0xFF9AF7FF).copy(alpha = 0.20f * bandAlpha),
                Color.Transparent
            ),
            start = Offset(w * (sweepX - 0.20f), h * bandStartY),
            end = Offset(w * (sweepX + 0.18f), h * bandEndY)
        )
        val topEdgeHalo = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.25f * topNear),
                Color(0xFFA7F7FF).copy(alpha = 0.092f * topNear),
                Color.Transparent
            ),
            center = Offset(center.x, rimInset),
            radius = maxSide * 0.38f
        )
        val bottomEdgeHalo = Brush.radialGradient(
            colors = listOf(
                Color(0xFFF2FFFF).copy(alpha = 0.18f * bottomNear),
                Color(0xFF88EFFF).copy(alpha = 0.062f * bottomNear),
                Color.Transparent
            ),
            center = Offset(center.x, h - rimInset),
            radius = maxSide * 0.36f
        )
        val leftEdgeHalo = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f * leftNear),
                Color(0xFF94F2FF).copy(alpha = 0.070f * leftNear),
                Color.Transparent
            ),
            center = Offset(rimInset, center.y),
            radius = maxSide * 0.34f
        )
        val rightEdgeHalo = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f * rightNear),
                Color(0xFF94F2FF).copy(alpha = 0.070f * rightNear),
                Color.Transparent
            ),
            center = Offset(w - rimInset, center.y),
            radius = maxSide * 0.34f
        )

        drawRect(brush = broadHalo, blendMode = BlendMode.Screen)
        drawRect(brush = pressureField, blendMode = BlendMode.Screen)
        drawRect(brush = elasticSurfaceField, blendMode = BlendMode.Multiply)
        drawRect(brush = topSoftLoad, blendMode = BlendMode.Screen)
        drawRect(brush = lowerWeight, blendMode = BlendMode.Multiply)
        drawRoundRect(
            brush = ambientRim,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = edgeStroke),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = flowingRim,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 0.82.dp.toPx()),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = topEdgeHalo,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = localEdgeStroke),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = bottomEdgeHalo,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = localEdgeStroke),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = leftEdgeHalo,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = localEdgeStroke),
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = rightEdgeHalo,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = localEdgeStroke),
            blendMode = BlendMode.Screen
        )
    }
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
    val base = if (includeShadow) this.glassOuterFrame(radius = radius, glassIntensity = glassIntensity) else this.clip(shape)

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

        val frostedNeutralVeil = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.frost * 0.28f * pulse),
                Color.White.copy(alpha = material.frost * 0.10f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.11f)
            ),
            startY = 0f,
            endY = h
        )
        val topLens = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.topHighlight * 0.48f * pulse),
                Color.White.copy(alpha = material.topHighlight * 0.055f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.22f
        )
        val lowerShade = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.52f)
            ),
            startY = h * 0.64f,
            endY = h
        )
        val mainRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.58f),
                Color.White.copy(alpha = material.rim * 0.048f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.14f),
                Color.White.copy(alpha = material.rim * 0.016f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val topHairline = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.42f),
                Color.White.copy(alpha = material.rim * 0.030f),
                Color.Transparent
            ),
            startY = 0f,
            endY = h * 0.18f
        )
        val innerSoftRim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = material.rim * 0.022f),
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.10f),
                Color.White.copy(alpha = material.rim * 0.008f)
            ),
            start = Offset(w * 0.10f, 0f),
            end = Offset(w * 0.94f, h)
        )
        val bottomShadow = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.Black.copy(alpha = material.depthShadow * 0.34f)
            ),
            startY = h * 0.58f,
            endY = h
        )
        val movingEdgeGlint = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = material.motionGlint),
                Color.Transparent
            ),
            start = Offset(w * (safeShimmer - 0.28f), 0f),
            end = Offset(w * (safeShimmer + 0.16f), h * 0.20f)
        )
        val cornerCatchlight = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = material.cornerHighlight),
                Color.White.copy(alpha = material.cornerHighlight * 0.08f),
                Color.Transparent
            ),
            center = Offset(w * (0.035f + drift * 0.010f), h * 0.020f),
            radius = w * 0.26f
        )

        onDrawWithContent {
            drawRect(frostedNeutralVeil, blendMode = BlendMode.Screen)
            drawRect(topLens, blendMode = BlendMode.Screen)
            drawRect(lowerShade, blendMode = BlendMode.Multiply)
            drawContent()
            drawRoundRect(brush = mainRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.32.dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.Screen)
            drawRoundRect(brush = innerSoftRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.SrcOver)
            drawRoundRect(brush = bottomShadow, topLeft = Offset(bottomInset, bottomInset), size = bottomSize, cornerRadius = cornerRadius, style = Stroke(width = 0.10.dp.toPx()), blendMode = BlendMode.Multiply)
            if (quality.enableMotion) {
                drawRoundRect(brush = movingEdgeGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.07.dp.toPx()), blendMode = BlendMode.Plus)
            }
            drawRoundRect(brush = cornerCatchlight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = cornerRadius, style = Stroke(width = 0.13.dp.toPx()), blendMode = BlendMode.Screen)
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
    val base = GlassMaterial(
        frost = 0.044f,
        rim = 0.104f,
        topHighlight = 0.036f,
        cornerHighlight = 0.021f,
        motionGlint = 0.0035f,
        depthShadow = 0.015f,
        shadowAmbient = 0.028f,
        shadowSpot = 0.0035f
    )
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
