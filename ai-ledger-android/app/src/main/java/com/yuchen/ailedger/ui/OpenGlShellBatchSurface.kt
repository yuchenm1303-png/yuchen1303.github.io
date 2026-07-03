package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.LocalOpenGLShellBatchState
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState
import com.yuchen.ailedger.ui.gl.OpenGLShellBatchItem
import com.yuchen.ailedger.ui.gl.OpenGLShellBatchState
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.launch

private val BatchSurfacePressPreloadEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val BatchSurfacePressSinkEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val BatchSurfacePressReleaseEasing = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val BatchSurfacePressPulseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)

@Composable
internal fun OpenGlShellBatchItemSurfaceImpl(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val batchState = LocalOpenGLShellBatchState.current ?: return
    val policy = LocalOpenGlShellBatchPolicy.current
    val acceptedShortEdgeDp = policy.acceptedShortEdgeDp

    if (acceptedShortEdgeDp == null) {
        OpenGlShellBatchRegisteredSurfaceImpl(
            batchState = batchState,
            quality = quality,
            rendererGlassIntensity = glassIntensity,
            frameGlassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            preserveStandaloneFrame = policy.preserveStandaloneFrame,
            onClick = onClick,
            content = content,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val shortEdgeDp = min(maxWidth.value, maxHeight.value)
        val radiusAccepted = policy.acceptedRadiusDp?.contains(radius) != false
        val backdropReady = LocalBlurredBackdrop.current?.isReady == true
        if (backdropReady && radiusAccepted && shortEdgeDp in acceptedShortEdgeDp) {
            OpenGlShellBatchRegisteredSurfaceImpl(
                batchState = batchState,
                quality = quality,
                rendererGlassIntensity = resolvedBatchRendererIntensity(glassIntensity),
                frameGlassIntensity = glassIntensity,
                motionIntensity = motionIntensity,
                radius = radius,
                modifier = Modifier.fillMaxSize(),
                preserveStandaloneFrame = policy.preserveStandaloneFrame,
                onClick = onClick,
                content = content,
            )
        } else {
            OpenGlShellStandaloneSurfaceImpl(
                quality = quality,
                glassIntensity = glassIntensity,
                motionIntensity = motionIntensity,
                radius = radius,
                modifier = Modifier.fillMaxSize(),
                onClick = onClick,
                content = content,
            )
        }
    }
}

@Composable
private fun resolvedBatchRendererIntensity(fallback: Float): Float =
    LocalGlassBackdrop.current
        ?.borderStyle
        ?.newOpenGlGlassIntensity
        ?.takeIf { it > 0f }
        ?.coerceIn(0.35f, 1.35f)
        ?: fallback.coerceIn(0.35f, 1.35f)

@Composable
private fun OpenGlShellStandaloneSurfaceImpl(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val interaction = remember { MutableInteractionSource() }
    val stableClick = remember { { currentOnClick?.invoke() } }
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = stableClick,
        )
    } else {
        Modifier
    }

    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = modifier.then(clickableModifier),
        role = GlassRole.Shell,
        content = content,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun OpenGlShellBatchRegisteredSurfaceImpl(
    batchState: OpenGLShellBatchState,
    quality: RenderQuality,
    rendererGlassIntensity: Float,
    frameGlassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    preserveStandaloneFrame: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val effectiveRadius = if (radius >= 999) radius else radius.coerceAtLeast(30)
    val itemId = remember { Any() }
    val shellPressEnabled = motionIntensity > 0.02f
    val shellPress = remember { Animatable(0f) }
    val shellOpenGlPressAnim = remember { Animatable(0f) }
    val dynamicState = remember { OpenGLGlassDynamicState() }
    val item = remember(itemId, effectiveRadius, dynamicState) {
        OpenGLShellBatchItem(
            id = itemId,
            radiusDp = effectiveRadius,
            dynamicState = dynamicState,
            baseIntensity = rendererGlassIntensity,
        )
    }
    val pressScope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    val stableClick = remember { { currentOnClick?.invoke() } }
    val prismEdgeHighlight = LocalRainbowPrismStyle.current.edgeHighlight.coerceIn(0f, 2f)
    val pressSize = remember { FloatArray(2) { 1f } }

    SideEffect {
        item.updateBaseIntensity(rendererGlassIntensity)
    }
    DisposableEffect(batchState, item) {
        batchState.register(item)
        onDispose { batchState.remove(item.id) }
    }

    LaunchedEffect(shellPressEnabled, shellPress, shellOpenGlPressAnim, dynamicState) {
        if (!shellPressEnabled) {
            dynamicState.reset()
            return@LaunchedEffect
        }
        snapshotFlow {
            packBatchSurfaceAnimation(shellPress.value, shellOpenGlPressAnim.value)
        }.collect { packed ->
            dynamicState.updateAnimation(
                pressValue = Float.fromBits((packed ushr 32).toInt()),
                openGlPress = Float.fromBits(packed.toInt()),
            )
        }
    }

    val pressModifier = if (shellPressEnabled) {
        Modifier
            .onSizeChanged { size ->
                pressSize[0] = size.width.coerceAtLeast(1).toFloat()
                pressSize[1] = size.height.coerceAtLeast(1).toFloat()
            }
            .pointerInput(motionIntensity, dynamicState) {
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        dynamicState.updatePressCenter(
                            Offset(
                                x = (position.x / pressSize[0]).coerceIn(0f, 1f),
                                y = (position.y / pressSize[1]).coerceIn(0f, 1f),
                            )
                        )
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePressCenter(down.position)
                    dynamicState.updateRimFlow(
                        seed = Random.nextFloat(),
                        direction = if (Random.nextBoolean()) 1f else -1f,
                        band = Random.nextInt(0, 4),
                        strength = 0.86f + Random.nextFloat() * 0.52f,
                    )
                    pressScope.launch {
                        shellPress.stop()
                        if (shellPress.value < 0.18f) shellPress.snapTo(0.18f)
                        shellPress.animateTo(0.42f, tween(150, easing = BatchSurfacePressPulseEasing))
                        shellPress.animateTo(0.62f, tween(360, easing = BatchSurfacePressSinkEasing))
                        shellPress.animateTo(0.76f, tween(620, easing = FastOutSlowInEasing))
                        shellPress.animateTo(0.62f, tween(680, easing = FastOutSlowInEasing))
                        shellPress.animateTo(
                            0.70f,
                            spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessVeryLow,
                            ),
                        )
                    }
                    pressScope.launch {
                        shellOpenGlPressAnim.stop()
                        shellOpenGlPressAnim.animateTo(
                            0.26f,
                            tween(230, easing = BatchSurfacePressPreloadEasing),
                        )
                        shellOpenGlPressAnim.animateTo(
                            0.72f,
                            tween(520, easing = BatchSurfacePressSinkEasing),
                        )
                        shellOpenGlPressAnim.animateTo(0.88f, tween(620, easing = FastOutSlowInEasing))
                        shellOpenGlPressAnim.animateTo(0.74f, tween(680, easing = FastOutSlowInEasing))
                        shellOpenGlPressAnim.animateTo(
                            0.80f,
                            spring(
                                dampingRatio = 0.95f,
                                stiffness = Spring.StiffnessVeryLow,
                            ),
                        )
                    }

                    var releasedInsideGesture = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
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
                        shellOpenGlPressAnim.stop()
                        val currentLens = shellOpenGlPressAnim.value.coerceIn(0f, 1f)
                        if (releasedInsideGesture && currentLens < 0.24f) {
                            shellOpenGlPressAnim.animateTo(
                                0.34f,
                                tween(120, easing = BatchSurfacePressPulseEasing),
                            )
                        }
                        shellOpenGlPressAnim.animateTo(
                            0f,
                            tween(
                                if (releasedInsideGesture) 560 else 380,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    pressScope.launch {
                        shellPress.stop()
                        if (releasedInsideGesture) {
                            val current = shellPress.value.coerceIn(0f, 1.08f)
                            if (current < 0.46f) {
                                shellPress.animateTo(
                                    0.52f,
                                    tween(105, easing = BatchSurfacePressPulseEasing),
                                )
                                shellPress.animateTo(
                                    -0.060f,
                                    tween(150, easing = BatchSurfacePressReleaseEasing),
                                )
                            } else {
                                shellPress.animateTo(
                                    -0.065f,
                                    tween(220, easing = BatchSurfacePressReleaseEasing),
                                )
                            }
                            shellPress.animateTo(
                                0f,
                                spring(
                                    dampingRatio = 0.66f,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                        } else {
                            shellPress.animateTo(0f, tween(430, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    val transformModifier = if (shellPressEnabled) {
        Modifier.graphicsLayer {
            val dynamic = dynamicState.snapshotState.value
            transformOrigin = TransformOrigin(dynamic.pressCenter.x, dynamic.pressCenter.y)
            scaleX = 1f + dynamic.pressCompression * 0.014f - dynamic.pressRebound * 0.004f
            scaleY = 1f - dynamic.pressCompression * 0.022f + dynamic.pressRebound * 0.008f
            translationY = dynamic.pressCompression * 2.10f - dynamic.pressRebound * 0.80f
            shadowElevation = dynamic.pressCompression * 0.45f
        }
    } else {
        Modifier
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = stableClick,
        )
    } else {
        Modifier
    }
    val shape = remember(effectiveRadius) { RoundedCornerShape(effectiveRadius.dp) }
    val backdropReady = LocalBlurredBackdrop.current?.isReady == true
    val framedIntensity = if (preserveStandaloneFrame) {
        frameGlassIntensity * dynamicState.snapshotState.value.glassIntensityScale
    } else {
        frameGlassIntensity
    }
    val contentFrameModifier = if (preserveStandaloneFrame) {
        Modifier.openGlBatchStandaloneShellFrame(
            radius = effectiveRadius,
            glassIntensity = framedIntensity,
        )
    } else {
        Modifier.clip(shape)
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .then(pressModifier)
            .onPlaced(item::updatePlacement)
            .then(transformModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(contentFrameModifier),
        ) {
            if (!backdropReady) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color(0xFF17345B).copy(
                                alpha = (0.34f * frameGlassIntensity)
                                    .coerceIn(0.18f, 0.48f),
                            )
                        )
                )
            }
            content()
            if (shellPressEnabled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .openGlBatchShellPressSurfaceOptics(
                            dynamicState = dynamicState,
                            radius = effectiveRadius,
                            prismEdgeHighlight = prismEdgeHighlight,
                        )
                )
            }
        }
    }
}

private fun packBatchSurfaceAnimation(pressValue: Float, openGlPress: Float): Long =
    (pressValue.toRawBits().toLong() shl 32) or
        (openGlPress.toRawBits().toLong() and 0xFFFF_FFFFL)
