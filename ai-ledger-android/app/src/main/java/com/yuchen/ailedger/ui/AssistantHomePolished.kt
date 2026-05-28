package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val ModelPressInEasing = CubicBezierEasing(0.10f, 0.00f, 0.06f, 1.00f)
private val ModelLensInEasing = CubicBezierEasing(0.18f, 0.00f, 0.10f, 1.00f)
private val ModelReleaseEasing = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)

@Composable
fun AssistantScreenV2(
    state: AssistantUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onDraftCommand: (String) -> Unit,
    onModelSelected: (ChatModel) -> Unit,
    onPickImage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleOnline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 68.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssistantEntrance(delayMs = 0, initialOffsetY = -10, initialScale = 0.98f) {
            AssistantHeroV2(state = state, onOpenTools = onOpenTools, onOpenSettings = onOpenSettings)
        }
        AssistantEntrance(delayMs = 46, initialOffsetY = 16, initialScale = 0.965f) {
            ModelAndNetworkPanel(state = state, onModelSelected = onModelSelected, onToggleOnline = onToggleOnline)
        }
        AssistantEntrance(delayMs = 92, modifier = Modifier.weight(1f), initialOffsetY = 30, initialScale = 0.955f) {
            ChatPanelV2(state = state, modifier = Modifier.fillMaxWidth(), onDraftCommand = onDraftCommand, onPickImage = onPickImage)
        }
        AssistantEntrance(delayMs = 138, initialOffsetY = 18, initialScale = 0.965f) {
            ComposerBarV2(state = state, onComposerChange = onComposerChange, onSend = onSend, onPickImage = onPickImage)
        }
    }
}

@Composable
private fun AssistantEntrance(
    delayMs: Long,
    modifier: Modifier = Modifier,
    initialOffsetY: Int = 24,
    initialScale: Float = 0.96f,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0L) delay(delayMs)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY } +
            scaleIn(initialScale = initialScale, animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.985f, animationSpec = tween(120))
    ) { content() }
}

@Composable
private fun AssistantHeroV2(state: AssistantUiState, onOpenTools: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("AI ASSISTANT", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("AI 助手", color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
            Text("直接说需求，我来帮你拆成动作。", color = Color.White.copy(alpha = 0.54f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RoundIconButtonV2("▦", state, onClick = onOpenTools)
            RoundIconButtonV2("⚙", state, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun ModelAndNetworkPanel(state: AssistantUiState, onModelSelected: (ChatModel) -> Unit, onToggleOnline: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val panelHeight by animateDpAsState(
        targetValue = if (expanded) 224.dp else 58.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "model-stack-panel-height"
    )
    ModelStackSelector(
        state = state,
        expanded = expanded,
        modifier = Modifier.fillMaxWidth().height(panelHeight),
        onToggleExpanded = { if (!state.isSending) expanded = !expanded },
        onSelected = { model ->
            if (!state.isSending) {
                onModelSelected(model)
                expanded = false
            }
        }
    )
}

@Composable
private fun ModelStackSelector(
    state: AssistantUiState,
    expanded: Boolean,
    modifier: Modifier,
    onToggleExpanded: () -> Unit,
    onSelected: (ChatModel) -> Unit
) {
    val fieldProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = if (expanded) 520 else 360, easing = FastOutSlowInEasing),
        label = "model-stack-ambient-rainbow-field"
    )
    BoxWithConstraints(modifier = modifier.modelStackAmbientAura(fieldProgress, state.glassIntensity)) {
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
        models.forEachIndexed { index, model ->
            val selected = model == selectedModel
            val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
            val expandedX = when (index) { 1, 3 -> halfWidth + gap else -> 0.dp }
            val expandedY = when (index) { 2, 3 -> rowStep; 4 -> rowStep * 2f; else -> 0.dp }
            val expandedWidth = if (index == 4) maxWidth else halfWidth
            val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
            val collapsedY = if (selected) 0.dp else (stackRank * 1.8f).dp
            val collapsedAlpha = if (selected) 1f else 0.40f + (4 - stackRank).coerceAtLeast(0) * 0.075f
            val staggerRank = when (index) { 0 -> 0; 2 -> 1; 1 -> 2; 3 -> 3; else -> 4 }
            val z = if (expanded) 30f - index else if (selected) 50f else 40f - stackRank
            ModelStackCard(
                model = model,
                selected = selected,
                state = state,
                expanded = expanded,
                staggerRank = staggerRank,
                expandedX = expandedX,
                expandedY = expandedY,
                expandedWidth = expandedWidth,
                expandedHeight = expandedHeight,
                collapsedX = collapsedX,
                collapsedY = collapsedY,
                collapsedWidth = collapsedWidth,
                collapsedHeight = collapsedHeight,
                collapsedAlpha = collapsedAlpha,
                zIndex = z,
                stackRank = stackRank,
                onClick = { if (expanded) onSelected(model) else onToggleExpanded() }
            )
        }
    }
}

@Composable
private fun ModelStackCard(
    model: ChatModel,
    selected: Boolean,
    state: AssistantUiState,
    expanded: Boolean,
    staggerRank: Int,
    expandedX: Dp,
    expandedY: Dp,
    expandedWidth: Dp,
    expandedHeight: Dp,
    collapsedX: Dp,
    collapsedY: Dp,
    collapsedWidth: Dp,
    collapsedHeight: Dp,
    collapsedAlpha: Float,
    zIndex: Float,
    stackRank: Int,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val cardProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 520 else 340,
            delayMillis = if (expanded) staggerRank * 30 else (ChatModel.entries.lastIndex - staggerRank) * 16,
            easing = FastOutSlowInEasing
        ),
        label = "model-card-spatial-arc-progress-${model.id}"
    )
    val eased = modelStackMotionEase(cardProgress)
    val currentWidth = lerpDp(collapsedWidth, expandedWidth, eased)
    val currentHeight = lerpDp(collapsedHeight, expandedHeight, eased)
    val currentAlpha = lerpFloat(collapsedAlpha, 1f, eased)
    val startX = with(density) { collapsedX.toPx() }
    val startY = with(density) { collapsedY.toPx() }
    val endX = with(density) { expandedX.toPx() }
    val endY = with(density) { expandedY.toPx() }
    val dx = endX - startX
    val dy = endY - startY
    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val speedPulse = modelStackSpeedPulse(cardProgress)
    val unitX = dx / distance
    val unitY = dy / distance
    val liftPx = -with(density) { (8.dp + (staggerRank * 0.85f).dp).toPx() } * speedPulse
    val curveX = startX + dx * eased
    val curveY = startY + dy * eased + liftPx
    val brake = modelStackArrivalBrake(cardProgress)
    val returnBrake = modelStackReturnBrake(cardProgress)
    val overshootPx = with(density) { 5.4.dp.toPx() } * brake - with(density) { 2.8.dp.toPx() } * returnBrake
    val tx = curveX + unitX * overshootPx
    val ty = curveY + unitY * overshootPx
    val settled = cardProgress < 0.025f || cardProgress > 0.985f
    val capsuleScaleX = modelStackCapsuleScaleX(cardProgress)
    val capsuleScaleY = modelStackCapsuleScaleY(cardProgress)
    val selectedPulse by animateFloatAsState(
        targetValue = if (selected && settled) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "model-card-selected-pulse-${model.id}"
    )
    val transformModifier = Modifier
        .width(currentWidth)
        .height(currentHeight)
        .zIndex(zIndex)
        .graphicsLayer {
            translationX = tx
            translationY = ty
            scaleX = capsuleScaleX * selectedPulse
            scaleY = capsuleScaleY * selectedPulse
            alpha = currentAlpha
            shadowElevation = 0f
        }
    ModelFrostCapsule(
        model = model,
        selected = selected,
        state = state,
        modifier = transformModifier,
        expansionProgress = cardProgress.coerceIn(0f, 1f),
        moving = speedPulse,
        stopPulse = (brake + returnBrake).coerceIn(0f, 1f),
        settled = settled,
        stackRank = stackRank,
        onClick = onClick
    )
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun lerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun smoothGlass(value: Float): Float { val x = value.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

private fun modelStackMotionEase(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val smooth = p * p * (3f - 2f * p)
    val elastic = sin(p * PI.toFloat()).coerceAtLeast(0f) * 0.038f * (1f - abs(0.5f - p) * 1.45f).coerceIn(0f, 1f)
    return (smooth + elastic).coerceIn(0f, 1f)
}
private fun modelStackSpeedPulse(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun modelStackArrivalBrake(progress: Float): Float = sin(((progress - 0.68f) / 0.32f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun modelStackReturnBrake(progress: Float): Float = sin(((0.30f - progress) / 0.30f).coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)
private fun modelStackCapsuleScaleX(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    val speed = modelStackSpeedPulse(p)
    val arrive = modelStackArrivalBrake(p)
    val back = modelStackReturnBrake(p)
    return 1f + 0.040f * speed + 0.030f * arrive - 0.014f * back
}
private fun modelStackCapsuleScaleY(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    val speed = modelStackSpeedPulse(p)
    val arrive = modelStackArrivalBrake(p)
    val back = modelStackReturnBrake(p)
    return 1f - 0.074f * speed + 0.038f * arrive + 0.016f * back
}

@Composable
private fun ModelFrostCapsule(
    model: ChatModel,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    expansionProgress: Float,
    moving: Float,
    stopPulse: Float,
    settled: Boolean,
    stackRank: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val stackEnergy = if (stackRank > 0) 0.44f else 1f
    LightweightModelCapsule(
        model = model,
        selected = selected,
        state = state,
        modifier = modifier,
        expansionProgress = expansionProgress,
        moving = moving,
        stopPulse = stopPulse,
        stackEnergy = stackEnergy,
        shape = shape,
        showContent = selected || expansionProgress > 0.035f || settled,
        onClick = onClick
    )
}

@Composable
private fun LightweightModelCapsule(
    model: ChatModel,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    expansionProgress: Float,
    moving: Float,
    stopPulse: Float,
    stackEnergy: Float,
    shape: RoundedCornerShape,
    showContent: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val shellPress = remember(model.id) { Animatable(0f) }
    val lensPress = remember(model.id) { Animatable(0f) }
    val rimSweep = remember(model.id) { Animatable(0f) }
    var pressCenter by remember(model.id) { mutableStateOf(Offset(0.50f, 0.50f)) }
    var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
    var flowSeed by remember(model.id) { mutableStateOf(0.35f) }
    var flowDirection by remember(model.id) { mutableStateOf(1f) }
    val pressValue = shellPress.value.coerceIn(-0.20f, 1.18f)
    val positivePress = pressValue.coerceAtLeast(0f)
    val rebound = smoothGlass((-pressValue / 0.18f).coerceIn(0f, 1f))
    val surfacePress = smoothGlass((positivePress / 0.86f).coerceIn(0f, 1f))
    val lensValue = lensPress.value.coerceIn(0f, 1.12f)
    val lensProgress = smoothGlass(lensValue.coerceIn(0f, 1f))
    val releaseSweep = rimSweep.value.coerceIn(0f, 1.24f)
    val selectedEnergy = if (selected) 1f else 0f
    val stackSafeEnergy = maxOf(stackEnergy, 0.44f)
    val settleSweep = smoothGlass(stopPulse) * 0.38f
    val pressBloom = maxOf(surfacePress, lensProgress * 0.88f) * 0.58f
    val flightGlow = smoothGlass(moving) * 0.12f
    val sweepEnergy = (settleSweep + pressBloom + flightGlow + releaseSweep * 0.78f).coerceIn(0f, 1.18f)
    val opticsPress = maxOf(surfacePress, lensProgress * 0.86f, rebound * 0.24f)
    val elasticity = stackSafeEnergy * state.motionIntensity.coerceIn(0f, 1f)

    fun updatePressCenter(position: Offset) {
        pressCenter = Offset(
            (position.x / cardSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
            (position.y / cardSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { size -> cardSize = Size(size.width.coerceAtLeast(1).toFloat(), size.height.coerceAtLeast(1).toFloat()) }
            .pointerInput(state.isSending, model.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updatePressCenter(down.position)
                    if (!state.isSending) {
                        flowSeed = Random.nextFloat()
                        flowDirection = if (Random.nextBoolean()) 1f else -1f
                        scope.launch {
                            shellPress.stop()
                            if (shellPress.value < 0.18f) shellPress.snapTo(0.18f)
                            shellPress.animateTo(0.74f, tween(118, easing = ModelPressInEasing))
                            shellPress.animateTo(0.92f, tween(210, easing = FastOutSlowInEasing))
                            shellPress.animateTo(0.78f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
                        }
                        scope.launch {
                            lensPress.stop()
                            lensPress.animateTo(0.24f, tween(130, easing = ModelLensInEasing))
                            lensPress.animateTo(0.92f, tween(330, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            rimSweep.stop()
                            rimSweep.snapTo(0f)
                            rimSweep.animateTo(0.36f, tween(210, easing = FastOutSlowInEasing))
                        }
                    }
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (tracked != null) {
                            updatePressCenter(tracked.position)
                            if (!tracked.pressed) {
                                released = true
                                break
                            }
                        }
                        if (event.changes.none { it.pressed }) {
                            released = true
                            break
                        }
                    }
                    if (!state.isSending) {
                        if (released) onClick()
                        scope.launch {
                            shellPress.stop()
                            shellPress.animateTo(-0.130f, tween(150, easing = ModelReleaseEasing))
                            shellPress.animateTo(0.045f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow))
                            shellPress.animateTo(0f, spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow))
                        }
                        scope.launch {
                            lensPress.stop()
                            lensPress.animateTo(0.42f, tween(180, easing = ModelReleaseEasing))
                            lensPress.animateTo(0f, tween(620, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            rimSweep.stop()
                            rimSweep.animateTo(1.18f, tween(560, easing = FastOutSlowInEasing))
                            rimSweep.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pressCenter.x, pressCenter.y)
                scaleX = 1f + surfacePress * 0.030f * elasticity - rebound * 0.010f * elasticity
                scaleY = 1f - surfacePress * 0.050f * elasticity + rebound * 0.018f * elasticity
                translationY = surfacePress * 2.8f * elasticity - rebound * 1.1f * elasticity
                shadowElevation = 0f
            }
            .clip(shape)
            .modelPrismSampleSurface(
                radius = 30f,
                surfaceAlpha = (0.044f + 0.014f * selectedEnergy + 0.018f * opticsPress) * stackSafeEnergy,
                rimAlpha = (0.12f + 0.055f * selectedEnergy + 0.04f * expansionProgress) * stackSafeEnergy,
                rimWidth = if (selected) 1.12f else 0.94f,
                topHighlight = (0.18f + 0.07f * selectedEnergy + 0.08f * opticsPress) * stackSafeEnergy,
                topHighlightHeight = 0.26f,
                innerRimAlpha = (0.12f + 0.05f * selectedEnergy + 0.04f * lensProgress) * stackSafeEnergy,
                bottomDepth = (0.082f + 0.025f * opticsPress) * stackSafeEnergy,
                cornerCatchlight = (0.072f + 0.060f * selectedEnergy + 0.05f * releaseSweep) * stackSafeEnergy,
                press = opticsPress,
                sweep = sweepEnergy,
                pressCenter = pressCenter,
                flowSeed = flowSeed,
                flowDirection = flowDirection,
                pressGlow = 1.74f * stackSafeEnergy,
                pressEdgeBoost = 1.38f * stackSafeEnergy,
                pressSweep = 1.48f * stackSafeEnergy,
                pressDarken = 0.10f,
                rainbowRimAlpha = (2.65f + 0.95f * selectedEnergy + 0.36f * expansionProgress + 0.18f * moving + 0.30f * stopPulse + 0.46f * releaseSweep) * stackSafeEnergy,
                rainbowRimWidth = 2.42f + 0.38f * selectedEnergy + 0.28f * opticsPress,
                rainbowPressEdge = 2.80f + 0.52f * selectedEnergy,
                rainbowSweepAlpha = 2.88f + 0.56f * selectedEnergy + 0.34f * releaseSweep,
                rainbowCornerAlpha = (0.72f + 0.24f * selectedEnergy + 0.36f * releaseSweep) * stackSafeEnergy,
                rainbowSaturation = 3.00f
            )
    ) {
        if (showContent) ModelStackCardContent(model = model, selected = selected, expansionProgress = expansionProgress, press = opticsPress)
    }
}

private fun Modifier.modelStackAmbientAura(expansion: Float, glassIntensity: Float): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val p = smoothGlass(expansion.coerceIn(0f, 1f))
    val alpha = (glassIntensity.coerceIn(0.45f, 1.35f) * p).coerceIn(0f, 1f)
    val mainGlow = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.045f * alpha),
            Color(0xFFFF7CE1).copy(alpha = 0.050f * alpha),
            Color(0xFF68F7FF).copy(alpha = 0.044f * alpha),
            Color.Transparent
        ),
        Offset(w * 0.50f, h * 0.46f),
        maxOf(w, h) * 0.78f
    )
    val crossing = Brush.linearGradient(
        listOf(
            Color.Transparent,
            Color(0xFF65F7FF).copy(alpha = 0.038f * alpha),
            Color(0xFFFF7CE1).copy(alpha = 0.046f * alpha),
            Color(0xFFFFE785).copy(alpha = 0.034f * alpha),
            Color.Transparent
        ),
        Offset(w * -0.12f, h * 0.08f),
        Offset(w * 1.12f, h * 0.92f)
    )
    onDrawWithContent {
        if (alpha > 0.001f) {
            drawRect(mainGlow, blendMode = BlendMode.Screen)
            drawRect(crossing, blendMode = BlendMode.Plus)
        }
        drawContent()
    }
}

private fun Modifier.modelPrismSampleSurface(
    radius: Float,
    surfaceAlpha: Float,
    rimAlpha: Float,
    rimWidth: Float,
    topHighlight: Float,
    topHighlightHeight: Float,
    innerRimAlpha: Float,
    bottomDepth: Float,
    cornerCatchlight: Float,
    press: Float,
    sweep: Float,
    pressCenter: Offset,
    flowSeed: Float,
    flowDirection: Float,
    pressGlow: Float,
    pressEdgeBoost: Float,
    pressSweep: Float,
    pressDarken: Float,
    rainbowRimAlpha: Float,
    rainbowRimWidth: Float,
    rainbowPressEdge: Float,
    rainbowSweepAlpha: Float,
    rainbowCornerAlpha: Float,
    rainbowSaturation: Float
): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val maxSide = maxOf(w, h)
    val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
    val rimInset = 0.62.dp.toPx()
    val innerInset = 1.72.dp.toPx()
    val bodySize = Size(w, h)
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val innerSize = Size((w - innerInset * 2f).coerceAtLeast(1f), (h - innerInset * 2f).coerceAtLeast(1f))
    val centerNorm = Offset(pressCenter.x.coerceIn(0f, 1f), pressCenter.y.coerceIn(0f, 1f))
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
    val safePress = press.coerceIn(0f, 1.18f)
    val topNear = (1f - centerNorm.y / 0.42f).coerceIn(0f, 1f) * safePress
    val bottomNear = (1f - (1f - centerNorm.y) / 0.42f).coerceIn(0f, 1f) * safePress
    val leftNear = (1f - centerNorm.x / 0.42f).coerceIn(0f, 1f) * safePress
    val rightNear = (1f - (1f - centerNorm.x) / 0.42f).coerceIn(0f, 1f) * safePress
    val sweepT = smoothGlass(sweep.coerceIn(0f, 1f))
    val seedShift = (flowSeed - 0.5f) * 0.28f
    val sweepX = if (flowDirection >= 0f) -0.34f + seedShift + sweepT * 1.70f else 1.34f + seedShift - sweepT * 1.70f
    val sat = rainbowSaturation.coerceIn(0f, 3.0f)
    fun prism(color: Color, alpha: Float): Color = color.copy(alpha = (alpha * (0.16f + sat * 0.84f)).coerceIn(0f, 1f))
    fun prismBandBrush(start: Offset, end: Offset, strength: Float): Brush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            prism(Color(0xFF64F7FF), strength * 0.26f),
            prism(Color(0xFFFF5CE1), strength * 0.30f),
            Color.White.copy(alpha = (strength * 0.16f).coerceIn(0f, 0.78f)),
            prism(Color(0xFFFFE36E), strength * 0.25f),
            prism(Color(0xFF74FF9E), strength * 0.22f),
            prism(Color(0xFF7994FF), strength * 0.20f),
            Color.Transparent
        ),
        start = start,
        end = end
    )
    val surface = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = surfaceAlpha.coerceIn(0f, 0.34f)),
            Color(0xFFE1F6FF).copy(alpha = surfaceAlpha.coerceIn(0f, 0.34f) * 0.34f),
            Color.Transparent,
            Color(0xFF000816).copy(alpha = bottomDepth.coerceIn(0f, 0.48f) * 0.46f)
        ),
        0f,
        h
    )
    val auroraBody = Brush.radialGradient(
        listOf(
            Color.White.copy(alpha = 0.038f * safePress),
            prism(Color(0xFF68F7FF), 0.065f * safePress),
            prism(Color(0xFFFF7CE1), 0.052f * safePress),
            Color.Transparent
        ),
        Offset(center.x, center.y - h * 0.05f),
        maxSide * (0.58f + 0.10f * safePress)
    )
    val topLens = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = topHighlight.coerceIn(0f, 0.78f)), Color(0xFFE8FFFF).copy(alpha = topHighlight.coerceIn(0f, 0.78f) * 0.28f), Color.Transparent),
        0f,
        h * topHighlightHeight.coerceIn(0.05f, 0.60f)
    )
    val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF020815).copy(alpha = bottomDepth.coerceIn(0f, 0.48f))), h * 0.50f, h)
    val topHairline = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFDFFFFF).copy(alpha = rimAlpha.coerceIn(0f, 1f) * 0.22f + topHighlight * 0.22f), Color.White.copy(alpha = topHighlight * 0.40f), Color.Transparent), 0f, w)
    val innerRim = Brush.linearGradient(listOf(Color.White.copy(alpha = innerRimAlpha.coerceIn(0f, 0.56f) * 0.66f), Color.Transparent, Color(0xFF00091E).copy(alpha = bottomDepth.coerceIn(0f, 0.48f) * 0.74f), Color.White.copy(alpha = innerRimAlpha.coerceIn(0f, 0.56f) * 0.18f)), Offset(w * 0.08f, 0f), Offset(w * 0.92f, h))
    val cornerLight = Brush.radialGradient(listOf(Color.White.copy(alpha = cornerCatchlight.coerceIn(0f, 0.62f)), Color(0xFFCFFFFF).copy(alpha = cornerCatchlight.coerceIn(0f, 0.62f) * 0.24f), Color.Transparent), Offset(w * 0.055f, h * 0.045f), maxSide * 0.30f)
    val rainbowCorner = Brush.radialGradient(listOf(Color.White.copy(alpha = rainbowCornerAlpha.coerceIn(0f, 1f) * 0.22f), prism(Color(0xFFFF5CE1), rainbowCornerAlpha * 0.20f), prism(Color(0xFF68F7FF), rainbowCornerAlpha * 0.22f), prism(Color(0xFFFFE36E), rainbowCornerAlpha * 0.12f), Color.Transparent), Offset(w * 0.10f, h * 0.10f), maxSide * 0.30f)
    val pressureDark = Brush.radialGradient(listOf(Color.Transparent, Color(0xFF071B3D).copy(alpha = pressDarken.coerceIn(0f, 0.45f) * 0.36f * safePress), Color(0xFF01040C).copy(alpha = pressDarken.coerceIn(0f, 0.45f) * safePress)), center, maxSide * (0.74f + 0.12f * safePress))
    val prismPressLight = Brush.radialGradient(listOf(Color.White.copy(alpha = pressGlow.coerceIn(0f, 1.8f) * 0.14f * safePress), prism(Color(0xFF64F7FF), pressGlow * 0.32f * safePress), prism(Color(0xFFFF5CE1), pressGlow * 0.30f * safePress), prism(Color(0xFFFFE36E), pressGlow * 0.24f * safePress), prism(Color(0xFF74FF9E), pressGlow * 0.20f * safePress), Color.Transparent), center, maxSide * (0.28f + 0.22f * safePress))
    val prismLocalEdge = Brush.linearGradient(listOf(Color.Transparent, prism(Color(0xFF64F7FF), rainbowPressEdge * 0.34f * safePress + pressEdgeBoost * 0.08f * safePress), prism(Color(0xFFFF5CE1), rainbowPressEdge * 0.32f * safePress), Color.White.copy(alpha = pressEdgeBoost.coerceIn(0f, 2f) * 0.070f * safePress), prism(Color(0xFFFFE36E), rainbowPressEdge * 0.28f * safePress), prism(Color(0xFF74FF9E), rainbowPressEdge * 0.24f * safePress), Color.Transparent), Offset(center.x - w * 0.26f, center.y - h * 0.78f), Offset(center.x + w * 0.24f, center.y + h * 0.78f))
    val rimBandPower = rainbowRimAlpha.coerceIn(0f, 4.0f)
    val pressBandBoost = (topNear + bottomNear + leftNear + rightNear).coerceIn(0f, 1f)
    val rimBandMain = prismBandBrush(Offset(w * (sweepX - 0.22f), h * -0.08f), Offset(w * (sweepX + 0.30f), h * 1.06f), rimBandPower * (0.75f + 0.25f * pressBandBoost))
    val rimBandCounter = prismBandBrush(Offset(w * (1.16f - sweepX), h * 0.02f), Offset(w * (0.50f - sweepX), h * 1.00f), rimBandPower * 0.60f * (0.70f + 0.30f * pressBandBoost))
    val rimBandTop = prismBandBrush(Offset(w * (sweepX - 0.18f), h * 0.00f), Offset(w * (sweepX + 0.34f), h * 0.28f), rimBandPower * 0.50f * (0.68f + 0.32f * topNear))
    val prismSweep = prismBandBrush(Offset(w * (sweepX - 0.24f), h * -0.04f), Offset(w * (sweepX + 0.32f), h * 1.04f), rainbowSweepAlpha.coerceIn(0f, 4.0f) * sweep + pressSweep.coerceIn(0f, 2.0f) * 0.14f * sweep)
    onDrawWithContent {
        drawRoundRect(surface, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(auroraBody, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(topLens, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(bottomShade, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
        if (safePress > 0.001f) {
            drawRoundRect(pressureDark, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
            drawRoundRect(prismPressLight, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
        }
        drawContent()
        drawRoundRect(topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.62.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(innerRim, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.50.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(cornerLight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.72.dp.toPx()), blendMode = BlendMode.Screen)
        if (rainbowCornerAlpha > 0.001f) drawRoundRect(rainbowCorner, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.64.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(rimBandMain, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((rimWidth + rainbowRimWidth * 1.04f).dp.toPx()), blendMode = BlendMode.Plus)
        drawRoundRect(rimBandCounter, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((rimWidth * 0.76f + rainbowRimWidth * 0.66f).dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(rimBandTop, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((rimWidth * 0.50f + rainbowRimWidth * 0.50f).dp.toPx()), blendMode = BlendMode.Plus)
        if (safePress > 0.001f) {
            val localEdgeAlpha = (topNear + bottomNear + leftNear + rightNear).coerceIn(0.18f, 1f)
            drawRoundRect(prismLocalEdge, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.82f + 1.08f * localEdgeAlpha).dp.toPx()), blendMode = BlendMode.Plus)
        }
        if (sweep > 0.001f) drawRoundRect(prismSweep, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke((0.64f + 0.82f * sweep.coerceIn(0f, 1.2f)).dp.toPx()), blendMode = BlendMode.Plus)
    }
}

@Composable
private fun ModelStackCardContent(model: ChatModel, selected: Boolean, expansionProgress: Float, press: Float) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(if (selected) 9.dp else 7.dp).graphicsLayer { scaleX = 1f + 0.16f * press; scaleY = 1f + 0.16f * press }.clip(RoundedCornerShape(999.dp)).background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.32f + 0.20f * expansionProgress)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(model.shortLabel, color = Color.White.copy(alpha = if (selected) 0.96f else 0.78f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.id, color = Color.White.copy(alpha = if (selected) 0.54f else 0.38f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChatPanelV2(state: AssistantUiState, modifier: Modifier, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isSending) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex) }
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier.fillMaxWidth(), GlassRole.Shell) {
        Box(Modifier.fillMaxSize()) {
            RainbowChatGlassOverlay(quality = state.quality, motionIntensity = state.motionIntensity, modifier = Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ChatStatusV2(if (state.isSending) "正在思考" else "可上下滑动")
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.messages, key = { it.id }) { message -> AnimatedMessageBubbleV2(message, state) }
                    item { StarterSuggestionsV2(state, onDraftCommand, onPickImage) }
                }
            }
        }
    }
}

@Composable
private fun StarterSuggestionsV2(state: AssistantUiState, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    AnimatedVisibility(visible = !state.isSending && state.messages.size <= 2, enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.72f)) { it / 2 }, exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 2 }) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 2.dp)) {
            Text("可以这样说", color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                SuggestionButtonV2("记一笔", state, Modifier.weight(1f)) { onDraftCommand("记一笔 午饭 18 元") }
                SuggestionButtonV2("设提醒", state, Modifier.weight(1f)) { onDraftCommand("今晚 9 点半提醒我复盘") }
                SuggestionButtonV2("识图", state, Modifier.weight(1f), onClick = onPickImage)
            }
        }
    }
}

@Composable
private fun AnimatedMessageBubbleV2(message: ChatMessage, state: AssistantUiState) {
    val fromUser = message.role == MessageRole.User
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally(spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)) { width -> if (fromUser) width / 3 else -width / 3 } + scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)), exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120))) {
        MessageBubbleV2(message, state)
    }
}

@Composable
private fun MessageBubbleV2(message: ChatMessage, state: AssistantUiState) {
    val fromUser = message.role == MessageRole.User
    val fill = if (fromUser) 0.76f else 0.90f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        GlassPanel(quality = state.quality, glassIntensity = state.glassIntensity * if (fromUser) 1.03f else 0.94f, motionIntensity = state.motionIntensity, radius = 22, modifier = Modifier.fillMaxWidth(fill), role = if (fromUser) GlassRole.Floating else GlassRole.Card) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.status == MessageStatus.Sending && !fromUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("正在思考", color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        ThinkingDotsV2(size = 6, color = Color(0xFF8DF9EA).copy(alpha = 0.88f))
                    }
                } else {
                    Text(text = messageText(message), color = messageTextColor(message, fromUser), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium)
                }
                if (!fromUser) MessageBadgeV2(message)
            }
        }
    }
}

@Composable
private fun MessageBadgeV2(message: ChatMessage) {
    val text = messageBadgeTextV2(message) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(badgeColorV2(message).copy(alpha = 0.82f)))
        Text(text, color = badgeColorV2(message).copy(alpha = 0.68f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ComposerBarV2(state: AssistantUiState, onComposerChange: (String) -> Unit, onSend: () -> Unit, onPickImage: () -> Unit) {
    val sendAction = if (state.isSending) ({}) else onSend
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RoundIconButtonV2("+", state, size = 48, onClick = onPickImage)
        ComposerInputV2(state, state.composerText, onComposerChange, sendAction, Modifier.weight(1f), if (state.isSending) "正在等待回复..." else "和我说点什么...")
        SendButtonV2(state, onClick = sendAction)
    }
}

@Composable
private fun ComposerInputV2(state: AssistantUiState, text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier, placeholder: String) {
    val focusPop by animateFloatAsState(targetValue = if (text.isNotBlank()) 1.012f else 1f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "composer-pop")
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(48.dp).graphicsLayer { scaleX = focusPop; scaleY = focusPop }, GlassRole.Card) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(value = text, onValueChange = onTextChange, singleLine = true, enabled = !state.isSending, textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium), cursorBrush = SolidColor(Color.White.copy(alpha = 0.86f)), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { onSend() }), modifier = Modifier.fillMaxWidth())
            AnimatedVisibility(visible = text.isBlank(), enter = fadeIn(tween(160)), exit = fadeOut(tween(100))) {
                Text(placeholder, color = Color.White.copy(alpha = 0.42f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SendButtonV2(state: AssistantUiState, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 1.02f, state.motionIntensity, 999, Modifier.size(48.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isSending) ThinkingDotsV2(size = 6, color = Color.White.copy(alpha = 0.90f)) else Text("↑", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RoundIconButtonV2(text: String, state: AssistantUiState, size: Int = 40, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 999, Modifier.size(size.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = if (text == "+") 25.sp else 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SuggestionButtonV2(text: String, state: AssistantUiState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 20, modifier.height(38.dp), GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.84f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun ChatStatusV2(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (text.contains("思考")) ThinkingDotsV2(size = 4, color = Color.White.copy(alpha = 0.50f))
    }
}

@Composable
private fun ThinkingDotsV2(size: Int, color: Color) {
    val transition = rememberInfiniteTransition(label = "thinking-dots-v2")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(920, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "thinking-phase-v2")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val wave = ((sin(phase * 2f * PI.toFloat() + index * 1.45f) + 1f) / 2f).coerceIn(0f, 1f)
            Box(Modifier.size(size.dp).graphicsLayer { translationY = -6f * wave; alpha = 0.32f + 0.68f * wave; scaleX = 0.72f + 0.34f * wave; scaleY = 0.72f + 0.34f * wave }.clip(RoundedCornerShape(999.dp)).background(color))
        }
    }
}

@Composable
private fun PulseDotV2(active: Boolean, color: Color) {
    if (!active) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = 0.68f }.clip(RoundedCornerShape(999.dp)).background(color))
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse-dot-v2")
    val pulse by transition.animateFloat(initialValue = 0.76f, targetValue = 1.22f, animationSpec = infiniteRepeatable(animation = tween(860, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "pulse-dot-value-v2")
    Box(Modifier.size(8.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.96f }.clip(RoundedCornerShape(999.dp)).background(color))
}

private fun messageText(message: ChatMessage): String = when (message.status) {
    MessageStatus.Sending -> message.text.ifBlank { "正在思考…" }
    MessageStatus.Failed -> message.errorText ?: message.text.ifBlank { "云端请求失败，请稍后再试。" }
    MessageStatus.Sent -> message.text
}

private fun messageTextColor(message: ChatMessage, fromUser: Boolean): Color = when (message.status) {
    MessageStatus.Failed -> Color(0xFFFFB4B4)
    MessageStatus.Sending -> Color.White.copy(alpha = 0.78f)
    MessageStatus.Sent -> Color.White.copy(alpha = if (fromUser) 0.96f else 0.86f)
}

private fun messageBadgeTextV2(message: ChatMessage): String? {
    val status = when (message.status) {
        MessageStatus.Sending -> "生成中"
        MessageStatus.Failed -> "请求失败"
        MessageStatus.Sent -> null
    }
    val main = message.modelLabel?.takeIf { it.isNotBlank() } ?: sourceReadableLabelV2(message.source) ?: status
    val source = sourceReadableLabelV2(message.source)
    val version = message.version?.takeIf { it.isNotBlank() }?.removePrefix("2026-")?.removePrefix("android-")?.take(18)
    return listOfNotNull(status, main, source, version).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
}

private fun sourceReadableLabelV2(source: String?): String? = when (source) {
    null, "" -> null
    "cloud_ai" -> "云端 AI"
    "workers_ai", "workers_ai_text_fallback" -> "Workers AI"
    "gemini_ai", "gemini_chat", "gemini_text_fallback" -> "Gemini"
    "kimi", "nvidia_chat" -> "Kimi / NIM"
    "mistral" -> "Mistral"
    "web_search_tool", "tavily_web_search", "tavily_ai_summary" -> "联网搜索"
    "cloud_fetch_failed" -> "云端连接失败"
    "cloud_error_normalized" -> "云端错误"
    "local" -> "本地"
    "local_ledger" -> "本地记账"
    "local_mobile" -> "手机动作"
    else -> source.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun badgeColorV2(message: ChatMessage): Color = when (message.status) {
    MessageStatus.Failed -> Color(0xFFFFB4B4)
    MessageStatus.Sending -> Color(0xFF8DF9EA)
    MessageStatus.Sent -> when (message.source) {
        "web_search_tool", "tavily_web_search", "tavily_ai_summary" -> Color(0xFF8DF9EA)
        "cloud_fetch_failed", "cloud_error_normalized" -> Color(0xFFFFB4B4)
        else -> Color.White
    }
}
