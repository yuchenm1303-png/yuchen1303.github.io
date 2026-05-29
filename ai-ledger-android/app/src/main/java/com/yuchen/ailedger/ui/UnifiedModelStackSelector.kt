package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.random.Random
import kotlinx.coroutines.launch

private val ModelPressPreload = CubicBezierEasing(0.20f, 0.00f, 0.18f, 1.00f)
private val ModelPressSink = CubicBezierEasing(0.14f, 0.00f, 0.10f, 1.00f)
private val ModelPressRelease = CubicBezierEasing(0.18f, 0.00f, 0.16f, 1.00f)
private val ModelPressPulse = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)
private val ModelCapsuleTravel = CubicBezierEasing(0.18f, 0.00f, 0.12f, 1.00f)
private val ModelCapsuleBrake = CubicBezierEasing(0.16f, 0.00f, 0.16f, 1.00f)

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

private fun ChatModel.modelCardPrismTheme(): ModelCardPrismTheme = when (this) {
    ChatModel.Auto -> ModelCardPrismTheme(Color(0xFF77FFF0), Color.White, Color(0xFF07142D), Color(0xFF62FFF0), Color(0xFFFF70D9), Color(0xFF8EA2FF), Color(0xFFFFE08A), 0.00f)
    ChatModel.Gemini -> ModelCardPrismTheme(Color(0xFF2F8CFF), Color(0xFFE2F7FF), Color(0xFF061A42), Color(0xFF37F6FF), Color(0xFF4F6CFF), Color(0xFF126BFF), Color(0xFFBEEFFF), 0.98f)
    ChatModel.Kimi -> ModelCardPrismTheme(Color(0xFFE35CFF), Color(0xFFFFE0FF), Color(0xFF2A0C45), Color(0xFFFF7AE4), Color(0xFFB85CFF), Color(0xFFFF4FC6), Color(0xFFFFB5F0), 1.00f)
    ChatModel.Mistral -> ModelCardPrismTheme(Color(0xFFFFC247), Color(0xFFFFF3C4), Color(0xFF3B2205), Color(0xFFFFE07A), Color(0xFFFF9B35), Color(0xFFFFD15C), Color(0xFFFFE26D), 0.98f)
    ChatModel.Workers -> ModelCardPrismTheme(Color(0xFFFF4F47), Color(0xFFFFD8D0), Color(0xFF3A0D0A), Color(0xFFFF7B5C), Color(0xFFFF3D86), Color(0xFFFF6B3D), Color(0xFFFFB06A), 1.00f)
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
        val models = ChatModel.entries
        val style = ModelCardGlassLabState.style
        val visuals = remember { mutableStateListOf<ModelCardVisual>() }
        visuals.clear()

        val gap = 10.dp
        val rowStep = 74.dp
        val collapsedHeight = 56.dp
        val expandedHeight = 64.dp
        val reservedGap = 8.dp
        val collapsedWidth = (maxWidth - reservedGap) * 0.642f
        val halfWidth = (maxWidth - gap) / 2f
        val selectedModel = state.selectedModel
        val behindModels = models.filter { it != selectedModel }

        Box(Modifier.fillMaxSize().drawModelCardGlass(visuals, style)) {
            models.forEachIndexed { index, model ->
                val selected = model == selectedModel
                val theme = model.modelCardPrismTheme()
                val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
                val scope = rememberCoroutineScope()
                val pressAnim = remember(model.id) { Animatable(0f) }
                val opticsAnim = remember(model.id) { Animatable(0f) }
                val travelAnim = remember(model.id) { Animatable(if (expanded) 1f else 0f) }
                val capsuleAnim = remember(model.id) { Animatable(0f) }
                var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
                var center by remember(model.id) { mutableStateOf(Offset(0.50f, 0.42f)) }
                var seed by remember(model.id) { mutableStateOf(0.50f) }
                var direction by remember(model.id) { mutableStateOf(1f) }
                var band by remember(model.id) { mutableStateOf(0) }
                var strength by remember(model.id) { mutableStateOf(1f) }

                val selectionProgress by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(durationMillis = if (selected) 520 else 260, delayMillis = if (selected) 28 else 0, easing = FastOutSlowInEasing),
                    label = "model-card-selection-material-${model.id}"
                )
                val selectedPulse by animateFloatAsState(
                    targetValue = if (selected) 1.008f else 1f,
                    animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow),
                    label = "model-card-selected-${model.id}"
                )

                LaunchedEffect(expanded, selected, model.id) {
                    val target = if (expanded) 1f else 0f
                    val reverseRank = (behindModels.size - stackRank).coerceAtLeast(0).toLong()
                    val travelDuration = if (expanded) {
                        (360 + stackRank * 44).coerceAtMost(548)
                    } else {
                        if (selected) 520 else 340 + reverseRank.toInt() * 42
                    }

                    launch {
                        capsuleAnim.stop()
                        capsuleAnim.snapTo(0f)
                        capsuleAnim.animateTo(1f, tween(durationMillis = if (expanded) 96 else 82, easing = ModelPressPulse))
                        capsuleAnim.animateTo(0f, tween(durationMillis = if (expanded) 270 else 220, easing = FastOutSlowInEasing))
                    }

                    travelAnim.stop()
                    travelAnim.animateTo(target, tween(durationMillis = travelDuration, easing = LinearEasing))
                }

                val pressValue = pressAnim.value.coerceIn(-0.16f, 1.12f)
                val positivePress = pressValue.coerceAtLeast(0f)
                val compression = modelSmooth((positivePress / 0.72f).coerceIn(0f, 1f))
                val rebound = modelSmooth((-pressValue / 0.11f).coerceIn(0f, 1f))
                val delayed = opticsAnim.value.coerceIn(0f, 1f)
                val selection = modelSmooth(selectionProgress.coerceIn(0f, 1f))
                val capsuleLaunch = modelSmooth(capsuleAnim.value.coerceIn(0f, 1f))
                val rawTravelProgress = travelAnim.value.coerceIn(0f, 1f)
                val travelPhase = if (expanded) rawTravelProgress else 1f - rawTravelProgress
                val pathProgress = modelCapsuleOvershootPath(travelPhase)
                val p = if (expanded) pathProgress else 1f - pathProgress
                val targetProgress = p.coerceIn(0f, 1f)
                val stackReveal = 1f - targetProgress
                val overshootAmount = if (expanded) (p - 1f).coerceAtLeast(0f) else (-p).coerceAtLeast(0f)
                val arrivalBrake = modelSmooth((overshootAmount / 0.090f).coerceIn(0f, 1f))
                val selectionBurst = if (selected) sin(selectionProgress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f) else 0f
                val materialPress = maxOf(positivePress, delayed * 0.92f, rebound * 0.42f, capsuleLaunch * 0.34f, arrivalBrake * 0.30f, selectionBurst * 0.52f).coerceIn(0f, 1.16f)

                val easedProgress = targetProgress
                val width = modelLerpDp(collapsedWidth, halfWidth, easedProgress)
                val height = modelLerpDp(collapsedHeight, expandedHeight, easedProgress)
                val expandedX = if (index % 2 == 1) halfWidth + gap else 0.dp
                val expandedY = rowStep * (index / 2).toFloat()
                val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
                val collapsedY = if (selected) 0.dp else (stackRank * 1.6f).dp
                val collapsedXPx = with(density) { collapsedX.toPx() }
                val collapsedYPx = with(density) { collapsedY.toPx() }
                val expandedXPx = with(density) { expandedX.toPx() }
                val expandedYPx = with(density) { expandedY.toPx() }
                val motionX = expandedXPx - collapsedXPx
                val motionY = expandedYPx - collapsedYPx
                val travelTotal = abs(motionX) + abs(motionY) + 0.001f
                val horizontalMotion = abs(motionX) / travelTotal
                val verticalMotion = abs(motionY) / travelTotal
                val tx = modelLerpRawFloat(collapsedXPx, expandedXPx, p)
                val ty = modelLerpRawFloat(collapsedYPx, expandedYPx, p)
                val alpha = modelLerpFloat(if (selected) 1f else 0.52f, 1f, easedProgress)
                val baseW = with(density) { width.toPx() }
                val baseH = with(density) { height.toPx() }
                val arrivalScaleX = arrivalBrake * (0.013f * horizontalMotion - 0.004f * verticalMotion)
                val arrivalScaleY = arrivalBrake * (0.010f * verticalMotion - 0.007f * horizontalMotion)
                val scaleX = selectedPulse * (1f + compression * 0.044f - rebound * 0.012f + capsuleLaunch * 0.026f + arrivalScaleX)
                val scaleY = selectedPulse * (1f - compression * 0.054f + rebound * 0.024f - capsuleLaunch * 0.017f + arrivalScaleY)
                val sinkY = compression * 3.70f - rebound * 1.05f + capsuleLaunch * 0.72f + arrivalBrake * 0.08f
                val energy = if (selected) modelLerpFloat(0.50f * style.unselectedEnergy.coerceIn(0f, 5f), 1f, selection) else 0.50f * style.unselectedEnergy.coerceIn(0f, 5f)

                visuals.add(ModelCardVisual(
                    left = tx - baseW * center.x * (scaleX - 1f),
                    top = ty + sinkY - baseH * center.y * (scaleY - 1f),
                    width = baseW * scaleX,
                    height = baseH * scaleY,
                    alpha = alpha,
                    selected = selected,
                    text = selection,
                    energy = energy,
                    press = materialPress,
                    delayed = delayed,
                    center = center,
                    seed = seed,
                    direction = direction,
                    band = band,
                    strength = strength,
                    stackReveal = stackReveal,
                    theme = theme
                ))

                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .zIndex(if (selected) 50f else 30f - index)
                        .onSizeChanged { cardSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat()) }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(center.x, center.y)
                            translationX = tx
                            translationY = ty + sinkY
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                            this.alpha = alpha
                            shadowElevation = compression * 0.62f + capsuleLaunch * 0.25f + arrivalBrake * 0.12f
                        }
                        .pointerInput(state.isSending, expanded, model.id) {
                            awaitEachGesture {
                                fun updateCenter(position: Offset) {
                                    center = Offset(
                                        (position.x / cardSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                        (position.y / cardSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    )
                                }
                                val down = awaitFirstDown(requireUnconsumed = false)
                                updateCenter(down.position)
                                if (state.isSending) return@awaitEachGesture

                                seed = Random.nextFloat()
                                direction = if (Random.nextBoolean()) 1f else -1f
                                band = Random.nextInt(0, 4)
                                strength = 1.06f + Random.nextFloat() * 0.62f

                                scope.launch {
                                    pressAnim.stop()
                                    if (pressAnim.value < 0.26f) pressAnim.snapTo(0.26f)
                                    pressAnim.animateTo(0.72f, tween(118, easing = ModelPressPulse))
                                    pressAnim.animateTo(0.82f, tween(250, easing = ModelPressSink))
                                    pressAnim.animateTo(0.76f, tween(360, easing = FastOutSlowInEasing))
                                }
                                scope.launch {
                                    opticsAnim.stop()
                                    opticsAnim.animateTo(0.42f, tween(140, easing = ModelPressPreload))
                                    opticsAnim.animateTo(1.02f, tween(320, easing = ModelPressSink))
                                    opticsAnim.animateTo(1.06f, tween(420, easing = FastOutSlowInEasing))
                                }

                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (tracked != null) {
                                        updateCenter(tracked.position)
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

                                if (released) {
                                    if (expanded) onSelected(model) else onToggleExpanded()
                                }
                                scope.launch {
                                    opticsAnim.stop()
                                    if (released && opticsAnim.value < 0.46f) opticsAnim.animateTo(0.58f, tween(120, easing = ModelPressPulse))
                                    opticsAnim.animateTo(0f, tween(if (released) 720 else 380, easing = FastOutSlowInEasing))
                                }
                                scope.launch {
                                    pressAnim.stop()
                                    if (released) {
                                        if (pressAnim.value.coerceIn(0f, 1.16f) < 0.54f) {
                                            pressAnim.animateTo(0.62f, tween(88, easing = ModelPressPulse))
                                            pressAnim.animateTo(-0.074f, tween(150, easing = ModelPressRelease))
                                        } else {
                                            pressAnim.animateTo(-0.080f, tween(202, easing = ModelPressRelease))
                                        }
                                        pressAnim.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
                                    } else {
                                        pressAnim.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                                    }
                                }
                            }
                        }
                ) {
                    UnifiedModelCardContent(model, selected, stackRank, selection, targetProgress, stackReveal, materialPress, theme)
                }
            }
        }
    }
}

private data class ModelCardVisual(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
    val selected: Boolean,
    val text: Float,
    val energy: Float,
    val press: Float,
    val delayed: Float,
    val center: Offset,
    val seed: Float,
    val direction: Float,
    val band: Int,
    val strength: Float,
    val stackReveal: Float,
    val theme: ModelCardPrismTheme
)

@Composable
private fun UnifiedModelCardContent(
    model: ChatModel,
    selected: Boolean,
    stackRank: Int,
    selection: Float,
    expansionProgress: Float,
    stackReveal: Float,
    materialPress: Float,
    theme: ModelCardPrismTheme
) {
    val density = LocalDensity.current
    val collapsed = stackReveal.coerceIn(0f, 1f)
    val fullTextAlpha = if (selected) 1f else expansionProgress.coerceIn(0f, 1f) * 0.92f
    val stackLabelAlpha = if (selected) 0f else modelSmooth(collapsed) * 0.62f
    val labelShiftX = with(density) { (stackRank * 24).dp.toPx() }
    val labelShiftY = with(density) { ((stackRank - 1) * 4).dp.toPx() }

    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        ModelStatusDot(selection, expansionProgress, materialPress, theme)
        Box(Modifier.weight(1f).fillMaxSize()) {
            Column(
                Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = fullTextAlpha },
                verticalArrangement = Arrangement.Center
            ) {
                Text(model.shortLabel, color = Color.White.copy(alpha = modelLerpFloat(0.88f, 0.985f, selection)), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.id, color = Color.White.copy(alpha = modelLerpFloat(0.46f, 0.62f, selection)), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selected) {
                Text(
                    model.shortLabel,
                    modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer {
                        alpha = stackLabelAlpha
                        translationX = -labelShiftX
                        translationY = labelShiftY
                    },
                    color = theme.bright.copy(alpha = 0.86f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun ModelStatusDot(selection: Float, expansionProgress: Float, materialPress: Float, theme: ModelCardPrismTheme) {
    Box(
        modifier = Modifier.size(20.dp).drawWithCache {
            val c = Offset(size.width / 2f, size.height / 2f)
            val selected = selection.coerceIn(0f, 1f)
            val press = materialPress.coerceIn(0f, 1f)
            val autoRainbow = theme.themeWeight <= 0.01f
            val coreAlpha = (0.70f * (1f - selected) + 0.94f * selected + 0.09f * press).coerceIn(0f, 1f)
            val themeLift = selected * theme.themeWeight
            val idleGlow = Brush.radialGradient(
                listOf(
                    theme.bright.copy(alpha = 0.060f + 0.040f * expansionProgress),
                    theme.main.copy(alpha = (0.110f + 0.180f * themeLift) * selected),
                    theme.prismB.copy(alpha = (0.030f + 0.075f * themeLift) * selected),
                    Color.Transparent
                ),
                c,
                size.minDimension * 0.60f
            )
            val pressGlow = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.18f * press),
                    theme.main.copy(alpha = 0.34f * press),
                    theme.prismB.copy(alpha = 0.150f * press),
                    theme.gold.copy(alpha = 0.105f * press),
                    Color.Transparent
                ),
                c,
                size.minDimension * (0.78f + 0.20f * press)
            )
            val rainbowRing = Brush.sweepGradient(
                listOf(
                    theme.prismA.copy(alpha = 0.78f),
                    theme.gold.copy(alpha = 0.76f),
                    theme.prismB.copy(alpha = 0.78f),
                    theme.prismC.copy(alpha = 0.78f),
                    theme.prismA.copy(alpha = 0.78f)
                ),
                c
            )
            onDrawBehind {
                drawCircle(idleGlow, radius = size.minDimension * 0.47f, center = c, blendMode = BlendMode.Screen)
                if (press > 0.001f) drawCircle(pressGlow, radius = size.minDimension * 0.66f, center = c, blendMode = BlendMode.Screen)
                if (autoRainbow) {
                    drawCircle(rainbowRing, radius = size.minDimension * (0.245f + 0.020f * selected + 0.018f * press), center = c, style = Stroke(size.minDimension * 0.070f), blendMode = BlendMode.Screen)
                    drawCircle(Color.White.copy(alpha = 0.24f + 0.15f * selected + 0.08f * press), radius = size.minDimension * (0.120f + 0.012f * press), center = c, blendMode = BlendMode.Screen)
                } else {
                    drawCircle(theme.main.copy(alpha = 0.82f * selected + 0.26f * press), radius = size.minDimension * (0.22f + 0.045f * selected + 0.026f * press), center = c)
                    drawCircle(Color.White.copy(alpha = coreAlpha), radius = size.minDimension * (0.090f + 0.014f * press), center = c)
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {}
}

private fun Modifier.drawModelCardGlass(visuals: List<ModelCardVisual>, style: ModelCardGlassStyle): Modifier = drawWithCache {
    fun s(value: Float, max: Float = 8f) = value.coerceIn(0f, max)
    onDrawWithContent {
        fun drawBody(v: ModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val theme = v.theme
            val energy = v.energy.coerceIn(0f, 8f)
            val radius = minOf(v.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
            val corner = CornerRadius(radius, radius)
            val body = energy * alpha * s(style.bodyAlpha, 6f)
            val mist = energy * alpha * s(style.innerMist, 6f)
            val press = v.press.coerceIn(0f, 1.12f)
            val compression = modelSmooth((press / 0.72f).coerceIn(0f, 1f))
            val optics = modelSmooth((maxOf(v.delayed, press * 0.72f) / 1.04f).coerceIn(0f, 1f))
            val refraction = (optics * 1.10f + compression * 0.46f).coerceIn(0f, 1.58f)
            val prismPower = (optics * 0.72f + compression * 0.36f).coerceIn(0f, 1.08f)
            val selectedGlow = v.text.coerceIn(0f, 1f)
            val themeSignal = (0.55f + selectedGlow * 0.90f + compression * 0.34f).coerceIn(0f, 1.65f)
            val themePower = theme.themeWeight * themeSignal
            val auraPower = alpha * selectedGlow * s(style.selectedAura, 8f)
            val px = v.center.x.coerceIn(0f, 1f)
            val py = v.center.y.coerceIn(0f, 1f)
            val leftWeight = (1f - px) * refraction
            val rightWeight = px * refraction
            val topWeight = (1f - py) * refraction
            val bottomWeight = py * refraction
            val centerWeight = (1f - (abs(px - 0.5f) + abs(py - 0.5f)).coerceIn(0f, 1f)) * refraction
            val horizontalTilt = (px - 0.5f) * 2f
            val verticalTilt = (py - 0.5f) * 2f
            val materialCenter = Offset(v.width * 0.52f, v.height * 0.50f)
            val pullX = horizontalTilt * compression * 0.034f
            val pullY = verticalTilt * compression * 0.052f
            val pullStart = Offset(v.width * (-0.10f + pullX), v.height * (-0.18f + pullY))
            val pullEnd = Offset(v.width * (1.10f + pullX), v.height * (1.14f + pullY))
            val aura = Brush.linearGradient(listOf(theme.main.copy(alpha = (0.048f + 0.062f * themePower) * auraPower), theme.bright.copy(alpha = 0.014f * auraPower), theme.prismB.copy(alpha = (0.018f + 0.024f * themePower) * auraPower), theme.gold.copy(alpha = 0.010f * auraPower), theme.prismC.copy(alpha = 0.016f * auraPower), Color.Transparent), Offset(v.width * -0.14f, v.height * -0.20f), Offset(v.width * 1.08f, v.height * 1.10f))
            val refractedBackdrop = Brush.linearGradient(listOf(theme.deep.copy(alpha = 0.160f * refraction), theme.main.copy(alpha = (0.070f + 0.075f * themePower) * refraction), Color(0xFF050D24).copy(alpha = 0.176f * refraction), theme.prismC.copy(alpha = (0.040f + 0.045f * themePower) * refraction), Color(0xFF020716).copy(alpha = 0.208f * refraction)), pullStart, pullEnd)
            val refractedCyan = Brush.linearGradient(listOf(Color.Transparent, theme.prismA.copy(alpha = 0.080f * refraction + 0.050f * leftWeight), Color.Transparent, theme.main.copy(alpha = (0.044f + 0.090f * themePower) * centerWeight), Color.Transparent), Offset(v.width * (-0.26f + pullX * 0.65f), v.height * 0.00f), Offset(v.width * (0.96f + pullX * 0.65f), v.height * 0.92f))
            val refractedMagenta = Brush.linearGradient(listOf(Color.Transparent, theme.prismB.copy(alpha = 0.048f * refraction + 0.030f * rightWeight), Color.Transparent, theme.gold.copy(alpha = 0.030f * topWeight), Color.Transparent), Offset(v.width * (1.18f + pullX * 0.50f), v.height * -0.08f), Offset(v.width * (-0.18f + pullX * 0.50f), v.height * 1.02f))
            val pressureBend = Brush.radialGradient(listOf(Color.Transparent, theme.deep.copy(alpha = 0.092f * compression + 0.026f * centerWeight), Color(0xFF00040D).copy(alpha = 0.112f * compression), Color.Transparent), materialCenter, maxOf(v.width, v.height) * (0.66f + 0.08f * compression))
            val fixedPrismField = Brush.linearGradient(listOf(Color.Transparent, theme.prismA.copy(alpha = 0.036f * prismPower + 0.030f * leftWeight), theme.main.copy(alpha = (0.050f + 0.084f * themePower) * prismPower + 0.038f * centerWeight), theme.prismB.copy(alpha = 0.026f * prismPower + 0.020f * rightWeight), theme.gold.copy(alpha = 0.028f * prismPower + 0.024f * topWeight), Color.Transparent), Offset(v.width * -0.16f, v.height * -0.12f), Offset(v.width * 1.14f, v.height * 1.10f))
            val innerMist = Brush.linearGradient(listOf(Color.White.copy(alpha = (0.004f + v.text * 0.002f + v.delayed * 0.001f) * mist), theme.bright.copy(alpha = (0.007f + v.text * 0.003f + v.delayed * 0.002f) * mist), theme.prismC.copy(alpha = 0.004f * mist), Color.Transparent, Color(0xFF000713).copy(alpha = 0.018f * mist)), Offset(v.width * 0.08f, 0f), Offset(v.width * 0.94f, v.height))
            val bodyVeil = Brush.verticalGradient(listOf(Color.White.copy(alpha = (0.018f + v.text * 0.004f + v.delayed * 0.002f) * body), theme.main.copy(alpha = (0.014f + v.text * 0.012f + v.delayed * 0.005f) * body), Color.Transparent, Color(0xFF000713).copy(alpha = 0.152f * body)), 0f, v.height)
            val clear = Brush.radialGradient(listOf(Color.Transparent, Color(0xFF031026).copy(alpha = 0.034f * body), Color(0xFF00040C).copy(alpha = 0.080f * body)), materialCenter, maxOf(v.width, v.height) * 0.78f)
            val horizontalResponse = Brush.horizontalGradient(listOf(theme.prismA.copy(alpha = 0.052f * leftWeight), Color.Transparent, theme.prismB.copy(alpha = 0.035f * rightWeight)), 0f, v.width)
            val verticalResponse = Brush.verticalGradient(listOf(theme.bright.copy(alpha = 0.020f * topWeight), Color.Transparent, Color(0xFF000713).copy(alpha = 0.062f * bottomWeight + 0.050f * compression)), 0f, v.height)
            val materialBend = Brush.linearGradient(listOf(Color(0xFF000713).copy(alpha = 0.026f * compression * (1f + verticalTilt.coerceAtLeast(0f) * 0.25f)), Color.Transparent, theme.bright.copy(alpha = 0.008f * prismPower * (1f - abs(horizontalTilt) * 0.22f)), Color.Transparent, Color(0xFF020815).copy(alpha = 0.040f * compression)), Offset(v.width * -0.04f, 0f), Offset(v.width * 1.04f, v.height))
            val bottomDepth = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF020815).copy(alpha = 0.066f * compression)), v.height * 0.44f, v.height)
            withTransform({ translate(v.left, v.top) }) {
                if (auraPower > 0.001f) drawRoundRect(brush = aura, topLeft = Offset(-3.4.dp.toPx(), -3.4.dp.toPx()), size = Size(v.width + 6.8.dp.toPx(), v.height + 6.8.dp.toPx()), cornerRadius = CornerRadius(radius + 3.4.dp.toPx(), radius + 3.4.dp.toPx()), blendMode = BlendMode.Screen)
                if (mist > 0.001f) drawRoundRect(innerMist, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(bodyVeil, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(clear, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                if (press > 0.001f) {
                    drawRoundRect(brush = refractedBackdrop, size = Size(v.width, v.height), cornerRadius = corner)
                    drawRoundRect(materialBend, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                    drawRoundRect(pressureBend, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                    drawRoundRect(brush = refractedCyan, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = refractedMagenta, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = fixedPrismField, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = horizontalResponse, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = verticalResponse, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                    drawRoundRect(bottomDepth, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                }
            }
        }

        fun drawRim(v: ModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val theme = v.theme
            val energy = v.energy.coerceIn(0f, 8f)
            val radius = minOf(v.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
            val corner = CornerRadius(radius, radius)
            val inset = 0.62.dp.toPx()
            val innerInset = 1.55.dp.toPx()
            val rimSize = Size((v.width - inset * 2f).coerceAtLeast(1f), (v.height - inset * 2f).coerceAtLeast(1f))
            val innerSize = Size((v.width - innerInset * 2f).coerceAtLeast(1f), (v.height - innerInset * 2f).coerceAtLeast(1f))
            val rimPower = energy * alpha
            val selectedGlow = v.text.coerceIn(0f, 1f)
            val collapsedReveal = modelSmooth(v.stackReveal.coerceIn(0f, 1f))
            val stackBoost = collapsedReveal * if (v.selected) 0.45f else 1.12f
            val outer = s(style.outerRim) * (1f + stackBoost * 0.22f)
            val top = s(style.topHairline) * (1f + v.text * 0.10f + v.press * 0.20f)
            val inner = s(style.innerDepth)
            val bottom = s(style.bottomShadow)
            val rainbow = s(style.selectedRainbowRim, 8f)
            val halo = s(style.selectedOuterHalo, 8f)
            val themeBoost = (theme.themeWeight * (0.62f + selectedGlow * 0.70f + stackBoost * 0.40f)).coerceIn(0f, 1.55f)
            val outerRim = Brush.linearGradient(listOf(theme.bright.copy(alpha = 0.260f * rimPower * outer), Color(0xFFF1FFFF).copy(alpha = 0.082f * rimPower * outer), theme.main.copy(alpha = (0.150f + 0.170f * themeBoost) * rimPower * outer), Color.White.copy(alpha = 0.045f * rimPower * outer), Color.Transparent, theme.deep.copy(alpha = 0.225f * rimPower * bottom), theme.prismC.copy(alpha = 0.072f * rimPower * outer)), Offset.Zero, Offset(v.width, v.height))
            val topLine = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.016f * rimPower * top), theme.bright.copy(alpha = 0.360f * rimPower * top), theme.main.copy(alpha = (0.220f + 0.240f * themeBoost) * rimPower * top), theme.prismA.copy(alpha = 0.120f * rimPower * top), theme.prismB.copy(alpha = 0.075f * rimPower * top), Color.Transparent), 0f, v.width)
            val innerLine = Brush.linearGradient(listOf(theme.bright.copy(alpha = 0.070f * rimPower * inner), Color.Transparent, Color(0xFF000713).copy(alpha = 0.235f * rimPower * inner), theme.main.copy(alpha = 0.075f * rimPower * inner)), Offset(v.width * 0.08f, 0f), Offset(v.width * 0.92f, v.height))
            val selectedHalo = Brush.linearGradient(listOf(theme.main.copy(alpha = (0.180f + 0.180f * themeBoost) * alpha * halo * selectedGlow), Color.White.copy(alpha = 0.045f * alpha * halo * selectedGlow), theme.prismB.copy(alpha = 0.060f * alpha * halo * selectedGlow), theme.gold.copy(alpha = 0.032f * alpha * halo * selectedGlow), Color.Transparent, theme.prismC.copy(alpha = 0.080f * alpha * halo * selectedGlow)), Offset(v.width * -0.12f, v.height * -0.18f), Offset(v.width * 1.08f, v.height * 1.10f))
            val selectedRainbow = Brush.linearGradient(listOf(theme.main.copy(alpha = (0.680f + 0.420f * themeBoost) * alpha * rainbow * selectedGlow), theme.bright.copy(alpha = 0.170f * alpha * rainbow * selectedGlow), theme.prismA.copy(alpha = 0.155f * alpha * rainbow * selectedGlow), theme.prismB.copy(alpha = 0.145f * alpha * rainbow * selectedGlow), theme.prismC.copy(alpha = 0.190f * alpha * rainbow * selectedGlow), Color.Transparent), Offset(v.width * -0.08f, 0f), Offset(v.width * 1.02f, v.height * 0.78f))
            val rightEdgeAccent = Brush.horizontalGradient(listOf(Color.Transparent, theme.prismC.copy(alpha = 0.044f * rimPower * stackBoost), theme.main.copy(alpha = (0.140f + 0.130f * themeBoost) * rimPower * stackBoost), theme.bright.copy(alpha = 0.390f * rimPower * stackBoost)), startX = v.width * 0.64f, endX = v.width)
            val bottomEdgeAccent = Brush.verticalGradient(listOf(Color.Transparent, theme.prismB.copy(alpha = 0.040f * rimPower * stackBoost), theme.main.copy(alpha = (0.124f + 0.110f * themeBoost) * rimPower * stackBoost), theme.bright.copy(alpha = 0.350f * rimPower * stackBoost)), startY = v.height * 0.54f, endY = v.height)
            val rightBottomHalo = Brush.linearGradient(listOf(Color.Transparent, theme.main.copy(alpha = 0.120f * alpha * stackBoost), theme.bright.copy(alpha = 0.150f * alpha * stackBoost), theme.prismC.copy(alpha = 0.090f * alpha * stackBoost), Color.Transparent), Offset(v.width * 0.58f, v.height * 0.24f), Offset(v.width * 1.10f, v.height * 1.06f))
            val press = v.press.coerceIn(0f, 1.16f)

            withTransform({ translate(v.left, v.top) }) {
                if (selectedGlow > 0.001f && halo > 0.001f) drawRoundRect(brush = selectedHalo, topLeft = Offset(-1.70.dp.toPx(), -1.70.dp.toPx()), size = Size(v.width + 3.40.dp.toPx(), v.height + 3.40.dp.toPx()), cornerRadius = CornerRadius(radius + 1.70.dp.toPx(), radius + 1.70.dp.toPx()), style = Stroke(2.25.dp.toPx()), blendMode = BlendMode.Screen)
                if (selectedGlow > 0.001f && rainbow > 0.001f) drawRoundRect(brush = selectedRainbow, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.42.dp.toPx()), blendMode = BlendMode.Plus)
                drawRoundRect(brush = outerRim, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(0.96.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = topLine, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.82.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerLine, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.54.dp.toPx()), blendMode = BlendMode.Screen)
                if (collapsedReveal > 0.001f) {
                    drawRoundRect(brush = rightBottomHalo, topLeft = Offset(-1.30.dp.toPx(), -1.30.dp.toPx()), size = Size(v.width + 2.60.dp.toPx(), v.height + 2.60.dp.toPx()), cornerRadius = CornerRadius(radius + 1.30.dp.toPx(), radius + 1.30.dp.toPx()), style = Stroke(1.60.dp.toPx()), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = rightEdgeAccent, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.18.dp.toPx()), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = bottomEdgeAccent, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.24.dp.toPx()), blendMode = BlendMode.Screen)
                }
                drawRoundRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF00040C).copy(alpha = 0.230f * rimPower * bottom)), v.height * 0.42f, v.height), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(0.92.dp.toPx()), blendMode = BlendMode.Multiply)

                if (press > 0.001f) {
                    val p = modelSmooth((press / 0.72f).coerceIn(0f, 1f))
                    val centerNorm = Offset(v.center.x.coerceIn(0f, 1f), v.center.y.coerceIn(0f, 1f))
                    val center = Offset(centerNorm.x * v.width, centerNorm.y * v.height)
                    val maxSide = maxOf(v.width, v.height)
                    val flow = modelSmooth((maxOf(press, v.delayed) / 0.62f).coerceIn(0f, 1f))
                    val shift = (v.seed - 0.5f) * 0.36f
                    val sweepX = if (v.direction >= 0f) -0.24f + shift + flow * 1.42f else 1.24f + shift - flow * 1.42f
                    val startY = when (v.band % 4) { 0 -> 0.02f; 1 -> 0.74f; 2 -> 0.10f; else -> 0.18f }
                    val endY = when (v.band % 4) { 0 -> 0.26f; 1 -> 0.98f; 2 -> 0.92f; else -> 0.58f }
                    val bandAlpha = modelSmooth((press / 0.50f).coerceIn(0f, 1f)) * v.strength.coerceIn(0.70f, 1.68f)
                    val flowingRim = Brush.linearGradient(listOf(Color.Transparent, theme.main.copy(alpha = 0.450f * bandAlpha), theme.bright.copy(alpha = 0.330f * bandAlpha), theme.prismA.copy(alpha = 0.255f * bandAlpha), theme.prismB.copy(alpha = 0.225f * bandAlpha), theme.prismC.copy(alpha = 0.210f * bandAlpha), Color.Transparent), Offset(v.width * (sweepX - 0.30f), v.height * startY), Offset(v.width * (sweepX + 0.26f), v.height * endY))
                    fun nearEdge(d: Float) = (1f - d / 0.42f).coerceIn(0f, 1f) * p
                    fun edgeHalo(power: Float, point: Offset) = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.210f * power), theme.main.copy(alpha = 0.210f * power), theme.prismA.copy(alpha = 0.080f * power), theme.prismB.copy(alpha = 0.070f * power), Color.Transparent), point, maxSide * 0.40f)
                    drawRoundRect(brush = Brush.radialGradient(listOf(theme.bright.copy(alpha = 0.070f * bandAlpha), theme.main.copy(alpha = 0.120f * bandAlpha), theme.prismB.copy(alpha = 0.026f * bandAlpha), Color.Transparent), center, maxSide * 0.80f), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(0.90.dp.toPx() + 0.34.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = flowingRim, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.34.dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = edgeHalo(nearEdge(centerNorm.y), Offset(center.x, inset)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.35.dp.toPx() + 0.60.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = edgeHalo(nearEdge(1f - centerNorm.y), Offset(center.x, v.height - inset)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.35.dp.toPx() + 0.60.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = edgeHalo(nearEdge(centerNorm.x), Offset(inset, center.y)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.35.dp.toPx() + 0.60.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = edgeHalo(nearEdge(1f - centerNorm.x), Offset(v.width - inset, center.y)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.35.dp.toPx() + 0.60.dp.toPx() * p), blendMode = BlendMode.Screen)
                }
            }
        }

        visuals.forEach { drawBody(it) }
        drawContent()
        visuals.forEach { drawRim(it) }
    }
}

private fun modelLerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun modelLerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun modelLerpRawFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction
private fun modelSmooth(value: Float): Float { val x = value.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
private fun modelCapsuleOvershootPath(phase: Float): Float {
    val x = phase.coerceIn(0f, 1f)
    val base = ModelCapsuleTravel.transform(x)
    val enter = modelSmooth(((x - 0.50f) / 0.26f).coerceIn(0f, 1f))
    val leave = 1f - modelSmooth(((x - 0.80f) / 0.20f).coerceIn(0f, 1f))
    val brakePulse = enter * leave
    return base + brakePulse * 0.092f
}
