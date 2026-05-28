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
                val sweepProgress = releaseSweep.value.coerceIn(0f, 1.30f)
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
                                        lensAnim.animateTo(0.20f, tween(120, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0.54f, tween(300, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.snapTo(0f)
                                        releaseSweep.animateTo(0.68f, tween(620, easing = FastOutSlowInEasing))
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
                                        lensAnim.animateTo(0.12f, tween(170, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.animateTo(1.18f, tween(560, easing = FastOutSlowInEasing))
                                        releaseSweep.animateTo(1.30f, tween(180, easing = FastOutSlowInEasing))
                                        releaseSweep.snapTo(0f)
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
            val sweep = prismSmooth(v.sweep.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(v.height / 2f, v.height / 2f)
            val bodySize = Size(v.width, v.height)
            val pressOffset = Offset((v.pressCenter.x - 0.5f) * v.width * 0.12f, (v.pressCenter.y - 0.5f) * v.height * 0.18f)
            val fogAnchor = Offset(v.width * 0.50f + pressOffset.x, v.height * 0.50f + pressOffset.y)
            val bodySurface = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.016f + selectedEnergy * 0.006f) * energy * alpha),
                    Color(0xFF9FDFFF).copy(alpha = 0.010f * energy * alpha),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = (0.120f + 0.044f * press) * energy * alpha)
                ),
                0f,
                v.height
            )
            val pressureShade = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF071A3B).copy(alpha = (0.026f + 0.044f * press) * energy * alpha),
                    Color(0xFF00040E).copy(alpha = (0.074f + 0.054f * press) * energy * alpha),
                    Color.Transparent
                ),
                Offset(fogAnchor.x - v.width * 0.70f, fogAnchor.y - v.height * 1.04f),
                Offset(fogAnchor.x + v.width * 0.60f, fogAnchor.y + v.height * 1.00f)
            )
            val mistEnergy = (lens * 0.18f + press * 0.070f).coerceIn(0f, 0.26f) * energy * alpha
            fun materialDriftMist(seed: Float, angleSeed: Float, order: Int, strengthScale: Float): Brush {
                val angle = (-0.52f + angleSeed * 1.04f) * PI.toFloat()
                val dir = Offset(cos(angle), sin(angle))
                val normal = Offset(-dir.y, dir.x)
                val drift = (seed - 0.5f) * 0.58f + (sweep - 0.35f) * (0.08f + order * 0.03f)
                val anchor = Offset(
                    fogAnchor.x + normal.x * v.width * drift + dir.x * v.width * 0.12f * order,
                    fogAnchor.y + normal.y * v.height * drift + dir.y * v.height * 0.16f * order
                )
                val halfLen = maxSide * (0.96f + 0.22f * order)
                return Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        prismColor(Color(0xFF66F7FF), 0.020f * mistEnergy * strengthScale),
                        prismColor(Color(0xFFFF67DC), 0.014f * mistEnergy * strengthScale),
                        Color.White.copy(alpha = (0.006f * mistEnergy * strengthScale).coerceIn(0f, 0.016f)),
                        prismColor(Color(0xFF798DFF), 0.012f * mistEnergy * strengthScale),
                        Color.Transparent
                    ),
                    Offset(anchor.x - dir.x * halfLen, anchor.y - dir.y * halfLen),
                    Offset(anchor.x + dir.x * halfLen, anchor.y + dir.y * halfLen)
                )
            }
            val topSubtleLens = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.024f + selectedEnergy * 0.008f) * energy * alpha),
                    Color(0xFFCFFBFF).copy(alpha = 0.010f * energy * alpha),
                    Color.Transparent
                ),
                0f,
                v.height * 0.26f
            )
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = bodySurface, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = pressureShade, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                drawRoundRect(brush = topSubtleLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                if (mistEnergy > 0.001f) {
                    drawRoundRect(brush = materialDriftMist(v.bandSeedA, v.bandAngleA, 0, 1.00f), size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = materialDriftMist(v.bandSeedB, v.bandAngleB, 1, 0.58f), size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
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
            val sweep = v.sweep.coerceIn(0f, 1.30f)
            val press = prismSmooth(v.press.coerceIn(0f, 1f))
            val lens = prismSmooth(v.lens.coerceIn(0f, 1f))
            val sweepPhase = sweep.coerceIn(0f, 1.20f)
            val sweepAlpha = when {
                sweepPhase < 0.08f -> (sweepPhase / 0.08f).coerceIn(0f, 1f)
                sweepPhase <= 0.86f -> 1f
                else -> ((1.20f - sweepPhase) / 0.34f).coerceIn(0f, 1f)
            }
            val center = Offset(v.width * v.pressCenter.x.coerceIn(0f, 1f), v.height * v.pressCenter.y.coerceIn(0f, 1f))
            val topNear = (1f - v.pressCenter.y / 0.42f).coerceIn(0f, 1f) * press
            val bottomNear = (1f - (1f - v.pressCenter.y) / 0.42f).coerceIn(0f, 1f) * press
            val leftNear = (1f - v.pressCenter.x / 0.42f).coerceIn(0f, 1f) * press
            val rightNear = (1f - (1f - v.pressCenter.x) / 0.42f).coerceIn(0f, 1f) * press
            val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)
            val restingPower = (0.26f + 0.10f * selectedEnergy) * energy * alpha
            val dynamicPower = (press * 0.34f + sweepAlpha * 0.92f + lens * 0.10f).coerceIn(0f, 1.05f) * energy * alpha
            fun thinFilmBrush(start: Offset, end: Offset, strength: Float, coolBias: Float = 1f): Brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    prismColor(Color(0xFF7EFAFF), strength * 0.16f * coolBias),
                    Color.White.copy(alpha = (strength * 0.42f).coerceIn(0f, 0.62f)),
                    prismColor(Color(0xFFE9D7FF), strength * 0.14f),
                    prismColor(Color(0xFFFF77DC), strength * 0.050f),
                    Color.Transparent
                ),
                start = start,
                end = end
            )
            fun movingGlintBrush(seed: Float, angleSeed: Float, order: Int, scale: Float, spectralOffset: Float): Brush {
                val direction = if (angleSeed > 0.50f) 1f else -1f
                val speed = 0.94f + seed * 0.28f + order * 0.08f
                val seedShift = (seed - 0.5f) * 0.34f + spectralOffset
                val sweepX = if (direction > 0f) {
                    -0.24f + seedShift + sweepPhase * speed * 1.24f
                } else {
                    1.24f + seedShift - sweepPhase * speed * 1.24f
                }
                val lane = ((seed * 5f).toInt() + order).coerceAtLeast(0) % 5
                val startY = when (lane) { 0 -> 0.04f; 1 -> 0.72f; 2 -> 0.14f; 3 -> 0.26f; else -> 0.06f }
                val endY = when (lane) { 0 -> 0.24f; 1 -> 0.96f; 2 -> 0.88f; 3 -> 0.58f; else -> 0.74f }
                val width = 0.105f + 0.030f * seed + order * 0.012f
                return thinFilmBrush(
                    Offset(v.width * (sweepX - width), v.height * startY),
                    Offset(v.width * (sweepX + width), v.height * endY),
                    dynamicPower * sweepAlpha * scale * (0.82f + 0.12f * pressBandBoost)
                )
            }
            fun prismHalo(power: Float, white: Float, cyan: Float): List<Color> = listOf(
                Color.White.copy(alpha = white * power),
                Color(0xFFEAD9FF).copy(alpha = 0.030f * power),
                Color(0xFF80FFF2).copy(alpha = cyan * power),
                Color.Transparent
            )
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF90FBFF).copy(alpha = 0.068f * restingPower),
                    Color.White.copy(alpha = 0.120f * restingPower),
                    Color(0xFFEEDAFF).copy(alpha = 0.040f * restingPower),
                    Color.Transparent
                ),
                0f,
                v.width
            )
            val innerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.052f * restingPower),
                    Color.Transparent,
                    Color(0xFF020A1D).copy(alpha = 0.18f * energy * alpha),
                    Color(0xFF86F9FF).copy(alpha = 0.030f * restingPower)
                ),
                Offset(v.width * 0.08f, 0f),
                Offset(v.width * 0.92f, v.height)
            )
            val tinyCornerGlint = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.036f * restingPower + 0.032f * dynamicPower),
                    Color(0xFF9AFAFF).copy(alpha = 0.034f * restingPower + 0.026f * dynamicPower),
                    Color.Transparent
                ),
                Offset(v.width * 0.08f, v.height * 0.10f),
                maxSide * 0.09f
            )
            val localEdgeStroke = 0.78.dp.toPx() + 0.46.dp.toPx() * press
            val topEdgeHalo = Brush.radialGradient(prismHalo(topNear, 0.16f, 0.052f), Offset(center.x, rimInset), maxSide * 0.30f)
            val bottomEdgeHalo = Brush.radialGradient(prismHalo(bottomNear, 0.12f, 0.038f), Offset(center.x, v.height - rimInset), maxSide * 0.30f)
            val leftEdgeHalo = Brush.radialGradient(prismHalo(leftNear, 0.13f, 0.042f), Offset(rimInset, center.y), maxSide * 0.29f)
            val rightEdgeHalo = Brush.radialGradient(prismHalo(rightNear, 0.13f, 0.042f), Offset(v.width - rimInset, center.y), maxSide * 0.29f)
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.56.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.42.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = tinyCornerGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.42.dp.toPx()), blendMode = BlendMode.Screen)
                if (dynamicPower > 0.001f) {
                    drawRoundRect(brush = movingGlintBrush(v.bandSeedA, v.bandAngleA, 0, 0.42f, -0.012f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(1.18.dp.toPx()), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = movingGlintBrush(v.bandSeedA, v.bandAngleA, 0, 1.00f, 0.000f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.46.dp.toPx() + 0.18.dp.toPx() * dynamicPower.coerceIn(0f, 1f)), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = movingGlintBrush(v.bandSeedB, v.bandAngleB, 1, 0.32f, 0.016f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.98.dp.toPx()), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = movingGlintBrush(v.bandSeedB, v.bandAngleB, 1, 0.70f, 0.026f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.38.dp.toPx() + 0.14.dp.toPx() * dynamicPower.coerceIn(0f, 1f)), blendMode = BlendMode.Plus)
                    if (v.bandCount >= 3) {
                        drawRoundRect(brush = movingGlintBrush(v.bandSeedC, v.bandAngleC, 2, 0.44f, -0.020f), topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.34.dp.toPx() + 0.10.dp.toPx() * dynamicPower.coerceIn(0f, 1f)), blendMode = BlendMode.Screen)
                    }
                }
                if (press > 0.001f) {
                    drawRoundRect(brush = topEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = bottomEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = leftEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = rightEdgeHalo, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(localEdgeStroke), blendMode = BlendMode.Screen)
                }
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
