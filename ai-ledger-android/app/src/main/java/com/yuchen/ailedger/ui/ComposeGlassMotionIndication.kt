package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val UnifiedPressEasing = CubicBezierEasing(0.08f, 0f, 0.04f, 1f)
private val UnifiedSinkEasing = CubicBezierEasing(0.12f, 0f, 0.08f, 1f)
private val UnifiedReleaseEasing = CubicBezierEasing(0.10f, 0f, 0.08f, 1f)

/**
 * 普通 Compose 点击光动效。
 *
 * 只有显式传入真实 [shape] 时才绘制或变换。光效路径直接由玻璃本体使用的同一个
 * Shape 在当前真实 size、layoutDirection 和 Density 下生成，不再根据宽高猜圆角。
 */
data class ComposeGlassMotionIndication(
    val motionIntensity: Float,
    val style: ComposeGlassMotionStyle,
    val shape: Shape? = null,
    val transformContent: Boolean = false,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ComposeGlassMotionNode(
            interactionSource = interactionSource,
            motionIntensity = motionIntensity.coerceIn(0f, 1f),
            style = style.normalized(),
            shape = shape,
            transformContent = transformContent,
        )
}

/**
 * 完整光动效点击入口。
 *
 * [shape] 必须与玻璃 Surface/容器使用同一个 Shape 实例；本 modifier 必须位于该容器的
 * shape、背景和内容之外。按钮实体、内容与光效随后共享同一条变换和真实 Outline。
 */
@Composable
fun Modifier.composeGlassMotionClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val motionIntensity = LocalGlassBackdrop.current?.motionIntensity ?: 1f
    val motionStyle = ComposeGlassLabState.motionStyle
    val indication = remember(motionIntensity, motionStyle, shape) {
        ComposeGlassMotionIndication(
            motionIntensity = motionIntensity,
            style = motionStyle,
            shape = shape,
            transformContent = true,
        )
    }
    return this
        .indication(interactionSource, indication)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

private class ComposeGlassMotionNode(
    private val interactionSource: InteractionSource,
    private val motionIntensity: Float,
    private val style: ComposeGlassMotionStyle,
    private val shape: Shape?,
    private val transformContent: Boolean,
) : Modifier.Node(), DrawModifierNode {
    private val press = Animatable(0f)
    private val lens = Animatable(0f)
    private val sweep = Animatable(0f)

    private var currentPress: PressInteraction.Press? = null
    private var pressCenter = Offset.Unspecified
    private var pressJob: Job? = null
    private var lensJob: Job? = null
    private var sweepJob: Job? = null

    private val enabled: Boolean
        get() = shape != null && motionIntensity > 0.02f && style.master > 0.02f

    private fun timed(baseMs: Int, minMs: Int, maxMs: Int): Int {
        val speed = style.speed.coerceIn(0.08f, 8f)
        val scale = when {
            speed <= 1f -> (1.08f / speed.coerceAtLeast(0.08f)).coerceIn(1.08f, 4.80f)
            else -> (1.08f / speed).coerceIn(0.32f, 1.08f)
        }
        return (baseMs * scale).roundToInt().coerceIn(minMs, maxMs)
    }

    override fun onAttach() {
        if (!enabled) return
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        currentPress = interaction
                        animatePressed(interaction.pressPosition)
                    }

                    is PressInteraction.Release -> {
                        if (currentPress == interaction.press) {
                            currentPress = null
                            animateReleased()
                        }
                    }

                    is PressInteraction.Cancel -> {
                        if (currentPress == interaction.press) {
                            currentPress = null
                            animateReleased()
                        }
                    }
                }
            }
        }
    }

    private fun animatePressed(position: Offset) {
        pressCenter = position
        pressJob?.cancel()
        lensJob?.cancel()
        sweepJob?.cancel()

        val tapImpulse = style.tapImpulse.coerceIn(0f, 3f)
        val deformation = style.deformation.coerceIn(0f, 3f)
        val inheritedGlow = style.fieldContinuity.coerceIn(0f, 3f)
        val contactTarget = (0.52f + deformation * 0.055f + tapImpulse * 0.018f).coerceIn(0.42f, 0.76f)
        val holdTarget = (0.74f + deformation * 0.045f + tapImpulse * 0.018f).coerceIn(0.58f, 0.92f)

        pressJob = coroutineScope.launch {
            press.stop()
            if (press.value < contactTarget * 0.68f) press.snapTo(contactTarget * 0.68f)
            press.animateTo(contactTarget, tween(timed(46, 28, 96), easing = UnifiedPressEasing))
            press.animateTo(
                holdTarget,
                spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        lensJob = coroutineScope.launch {
            lens.stop()
            if (lens.value < 0.30f) lens.snapTo(0.30f)
            lens.animateTo(
                (0.68f + style.touchLight.coerceIn(0f, 3f) * 0.055f).coerceIn(0.54f, 0.92f),
                tween(timed(82, 42, 180), easing = UnifiedPressEasing),
            )
        }
        sweepJob = coroutineScope.launch {
            sweep.stop()
            val carry = (sweep.value * (0.18f + inheritedGlow * 0.08f)).coerceIn(0f, 0.42f)
            sweep.snapTo(carry)
            sweep.animateTo(
                (0.82f + style.sweepMomentum.coerceIn(0f, 3f) * 0.050f).coerceIn(0.62f, 1.02f),
                tween(timed(260, 128, 420), easing = FastOutSlowInEasing),
            )
        }
    }

    private fun animateReleased() {
        pressJob?.cancel()
        lensJob?.cancel()
        sweepJob?.cancel()

        val reboundScale = style.rebound.coerceIn(0f, 1.5f)
        val afterglowScale = style.afterglow.coerceIn(0f, 1.5f)
        val cohesion = style.releaseCohesion.coerceIn(0f, 1.5f)
        pressJob = coroutineScope.launch {
            press.stop()
            press.animateTo(
                -0.052f * reboundScale,
                tween(timed(74, 42, 150), easing = UnifiedReleaseEasing),
            )
            press.animateTo(
                0f,
                spring(
                    dampingRatio = (0.76f + cohesion * 0.05f).coerceIn(0.76f, 0.88f),
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        lensJob = coroutineScope.launch {
            lens.stop()
            lens.animateTo(
                (0.18f + 0.18f * afterglowScale).coerceIn(0.16f, 0.38f),
                tween(timed(96, 56, 190), easing = UnifiedReleaseEasing),
            )
            lens.animateTo(
                0f,
                tween(timed((190f + 105f * afterglowScale).roundToInt(), 120, 360), easing = FastOutSlowInEasing),
            )
        }
        sweepJob = coroutineScope.launch {
            sweep.stop()
            sweep.animateTo(
                (0.10f + 0.10f * afterglowScale).coerceIn(0.08f, 0.24f),
                tween(timed(118, 68, 220), easing = UnifiedReleaseEasing),
            )
            sweep.animateTo(
                0f,
                tween(timed((180f + 95f * afterglowScale).roundToInt(), 110, 340), easing = FastOutSlowInEasing),
            )
        }
    }

    override fun ContentDrawScope.draw() {
        val exactShape = shape
        if (!enabled || exactShape == null) {
            drawContent()
            return
        }

        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val exactOutlinePath = exactShape
            .createOutline(size = size, layoutDirection = layoutDirection, density = this)
            .toExactPath()
        val rawCenter = pressCenter
        val center = if (rawCenter.x.isFinite() && rawCenter.y.isFinite()) {
            Offset(rawCenter.x.coerceIn(0f, w), rawCenter.y.coerceIn(0f, h))
        } else {
            Offset(w * 0.5f, h * 0.5f)
        }

        val energy = (motionIntensity * style.master).coerceIn(0f, 1.35f)
        val sizeElasticity = composeGlassSizeElasticity(Size(w, h))
        val elasticity = (sizeElasticity * energy * style.deformation).coerceIn(0f, 1.35f)
        val pressValue = press.value.coerceIn(-0.22f, 1.32f)
        val positivePress = pressValue.coerceAtLeast(0f)
        val rebound = composeGlassSmoothStep((-pressValue / 0.18f).coerceIn(0f, 1f))
        val compression = composeGlassSmoothStep((positivePress / 0.94f).coerceIn(0f, 1f))
        val lensValue = lens.value.coerceIn(0f, 1.12f)
        val sweepValue = sweep.value.coerceIn(0f, 1.18f)
        val opticsPress = maxOf(positivePress, lensValue * 0.86f, rebound * 0.28f) * energy

        if (opticsPress <= 0.001f && compression <= 0.001f && rebound <= 0.001f) {
            drawContent()
            return
        }

        if (!transformContent) {
            drawContent()
            return
        }

        // 所有普通 Compose 玻璃都采用“边界内形变”。不再向布局区域外膨胀，避免被
        // LazyColumn、页面 viewport 或父级圆角裁剪。光效、本体和内容仍共享同一矩阵。
        val horizontalCompression = compression * (0.005f + 0.009f * elasticity)
        val verticalCompression = compression * (0.010f + 0.044f * elasticity)
        val reboundCompression = rebound * 0.004f * elasticity
        val scaleX = (1f - horizontalCompression - reboundCompression).coerceIn(0.94f, 1f)
        val scaleY = (1f - verticalCompression - reboundCompression * 0.64f).coerceIn(0.90f, 1f)

        val desiredTranslationY = compression * (0.38f + 2.42f * elasticity) -
            rebound * 0.58f * elasticity
        val availableTop = center.y * (1f - scaleY)
        val availableBottom = (h - center.y) * (1f - scaleY)
        val translationY = desiredTranslationY.coerceIn(-availableTop, availableBottom)
        val contentScope = this

        withTransform({
            translate(top = translationY)
            scale(scaleX = scaleX, scaleY = scaleY, pivot = center)
        }) {
            contentScope.drawComposeGlassMotionUnderlay(
                outlinePath = exactOutlinePath,
                press = opticsPress,
                sweep = sweepValue,
                center = center,
                style = style,
                elasticity = elasticity,
            )
            contentScope.drawContent()
            contentScope.drawComposeGlassMotionOverlay(
                outlinePath = exactOutlinePath,
                press = opticsPress,
                sweep = sweepValue,
                center = center,
                style = style,
                elasticity = elasticity,
            )
        }
    }
}

private fun ContentDrawScope.drawComposeGlassMotionUnderlay(
    outlinePath: Path,
    press: Float,
    sweep: Float,
    center: Offset,
    style: ComposeGlassMotionStyle,
    elasticity: Float,
) {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val p = composeGlassSmoothStep((press / 0.92f).coerceIn(0f, 1f)) *
        elasticity.coerceIn(0.08f, 1.35f)
    val touch = style.touchLight.coerceIn(0f, 1.8f)
    val prism = style.prism.coerceIn(0f, 1.5f)
    val sweepStrength = style.sweep.coerceIn(0f, 1.5f)
    val sweepProgress = composeGlassSmoothStep((sweep / 1.18f).coerceIn(0f, 1f))
    val sweepX = -0.36f + sweepProgress * 1.66f

    clipPath(outlinePath) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.082f * p * touch),
                    Color(0xFF9DFFF2).copy(alpha = 0.036f * p * touch),
                    Color(0xFFFF9BE9).copy(alpha = 0.014f * p * prism),
                    Color.Transparent,
                ),
                center = center,
                radius = maxSide * (0.44f + 0.22f * p),
            ),
            size = Size(w, h),
            blendMode = BlendMode.Screen,
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF7AD9).copy(alpha = 0.060f * p * prism * sweepStrength),
                    Color(0xFFFFD166).copy(alpha = 0.040f * p * prism * sweepStrength),
                    Color(0xFF7CFFEA).copy(alpha = 0.072f * p * prism * sweepStrength),
                    Color(0xFF8EA2FF).copy(alpha = 0.052f * p * prism * sweepStrength),
                    Color.Transparent,
                ),
                start = Offset(w * (sweepX - 0.46f), h * -0.10f),
                end = Offset(w * (sweepX + 0.58f), h * 1.08f),
            ),
            size = Size(w, h),
            blendMode = BlendMode.Screen,
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF04112A).copy(alpha = 0.012f * p),
                    Color(0xFF00030A).copy(alpha = 0.046f * p),
                ),
                center = center,
                radius = maxSide * (0.74f + 0.18f * p),
            ),
            size = Size(w, h),
            blendMode = BlendMode.Multiply,
        )
    }
}

private fun ContentDrawScope.drawComposeGlassMotionOverlay(
    outlinePath: Path,
    press: Float,
    sweep: Float,
    center: Offset,
    style: ComposeGlassMotionStyle,
    elasticity: Float,
) {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val p = composeGlassSmoothStep((press / 0.92f).coerceIn(0f, 1f)) *
        elasticity.coerceIn(0.08f, 1.35f)
    val touch = style.touchLight.coerceIn(0f, 1.8f)
    val prism = style.prism.coerceIn(0f, 1.5f)
    val sweepStrength = style.sweep.coerceIn(0f, 1.5f)
    val sweepProgress = composeGlassSmoothStep((sweep / 1.18f).coerceIn(0f, 1f))
    val sweepX = -0.36f + sweepProgress * 1.66f

    clipPath(outlinePath) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.136f * p * touch),
                    Color(0xFF9DFFF1).copy(alpha = 0.058f * p * touch),
                    Color(0xFFFF8FE7).copy(alpha = 0.024f * p * prism),
                    Color.Transparent,
                ),
                center = center,
                radius = maxSide * (0.28f + 0.10f * p),
            ),
            size = Size(w, h),
            blendMode = BlendMode.Screen,
        )
        drawPath(
            path = outlinePath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF72D2).copy(alpha = 0.18f * p * prism * sweepStrength),
                    Color(0xFFFFF0A8).copy(alpha = 0.17f * p * prism * sweepStrength),
                    Color(0xFF76FFF1).copy(alpha = 0.20f * p * prism * sweepStrength),
                    Color(0xFF9AA8FF).copy(alpha = 0.14f * p * prism * sweepStrength),
                    Color.Transparent,
                ),
                start = Offset(w * (sweepX - 0.24f), 0f),
                end = Offset(w * (sweepX + 0.30f), h * 0.98f),
            ),
            style = Stroke(0.48.dp.toPx() + 0.76.dp.toPx() * p),
            blendMode = BlendMode.Plus,
        )
        drawPath(
            path = outlinePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.052f * p * touch),
                    Color(0xFFE9FFFF).copy(alpha = 0.024f * p * touch),
                    Color.Transparent,
                    Color(0xFF000819).copy(alpha = 0.040f * p),
                ),
                startY = 0f,
                endY = h,
            ),
            style = Stroke(0.36.dp.toPx() + 0.54.dp.toPx() * p),
            blendMode = BlendMode.Screen,
        )
    }
}

private fun Outline.toExactPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

private fun composeGlassSizeElasticity(size: Size): Float {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val maxSideDamp = ((720f - maxSide) / 520f).coerceIn(0.24f, 1f)
    val heightDamp = ((360f - h) / 260f).coerceIn(0.50f, 1f)
    val heightGain = when {
        h <= 180f -> 1.00f
        h <= 280f -> 0.82f
        else -> 0.62f
    }
    return (maxSideDamp * heightDamp * heightGain).coerceIn(0.14f, 1f)
}

private fun composeGlassSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
