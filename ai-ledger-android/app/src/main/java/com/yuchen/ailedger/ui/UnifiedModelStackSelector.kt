package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatModel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun UnifiedParentModelStackSelector(
    state: AssistantUiState,
    expanded: Boolean,
    modifier: Modifier,
    onToggleExpanded: () -> Unit,
    onSelected: (ChatModel) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val models = ChatModel.entries
        val gap = 10.dp
        val rowStep = 74.dp
        val collapsedHeight = 56.dp
        val expandedHeight = 64.dp
        val reservedGap = 8.dp
        val collapsedWidth = (maxWidth - reservedGap) * 0.642f
        val halfWidth = (maxWidth - gap) / 2f
        val selectedModel = state.selectedModel
        val behindModels = models.filter { it != selectedModel }
        val visuals = mutableListOf<UnifiedModelCardVisual>()

        Box(
            Modifier
                .fillMaxSize()
                .drawUnifiedModelStackPrism(visuals)
        ) {
            models.forEachIndexed { index, model ->
                val selected = model == selectedModel
                val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
                val expandedX = when (index) { 1, 3 -> halfWidth + gap else -> 0.dp }
                val expandedY = when (index) { 2, 3 -> rowStep; 4 -> rowStep * 2f; else -> 0.dp }
                val expandedWidth = if (index == 4) maxWidth else halfWidth
                val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
                val collapsedY = if (selected) 0.dp else (stackRank * 1.6f).dp
                val collapsedAlpha = if (selected) 1f else 0.48f + (4 - stackRank).coerceAtLeast(0) * 0.080f
                val staggerRank = when (index) { 0 -> 0; 2 -> 1; 1 -> 2; 3 -> 3; else -> 4 }
                val z = if (expanded) 30f - index else if (selected) 50f else 40f - stackRank
                val cardProgress by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (expanded) 460 else 280,
                        delayMillis = if (expanded) staggerRank * 24 else (ChatModel.entries.lastIndex - staggerRank) * 12,
                        easing = FastOutSlowInEasing
                    ),
                    label = "unified-model-card-progress-${model.id}"
                )
                val eased = unifiedModelStackEase(cardProgress)
                val currentWidth = unifiedLerpDp(collapsedWidth, expandedWidth, eased)
                val currentHeight = unifiedLerpDp(collapsedHeight, expandedHeight, eased)
                val currentAlpha = unifiedLerpFloat(collapsedAlpha, 1f, eased)
                val startX = with(density) { collapsedX.toPx() }
                val startY = with(density) { collapsedY.toPx() }
                val endX = with(density) { expandedX.toPx() }
                val endY = with(density) { expandedY.toPx() }
                val dx = endX - startX
                val dy = endY - startY
                val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val speedPulse = unifiedModelStackSpeedPulse(cardProgress)
                val unitX = dx / distance
                val unitY = dy / distance
                val liftPx = -with(density) { (7.dp + (staggerRank * 0.7f).dp).toPx() } * speedPulse
                val curveX = startX + dx * eased
                val curveY = startY + dy * eased + liftPx
                val brake = unifiedModelStackArrivalBrake(cardProgress)
                val returnBrake = unifiedModelStackReturnBrake(cardProgress)
                val overshootPx = with(density) { 4.6.dp.toPx() } * brake - with(density) { 2.6.dp.toPx() } * returnBrake
                val tx = curveX + unitX * overshootPx
                val ty = curveY + unitY * overshootPx
                val settled = cardProgress < 0.025f || cardProgress > 0.985f
                val capsuleScaleX = unifiedModelStackCapsuleScaleX(cardProgress)
                val capsuleScaleY = unifiedModelStackCapsuleScaleY(cardProgress)
                val selectedPulse by animateFloatAsState(
                    targetValue = if (selected && settled) 1.010f else 1f,
                    animationSpec = spring(dampingRatio = 0.60f, stiffness = Spring.StiffnessMediumLow),
                    label = "unified-model-card-selected-pulse-${model.id}"
                )
                val interactionSource = remember(model.id) { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val pressProgress by animateFloatAsState(
                    targetValue = if (pressed && !state.isSending) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
                    label = "unified-model-card-press-${model.id}"
                )
                val releaseSweep = remember(model.id) { Animatable(0f) }
                LaunchedEffect(pressed, state.isSending) {
                    if (pressed && !state.isSending) {
                        releaseSweep.stop()
                        releaseSweep.snapTo(0f)
                        releaseSweep.animateTo(0.42f, tween(180, easing = FastOutSlowInEasing))
                    } else if (releaseSweep.value > 0.001f) {
                        launch {
                            releaseSweep.stop()
                            releaseSweep.animateTo(1.18f, tween(520, easing = FastOutSlowInEasing))
                            releaseSweep.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                        }
                    }
                }
                val visualLeft = tx
                val visualTop = ty
                val visualWidth = with(density) { currentWidth.toPx() }
                val visualHeight = with(density) { currentHeight.toPx() }
                val visualScaleX = capsuleScaleX * selectedPulse
                val visualScaleY = capsuleScaleY * selectedPulse
                val drawLeft = visualLeft + visualWidth * (1f - visualScaleX) * 0.5f
                val drawTop = visualTop + visualHeight * (1f - visualScaleY) * 0.5f
                val drawWidth = visualWidth * visualScaleX
                val drawHeight = visualHeight * visualScaleY
                val energy = if (stackRank > 0) 0.42f else 1f
                visuals.add(
                    UnifiedModelCardVisual(
                        left = drawLeft,
                        top = drawTop,
                        width = drawWidth,
                        height = drawHeight,
                        alpha = currentAlpha,
                        selected = selected,
                        stackEnergy = energy,
                        press = pressProgress,
                        sweep = releaseSweep.value,
                        moving = speedPulse,
                        stopPulse = (brake + returnBrake).coerceIn(0f, 1f)
                    )
                )
                Box(
                    modifier = Modifier
                        .width(currentWidth)
                        .height(currentHeight)
                        .zIndex(z)
                        .graphicsLayer {
                            translationX = tx
                            translationY = ty
                            scaleX = visualScaleX
                            scaleY = visualScaleY
                            alpha = currentAlpha
                            shadowElevation = 0f
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = !state.isSending,
                            onClick = { if (expanded) onSelected(model) else onToggleExpanded() }
                        )
                ) {
                    UnifiedModelCardContent(model = model, selected = selected, expansionProgress = cardProgress.coerceIn(0f, 1f))
                }
            }
        }
    }
}

private data class UnifiedModelCardVisual(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
    val selected: Boolean,
    val stackEnergy: Float,
    val press: Float,
    val sweep: Float,
    val moving: Float,
    val stopPulse: Float
)

@Composable
private fun UnifiedModelCardContent(model: ChatModel, selected: Boolean, expansionProgress: Float) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (selected) 9.dp else 7.dp).graphicsLayer { alpha = if (selected) 1f else 0.52f + 0.22f * expansionProgress })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(model.shortLabel, color = Color.White.copy(alpha = if (selected) 0.96f else 0.78f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.id, color = Color.White.copy(alpha = if (selected) 0.54f else 0.38f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Modifier.drawUnifiedModelStackPrism(visuals: List<UnifiedModelCardVisual>): Modifier = drawWithCache {
    fun prismSmooth(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
    fun prismColor(color: Color, alpha: Float, saturation: Float): Color = color.copy(alpha = (alpha * (0.32f + saturation.coerceIn(0f, 1f) * 0.68f)).coerceIn(0f, 1f))

    onDrawWithContent {
        fun drawCapsuleBody(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val spec = LightweightPrismCapsuleDefaults.LabMax
            val energy = v.stackEnergy.coerceIn(0f, 1f)
            val selectedEnergy = if (v.selected) 1f else 0f
            val surfaceAlpha = spec.surfaceAlpha * energy * alpha
            val topHighlight = spec.topHighlight * (0.88f + 0.12f * selectedEnergy) * energy * alpha
            val bottomDepth = spec.bottomDepth * energy * alpha
            val press = prismSmooth(v.press.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(spec.radius.dp.toPx(), spec.radius.dp.toPx())
            val bodySize = Size(v.width, v.height)
            val center = Offset(v.width * 0.50f, v.height * 0.50f)
            val surface = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f)),
                    Color(0xFFCFEAFF).copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f) * 0.28f),
                    Color.Transparent,
                    Color(0xFF000816).copy(alpha = bottomDepth.coerceIn(0f, 0.35f) * 0.42f)
                ),
                0f,
                v.height
            )
            val topLens = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = topHighlight.coerceIn(0f, 0.5f)),
                    Color(0xFFE8FFFF).copy(alpha = topHighlight.coerceIn(0f, 0.5f) * 0.22f),
                    Color.Transparent
                ),
                0f,
                v.height * spec.topHighlightHeight.coerceIn(0.05f, 0.60f)
            )
            val bottomShade = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Transparent, Color(0xFF020815).copy(alpha = bottomDepth.coerceIn(0f, 0.35f))),
                v.height * 0.52f,
                v.height
            )
            val pressureDark = Brush.radialGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF071B3D).copy(alpha = spec.pressDarken.coerceIn(0f, 0.4f) * 0.34f * press * alpha),
                    Color(0xFF01040C).copy(alpha = spec.pressDarken.coerceIn(0f, 0.4f) * press * alpha)
                ),
                center,
                maxSide * (0.72f + 0.12f * press)
            )
            val prismPressLight = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.pressGlow.coerceIn(0f, 0.9f) * 0.20f * press * alpha),
                    prismColor(Color(0xFF6AF7FF), spec.pressGlow * 0.26f * press * alpha, spec.rainbowSaturation),
                    prismColor(Color(0xFFFF7FE0), spec.pressGlow * 0.24f * press * alpha, spec.rainbowSaturation),
                    prismColor(Color(0xFFFFE789), spec.pressGlow * 0.18f * press * alpha, spec.rainbowSaturation),
                    prismColor(Color(0xFF7CFFA0), spec.pressGlow * 0.16f * press * alpha, spec.rainbowSaturation),
                    Color.Transparent
                ),
                center,
                maxSide * (0.30f + 0.22f * press)
            )
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = surface, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = topLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = bottomShade, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                if (press > 0.001f) {
                    drawRoundRect(brush = pressureDark, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                    drawRoundRect(brush = prismPressLight, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                }
            }
        }

        fun drawCapsuleRim(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val spec = LightweightPrismCapsuleDefaults.LabMax
            val energy = v.stackEnergy.coerceIn(0f, 1f)
            val selectedEnergy = if (v.selected) 1f else 0f
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(spec.radius.dp.toPx(), spec.radius.dp.toPx())
            val rimInset = 0.62.dp.toPx()
            val innerInset = 1.72.dp.toPx()
            val rimSize = Size((v.width - rimInset * 2f).coerceAtLeast(1f), (v.height - rimInset * 2f).coerceAtLeast(1f))
            val innerSize = Size((v.width - innerInset * 2f).coerceAtLeast(1f), (v.height - innerInset * 2f).coerceAtLeast(1f))
            val sweep = v.sweep.coerceIn(0f, 1.18f)
            val press = prismSmooth(v.press.coerceIn(0f, 1f))
            val sweepT = prismSmooth(sweep.coerceIn(0f, 1f))
            val sweepX = -0.28f + sweepT * 1.56f
            val rimBandPower = spec.rainbowRimAlpha.coerceIn(0f, 1f) * energy * alpha * (0.72f + 0.18f * selectedEnergy)
            val topNear = press
            val pressBandBoost = press
            fun prismBandBrush(start: Offset, end: Offset, strength: Float): Brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    prismColor(Color(0xFF68F7FF), strength * 0.24f, spec.rainbowSaturation),
                    prismColor(Color(0xFFFF7CE1), strength * 0.28f, spec.rainbowSaturation),
                    Color.White.copy(alpha = (strength * 0.18f).coerceIn(0f, 1f)),
                    prismColor(Color(0xFFFFE785), strength * 0.22f, spec.rainbowSaturation),
                    prismColor(Color(0xFF7BFF9E), strength * 0.20f, spec.rainbowSaturation),
                    prismColor(Color(0xFF6FA8FF), strength * 0.18f, spec.rainbowSaturation),
                    Color.Transparent
                ),
                start = start,
                end = end
            )
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFDFFFFF).copy(alpha = (spec.rimAlpha * energy * alpha * 0.18f + spec.topHighlight * energy * alpha * 0.20f).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = (spec.topHighlight * energy * alpha * 0.34f).coerceIn(0f, 1f)),
                    Color.Transparent
                ),
                0f,
                v.width
            )
            val innerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = spec.innerRimAlpha.coerceIn(0f, 0.5f) * 0.62f * energy * alpha),
                    Color.Transparent,
                    Color(0xFF00091E).copy(alpha = spec.bottomDepth.coerceIn(0f, 0.35f) * 0.68f * energy * alpha),
                    Color.White.copy(alpha = spec.innerRimAlpha.coerceIn(0f, 0.5f) * 0.16f * energy * alpha)
                ),
                Offset(v.width * 0.08f, 0f),
                Offset(v.width * 0.92f, v.height)
            )
            val cornerLight = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.5f) * energy * alpha),
                    Color(0xFFCFFFFF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.5f) * 0.20f * energy * alpha),
                    Color.Transparent
                ),
                Offset(v.width * 0.055f, v.height * 0.045f),
                maxSide * 0.30f
            )
            val rainbowCorner = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.rainbowCornerAlpha.coerceIn(0f, 0.5f) * 0.22f * energy * alpha),
                    prismColor(Color(0xFFFF87E5), spec.rainbowCornerAlpha * 0.16f * energy * alpha, spec.rainbowSaturation),
                    prismColor(Color(0xFF79F8FF), spec.rainbowCornerAlpha * 0.18f * energy * alpha, spec.rainbowSaturation),
                    Color.Transparent
                ),
                Offset(v.width * 0.10f, v.height * 0.10f),
                maxSide * 0.24f
            )
            val rimBandMain = prismBandBrush(Offset(v.width * (sweepX - 0.22f), v.height * -0.06f), Offset(v.width * (sweepX + 0.28f), v.height * 1.04f), rimBandPower * (0.72f + 0.28f * pressBandBoost))
            val rimBandCounter = prismBandBrush(Offset(v.width * (1.12f - sweepX), v.height * 0.02f), Offset(v.width * (0.54f - sweepX), v.height * 1.00f), rimBandPower * 0.52f * (0.70f + 0.30f * pressBandBoost))
            val rimBandTop = prismBandBrush(Offset(v.width * (sweepX - 0.18f), v.height * 0.02f), Offset(v.width * (sweepX + 0.34f), v.height * 0.26f), rimBandPower * 0.42f * (0.68f + 0.32f * topNear))
            val prismLocalEdge = prismBandBrush(Offset(v.width * 0.25f, v.height * -0.16f), Offset(v.width * 0.74f, v.height * 1.08f), spec.rainbowPressEdge * press * energy * alpha)
            val prismSweep = prismBandBrush(Offset(v.width * (sweepX - 0.24f), v.height * -0.04f), Offset(v.width * (sweepX + 0.30f), v.height * 1.04f), spec.rainbowSweepAlpha.coerceIn(0f, 1f) * sweep * energy * alpha + spec.pressSweep.coerceIn(0f, 1f) * 0.12f * sweep * energy * alpha)

            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.55.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.46.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = cornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.68.dp.toPx()), blendMode = BlendMode.Screen)
                if (spec.rainbowCornerAlpha > 0.001f) drawRoundRect(brush = rainbowCorner, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.58.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = rimBandMain, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth + spec.rainbowRimWidth * 0.92f).dp.toPx()), blendMode = BlendMode.Plus)
                drawRoundRect(brush = rimBandCounter, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth * 0.72f + spec.rainbowRimWidth * 0.58f).dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = rimBandTop, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth * 0.46f + spec.rainbowRimWidth * 0.42f).dp.toPx()), blendMode = BlendMode.Plus)
                if (press > 0.001f) drawRoundRect(brush = prismLocalEdge, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.76f + 0.96f * press).dp.toPx()), blendMode = BlendMode.Plus)
                if (sweep > 0.001f) drawRoundRect(brush = prismSweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.58f + 0.68f * sweep).dp.toPx()), blendMode = BlendMode.Plus)
            }
        }

        visuals.forEach { drawCapsuleBody(it) }
        drawContent()
        visuals.forEach { drawCapsuleRim(it) }
    }
}

private fun unifiedLerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun unifiedLerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun unifiedModelStackEase(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val smooth = p * p * (3f - 2f * p)
    val elastic = sin(p * PI.toFloat()).coerceAtLeast(0f) * 0.026f * (1f - kotlin.math.abs(0.5f - p) * 1.6f).coerceIn(0f, 1f)
    return (smooth + elastic).coerceIn(0f, 1f)
}
private fun unifiedModelStackSpeedPulse(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun unifiedModelStackArrivalBrake(progress: Float): Float = sin(((progress - 0.70f) / 0.30f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun unifiedModelStackReturnBrake(progress: Float): Float = sin(((0.28f - progress) / 0.28f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun unifiedModelStackCapsuleScaleX(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    val speed = unifiedModelStackSpeedPulse(p)
    val arrive = unifiedModelStackArrivalBrake(p)
    val back = unifiedModelStackReturnBrake(p)
    return 1f + 0.034f * speed + 0.026f * arrive - 0.012f * back
}
private fun unifiedModelStackCapsuleScaleY(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    val speed = unifiedModelStackSpeedPulse(p)
    val arrive = unifiedModelStackArrivalBrake(p)
    val back = unifiedModelStackReturnBrake(p)
    return 1f - 0.068f * speed + 0.034f * arrive + 0.014f * back
}
