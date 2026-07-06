package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/*
 * New ordinary glass motion layer.
 *
 * The original Glass.kt ordinaryPress / ordinaryLens / ordinarySweep code is kept untouched
 * and can be reconnected later by passing motionIntensity back to the original chain.
 * The app currently passes motionIntensity = 0f to the original chain so only the glass body
 * remains there. This file provides one unified newPress progress for shape, bloom, sweep,
 * afterglow, and rebound. Shell is forwarded directly and never enters this ordinary layer.
 */
@Composable
fun PressableGlass(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    role: GlassRole,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    if (role == GlassRole.Shell) {
        PressableGlass(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            onClick = onClick,
            intensity = null,
            content = content
        )
        return
    }

    val motion = ComposeGlassLabState.motionStyle.normalized()
    PressableGlass(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = 0f,
        radius = radius,
        modifier = modifier.newOrdinaryGlassMotionLayer(
            radius = radius,
            role = role,
            motion = motion,
            baseMotion = motionIntensity
        ),
        role = role,
        onClick = onClick,
        intensity = null,
        content = content
    )
}

@Composable
fun GlassPanel(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    role: GlassRole,
    content: @Composable () -> Unit
) {
    if (role == GlassRole.Shell) {
        GlassPanel(
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            viewportTopInset = 0.dp,
            intensity = null,
            content = content
        )
        return
    }

    val motion = ComposeGlassLabState.motionStyle.normalized()
    GlassPanel(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = 0f,
        radius = radius,
        modifier = modifier.newOrdinaryGlassMotionLayer(
            radius = radius,
            role = role,
            motion = motion,
            baseMotion = motionIntensity
        ),
        role = role,
        viewportTopInset = 0.dp,
        intensity = null,
        content = content
    )
}

@Composable
private fun Modifier.newOrdinaryGlassMotionLayer(
    radius: Int,
    role: GlassRole,
    motion: ComposeGlassMotionStyle,
    baseMotion: Float,
): Modifier {
    if (role == GlassRole.Shell) return this

    val master = motion.master.coerceIn(0f, 8f) * baseMotion.coerceIn(0f, 1.4f)
    if (master <= 0.001f) return this

    val deformation = motion.deformation.coerceIn(0f, 8f) * master
    val touchLight = motion.touchLight.coerceIn(0f, 16f) * master
    val sweep = motion.sweep.coerceIn(0f, 16f) * master
    val rebound = motion.rebound.coerceIn(0f, 8f) * master
    val afterglow = motion.afterglow.coerceIn(0f, 12f) * master
    val speed = motion.speed.coerceIn(0.35f, 2.5f)
    val durationScale = (1f / speed).coerceIn(0.42f, 2.85f)

    fun scaledDuration(ms: Int): Int = (ms * durationScale).toInt().coerceIn(40, 1800)

    val scope = rememberCoroutineScope()
    val newPress = remember { Animatable(0f) }
    var pressCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var measuredSize by remember { mutableStateOf(Size(1f, 1f)) }

    return this
        .onSizeChanged { size ->
            measuredSize = Size(
                width = size.width.coerceAtLeast(1).toFloat(),
                height = size.height.coerceAtLeast(1).toFloat()
            )
        }
        .pointerInput(master, deformation, touchLight, sweep, rebound, afterglow, speed, role) {
            awaitEachGesture {
                fun updateCenter(position: Offset) {
                    pressCenter = Offset(
                        x = (position.x / measuredSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        y = (position.y / measuredSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    )
                }

                val down = awaitFirstDown(requireUnconsumed = false)
                updateCenter(down.position)

                val instant = (0.18f + deformation * 0.018f + touchLight * 0.004f)
                    .coerceIn(0.14f, 0.56f)
                val burst = (0.54f + deformation * 0.060f + touchLight * 0.010f)
                    .coerceIn(0.22f, 1.22f)
                val hold = (0.40f + deformation * 0.042f + afterglow * 0.010f)
                    .coerceIn(0.18f, 0.94f)

                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    newPress.snapTo(maxOf(newPress.value, instant))
                    newPress.animateTo(
                        burst,
                        tween(scaledDuration(104), easing = FastOutSlowInEasing)
                    )
                    newPress.animateTo(
                        hold,
                        spring(
                            dampingRatio = 0.66f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val tracked = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                    if (tracked != null) {
                        updateCenter(tracked.position)
                        if (!tracked.pressed) break
                    }
                    if (event.changes.none { it.pressed }) break
                }

                scope.launch {
                    newPress.stop()
                    newPress.animateTo(
                        (-0.09f - rebound * 0.016f).coerceIn(-0.62f, -0.015f),
                        tween(scaledDuration(136), easing = FastOutSlowInEasing)
                    )
                    newPress.animateTo(
                        (0.030f + afterglow * 0.004f).coerceIn(0.018f, 0.090f),
                        spring(
                            dampingRatio = 0.54f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    newPress.animateTo(
                        0f,
                        spring(
                            dampingRatio = 0.76f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
        .graphicsLayer {
            val pressPositive = newPress.value.coerceAtLeast(0f)
            val releaseNegative = (-newPress.value).coerceAtLeast(0f)
            val p = newMotionSmoothStep((pressPositive / 1.22f).coerceIn(0f, 1f))
            val r = newMotionSmoothStep((releaseNegative / 0.62f).coerceIn(0f, 1f))
            val grow = deformation.coerceIn(0f, 8f)
            val bounce = rebound.coerceIn(0f, 8f)

            transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
            scaleX = 1f + p * (0.030f + 0.010f * grow) -
                r * (0.008f + 0.003f * bounce)
            scaleY = 1f + p * (0.022f + 0.007f * grow) -
                r * (0.006f + 0.003f * bounce)
            translationY = p * (0.16f + 0.10f * grow) -
                r * (0.12f + 0.06f * bounce)
            shadowElevation = p * (0.40f + 0.10f * grow)
        }
        .drawWithContent {
            val pressValue = newPress.value
            val p = newMotionSmoothStep((pressValue.coerceAtLeast(0f) / 1.22f).coerceIn(0f, 1f))
            val r = newMotionSmoothStep(((-pressValue).coerceAtLeast(0f) / 0.62f).coerceIn(0f, 1f))
            val active = maxOf(p, r)

            drawContent()
            if (active <= 0.001f) return@drawWithContent

            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val maxSide = maxOf(w, h)
            val center = Offset(
                x = pressCenter.x.coerceIn(0f, 1f) * w,
                y = pressCenter.y.coerceIn(0f, 1f) * h
            )
            val radiusPx = minOf(radius.dp.toPx(), w * 0.5f, h * 0.5f)
            val cornerRadius = CornerRadius(radiusPx, radiusPx)
            val bloomPower = (touchLight * (0.30f + p * 0.62f + r * 0.18f))
                .coerceIn(0f, 48f)
            val sweepPower = (sweep * p).coerceIn(0f, 36f)
            val afterPower = (afterglow * (r * 0.70f + p * 0.18f)).coerceIn(0f, 28f)
            val minBloomRadius = 108.dp.toPx() * (0.74f + active * 0.28f)
            val bloomRadius = maxOf(
                maxSide * (0.40f + active * 0.26f + bloomPower.coerceIn(0f, 12f) * 0.018f),
                minBloomRadius
            )
            val sweepX = -0.30f + p * 1.48f

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF2FA).copy(alpha = (0.040f * bloomPower).coerceIn(0f, 0.78f)),
                        Color(0xFFE6FFFB).copy(alpha = (0.030f * bloomPower).coerceIn(0f, 0.52f)),
                        Color(0xFFFFE9C8).copy(alpha = (0.010f * bloomPower + 0.010f * afterPower).coerceIn(0f, 0.28f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = bloomRadius
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )

            if (sweepPower > 0.001f) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFEFFFFF).copy(alpha = (0.020f * bloomPower).coerceIn(0f, 0.38f)),
                            Color(0xFFFFE6B8).copy(alpha = (0.014f * sweepPower).coerceIn(0f, 0.34f)),
                            Color(0xFF91FFF2).copy(alpha = (0.014f * sweepPower).coerceIn(0f, 0.34f)),
                            Color.Transparent
                        ),
                        start = Offset(w * (sweepX - 0.44f), h * -0.08f),
                        end = Offset(w * (sweepX + 0.42f), h * 1.08f)
                    ),
                    size = Size(w, h),
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Screen
                )
            }
        }
}

private fun newMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
