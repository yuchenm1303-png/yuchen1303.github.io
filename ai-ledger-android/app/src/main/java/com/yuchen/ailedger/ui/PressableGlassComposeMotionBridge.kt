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

    val master = newMotionPower(motion.master, uiMax = 1.5f, effectiveMax = 7.5f) *
        baseMotion.coerceIn(0f, 1.4f)
    if (master <= 0.001f) return this

    val deformation = newMotionPower(motion.deformation, uiMax = 1.5f, effectiveMax = 9.5f) * master
    val touchLight = newMotionPower(motion.touchLight, uiMax = 1.8f, effectiveMax = 18f) * master
    val sweep = newMotionPower(motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val rebound = newMotionPower(motion.rebound, uiMax = 1.5f, effectiveMax = 9f) * master
    val afterglow = newMotionPower(motion.afterglow, uiMax = 1.5f, effectiveMax = 14f) * master
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

                val instant = (0.20f + deformation * 0.014f + touchLight * 0.003f)
                    .coerceIn(0.16f, 0.68f)
                val burst = (0.58f + deformation * 0.050f + touchLight * 0.008f)
                    .coerceIn(0.24f, 1.34f)
                val hold = (0.43f + deformation * 0.034f + afterglow * 0.010f)
                    .coerceIn(0.18f, 1.02f)

                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    newPress.snapTo(maxOf(newPress.value, instant))
                    newPress.animateTo(
                        burst,
                        tween(scaledDuration(122), easing = FastOutSlowInEasing)
                    )
                    newPress.animateTo(
                        hold,
                        spring(
                            dampingRatio = 0.70f,
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
                        (-0.085f - rebound * 0.012f).coerceIn(-0.58f, -0.012f),
                        tween(scaledDuration(154), easing = FastOutSlowInEasing)
                    )
                    newPress.animateTo(
                        (0.026f + afterglow * 0.0035f).coerceIn(0.014f, 0.082f),
                        spring(
                            dampingRatio = 0.58f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    newPress.animateTo(
                        0f,
                        spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
        .graphicsLayer {
            val pressPositive = newPress.value.coerceAtLeast(0f)
            val releaseNegative = (-newPress.value).coerceAtLeast(0f)
            val p = newMotionSmoothStep((pressPositive / 1.34f).coerceIn(0f, 1f))
            val r = newMotionSmoothStep((releaseNegative / 0.58f).coerceIn(0f, 1f))
            val grow = deformation.coerceIn(0f, 10f)
            val bounce = rebound.coerceIn(0f, 8f)

            transformOrigin = TransformOrigin(0.5f, 0.5f)
            scaleX = 1f + p * (0.050f + 0.014f * grow) -
                r * (0.006f + 0.002f * bounce)
            scaleY = 1f + p * (0.040f + 0.010f * grow) -
                r * (0.005f + 0.002f * bounce)
            translationX = 0f
            translationY = 0f
            shadowElevation = p * (0.46f + 0.10f * grow)
        }
        .drawWithContent {
            val pressValue = newPress.value
            val p = newMotionSmoothStep((pressValue.coerceAtLeast(0f) / 1.34f).coerceIn(0f, 1f))
            val r = newMotionSmoothStep(((-pressValue).coerceAtLeast(0f) / 0.58f).coerceIn(0f, 1f))
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
            val bloomPower = (touchLight * (0.32f + p * 0.64f + r * 0.18f))
                .coerceIn(0f, 60f)
            val sweepPower = (sweep * p).coerceIn(0f, 48f)
            val afterPower = (afterglow * (r * 0.70f + p * 0.16f)).coerceIn(0f, 36f)
            val minBloomRadius = 116.dp.toPx() * (0.76f + active * 0.30f)
            val bloomRadius = maxOf(
                maxSide * (0.44f + active * 0.28f + bloomPower.coerceIn(0f, 14f) * 0.018f),
                minBloomRadius
            )
            val sweepX = -0.28f + p * 1.44f

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF2FA).copy(alpha = (0.040f * bloomPower).coerceIn(0f, 0.84f)),
                        Color(0xFFE6FFFB).copy(alpha = (0.030f * bloomPower).coerceIn(0f, 0.56f)),
                        Color(0xFFFFE9C8).copy(alpha = (0.010f * bloomPower + 0.010f * afterPower).coerceIn(0f, 0.30f)),
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
                            Color(0xFFEFFFFF).copy(alpha = (0.020f * bloomPower).coerceIn(0f, 0.40f)),
                            Color(0xFFFFE6B8).copy(alpha = (0.014f * sweepPower).coerceIn(0f, 0.36f)),
                            Color(0xFF91FFF2).copy(alpha = (0.014f * sweepPower).coerceIn(0f, 0.36f)),
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

private fun newMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun newMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
