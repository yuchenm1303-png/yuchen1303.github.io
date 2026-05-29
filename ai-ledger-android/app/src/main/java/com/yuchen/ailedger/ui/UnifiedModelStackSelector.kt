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
                val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
                val scope = rememberCoroutineScope()
                val pressAnim = remember(model.id) { Animatable(0f) }
                val opticsAnim = remember(model.id) { Animatable(0f) }
                var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
                var center by remember(model.id) { mutableStateOf(Offset(0.50f, 0.42f)) }
                var seed by remember(model.id) { mutableStateOf(0.50f) }
                var direction by remember(model.id) { mutableStateOf(1f) }
                var band by remember(model.id) { mutableStateOf(0) }
                var strength by remember(model.id) { mutableStateOf(1f) }

                val targetProgress by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (expanded) 440 else 260,
                        delayMillis = if (expanded) index * 18 else (models.lastIndex - index) * 8,
                        easing = FastOutSlowInEasing
                    ),
                    label = "model-card-expand-${model.id}"
                )
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

                val pressValue = pressAnim.value.coerceIn(-0.14f, 1.08f)
                val positivePress = pressValue.coerceAtLeast(0f)
                val compression = modelSmooth((positivePress / 0.72f).coerceIn(0f, 1f))
                val rebound = modelSmooth((-pressValue / 0.10f).coerceIn(0f, 1f))
                val delayed = opticsAnim.value.coerceIn(0f, 1f)
                val selection = modelSmooth(selectionProgress.coerceIn(0f, 1f))
                val selectionBurst = if (selected) sin(selectionProgress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f) else 0f
                val materialPress = maxOf(positivePress, delayed * 0.62f, rebound * 0.24f, selectionBurst * 0.42f).coerceIn(0f, 1.08f)

                val p = modelStackEase(targetProgress)
                val width = modelLerpDp(collapsedWidth, halfWidth, p)
                val height = modelLerpDp(collapsedHeight, expandedHeight, p)
                val expandedX = if (index % 2 == 1) halfWidth + gap else 0.dp
                val expandedY = rowStep * (index / 2).toFloat()
                val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
                val collapsedY = if (selected) 0.dp else (stackRank * 1.6f).dp
                val tx = modelLerpFloat(with(density) { collapsedX.toPx() }, with(density) { expandedX.toPx() }, p)
                val ty = modelLerpFloat(with(density) { collapsedY.toPx() }, with(density) { expandedY.toPx() }, p)
                val alpha = modelLerpFloat(if (selected) 1f else 0.52f, 1f, p)
                val baseW = with(density) { width.toPx() }
                val baseH = with(density) { height.toPx() }
                val scaleX = selectedPulse * (1f + compression * 0.014f - rebound * 0.004f)
                val scaleY = selectedPulse * (1f - compression * 0.022f + rebound * 0.008f)
                val sinkY = compression * 2.10f - rebound * 0.80f
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
                    strength = strength
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
                            shadowElevation = compression * 0.45f
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
                                strength = 0.86f + Random.nextFloat() * 0.52f

                                scope.launch {
                                    pressAnim.stop()
                                    if (pressAnim.value < 0.18f) pressAnim.snapTo(0.18f)
                                    pressAnim.animateTo(0.42f, tween(132, easing = ModelPressPulse))
                                    pressAnim.animateTo(0.62f, tween(260, easing = ModelPressSink))
                                    pressAnim.animateTo(0.70f, tween(360, easing = FastOutSlowInEasing))
                                }
                                scope.launch {
                                    opticsAnim.stop()
                                    opticsAnim.animateTo(0.24f, tween(180, easing = ModelPressPreload))
                                    opticsAnim.animateTo(0.74f, tween(360, easing = ModelPressSink))
                                    opticsAnim.animateTo(0.82f, tween(420, easing = FastOutSlowInEasing))
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
                                    if (released && opticsAnim.value < 0.24f) opticsAnim.animateTo(0.32f, tween(110, easing = ModelPressPulse))
                                    opticsAnim.animateTo(0f, tween(if (released) 520 else 340, easing = FastOutSlowInEasing))
                                }
                                scope.launch {
                                    pressAnim.stop()
                                    if (released) {
                                        if (pressAnim.value.coerceIn(0f, 1.08f) < 0.46f) {
                                            pressAnim.animateTo(0.52f, tween(96, easing = ModelPressPulse))
                                            pressAnim.animateTo(-0.056f, tween(140, easing = ModelPressRelease))
                                        } else {
                                            pressAnim.animateTo(-0.060f, tween(190, easing = ModelPressRelease))
                                        }
                                        pressAnim.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow))
                                    } else {
                                        pressAnim.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                                    }
                                }
                            }
                        }
                ) {
                    UnifiedModelCardContent(model, selection, targetProgress, materialPress)
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
    val strength: Float
)

@Composable
private fun UnifiedModelCardContent(model: ChatModel, selection: Float, expansionProgress: Float, materialPress: Float) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        ModelStatusDot(selection, expansionProgress, materialPress)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(model.shortLabel, color = Color.White.copy(alpha = modelLerpFloat(0.88f, 0.985f, selection)), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.id, color = Color.White.copy(alpha = modelLerpFloat(0.46f, 0.62f, selection)), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ModelStatusDot(selection: Float, expansionProgress: Float, materialPress: Float) {
    Box(
        modifier = Modifier.size(20.dp).drawWithCache {
            val c = Offset(size.width / 2f, size.height / 2f)
            val selected = selection.coerceIn(0f, 1f)
            val press = materialPress.coerceIn(0f, 1f)
            val idleGlow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.070f + 0.045f * expansionProgress), Color.Transparent), c, size.minDimension * 0.48f)
            val pressGlow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * press), Color(0xFF9DFFF1).copy(alpha = 0.12f * press), Color.Transparent), c, size.minDimension * (0.68f + 0.20f * press))
            onDrawBehind {
                drawCircle(idleGlow, radius = size.minDimension * 0.40f, center = c, blendMode = BlendMode.Screen)
                if (press > 0.001f) drawCircle(pressGlow, radius = size.minDimension * 0.54f, center = c, blendMode = BlendMode.Screen)
                drawCircle(Color.White.copy(alpha = 0.72f * (1f - selected)), radius = size.minDimension * 0.17f, center = c)
                drawCircle(Color(0xFF8DF9EA).copy(alpha = 0.76f * selected + 0.18f * press), radius = size.minDimension * (0.17f + 0.045f * selected + 0.030f * press), center = c)
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
            val energy = v.energy.coerceIn(0f, 8f)
            val radius = minOf(v.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
            val corner = CornerRadius(radius, radius)
            val body = energy * alpha * s(style.bodyAlpha, 6f)
            val mist = energy * alpha * s(style.innerMist, 6f)
            val press = v.press.coerceIn(0f, 1.08f)
            val compression = modelSmooth((press / 0.72f).coerceIn(0f, 1f))
            val center = Offset(v.width * v.center.x.coerceIn(0f, 1f), v.height * v.center.y.coerceIn(0f, 1f))
            val innerMist = Brush.linearGradient(listOf(Color.White.copy(alpha = (0.010f + v.text * 0.004f + v.delayed * 0.004f) * mist), Color(0xFFDFFBFF).copy(alpha = (0.018f + v.text * 0.006f + v.delayed * 0.006f) * mist), Color(0xFF9FB6FF).copy(alpha = 0.006f * mist), Color.Transparent, Color(0xFF000713).copy(alpha = 0.014f * mist)), Offset(v.width * 0.08f, 0f), Offset(v.width * 0.94f, v.height))
            val bodyVeil = Brush.verticalGradient(listOf(Color.White.copy(alpha = (0.052f + v.text * 0.010f + v.delayed * 0.012f) * body), Color(0xFFB8F7FF).copy(alpha = (0.020f + v.text * 0.006f + v.delayed * 0.010f) * body), Color.Transparent, Color(0xFF000713).copy(alpha = 0.115f * body)), 0f, v.height)
            val clear = Brush.radialGradient(listOf(Color.Transparent, Color(0xFF031026).copy(alpha = 0.024f * body), Color(0xFF00040C).copy(alpha = 0.056f * body)), Offset(v.width * 0.50f, v.height * 0.58f), maxOf(v.width, v.height) * 0.78f)
            val pressure = Brush.radialGradient(listOf(Color(0xFFEFFFFF).copy(alpha = 0.045f * press), Color(0xFFB8F7FF).copy(alpha = 0.020f * press), Color.Transparent), center, maxOf(v.width, v.height) * (0.74f + 0.16f * press))
            val sink = Brush.radialGradient(listOf(Color.Transparent, Color(0xFF102C66).copy(alpha = 0.007f * press), Color(0xFF030B1A).copy(alpha = 0.038f * compression)), center, maxOf(v.width, v.height) * (0.96f + 0.05f * press))
            withTransform({ translate(v.left, v.top) }) {
                if (mist > 0.001f) drawRoundRect(innerMist, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(bodyVeil, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(clear, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                if (press > 0.001f) {
                    drawRoundRect(pressure, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(sink, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                    drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF020815).copy(alpha = 0.044f * compression)), v.height * 0.44f, v.height), size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Multiply)
                }
            }
        }

        fun drawRim(v: ModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val energy = v.energy.coerceIn(0f, 8f)
            val radius = minOf(v.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
            val corner = CornerRadius(radius, radius)
            val inset = 0.62.dp.toPx()
            val innerInset = 1.55.dp.toPx()
            val rimSize = Size((v.width - inset * 2f).coerceAtLeast(1f), (v.height - inset * 2f).coerceAtLeast(1f))
            val innerSize = Size((v.width - innerInset * 2f).coerceAtLeast(1f), (v.height - innerInset * 2f).coerceAtLeast(1f))
            val rimPower = energy * alpha
            val outer = s(style.outerRim)
            val top = s(style.topHairline) * (1f + v.text * 0.10f + v.press * 0.16f)
            val inner = s(style.innerDepth)
            val bottom = s(style.bottomShadow)
            val outerRim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.320f * rimPower * outer), Color(0xFFF1FFFF).copy(alpha = 0.150f * rimPower * outer), Color(0xFFB7FFF7).copy(alpha = 0.115f * rimPower * outer), Color.White.copy(alpha = 0.060f * rimPower * outer), Color.Transparent, Color(0xFF010817).copy(alpha = 0.225f * rimPower * bottom), Color.White.copy(alpha = 0.062f * rimPower * outer)), Offset.Zero, Offset(v.width, v.height))
            val topLine = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.020f * rimPower * top), Color.White.copy(alpha = 0.470f * rimPower * top), Color(0xFFFFF3C8).copy(alpha = 0.125f * rimPower * top), Color(0xFFE9FFFF).copy(alpha = 0.210f * rimPower * top), Color.White.copy(alpha = 0.080f * rimPower * top), Color.Transparent), 0f, v.width)
            val innerLine = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.072f * rimPower * inner), Color.Transparent, Color(0xFF000713).copy(alpha = 0.235f * rimPower * inner), Color(0xFF89FFF3).copy(alpha = 0.044f * rimPower * inner)), Offset(v.width * 0.08f, 0f), Offset(v.width * 0.92f, v.height))
            val press = v.press.coerceIn(0f, 1.08f)

            withTransform({ translate(v.left, v.top) }) {
                drawRoundRect(brush = outerRim, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(0.96.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = topLine, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.82.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = innerLine, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.54.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF00040C).copy(alpha = 0.300f * rimPower * bottom)), v.height * 0.42f, v.height), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(0.92.dp.toPx()), blendMode = BlendMode.Multiply)

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
                    val bandAlpha = modelSmooth((press / 0.50f).coerceIn(0f, 1f)) * v.strength.coerceIn(0.70f, 1.45f)
                    val pressureGlow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.120f * p), Color(0xFF9DFFF1).copy(alpha = 0.062f * p), Color(0xFFFF8FE7).copy(alpha = 0.034f * p), Color.Transparent), center, maxSide * (0.32f + 0.12f * p))
                    val flowingRim = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFF6ADB).copy(alpha = 0.20f * bandAlpha), Color.White.copy(alpha = 0.34f * bandAlpha), Color(0xFFFFE08A).copy(alpha = 0.18f * bandAlpha), Color(0xFF62FFF0).copy(alpha = 0.24f * bandAlpha), Color(0xFF92A6FF).copy(alpha = 0.12f * bandAlpha), Color.Transparent), Offset(v.width * (sweepX - 0.26f), v.height * startY), Offset(v.width * (sweepX + 0.22f), v.height * endY))
                    fun nearEdge(d: Float) = (1f - d / 0.42f).coerceIn(0f, 1f) * p
                    fun edgeHalo(power: Float, point: Offset) = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f * power), Color(0xFFFF7DE2).copy(alpha = 0.038f * power), Color(0xFFFFE28A).copy(alpha = 0.026f * power), Color(0xFF80FFF2).copy(alpha = 0.052f * power), Color.Transparent), point, maxSide * 0.36f)
                    drawRoundRect(brush = pressureGlow, size = Size(v.width, v.height), cornerRadius = corner, blendMode = BlendMode.Screen)
                    drawRoundRect(brush = Brush.radialGradient(listOf(Color(0xFFEFFFFF).copy(alpha = 0.052f * bandAlpha), Color(0xFF92FFF1).copy(alpha = 0.026f * bandAlpha), Color.Transparent), center, maxSide * 0.74f), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(0.74.dp.toPx() + 0.26.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = flowingRim, topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.02.dp.toPx()), blendMode = BlendMode.Plus)
                    drawRoundRect(brush = edgeHalo(nearEdge(centerNorm.y), Offset(center.x, inset)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.18.dp.toPx() + 0.48.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = edgeHalo(nearEdge(1f - centerNorm.y), Offset(center.x, v.height - inset)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.18.dp.toPx() + 0.48.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = edgeHalo(nearEdge(centerNorm.x), Offset(inset, center.y)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.18.dp.toPx() + 0.48.dp.toPx() * p), blendMode = BlendMode.Screen)
                    drawRoundRect(brush = edgeHalo(nearEdge(1f - centerNorm.x), Offset(v.width - inset, center.y)), topLeft = Offset(inset, inset), size = rimSize, cornerRadius = corner, style = Stroke(1.18.dp.toPx() + 0.48.dp.toPx() * p), blendMode = BlendMode.Screen)
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
private fun modelSmooth(value: Float): Float { val x = value.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
private fun modelStackEase(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val smooth = modelSmooth(p)
    val elastic = sin(p * PI.toFloat()).coerceAtLeast(0f) * 0.030f * (1f - abs(0.5f - p) * 1.6f).coerceIn(0f, 1f)
    return (smooth + elastic).coerceIn(0f, 1f)
}
