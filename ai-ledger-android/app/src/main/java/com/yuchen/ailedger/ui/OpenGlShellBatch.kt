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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import com.yuchen.ailedger.ui.gl.LocalOpenGLShellBatchState
import com.yuchen.ailedger.ui.gl.NewOpenGLGlassBatchLayer
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState
import com.yuchen.ailedger.ui.gl.OpenGLShellBatchItem
import com.yuchen.ailedger.ui.gl.rememberOpenGLShellBatchState
import kotlin.random.Random
import kotlinx.coroutines.launch

private val BatchShellPressPreloadEasing = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val BatchShellPressSinkEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val BatchShellPressReleaseEasing = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val BatchShellPressPulseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)

/**
 * 一个页面级 TextureView / EGL 宿主，内部的每个 Shell 仍保留独立几何、采样原点与动态状态。
 * 它不是把一块大玻璃裁成多块，而是在同一 OpenGL 帧中按卡片逐个执行股票 Hero 使用的
 * 同一 fragment shader。
 */
@Composable
internal fun OpenGlShellBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberOpenGLShellBatchState()
    val parentCoordinates = remember { GlassCoordinateSource() }

    DisposableEffect(state) {
        onDispose { state.clear() }
    }

    Box(
        modifier = modifier.onPlaced { parentCoordinates.coordinates = it },
    ) {
        NewOpenGLGlassBatchLayer(
            state = state,
            parentCoordinates = parentCoordinates,
            modifier = Modifier.matchParentSize(),
        )
        CompositionLocalProvider(LocalOpenGLShellBatchState provides state) {
            content()
        }
    }
}

/**
 * OpenGlShellGlass 在批宿主中的单卡实现。按压曲线、折射动态状态和光学叠层与单卡 Shell
 * 保持一致；这里只把底层 OpenGL 输出登记给父级共享宿主。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun OpenGlShellBatchItemSurface(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val batchState = LocalOpenGLShellBatchState.current ?: return
    val effectiveRadius = if (radius >= 999) radius else radius.coerceAtLeast(30)
    val coordinates = remember { GlassCoordinateSource() }
    val itemId = remember { Any() }
    val shellPressEnabled = motionIntensity > 0.02f
    val shellPress = remember { Animatable(0f) }
    val shellOpenGlPressAnim = remember { Animatable(0f) }
    val dynamicState = remember { OpenGLGlassDynamicState() }
    val pressScope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val prismEdgeHighlight = LocalRainbowPrismStyle.current.edgeHighlight.coerceIn(0f, 2f)
    var pressSize by remember { mutableStateOf(Size(1f, 1f)) }

    SideEffect {
        batchState.upsert(
            OpenGLShellBatchItem(
                id = itemId,
                coordinates = coordinates,
                radiusDp = effectiveRadius,
                baseIntensity = glassIntensity,
                dynamicState = dynamicState,
            )
        )
    }
    DisposableEffect(batchState, itemId) {
        onDispose { batchState.remove(itemId) }
    }

    LaunchedEffect(shellPressEnabled, shellPress, shellOpenGlPressAnim, dynamicState) {
        if (!shellPressEnabled) {
            dynamicState.reset()
            return@LaunchedEffect
        }
        snapshotFlow { shellPress.value to shellOpenGlPressAnim.value }
            .collect { (pressValue, openGlPress) ->
                dynamicState.updateAnimation(pressValue, openGlPress)
            }
    }

    val pressModifier = if (shellPressEnabled) {
        Modifier
            .onSizeChanged { size ->
                pressSize = Size(
                    size.width.coerceAtLeast(1).toFloat(),
                    size.height.coerceAtLeast(1).toFloat(),
                )
            }
            .pointerInput(motionIntensity, dynamicState) {
                awaitEachGesture {
                    fun updatePressCenter(position: Offset) {
                        dynamicState.updatePressCenter(
                            Offset(
                                x = (position.x / pressSize.width).coerceIn(0f, 1f),
                                y = (position.y / pressSize.height).coerceIn(0f, 1f),
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
                        shellPress.animateTo(0.42f, tween(150, easing = BatchShellPressPulseEasing))
                        shellPress.animateTo(0.62f, tween(360, easing = BatchShellPressSinkEasing))
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
                            tween(230, easing = BatchShellPressPreloadEasing),
                        )
                        shellOpenGlPressAnim.animateTo(
                            0.72f,
                            tween(520, easing = BatchShellPressSinkEasing),
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
                                tween(120, easing = BatchShellPressPulseEasing),
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
                                    tween(105, easing = BatchShellPressPulseEasing),
                                )
                                shellPress.animateTo(
                                    -0.060f,
                                    tween(150, easing = BatchShellPressReleaseEasing),
                                )
                            } else {
                                shellPress.animateTo(
                                    -0.065f,
                                    tween(220, easing = BatchShellPressReleaseEasing),
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
            onClick = onClick,
        )
    } else {
        Modifier
    }
    val shape = remember(effectiveRadius) { RoundedCornerShape(effectiveRadius.dp) }
    val backdropReady = LocalBlurredBackdrop.current?.isReady == true

    Box(
        modifier = modifier
            .then(clickableModifier)
            .then(pressModifier)
            .onPlaced { coordinates.coordinates = it }
            .then(transformModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        ) {
            if (!backdropReady) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color(0xFF17345B).copy(
                                alpha = (0.34f * glassIntensity)
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
                        .batchShellPressSurfaceOptics(
                            dynamicState = dynamicState,
                            radius = effectiveRadius,
                            prismEdgeHighlight = prismEdgeHighlight,
                        )
                )
            }
        }
    }
}

private fun batchGlassSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun Modifier.batchShellPressSurfaceOptics(
    dynamicState: OpenGLGlassDynamicState,
    radius: Int,
    prismEdgeHighlight: Float,
): Modifier = drawWithContent {
    drawContent()
    val dynamic = dynamicState.snapshotState.value
    val safePress = dynamic.surfaceOpticsPress.coerceIn(0f, 1.08f)
    if (safePress < 0.001f) return@drawWithContent
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val raw = (safePress / 0.72f).coerceIn(0f, 1f)
    val p = batchGlassSmoothStep(raw)
    val breath = batchGlassSmoothStep((safePress / 0.50f).coerceIn(0f, 1f)) *
        (1f - 0.11f * batchGlassSmoothStep(((safePress - 0.58f) / 0.28f).coerceIn(0f, 1f)))
    val compression = p * p
    val centerNorm = Offset(
        dynamic.pressCenter.x.coerceIn(0f, 1f),
        dynamic.pressCenter.y.coerceIn(0f, 1f),
    )
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
    val rimInset = 0.56.dp.toPx()
    val rimRadius = (radius.dp.toPx() - rimInset).coerceAtLeast(0f)
    val cornerRadius = CornerRadius(rimRadius, rimRadius)
    val rimSize = Size(
        (w - rimInset * 2f).coerceAtLeast(1f),
        (h - rimInset * 2f).coerceAtLeast(1f),
    )
    val maxSide = maxOf(w, h)
    val pressGlow = p
    fun nearEdge(distance: Float): Float =
        (1f - distance / 0.42f).coerceIn(0f, 1f) * pressGlow

    val topNear = nearEdge(centerNorm.y)
    val bottomNear = nearEdge(1f - centerNorm.y)
    val leftNear = nearEdge(centerNorm.x)
    val rightNear = nearEdge(1f - centerNorm.x)
    val edgeStroke = (0.74.dp + (0.26f * p).dp).toPx()
    val localEdgeStroke = (1.18.dp + (0.48f * p).dp).toPx()
    val flow = batchGlassSmoothStep((safePress / 0.62f).coerceIn(0f, 1f))
    val seedShift = (dynamic.rimFlowSeed - 0.5f) * 0.36f
    val sweepX = if (dynamic.rimFlowDirection >= 0f) {
        -0.24f + seedShift + flow * 1.42f
    } else {
        1.24f + seedShift - flow * 1.42f
    }
    val bandStartY = when (dynamic.rimFlowBand % 4) {
        0 -> 0.02f
        1 -> 0.74f
        2 -> 0.10f
        else -> 0.18f
    }
    val bandEndY = when (dynamic.rimFlowBand % 4) {
        0 -> 0.26f
        1 -> 0.98f
        2 -> 0.92f
        else -> 0.58f
    }
    val bandAlpha = breath * dynamic.rimFlowStrength.coerceIn(0.70f, 1.45f)
    val prism = prismEdgeHighlight.coerceIn(0f, 2f)
    val prismSoft = prism * 0.55f

    val pressureField = Brush.radialGradient(
        listOf(
            Color(0xFFEFFFFF).copy(alpha = 0.066f * breath),
            Color(0xFFB8F7FF).copy(alpha = 0.032f * breath),
            Color(0xFF82E8FF).copy(alpha = 0.010f * breath),
            Color.Transparent,
        ),
        center,
        maxSide * (0.86f + 0.06f * p),
    )
    val broadHalo = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.021f * breath),
            Color(0xFFD8FFFF).copy(alpha = 0.014f * breath),
            Color.Transparent,
        ),
        Offset(w * 0.50f, h * 0.40f),
        maxSide * 1.18f,
    )
    val elasticSurfaceField = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color(0xFF102C66).copy(alpha = 0.006f * p),
            Color(0xFF030B1A).copy(alpha = 0.034f * compression),
        ),
        center,
        maxSide * (1.00f + 0.035f * p),
    )
    val lowerWeight = Brush.verticalGradient(
        listOf(
            Color.Transparent,
            Color.Transparent,
            Color(0xFF020815).copy(alpha = 0.044f * compression),
        ),
        h * 0.44f,
        h,
    )
    val ambientRim = Brush.radialGradient(
        listOf(
            Color(0xFFEFFFFF).copy(alpha = 0.052f * breath),
            Color(0xFF92FFF1).copy(alpha = (0.018f + 0.020f * prismSoft) * breath),
            Color(0xFFFF8BE8).copy(alpha = 0.014f * prismSoft * breath),
            Color.Transparent,
        ),
        center,
        maxSide * 0.74f,
    )
    val flowingRim = Brush.linearGradient(
        listOf(
            Color.Transparent,
            Color(0xFFFF6ADB).copy(alpha = 0.20f * prism * bandAlpha),
            Color.White.copy(alpha = 0.34f * bandAlpha),
            Color(0xFFFFE08A).copy(alpha = 0.18f * prism * bandAlpha),
            Color(0xFF62FFF0).copy(alpha = (0.14f + 0.16f * prism) * bandAlpha),
            Color(0xFF92A6FF).copy(alpha = 0.12f * prism * bandAlpha),
            Color.Transparent,
        ),
        Offset(w * (sweepX - 0.26f), h * bandStartY),
        Offset(w * (sweepX + 0.22f), h * bandEndY),
    )
    fun prismHalo(power: Float, white: Float, cyan: Float) = listOf(
        Color.White.copy(alpha = white * power),
        Color(0xFFFF7DE2).copy(alpha = 0.050f * prism * power),
        Color(0xFFFFE28A).copy(alpha = 0.036f * prism * power),
        Color(0xFF80FFF2).copy(alpha = cyan * power * (0.65f + prism * 0.35f)),
        Color.Transparent,
    )
    val topEdgeHalo = Brush.radialGradient(
        prismHalo(topNear, 0.23f, 0.072f),
        Offset(center.x, rimInset),
        maxSide * 0.38f,
    )
    val bottomEdgeHalo = Brush.radialGradient(
        prismHalo(bottomNear, 0.16f, 0.054f),
        Offset(center.x, h - rimInset),
        maxSide * 0.36f,
    )
    val leftEdgeHalo = Brush.radialGradient(
        prismHalo(leftNear, 0.18f, 0.060f),
        Offset(rimInset, center.y),
        maxSide * 0.34f,
    )
    val rightEdgeHalo = Brush.radialGradient(
        prismHalo(rightNear, 0.18f, 0.060f),
        Offset(w - rimInset, center.y),
        maxSide * 0.34f,
    )

    drawRect(broadHalo, blendMode = BlendMode.Screen)
    drawRect(pressureField, blendMode = BlendMode.Screen)
    drawRect(elasticSurfaceField, blendMode = BlendMode.Multiply)
    drawRect(lowerWeight, blendMode = BlendMode.Multiply)
    drawRoundRect(
        brush = ambientRim,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(edgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = flowingRim,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(0.82.dp.toPx() + 0.20.dp.toPx() * prism),
        blendMode = BlendMode.Plus,
    )
    drawRoundRect(
        brush = topEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = bottomEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = leftEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        brush = rightEdgeHalo,
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = cornerRadius,
        style = Stroke(localEdgeStroke),
        blendMode = BlendMode.Screen,
    )
}
