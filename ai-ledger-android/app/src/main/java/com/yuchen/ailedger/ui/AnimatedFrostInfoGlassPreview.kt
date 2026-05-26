package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private val FrostPrismPressEasing = CubicBezierEasing(0.12f, 0.00f, 0.08f, 1.00f)
private val FrostPrismReleaseEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)

@Composable
fun AnimatedFrostInfoGlassPreview(
    state: AssistantUiState,
    modifier: Modifier = Modifier
) {
    val motionEnabled = state.quality.enableMotion && state.motionIntensity > 0.02f
    val transition = rememberInfiniteTransition(label = "animated-frost-info-glass")
    val phaseA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8600, easing = LinearEasing), RepeatMode.Restart),
        label = "frost-prism-phase-a"
    )
    val phaseB by transition.animateFloat(
        initialValue = 0.31f,
        targetValue = 1.31f,
        animationSpec = infiniteRepeatable(tween(12700, easing = LinearEasing), RepeatMode.Restart),
        label = "frost-prism-phase-b"
    )
    val pressAnim = remember { Animatable(0f) }
    val afterglowAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var panelSize by remember { mutableStateOf(Size(1f, 1f)) }
    var pressCenter by remember { mutableStateOf(Offset(0.52f, 0.45f)) }

    val pressValue = pressAnim.value.coerceIn(-0.22f, 1.16f)
    val press = pressValue.coerceAtLeast(0f)
    val recoil = (-pressValue).coerceAtLeast(0f)
    val afterglow = afterglowAnim.value.coerceIn(0f, 1f)
    val energy = (press * 0.92f + afterglow * 0.46f).coerceIn(0f, 1.15f)
    val phase1 = if (motionEnabled) phaseA else 0.18f
    val phase2 = if (motionEnabled) phaseB else 0.62f
    val prismStrength = (0.72f + state.motionIntensity.coerceIn(0f, 1.4f) * 0.18f).coerceIn(0.60f, 1.10f)
    val radiusDp = 34.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(vertical = 3.dp)
            .onSizeChanged { panelSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat()) }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + press * 0.020f - recoil * 0.008f
                scaleY = 1f - press * 0.034f + recoil * 0.014f
                translationY = press * 3.0f - recoil * 1.4f
                shadowElevation = press * 0.80f
            }
            .pointerInput(motionEnabled, state.motionIntensity) {
                if (!motionEnabled) return@pointerInput
                awaitEachGesture {
                    fun updateCenter(position: Offset) {
                        pressCenter = Offset(
                            x = (position.x / panelSize.width.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f),
                            y = (position.y / panelSize.height.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateCenter(down.position)
                    scope.launch {
                        afterglowAnim.stop()
                        afterglowAnim.snapTo(0f)
                    }
                    scope.launch {
                        pressAnim.stop()
                        if (pressAnim.value < 0.20f) pressAnim.snapTo(0.20f)
                        pressAnim.animateTo(1.00f, tween(155, easing = FrostPrismPressEasing))
                        pressAnim.animateTo(0.76f, spring(dampingRatio = 0.66f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
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
                        pressAnim.stop()
                        pressAnim.animateTo(-0.12f, tween(140, easing = FrostPrismReleaseEasing))
                        pressAnim.animateTo(0.035f, spring(dampingRatio = 0.46f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
                        pressAnim.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                    }
                    scope.launch {
                        afterglowAnim.stop()
                        afterglowAnim.snapTo(0.86f)
                        afterglowAnim.animateTo(0f, tween(860, easing = FastOutSlowInEasing))
                    }
                }
            }
            .clip(RoundedCornerShape(radiusDp)),
        contentAlignment = Alignment.Center
    ) {
        FrostInfoGlassPanel(
            modifier = Modifier.fillMaxSize(),
            radius = 34f,
            backdropAlpha = 1f,
            frostAlpha = 0.055f + energy * 0.030f,
            dimAlpha = 0.035f + press * 0.026f
        ) {}
        FrostPrismSurface(
            radius = 34f,
            press = press,
            recoil = recoil,
            afterglow = afterglow,
            center = pressCenter,
            phaseA = phase1,
            phaseB = phase2,
            prismStrength = prismStrength,
            modifier = Modifier.fillMaxSize()
        )
        FrostInfoContent(energy = energy, press = press, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun FrostInfoContent(
    energy: Float,
    press: Float,
    modifier: Modifier = Modifier
) {
    val textAlpha = (0.72f + energy * 0.20f).coerceIn(0.68f, 0.96f)
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("雾面信息玻璃 · 动效版", color = Color.White.copy(alpha = textAlpha), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text("胶囊按压、棱彩边缘、柔和膜层扫光；纯 Compose 表层，不接 OpenGL。", color = Color.White.copy(alpha = textAlpha * 0.56f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FrostAnimatedMetric("边缘棱彩", "ON", textAlpha, press, Modifier.weight(1f))
            FrostAnimatedMetric("雾面呼吸", "柔和", textAlpha, press, Modifier.weight(1f))
            FrostAnimatedMetric("GL 隔离", "安全", textAlpha, press, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FrostAnimatedMetric(label: String, value: String, alpha: Float, press: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Color.White.copy(alpha = alpha * 0.50f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = Color.White.copy(alpha = (alpha + press * 0.08f).coerceIn(0f, 1f)), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FrostPrismSurface(
    radius: Float,
    press: Float,
    recoil: Float,
    afterglow: Float,
    center: Offset,
    phaseA: Float,
    phaseB: Float,
    prismStrength: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val r = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
        val tau = (PI * 2.0).toFloat()
        val a = phaseA * tau
        val b = phaseB * tau
        val breath = ((sin(a * 0.72f + b * 0.22f) + 1f) * 0.5f).coerceIn(0f, 1f)
        val energy = (press * 0.92f + afterglow * 0.50f + recoil * 0.28f).coerceIn(0f, 1.20f)
        val cx = center.x.coerceIn(0.05f, 0.95f) * w
        val cy = center.y.coerceIn(0.08f, 0.92f) * h
        val driftX = (0.52f + 0.34f * cos(a)).coerceIn(0.08f, 0.92f) * w
        val driftY = (0.42f + 0.24f * sin(b * 0.86f + 0.75f)).coerceIn(0.08f, 0.90f) * h
        val filmPower = prismStrength * (0.72f + breath * 0.28f + energy * 0.36f)
        val rimInset = 0.78.dp.toPx()
        val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
        val rimRadius = CornerRadius((radius.dp.toPx() - rimInset).coerceAtLeast(0f), (radius.dp.toPx() - rimInset).coerceAtLeast(0f))
        val sweep = ((phaseA + phaseB * 0.31f) - (phaseA + phaseB * 0.31f).toInt())

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF6ED9).copy(alpha = 0.060f * filmPower),
                    Color(0xFFFFD66E).copy(alpha = 0.042f * filmPower),
                    Color(0xFF64FFF0).copy(alpha = 0.058f * filmPower),
                    Color.Transparent
                ),
                center = Offset(driftX, driftY),
                radius = maxOf(w, h) * (0.74f + breath * 0.20f)
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.080f * energy),
                    Color(0xFF7CFFF1).copy(alpha = 0.075f * energy),
                    Color(0xFFFF7DE2).copy(alpha = 0.052f * energy),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = maxOf(w, h) * (0.42f + energy * 0.18f)
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.115f + energy * 0.045f),
                    Color(0xFFB8FFF4).copy(alpha = 0.030f + energy * 0.030f),
                    Color.Transparent,
                    Color(0xFF050B18).copy(alpha = 0.050f + press * 0.050f)
                ),
                startY = 0f,
                endY = h
            ),
            size = Size(w, h),
            cornerRadius = r,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF5ED8).copy(alpha = 0.105f * filmPower),
                    Color(0xFFFFE087).copy(alpha = 0.088f * filmPower),
                    Color(0xFF72FFF0).copy(alpha = 0.110f * filmPower),
                    Color(0xFF8DA2FF).copy(alpha = 0.086f * filmPower),
                    Color.Transparent
                ),
                start = Offset(w * (sweep - 0.54f), h * -0.12f),
                end = Offset(w * (sweep + 0.38f), h * 1.06f)
            ),
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = rimRadius,
            style = Stroke(width = 1.05.dp.toPx() + energy * 0.75.dp.toPx()),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f + energy * 0.12f),
                    Color(0xFF7CFFF1).copy(alpha = 0.060f + energy * 0.060f),
                    Color.Transparent,
                    Color(0xFFFF8BE8).copy(alpha = 0.035f + energy * 0.040f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(1.45.dp.toPx(), 1.45.dp.toPx()),
            size = Size(w - 2.90.dp.toPx(), h - 2.90.dp.toPx()),
            cornerRadius = CornerRadius((radius.dp.toPx() - 1.45.dp.toPx()).coerceAtLeast(0f), (radius.dp.toPx() - 1.45.dp.toPx()).coerceAtLeast(0f)),
            style = Stroke(width = 0.45.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}
