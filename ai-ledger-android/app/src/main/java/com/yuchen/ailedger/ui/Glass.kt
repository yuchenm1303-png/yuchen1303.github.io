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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.NewOpenGLGlassCardLayer
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

enum class GlassRole {
    Shell,
    Card,
    Chip,
    Nav,
    Floating,
    Flex
}

val LocalOpenGLGlassViewportActive = compositionLocalOf { false }

private const val SHELL_GLASS_BLUR_DP = 118
private const val CARD_GLASS_BLUR_DP = 76
private const val CHIP_GLASS_BLUR_DP = 58
private const val UNIFIED_GLASS_BACKDROP_ALPHA = 0.92f
private const val UNIFIED_EDGE_STRENGTH = 0.20f
private const val USE_CARD_BOUND_OPENGL_GLASS = true

private val ShellPressPreloadEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val ShellPressSinkEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val ShellPressReleaseEasing = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val ShellPressPulseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)
private val OrdinaryPressEasing = CubicBezierEasing(0.12f, 0.00f, 0.08f, 1.00f)
private val OrdinarySinkEasing = CubicBezierEasing(0.10f, 0.00f, 0.08f, 1.00f)
private val OrdinaryReleaseEasing = CubicBezierEasing(0.14f, 0.00f, 0.12f, 1.00f)

private fun blurForRole(role: GlassRole): Int {
    val base = when (role) {
        GlassRole.Shell -> SHELL_GLASS_BLUR_DP
        GlassRole.Card, GlassRole.Floating, GlassRole.Nav -> CARD_GLASS_BLUR_DP
        GlassRole.Chip, GlassRole.Flex -> CHIP_GLASS_BLUR_DP
    }
    if (role == GlassRole.Shell) return base
    return (base * ComposeGlassLabState.style.blurScale).roundToInt().coerceIn(32, 128)
}

private fun ordinaryBackdropAlpha(role: GlassRole, intensity: Float): Float {
    val roleScale = if (role == GlassRole.Shell) UNIFIED_GLASS_BACKDROP_ALPHA else ComposeGlassLabState.style.backdropAlpha
    return roleScale * intensity.coerceIn(0.70f, 1.25f)
}

private fun effectiveGlassRadius(radius: Int, role: GlassRole): Int {
    if (radius >= 999) return radius
    val base = when (role) {
        GlassRole.Shell -> radius.coerceAtLeast(30)
        GlassRole.Card -> radius.coerceAtLeast(28)
        GlassRole.Floating -> radius.coerceAtLeast(26)
        GlassRole.Nav -> radius.coerceAtLeast(999)
        GlassRole.Chip, GlassRole.Flex -> radius
    }
    if (role == GlassRole.Shell) return base
    return (base * ComposeGlassLabState.style.radiusScale).roundToInt().coerceAtLeast(8)
}

private fun glassSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun composeMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun composeMotionSmoothStep(value: Float): Float {
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
    if (role != GlassRole.Shell) {
        OrdinaryGlassPanel(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            intensity = intensity,
            content = content
        )
        return
    }

    val effectiveRadius = effectiveGlassRadius(radius, GlassRole.Shell)
    val baseIntensity = intensity ?: glassIntensity
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val prismEdgeHighlight = LocalRainbowPrismStyle.current.edgeHighlight.coerceIn(0f, 2f)
    val coordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalGlassBackdrop.current
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current
    val density = LocalDensity.current
    val safeViewportTopInset = if (viewportTopInset > 0.dp) viewportTopInset else 0.dp
    val safeViewportTopInsetPx = with(density) { safeViewportTopInset.toPx().toInt().toFloat() }
    val useNewOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && !viewportOwnsShell && cardBackdrop != null

    val shellPressEnabled = motionIntensity > 0.02f
    val shellPress = remember { Animatable(0f) }
    val shellOpenGlPressAnim = remember { Animatable(0f) }
    val shellDynamicState = remember { OpenGLGlassDynamicState() }
    val shellPressScope = rememberCoroutineScope()
    var shellPressSize by remember { mutableStateOf(Size(1f, 1f)) }

    LaunchedEffect(shellPressEnabled, shellPress, shellOpenGlPressAnim, shellDynamicState) {
        if (!shellPressEnabled) {
            shellDynamicState.reset()
            return@LaunchedEffect
        }
        snapshotFlow { shellPress.value to shellOpenGlPressAnim.value }
            .collect { (pressValue, openGlPress) ->
                shellDynamicState.updateAnimation(pressValue, openGlPress)
            }
    }

    val shellPressModifier = if (shellPressEnabled) {
        Modifier
            .onSizeChanged { size ->
                shellPressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
            }
            .pointerInput(motionIntensity, shellDynamicState) {
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        shellDynamicState.updatePressCenter(
                            Offset(
                                x = (position.x / shellPressSize.width).coerceIn(0f, 1f),
                                y = (position.y / shellPressSize.height).coerceIn(0f, 1f)
                            )
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePressCenter(down.position)
                    shellDynamicState.updateRimFlow(
                        seed = Random.nextFloat(),
                        direction = if (Random.nextBoolean()) 1f else -1f,
                        band = Random.nextInt(0, 4),
                        strength = 0.86f + Random.nextFloat() * 0.52f
                    )
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

    val shellTransformModifier = if (shellPressEnabled) {
        Modifier.graphicsLayer {
            val dynamic = shellDynamicState.snapshotState.value
            transformOrigin = TransformOrigin(dynamic.pressCenter.x, dynamic.pressCenter.y)
            scaleX = 1f + dynamic.pressCompression * 0.014f - dynamic.pressRebound * 0.004f
            scaleY = 1f - dynamic.pressCompression * 0.022f + dynamic.pressRebound * 0.008f
            translationY = dynamic.pressCompression * 2.10f - dynamic.pressRebound * 0.80f
            shadowElevation = dynamic.pressCompression * 0.45f
        }
    } else Modifier

    Box(
        modifier = modifier
            .then(shellPressModifier)
            .onPlaced { coordinates.coordinates = it }
            .then(shellTransformModifier)
    ) {
        if (useNewOpenGlBackdrop) {
            NewOpenGLGlassCardLayer(
                radius = effectiveRadius,
                glassIntensity = baseIntensity,
                coordinateSource = coordinates,
                modifier = Modifier.matchParentSize(),
                viewportTopInsetPx = safeViewportTopInsetPx,
                dynamicState = shellDynamicState
            )
        }

        ShellFramedContent(
            quality = quality,
            baseIntensity = baseIntensity,
            shimmer = shimmer,
            breathe = breathe,
            effectiveRadius = effectiveRadius,
            safeViewportTopInset = safeViewportTopInset,
            useNewOpenGlBackdrop = useNewOpenGlBackdrop,
            viewportOwnsShell = viewportOwnsShell,
            backdrop = backdrop,
            coordinates = coordinates,
            dynamicState = shellDynamicState,
            shellPressEnabled = shellPressEnabled,
            prismEdgeHighlight = prismEdgeHighlight,
            content = content
        )
    }
}

@Composable
private fun ShellFramedContent(
    quality: RenderQuality,
    baseIntensity: Float,
    shimmer: Float,
    breathe: Float,
    effectiveRadius: Int,
    safeViewportTopInset: Dp,
    useNewOpenGlBackdrop: Boolean,
    viewportOwnsShell: Boolean,
    backdrop: GlassBackdropSpec?,
    coordinates: GlassCoordinateSource,
    dynamicState: OpenGLGlassDynamicState,
    shellPressEnabled: Boolean,
    prismEdgeHighlight: Float,
    content: @Composable () -> Unit
) {
    val dynamic = dynamicState.snapshotState.value
    val pressedGlassIntensity = baseIntensity * dynamic.glassIntensityScale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = safeViewportTopInset)
            .ordinaryGlassFrame(
                radius = effectiveRadius,
                glassIntensity = pressedGlassIntensity,
                role = GlassRole.Shell,
                parentOwnsOrdinaryGlass = false
            )
    ) {
        if (!useNewOpenGlBackdrop && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                radius = effectiveRadius,
                coordinateSource = coordinates,
                quality = backdrop.quality,
                motionIntensity = backdrop.motionIntensity,
                theme = backdrop.theme,
                blurRadiusDp = blurForRole(GlassRole.Shell),
                liftAlpha = ordinaryBackdropAlpha(GlassRole.Shell, pressedGlassIntensity)
            )
        }
        if (!useNewOpenGlBackdrop) {
            Box(
                Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality,
                        effectiveRadius,
                        shimmer + dynamic.pressCompression * 0.030f,
                        breathe,
                        pressedGlassIntensity,
                        role = GlassRole.Shell,
                        includeShadow = false
                    )
            )
        }
        ShellStaticContent(content)
        if (shellPressEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .shellPressSurfaceOptics(
                        dynamicState = dynamicState,
                        radius = effectiveRadius,
                        prismEdgeHighlight = prismEdgeHighlight
                    )
            )
        }
    }
}

@Composable
private fun ShellStaticContent(content: @Composable () -> Unit) {
    content()
}

@Composable
private fun OrdinaryGlassPanel(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    role: GlassRole,
    intensity: Float?,
    content: @Composable () -> Unit
) {
    val effectiveRadius = effectiveGlassRadius(radius, role)
    val resolvedIntensity = intensity ?: glassIntensity
    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val backdrop = LocalGlassBackdrop.current
    val parentOwnsOrdinaryGlass =
        LocalOrdinaryGlassRenderMode.current == OrdinaryGlassRenderMode.ParentDraw

    ReportOrdinaryGlassNode(
        coordinates = coordinates,
        role = role,
        quality = quality,
        radius = effectiveRadius,
        glassIntensity = resolvedIntensity,
        backdropAlpha = ordinaryBackdropAlpha(role, resolvedIntensity),
        edgeStrength = UNIFIED_EDGE_STRENGTH,
        pressable = false,
        shimmer = shimmer,
        breathe = breathe,
        pressProgress = 0f,
        lensProgress = 0f,
        sweepProgress = 0f,
        elasticity = 0f,
        pressCenter = Offset(0.5f, 0.5f)
    )

    Box(
        modifier = modifier
            .onPlaced { coordinates.coordinates = it }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .ordinaryGlassFrame(
                    radius = effectiveRadius,
                    glassIntensity = resolvedIntensity,
                    role = role,
                    parentOwnsOrdinaryGlass = parentOwnsOrdinaryGlass
                )
        ) {
            if (!parentOwnsOrdinaryGlass && backdrop != null) {
                SampledWeatherGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    radius = effectiveRadius,
                    coordinateSource = coordinates,
                    quality = backdrop.quality,
                    motionIntensity = backdrop.motionIntensity,
                    theme = backdrop.theme,
                    blurRadiusDp = blurForRole(role),
                    liftAlpha = ordinaryBackdropAlpha(role, resolvedIntensity)
                )
            }
            if (!parentOwnsOrdinaryGlass) {
                Box(
                    Modifier
                        .matchParentSize()
                        .glassSkin(
                            quality,
                            effectiveRadius,
                            shimmer,
                            breathe,
                            resolvedIntensity,
                            role = role,
                            includeShadow = false
                        )
                )
            }
            content()
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
    if (role == GlassRole.Shell) {
        LegacyPressableGlass(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            onClick = onClick,
            intensity = intensity,
            content = content
        )
        return
    }

    val effectiveRadius = effectiveGlassRadius(radius, role)
    val baseIntensity = intensity ?: glassIntensity
    val interaction = remember { MutableInteractionSource() }

    /*
     * 普通 Compose 玻璃按钮跟手版源头动效。
     *
     * 这里只有 ordinaryMaterial 一条主材料曲线：
     * - 按下：current -> pressedTarget，只跑一段自然展开。
     * - 松手：current -> 0，只跑一段自然回落。
     *
     * pressProgress / lensProgress / sweepProgress 仍然对外保留，方便父级绘制链兼容；
     * 几何只由同一条正向材料曲线连续派生，极短点击的可见反馈由独立光效闪烁承接，
     * 不再通过 contact / hold / impulse / release 多段目标制造“先停一下再继续跑”的手感。
     */
    val pressScope = rememberCoroutineScope()
    val ordinaryMaterial = remember { Animatable(0f) }
    val ordinaryTapFlash = remember { Animatable(0f) }
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.50f)) }
    var pressSize by remember { mutableStateOf(Size(1f, 1f)) }

    val backdrop = LocalGlassBackdrop.current
    val requestedMotion = motionIntensity.coerceIn(0f, 1f)
    val settingsMotion = if (
        requestedMotion <= 0.02f &&
        LocalSettingsFrostParentLayer.current != null
    ) {
        backdrop?.motionIntensity?.coerceIn(0f, 1f)
    } else {
        null
    }
    val motionGate = settingsMotion ?: requestedMotion
    val motion = ComposeGlassLabState.motionStyle.normalized()
    val master = composeMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f) *
        motionGate.coerceIn(0f, 1f)
    val ordinaryPressEnabled = role != GlassRole.Shell && master > 0.001f

    val deformation = composeMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master
    val touchLight = composeMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val sweep = composeMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val reboundControl = composeMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master
    val afterglow = composeMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
    val tapImpulse = composeMotionPower(value = motion.tapImpulse, uiMax = 1.6f, effectiveMax = 4f) * master
    val releaseCohesion = composeMotionPower(value = motion.releaseCohesion, uiMax = 1.6f, effectiveMax = 4f) * master
    val fieldContinuity = composeMotionPower(value = motion.fieldContinuity, uiMax = 1.6f, effectiveMax = 4f) * master
    val sweepMomentum = composeMotionPower(value = motion.sweepMomentum, uiMax = 1.6f, effectiveMax = 4f) * master

    val elasticity = if (ordinaryPressEnabled) {
        ordinaryGlassElasticity(role, pressSize) * master.coerceIn(0f, 1f)
    } else {
        0f
    }

    val capsule = ComposeGlassLabState.capsuleTuning.normalized()
    val capsuleAspect = pressSize.width.coerceAtLeast(1f) / pressSize.height.coerceAtLeast(1f)
    val capsuleCompact = ((190f - minOf(pressSize.width, pressSize.height)) / 150f).coerceIn(0f, 1f)
    val capsuleElongated = ((capsuleAspect - 1.55f) / 3.20f).coerceIn(0f, 1f)
    val capsuleCompactGain = (1f + capsuleCompact * (capsule.compactBoost - 1f) * 0.72f).coerceIn(0.88f, 1.48f)
    val capsuleXGain = (capsuleCompactGain * (1f - capsuleElongated * capsule.elongatedX * 0.34f)).coerceIn(0.72f, 1.52f)
    val capsuleYGain = (1f + capsuleElongated * capsule.elongatedY * 0.72f).coerceIn(0.88f, 1.36f)
    val capsuleBodyGain = (1f + capsule.basePx * 10.5f + capsule.tapPx * 6.5f + capsule.tapPop * 0.055f).coerceIn(0.88f, 1.62f)
    val capsuleLightGain = (1f + capsule.tapPx * 5.0f + capsule.sticky * 7.5f + capsule.tapCarry * 0.10f).coerceIn(0.88f, 1.72f)
    val capsuleSinkGain = (0.78f + capsule.sink * 0.58f).coerceIn(0.72f, 1.46f)
    val capsuleSettleDamp = (1f - capsule.settle * 0.10f).coerceIn(0.88f, 1f)

    val materialValue = if (ordinaryPressEnabled) {
        ordinaryMaterial.value.coerceIn(0f, 1.40f)
    } else {
        0f
    }
    val positiveMaterial = materialValue.coerceAtLeast(0f)
    val tapFlashValue = if (ordinaryPressEnabled) {
        ordinaryTapFlash.value.coerceIn(0f, 1.40f)
    } else {
        0f
    }

    /*
     * 跟手胶囊版普通玻璃材料曲线：
     * - 几何形变只看 positiveMaterial，按下和松手都保持一段主动画。
     * - 极短点击的可见反馈交给 ordinaryTapFlash；它只参与白胶光场、边缘亮线和扫光，
     *   不参与 scale / translation / shadow，避免松手后按钮本体继续“补动画”。
     * - 胶囊体积、触点白胶、边缘亮线从几何主曲线和光效保底曲线连续派生。
     */
    val pressCompression = composeMotionSmoothStep((positiveMaterial / 0.82f).coerceIn(0f, 1f))
    val flashEnvelope = composeMotionSmoothStep((tapFlashValue / 0.86f).coerceIn(0f, 1f))
    val tapEnvelope = flashEnvelope
    val releaseEnvelope = 0f
    val contactEnvelope = maxOf(pressCompression, flashEnvelope * 0.46f)
    val pressGlowEnvelope = (
        pressCompression * (0.74f + touchLight.coerceIn(0f, 16f) * 0.010f) * capsuleLightGain +
            positiveMaterial * (0.070f + afterglow.coerceIn(0f, 12f) * 0.005f)
        ).coerceIn(0f, 1.28f)
    val flashGlowEnvelope = (
        flashEnvelope * (0.58f + touchLight.coerceIn(0f, 16f) * 0.010f) * capsuleLightGain
        ).coerceIn(0f, 1.18f)
    val glowEnvelope = (
        pressGlowEnvelope + flashGlowEnvelope * (1f - pressGlowEnvelope * 0.34f)
        ).coerceIn(0f, 1.52f)
    val pressSweepEnvelope = (
        pressCompression * (0.20f + sweep.coerceIn(0f, 16f) * 0.014f) +
            positiveMaterial * (0.070f + sweepMomentum.coerceIn(0f, 4f) * 0.030f)
        ).coerceIn(0f, 1.04f)
    val flashSweepEnvelope = (
        flashEnvelope * (0.30f + sweep.coerceIn(0f, 16f) * 0.011f + sweepMomentum.coerceIn(0f, 4f) * 0.032f)
        ).coerceIn(0f, 0.92f)
    val sweepEnvelope = (
        pressSweepEnvelope + flashSweepEnvelope * (1f - pressSweepEnvelope * 0.30f)
        ).coerceIn(0f, 1.26f)

    val pressValue = if (ordinaryPressEnabled) {
        (
            positiveMaterial *
                (0.92f + deformation.coerceIn(0f, 12f) * 0.022f) * capsuleBodyGain
            ).coerceIn(0f, 2.18f)
    } else {
        0f
    }
    val positivePress = pressValue.coerceAtLeast(0f)
    val rebound = 0f
    val lensValue = if (ordinaryPressEnabled) {
        (
            contactEnvelope * (0.60f + touchLight.coerceIn(0f, 16f) * 0.024f) * capsuleLightGain +
                glowEnvelope * (0.34f + afterglow.coerceIn(0f, 12f) * 0.020f)
            ).coerceIn(0f, 3.35f)
    } else {
        0f
    }
    val sweepValue = if (ordinaryPressEnabled) {
        (
            sweepEnvelope * (0.48f + sweep.coerceIn(0f, 16f) * 0.022f)
            ).coerceIn(0f, 3.10f)
    } else {
        0f
    }
    val opticsPress = maxOf(positivePress, lensValue * 0.78f, glowEnvelope * 0.56f, tapFlashValue * 0.58f)
    val pressedIntensity = baseIntensity * (
        1f +
            pressCompression * (0.046f + 0.140f * elasticity) * capsuleBodyGain +
            glowEnvelope * 0.014f +
            flashEnvelope * 0.010f
    )

    val shimmer = rememberGlassShimmer(quality, motionIntensity)
    val breathe = rememberGlassBreath(quality, motionIntensity)
    val coordinates = remember { GlassCoordinateSource() }
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val useNewOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && role == GlassRole.Shell && !viewportOwnsShell && cardBackdrop != null
    val parentOwnsOrdinaryGlass = role != GlassRole.Shell &&
        LocalOrdinaryGlassRenderMode.current == OrdinaryGlassRenderMode.ParentDraw

    val ordinarySpeed = motion.speed.coerceIn(0.08f, 8f)
    val ordinaryDurationScale = when {
        ordinarySpeed <= 1f -> (1.12f / ordinarySpeed.coerceAtLeast(0.08f)).coerceIn(1.12f, 5.20f)
        else -> (1.12f / ordinarySpeed).coerceIn(0.34f, 1.12f)
    }
    fun ordinaryDuration(baseMs: Int, minMs: Int, maxMs: Int): Int =
        (baseMs * ordinaryDurationScale).roundToInt().coerceIn(minMs, maxMs)

    ReportOrdinaryGlassNode(
        coordinates = coordinates,
        role = role,
        quality = quality,
        radius = effectiveRadius,
        glassIntensity = pressedIntensity,
        backdropAlpha = ordinaryBackdropAlpha(role, pressedIntensity),
        edgeStrength = 0f,
        pressable = true,
        shimmer = shimmer + 0.030f * maxOf(pressCompression, glowEnvelope * 0.42f),
        breathe = breathe,
        pressProgress = pressValue,
        lensProgress = lensValue,
        sweepProgress = sweepValue,
        elasticity = elasticity,
        pressCenter = pressCenter
    )

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                pressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
            }
            .pointerInput(
                ordinaryPressEnabled,
                master,
                deformation,
                touchLight,
                sweep,
                reboundControl,
                afterglow,
                tapImpulse,
                releaseCohesion,
                fieldContinuity,
                sweepMomentum,
                ordinarySpeed,
                role
            ) {
                if (!ordinaryPressEnabled) return@pointerInput
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        pressCenter = Offset(
                            (position.x / pressSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                            (position.y / pressSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        )
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTimeNanos = System.nanoTime()
                    updatePressCenter(down.position)

                    pressScope.launch {
                        ordinaryMaterial.stop()

                        /*
                         * 按下只跑一段主动画：current -> pressedTarget。
                         * 不再拆成 contactTarget + holdTarget，也不再用短点击 impulse 继续推高几何形变。
                         */
                        if (ordinaryMaterial.value < 0f) {
                            ordinaryMaterial.snapTo(0f)
                        }

                        val pressedTarget = (
                            0.78f +
                                deformation.coerceIn(0f, 12f) * 0.023f +
                                touchLight.coerceIn(0f, 16f) * 0.005f +
                                tapImpulse.coerceIn(0f, 4f) * 0.010f
                            ).let { it * (0.94f + (capsuleBodyGain - 1f) * 0.44f) }
                            .coerceIn(0.62f, 1.16f)

                        ordinaryMaterial.animateTo(
                            pressedTarget,
                            spring(
                                dampingRatio = 0.90f,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }

                    pressScope.launch {
                        ordinaryTapFlash.stop()
                        val flashTarget = (
                            0.78f +
                                touchLight.coerceIn(0f, 16f) * 0.010f +
                                tapImpulse.coerceIn(0f, 4f) * 0.026f
                            ).coerceIn(0.70f, 1.02f)
                        ordinaryTapFlash.snapTo(maxOf(ordinaryTapFlash.value, flashTarget))
                        ordinaryTapFlash.animateTo(
                            0f,
                            tween(ordinaryDuration(220, 150, 320), easing = FastOutSlowInEasing)
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

                    pressScope.launch {
                        ordinaryMaterial.stop()

                        /*
                         * 松手也只跑一段主动画：current -> 0。
                         * 释放粘度只调阻尼，不再生成负向 releaseTarget 或正向 impulseTarget。
                         */
                        ordinaryMaterial.animateTo(
                            0f,
                            spring(
                                dampingRatio = ((0.78f + releaseCohesion.coerceIn(0f, 4f) * 0.030f) * capsuleSettleDamp).coerceIn(0.76f, 0.92f),
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }

                }
            }
            .onPlaced { coordinates.coordinates = it }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                if (parentOwnsOrdinaryGlass) {
                    scaleX = 1f + pressCompression * 0.020f * capsuleXGain
                    scaleY = 1f - pressCompression * 0.0055f * capsuleYGain
                    translationY = pressCompression * 0.52f * capsuleSinkGain
                    shadowElevation = pressCompression * 0.30f
                } else {
                    val grow = deformation.coerceIn(0f, 12f)
                    val bounce = reboundControl.coerceIn(0f, 10f)
                    val endCapDamp = when (role) {
                        GlassRole.Chip -> 1.00f
                        GlassRole.Flex -> 0.70f
                        GlassRole.Floating -> 0.86f
                        GlassRole.Card -> 0.72f
                        GlassRole.Nav -> 0.58f
                        GlassRole.Shell -> 0f
                    }
                    scaleX = 1f +
                        pressCompression * (0.040f + 0.008f * grow) * endCapDamp * capsuleXGain -
                        rebound * (0.006f + 0.0015f * bounce) * endCapDamp
                    scaleY = 1f -
                        pressCompression * (0.010f + 0.0035f * grow) * capsuleYGain -
                        rebound * (0.004f + 0.0015f * bounce)
                    translationY =
                        pressCompression * (0.44f + 0.15f * grow) * capsuleSinkGain -
                            rebound * (0.12f + 0.050f * bounce)
                    shadowElevation = pressCompression * (0.74f + 0.12f * grow)
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .ordinaryGlassFrame(
                radius = effectiveRadius,
                glassIntensity = pressedIntensity,
                role = role,
                parentOwnsOrdinaryGlass = parentOwnsOrdinaryGlass
            )
    ) {
        if (useNewOpenGlBackdrop) {
            NewOpenGLGlassCardLayer(radius = effectiveRadius, glassIntensity = pressedIntensity, coordinateSource = coordinates, modifier = Modifier.matchParentSize())
        } else if (!parentOwnsOrdinaryGlass && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(Modifier.matchParentSize(), effectiveRadius, coordinates, backdrop.quality, backdrop.motionIntensity, backdrop.theme, blurForRole(role), ordinaryBackdropAlpha(role, pressedIntensity))
        }
        if (!parentOwnsOrdinaryGlass && !useNewOpenGlBackdrop) {
            Box(
                Modifier
                    .matchParentSize()
                    .glassSkin(
                        quality,
                        effectiveRadius,
                        shimmer + 0.030f * maxOf(pressCompression, glowEnvelope * 0.42f),
                        breathe,
                        pressedIntensity,
                        role = role,
                        includeShadow = false
                    )
            )
        }
        content()
        if (!parentOwnsOrdinaryGlass && ordinaryPressEnabled && opticsPress > 0.001f) {
            Box(Modifier.matchParentSize().ordinaryPressSurfaceOptics(opticsPress, sweepValue, effectiveRadius, pressCenter, role, elasticity))
        }
    }
}

@Composable
private fun LegacyPressableGlass(
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
    val backdrop = LocalGlassBackdrop.current
    val requestedMotion = motionIntensity.coerceIn(0f, 1f)
    val settingsMotion = if (
        requestedMotion <= 0.02f &&
        LocalSettingsFrostParentLayer.current != null
    ) {
        backdrop?.motionIntensity?.coerceIn(0f, 1f)
    } else {
        null
    }
    val motion = settingsMotion ?: requestedMotion
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
    val cardBackdrop = LocalBlurredBackdrop.current
    val viewportOwnsShell = LocalOpenGLGlassViewportActive.current && role == GlassRole.Shell
    val useNewOpenGlBackdrop = USE_CARD_BOUND_OPENGL_GLASS && role == GlassRole.Shell && !viewportOwnsShell && cardBackdrop != null
    val parentOwnsOrdinaryGlass = role != GlassRole.Shell &&
        LocalOrdinaryGlassRenderMode.current == OrdinaryGlassRenderMode.ParentDraw

    ReportOrdinaryGlassNode(
        coordinates = coordinates,
        role = role,
        quality = quality,
        radius = effectiveRadius,
        glassIntensity = pressedIntensity,
        backdropAlpha = ordinaryBackdropAlpha(role, pressedIntensity),
        edgeStrength = UNIFIED_EDGE_STRENGTH,
        pressable = true,
        shimmer = shimmer + 0.030f * pressCompression,
        breathe = breathe,
        pressProgress = pressValue,
        lensProgress = lensValue,
        sweepProgress = sweepValue,
        elasticity = elasticity,
        pressCenter = pressCenter
    )

    Box(
        modifier = modifier
            .onSizeChanged { size -> pressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat()) }
            .pointerInput(ordinaryPressEnabled, motion, role) {
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
            .ordinaryGlassFrame(
                radius = effectiveRadius,
                glassIntensity = pressedIntensity,
                role = role,
                parentOwnsOrdinaryGlass = parentOwnsOrdinaryGlass
            )
    ) {
        if (useNewOpenGlBackdrop) {
            NewOpenGLGlassCardLayer(radius = effectiveRadius, glassIntensity = pressedIntensity, coordinateSource = coordinates, modifier = Modifier.matchParentSize())
        } else if (!parentOwnsOrdinaryGlass && !viewportOwnsShell && backdrop != null) {
            SampledWeatherGlassBackdrop(Modifier.matchParentSize(), effectiveRadius, coordinates, backdrop.quality, backdrop.motionIntensity, backdrop.theme, blurForRole(role), ordinaryBackdropAlpha(role, pressedIntensity))
        }
        if (!parentOwnsOrdinaryGlass && !useNewOpenGlBackdrop) {
            Box(Modifier.matchParentSize().glassSkin(quality, effectiveRadius, shimmer + 0.030f * pressCompression, breathe, pressedIntensity, role = role, includeShadow = false))
        }
        content()
        if (!parentOwnsOrdinaryGlass && ordinaryPressEnabled && opticsPress > 0.001f) {
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
    drawContent()

    if (role == GlassRole.Shell) return@drawWithContent

    val dynamicPress = press.coerceAtLeast(0f)
    val lensValue = (press * 0.72f).coerceAtLeast(0f)
    val sweepValue = sweep.coerceAtLeast(0f)
    val afterValue = (sweep * 0.55f).coerceAtLeast(0f)
    val active = maxOf(dynamicPress, lensValue, sweepValue, afterValue)
    if (active <= 0.001f) return@drawWithContent

    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val center = Offset(
        pressCenter.x.coerceIn(0f, 1f) * w,
        pressCenter.y.coerceIn(0f, 1f) * h
    )
    val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())

    val motion = ComposeGlassLabState.motionStyle.normalized()
    val master = composeMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    val touchLight = composeMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val prism = composeMotionPower(value = motion.prism, uiMax = 1.5f, effectiveMax = 16f) * master
    val sweepGain = composeMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val afterglow = composeMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master
    val elasticityBoost = elasticity.coerceIn(0.08f, 1f)

    val capsule = ComposeGlassLabState.capsuleTuning.normalized()
    val capsuleLight = (1f + capsule.tapPx * 5.2f + capsule.sticky * 8.0f + capsule.basePx * 4.0f).coerceIn(0.92f, 1.82f)
    val pressShape = composeMotionSmoothStep((dynamicPress + lensValue * 0.82f).coerceIn(0f, 3.2f) / 3.2f)
    val lightPower = (touchLight * (0.54f + lensValue * 0.82f + afterValue * 0.20f) * elasticityBoost * capsuleLight).coerceIn(0f, 86f)
    val prismPower = (prism * (0.24f + dynamicPress * 0.36f + sweepValue * 0.46f) * elasticityBoost).coerceIn(0f, 72f)
    val sweepPower = (sweepGain * (0.18f + sweepValue * 0.94f) * elasticityBoost).coerceIn(0f, 78f)
    val afterPower = (afterglow * (afterValue * 0.80f + lensValue * 0.28f) * elasticityBoost * capsuleLight).coerceIn(0f, 62f)
    val sweepPhase = (sweepValue / 3.60f).coerceIn(0f, 1.30f)
    val sweepX = -0.46f + sweepPhase * 1.92f
    val rimInset = 0.42.dp.toPx()
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.086f * lightPower).coerceIn(0f, 1f)),
                Color(0xFFE8FFFF).copy(alpha = (0.054f * lightPower).coerceIn(0f, 1f)),
                Color(0xFF89FFF3).copy(alpha = (0.022f * lightPower).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            center = center,
            radius = maxSide * (0.22f + 0.026f * lightPower.coerceIn(0f, 20f) + 0.34f * pressShape)
        ),
        size = Size(w, h),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Plus
    )

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.040f * lightPower).coerceIn(0f, 0.82f)),
                Color.Transparent
            ),
            center = center,
            radius = maxSide * (0.10f + 0.018f * lightPower.coerceIn(0f, 20f))
        ),
        size = Size(w, h),
        cornerRadius = cornerRadius,
        blendMode = BlendMode.Plus
    )

    if (sweepPower > 0.001f || prismPower > 0.001f) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = (0.024f * prismPower).coerceIn(0f, 0.88f)),
                    Color(0xFFFFFBDF).copy(alpha = (0.018f * prismPower + 0.020f * sweepPower).coerceIn(0f, 0.72f)),
                    Color(0xFFE8FFFF).copy(alpha = (0.024f * prismPower + 0.030f * sweepPower).coerceIn(0f, 0.86f)),
                    Color.Transparent
                ),
                start = Offset(w * (sweepX - 0.40f), h * -0.10f),
                end = Offset(w * (sweepX + 0.48f), h * 1.10f)
            ),
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = cornerRadius,
            style = Stroke((0.78.dp.toPx() + 0.20.dp.toPx() * sweepPower.coerceIn(0f, 24f)).coerceAtMost(9.5.dp.toPx())),
            blendMode = BlendMode.Plus
        )
    }

    if (afterPower > 0.001f) {
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = (0.026f * afterPower).coerceIn(0f, 0.78f)),
                    Color(0xFFB8FFF9).copy(alpha = (0.020f * afterPower).coerceIn(0f, 0.58f)),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.42f),
                radius = maxSide * (0.46f + afterPower.coerceIn(0f, 10f) * 0.065f)
            ),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            blendMode = BlendMode.Screen
        )
    }
}

private fun rememberGlassShimmer(quality: RenderQuality, motionIntensity: Float): Float = if (quality.enableMotion && motionIntensity > 0.02f) 0.20f else 0.16f

private fun rememberGlassBreath(quality: RenderQuality, motionIntensity: Float): Float = if (quality.enableMotion && motionIntensity > 0.02f) 0.38f else 0.34f

private fun roleShadowElevation(role: GlassRole): Dp = when (role) {
    GlassRole.Shell -> 5.dp
    GlassRole.Card, GlassRole.Floating, GlassRole.Nav -> 4.dp
    GlassRole.Chip, GlassRole.Flex -> 3.dp
}

private fun Modifier.ordinaryGlassFrame(
    radius: Int,
    glassIntensity: Float,
    role: GlassRole,
    parentOwnsOrdinaryGlass: Boolean
): Modifier = if (parentOwnsOrdinaryGlass && role != GlassRole.Shell) {
    clip(RoundedCornerShape(radius.dp))
} else {
    glassOuterFrame(radius = radius, glassIntensity = glassIntensity, role = role)
}

private fun Modifier.glassOuterFrame(radius: Int, glassIntensity: Float, role: GlassRole): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val material = glassMaterial(glassIntensity, role)
    return this
        .shadow(
            elevation = roleShadowElevation(role),
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = material.shadowAmbient),
            spotColor = Color.White.copy(alpha = material.shadowSpot)
        )
        .clip(shape)
}

private fun Modifier.shellPressSurfaceOptics(
    dynamicState: OpenGLGlassDynamicState,
    radius: Int,
    prismEdgeHighlight: Float
): Modifier = drawWithContent {
    drawContent()
    val dynamic = dynamicState.snapshotState.value
    val safePress = dynamic.surfaceOpticsPress.coerceIn(0f, 1.08f)
    if (safePress < 0.001f) return@drawWithContent
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val raw = (safePress / 0.72f).coerceIn(0f, 1f)
    val p = glassSmoothStep(raw)
    val breath = glassSmoothStep((safePress / 0.50f).coerceIn(0f, 1f)) * (1f - 0.11f * glassSmoothStep(((safePress - 0.58f) / 0.28f).coerceIn(0f, 1f)))
    val compression = p * p
    val centerNorm = Offset(dynamic.pressCenter.x.coerceIn(0f, 1f), dynamic.pressCenter.y.coerceIn(0f, 1f))
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
    val seedShift = (dynamic.rimFlowSeed - 0.5f) * 0.36f
    val sweepX = if (dynamic.rimFlowDirection >= 0f) -0.24f + seedShift + flow * 1.42f else 1.24f + seedShift - flow * 1.42f
    val bandStartY = when (dynamic.rimFlowBand % 4) { 0 -> 0.02f; 1 -> 0.74f; 2 -> 0.10f; else -> 0.18f }
    val bandEndY = when (dynamic.rimFlowBand % 4) { 0 -> 0.26f; 1 -> 0.98f; 2 -> 0.92f; else -> 0.58f }
    val bandAlpha = breath * dynamic.rimFlowStrength.coerceIn(0.70f, 1.45f)
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
    val base = if (includeShadow) this.glassOuterFrame(radius, glassIntensity, role) else this.clip(shape)
    return base.drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val radiusPx = radius.dp.toPx()
        val cornerRadius = CornerRadius(radiusPx, radiusPx)
        val intensityScale = glassIntensity.coerceIn(0.25f, 1.45f)
        val pulse = 0.94f + breathe * 0.030f

        val glass = ComposeGlassLabState.style
        val frost = ComposeGlassRuntimeDefaults.frost * intensityScale
        val quiet = glass.quiet
        val topLight = glass.topLight * intensityScale
        val edgeWidth = glass.topWidthDp.dp.toPx().coerceAtLeast(0.05.dp.toPx())
        val pathFlow = glass.topVariation
        val bottomLight = glass.bottomLight * intensityScale
        val bottomWidth = glass.bottomWidthDp.dp.toPx().coerceAtLeast(0.05.dp.toPx())
        val outerRim = glass.outerRim * intensityScale
        val bottomMass = glass.bottomMass * intensityScale
        val sideCarry = glass.sideLight * intensityScale

        val baseField = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.026f * frost * pulse),
                Color.White.copy(alpha = 0.0f),
                Color(0xFF000000).copy(alpha = 0.080f * quiet)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        val quietField = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.010f / quiet.coerceAtLeast(0.25f)),
                Color.Transparent,
                Color(0xFF000000).copy(alpha = 0.045f * quiet)
            ),
            center = Offset(w * 0.50f, h * 0.46f),
            radius = maxOf(w, h) * 0.72f
        )

        val opticalBandWidth = (1.0.dp.toPx() + edgeWidth * 2.20f + bottomWidth * 0.72f).coerceIn(1.0.dp.toPx(), minOf(w, h) * 0.22f)
        val innerLeft = opticalBandWidth
        val innerTop = opticalBandWidth
        val innerRight = (w - opticalBandWidth).coerceAtLeast(innerLeft + 1f)
        val innerBottom = (h - opticalBandWidth).coerceAtLeast(innerTop + 1f)
        val innerRadius = (radiusPx - opticalBandWidth).coerceAtLeast(0f)
        val edgeBandPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
            addRoundRect(RoundRect(innerLeft, innerTop, innerRight, innerBottom, innerRadius, innerRadius))
        }

        val bottomMassWidth = (opticalBandWidth + bottomWidth * 2.10f).coerceIn(opticalBandWidth, minOf(w, h) * 0.28f)
        val massInnerLeft = bottomMassWidth
        val massInnerTop = bottomMassWidth
        val massInnerRight = (w - bottomMassWidth).coerceAtLeast(massInnerLeft + 1f)
        val massInnerBottom = (h - bottomMassWidth).coerceAtLeast(massInnerTop + 1f)
        val massInnerRadius = (radiusPx - bottomMassWidth).coerceAtLeast(0f)
        val bottomMassBandPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
            addRoundRect(RoundRect(massInnerLeft, massInnerTop, massInnerRight, massInnerBottom, massInnerRadius, massInnerRadius))
        }

        val topAlpha = (0.120f + 0.055f * pathFlow) * topLight
        val sideAlpha = 0.028f * sideCarry
        val bottomAlpha = 0.090f * bottomLight
        val edgeBandBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color(0xFFEAF9FF).copy(alpha = topAlpha * 0.36f),
                Color.White.copy(alpha = sideAlpha),
                Color.Transparent,
                Color.White.copy(alpha = bottomAlpha * 0.26f),
                Color.White.copy(alpha = bottomAlpha)
            ),
            startY = 0f,
            endY = h
        )

        val flowBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha * 0.18f * pathFlow),
                Color.White.copy(alpha = topAlpha * 0.04f),
                Color.Transparent,
                Color.White.copy(alpha = topAlpha * 0.08f * pathFlow),
                Color.White.copy(alpha = topAlpha * 0.025f)
            ),
            startX = 0f,
            endX = w
        )

        val bottomMassBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFF07132F).copy(alpha = 0.024f * bottomMass),
                Color(0xFF030714).copy(alpha = 0.082f * bottomMass),
                Color(0xFF00020A).copy(alpha = 0.180f * bottomMass)
            ),
            startY = 0f,
            endY = h
        )

        val rimField = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.120f * outerRim),
                Color.White.copy(alpha = 0.018f * outerRim),
                Color.Transparent,
                Color.Black.copy(alpha = 0.072f * bottomMass),
                Color.White.copy(alpha = 0.014f * outerRim)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        onDrawWithContent {
            drawRect(baseField)
            drawRect(quietField)

            drawPath(path = edgeBandPath, brush = edgeBandBrush, blendMode = BlendMode.Screen)

            if (pathFlow > 0.001f) {
                clipRect(left = 0f, top = 0f, right = w, bottom = (h * 0.52f).coerceAtLeast(1f)) {
                    drawPath(path = edgeBandPath, brush = flowBrush, blendMode = BlendMode.Screen)
                }
            }

            if (bottomMass > 0.001f) {
                clipRect(left = 0f, top = h * 0.45f, right = w, bottom = h) {
                    drawPath(path = bottomMassBandPath, brush = bottomMassBrush, blendMode = BlendMode.Multiply)
                }
            }

            drawContent()

            drawRoundRect(
                brush = rimField,
                topLeft = Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
                size = Size((w - 1.5.dp.toPx()).coerceAtLeast(1f), (h - 1.5.dp.toPx()).coerceAtLeast(1f)),
                cornerRadius = cornerRadius,
                style = Stroke(maxOf(0.34.dp.toPx(), 0.48.dp.toPx() * outerRim)),
                blendMode = BlendMode.Screen
            )
        }
    }
}

private data class GlassMaterial(
    val frost: Float,
    val rim: Float,
    val innerRim: Float,
    val topHighlight: Float,
    val cornerGlint: Float,
    val depthShadow: Float,
    val shadowAmbient: Float,
    val shadowSpot: Float,
    val strokeWidth: Float
)

private fun glassMaterial(intensity: Float, role: GlassRole): GlassMaterial {
    val safeIntensity = intensity.coerceIn(0.25f, 1.45f)
    val ordinary = role != GlassRole.Shell
    if (ordinary) {
        val glass = ComposeGlassLabState.style
        return GlassMaterial(
            frost = (0.044f * ComposeGlassRuntimeDefaults.frost * safeIntensity).coerceIn(0.004f, 0.090f),
            rim = (0.104f * glass.outerRim * safeIntensity).coerceIn(0.010f, 0.240f),
            innerRim = (0.030f * ComposeGlassRuntimeDefaults.innerBevel * safeIntensity).coerceIn(0f, 0.120f),
            topHighlight = (0.036f * glass.topLight * safeIntensity).coerceIn(0.002f, 0.100f),
            cornerGlint = (0.020f * glass.topVariation * safeIntensity).coerceIn(0f, 0.080f),
            depthShadow = (0.015f * glass.bottomMass * safeIntensity).coerceIn(0.002f, 0.055f),
            shadowAmbient = (0.028f * ComposeGlassRuntimeDefaults.shadow * safeIntensity).coerceIn(0.004f, 0.080f),
            shadowSpot = (0.0035f * ComposeGlassRuntimeDefaults.shadow * safeIntensity).coerceIn(0.001f, 0.014f),
            strokeWidth = (0.42f + glass.outerRim * 0.24f).coerceIn(0.40f, 2.20f)
        )
    }

    val base = GlassMaterial(0.044f, 0.104f, 0.030f, 0.036f, 0.020f, 0.015f, 0.028f, 0.0035f, 1f)
    return GlassMaterial(
        frost = (base.frost * safeIntensity).coerceIn(0.004f, 0.090f),
        rim = (base.rim * safeIntensity).coerceIn(0.010f, 0.240f),
        innerRim = (base.innerRim * safeIntensity * 0.30f).coerceIn(0f, 0.120f),
        topHighlight = (base.topHighlight * safeIntensity).coerceIn(0.002f, 0.100f),
        cornerGlint = (base.cornerGlint * safeIntensity * 0.46f).coerceIn(0f, 0.080f),
        depthShadow = (base.depthShadow * safeIntensity).coerceIn(0.002f, 0.055f),
        shadowAmbient = (base.shadowAmbient * safeIntensity).coerceIn(0.004f, 0.080f),
        shadowSpot = (base.shadowSpot * safeIntensity).coerceIn(0.001f, 0.014f),
        strokeWidth = 1f
    )
}
