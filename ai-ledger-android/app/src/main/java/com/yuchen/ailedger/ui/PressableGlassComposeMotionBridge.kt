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
 * 只把 Compose 玻璃光动效接入真实普通 PressableGlass 点击链。
 *
 * 不修改普通玻璃静态材质，不控制上下固定高光、固定边框、底部固定暗边。
 * 这里只处理手指按压/释放期间临时出现的真实动态层：胶囊膨胀、触点白光、棱彩扫光、释放回弹和余辉。
 * Shell 角色直接转发，避免触碰 OpenGL 大玻璃稳定链。
 *
 * 这个重载刻意不提供默认参数，避免和 Glass.kt 里带 intensity 默认值的原始 PressableGlass 抢解析时继续落回弱动画版本。
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
    val baseMotionGate = if (motion.master <= 0.001f) 0f else motionIntensity * motion.master.coerceIn(0f, 1f)
    PressableGlass(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = baseMotionGate,
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
    val master = composeMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 8f)
    if (master <= 0.001f) return this

    val deformation = composeMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 8f) * master
    val touchLight = composeMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 16f) * master
    val prism = composeMotionPower(value = motion.prism, uiMax = 1.5f, effectiveMax = 16f) * master
    val sweep = composeMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 16f) * master
    val rebound = composeMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 8f) * master
    val afterglow = composeMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 12f) * master

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val lens = remember { Animatable(0f) }
    val sweepProgress = remember { Animatable(0f) }
    val afterglowProgress = remember { Animatable(0f) }
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
                    if (press.value < 0.18f) press.snapTo(0.18f)
                    val burstTarget = (0.68f + deformation * 0.20f).coerceIn(0.28f, 2.20f)
                    val holdTarget = (0.52f + deformation * 0.12f).coerceIn(0.20f, 1.55f)
                    press.animateTo(burstTarget, tween(72, easing = FastOutSlowInEasing))
                    press.animateTo(holdTarget, spring(dampingRatio = 0.54f, stiffness = Spring.StiffnessMedium))
                }
                scope.launch {
                    lens.stop()
                    if (lens.value < 0.18f) lens.snapTo(0.18f)
                    val lensTarget = (0.76f + touchLight * 0.22f).coerceIn(0.24f, 3.40f)
                    lens.animateTo(lensTarget, tween(96, easing = FastOutSlowInEasing))
                }
                scope.launch {
                    sweepProgress.stop()
                    sweepProgress.snapTo(0f)
                    val sweepTarget = (0.88f + sweep * 0.15f).coerceIn(0.26f, 3.60f)
                    sweepProgress.animateTo(sweepTarget, tween(300, easing = FastOutSlowInEasing))
                }
                scope.launch {
                    afterglowProgress.stop()
                    afterglowProgress.snapTo(0f)
                    afterglowProgress.animateTo((0.38f + afterglow * 0.09f).coerceIn(0.12f, 1.80f), tween(180, easing = FastOutSlowInEasing))
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
                    val reboundTarget = (-0.18f - rebound * 0.040f).coerceIn(-2.00f, -0.030f)
                    press.animateTo(reboundTarget, tween(92, easing = FastOutSlowInEasing))
                    press.animateTo(0.055f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
                    press.animateTo(0f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow))
                }
                scope.launch {
                    lens.stop()
                    lens.animateTo((0.22f + afterglow * 0.085f).coerceIn(0.02f, 2.20f), tween((190 + afterglow * 28f).toInt().coerceIn(190, 920), easing = FastOutSlowInEasing))
                    lens.animateTo(0f, tween((300 + afterglow * 44f).toInt().coerceIn(300, 1280), easing = FastOutSlowInEasing))
                }
                scope.launch {
                    sweepProgress.stop()
                    sweepProgress.animateTo((0.12f + afterglow * 0.055f).coerceIn(0.01f, 1.50f), tween((210 + afterglow * 30f).toInt().coerceIn(210, 980), easing = FastOutSlowInEasing))
                    sweepProgress.animateTo(0f, tween((260 + afterglow * 42f).toInt().coerceIn(260, 1140), easing = FastOutSlowInEasing))
                }
                scope.launch {
                    afterglowProgress.stop()
                    afterglowProgress.animateTo((0.36f + afterglow * 0.10f).coerceIn(0.02f, 2.20f), tween(140, easing = FastOutSlowInEasing))
                    afterglowProgress.animateTo(0f, tween((520 + afterglow * 46f).toInt().coerceIn(520, 1500), easing = FastOutSlowInEasing))
                }
            }
        }
        .graphicsLayer {
            val p = composeMotionSmoothStep(press.value.coerceAtLeast(0f).coerceIn(0f, 2.2f) / 2.2f)
            val r = composeMotionSmoothStep((-press.value).coerceAtLeast(0f).coerceIn(0f, 2.0f) / 2.0f)
            val grow = deformation.coerceIn(0f, 12f)
            val bounce = rebound.coerceIn(0f, 10f)
            transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
            scaleX = 1f + p * (0.080f + 0.018f * grow) - r * (0.018f + 0.007f * bounce)
            scaleY = 1f + p * (0.058f + 0.014f * grow) - r * (0.014f + 0.006f * bounce)
            translationY = p * (0.45f + 0.22f * grow) - r * (0.36f + 0.16f * bounce)
            shadowElevation = p * (1.2f + 0.18f * grow)
        }
        .drawWithContent {
            drawContent()

            val dynamicPress = press.value.coerceAtLeast(0f)
            val lensValue = lens.value.coerceAtLeast(0f)
            val sweepValue = sweepProgress.value.coerceAtLeast(0f)
            val afterValue = afterglowProgress.value.coerceAtLeast(0f)
            val active = maxOf(dynamicPress, lensValue, sweepValue, afterValue)
            if (active <= 0.001f) return@drawWithContent

            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val maxSide = maxOf(w, h)
            val center = Offset(pressCenter.x.coerceIn(0f, 1f) * w, pressCenter.y.coerceIn(0f, 1f) * h)
            val cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
            val pressShape = composeMotionSmoothStep((dynamicPress + lensValue * 0.60f).coerceIn(0f, 3.4f) / 3.4f)
            val lightPower = (touchLight * (0.42f + lensValue * 0.72f + afterValue * 0.18f)).coerceIn(0f, 72f)
            val prismPower = (prism * (0.24f + dynamicPress * 0.34f + sweepValue * 0.46f)).coerceIn(0f, 72f)
            val sweepPower = (sweep * (0.16f + sweepValue * 0.92f)).coerceIn(0f, 72f)
            val afterPower = (afterglow * (afterValue * 0.85f + lensValue * 0.22f)).coerceIn(0f, 54f)
            val sweepPhase = (sweepValue / 3.60f).coerceIn(0f, 1.30f)
            val sweepX = -0.46f + sweepPhase * 1.92f
            val rimInset = 0.42.dp.toPx()
            val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.070f * lightPower).coerceIn(0f, 1f)),
                        Color(0xFFE8FFFF).copy(alpha = (0.044f * lightPower).coerceIn(0f, 1f)),
                        Color(0xFF89FFF3).copy(alpha = (0.018f * lightPower).coerceIn(0f, 1f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxSide * (0.26f + 0.030f * lightPower.coerceIn(0f, 18f) + 0.30f * pressShape)
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Plus
            )

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.030f * lightPower).coerceIn(0f, 0.72f)),
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
                            Color(0xFFFF67DA).copy(alpha = (0.036f * prismPower).coerceIn(0f, 1f)),
                            Color(0xFFFFF3AA).copy(alpha = (0.034f * prismPower + 0.032f * sweepPower).coerceIn(0f, 1f)),
                            Color(0xFF6CFFF1).copy(alpha = (0.044f * prismPower + 0.040f * sweepPower).coerceIn(0f, 1f)),
                            Color(0xFF9BA9FF).copy(alpha = (0.030f * prismPower).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        start = Offset(w * (sweepX - 0.40f), h * -0.10f),
                        end = Offset(w * (sweepX + 0.48f), h * 1.10f)
                    ),
                    topLeft = Offset(rimInset, rimInset),
                    size = rimSize,
                    cornerRadius = cornerRadius,
                    style = Stroke((0.60.dp.toPx() + 0.16.dp.toPx() * sweepPower.coerceIn(0f, 24f)).coerceAtMost(9.0.dp.toPx())),
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
