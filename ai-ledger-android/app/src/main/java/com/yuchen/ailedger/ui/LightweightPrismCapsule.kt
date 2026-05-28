package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithCache
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
import kotlinx.coroutines.launch

internal data class LightweightPrismCapsuleSpec(
    val radius: Float = 32.4f,
    val surfaceAlpha: Float = 0.050f,
    val rimAlpha: Float = 0.78f,
    val rimWidth: Float = 0.52f,
    val topHighlight: Float = 0.075f,
    val topHighlightHeight: Float = 0.16f,
    val innerRimAlpha: Float = 0.45f,
    val bottomDepth: Float = 0.018f,
    val cornerCatchlight: Float = 0.42f,
    val pressGlow: Float = 0.55f,
    val pressEdgeBoost: Float = 0.70f,
    val pressSweep: Float = 0.70f,
    val pressDarken: Float = 0.22f,
    val pressElasticity: Float = 1.20f,
    val rainbowRimAlpha: Float = 0.70f,
    val rainbowRimWidth: Float = 1.40f,
    val rainbowPressEdge: Float = 0.80f,
    val rainbowSweepAlpha: Float = 0.80f,
    val rainbowCornerAlpha: Float = 0.38f,
    val rainbowSaturation: Float = 1.00f
)

internal object LightweightPrismCapsuleDefaults {
    val LabMax = LightweightPrismCapsuleSpec()
}

@Composable
internal fun LightweightPrismCapsule(
    modifier: Modifier = Modifier,
    spec: LightweightPrismCapsuleSpec = LightweightPrismCapsuleDefaults.LabMax,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(press: Float) -> Unit
) {
    val shape = RoundedCornerShape(spec.radius.dp)
    val pressAnim = remember { Animatable(0f) }
    val sweepAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var pressCenter by remember { mutableStateOf(Offset(0.50f, 0.50f)) }
    var pressSize by remember { mutableStateOf(Size(1f, 1f)) }
    val press = pressAnim.value.coerceIn(0f, 1.12f)
    val sweep = sweepAnim.value.coerceIn(0f, 1.18f)
    val p = prismSmooth(press.coerceIn(0f, 1f))
    val rebound = prismSmooth(((sweep - 0.68f) / 0.50f).coerceIn(0f, 1f)) * (1f - p)
    val elastic = spec.pressElasticity.coerceIn(0f, 1.2f)

    Box(
        modifier = modifier
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + p * 0.018f * elastic - rebound * 0.004f * elastic
                scaleY = 1f - p * 0.026f * elastic + rebound * 0.010f * elastic
                translationY = p * 2.00f * elastic - rebound * 0.70f * elastic
            }
            .onSizeChanged { size ->
                pressSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
            }
            .pointerInput(enabled, onClick) {
                awaitEachGesture {
                    fun updatePress(position: Offset) {
                        pressCenter = Offset(
                            (position.x / pressSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                            (position.y / pressSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        )
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePress(down.position)
                    if (enabled) {
                        scope.launch {
                            pressAnim.stop()
                            if (pressAnim.value < 0.18f) pressAnim.snapTo(0.18f)
                            pressAnim.animateTo(0.92f, tween(132, easing = FastOutSlowInEasing))
                            pressAnim.animateTo(0.78f, spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow))
                        }
                        scope.launch {
                            sweepAnim.stop()
                            sweepAnim.snapTo(0f)
                            sweepAnim.animateTo(0.42f, tween(180, easing = FastOutSlowInEasing))
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updatePress(tracked.position)
                            if (!tracked.pressed) break
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (enabled) {
                        onClick?.invoke()
                        scope.launch {
                            pressAnim.stop()
                            pressAnim.animateTo(0f, tween(460, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            sweepAnim.stop()
                            sweepAnim.animateTo(1.18f, tween(520, easing = FastOutSlowInEasing))
                            sweepAnim.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
            .clip(shape)
            .lightweightPrismCapsuleSurface(
                spec = spec,
                press = p,
                sweep = sweep,
                pressCenter = pressCenter
            )
    ) {
        content(p)
    }
}

@Composable
internal fun LightweightPrismCapsuleLabPreview(
    spec: LightweightPrismCapsuleSpec,
    modifier: Modifier = Modifier
) {
    LightweightPrismCapsule(
        modifier = modifier
            .fillMaxWidth()
            .height(106.dp)
            .padding(horizontal = 8.dp, vertical = 13.dp),
        spec = spec,
        enabled = true
    ) { press ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF93FFF1).copy(alpha = 0.54f + 0.34f * press))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("轻量玻璃 / Prism Press Lab", color = Color.White.copy(alpha = 0.96f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("棱彩边缘 · 按压高光 · 释放扫光", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private fun Modifier.lightweightPrismCapsuleSurface(
    spec: LightweightPrismCapsuleSpec,
    press: Float,
    sweep: Float,
    pressCenter: Offset
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val corner = CornerRadius(spec.radius.dp.toPx(), spec.radius.dp.toPx())
    val rimInset = 0.62.dp.toPx()
    val innerInset = 1.72.dp.toPx()
    val bodySize = Size(w, h)
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val innerSize = Size((w - innerInset * 2f).coerceAtLeast(1f), (h - innerInset * 2f).coerceAtLeast(1f))
    val center = Offset(pressCenter.x.coerceIn(0f, 1f) * w, pressCenter.y.coerceIn(0f, 1f) * h)
    val topNear = (1f - pressCenter.y / 0.42f).coerceIn(0f, 1f) * press
    val bottomNear = (1f - (1f - pressCenter.y) / 0.42f).coerceIn(0f, 1f) * press
    val leftNear = (1f - pressCenter.x / 0.42f).coerceIn(0f, 1f) * press
    val rightNear = (1f - (1f - pressCenter.x) / 0.42f).coerceIn(0f, 1f) * press
    val sweepT = prismSmooth(sweep.coerceIn(0f, 1f))
    val sweepX = -0.28f + sweepT * 1.56f
    val sat = spec.rainbowSaturation.coerceIn(0f, 1f)

    fun prism(color: Color, alpha: Float): Color = color.copy(alpha = (alpha * (0.32f + sat * 0.68f)).coerceIn(0f, 1f))
    fun prismBandBrush(start: Offset, end: Offset, strength: Float): Brush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            prism(Color(0xFF68F7FF), strength * 0.24f),
            prism(Color(0xFFFF7CE1), strength * 0.28f),
            Color.White.copy(alpha = (strength * 0.18f).coerceIn(0f, 1f)),
            prism(Color(0xFFFFE785), strength * 0.22f),
            prism(Color(0xFF7BFF9E), strength * 0.20f),
            prism(Color(0xFF6FA8FF), strength * 0.18f),
            Color.Transparent
        ),
        start = start,
        end = end
    )

    val surface = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = spec.surfaceAlpha.coerceIn(0f, 0.20f)),
            Color(0xFFD9F1FF).copy(alpha = spec.surfaceAlpha.coerceIn(0f, 0.20f) * 0.52f),
            Color(0xFFEAF7FF).copy(alpha = spec.surfaceAlpha.coerceIn(0f, 0.20f) * 0.20f),
            Color(0xFF000816).copy(alpha = spec.bottomDepth.coerceIn(0f, 0.35f) * 0.36f)
        ),
        0f,
        h
    )
    val centerMist = Brush.radialGradient(
        listOf(
            Color(0xFFEAF7FF).copy(alpha = (0.040f + spec.surfaceAlpha * 0.70f).coerceIn(0f, 0.16f)),
            Color(0xFF9EDBFF).copy(alpha = (0.018f + spec.surfaceAlpha * 0.22f).coerceIn(0f, 0.08f)),
            Color.Transparent
        ),
        Offset(w * 0.50f, h * 0.50f),
        maxSide * 0.64f
    )
    val topLens = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = spec.topHighlight.coerceIn(0f, 0.5f)),
            Color(0xFFE8FFFF).copy(alpha = spec.topHighlight.coerceIn(0f, 0.5f) * 0.36f),
            Color.Transparent
        ),
        0f,
        h * spec.topHighlightHeight.coerceIn(0.05f, 0.60f)
    )
    val bottomShade = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Transparent, Color(0xFF020815).copy(alpha = spec.bottomDepth.coerceIn(0f, 0.35f))),
        h * 0.52f,
        h
    )
    val topHairline = Brush.horizontalGradient(
        listOf(
            Color.Transparent,
            Color(0xFFDFFFFF).copy(alpha = spec.rimAlpha.coerceIn(0f, 1f) * 0.26f + spec.topHighlight * 0.24f),
            Color.White.copy(alpha = spec.rimAlpha.coerceIn(0f, 1f) * 0.30f + spec.topHighlight * 0.36f),
            Color.Transparent
        ),
        0f,
        w
    )
    val innerRim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = spec.innerRimAlpha.coerceIn(0f, 0.7f) * 0.70f),
            Color.Transparent,
            Color(0xFF00091E).copy(alpha = spec.bottomDepth.coerceIn(0f, 0.35f) * 0.68f),
            Color.White.copy(alpha = spec.innerRimAlpha.coerceIn(0f, 0.7f) * 0.22f)
        ),
        Offset(w * 0.08f, 0f),
        Offset(w * 0.92f, h)
    )
    val cornerLight = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.90f),
            Color(0xFFCFFFFF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.32f),
            Color.Transparent
        ),
        Offset(w * 0.055f, h * 0.045f),
        maxSide * 0.30f
    )
    val rightCornerLight = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.58f),
            Color(0xFFB7F7FF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.22f),
            Color.Transparent
        ),
        Offset(w * 0.94f, h * 0.10f),
        maxSide * 0.24f
    )
    val lowerCornerLight = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.28f),
            Color(0xFFC7E9FF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.12f),
            Color.Transparent
        ),
        Offset(w * 0.92f, h * 0.88f),
        maxSide * 0.26f
    )
    val rainbowCorner = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = spec.rainbowCornerAlpha.coerceIn(0f, 0.5f) * 0.24f),
            prism(Color(0xFFFF87E5), spec.rainbowCornerAlpha * 0.18f),
            prism(Color(0xFF79F8FF), spec.rainbowCornerAlpha * 0.22f),
            Color.Transparent
        ),
        Offset(w * 0.10f, h * 0.10f),
        maxSide * 0.24f
    )
    val pressureDark = Brush.radialGradient(
        listOf(
            Color.Transparent,
            Color(0xFF071B3D).copy(alpha = spec.pressDarken.coerceIn(0f, 0.4f) * 0.34f * press),
            Color(0xFF01040C).copy(alpha = spec.pressDarken.coerceIn(0f, 0.4f) * press)
        ),
        center,
        maxSide * (0.72f + 0.12f * press)
    )
    val prismPressLight = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = spec.pressGlow.coerceIn(0f, 0.9f) * 0.20f * press),
            prism(Color(0xFF6AF7FF), spec.pressGlow * 0.26f * press),
            prism(Color(0xFFFF7FE0), spec.pressGlow * 0.24f * press),
            prism(Color(0xFFFFE789), spec.pressGlow * 0.18f * press),
            prism(Color(0xFF7CFFA0), spec.pressGlow * 0.16f * press),
            Color.Transparent
        ),
        center,
        maxSide * (0.30f + 0.22f * press)
    )
    val prismLocalEdge = Brush.linearGradient(
        listOf(
            Color.Transparent,
            prism(Color(0xFF6BF7FF), spec.rainbowPressEdge * 0.30f * press + spec.pressEdgeBoost * 0.10f * press),
            prism(Color(0xFFFF7FE0), spec.rainbowPressEdge * 0.28f * press),
            Color.White.copy(alpha = spec.pressEdgeBoost.coerceIn(0f, 1f) * 0.10f * press),
            prism(Color(0xFFFFE889), spec.rainbowPressEdge * 0.22f * press),
            prism(Color(0xFF7DFFA0), spec.rainbowPressEdge * 0.20f * press),
            Color.Transparent
        ),
        Offset(center.x - w * 0.24f, center.y - h * 0.74f),
        Offset(center.x + w * 0.22f, center.y + h * 0.74f)
    )
    val rimBandPower = spec.rainbowRimAlpha.coerceIn(0f, 1f)
    val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)
    val rimBandMain = prismBandBrush(
        start = Offset(w * (sweepX - 0.22f), h * -0.06f),
        end = Offset(w * (sweepX + 0.28f), h * 1.04f),
        strength = rimBandPower * (0.72f + 0.28f * pressBandBoost)
    )
    val rimBandCounter = prismBandBrush(
        start = Offset(w * (1.12f - sweepX), h * 0.02f),
        end = Offset(w * (0.54f - sweepX), h * 1.00f),
        strength = rimBandPower * 0.52f * (0.70f + 0.30f * pressBandBoost)
    )
    val rimBandTop = prismBandBrush(
        start = Offset(w * (sweepX - 0.18f), h * 0.02f),
        end = Offset(w * (sweepX + 0.34f), h * 0.26f),
        strength = rimBandPower * 0.42f * (0.68f + 0.32f * topNear)
    )
    val prismSweep = prismBandBrush(
        start = Offset(w * (sweepX - 0.24f), h * -0.04f),
        end = Offset(w * (sweepX + 0.30f), h * 1.04f),
        strength = spec.rainbowSweepAlpha.coerceIn(0f, 1f) * sweep + spec.pressSweep.coerceIn(0f, 1f) * 0.12f * sweep
    )

    onDrawWithContent {
        drawRoundRect(brush = surface, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(brush = centerMist, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(brush = topLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(brush = bottomShade, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
        if (press > 0.001f) {
            drawRoundRect(brush = pressureDark, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
            drawRoundRect(brush = prismPressLight, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        }
        drawContent()
        drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.78.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(brush = innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.58.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(brush = cornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.86.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(brush = rightCornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.74.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(brush = lowerCornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.62.dp.toPx()), blendMode = BlendMode.Screen)
        if (spec.rainbowCornerAlpha > 0.001f) {
            drawRoundRect(brush = rainbowCorner, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.64.dp.toPx()), blendMode = BlendMode.Screen)
        }
        drawRoundRect(brush = rimBandMain, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth + spec.rainbowRimWidth * 0.92f).dp.toPx()), blendMode = BlendMode.Plus)
        drawRoundRect(brush = rimBandCounter, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth * 0.72f + spec.rainbowRimWidth * 0.58f).dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(brush = rimBandTop, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth * 0.46f + spec.rainbowRimWidth * 0.42f).dp.toPx()), blendMode = BlendMode.Plus)
        if (press > 0.001f) {
            val localEdgeAlpha = (topNear + bottomNear + leftNear + rightNear).coerceIn(0.16f, 1f)
            drawRoundRect(brush = prismLocalEdge, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.76f + 0.96f * localEdgeAlpha).dp.toPx()), blendMode = BlendMode.Plus)
        }
        if (sweep > 0.001f) {
            drawRoundRect(brush = prismSweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.58f + 0.68f * sweep).dp.toPx()), blendMode = BlendMode.Plus)
        }
    }
}

private fun prismSmooth(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
