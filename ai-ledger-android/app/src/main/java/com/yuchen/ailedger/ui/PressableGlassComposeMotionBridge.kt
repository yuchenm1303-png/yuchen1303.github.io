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
 * 普通玻璃真实光动效统一层。
 *
 * 不修改普通玻璃静态材质，不控制上下固定高光、固定边框、底部固定暗边。
 * 这里把原始普通玻璃自带弱动态关掉，只保留这一套动态层，避免光效环和玻璃本体不同步或多层叠色。
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
        motionIntensity = 0f,
        radius = radius,
        modifier = modifier.unifiedOrdinaryGlassMotionLayer(radius = radius, role = role, motion = motion),
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
        modifier = modifier.unifiedOrdinaryGlassMotionLayer(radius = radius, role = role, motion = motion),
        role = role,
        viewportTopInset = 0.dp,
        intensity = null,
        content = content
    )
}

@Composable
private fun Modifier.unifiedOrdinaryGlassMotionLayer(
    radius: Int,
    role: GlassRole,
    motion: ComposeGlassMotionStyle
): Modifier {
    if (role == GlassRole.Shell) return this
    val master = ordinaryMotionPower(value = motion.master, uiMax = 1.5f, effectiveMax = 7f)
    if (master <= 0.001f) return this

    val deformation = ordinaryMotionPower(value = motion.deformation, uiMax = 1.5f, effectiveMax = 7f) * master
    val touchLight = ordinaryMotionPower(value = motion.touchLight, uiMax = 1.8f, effectiveMax = 13f) * master
    val prism = ordinaryMotionPower(value = motion.prism, uiMax = 1.5f, effectiveMax = 10f) * master
    val sweep = ordinaryMotionPower(value = motion.sweep, uiMax = 1.5f, effectiveMax = 10f) * master
    val rebound = ordinaryMotionPower(value = motion.rebound, uiMax = 1.5f, effectiveMax = 7f) * master
    val afterglow = ordinaryMotionPower(value = motion.afterglow, uiMax = 1.5f, effectiveMax = 9f) * master

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

                press.stop()
                lens.stop()
                sweepProgress.stop()
                val instantPress = (0.24f + deformation * 0.030f).coerceIn(0.20f, 0.58f)
                val instantLens = (0.34f + touchLight * 0.035f).coerceIn(0.24f, 0.92f)
                val instantSweep = (0.10f + sweep * 0.012f).coerceIn(0.05f, 0.32f)
                val burstTarget = (0.54f + deformation * 0.15f).coerceIn(0.24f, 1.72f)
                val holdTarget = (0.42f + deformation * 0.085f).coerceIn(0.18f, 1.18f)
                val lensTarget = (0.58f + touchLight * 0.13f).coerceIn(0.24f, 2.15f)
                val sweepTarget = (0.66f + sweep * 0.10f).coerceIn(0.20f, 2.20f)
                press.snapTo(maxOf(press.value, instantPress))
                lens.snapTo(maxOf(lens.value, instantLens))
                sweepProgress.snapTo(maxOf(sweepProgress.value, instantSweep))

                scope.launch {
                    press.animateTo(burstTarget, tween(46, easing = FastOutSlowInEasing))
                    press.animateTo(holdTarget, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium))
                }
                scope.launch {
                    lens.animateTo(lensTarget, tween(58, easing = FastOutSlowInEasing))
                }
                scope.launch {
                    sweepProgress.animateTo(sweepTarget, tween(210, easing = FastOutSlowInEasing))
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
                    val reboundTarget = (-0.12f - rebound * 0.028f).coerceIn(-1.40f, -0.025f)
                    press.animateTo(reboundTarget, tween(88, easing = FastOutSlowInEasing))
                    press.animateTo(0.040f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow))
                    press.animateTo(0f, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessLow))
                }
                scope.launch {
                    lens.stop()
                    lens.animateTo((0.16f + afterglow * 0.060f).coerceIn(0.01f, 1.45f), tween((170 + afterglow * 22f).toInt().coerceIn(170, 700), easing = FastOutSlowInEasing))
                    lens.animateTo(0f, tween((240 + afterglow * 30f).toInt().coerceIn(240, 900), easing = FastOutSlowInEasing))
                }
                scope.launch {
                    sweepProgress.stop()
                    sweepProgress.animateTo((0.08f + afterglow * 0.035f).coerceIn(0f, 0.88f), tween((180 + afterglow * 22f).toInt().coerceIn(180, 680), easing = FastOutSlowInEasing))
                    sweepProgress.animateTo(0f, tween((220 + afterglow * 28f).toInt().coerceIn(220, 780), easing = FastOutSlowInEasing))
                }
            }
        }
        .graphicsLayer {
            val p = ordinaryMotionSmoothStep(press.value.coerceAtLeast(0f).coerceIn(0f, 1.72f) / 1.72f)
            val r = ordinaryMotionSmoothStep((-press.value).coerceAtLeast(0f).coerceIn(0f, 1.40f) / 1.40f)
            val grow = deformation.coerceIn(0f, 10f)
            val bounce = rebound.coerceIn(0f, 8f)
            transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
            scaleX = 1f + p * (0.050f + 0.013f * grow) - r * (0.012f + 0.005f * bounce)
            scaleY = 1f + p * (0.038f + 0.010f * grow) - r * (0.010f + 0.004f * bounce)
            translationY = p * (0.28f + 0.16f * grow) - r * (0.22f + 0.10f * bounce)
            shadowElevation = p * (0.80f + 0.12f * grow)
        }
        .drawWithContent {
            drawContent()

            val pressValue = press.value.coerceAtLeast(0f)
            val lensValue = lens.value.coerceAtLeast(0f)
            val sweepValue = sweepProgress.value.coerceAtLeast(0f)
            val active = maxOf(pressValue, lensValue, sweepValue)
            if (active <= 0.001f) return@drawWithContent

            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val maxSide = maxOf(w, h)
            val center = Offset(pressCenter.x.coerceIn(0f, 1f) * w, pressCenter.y.coerceIn(0f, 1f) * h)
            val radiusPx = minOf(radius.dp.toPx(), w * 0.5f, h * 0.5f)
            val cornerRadius = CornerRadius(radiusPx, radiusPx)
            val rimInset = 0.50.dp.toPx().coerceAtMost(minOf(w, h) * 0.08f)
            val rimRadius = (radiusPx - rimInset).coerceAtLeast(0f)
            val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
            val pressShape = ordinaryMotionSmoothStep((pressValue + lensValue * 0.62f).coerceIn(0f, 2.65f) / 2.65f)
            val lightPower = (touchLight * (0.34f + lensValue * 0.62f)).coerceIn(0f, 42f)
            val chromaPower = (prism * (0.18f + pressValue * 0.24f + sweepValue * 0.30f)).coerceIn(0f, 28f)
            val sweepPower = (sweep * sweepValue).coerceIn(0f, 30f)
            val sweepPhase = (sweepValue / 2.20f).coerceIn(0f, 1.20f)
            val sweepX = -0.42f + sweepPhase * 1.84f
            val minBloomRadius = 112.dp.toPx() * (0.76f + pressShape * 0.28f)
            val softBloomRadius = maxOf(
                maxSide * (0.28f + 0.030f * lightPower.coerceIn(0f, 14f) + 0.22f * pressShape),
                minBloomRadius
            )

            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF1FA).copy(alpha = (0.050f * lightPower + 0.010f * chromaPower).coerceIn(0f, 0.82f)),
                        Color(0xFFE8FFFB).copy(alpha = (0.034f * lightPower).coerceIn(0f, 0.52f)),
                        Color(0xFFFFE4C7).copy(alpha = (0.012f * lightPower + 0.012f * chromaPower).coerceIn(0f, 0.30f)),
                        Color(0xFFBDEBFF).copy(alpha = (0.012f * chromaPower).coerceIn(0f, 0.28f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = softBloomRadius
                ),
                size = Size(w, h),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )

            if (sweepPower > 0.001f || chromaPower > 0.001f) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFF78DA).copy(alpha = (0.020f * chromaPower).coerceIn(0f, 0.46f)),
                            Color(0xFFFFE6B8).copy(alpha = (0.026f * lightPower + 0.016f * sweepPower).coerceIn(0f, 0.58f)),
                            Color(0xFF76FFF0).copy(alpha = (0.024f * chromaPower + 0.020f * sweepPower).coerceIn(0f, 0.54f)),
                            Color.Transparent
                        ),
                        start = Offset(w * (sweepX - 0.34f), h * -0.06f),
                        end = Offset(w * (sweepX + 0.40f), h * 1.06f)
                    ),
                    topLeft = Offset(rimInset, rimInset),
                    size = rimSize,
                    cornerRadius = CornerRadius(rimRadius, rimRadius),
                    style = Stroke((0.64.dp.toPx() + 0.10.dp.toPx() * sweepPower.coerceIn(0f, 16f)).coerceAtMost(4.8.dp.toPx())),
                    blendMode = BlendMode.Screen
                )
            }
        }
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
