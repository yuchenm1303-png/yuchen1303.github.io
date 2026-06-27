package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.yuchen.ailedger.model.ModelCardGlassStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.launch

private val ModelPressPreload = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val ModelPressSink = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val ModelPressRelease = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val ModelPressPulse = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)
private val ModelStackTravel = CubicBezierEasing(0.16f, 0.00f, 0.10f, 1.00f)
private val ModelStackVisual = CubicBezierEasing(0.18f, 0.00f, 0.14f, 1.00f)
private const val ModelStackTapMaxMillis = 450L
private const val ModelStackTapSlopDp = 10f
private const val ModelShapeMotionBlend = 0.22f

private data class ModelCardPrismTheme(
    val main: Color,
    val bright: Color,
    val deep: Color,
    val prismA: Color,
    val prismB: Color,
    val prismC: Color,
    val gold: Color,
    val themeWeight: Float
)

private data class ModelCardGeometry(
    val collapsedXPx: Float,
    val collapsedYPx: Float,
    val expandedXPx: Float,
    val expandedYPx: Float,
    val horizontalMotion: Float,
    val verticalMotion: Float,
    val overshoot: Float
)

private data class FoldedLabelPlacement(
    val xFraction: Float,
    val yFraction: Float,
    val scale: Float,
    val alpha: Float
)

private enum class ModelStackMaterialKind { Folded, Full }

private data class ModelStackMaterialItem(
    val kind: ModelStackMaterialKind,
    val zIndex: Float,
    val xPx: Float,
    val yPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val scaleX: Float,
    val scaleY: Float,
    val alpha: Float,
    val originX: Float,
    val originY: Float,
    val style: ModelCardGlassStyle,
    val theme: ModelCardPrismTheme,
    val stackRank: Int = 0,
    val stackPress: Float = 0f,
    val selected: Boolean = false,
    val selection: Float = 0f,
    val energy: Float = 0f,
    val materialPress: Float = 0f,
    val delayed: Float = 0f,
    val stackReveal: Float = 0f,
    val center: Offset = Offset(0.50f, 0.42f),
    val seed: Float = 0.50f,
    val direction: Float = 1f,
    val band: Int = 0,
    val strength: Float = 1f
)

private val ModelMaterialZComparator = Comparator<ModelStackMaterialItem> { left, right ->
    left.zIndex.compareTo(right.zIndex)
}

private val AutoModelTheme = ModelCardPrismTheme(Color(0xFF77FFF0), Color.White, Color(0xFF07142D), Color(0xFF62FFF0), Color(0xFFFF70D9), Color(0xFF8EA2FF), Color(0xFFFFE08A), 0.00f)
private val GeminiModelTheme = ModelCardPrismTheme(Color(0xFF2F8CFF), Color(0xFFE2F7FF), Color(0xFF061A42), Color(0xFF37F6FF), Color(0xFF4F6CFF), Color(0xFF126BFF), Color(0xFFBEEFFF), 0.98f)
private val KimiModelTheme = ModelCardPrismTheme(Color(0xFFE35CFF), Color(0xFFFFE0FF), Color(0xFF2A0C45), Color(0xFFFF7AE4), Color(0xFFB85CFF), Color(0xFFFF4FC6), Color(0xFFFFB5F0), 1.00f)
private val MistralModelTheme = ModelCardPrismTheme(Color(0xFFFFC247), Color(0xFFFFF3C4), Color(0xFF3B2205), Color(0xFFFFE07A), Color(0xFFFF9B35), Color(0xFFFFD15C), Color(0xFFFFE26D), 0.98f)
private val WorkersModelTheme = ModelCardPrismTheme(Color(0xFFFF4F47), Color(0xFFFFD8D0), Color(0xFF3A0D0A), Color(0xFFFF7B5C), Color(0xFFFF3D86), Color(0xFFFF6B3D), Color(0xFFFFB06A), 1.00f)
private val DeepSeekModelTheme = ModelCardPrismTheme(Color(0xFF2563EB), Color(0xFFA7F3FF), Color(0xFF031B3C), Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF14B8A6), Color(0xFFB7F7FF), 0.98f)
private val GptOssModelTheme = ModelCardPrismTheme(Color(0xFF10B981), Color(0xFFD1FAE5), Color(0xFF03140F), Color(0xFF34D399), Color(0xFF6EE7B7), Color(0xFFA7F3D0), Color(0xFFE5FFF5), 0.95f)

private fun ChatModel.modelCardPrismTheme(): ModelCardPrismTheme = when (this) {
    ChatModel.Auto -> AutoModelTheme
    ChatModel.Gemini -> GeminiModelTheme
    ChatModel.Kimi -> KimiModelTheme
    ChatModel.Mistral -> MistralModelTheme
    ChatModel.Workers -> WorkersModelTheme
    ChatModel.DeepSeekV4 -> DeepSeekModelTheme
    ChatModel.GptOss -> GptOssModelTheme
}

private fun ChatModel.modelExpandedLayoutSlot(): Int = when (this) {
    ChatModel.Auto -> 0
    ChatModel.Kimi -> 2
    ChatModel.Workers -> 4
    ChatModel.Mistral -> 3
    ChatModel.Gemini -> 5
    ChatModel.DeepSeekV4 -> 6
    ChatModel.GptOss -> 7
}

private fun foldedLabelPlacement(stackRank: Int, totalBackCount: Int): FoldedLabelPlacement {
    val count = totalBackCount.coerceAtLeast(1)
    val index = (stackRank - 1).coerceIn(0, count - 1)
    val t = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
    return FoldedLabelPlacement(
        xFraction = modelLerpFloat(0.74f, 0.18f, t),
        yFraction = modelLerpFloat(0.16f, 0.46f, t),
        scale = modelLerpFloat(1.00f, 0.92f, t),
        alpha = modelLerpFloat(0.70f, 0.44f, t)
    )
}

private fun foldedLabelFontSize(label: String) = when {
    label.length >= 8 -> 7.4.sp
    label.length >= 6 -> 7.8.sp
    label.length >= 5 -> 8.0.sp
    else -> 8.2.sp
}

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
        val models = remember { ChatModel.entries }
        val style = ModelCardGlassLabState.style
        val animationScope = rememberCoroutineScope()
        val currentIsSending by rememberUpdatedState(state.isSending)
        val currentExpanded by rememberUpdatedState(expanded)
        val currentOnToggleExpanded by rememberUpdatedState(onToggleExpanded)
        val currentOnSelected by rememberUpdatedState(onSelected)
        val stackTransitionKey = remember(expanded, state.selectedModel) { Any() }
        val currentStackTransitionKey by rememberUpdatedState(stackTransitionKey)
        val tapSlopPx = remember(density.density) { with(density) { ModelStackTapSlopDp.dp.toPx() } }
        val stackPressAnim = remember { Animatable(0f) }
        val stackPress = stackPressAnim.value.coerceIn(0f, 1f)
        val materialItems = ArrayList<ModelStackMaterialItem>(models.size * 2)

        val gap = 10.dp
        val rowStep = 74.dp
        val collapsedHeight = 56.dp
        val expandedHeight = 64.dp
        val reservedGap = 8.dp
        val collapsedWidth = (maxWidth - reservedGap) * 0.642f
        val halfWidth = (maxWidth - gap) / 2f
        val selectedModel = state.selectedModel
        val totalBackCount = (models.size - 1).coerceAtLeast(0)
        val stackRanks = remember(selectedModel) {
            val result = HashMap<ChatModel, Int>(models.size)
            var nextRank = 1
            models.forEach { model ->
                if (model == selectedModel) {
                    result[model] = 0
                } else {
                    result[model] = nextRank
                    nextRank += 1
                }
            }
            result
        }

        val collapsedWidthPx = with(density) { collapsedWidth.toPx() }
        val halfWidthPx = with(density) { halfWidth.toPx() }
        val collapsedHeightPx = with(density) { collapsedHeight.toPx() }
        val expandedHeightPx = with(density) { expandedHeight.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val rowStepPx = with(density) { rowStep.toPx() }
        val collapsedStepXPx = with(density) { 5.dp.toPx() }
        val collapsedStepYPx = with(density) { 1.6.dp.toPx() }
        val distanceReferencePx = with(density) { 220.dp.toPx() }

        Box(Modifier.fillMaxSize().drawModelStackGlassMaterials(materialItems)) {
            models.forEachIndexed { index, model ->
                val selected = model == selectedModel
                val theme = model.modelCardPrismTheme()
                val layoutSlot = model.modelExpandedLayoutSlot()
                val stackRank = stackRanks[model] ?: 0
                val pressAnim = remember(model.id) { Animatable(0f) }
                val opticsAnim = remember(model.id) { Animatable(0f) }
                val visualAnim = remember(model.id) { Animatable(if (expanded) 1f else 0f) }
                val motionAnim = remember(model.id) { Animatable(if (expanded) 1f else 0f) }
                val capsuleAnim = remember(model.id) { Animatable(0f) }
                val foldedBlendAnim = remember(model.id) { Animatable(1f) }
                var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
                var center by remember(model.id) { mutableStateOf(Offset(0.50f, 0.42f)) }
                var seed by remember(model.id) { mutableStateOf(0.50f) }
                var direction by remember(model.id) { mutableStateOf(1f) }
                var band by remember(model.id) { mutableStateOf(0) }
                var strength by remember(model.id) { mutableStateOf(1f) }
                var renderAsFullCard by remember(model.id) { mutableStateOf(false) }
                var hasModelStackEntered by remember(model.id) { mutableStateOf(false) }

                val geometry = remember(
                    collapsedWidthPx,
                    halfWidthPx,
                    selectedModel,
                    layoutSlot,
                    stackRank,
                    gapPx,
                    rowStepPx,
                    collapsedStepXPx,
                    collapsedStepYPx,
                    distanceReferencePx
                ) {
                    val expandedXPx = if (layoutSlot % 2 == 1) halfWidthPx + gapPx else 0f
                    val expandedYPx = rowStepPx * (layoutSlot / 2).toFloat()
                    val collapsedXPx = if (selected) 0f else stackRank * collapsedStepXPx
                    val collapsedYPx = if (selected) 0f else stackRank * collapsedStepYPx
                    val motionX = expandedXPx - collapsedXPx
                    val motionY = expandedYPx - collapsedYPx
                    val travelTotal = abs(motionX) + abs(motionY) + 0.001f
                    val distanceWeight = (travelTotal / distanceReferencePx).coerceIn(0.35f, 1f)
                    ModelCardGeometry(
                        collapsedXPx,
                        collapsedYPx,
                        expandedXPx,
                        expandedYPx,
                        abs(motionX) / travelTotal,
                        abs(motionY) / travelTotal,
                        0.040f + 0.020f * distanceWeight
                    )
                }

                val selectionProgress by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(if (selected) 520 else 260, delayMillis = if (selected) 28 else 0, easing = FastOutSlowInEasing),
                    label = "model-card-selection-material-${model.id}"
                )
                val selectedPulse by animateFloatAsState(
                    targetValue = if (selected) 1.008f else 1f,
                    animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow),
                    label = "model-card-selected-${model.id}"
                )

                LaunchedEffect(expanded, selected, model.id, stackTransitionKey) {
                    val target = if (expanded) 1f else 0f
                    val launchedTransitionKey = stackTransitionKey
                    if (!hasModelStackEntered && !expanded) {
                        hasModelStackEntered = true
                        renderAsFullCard = false
                        foldedBlendAnim.snapTo(1f)
                        visualAnim.snapTo(0f)
                        motionAnim.snapTo(0f)
                        capsuleAnim.snapTo(0f)
                        return@LaunchedEffect
                    }
                    hasModelStackEntered = true
                    val wasFullyFolded = !selected && !renderAsFullCard
                    if (expanded && wasFullyFolded) {
                        visualAnim.stop()
                        motionAnim.stop()
                        visualAnim.snapTo(0f)
                        motionAnim.snapTo(0f)
                    }
                    renderAsFullCard = true
                    foldedBlendAnim.stop()
                    if (expanded && !selected) {
                        if (wasFullyFolded) foldedBlendAnim.snapTo(1f)
                        launch {
                            foldedBlendAnim.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
                        }
                    } else if (selected) {
                        foldedBlendAnim.snapTo(0f)
                    }
                    capsuleAnim.stop()
                    capsuleAnim.snapTo(0f)

                    val reverseRank = (totalBackCount - stackRank).coerceAtLeast(0)
                    val visualDuration =
                        if (expanded) (460 + stackRank * 30).coerceAtMost(610)
                        else if (selected) 430
                        else 390 + reverseRank * 24

                    launch {
                        visualAnim.stop()
                        visualAnim.animateTo(target, tween(durationMillis = visualDuration, easing = ModelStackVisual))
                    }
                    motionAnim.stop()
                    motionAnim.animateTo(
                        targetValue = target,
                        animationSpec = spring(
                            dampingRatio = if (expanded) 0.70f else 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialVelocity = modelContinuousLaunchVelocity(
                            expanded = expanded,
                            stackRank = stackRank,
                            stackPress = stackPressAnim.value,
                            cardPress = pressAnim.value
                        )
                    )
                    if (!expanded && !selected && launchedTransitionKey === currentStackTransitionKey) {
                        foldedBlendAnim.stop()
                        foldedBlendAnim.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
                        if (launchedTransitionKey === currentStackTransitionKey) renderAsFullCard = false
                    }
                }

                val fullCardNeeded = selected || expanded || renderAsFullCard
                val layerFactor = when (stackRank) {
                    1 -> 0.82f
                    2 -> 0.62f
                    3 -> 0.44f
                    else -> 0.28f
                }
                val stackCompression = modelSmooth((stackPress * layerFactor / 0.72f).coerceIn(0f, 1f))

                if (!fullCardNeeded) {
                    val foldedAlpha = (0.68f - stackRank * 0.055f).coerceIn(0.42f, 0.70f)
                    materialItems.add(
                        foldedMaterialItem(
                            widthPx = collapsedWidthPx,
                            heightPx = collapsedHeightPx,
                            zIndex = 18f - index,
                            geometry = geometry,
                            style = style,
                            theme = theme,
                            stackRank = stackRank,
                            stackCompression = stackCompression,
                            layerFactor = layerFactor,
                            alpha = foldedAlpha
                        )
                    )
                    FoldedModelBackPlate(
                        width = collapsedWidth,
                        height = collapsedHeight,
                        zIndex = 18f - index,
                        geometry = geometry,
                        stackRank = stackRank,
                        totalBackCount = totalBackCount,
                        stackCompression = stackCompression,
                        layerFactor = layerFactor,
                        alpha = foldedAlpha,
                        model = model,
                        theme = theme
                    )
                    return@forEachIndexed
                }

                val foldedBlend = if (!selected) {
                    when {
                        expanded && !renderAsFullCard -> 1f
                        renderAsFullCard -> foldedBlendAnim.value.coerceIn(0f, 1f)
                        else -> 0f
                    }
                } else {
                    0f
                }
                if (foldedBlend > 0.001f) {
                    val foldedAlpha = foldedBlend * (0.68f - stackRank * 0.055f).coerceIn(0.42f, 0.70f)
                    materialItems.add(
                        foldedMaterialItem(
                            widthPx = collapsedWidthPx,
                            heightPx = collapsedHeightPx,
                            zIndex = 18f - index,
                            geometry = geometry,
                            style = style,
                            theme = theme,
                            stackRank = stackRank,
                            stackCompression = stackCompression,
                            layerFactor = layerFactor,
                            alpha = foldedAlpha
                        )
                    )
                    FoldedModelBackPlate(
                        width = collapsedWidth,
                        height = collapsedHeight,
                        zIndex = 18f - index,
                        geometry = geometry,
                        stackRank = stackRank,
                        totalBackCount = totalBackCount,
                        stackCompression = stackCompression,
                        layerFactor = layerFactor,
                        alpha = foldedAlpha,
                        model = model,
                        theme = theme
                    )
                }

                val pressValue = pressAnim.value.coerceIn(-0.16f, 1.12f)
                val positivePress = pressValue.coerceAtLeast(0f)
                val compression = modelSmooth((positivePress / 0.72f).coerceIn(0f, 1f))
                val rebound = modelSmooth((-pressValue / 0.11f).coerceIn(0f, 1f))
                val delayed = opticsAnim.value.coerceIn(0f, 1f)
                val selection = modelSmooth(selectionProgress.coerceIn(0f, 1f))
                val visualProgress = visualAnim.value.coerceIn(0f, 1f)
                val rawMotion = motionAnim.value
                val motionVelocity = motionAnim.velocity
                val motionSpeedEnergy = modelCapsuleSpeedEnergy(motionVelocity, expanded)
                val capsuleLaunch = maxOf(
                    modelSmooth(capsuleAnim.value.coerceIn(0f, 1f)),
                    motionSpeedEnergy * 0.42f
                )
                val clampedMotion = rawMotion.coerceIn(0f, 1f)
                val targetProgress = modelBlendedShapeProgress(visualProgress, clampedMotion)
                val stackReveal = 1f - targetProgress
                val p = modelSoftLimit(
                    value = rawMotion,
                    min = -geometry.overshoot * 0.34f,
                    max = 1f + geometry.overshoot * 0.46f,
                    softness = geometry.overshoot * 0.68f
                )
                val overshootAmount = if (expanded) (p - 1f).coerceAtLeast(0f) else (-p).coerceAtLeast(0f)
                val dockGlow = modelSmooth((overshootAmount / geometry.overshoot.coerceAtLeast(0.001f)).coerceIn(0f, 1f))
                val selectionBurst = if (selected) sin(selectionProgress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f) else 0f
                val materialPress = maxOf(
                    positivePress,
                    delayed * 0.92f,
                    rebound * 0.42f,
                    capsuleLaunch * 0.10f,
                    dockGlow * 0.055f,
                    selectionBurst * 0.18f
                ).coerceIn(0f, 1.08f)

                val width = modelLerpDp(collapsedWidth, halfWidth, targetProgress)
                val height = modelLerpDp(collapsedHeight, expandedHeight, targetProgress)
                val widthPx = modelLerpRawFloat(collapsedWidthPx, halfWidthPx, targetProgress)
                val heightPx = modelLerpRawFloat(collapsedHeightPx, expandedHeightPx, targetProgress)
                val releaseStretchX = capsuleLaunch * (0.014f * geometry.horizontalMotion - 0.004f * geometry.verticalMotion)
                val releaseStretchY = capsuleLaunch * (0.006f * geometry.verticalMotion - 0.008f * geometry.horizontalMotion)
                val dockScaleX = dockGlow * (0.0105f * geometry.horizontalMotion - 0.0030f * geometry.verticalMotion)
                val dockScaleY = dockGlow * (0.0080f * geometry.verticalMotion - 0.0042f * geometry.horizontalMotion)
                val scaleX = selectedPulse * (1f + compression * 0.055f - rebound * 0.010f + releaseStretchX + dockScaleX)
                val scaleY = selectedPulse * (1f - compression * 0.064f + rebound * 0.028f + releaseStretchY + dockScaleY)
                val sinkY = compression * 4.10f - rebound * 1.05f + capsuleLaunch * 0.20f + dockGlow * 0.18f
                val baseX = modelLerpRawFloat(geometry.collapsedXPx, geometry.expandedXPx, p)
                val baseY = modelLerpRawFloat(geometry.collapsedYPx, geometry.expandedYPx, p)
                val motionDx = geometry.expandedXPx - geometry.collapsedXPx
                val motionDy = geometry.expandedYPx - geometry.collapsedYPx
                val motionLength = sqrt(motionDx * motionDx + motionDy * motionDy).coerceAtLeast(1f)
                val lateralOffset = modelContinuousCapsuleSideOffset(
                    progress = p,
                    velocity = motionVelocity,
                    distancePx = motionLength,
                    overshoot = geometry.overshoot,
                    expanded = expanded,
                    layoutSlot = layoutSlot,
                    stackRank = stackRank
                )
                val xPx = baseX + (-motionDy / motionLength) * lateralOffset
                val yPx = baseY + (motionDx / motionLength) * lateralOffset + sinkY
                val baseAlpha = modelLerpFloat(if (selected) 1f else 0.54f, 1f, targetProgress)
                val alpha = if (expanded) {
                    baseAlpha
                } else {
                    baseAlpha * (1f - foldedBlend).coerceIn(0f, 1f)
                }
                val energy = if (selected) {
                    modelLerpFloat(0.50f * style.unselectedEnergy.coerceIn(0f, 5f), 1f, selection)
                } else {
                    0.50f * style.unselectedEnergy.coerceIn(0f, 5f)
                }

                materialItems.add(
                    ModelStackMaterialItem(
                        kind = ModelStackMaterialKind.Full,
                        zIndex = if (selected) 50f else 30f - index,
                        xPx = xPx,
                        yPx = yPx,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        alpha = alpha,
                        originX = center.x,
                        originY = center.y,
                        style = style,
                        theme = theme,
                        selected = selected,
                        selection = selection,
                        energy = energy,
                        materialPress = materialPress,
                        delayed = delayed,
                        stackReveal = stackReveal,
                        center = center,
                        seed = seed,
                        direction = direction,
                        band = band,
                        strength = strength
                    )
                )

                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .zIndex(if (selected) 50f else 30f - index)
                        .onSizeChanged {
                            cardSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat())
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(center.x, center.y)
                            translationX = xPx
                            translationY = yPx
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                            this.alpha = alpha
                            shadowElevation = 0f
                        }
                        .pointerInput(model.id, tapSlopPx) {
                            awaitEachGesture {
                                fun updateCenter(position: Offset) {
                                    center = Offset(
                                        (position.x / cardSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                        (position.y / cardSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    )
                                }

                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downUptime = down.uptimeMillis
                                val downPosition = down.position
                                val tapSlopSquared = tapSlopPx * tapSlopPx
                                var releaseUptime = downUptime
                                var movedBeyondTapSlop = false
                                updateCenter(down.position)
                                if (currentIsSending) return@awaitEachGesture

                                seed = Random.nextFloat()
                                direction = if (Random.nextBoolean()) 1f else -1f
                                band = Random.nextInt(0, 4)
                                strength = 1.06f + Random.nextFloat() * 0.62f

                                animationScope.launch {
                                    pressAnim.stop()
                                    if (pressAnim.value < 0.34f) pressAnim.snapTo(0.34f)
                                    pressAnim.animateTo(0.88f, tween(82, easing = ModelPressPulse))
                                    pressAnim.animateTo(0.78f, tween(160, easing = ModelPressSink))
                                    pressAnim.animateTo(0.74f, tween(220, easing = FastOutSlowInEasing))
                                }
                                animationScope.launch {
                                    opticsAnim.stop()
                                    opticsAnim.animateTo(0.46f, tween(104, easing = ModelPressPreload))
                                    opticsAnim.animateTo(1.05f, tween(260, easing = ModelPressSink))
                                    opticsAnim.animateTo(1.08f, tween(360, easing = FastOutSlowInEasing))
                                }
                                if (!currentExpanded) {
                                    animationScope.launch {
                                        stackPressAnim.stop()
                                        if (stackPressAnim.value < 0.28f) stackPressAnim.snapTo(0.28f)
                                        stackPressAnim.animateTo(0.78f, tween(92, easing = ModelPressPulse))
                                        stackPressAnim.animateTo(0.70f, tween(180, easing = ModelPressSink))
                                    }
                                }

                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (tracked != null) {
                                        updateCenter(tracked.position)
                                        val dx = tracked.position.x - downPosition.x
                                        val dy = tracked.position.y - downPosition.y
                                        if (dx * dx + dy * dy > tapSlopSquared) movedBeyondTapSlop = true
                                        if (!tracked.pressed) {
                                            releaseUptime = tracked.uptimeMillis
                                            released = true
                                            break
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) {
                                        releaseUptime = event.changes.maxOfOrNull { it.uptimeMillis } ?: releaseUptime
                                        released = true
                                        break
                                    }
                                }

                                val isTapRelease = released && !movedBeyondTapSlop && releaseUptime - downUptime <= ModelStackTapMaxMillis
                                if (isTapRelease) {
                                    if (currentExpanded) currentOnSelected(model) else currentOnToggleExpanded()
                                }

                                animationScope.launch {
                                    opticsAnim.stop()
                                    if (released && !movedBeyondTapSlop && opticsAnim.value < 0.52f) {
                                        opticsAnim.animateTo(0.64f, tween(74, easing = ModelPressPulse))
                                    }
                                    opticsAnim.animateTo(0f, tween(if (released && !movedBeyondTapSlop) 620 else 340, easing = FastOutSlowInEasing))
                                }
                                animationScope.launch {
                                    pressAnim.stop()
                                    if (released) {
                                        if (pressAnim.value.coerceIn(0f, 1.16f) < 0.58f) {
                                            pressAnim.animateTo(0.68f, tween(62, easing = ModelPressPulse))
                                            pressAnim.animateTo(-0.086f, tween(142, easing = ModelPressRelease))
                                        } else {
                                            pressAnim.animateTo(-0.092f, tween(178, easing = ModelPressRelease))
                                        }
                                        pressAnim.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow))
                                    } else {
                                        pressAnim.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
                                    }
                                }
                                if (!currentExpanded) {
                                    animationScope.launch {
                                        stackPressAnim.stop()
                                        stackPressAnim.animateTo(-0.070f, tween(142, easing = ModelPressRelease))
                                        stackPressAnim.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow))
                                    }
                                }
                            }
                        }
                ) {
                    UnifiedModelCardContent(model, selected, stackRank, totalBackCount, selection, targetProgress, stackReveal, theme)
                }
            }
            materialItems.sortWith(ModelMaterialZComparator)
        }
    }
}

private fun foldedMaterialItem(
    widthPx: Float,
    heightPx: Float,
    zIndex: Float,
    geometry: ModelCardGeometry,
    style: ModelCardGlassStyle,
    theme: ModelCardPrismTheme,
    stackRank: Int,
    stackCompression: Float,
    layerFactor: Float,
    alpha: Float
): ModelStackMaterialItem {
    return ModelStackMaterialItem(
        kind = ModelStackMaterialKind.Folded,
        zIndex = zIndex,
        xPx = geometry.collapsedXPx - stackCompression * 1.8f * layerFactor,
        yPx = geometry.collapsedYPx + stackCompression * 5.6f * layerFactor,
        widthPx = widthPx,
        heightPx = heightPx,
        scaleX = 1f + stackCompression * 0.032f * layerFactor,
        scaleY = 1f - stackCompression * 0.046f * layerFactor,
        alpha = alpha,
        originX = 0.50f,
        originY = 0.50f,
        style = style,
        theme = theme,
        stackRank = stackRank,
        stackPress = stackCompression
    )
}

@Composable
private fun FoldedModelBackPlate(
    width: Dp,
    height: Dp,
    zIndex: Float,
    geometry: ModelCardGeometry,
    stackRank: Int,
    totalBackCount: Int,
    stackCompression: Float,
    layerFactor: Float,
    alpha: Float,
    model: ChatModel,
    theme: ModelCardPrismTheme
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .zIndex(zIndex)
            .graphicsLayer {
                translationX = geometry.collapsedXPx - stackCompression * 1.8f * layerFactor
                translationY = geometry.collapsedYPx + stackCompression * 5.6f * layerFactor
                scaleX = 1f + stackCompression * 0.032f * layerFactor
                scaleY = 1f - stackCompression * 0.046f * layerFactor
                this.alpha = alpha
                shadowElevation = 0f
            }
    ) {
        FoldedModelBackPlateLabel(model = model, stackRank = stackRank, totalBackCount = totalBackCount, theme = theme)
    }
}

@Composable
private fun FoldedModelBackPlateLabel(
    model: ChatModel,
    stackRank: Int,
    totalBackCount: Int,
    theme: ModelCardPrismTheme
) {
    val density = LocalDensity.current
    val placement = remember(stackRank, totalBackCount) { foldedLabelPlacement(stackRank, totalBackCount) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val xPx = with(density) { maxWidth.toPx() * placement.xFraction }
        val yPx = with(density) { maxHeight.toPx() * placement.yFraction }
        Text(
            text = model.shortLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                    alpha = placement.alpha
                    translationX = xPx
                    translationY = yPx
                    scaleX = placement.scale
                    scaleY = placement.scale
                },
            color = theme.bright.copy(alpha = 0.90f),
            fontSize = foldedLabelFontSize(model.shortLabel),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun UnifiedModelCardContent(
    model: ChatModel,
    selected: Boolean,
    stackRank: Int,
    totalBackCount: Int,
    selection: Float,
    expansionProgress: Float,
    stackReveal: Float,
    theme: ModelCardPrismTheme
) {
    val density = LocalDensity.current
    val fullTextAlpha = if (selected) 1f else expansionProgress.coerceIn(0f, 1f) * 0.92f
    val stackLabelAlpha = if (selected) 0f else modelSmooth(stackReveal.coerceIn(0f, 1f)) * 0.62f
    val placement = remember(stackRank, totalBackCount) { foldedLabelPlacement(stackRank, totalBackCount) }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(20.dp))
        Box(Modifier.weight(1f).fillMaxSize()) {
            Column(
                Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = fullTextAlpha },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    model.shortLabel,
                    color = Color.White.copy(alpha = modelLerpFloat(0.88f, 0.985f, selection)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    model.id,
                    color = Color.White.copy(alpha = modelLerpFloat(0.46f, 0.62f, selection)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!selected) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val xPx = with(density) { maxWidth.toPx() * placement.xFraction }
                    val yPx = with(density) { maxHeight.toPx() * placement.yFraction }
                    Text(
                        text = model.shortLabel,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                alpha = placement.alpha * stackLabelAlpha
                                translationX = xPx
                                translationY = yPx
                                scaleX = placement.scale
                                scaleY = placement.scale
                            },
                        color = theme.bright.copy(alpha = 0.86f),
                        fontSize = foldedLabelFontSize(model.shortLabel),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

private fun Modifier.drawModelStackGlassMaterials(items: List<ModelStackMaterialItem>): Modifier = drawWithCache {
    onDrawWithContent {
        var itemIndex = 0
        while (itemIndex < items.size) {
            val item = items[itemIndex]
            itemIndex += 1
            if (item.alpha <= 0.001f || item.widthPx <= 1f || item.heightPx <= 1f) continue
            withTransform({
                translate(item.xPx, item.yPx)
                scale(
                    scaleX = item.scaleX,
                    scaleY = item.scaleY,
                    pivot = Offset(item.widthPx * item.originX, item.heightPx * item.originY)
                )
            }) {
                val cardSize = Size(item.widthPx, item.heightPx)
                when (item.kind) {
                    ModelStackMaterialKind.Folded -> drawFoldedModelBackPlateSurface(
                        cardSize = cardSize,
                        style = item.style,
                        theme = item.theme,
                        stackRank = item.stackRank,
                        press = item.stackPress,
                        surfaceAlpha = item.alpha
                    )
                    ModelStackMaterialKind.Full -> drawModelCardGlassSurface(
                        cardSize = cardSize,
                        style = item.style,
                        theme = item.theme,
                        selected = item.selected,
                        selection = item.selection,
                        energy = item.energy,
                        materialPress = item.materialPress,
                        delayed = item.delayed,
                        stackReveal = item.stackReveal,
                        center = item.center,
                        seed = item.seed,
                        direction = item.direction,
                        band = item.band,
                        strength = item.strength,
                        surfaceAlpha = item.alpha
                    )
                }
            }
        }
        drawContent()
    }
}

private fun DrawScope.drawFoldedModelBackPlateSurface(
    cardSize: Size,
    style: ModelCardGlassStyle,
    theme: ModelCardPrismTheme,
    stackRank: Int,
    press: Float,
    surfaceAlpha: Float
) {
    val radius = minOf(cardSize.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
    val corner = CornerRadius(radius, radius)
    val rankFade = (0.30f - stackRank * 0.032f).coerceIn(0.15f, 0.28f)
    val backDotCenter = Offset(24.dp.toPx(), cardSize.height / 2f)
    val body = Brush.linearGradient(
        listOf(
            Color.White.modelAlpha(0.012f + 0.006f * press, surfaceAlpha),
            theme.main.modelAlpha(rankFade * 0.15f + 0.014f * press, surfaceAlpha),
            theme.deep.modelAlpha(rankFade * 0.24f, surfaceAlpha),
            Color(0xFF000713).modelAlpha(rankFade * 0.30f, surfaceAlpha)
        ),
        Offset(cardSize.width * 0.08f, 0f),
        Offset(cardSize.width, cardSize.height)
    )
    val rim = Brush.linearGradient(
        listOf(
            theme.bright.modelAlpha(0.145f + 0.045f * press, surfaceAlpha),
            theme.main.modelAlpha(0.118f + 0.034f * press, surfaceAlpha),
            Color.Transparent,
            theme.prismC.modelAlpha(0.130f + 0.026f * press, surfaceAlpha)
        ),
        Offset.Zero,
        Offset(cardSize.width, cardSize.height)
    )
    val rearEdgeRim = Brush.linearGradient(
        listOf(
            Color.Transparent,
            theme.prismC.modelAlpha(0.150f + 0.028f * press, surfaceAlpha),
            theme.main.modelAlpha(0.120f + 0.028f * press, surfaceAlpha),
            theme.bright.modelAlpha(0.185f + 0.032f * press, surfaceAlpha)
        ),
        Offset(cardSize.width * 0.66f, cardSize.height * 0.18f),
        Offset(cardSize.width, cardSize.height)
    )
    val backDotGlow = Brush.radialGradient(
        listOf(
            theme.bright.modelAlpha(0.030f + 0.020f * press, surfaceAlpha),
            theme.main.modelAlpha(0.044f + 0.024f * press, surfaceAlpha),
            theme.prismB.modelAlpha(0.020f + 0.012f * press, surfaceAlpha),
            Color.Transparent
        ),
        backDotCenter,
        6.8.dp.toPx()
    )
    drawRoundRect(body, size = cardSize, cornerRadius = corner, blendMode = BlendMode.Screen)
    drawRoundRect(
        brush = rim,
        topLeft = Offset(0.58.dp.toPx(), 0.58.dp.toPx()),
        size = Size(cardSize.width - 1.16.dp.toPx(), cardSize.height - 1.16.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(0.88.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = rearEdgeRim,
        topLeft = Offset(0.72.dp.toPx(), 0.72.dp.toPx()),
        size = Size(cardSize.width - 1.44.dp.toPx(), cardSize.height - 1.44.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(1.04.dp.toPx()),
        blendMode = BlendMode.Plus
    )
    drawCircle(brush = backDotGlow, radius = 6.2.dp.toPx(), center = backDotCenter, blendMode = BlendMode.Screen)
    drawCircle(theme.bright.modelAlpha(0.34f + 0.12f * press, surfaceAlpha), radius = 1.55.dp.toPx(), center = backDotCenter, blendMode = BlendMode.Screen)
}

private fun DrawScope.drawModelCardGlassSurface(
    style: ModelCardGlassStyle,
    theme: ModelCardPrismTheme,
    selected: Boolean,
    selection: Float,
    energy: Float,
    materialPress: Float,
    delayed: Float,
    stackReveal: Float,
    center: Offset,
    seed: Float,
    direction: Float,
    band: Int,
    strength: Float,
    surfaceAlpha: Float,
    cardSize: Size
) {
    fun s(value: Float, max: Float = 8f) = value.coerceIn(0f, max)
    val radius = minOf(cardSize.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
    val corner = CornerRadius(radius, radius)
    val press = materialPress.coerceIn(0f, 1.12f)
    val hasPress = press > 0.001f
    val compression = modelSmooth((press / 0.72f).coerceIn(0f, 1f))
    val optics = modelSmooth((maxOf(delayed, press * 0.72f) / 1.04f).coerceIn(0f, 1f))
    val selectedGlow = selection.coerceIn(0f, 1f)
    val hasSelectedGlow = selectedGlow > 0.001f
    val collapsedReveal = modelSmooth(stackReveal.coerceIn(0f, 1f))
    val stackBoost = collapsedReveal * if (selected) 0.45f else 1.12f
    val themeBoost = (theme.themeWeight * (0.62f + selectedGlow * 0.70f + stackBoost * 0.40f)).coerceIn(0f, 1.55f)
    val body = energy * s(style.bodyAlpha, 6f)
    val mist = energy * s(style.innerMist, 6f)
    val rimPower = energy
    val materialCenter = Offset(
        cardSize.width * (0.52f + (center.x - 0.5f) * compression * 0.068f),
        cardSize.height * (0.50f + (center.y - 0.5f) * compression * 0.104f)
    )
    val statusCenter = Offset(24.dp.toPx(), cardSize.height / 2f)
    val statusDiameter = 20.dp.toPx()
    val statusPress = press.coerceIn(0f, 1f)
    val autoRainbow = theme.themeWeight <= 0.01f

    val statusGlow = Brush.radialGradient(
        listOf(
            theme.bright.modelAlpha(0.060f + 0.038f * selectedGlow, surfaceAlpha),
            theme.main.modelAlpha(0.105f + 0.120f * selectedGlow, surfaceAlpha),
            theme.prismB.modelAlpha(0.035f + 0.060f * selectedGlow, surfaceAlpha),
            Color.Transparent
        ),
        statusCenter,
        statusDiameter * 0.58f
    )
    val statusPressGlow = if (hasPress) {
        Brush.radialGradient(
            listOf(
                Color.White.modelAlpha(0.16f * statusPress, surfaceAlpha),
                theme.main.modelAlpha(0.30f * statusPress, surfaceAlpha),
                theme.prismB.modelAlpha(0.130f * statusPress, surfaceAlpha),
                theme.gold.modelAlpha(0.095f * statusPress, surfaceAlpha),
                Color.Transparent
            ),
            statusCenter,
            statusDiameter * (0.68f + 0.18f * statusPress)
        )
    } else null
    val bodyVeil = Brush.verticalGradient(
        listOf(
            Color.White.modelAlpha((0.020f + selectedGlow * 0.006f) * body, surfaceAlpha),
            theme.main.modelAlpha((0.020f + selectedGlow * 0.016f + optics * 0.006f) * body, surfaceAlpha),
            theme.deep.modelAlpha(0.050f * body, surfaceAlpha),
            Color(0xFF000713).modelAlpha(0.155f * body, surfaceAlpha)
        ),
        0f,
        cardSize.height
    )
    val mistBrush = Brush.linearGradient(
        listOf(
            Color.White.modelAlpha(0.004f * mist, surfaceAlpha),
            theme.bright.modelAlpha(0.010f * mist, surfaceAlpha),
            theme.prismC.modelAlpha(0.004f * mist, surfaceAlpha),
            Color.Transparent,
            Color(0xFF000713).modelAlpha(0.018f * mist, surfaceAlpha)
        ),
        Offset(cardSize.width * 0.08f, 0f),
        Offset(cardSize.width * 0.94f, cardSize.height)
    )
    val aura = if (hasSelectedGlow) {
        Brush.linearGradient(
            listOf(
                theme.main.modelAlpha((0.050f + 0.060f * themeBoost) * selectedGlow * s(style.selectedAura), surfaceAlpha),
                theme.bright.modelAlpha(0.014f * selectedGlow * s(style.selectedAura), surfaceAlpha),
                theme.prismB.modelAlpha(0.024f * selectedGlow * s(style.selectedAura), surfaceAlpha),
                theme.gold.modelAlpha(0.010f * selectedGlow * s(style.selectedAura), surfaceAlpha),
                Color.Transparent
            ),
            Offset(cardSize.width * -0.14f, cardSize.height * -0.20f),
            Offset(cardSize.width * 1.08f, cardSize.height * 1.10f)
        )
    } else null
    val pressMaterial = if (hasPress) {
        Brush.radialGradient(
            listOf(
                Color.Transparent,
                theme.deep.modelAlpha(0.080f * compression, surfaceAlpha),
                theme.main.modelAlpha((0.050f + 0.060f * themeBoost) * optics, surfaceAlpha),
                theme.prismA.modelAlpha(0.032f * optics, surfaceAlpha),
                theme.gold.modelAlpha(0.020f * optics, surfaceAlpha),
                Color.Transparent
            ),
            materialCenter,
            maxOf(cardSize.width, cardSize.height) * (0.72f + 0.12f * compression)
        )
    } else null
    val combinedRim = Brush.linearGradient(
        listOf(
            theme.bright.modelAlpha(0.260f * rimPower * s(style.outerRim) + 0.070f * rimPower * stackBoost, surfaceAlpha),
            Color(0xFFF1FFFF).modelAlpha(0.082f * rimPower * s(style.outerRim), surfaceAlpha),
            theme.main.modelAlpha((0.150f + 0.170f * themeBoost) * rimPower * s(style.outerRim) + (0.112f + 0.092f * themeBoost) * rimPower * stackBoost, surfaceAlpha),
            Color.Transparent,
            theme.deep.modelAlpha(0.225f * rimPower * s(style.bottomShadow), surfaceAlpha),
            theme.prismC.modelAlpha(0.072f * rimPower * s(style.outerRim) + 0.090f * rimPower * stackBoost, surfaceAlpha)
        ),
        Offset.Zero,
        Offset(cardSize.width, cardSize.height)
    )
    val selectedRainbow = if (hasSelectedGlow) {
        Brush.linearGradient(
            listOf(
                theme.main.modelAlpha((0.680f + 0.420f * themeBoost) * s(style.selectedRainbowRim) * selectedGlow, surfaceAlpha),
                theme.bright.modelAlpha(0.170f * s(style.selectedRainbowRim) * selectedGlow, surfaceAlpha),
                theme.prismA.modelAlpha(0.155f * s(style.selectedRainbowRim) * selectedGlow, surfaceAlpha),
                theme.prismB.modelAlpha(0.145f * s(style.selectedRainbowRim) * selectedGlow, surfaceAlpha),
                theme.prismC.modelAlpha(0.190f * s(style.selectedRainbowRim) * selectedGlow, surfaceAlpha),
                Color.Transparent
            ),
            Offset(cardSize.width * -0.08f, 0f),
            Offset(cardSize.width * 1.02f, cardSize.height * 0.78f)
        )
    } else null

    aura?.let {
        drawRoundRect(
            brush = it,
            topLeft = Offset(-3.4.dp.toPx(), -3.4.dp.toPx()),
            size = Size(cardSize.width + 6.8.dp.toPx(), cardSize.height + 6.8.dp.toPx()),
            cornerRadius = CornerRadius(radius + 3.4.dp.toPx(), radius + 3.4.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
    drawRoundRect(mistBrush, size = cardSize, cornerRadius = corner, blendMode = BlendMode.Screen)
    drawRoundRect(bodyVeil, size = cardSize, cornerRadius = corner, blendMode = BlendMode.Screen)
    pressMaterial?.let { drawRoundRect(it, size = cardSize, cornerRadius = corner, blendMode = BlendMode.Screen) }

    drawCircle(brush = statusGlow, radius = statusDiameter * 0.47f, center = statusCenter, blendMode = BlendMode.Screen)
    statusPressGlow?.let {
        drawCircle(brush = it, radius = statusDiameter * 0.64f, center = statusCenter, blendMode = BlendMode.Screen)
    }

    if (autoRainbow) {
        drawCircle(theme.prismA.modelAlpha(0.34f + 0.10f * selectedGlow + 0.06f * statusPress, surfaceAlpha), radius = statusDiameter * (0.132f + 0.008f * statusPress), center = statusCenter, blendMode = BlendMode.Screen)
        drawCircle(theme.gold.modelAlpha(0.28f + 0.08f * selectedGlow + 0.05f * statusPress, surfaceAlpha), radius = statusDiameter * (0.165f + 0.010f * statusPress), center = statusCenter, blendMode = BlendMode.Screen)
        drawCircle(theme.prismB.modelAlpha(0.24f + 0.07f * selectedGlow + 0.05f * statusPress, surfaceAlpha), radius = statusDiameter * (0.200f + 0.012f * statusPress), center = statusCenter, blendMode = BlendMode.Screen)
        drawCircle(Color.White.modelAlpha(0.88f + 0.08f * selectedGlow + 0.04f * statusPress, surfaceAlpha), radius = statusDiameter * (0.078f + 0.008f * statusPress), center = statusCenter, blendMode = BlendMode.Screen)
    } else {
        drawCircle(theme.main.modelAlpha(0.82f * selectedGlow + 0.26f * statusPress, surfaceAlpha), radius = statusDiameter * (0.22f + 0.045f * selectedGlow + 0.026f * statusPress), center = statusCenter)
        drawCircle(Color.White.modelAlpha((0.70f * (1f - selectedGlow) + 0.94f * selectedGlow + 0.09f * statusPress).coerceIn(0f, 1f), surfaceAlpha), radius = statusDiameter * (0.090f + 0.014f * statusPress), center = statusCenter)
    }

    selectedRainbow?.let {
        drawRoundRect(
            brush = it,
            topLeft = Offset(0.62.dp.toPx(), 0.62.dp.toPx()),
            size = Size(cardSize.width - 1.24.dp.toPx(), cardSize.height - 1.24.dp.toPx()),
            cornerRadius = corner,
            style = Stroke(1.42.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
    drawRoundRect(
        brush = combinedRim,
        topLeft = Offset(0.62.dp.toPx(), 0.62.dp.toPx()),
        size = Size(cardSize.width - 1.24.dp.toPx(), cardSize.height - 1.24.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(0.96.dp.toPx() + 0.20.dp.toPx() * collapsedReveal),
        blendMode = BlendMode.Screen
    )

    if (hasPress) {
        val flow = modelSmooth((maxOf(press, delayed) / 0.62f).coerceIn(0f, 1f))
        val shift = (seed - 0.5f) * 0.36f
        val sweepX = if (direction >= 0f) -0.24f + shift + flow * 1.42f else 1.24f + shift - flow * 1.42f
        val startY = when (band % 4) {
            0 -> 0.02f
            1 -> 0.74f
            2 -> 0.10f
            else -> 0.18f
        }
        val endY = when (band % 4) {
            0 -> 0.26f
            1 -> 0.98f
            2 -> 0.92f
            else -> 0.58f
        }
        val bandAlpha = modelSmooth((press / 0.50f).coerceIn(0f, 1f)) * strength.coerceIn(0.70f, 1.68f)
        val flowingRim = Brush.linearGradient(
            listOf(
                Color.Transparent,
                theme.main.modelAlpha(0.450f * bandAlpha, surfaceAlpha),
                theme.bright.modelAlpha(0.330f * bandAlpha, surfaceAlpha),
                theme.prismA.modelAlpha(0.255f * bandAlpha, surfaceAlpha),
                theme.prismB.modelAlpha(0.225f * bandAlpha, surfaceAlpha),
                theme.prismC.modelAlpha(0.210f * bandAlpha, surfaceAlpha),
                Color.Transparent
            ),
            Offset(cardSize.width * (sweepX - 0.30f), cardSize.height * startY),
            Offset(cardSize.width * (sweepX + 0.26f), cardSize.height * endY)
        )
        drawRoundRect(
            brush = flowingRim,
            topLeft = Offset(0.62.dp.toPx(), 0.62.dp.toPx()),
            size = Size(cardSize.width - 1.24.dp.toPx(), cardSize.height - 1.24.dp.toPx()),
            cornerRadius = corner,
            style = Stroke(1.34.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
}

private fun Color.modelAlpha(alpha: Float, surfaceAlpha: Float): Color = copy(alpha = (alpha * surfaceAlpha).coerceIn(0f, 1f))

private fun modelDockingProgress(phase: Float, overshoot: Float): Float {
    val t = phase.coerceIn(0f, 1f)
    val out = overshoot.coerceIn(0.030f, 0.070f)
    return when {
        t < 0.15f -> modelLerpRawFloat(0f, 0.30f, modelEaseOutCubic(t / 0.15f))
        t < 0.50f -> modelLerpRawFloat(0.30f, 0.84f, modelSmoother((t - 0.15f) / 0.35f))
        t < 0.68f -> modelLerpRawFloat(0.84f, 1f + out, modelEaseOutCubic((t - 0.50f) / 0.18f))
        t < 0.84f -> modelLerpRawFloat(1f + out, 1f + out * 0.42f, modelSmoother((t - 0.68f) / 0.16f))
        else -> modelLerpRawFloat(1f + out * 0.42f, 1f, modelSmoother((t - 0.84f) / 0.16f))
    }
}

private fun modelEaseOutCubic(value: Float): Float {
    val x = 1f - value.coerceIn(0f, 1f)
    return 1f - x * x * x
}

private fun modelSmoother(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private fun modelBlendedShapeProgress(visual: Float, motion: Float): Float {
    return modelSmooth(modelLerpRawFloat(visual.coerceIn(0f, 1f), motion.coerceIn(0f, 1f), ModelShapeMotionBlend).coerceIn(0f, 1f))
}

private fun modelContinuousLaunchVelocity(
    expanded: Boolean,
    stackRank: Int,
    stackPress: Float,
    cardPress: Float
): Float {
    if (!expanded) return 0f
    val pressEnergy = maxOf(stackPress, cardPress.coerceAtLeast(0f)).coerceIn(0f, 1f)
    val stackMass = 1f - (stackRank * 0.040f).coerceIn(0f, 0.22f)
    return (1.08f + pressEnergy * 0.64f) * stackMass
}

private fun modelCapsuleSpeedEnergy(velocity: Float, expanded: Boolean): Float {
    val forwardVelocity = if (expanded) velocity else -velocity
    return modelSmooth((forwardVelocity.coerceAtLeast(0f) / 2.25f).coerceIn(0f, 1f))
}

private fun modelContinuousCapsuleSideOffset(
    progress: Float,
    velocity: Float,
    distancePx: Float,
    overshoot: Float,
    expanded: Boolean,
    layoutSlot: Int,
    stackRank: Int
): Float {
    if (!expanded) return 0f
    val distance = distancePx.coerceAtLeast(1f)
    val amplitude = (distance * 0.020f).coerceIn(3.5f, 10.5f)
    val t = progress.coerceIn(0f, 1.18f)
    val approachWindow = modelSmoother(((t - 0.68f) / 0.24f).coerceIn(0f, 1f))
    val settleWindow = 1f - modelSmoother(((t - 0.98f) / 0.18f).coerceIn(0f, 1f))
    val forwardSpeed = modelCapsuleSpeedEnergy(velocity, expanded)
    val overshootT = ((progress - 1f) / overshoot.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    val overshootTail = 4f * modelSmoother(overshootT) * (1f - modelSmoother(overshootT))
    val direction = if ((layoutSlot + stackRank) % 2 == 0) 1f else -1f
    val side = amplitude * (
        approachWindow * settleWindow * forwardSpeed +
            overshootTail * 0.22f
    )
    return direction * side
}

private fun modelSoftLimit(value: Float, min: Float, max: Float, softness: Float): Float {
    val safeSoftness = softness.coerceAtLeast(0.001f)
    return when {
        value < min -> min - safeSoftness * modelSoftSaturate((min - value) / safeSoftness)
        value > max -> max + safeSoftness * modelSoftSaturate((value - max) / safeSoftness)
        else -> value
    }
}

private fun modelSoftSaturate(value: Float): Float {
    val x = value.coerceAtLeast(0f)
    return x / (1f + x)
}

private fun modelLerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun modelLerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun modelLerpRawFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction
private fun modelSmooth(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
