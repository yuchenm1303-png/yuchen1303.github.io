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
                .drawUnifiedModelRimGlass(visuals)
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

                val pressRaw = pressAnim.value.coerceIn(0f, 1.16f)
                val lensRaw = lensAnim.value.coerceIn(0f, 1.12f)
                val pressProgress = unifiedModelStackSmooth(pressRaw.coerceIn(0f, 1f))
                val sweepProgress = releaseSweep.value.coerceIn(0f, 1.30f)
                val rebound = unifiedModelStackSmooth(((sweepProgress - 0.62f) / 0.58f).coerceIn(0f, 1f)) * (1f - pressProgress)
                val pressElasticity = 0.88f
                val pressScaleX = 1f + pressProgress * 0.018f * pressElasticity - rebound * 0.004f * pressElasticity
                val pressScaleY = 1f - pressProgress * 0.026f * pressElasticity + rebound * 0.010f * pressElasticity
                val pressShiftY = pressProgress * 1.70f * pressElasticity - rebound * 0.62f * pressElasticity

                fun updatePressCenter(position: Offset) {
                    pressCenter = Offset(
                        (position.x / cardSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        (position.y / cardSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    )
                }

                val visualWidth = with(density) { currentWidth.toPx() }
                val visualHeight = with(density) { currentHeight.toPx() }
                val flightScaleX = capsuleScaleX * selectedPulse
                val flightScaleY = capsuleScaleY * selectedPulse
                val flightLeft = tx + visualWidth * (1f - flightScaleX) * 0.5f
                val flightTop = ty + visualHeight * (1f - flightScaleY) * 0.5f
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
                        expansion = cardProgress.coerceIn(0f, 1f),
                        pressCenter = pressCenter
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
                                    scope.launch {
                                        pressAnim.stop()
                                        if (pressAnim.value < 0.12f) pressAnim.snapTo(0.12f)
                                        pressAnim.animateTo(0.86f, tween(150, easing = FastOutSlowInEasing))
                                        pressAnim.animateTo(0.68f, spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow))
                                    }
                                    scope.launch {
                                        lensAnim.stop()
                                        lensAnim.animateTo(0.24f, tween(130, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0.56f, tween(290, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.snapTo(0f)
                                        releaseSweep.animateTo(0.58f, tween(520, easing = FastOutSlowInEasing))
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
                                    } else {
                                        onToggleExpanded()
                                    }
                                    scope.launch {
                                        pressAnim.stop()
                                        pressAnim.animateTo(0f, tween(430, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        lensAnim.stop()
                                        lensAnim.animateTo(0.14f, tween(160, easing = FastOutSlowInEasing))
                                        lensAnim.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.animateTo(1.05f, tween(520, easing = FastOutSlowInEasing))
                                        releaseSweep.animateTo(1.20f, tween(160, easing = FastOutSlowInEasing))
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
    val expansion: Float,
    val pressCenter: Offset
)

@Composable
private fun UnifiedModelCardContent(model: ChatModel, selected: Boolean, expansionProgress: Float) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ModelStatusDot(selected = selected, expansionProgress = expansionProgress)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                model.shortLabel,
                color = Color.White.copy(alpha = if (selected) 0.97f else 0.82f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                model.id,
                color = Color.White.copy(alpha = if (selected) 0.58f else 0.40f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModelStatusDot(selected: Boolean, expansionProgress: Float) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val selectedGlow = Brush.radialGradient(
                    listOf(
                        Color(0xFF8DFFF3).copy(alpha = 0.26f),
                        Color(0xFF8DFFF3).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.66f
                )
                val idleGlow = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.060f + 0.040f * expansionProgress),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.48f
                )
                onDrawBehind {
                    if (selected) drawCircle(selectedGlow, radius = size.minDimension * 0.50f, center = center, blendMode = BlendMode.Screen)
                    else drawCircle(idleGlow, radius = size.minDimension * 0.40f, center = center, blendMode = BlendMode.Screen)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(if (selected) 9.dp else 7.dp)
                .graphicsLayer { alpha = if (selected) 1f else 0.52f + 0.22f * expansionProgress }
                .background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
        )
    }
}

private fun Modifier.drawUnifiedModelRimGlass(visuals: List<UnifiedModelCardVisual>): Modifier = drawWithCache {
    fun smooth(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    onDrawWithContent {
        fun drawCardBody(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return

            val energy = v.stackEnergy.coerceIn(0f, 1f)
            val selectedPower = if (v.selected) 1f else 0f
            val press = smooth(v.press.coerceIn(0f, 1f))
            val lens = smooth(v.lens.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(v.height / 2f, v.height / 2f)
            val bodySize = Size(v.width, v.height)
            val rimBleed = 3.0.dp.toPx()
            val bodyAlpha = energy * alpha
            val pressCenter = Offset(
                v.width * v.pressCenter.x.coerceIn(0f, 1f),
                v.height * v.pressCenter.y.coerceIn(0f, 1f)
            )

            val selectedOuterAura = Brush.radialGradient(
                listOf(
                    Color(0xFF8DFFF3).copy(alpha = 0.110f * selectedPower * alpha),
                    Color(0xFF4AECE2).copy(alpha = 0.046f * selectedPower * alpha),
                    Color.Transparent
                ),
                center = Offset(v.width * 0.18f, v.height * 0.18f),
                radius = maxSide * 0.72f
            )
            val bodyVeil = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.032f + selectedPower * 0.014f + lens * 0.012f) * bodyAlpha),
                    Color(0xFFB8F7FF).copy(alpha = (0.012f + selectedPower * 0.010f) * bodyAlpha),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = (0.116f + press * 0.032f) * bodyAlpha)
                ),
                0f,
                v.height
            )
            val cornerMist = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = (0.062f + selectedPower * 0.020f) * bodyAlpha),
                    Color(0xFF9DFFF4).copy(alpha = (0.025f + selectedPower * 0.024f) * bodyAlpha),
                    Color.Transparent
                ),
                center = Offset(v.width * 0.035f, v.height * 0.02f),
                radius = maxSide * 0.32f
            )
            val surfaceClear = Brush.radialGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF031026).copy(alpha = 0.030f * bodyAlpha),
                    Color(0xFF00040C).copy(alpha = 0.060f * bodyAlpha)
                ),
                center = Offset(v.width * 0.50f, v.height * 0.58f),
                radius = maxSide * 0.78f
            )
            val pressedLens = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.054f * press * bodyAlpha),
                    Color(0xFF8DFFF3).copy(alpha = 0.030f * press * bodyAlpha),
                    Color.Transparent
                ),
                center = pressCenter,
                radius = maxSide * (0.34f + 0.18f * press)
            )

            withTransform({ translate(v.left, v.top) }) {
                if (v.selected) {
                    drawRoundRect(
                        brush = selectedOuterAura,
                        topLeft = Offset(-rimBleed, -rimBleed),
                        size = Size(v.width + rimBleed * 2f, v.height + rimBleed * 2f),
                        cornerRadius = CornerRadius(v.height / 2f + rimBleed, v.height / 2f + rimBleed),
                        blendMode = BlendMode.Screen
                    )
                }
                drawRoundRect(brush = bodyVeil, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = surfaceClear, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                drawRoundRect(brush = cornerMist, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                if (press > 0.001f) {
                    drawRoundRect(brush = pressedLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                }
            }
        }

        fun drawCardRim(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return

            val energy = v.stackEnergy.coerceIn(0f, 1f)
            val selectedPower = if (v.selected) 1f else 0f
            val press = smooth(v.press.coerceIn(0f, 1f))
            val lens = smooth(v.lens.coerceIn(0f, 1f))
            val sweep = smooth(v.sweep.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val corner = CornerRadius(v.height / 2f, v.height / 2f)
            val rimInset = 0.62.dp.toPx()
            val innerInset = 1.55.dp.toPx()
            val rimSize = Size((v.width - rimInset * 2f).coerceAtLeast(1f), (v.height - rimInset * 2f).coerceAtLeast(1f))
            val innerSize = Size((v.width - innerInset * 2f).coerceAtLeast(1f), (v.height - innerInset * 2f).coerceAtLeast(1f))
            val rimPower = energy * alpha
            val selectedRim = (0.030f + selectedPower * 0.125f + press * 0.050f + lens * 0.020f) * rimPower
            val coolBias = 1f + selectedPower * 0.55f
            val pressCenter = Offset(
                v.width * v.pressCenter.x.coerceIn(0f, 1f),
                v.height * v.pressCenter.y.coerceIn(0f, 1f)
            )

            val outerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.185f * rimPower + selectedRim * 0.24f),
                    Color(0xFF98FFF2).copy(alpha = 0.070f * rimPower * coolBias + selectedRim),
                    Color.White.copy(alpha = 0.032f * rimPower),
                    Color.Transparent,
                    Color(0xFF010817).copy(alpha = 0.230f * rimPower),
                    Color.White.copy(alpha = 0.036f * rimPower)
                ),
                Offset.Zero,
                Offset(v.width, v.height)
            )
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.155f * rimPower + selectedRim * 0.52f),
                    Color(0xFFB7FFF7).copy(alpha = 0.100f * rimPower + selectedRim * 0.72f),
                    Color.White.copy(alpha = 0.092f * rimPower),
                    Color.Transparent
                ),
                0f,
                v.width
            )
            val innerDepth = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.040f * rimPower),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = 0.240f * rimPower),
                    Color(0xFF89FFF3).copy(alpha = 0.035f * rimPower + selectedRim * 0.28f)
                ),
                Offset(v.width * 0.08f, 0f),
                Offset(v.width * 0.92f, v.height)
            )
            val bottomWeight = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF00040C).copy(alpha = 0.300f * rimPower + 0.070f * press)
                ),
                v.height * 0.42f,
                v.height
            )
            val cornerCatch = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.150f * rimPower + selectedRim * 0.70f),
                    Color(0xFF9DFFF4).copy(alpha = 0.060f * rimPower + selectedRim * 0.36f),
                    Color.Transparent
                ),
                center = Offset(v.width * 0.055f, v.height * 0.060f),
                radius = maxSide * 0.17f
            )
            val pressEdge = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.180f * press * rimPower),
                    Color(0xFF8DFFF3).copy(alpha = 0.120f * press * rimPower * coolBias),
                    Color.Transparent
                ),
                center = pressCenter,
                radius = maxSide * 0.36f
            )
            val sweepX = -0.18f + sweep * 1.38f
            val releaseGlint = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFF7DFFF0).copy(alpha = 0.085f * sweep * rimPower * coolBias),
                    Color.White.copy(alpha = 0.210f * sweep * rimPower),
                    Color(0xFFE9D8FF).copy(alpha = 0.058f * sweep * rimPower),
                    Color.Transparent
                ),
                Offset(v.width * (sweepX - 0.15f), 0f),
                Offset(v.width * (sweepX + 0.12f), v.height * 0.86f)
            )

            withTransform({ translate(v.left, v.top) }) {
                if (v.selected) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF8DFFF3).copy(alpha = 0.100f * alpha),
                                Color.Transparent,
                                Color(0xFF8DFFF3).copy(alpha = 0.050f * alpha)
                            ),
                            Offset.Zero,
                            Offset(v.width, v.height)
                        ),
                        topLeft = Offset(-1.15.dp.toPx(), -1.15.dp.toPx()),
                        size = Size(v.width + 2.30.dp.toPx(), v.height + 2.30.dp.toPx()),
                        cornerRadius = CornerRadius(v.height / 2f + 1.15.dp.toPx(), v.height / 2f + 1.15.dp.toPx()),
                        style = Stroke(1.25.dp.toPx()),
                        blendMode = BlendMode.Screen
                    )
                }
                drawRoundRect(brush = outerRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.92.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.72.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerDepth, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.52.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = bottomWeight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.92.dp.toPx()), blendMode = BlendMode.Multiply)
                drawRoundRect(brush = cornerCatch, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(1.08.dp.toPx()), blendMode = BlendMode.Screen)
                if (sweep > 0.001f) {
                    drawRoundRect(brush = releaseGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.82.dp.toPx()), blendMode = BlendMode.Plus)
                }
                if (press > 0.001f) {
                    drawRoundRect(brush = pressEdge, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(1.12.dp.toPx() + 0.30.dp.toPx() * press), blendMode = BlendMode.Screen)
                }
            }
        }

        visuals.forEach { drawCardBody(it) }
        drawContent()
        visuals.forEach { drawCardRim(it) }
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
