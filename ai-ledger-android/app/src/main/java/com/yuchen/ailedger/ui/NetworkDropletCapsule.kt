package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch

private const val NetworkPrismStrength = 2.76f
private const val NetworkPurpleWhiteLight = 0.67f
private const val NetworkActiveGlow = 0.53f

@Composable
fun NetworkDropletCapsule(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val pressAnim = remember { Animatable(0f) }
    val activeAnim = remember { Animatable(if (state.onlineEnabled) 1f else 0f) }
    val shimmerAnim = remember { Animatable(0.42f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableStateOf(1f) }
    var pressX by remember { mutableStateOf(0.82f) }

    LaunchedEffect(state.onlineEnabled) {
        activeAnim.animateTo(
            targetValue = if (state.onlineEnabled) 1f else 0f,
            animationSpec = if (state.onlineEnabled) {
                spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)
            } else {
                tween(460, easing = FastOutSlowInEasing)
            }
        )
    }

    LaunchedEffect(state.onlineEnabled) {
        if (state.onlineEnabled) {
            while (true) {
                shimmerAnim.animateTo(
                    targetValue = 0.18f + Math.random().toFloat() * 0.82f,
                    animationSpec = tween(1100, easing = FastOutSlowInEasing)
                )
            }
        } else {
            shimmerAnim.animateTo(0.24f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    val active = activeAnim.value.coerceIn(0f, 1f)
    val press = pressAnim.value.coerceIn(-0.22f, 1.18f)
    val pressPositive = press.coerceAtLeast(0f)
    val recoil = (-press).coerceAtLeast(0f)
    val shimmer = shimmerAnim.value.coerceIn(0f, 1f)
    val lightEnergy = (active * NetworkActiveGlow * (0.86f + shimmer * 0.16f) + pressPositive * 0.22f).coerceIn(0f, 1.28f)
    val phase = (pressX * 0.62f + shimmer * 0.22f + lightEnergy * 0.18f).coerceIn(0f, 1.6f)

    Box(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressX.coerceIn(0f, 1f), 0.5f)
                scaleX = 1f + pressPositive * 0.055f + active * 0.010f - recoil * 0.012f
                scaleY = 1f - pressPositive * 0.070f + recoil * 0.024f
                translationY = pressPositive * 3.6f - recoil * 1.2f
                translationX = (pressX - 0.5f) * pressPositive * 4.8f
            }
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1).toFloat() }
            .clip(RoundedCornerShape(999.dp))
            .pointerInput(enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressX = (down.position.x / widthPx.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                    if (!enabled) return@awaitEachGesture
                    scope.launch {
                        pressAnim.stop()
                        pressAnim.snapTo(0.20f)
                        pressAnim.animateTo(1.10f, tween(135, easing = FastOutSlowInEasing))
                        pressAnim.animateTo(0.90f, spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow))
                    }
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            pressX = (tracked.position.x / widthPx.coerceAtLeast(1f)).coerceIn(0.08f, 0.92f)
                            released = !tracked.pressed
                        } else {
                            released = event.changes.none { it.pressed }
                        }
                    }
                    onClick()
                    scope.launch {
                        pressAnim.stop()
                        pressAnim.animateTo(-0.18f, tween(120, easing = FastOutSlowInEasing))
                        pressAnim.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessLow))
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        NetworkDropletCanvas(active, lightEnergy, pressPositive, shimmer, phase, Modifier.fillMaxSize())
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NetworkDropDot(active = state.onlineEnabled, energy = lightEnergy)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("联网", color = Color.White.copy(alpha = if (state.onlineEnabled) 0.74f else 0.50f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (state.onlineEnabled) "已开启" else "已关闭", color = Color.White.copy(alpha = if (state.onlineEnabled) 0.96f else 0.76f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NetworkDropletCanvas(active: Float, lightEnergy: Float, press: Float, shimmer: Float, phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.height / 2f
        val prism = NetworkPrismStrength
        val purple = NetworkPurpleWhiteLight
        val baseAlpha = if (active > 0.01f) 0.20f + active * 0.10f else 0.105f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = baseAlpha + purple * lightEnergy * 0.06f),
                    Color(0xFFBFEAFF).copy(alpha = 0.12f + active * 0.18f),
                    Color(0xFF405B9E).copy(alpha = 0.22f + active * 0.08f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(r, r)
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f + press * 0.13f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.08f),
                radius = size.width * 0.48f
            ),
            cornerRadius = CornerRadius(r, r),
            blendMode = BlendMode.Screen
        )
        if (lightEnergy > 0.01f) {
            val sweep = (phase - phase.toInt()) * size.width * 0.60f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF2E93).copy(alpha = lightEnergy * prism * 0.030f),
                        Color(0xFFFFD84D).copy(alpha = lightEnergy * prism * 0.026f),
                        Color(0xFF55FFD6).copy(alpha = lightEnergy * prism * 0.038f),
                        Color(0xFF4F89FF).copy(alpha = lightEnergy * prism * 0.034f),
                        Color(0xFFC05CFF).copy(alpha = lightEnergy * prism * 0.030f)
                    ),
                    start = Offset(-size.width * 0.30f + sweep, 0f),
                    end = Offset(size.width * 1.05f + sweep, size.height)
                ),
                cornerRadius = CornerRadius(r, r),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF4B5).copy(alpha = lightEnergy * 0.10f), Color(0xFF63FFE4).copy(alpha = lightEnergy * 0.08f), Color.Transparent),
                    center = Offset(size.width * (0.70f + shimmer * 0.10f), size.height * 0.38f),
                    radius = size.width * 0.28f
                ),
                cornerRadius = CornerRadius(r, r),
                blendMode = BlendMode.Plus
            )
        }
        drawRoundRect(Color.White.copy(alpha = 0.18f + lightEnergy * 0.08f), cornerRadius = CornerRadius(r, r), style = Stroke(width = 1.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(Color.Black.copy(alpha = 0.10f - active * 0.035f), cornerRadius = CornerRadius(r, r), blendMode = BlendMode.Multiply)
    }
}

@Composable
private fun NetworkDropDot(active: Boolean, energy: Float) {
    Canvas(Modifier.width(12.dp).height(12.dp)) {
        val a = if (active) 0.78f + energy * 0.18f else 0.36f
        drawCircle(Color.White.copy(alpha = a), radius = size.minDimension * 0.34f, center = center, blendMode = BlendMode.Screen)
        if (active) drawCircle(Color(0xFF7EFFE7).copy(alpha = energy * 0.50f), radius = size.minDimension * 0.50f, center = center, blendMode = BlendMode.Plus)
    }
}
