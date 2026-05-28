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
        val spec = LightweightPrismCapsuleDefaults.LabMax

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

                val pressAnim = remember(model.id) { Animatable(0f) }
                val releaseSweep = remember(model.id) { Animatable(0f) }
                var pressCenter by remember(model.id) { mutableStateOf(Offset(0.50f, 0.50f)) }
                var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
                val pressRaw = pressAnim.value.coerceIn(0f, 1.14f)
                val pressProgress = unifiedModelStackSmooth(pressRaw.coerceIn(0f, 1f))
                val sweepProgress = releaseSweep.value.coerceIn(0f, 1.20f)
                val rebound = unifiedModelStackSmooth(((sweepProgress - 0.62f) / 0.58f).coerceIn(0f, 1f)) * (1f - pressProgress)
                val pressElasticity = spec.pressElasticity.coerceIn(0f, 1.25f)
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
                        sweep = sweepProgress,
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
                                        if (pressAnim.value < 0.20f) pressAnim.snapTo(0.20f)
                                        pressAnim.animateTo(1.00f, tween(145, easing = FastOutSlowInEasing))
                                        pressAnim.animateTo(0.82f, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow))
                                    }
                                    scope.launch {
                                        releaseSweep.stop()
                                        releaseSweep.snapTo(0f)
                                        releaseSweep.animateTo(0.34f, tween(210, easing = FastOutSlowInEasing))
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
                                        releaseSweep.stop()
                                        releaseSweep.animateTo(1.20f, tween(540, easing = FastOutSlowInEasing))
                                        releaseSweep.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
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
    val sweep: Float,
    val pressCenter: Offset
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
            val center = Offset(v.width * v.pressCenter.x.coerceIn(0f, 1f), v.height * v.pressCenter.y.coerceIn(0f, 1f))
            val surface = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f)),
                    Color(0xFFD9F1FF).copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f) * 0.52f),
                    Color(0xFFEAF7FF).copy(alpha = surfaceAlpha.coerceIn(0f, 0.20f) * 0.20f),
                    Color(0xFF000816).copy(alpha = bottomDepth.coerceIn(0f, 0.35f) * 0.36f)
                ),
                0f,
                v.height
            )
            val centerMist = Brush.radialGradient(
                listOf(
                    Color(0xFFEAF7FF).copy(alpha = (0.036f + surfaceAlpha * 0.58f).coerceIn(0f, 0.14f)),
                    Color(0xFFB7DBFF).copy(alpha = (0.014f + surfaceAlpha * 0.18f).coerceIn(0f, 0.07f)),
                    Color.Transparent
                ),
                Offset(v.width * 0.50f, v.height * 0.52f),
                maxSide * 0.62f
            )
            val topLens = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = topHighlight.coerceIn(0f, 0.5f)),
                    Color(0xFFE8FFFF).copy(alpha = topHighlight.coerceIn(0f, 0.5f) * 0.36f),
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
                    Color(0xFF071B3D).copy(alpha = spec.pressDarken.coerceIn(0f, 0.4f) * 0.30f * press * alpha),
                    Color(0xFF01040C).copy(alpha = spec.pressDarken.coerceIn(0f, 0.4f) * 0.78f * press * alpha)
                ),
                center,
                maxSide * (0.58f + 0.10f * press)
            )
            val pressLens = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.pressGlow.coerceIn(0f, 0.9f) * 0.34f * press * alpha),
                    Color(0xFFEFFFFF).copy(alpha = spec.pressGlow.coerceIn(0f, 0.9f) * 0.18f * press * alpha),
                    prismColor(Color(0xFF73F7FF), spec.pressGlow * 0.11f * press * alpha, spec.rainbowSaturation),
                    prismColor(Color(0xFFFF85DD), spec.pressGlow * 0.085f * press * alpha, spec.rainbowSaturation),
                    Color.Transparent
                ),
                Offset(center.x, center.y - v.height * 0.07f),
                maxSide * (0.18f + 0.08f * press)
            )
            val lensSheen = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = spec.pressGlow.coerceIn(0f, 0.9f) * 0.18f * press * alpha),
                    prismColor(Color(0xFF88F4FF), spec.pressGlow * 0.10f * press * alpha, spec.rainbowSaturation),
                    Color.Transparent
                ),
                Offset(center.x - v.width * 0.20f, center.y - v.height * 0.44f),
                Offset(center.x + v.width * 0.26f, center.y + v.height * 0.22f)
            )
            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = surface, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = centerMist, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = topLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(brush = bottomShade, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                if (press > 0.001f) {
                    drawRoundRect(brush = pressureDark, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
                    drawRoundRect(brush = pressLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = lensSheen, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
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
            val sweep = v.sweep.coerceIn(0f, 1.20f)
            val press = prismSmooth(v.press.coerceIn(0f, 1f))
            val center = Offset(v.width * v.pressCenter.x.coerceIn(0f, 1f), v.height * v.pressCenter.y.coerceIn(0f, 1f))
            val topNear = (1f - v.pressCenter.y / 0.42f).coerceIn(0f, 1f) * press
            val bottomNear = (1f - (1f - v.pressCenter.y) / 0.42f).coerceIn(0f, 1f) * press
            val leftNear = (1f - v.pressCenter.x / 0.42f).coerceIn(0f, 1f) * press
            val rightNear = (1f - (1f - v.pressCenter.x) / 0.42f).coerceIn(0f, 1f) * press
            val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)
            val sweepT = prismSmooth(sweep.coerceIn(0f, 1f))
            val sweepX = -0.32f + sweepT * 1.64f
            val rimBandPower = spec.rainbowRimAlpha.coerceIn(0f, 1f) * energy * alpha * (0.58f + 0.16f * selectedEnergy)
            val dynamicSweep = (press * 0.58f + sweep * 0.86f).coerceIn(0f, 1.18f)
            fun prismBandBrush(start: Offset, end: Offset, strength: Float): Brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    prismColor(Color(0xFF68F7FF), strength * 0.26f, spec.rainbowSaturation),
                    prismColor(Color(0xFFFF7CE1), strength * 0.24f, spec.rainbowSaturation),
                    Color.White.copy(alpha = (strength * 0.16f).coerceIn(0f, 1f)),
                    prismColor(Color(0xFFFFE785), strength * 0.20f, spec.rainbowSaturation),
                    prismColor(Color(0xFF7BFF9E), strength * 0.18f, spec.rainbowSaturation),
                    Color.Transparent
                ),
                start = start,
                end = end
            )
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFDFFFFF).copy(alpha = (spec.rimAlpha * energy * alpha * 0.26f + spec.topHighlight * energy * alpha * 0.24f).coerceIn(0f, 1f)),
                    Color.White.copy(alpha = (spec.rimAlpha * energy * alpha * 0.30f + spec.topHighlight * energy * alpha * 0.36f).coerceIn(0f, 1f)),
                    Color.Transparent
                ),
                0f,
                v.width
            )
            val innerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = spec.innerRimAlpha.coerceIn(0f, 0.7f) * 0.70f * energy * alpha),
                    Color.Transparent,
                    Color(0xFF00091E).copy(alpha = spec.bottomDepth.coerceIn(0f, 0.35f) * 0.68f * energy * alpha),
                    Color.White.copy(alpha = spec.innerRimAlpha.coerceIn(0f, 0.7f) * 0.22f * energy * alpha)
                ),
                Offset(v.width * 0.08f, 0f),
                Offset(v.width * 0.92f, v.height)
            )
            val cornerLight = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.90f * energy * alpha),
                    Color(0xFFCFFFFF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.32f * energy * alpha),
                    Color.Transparent
                ),
                Offset(v.width * 0.055f, v.height * 0.045f),
                maxSide * 0.30f
            )
            val rightCornerLight = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.58f * energy * alpha),
                    Color(0xFFB7F7FF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.22f * energy * alpha),
                    Color.Transparent
                ),
                Offset(v.width * 0.94f, v.height * 0.10f),
                maxSide * 0.24f
            )
            val lowerCornerLight = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.28f * energy * alpha),
                    Color(0xFFC7E9FF).copy(alpha = spec.cornerCatchlight.coerceIn(0f, 0.7f) * 0.12f * energy * alpha),
                    Color.Transparent
                ),
                Offset(v.width * 0.92f, v.height * 0.88f),
                maxSide * 0.26f
            )
            val rimBandMain = prismBandBrush(Offset(v.width * (sweepX - 0.24f), v.height * -0.08f), Offset(v.width * (sweepX + 0.24f), v.height * 1.06f), rimBandPower * (0.56f + 0.18f * pressBandBoost))
            val pressEdgeSweep = prismBandBrush(Offset(center.x - v.width * 0.30f, center.y - v.height * 0.82f), Offset(center.x + v.width * 0.24f, center.y + v.height * 0.82f), spec.rainbowPressEdge * press * energy * alpha * 0.95f)
            val releaseSweep = prismBandBrush(Offset(v.width * (sweepX - 0.20f), v.height * -0.04f), Offset(v.width * (sweepX + 0.24f), v.height * 1.04f), (spec.rainbowSweepAlpha * 0.90f + spec.pressSweep * 0.18f) * dynamicSweep * energy * alpha)
            val secondarySweep = prismBandBrush(Offset(v.width * (1.10f - sweepX), v.height * 0.02f), Offset(v.width * (0.62f - sweepX), v.height * 1.02f), spec.rainbowSweepAlpha * 0.28f * sweep * energy * alpha)

            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.78.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.58.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = cornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.86.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = rightCornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.74.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = lowerCornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.62.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = rimBandMain, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((spec.rimWidth + spec.rainbowRimWidth * 0.56f).dp.toPx()), blendMode = BlendMode.Screen)
                if (press > 0.001f) {
                    drawRoundRect(brush = pressEdgeSweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.86f + 1.18f * press).dp.toPx()), blendMode = BlendMode.Plus)
                }
                if (dynamicSweep > 0.001f) {
                    drawRoundRect(brush = releaseSweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.54f + 0.74f * dynamicSweep).dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = secondarySweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.38f + 0.42f * sweep).dp.toPx()), blendMode = BlendMode.Screen)
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
    val elastic = sin(p * PI.toFloat()).coerceAtLeast(0f) * 0.026f * (1f - abs(0.5f - p) * 1.6f).coerceIn(0f, 1f)
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
