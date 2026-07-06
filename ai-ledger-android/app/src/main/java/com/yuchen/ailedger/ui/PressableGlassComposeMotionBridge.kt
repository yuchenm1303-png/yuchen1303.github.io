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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 普通玻璃真实光动效桥接。
 *
 * 这里不再额外绘制任何光圈、边框或扫光，避免外层 drawRoundRect 和玻璃本体坐标不同步。
 * 光效交回 Glass.kt 原生 ordinaryPressSurfaceOptics 绘制，边界和玻璃本体天然一致。
 * 这个桥接层只负责把设置页动效强度交回原生普通玻璃链，并给整块玻璃一个同步的按压形变。
 * Shell 角色直接转发，避免触碰 OpenGL 大玻璃稳定链。
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
        motionIntensity = resolvedNativeOrdinaryMotion(motionIntensity, motion),
        radius = radius,
        modifier = modifier.ordinaryGlassNativeSyncedPressTransform(role = role, motion = motion),
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
        motionIntensity = resolvedNativeOrdinaryMotion(motionIntensity, motion),
        radius = radius,
        modifier = modifier.ordinaryGlassNativeSyncedPressTransform(role = role, motion = motion),
        role = role,
        viewportTopInset = 0.dp,
        intensity = null,
        content = content
    )
}

@Composable
private fun Modifier.ordinaryGlassNativeSyncedPressTransform(
    role: GlassRole,
    motion: ComposeGlassMotionStyle
): Modifier {
    if (role == GlassRole.Shell) return this
    val master = ordinaryMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 4f)
    if (master <= 0.001f) return this

    val deformation = ordinaryMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 4f) * master
    val rebound = ordinaryMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 4f) * master

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    var pressCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var measuredSize by remember { mutableStateOf(Size(1f, 1f)) }

    return this
        .onSizeChanged { size ->
            measuredSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
        }
        .pointerInput(master, deformation, rebound, role) {
            awaitEachGesture {
                fun updateCenter(position: Offset) {
                    pressCenter = Offset(
                        x = (position.x / measuredSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        y = (position.y / measuredSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    )
                }
                val down = awaitFirstDown(requireUnconsumed = false)
                updateCenter(down.position)

                val instantPress = (0.18f + deformation * 0.025f).coerceIn(0.16f, 0.42f)
                val burstTarget = (0.44f + deformation * 0.095f).coerceIn(0.20f, 1.05f)
                val holdTarget = (0.34f + deformation * 0.060f).coerceIn(0.16f, 0.82f)

                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    press.snapTo(maxOf(press.value, instantPress))
                    press.animateTo(burstTarget, tween(76, easing = FastOutSlowInEasing))
                    press.animateTo(holdTarget, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow))
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                    if (tracked != null) {
                        updateCenter(tracked.position)
                        if (!tracked.pressed) break
                    }
                    if (event.changes.none { it.pressed }) break
                }

                scope.launch {
                    press.stop()
                    val reboundTarget = (-0.090f - rebound * 0.020f).coerceIn(-0.86f, -0.018f)
                    press.animateTo(reboundTarget, tween(120, easing = FastOutSlowInEasing))
                    press.animateTo(0.030f, spring(dampingRatio = 0.54f, stiffness = Spring.StiffnessMediumLow))
                    press.animateTo(0f, spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessLow))
                }
            }
        }
        .graphicsLayer {
            val p = ordinaryMotionSmoothStep(press.value.coerceAtLeast(0f).coerceIn(0f, 1.05f) / 1.05f)
            val r = ordinaryMotionSmoothStep((-press.value).coerceAtLeast(0f).coerceIn(0f, 0.86f) / 0.86f)
            val grow = deformation.coerceIn(0f, 7f)
            val bounce = rebound.coerceIn(0f, 6f)
            transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
            scaleX = 1f + p * (0.034f + 0.009f * grow) - r * (0.008f + 0.004f * bounce)
            scaleY = 1f + p * (0.025f + 0.007f * grow) - r * (0.007f + 0.003f * bounce)
            translationY = p * (0.20f + 0.12f * grow) - r * (0.18f + 0.08f * bounce)
            shadowElevation = p * (0.54f + 0.10f * grow)
        }
}

private fun resolvedNativeOrdinaryMotion(baseMotion: Float, motion: ComposeGlassMotionStyle): Float {
    val master = ordinaryMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 1.35f)
    val touch = ordinaryMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 1.20f)
    val sweep = ordinaryMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 1.18f)
    val afterglow = ordinaryMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 1.12f)
    val gain = (0.76f + touch * 0.12f + sweep * 0.08f + afterglow * 0.04f).coerceIn(0.20f, 1.24f)
    return (baseMotion * master * gain).coerceIn(0f, 1f)
}

private fun ordinaryMotionPower(value: Float, uiMax: Float, effectiveMax: Float): Float {
    val clean = value.coerceAtLeast(0f)
    if (clean <= 1f) return clean
    val span = (uiMax - 1f).coerceAtLeast(0.001f)
    val t = ((clean - 1f) / span).coerceIn(0f, 1f)
    return 1f + t * (effectiveMax - 1f)
}

private fun ordinaryMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
