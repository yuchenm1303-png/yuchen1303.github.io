package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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
        val scope = rememberCoroutineScope()
        val parentMaxWidth = maxWidth
        val models = ChatModel.entries
        val gap = 10.dp
        val rowStep = 74.dp
        val collapsedHeight = 56.dp
        val expandedHeight = 64.dp
        val reservedGap = 8.dp
        val collapsedWidth = (parentMaxWidth - reservedGap) * 0.642f
        val halfWidth = (parentMaxWidth - gap) / 2f
        val selectedModel = state.selectedModel
        val behindModels = models.filter { it != selectedModel }
        val visuals = mutableListOf<UnifiedModelCardVisual>()

        Box(
            Modifier
                .fillMaxSize()
                .drawUnifiedModelStackPrism(visuals)
        ) {
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(state.isSending, expanded) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.none { it.pressed }) break
                                }
                                if (!state.isSending) onToggleExpanded()
                            }
                        }
                )
            }

            models.forEachIndexed { index, model ->
                val selected = model == selectedModel
                val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
                val column = index % 2
                val row = index / 2
                val expandedX = if (column == 1) halfWidth + gap else 0.dp
                val expandedY = rowStep * row.toFloat()
                val expandedWidth = halfWidth
                val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
                val collapsedY = if (selected) 0.dp else (stackRank * 1.6f).dp
                val collapsedAlpha = if (selected) 1f else 0.48f + (4 - stackRank).coerceAtLeast(0) * 0.080f
                val staggerRank = index
                val z = if (expanded) 30f - index else if (selected) 50f else 40f - stackRank
                val cardProgress by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (expanded) 500 else 300,
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
                val overshootPx = with(density) { 4.8.dp.toPx() } * brake - with(density) { 2.6.dp.toPx() } * returnBrake
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

                val pressAnim = remember(model.id) { Animatable(0f) }
                val lensAnim = remember(model.id) { Animatable(0f) }
                val releaseSweep = remember(model.id) { Animatable(0f) }
                var pressCenter by remember(model.id) { mutableStateOf(Offset(0.50f, 0.50f)) }
                var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
                var bandCount by remember(model.id) { mutableStateOf(2) }
                var bandSeedA by remember(model.id) { mutableStateOf(Random.nextFloat()) }
                var bandSeedB by remember(model.id) { mutableStateOf(Random.nextFloat()) }
                var bandSeedC by remember(model.id) { mutableStateOf(Random.nextFloat()) }
                var bandAngleA by remember(model.id) { mutableStateOf(Random.nextFloat()) }
                var bandAngleB by remember(model.id) { mutableStateOf(Random.nextFloat()) }
                var bandAngleC by remember(model.id) { mutableStateOf(Random.nextFloat()) }
                val pressRaw = pressAnim.value.coerceIn(0f, 1.14f)
                val lensRaw = lensAnim.value.coerceIn(0f, 1.10f)
                val pressProgress = unifiedModelStackSmooth(pressRaw.coerceIn(0f, 1f))
                val sweepProgress = releaseSweep.value.coerceIn(0f, 1.20f)
                val rebound = unifiedModelStackSmooth(((sweepProgress - 0.62f) / 0.58f).coerceIn(0f, 1f)) * (1f - pressProgress)
                val pressElasticity = 1.18f
                val pressScaleX = 1f + pressProgress * 0.034f * pressElasticity - rebound * 0.007f * pressElasticity
                val pressScaleY = 1f - pressProgress * 0.052f * pressElasticity + rebound * 0.017f * pressElasticity
                val pressShiftY = pressProgress * 3.20f * pressElasticity - rebound * 1.05f * pressElasticity

                fun updatePressCenter(position: Offset) {
                    pressCenter = Offset(
                        (position.x / cardSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        (position.y / cardSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    )
                }

                val visualLeft = tx
                val visualTop = ty
                val visualWidth = with(density) { currentWidth.toPx() }
                val visualHeight = with(density) { currentHeight.toPx() }
                val flightScaleX = capsuleScaleX * selectedPulse
                val flightScaleY = capsuleScaleY * selectedPulse
                val flightLeft = visualLeft + visualWidth * (1f - flightScaleX) * 0.5f
                val flightTop = visualTop + visualHeight * (1f - flightScaleY) * 0.5f
                val flightWidth = visualWidth * flightScaleX
                val flightHeight = visualHeight * flightScaleY
                val drawLeft = flightLeft + flightWidth * pressCenter.x * (1f - pressScaleX)
                val drawTop = flightTop + flightHeight * pressCenter.y * (1f - pressScaleY) + pressShiftY
                val drawWidth = flightWidth * pressScaleX
                val drawHeight = flightHeight * pressScaleY
                val contentScaleX = if (visualWidth > 0f) drawWidth / visualWidth else 1f
                val contentScaleY = if (visualHeight > 0f) drawHeight / visualHeight else 1f
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
                        press = pressRaw,
                        lens = lensRaw,
                        sweep = sweepProgress,
                        pressCenter = pressCenter,
                        bandCount = bandCount,
                        bandSeedA = bandSeedA,
                        bandSeedB = bandSeedB,
                        bandSeedC = bandSeedC,
                        bandAngleA = bandAngleA,
                        bandAngleB = bandAngleB,
                        bandAngleC = bandAngleC
                    )
                )
                Box(
                    modifier = Modifier
                        .width(currentWidth)
                        .height(currentHeight)
                        .zIndex(z)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = drawLeft
                            translationY = drawTop
                            scaleX = contentScaleX
                            scaleY = contentScaleY
                            alpha = currentAlpha
                            shadowElevation = 0f
                        }
                        .onSizeChanged { size ->
                            cardSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat())
                        }
                        .pointerInput(state.isSending, expanded, model.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                updatePressCenter(down.position)
                                if (!state.isSending) {
                                    bandCount = if (Random.nextBoolean()) 2 else 3
                                    bandSeedA = Random.nextFloat()
                                    bandSeedB = Random.nextFloat()
                                    bandSeedC = Random.nextFloat()
                                    bandAngleA = Random.nextFloat()
                                    bandAngleB = Random.nextFloat()
                                    bandAngleC = Random.nextFloat()
                                    scope.launch {
                                        pressAnim.stop()
                                        if (pressAnim.value < 0.18f) pressAnim.snapTo(0.18f)
                                        pressAnim.animateTo(1.00f, tween(145, easing = FastOutSlowInEasing))
                                        pressAnim.animateTo(0.82f, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow))
                                    }
                                    scope.launch {
                                        lensAnim.stop()
                                        lensAnim.animateTo(0.30f, tween(120, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0.92f, tween(320, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.snapTo(0f)
                                        releaseSweep.animateTo(1.00f, tween(760, easing = FastOutSlowInEasing))
                                        releaseSweep.animateTo(0.18f, tween(420, easing = FastOutSlowInEasing))
                                    }
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (tracked != null) {
                                        updatePressCenter(tracked.position)
                                        if (!tracked.pressed) break
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                                if (!state.isSending) {
                                    if (expanded) {
                                        onSelected(model)
                                        if (model == ChatModel.Auto) onToggleExpanded()
                                    } else {
                                        onToggleExpanded()
                                    }
                                    scope.launch {
                                        pressAnim.stop()
                                        pressAnim.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        lensAnim.stop()
                                        lensAnim.animateTo(0.34f, tween(170, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.animateTo(1.20f, tween(520, easing = FastOutSlowInEasing))
                                        releaseSweep.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                                    }
                                }
                            }
                        }
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
    val lens: Float,
    val sweep: Float,
    val pressCenter: Offset,
    val bandCount: Int,
    val bandSeedA: Float,
    val bandSeedB: Float,
    val bandSeedC: Float,
    val bandAngleA: Float,
    val bandAngleB: Float,
    val bandAngleC: Float
)

@Composable
private fun UnifiedModelCardContent(model: ChatModel, selected: Boolean, expansionProgress: Float) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(if (selected) 9.dp else 7.dp)
                .graphicsLayer { alpha = if (selected) 1f else 0.52f + 0.22f * expansionProgress }
                .background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
        )
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
    fun prismColor(color: Color, alpha: Float): Color = color.copy(alpha = alpha.coerceIn(0f, 1f))

    onDrawWithContent {
        fun drawCapsuleBody(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val energy = v.stackEnergy.coerceIn(0f, 1f)
            val selectedEnergy = if (v.selected) 1f else 0f
            val press = prismSmooth(v.press.coerceIn(0f, 1f))
            val lens = prismSmooth(v.lens.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(v.height / 2f, v.height / 2f)
            val bodySize = Size(v.width, v.height)
            val center = Offset(v.width * v.pressCenter.x.coerceIn(0f, 1f), v.height * v.pressCenter.y.coerceIn(0f, 1f))
            val bodySurface = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.020f + selectedEnergy * 0.008f) * energy * alpha),
                    Color(0xFF9FDFFF).copy(alpha = 0.014f * energy * alpha),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = (0.112f + 0.050f * press) * energy * alpha)
                ),
                0f,
                v.height
            )
            val opticalDepth = Brush.radialGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF071A3B).copy(alpha = (0.064f + 0.060f * press) * energy * alpha),
                    Color(0xFF00040E).copy(alpha = (0.118f + 0.070f * press) * energy * alpha)
                ),
                center,
                maxSide * (0.62f + 0.14f * press)
            )
            val softPressFog = Brush.radialGradient(
                listOf(
                    Color(0xFFE9FFFF).copy(alpha = 0.018f * lens * alpha),
                    Color(0xFF66F7FF).copy(alpha = 0.064f * lens * alpha),
                    Color(0xFFFF64DA).copy(alpha = 0.038f * lens * alpha),
                    Color(0xFF6C7DFF).copy(alpha = 0.032f * lens * alpha),
                    Color.Transparent
                ),
                Offset(center.x + v.width * 0.025f, center.y - v.height * 0.035f),
                maxSide * (0.34f + 0.18f * lens)
            )
            val softPressFogWide = Brush.radialGradient(
                listOf(
                    Color(0xFF77FAFF).copy(alpha = 0.028f * lens * alpha),
                    Color(0xFFFF72E5).copy(alpha = 0.018f * lens * alpha),
                    Color.Transparent
                ),
                Offset(center.x - v.width * 0.035f, center.y + v.height * 0.025f),
                maxSide * (0.54f + 0.18f * lens)
            )
            val topSubtleLens = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.030f + selectedEnergy * 0.012f) * energy * alpha),
                    Color(0xFFCFFBFF).copy(alpha = 0.014f * energy * alpha),
                    Color.Transparent
                ),
                0f,
                v.height * 0.26f
            )
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = bodySurface, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = opticalDepth, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                drawRoundRect(brush = topSubtleLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                if (lens > 0.001f) {
                    drawRoundRect(brush = softPressFogWide, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = softPressFog, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                }
            }
        }

        fun drawCapsuleRim(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val energy = v.stackEnergy.coerceIn(0f, 1f)
            val selectedEnergy = if (v.selected) 1f else 0f
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(v.height / 2f, v.height / 2f)
            val rimInset = 0.64.dp.toPx()
            val innerInset = 1.62.dp.toPx()
            val rimSize = Size((v.width - rimInset * 2f).coerceAtLeast(1f), (v.height - rimInset * 2f).coerceAtLeast(1f))
            val innerSize = Size((v.width - innerInset * 2f).coerceAtLeast(1f), (v.height - innerInset * 2f).coerceAtLeast(1f))
            val sweep = v.sweep.coerceIn(0f, 1.20f)
            val press = prismSmooth(v.press.coerceIn(0f, 1f))
            val lens = prismSmooth(v.lens.coerceIn(0f, 1f))
            val center = Offset(v.width * v.pressCenter.x.coerceIn(0f, 1f), v.height * v.pressCenter.y.coerceIn(0f, 1f))
            val topNear = (1f - v.pressCenter.y / 0.42f).coerceIn(0f, 1f) * press
            val bottomNear = (1f - (1f - v.pressCenter.y) / 0.42f).coerceIn(0f, 1f) * press
            val leftNear = (1f - v.pressCenter.x / 0.42f).coerceIn(0f, 1f) * press
            val rightNear = (1f - (1f - v.pressCenter.x) / 0.42f).coerceIn(0f, 1f) * press
            val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)
            val sweepT = prismSmooth(sweep.coerceIn(0f, 1f))
            val restingPower = (0.34f + 0.12f * selectedEnergy) * energy * alpha
            val dynamicPower = (press * 0.78f + sweep * 0.92f + lens * 0.30f).coerceIn(0f, 1.28f) * energy * alpha
            fun prismBandBrush(start: Offset, end: Offset, strength: Float): Brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    prismColor(Color(0xFF63F7FF), strength * 0.44f),
                    prismColor(Color(0xFFFF56D9), strength * 0.42f),
                    Color.White.copy(alpha = (strength * 0.13f).coerceIn(0f, 0.46f)),
                    prismColor(Color(0xFFFFE46C), strength * 0.26f),
                    prismColor(Color(0xFF74FF9C), strength * 0.22f),
                    prismColor(Color(0xFF7893FF), strength * 0.22f),
                    Color.Transparent
                ),
                start = start,
                end = end
            )
            fun randomBand(seed: Float, angleSeed: Float, order: Int, strengthScale: Float): Brush {
                val angle = (-0.72f + angleSeed * 1.44f) * PI.toFloat()
                val dir = Offset(cos(angle), sin(angle))
                val normal = Offset(-dir.y, dir.x)
                val travel = -0.78f + sweepT * (1.72f + 0.16f * order) + (seed - 0.5f) * 0.34f
                val centerPoint = Offset(
                    v.width * (0.50f + normal.x * travel),
                    v.height * (0.50f + normal.y * travel)
                )
                val halfLen = maxSide * (0.72f + 0.10f * order)
                return prismBandBrush(
                    Offset(centerPoint.x - dir.x * halfLen, centerPoint.y - dir.y * halfLen),
                    Offset(centerPoint.x + dir.x * halfLen, centerPoint.y + dir.y * halfLen),
                    dynamicPower * strengthScale
                )
            }
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF6FFAFF).copy(alpha = 0.10f * restingPower),
                    Color.White.copy(alpha = 0.16f * restingPower),
                    Color(0xFFFF6FE0).copy(alpha = 0.070f * restingPower),
                    Color.Transparent
                ),
                0f,
                v.width
            )
            val innerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.070f * restingPower),
                    Color.Transparent,
                    Color(0xFF020A1D).copy(alpha = 0.20f * energy * alpha),
                    Color(0xFF63F7FF).copy(alpha = 0.042f * restingPower)
                ),
                Offset(v.width * 0.08f, 0f),
                Offset(v.width * 0.92f, v.height)
            )
            val tinyCornerGlint = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.070f * restingPower + 0.090f * dynamicPower),
                    Color(0xFF6AF7FF).copy(alpha = 0.070f * restingPower + 0.060f * dynamicPower),
                    Color.Transparent
                ),
                Offset(v.width * 0.08f, v.height * 0.10f),
                maxSide * 0.11f
            )
            val pressEdgeSweep = prismBandBrush(
                Offset(center.x - v.width * 0.28f, center.y - v.height * 0.82f),
                Offset(center.x + v.width * 0.24f, center.y + v.height * 0.82f),
                (0.70f + pressBandBoost * 0.82f) * press * energy * alpha
            )
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.64.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.50.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = tinyCornerGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.52.dp.toPx()), blendMode = BlendMode.Screen)
                if (dynamicPower > 0.001f) {
                    drawRoundRect(brush = randomBand(v.bandSeedA, v.bandAngleA, 0, 1.08f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.60f + 0.84f * dynamicPower.coerceIn(0f, 1.2f)).dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = randomBand(v.bandSeedB, v.bandAngleB, 1, 0.86f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.54f + 0.74f * dynamicPower.coerceIn(0f, 1.2f)).dp.toPx()), blendMode = BlendMode.Plus)
                    if (v.bandCount >= 3) {
                        drawRoundRect(brush = randomBand(v.bandSeedC, v.bandAngleC, 2, 0.66f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.48f + 0.62f * dynamicPower.coerceIn(0f, 1.2f)).dp.toPx()), blendMode = BlendMode.Screen)
                    }
                }
                if (press > 0.001f) drawRoundRect(brush = pressEdgeSweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.78f + 1.20f * press).dp.toPx()), blendMode = BlendMode.Plus)
            }
        }

        visuals.forEach { drawCapsuleBody(it) }
        drawContent()
        visuals.forEach { drawCapsuleRim(it) }
    }
}

private fun unifiedLerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun unifiedLerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun unifiedModelStackSmooth(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
private fun unifiedModelStackEase(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val smooth = unifiedModelStackSmooth(p)
    val elastic = sin(p * PI.toFloat()).coerceAtLeast(0f) * 0.030f * (1f - abs(0.5f - p) * 1.6f).coerceIn(0f, 1f)
    return (smooth + elastic).coerceIn(0f, 1f)
}
private fun unifiedModelStackSpeedPulse(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun unifiedModelStackArrivalBrake(progress: Float): Float = sin(((progress - 0.70f) / 0.30f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun unifiedModelStackReturnBrake(progress: Float): Float = sin(((0.28f - progress) / 0.28f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun unifiedModelStackCapsuleScaleX(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    return 1f + 0.036f * unifiedModelStackSpeedPulse(p) + 0.028f * unifiedModelStackArrivalBrake(p) - 0.012f * unifiedModelStackReturnBrake(p)
}
private fun unifiedModelStackCapsuleScaleY(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    return 1f - 0.070f * unifiedModelStackSpeedPulse(p) + 0.036f * unifiedModelStackArrivalBrake(p) + 0.014f * unifiedModelStackReturnBrake(p)
}
