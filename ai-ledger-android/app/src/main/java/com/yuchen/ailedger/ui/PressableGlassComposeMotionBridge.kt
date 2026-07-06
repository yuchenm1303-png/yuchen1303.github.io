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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.launch

/**
 * 只把“Compose 玻璃光动效”接入真实普通 PressableGlass 点击链。
 *
 * 这里不修改普通玻璃静态材质，不控制上下固定高光、固定边框、底部固定暗边等“假光效”。
 * 滑块只影响按压/释放时临时出现的真实动态层：形变、触点白光、棱彩扫光、释放回弹和余辉。
 * Shell 角色直接转发，避免触碰 OpenGL 大玻璃稳定链。
 */
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
    val oldMotionGate = if (motion.master <= 0.001f) 0f else motionIntensity * motion.master.coerceIn(0f, 1f)
    PressableGlass(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = oldMotionGate,
        radius = radius,
        modifier = modifier.composeTruePressLightAndMotion(radius = radius, role = role, motion = motion),
        role = role,
        onClick = onClick,
        intensity = null,
        content = content
    )
}

@Composable
private fun Modifier.composeTruePressLightAndMotion(
    radius: Int,
    role: GlassRole,
    motion: ComposeGlassMotionStyle
): Modifier {
    if (role == GlassRole.Shell) return this
    val master = motion.master.coerceIn(0f, 8f)
    if (master <= 0.001f) return this

    val deformation = motion.deformation.coerceIn(0f, 8f) * master
    val touchLight = motion.touchLight.coerceIn(0f, 16f) * master
    val prism = motion.prism.coerceIn(0f, 16f) * master
    val sweep = motion.sweep.coerceIn(0f, 16f) * master
    val rebound = motion.rebound.coerceIn(0f, 8f) * master
    val afterglow = motion.afterglow.coerceIn(0f, 12f) * master

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val lens = remember { Animatable(0f) }
    val sweepProgress = remember { Animatable(0f) }
    var pressCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var measuredSize by remember { mutableStateOf(Size(1f, 1f)) }

    return this
        .onSizeChanged { size ->
            measuredSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
        }
        .pointerInput(master, deformation, touchLight, prism, sweep, rebound, afterglow, role) {
            awaitEachGesture {
                fun updateCenter(position: Offset) {
                    pressCenter = Offset(
                        x = (position.x / measuredSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        y = (position.y / measuredSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    )
                }
                val down = awaitFirstDown(requireUnconsumed = false)
                updateCenter(down.position)

                scope.launch {
                    press.stop()
                    val target = (0.34f + deformation * 0.12f).coerceIn(0.08f, 1.80f)
                    if (press.value < 0.12f) press.snapTo(0.12f)
                    press.animateTo(target, tween(96, easing = FastOutSlowInEasing))
                    press.animateTo(target * 0.86f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow))
                }
                scope.launch {
                    lens.stop()
                    if (lens.value < 0.10f) lens.snapTo(0.10f)
                    lens.animateTo((0.42f + touchLight * 0.14f).coerceIn(0.10f, 2.40f), tween(130, easing = FastOutSlowInEasing))
                }
                scope.launch {
                    sweepProgress.stop()
                    sweepProgress.snapTo(0f)
                    sweepProgress.animateTo((0.55f + sweep * 0.11f).coerceIn(0.20f, 2.60f), tween(360, easing = FastOutSlowInEasing))
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
                    press.animateTo((-0.060f - rebound * 0.020f).coerceIn(-1.80f, -0.010f), tween(120, easing = FastOutSlowInEasing))
                    press.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessLow))
                }
                scope.launch {
                    lens.stop()
                    lens.animateTo((0.12f + afterglow * 0.060f).coerceIn(0f, 1.60f), tween((180 + afterglow * 34f).toInt().coerceIn(180, 820), easing = FastOutSlowInEasing))
                    lens.animateTo(0f, tween((280 + afterglow * 42f).toInt().coerceIn(280, 1100), easing = FastOutSlowInEasing))
                }
                scope.launch {
                    sweepProgress.stop()
                    sweepProgress.animateTo((0.06f + afterglow * 0.040f).coerceIn(0f, 1.20f), tween((220 + afterglow * 30f).toInt().coerceIn(220, 900), easing = FastOutSlowInEasing))
                    sweepProgress.animateTo(0f, tween((260 + afterglow * 34f).toInt().coerceIn(260, 980), easing = FastOutSlowInEasing))
                }
            }
        }
        .graphicsLayer {
            val p = composeMotionSmoothStep(press.value.coerceAtLeast(0f).coerceIn(0f, 2f) / 1.8f)
            val r = composeMotionSmoothStep((-press.value).coerceAtLeast(0f).coerceIn(0f, 2f) / 1.8f)
            val deformationGain = deformation.coerceIn(0f, 10f)
            transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
            scaleX = 1f + p * 0.018f * deformationGain - r * 0.010f * rebound.coerceIn(0f, 10f)
            scaleY = 1f - p * 0.024f * deformationGain + r * 0.018f * rebound.coerceIn(0f, 10f)
            translationY = p * 1.45f * deformationGain - r * 0.90f * rebound.coerceIn(0f, 10f)
        }
        .drawWithContent {
            drawContent()

            val dynamicPress = press.value.coerceAtLeast(0f)
            val lensValue = lens.value.coerceAtLeast(0f)
            val sweepValue = sweepProgress.value.coerceAtLeast(0f)
            val active = maxOf(dynamicPress, lensValue, sweepValue)
            if (active <= 0.001f) return@drawWithContent

            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val maxSide = maxOf(w, h)
            val center = Offset(pressCenter.x.coerceIn(0f, 1f) * w, pressCenter.y.coerceIn(0f, 1f) * h)
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val lightPower = (touchLight * (0.32f + lensValue * 0.68f)).coerceIn(0f, 32f)
            val prismPower = (prism * (0.20f + dynamicPress * 0.40f + sweepValue * 0.40f)).coerceIn(0f, 32f)
            val sweepPower = (sweep * sweepValue).coerceIn(0f, 32f)
            val afterPower = (afterglow * lensValue).coerceIn(0f, 24f)
            val p = composeMotionSmoothStep((dynamicPress + lensValue * 0.78f).coerceIn(0f, 3.2f) / 3.2f)
            val sweepPhase = (sweepValue / 2.60f).coerceIn(0f, 1.25f)
            val sweepX = -0.42f + sweepPhase * 1.86f
            val rimInset = 0.54.dp.toPx()
            val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.055f * lightPower).coerceIn(0f, 1f)),
                        Color(0xFFCFFFFF).copy(alpha = (0.030f * lightPower).coerceIn(0f, 1f)),
                        Color(0xFF7DFFF0).copy(alpha = (0.010f * lightPower).coerceIn(0f, 1f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxSide * (0.20f + 0.18f * lightPower.coerceIn(0f, 8f) + 0.22f * p)
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
                            Color(0xFFFF68D9).copy(alpha = (0.030f * prismPower).coerceIn(0f, 1f)),
                            Color(0xFFFFF0A6).copy(alpha = (0.034f * prismPower + 0.028f * sweepPower).coerceIn(0f, 1f)),
                            Color(0xFF67FFF0).copy(alpha = (0.040f * prismPower + 0.034f * sweepPower).coerceIn(0f, 1f)),
                            Color(0xFF8EA3FF).copy(alpha = (0.026f * prismPower).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        start = Offset(w * (sweepX - 0.30f), h * -0.08f),
                        end = Offset(w * (sweepX + 0.34f), h * 1.06f)
                    ),
                    topLeft = Offset(rimInset, rimInset),
                    size = rimSize,
                    cornerRadius = cornerRadius,
                    style = Stroke((0.45.dp.toPx() + 0.12.dp.toPx() * sweepPower.coerceIn(0f, 18f)).coerceAtMost(7.5.dp.toPx())),
                    blendMode = BlendMode.Plus
                )
            }

            if (afterPower > 0.001f) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = (0.020f * afterPower).coerceIn(0f, 0.72f)),
                            Color(0xFFB8FFF9).copy(alpha = (0.016f * afterPower).coerceIn(0f, 0.52f)),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.50f, h * 0.42f),
                        radius = maxSide * (0.42f + afterPower.coerceIn(0f, 8f) * 0.06f)
                    ),
                    size = Size(w, h),
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Screen
                )
            }
        }
}

private fun composeMotionSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
