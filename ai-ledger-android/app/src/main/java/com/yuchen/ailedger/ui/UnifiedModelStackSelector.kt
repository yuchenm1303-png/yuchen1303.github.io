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
import androidx.compose.ui.graphics.StrokeCap
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
                                        lensAnim.animateTo(0.28f, tween(120, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0.76f, tween(300, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.snapTo(0f)
                                        releaseSweep.animateTo(0.72f, tween(680, easing = FastOutSlowInEasing))
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
                                        lensAnim.animateTo(0.18f, tween(170, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.animateTo(1.20f, tween(560, easing = FastOutSlowInEasing))
                                        releaseSweep.animateTo(1.30f, tween(220, easing = FastOutSlowInEasing))
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
    fun wrap01(value: Float): Float {
        val r = value % 1f
        return if (r < 0f) r + 1f else r
    }

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
            val pressOffset = Offset((v.pressCenter.x - 0.5f) * v.width * 0.18f, (v.pressCenter.y - 0.5f) * v.height * 0.26f)
            val fogAnchor = Offset(v.width * 0.50f + pressOffset.x, v.height * 0.50f + pressOffset.y)
            val bodySurface = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.018f + selectedEnergy * 0.006f) * energy * alpha),
                    Color(0xFF9FDFFF).copy(alpha = 0.012f * energy * alpha),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = (0.118f + 0.050f * press) * energy * alpha)
                ),
                0f,
                v.height
            )
            val pressureShade = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF071A3B).copy(alpha = (0.030f + 0.050f * press) * energy * alpha),
                    Color(0xFF00040E).copy(alpha = (0.078f + 0.060f * press) * energy * alpha),
                    Color.Transparent
                ),
                Offset(fogAnchor.x - v.width * 0.68f, fogAnchor.y - v.height * 1.04f),
                Offset(fogAnchor.x + v.width * 0.58f, fogAnchor.y + v.height * 1.00f)
            )
            val mistEnergy = (lens * 0.30f + press * 0.10f).coerceIn(0f, 0.42f) * energy * alpha
            fun materialDriftMist(seed: Float, angleSeed: Float, order: Int, strengthScale: Float): Brush {
                val angle = (-0.52f + angleSeed * 1.04f) * PI.toFloat()
                val dir = Offset(cos(angle), sin(angle))
                val normal = Offset(-dir.y, dir.x)
                val drift = (seed - 0.5f) * 0.58f + (sweep - 0.35f) * (0.10f + order * 0.04f)
                val anchor = Offset(
                    fogAnchor.x + normal.x * v.width * drift + dir.x * v.width * 0.12f * order,
                    fogAnchor.y + normal.y * v.height * drift + dir.y * v.height * 0.16f * order
                )
                val halfLen = maxSide * (0.92f + 0.22f * order)
                return Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        prismColor(Color(0xFF66F7FF), 0.034f * mistEnergy * strengthScale),
                        prismColor(Color(0xFFFF67DC), 0.026f * mistEnergy * strengthScale),
                        Color.White.copy(alpha = (0.010f * mistEnergy * strengthScale).coerceIn(0f, 0.028f)),
                        prismColor(Color(0xFFFFE56E), 0.014f * mistEnergy * strengthScale),
                        prismColor(Color(0xFF798DFF), 0.018f * mistEnergy * strengthScale),
                        Color.Transparent
                    ),
                    Offset(anchor.x - dir.x * halfLen, anchor.y - dir.y * halfLen),
                    Offset(anchor.x + dir.x * halfLen, anchor.y + dir.y * halfLen)
                )
            }
            val topSubtleLens = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.026f + selectedEnergy * 0.010f) * energy * alpha),
                    Color(0xFFCFFBFF).copy(alpha = 0.012f * energy * alpha),
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
                    drawRoundRect(brush = materialDriftMist(v.bandSeedB, v.bandAngleB, 1, 0.66f), size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
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
                sweepPhase <= 0.92f -> 1f
                else -> ((1.20f - sweepPhase) / 0.28f).coerceIn(0f, 1f)
            }
            val center = Offset(v.width * v.pressCenter.x.coerceIn(0f, 1f), v.height * v.pressCenter.y.coerceIn(0f, 1f))
            val topNear = (1f - v.pressCenter.y / 0.42f).coerceIn(0f, 1f) * press
            val bottomNear = (1f - (1f - v.pressCenter.y) / 0.42f).coerceIn(0f, 1f) * press
            val leftNear = (1f - v.pressCenter.x / 0.42f).coerceIn(0f, 1f) * press
            val rightNear = (1f - (1f - v.pressCenter.x) / 0.42f).coerceIn(0f, 1f) * press
            val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)
            val restingPower = (0.30f + 0.11f * selectedEnergy) * energy * alpha
            val dynamicPower = (press * 0.54f + sweepAlpha * 0.96f + lens * 0.18f).coerceIn(0f, 1.22f) * energy * alpha
            fun prismBandBrush(start: Offset, end: Offset, strength: Float): Brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    prismColor(Color(0xFF63F7FF), strength * 0.50f),
                    prismColor(Color(0xFFFF56D9), strength * 0.46f),
                    Color.White.copy(alpha = (strength * 0.16f).coerceIn(0f, 0.48f)),
                    prismColor(Color(0xFFFFE46C), strength * 0.28f),
                    prismColor(Color(0xFF74FF9C), strength * 0.24f),
                    prismColor(Color(0xFF7893FF), strength * 0.24f),
                    Color.Transparent
                ),
                start = start,
                end = end
            )
            fun capsulePointAndTangent(phaseRaw: Float): Pair<Offset, Offset> {
                val phase = wrap01(phaseRaw)
                val radius = (v.height / 2f - rimInset).coerceAtLeast(1f)
                val cxLeft = v.height / 2f
                val cxRight = v.width - v.height / 2f
                val cy = v.height / 2f
                val straight = (v.width - v.height).coerceAtLeast(1f)
                return when {
                    phase < 0.25f -> {
                        val t = phase / 0.25f
                        Offset(cxLeft + straight * t, rimInset) to Offset(1f, 0f)
                    }
                    phase < 0.50f -> {
                        val t = (phase - 0.25f) / 0.25f
                        val theta = -PI.toFloat() / 2f + t * PI.toFloat()
                        val point = Offset(cxRight + cos(theta) * radius, cy + sin(theta) * radius)
                        point to Offset(-sin(theta), cos(theta))
                    }
                    phase < 0.75f -> {
                        val t = (phase - 0.50f) / 0.25f
                        Offset(cxRight - straight * t, v.height - rimInset) to Offset(-1f, 0f)
                    }
                    else -> {
                        val t = (phase - 0.75f) / 0.25f
                        val theta = PI.toFloat() / 2f + t * PI.toFloat()
                        val point = Offset(cxLeft + cos(theta) * radius, cy + sin(theta) * radius)
                        point to Offset(-sin(theta), cos(theta))
                    }
                }
            }
            fun edgePhaseFromPress(): Float {
                val px = v.pressCenter.x.coerceIn(0f, 1f)
                val py = v.pressCenter.y.coerceIn(0f, 1f)
                val topDistance = py
                val bottomDistance = 1f - py
                val leftDistance = px
                val rightDistance = 1f - px
                val minDistance = minOf(topDistance, bottomDistance, leftDistance, rightDistance)
                return when (minDistance) {
                    topDistance -> 0.25f * px
                    rightDistance -> 0.25f + 0.25f * py
                    bottomDistance -> 0.50f + 0.25f * (1f - px)
                    else -> 0.75f + 0.25f * (1f - py)
                }
            }
            fun drawRimSpark(phase: Float, strength: Float, lengthScale: Float, strokeScale: Float) {
                if (strength <= 0.001f) return
                val (point, tangent) = capsulePointAndTangent(phase)
                val halfLen = maxSide * lengthScale
                val start = Offset(point.x - tangent.x * halfLen, point.y - tangent.y * halfLen)
                val end = Offset(point.x + tangent.x * halfLen, point.y + tangent.y * halfLen)
                drawLine(
                    brush = prismBandBrush(start, end, strength),
                    start = start,
                    end = end,
                    strokeWidth = (0.82f + 1.42f * strokeScale).dp.toPx(),
                    cap = StrokeCap.Round,
                    blendMode = BlendMode.Plus
                )
            }
            fun drawTravellingBand(seed: Float, angleSeed: Float, order: Int, scale: Float) {
                val direction = if (angleSeed > 0.50f) 1f else -1f
                val speed = 0.82f + seed * 0.32f + order * 0.10f
                val phase = wrap01(seed + direction * sweepPhase * speed + order * 0.075f)
                val strength = dynamicPower * sweepAlpha * scale * (0.86f + 0.18f * pressBandBoost)
                drawRimSpark(phase, strength, 0.090f + 0.018f * order, strength.coerceIn(0f, 1.25f))
                drawRimSpark(phase - direction * 0.022f, strength * 0.42f, 0.060f + 0.012f * order, strength.coerceIn(0f, 1.05f))
            }
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF6FFAFF).copy(alpha = 0.09f * restingPower),
                    Color.White.copy(alpha = 0.15f * restingPower),
                    Color(0xFFFF6FE0).copy(alpha = 0.060f * restingPower),
                    Color.Transparent
                ),
                0f,
                v.width
            )
            val innerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.060f * restingPower),
                    Color.Transparent,
                    Color(0xFF020A1D).copy(alpha = 0.19f * energy * alpha),
                    Color(0xFF63F7FF).copy(alpha = 0.036f * restingPower)
                ),
                Offset(v.width * 0.08f, 0f),
                Offset(v.width * 0.92f, v.height)
            )
            val tinyCornerGlint = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.048f * restingPower + 0.060f * dynamicPower),
                    Color(0xFF6AF7FF).copy(alpha = 0.046f * restingPower + 0.040f * dynamicPower),
                    Color.Transparent
                ),
                Offset(v.width * 0.08f, v.height * 0.10f),
                maxSide * 0.10f
            )
            val localPressPhase = edgePhaseFromPress()
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.60.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.46.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = tinyCornerGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.46.dp.toPx()), blendMode = BlendMode.Screen)
                if (dynamicPower > 0.001f) {
                    drawTravellingBand(v.bandSeedA, v.bandAngleA, 0, 1.00f)
                    drawTravellingBand(v.bandSeedB, v.bandAngleB, 1, 0.78f)
                    if (v.bandCount >= 3) drawTravellingBand(v.bandSeedC, v.bandAngleC, 2, 0.58f)
                }
                if (press > 0.001f) {
                    val localStrength = (0.78f + pressBandBoost * 0.86f) * press * energy * alpha
                    drawRimSpark(localPressPhase, localStrength, 0.075f, localStrength.coerceIn(0f, 1.25f))
                    drawRimSpark(localPressPhase + 0.018f, localStrength * 0.46f, 0.045f, localStrength.coerceIn(0f, 1.10f))
                    drawRimSpark(localPressPhase - 0.018f, localStrength * 0.34f, 0.038f, localStrength.coerceIn(0f, 1.00f))
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
