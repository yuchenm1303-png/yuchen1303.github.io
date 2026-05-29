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
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val ModelStackTravel = CubicBezierEasing(0.16f, 0.00f, 0.10f, 1.00f)
private val ModelStackVisual = CubicBezierEasing(0.18f, 0.00f, 0.14f, 1.00f)

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

private val AutoModelTheme = ModelCardPrismTheme(Color(0xFF8FFFF8), Color.White, Color(0xFF07142D), Color(0xFF72FFF5), Color(0xFFFF82E4), Color(0xFFA0B4FF), Color(0xFFFFE898), 0.00f)
private val GeminiModelTheme = ModelCardPrismTheme(Color(0xFF4EA0FF), Color(0xFFF0FBFF), Color(0xFF061A42), Color(0xFF4BFAFF), Color(0xFF6C82FF), Color(0xFF2480FF), Color(0xFFD3F5FF), 1.00f)
private val KimiModelTheme = ModelCardPrismTheme(Color(0xFFEB72FF), Color(0xFFFFEBFF), Color(0xFF2A0C45), Color(0xFFFF8DEB), Color(0xFFC878FF), Color(0xFFFF66D2), Color(0xFFFFC4F3), 1.00f)
private val MistralModelTheme = ModelCardPrismTheme(Color(0xFFFFCC5C), Color(0xFFFFF6D2), Color(0xFF3B2205), Color(0xFFFFE68D), Color(0xFFFFA94E), Color(0xFFFFDA72), Color(0xFFFFEA83), 1.00f)
private val WorkersModelTheme = ModelCardPrismTheme(Color(0xFFFF665F), Color(0xFFFFE1DA), Color(0xFF3A0D0A), Color(0xFFFF8A70), Color(0xFFFF5697), Color(0xFFFF7C52), Color(0xFFFFBE80), 1.00f)

private fun ChatModel.modelCardPrismTheme(): ModelCardPrismTheme = when (this) {
    ChatModel.Auto -> AutoModelTheme
    ChatModel.Gemini -> GeminiModelTheme
    ChatModel.Kimi -> KimiModelTheme
    ChatModel.Mistral -> MistralModelTheme
    ChatModel.Workers -> WorkersModelTheme
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
        val currentIsSending by rememberUpdatedState(state.isSending)
        val currentExpanded by rememberUpdatedState(expanded)
        val currentOnToggleExpanded by rememberUpdatedState(onToggleExpanded)
        val currentOnSelected by rememberUpdatedState(onSelected)
        val gap = 10.dp
        val rowStep = 74.dp
        val collapsedHeight = 56.dp
        val expandedHeight = 64.dp
        val reservedGap = 8.dp
        val collapsedWidth = (maxWidth - reservedGap) * 0.642f
        val halfWidth = (maxWidth - gap) / 2f
        val selectedModel = state.selectedModel
        val behindModels = remember(selectedModel) { models.filter { it != selectedModel } }

        Box(Modifier.fillMaxSize()) {
            models.forEachIndexed { index, model ->
                val selected = model == selectedModel
                val theme = remember(model) { model.modelCardPrismTheme() }
                val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
                val scope = rememberCoroutineScope()
                val pressAnim = remember(model.id) { Animatable(0f) }
                val opticsAnim = remember(model.id) { Animatable(0f) }
                val visualAnim = remember(model.id) { Animatable(if (expanded) 1f else 0f) }
                val motionAnim = remember(model.id) { Animatable(if (expanded) 1f else 0f) }
                val capsuleAnim = remember(model.id) { Animatable(0f) }
                var cardSize by remember(model.id) { mutableStateOf(Size(1f, 1f)) }
                var center by remember(model.id) { mutableStateOf(Offset(0.50f, 0.42f)) }
                var seed by remember(model.id) { mutableStateOf(0.50f) }
                var direction by remember(model.id) { mutableStateOf(1f) }
                var band by remember(model.id) { mutableStateOf(0) }
                var strength by remember(model.id) { mutableStateOf(1f) }

                val geometry = remember(density.density, maxWidth, selectedModel, index, stackRank) {
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
                    val distanceWeight = (travelTotal / with(density) { 220.dp.toPx() }).coerceIn(0.35f, 1f)
                    ModelCardGeometry(
                        collapsedXPx = collapsedXPx,
                        collapsedYPx = collapsedYPx,
                        expandedXPx = expandedXPx,
                        expandedYPx = expandedYPx,
                        horizontalMotion = horizontalMotion,
                        verticalMotion = verticalMotion,
                        overshoot = 0.040f + 0.020f * distanceWeight
                    )
                }

                val selectionProgress by animateFloatAsState(if (selected) 1f else 0f, tween(if (selected) 520 else 260, delayMillis = if (selected) 28 else 0, easing = FastOutSlowInEasing), label = "model-card-selection-material-${model.id}")
                val selectedPulse by animateFloatAsState(if (selected) 1.008f else 1f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow), label = "model-card-selected-${model.id}")

                LaunchedEffect(expanded, selected, model.id) {
                    val target = if (expanded) 1f else 0f
                    val reverseRank = (behindModels.size - stackRank).coerceAtLeast(0)
                    val motionDuration = if (expanded) (450 + stackRank * 32).coerceAtMost(570) else if (selected) 390 else 360 + reverseRank * 28
                    val visualDuration = if (expanded) (350 + stackRank * 24).coerceAtMost(450) else if (selected) 320 else 300 + reverseRank * 20
                    launch {
                        capsuleAnim.stop(); capsuleAnim.snapTo(0f)
                        capsuleAnim.animateTo(1f, tween(if (expanded) 60 else 58, easing = ModelPressPulse))
                        capsuleAnim.animateTo(0f, tween(if (expanded) 230 else 210, easing = FastOutSlowInEasing))
                    }
                    launch {
                        visualAnim.stop()
                        visualAnim.animateTo(target, tween(durationMillis = visualDuration, easing = ModelStackVisual))
                    }
                    motionAnim.stop()
                    motionAnim.animateTo(target, tween(durationMillis = motionDuration, easing = ModelStackTravel))
                }

                val pressValue = pressAnim.value.coerceIn(-0.16f, 1.12f)
                val positivePress = pressValue.coerceAtLeast(0f)
                val compression = modelSmooth((positivePress / 0.72f).coerceIn(0f, 1f))
                val rebound = modelSmooth((-pressValue / 0.11f).coerceIn(0f, 1f))
                val delayed = opticsAnim.value.coerceIn(0f, 1f)
                val selection = modelSmooth(selectionProgress.coerceIn(0f, 1f))
                val capsuleLaunch = modelSmooth(capsuleAnim.value.coerceIn(0f, 1f))
                val targetProgress = modelSmooth(visualAnim.value.coerceIn(0f, 1f))
                val stackReveal = 1f - targetProgress
                val rawMotion = motionAnim.value.coerceIn(0f, 1f)
                val motionPhase = if (expanded) rawMotion else 1f - rawMotion
                val pathProgress = modelDockingProgress(motionPhase, geometry.overshoot)
                val p = if (expanded) pathProgress else 1f - pathProgress
                val overshootAmount = if (expanded) (p - 1f).coerceAtLeast(0f) else (-p).coerceAtLeast(0f)
                val dockGlow = modelSmooth((overshootAmount / geometry.overshoot.coerceAtLeast(0.001f)).coerceIn(0f, 1f))
                val selectionBurst = if (selected) sin(selectionProgress.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f) else 0f
                val materialPress = maxOf(positivePress, delayed * 0.92f, rebound * 0.42f, capsuleLaunch * 0.42f, dockGlow * 0.08f, selectionBurst * 0.52f).coerceIn(0f, 1.16f)

                val width = modelLerpDp(collapsedWidth, halfWidth, targetProgress)
                val height = modelLerpDp(collapsedHeight, expandedHeight, targetProgress)
                val tx = modelLerpRawFloat(geometry.collapsedXPx, geometry.expandedXPx, p)
                val ty = modelLerpRawFloat(geometry.collapsedYPx, geometry.expandedYPx, p)
                val alpha = modelLerpFloat(if (selected) 1f else 0.60f, 1f, targetProgress)
                val releaseStretchX = capsuleLaunch * (0.044f * geometry.horizontalMotion - 0.012f * geometry.verticalMotion)
                val releaseStretchY = capsuleLaunch * (0.016f * geometry.verticalMotion - 0.024f * geometry.horizontalMotion)
                val dockScaleX = dockGlow * (0.0016f * geometry.horizontalMotion - 0.0008f * geometry.verticalMotion)
                val dockScaleY = dockGlow * (0.0014f * geometry.verticalMotion - 0.0010f * geometry.horizontalMotion)
                val scaleX = selectedPulse * (1f + compression * 0.055f - rebound * 0.010f + releaseStretchX + dockScaleX)
                val scaleY = selectedPulse * (1f - compression * 0.064f + rebound * 0.028f + releaseStretchY + dockScaleY)
                val sinkY = compression * 4.10f - rebound * 1.05f + capsuleLaunch * 0.82f + dockGlow * 0.006f
                val energy = if (selected) modelLerpFloat(0.62f * style.unselectedEnergy.coerceIn(0f, 5f), 1.16f, selection) else 0.68f * style.unselectedEnergy.coerceIn(0f, 5f)

                Box(
                    modifier = Modifier.width(width).height(height).zIndex(if (selected) 50f else 30f - index)
                        .onSizeChanged { cardSize = Size(it.width.coerceAtLeast(1).toFloat(), it.height.coerceAtLeast(1).toFloat()) }
                        .graphicsLayer { transformOrigin = TransformOrigin(center.x, center.y); translationX = tx; translationY = ty + sinkY; this.scaleX = scaleX; this.scaleY = scaleY; this.alpha = alpha; shadowElevation = compression * 0.62f + capsuleLaunch * 0.26f + dockGlow * 0.01f }
                        .drawModelCardGlassSurface(style, theme, selected, selection, energy, materialPress, delayed, stackReveal, center, seed, direction, band, strength)
                        .pointerInput(model.id) {
                            awaitEachGesture {
                                fun updateCenter(position: Offset) { center = Offset((position.x / cardSize.width.coerceAtLeast(1f)).coerceIn(0f, 1f), (position.y / cardSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)) }
                                val down = awaitFirstDown(requireUnconsumed = false)
                                updateCenter(down.position)
                                if (currentIsSending) return@awaitEachGesture
                                seed = Random.nextFloat(); direction = if (Random.nextBoolean()) 1f else -1f; band = Random.nextInt(0, 4); strength = 1.06f + Random.nextFloat() * 0.62f
                                scope.launch { pressAnim.stop(); if (pressAnim.value < 0.34f) pressAnim.snapTo(0.34f); pressAnim.animateTo(0.88f, tween(82, easing = ModelPressPulse)); pressAnim.animateTo(0.78f, tween(160, easing = ModelPressSink)); pressAnim.animateTo(0.74f, tween(220, easing = FastOutSlowInEasing)) }
                                scope.launch { opticsAnim.stop(); opticsAnim.animateTo(0.46f, tween(104, easing = ModelPressPreload)); opticsAnim.animateTo(1.05f, tween(260, easing = ModelPressSink)); opticsAnim.animateTo(1.08f, tween(360, easing = FastOutSlowInEasing)) }
                                var released = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (tracked != null) { updateCenter(tracked.position); if (!tracked.pressed) { released = true; break } }
                                    if (event.changes.none { it.pressed }) { released = true; break }
                                }
                                if (released) { if (currentExpanded) currentOnSelected(model) else currentOnToggleExpanded() }
                                scope.launch { opticsAnim.stop(); if (released && opticsAnim.value < 0.52f) opticsAnim.animateTo(0.64f, tween(74, easing = ModelPressPulse)); opticsAnim.animateTo(0f, tween(if (released) 620 else 340, easing = FastOutSlowInEasing)) }
                                scope.launch { pressAnim.stop(); if (released) { if (pressAnim.value.coerceIn(0f, 1.16f) < 0.58f) { pressAnim.animateTo(0.68f, tween(62, easing = ModelPressPulse)); pressAnim.animateTo(-0.086f, tween(142, easing = ModelPressRelease)) } else { pressAnim.animateTo(-0.092f, tween(178, easing = ModelPressRelease)) }; pressAnim.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow)) } else { pressAnim.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) } }
                            }
                        }
                ) { UnifiedModelCardContent(model, selected, stackRank, selection, targetProgress, stackReveal, materialPress, theme) }
            }
        }
    }
}

@Composable
private fun UnifiedModelCardContent(model: ChatModel, selected: Boolean, stackRank: Int, selection: Float, expansionProgress: Float, stackReveal: Float, materialPress: Float, theme: ModelCardPrismTheme) {
    val density = LocalDensity.current
    val fullTextAlpha = if (selected) 1f else expansionProgress.coerceIn(0f, 1f) * 0.98f
    val stackLabelAlpha = if (selected) 0f else modelSmooth(stackReveal.coerceIn(0f, 1f)) * 0.72f
    val labelShiftX = with(density) { (stackRank * 24).dp.toPx() }
    val labelShiftY = with(density) { ((stackRank - 1) * 4).dp.toPx() }
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        ModelStatusDot(selection, expansionProgress, materialPress, theme)
        Box(Modifier.weight(1f).fillMaxSize()) {
            Column(Modifier.align(Alignment.CenterStart).graphicsLayer { alpha = fullTextAlpha }, verticalArrangement = Arrangement.Center) {
                Text(model.shortLabel, color = Color.White.copy(alpha = modelLerpFloat(0.94f, 1f, selection)), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.id, color = Color.White.copy(alpha = modelLerpFloat(0.58f, 0.78f, selection)), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selected) Text(model.shortLabel, modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer { alpha = stackLabelAlpha; translationX = -labelShiftX; translationY = labelShiftY }, color = theme.bright.copy(alpha = 0.94f), fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun ModelStatusDot(selection: Float, expansionProgress: Float, materialPress: Float, theme: ModelCardPrismTheme) {
    Box(modifier = Modifier.size(24.dp).drawWithCache {
        val c = Offset(size.width / 2f, size.height / 2f)
        val selected = selection.coerceIn(0f, 1f)
        val press = materialPress.coerceIn(0f, 1f)
        val autoRainbow = theme.themeWeight <= 0.01f
        val glow = (0.52f + 0.34f * selected + 0.22f * expansionProgress + 0.18f * press).coerceIn(0f, 1.18f)
        val coreAlpha = (0.86f * (1f - selected) + 1.00f * selected + 0.12f * press).coerceIn(0f, 1f)
        val outerHalo = Brush.radialGradient(listOf(theme.bright.copy(alpha = 0.24f * glow), theme.main.copy(alpha = 0.30f * glow), theme.prismB.copy(alpha = 0.16f * glow), Color.Transparent), c, size.minDimension * 0.74f)
        val innerHalo = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f * glow), theme.bright.copy(alpha = 0.35f * glow), theme.main.copy(alpha = 0.28f * glow), Color.Transparent), c, size.minDimension * 0.45f)
        val pressGlow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f * press), theme.main.copy(alpha = 0.44f * press), theme.prismB.copy(alpha = 0.26f * press), theme.gold.copy(alpha = 0.18f * press), Color.Transparent), c, size.minDimension * (0.88f + 0.22f * press))
        onDrawBehind {
            drawCircle(brush = outerHalo, radius = size.minDimension * 0.54f, center = c, blendMode = BlendMode.Screen)
            drawCircle(brush = innerHalo, radius = size.minDimension * 0.35f, center = c, blendMode = BlendMode.Screen)
            if (press > 0.001f) drawCircle(brush = pressGlow, radius = size.minDimension * 0.72f, center = c, blendMode = BlendMode.Screen)
            val rayAlpha = (0.16f + 0.22f * selected + 0.12f * press).coerceIn(0f, 0.46f)
            drawLine(Color.White.copy(alpha = rayAlpha), Offset(c.x - size.width * 0.31f, c.y), Offset(c.x + size.width * 0.31f, c.y), strokeWidth = 0.72.dp.toPx())
            drawLine(Color.White.copy(alpha = rayAlpha * 0.82f), Offset(c.x, c.y - size.height * 0.31f), Offset(c.x, c.y + size.height * 0.31f), strokeWidth = 0.72.dp.toPx())
            drawCircle(theme.prismA.copy(alpha = if (autoRainbow) 0.42f + 0.12f * selected + 0.08f * press else 0.24f + 0.18f * selected + 0.10f * press), radius = size.minDimension * (0.170f + 0.014f * press), center = c, blendMode = BlendMode.Screen)
            drawCircle(theme.gold.copy(alpha = if (autoRainbow) 0.36f + 0.10f * selected + 0.06f * press else 0.16f + 0.10f * selected + 0.08f * press), radius = size.minDimension * (0.215f + 0.012f * press), center = c, blendMode = BlendMode.Screen)
            drawCircle(theme.prismB.copy(alpha = if (autoRainbow) 0.32f + 0.10f * selected + 0.06f * press else 0.14f + 0.10f * selected + 0.06f * press), radius = size.minDimension * (0.255f + 0.012f * press), center = c, blendMode = BlendMode.Screen)
            drawCircle(Color.White.copy(alpha = coreAlpha), radius = size.minDimension * (0.105f + 0.016f * press + 0.010f * selected), center = c, blendMode = BlendMode.Screen)
            drawCircle(Color.White.copy(alpha = (0.72f + 0.20f * selected).coerceIn(0f, 1f)), radius = size.minDimension * 0.044f, center = Offset(c.x - size.width * 0.030f, c.y - size.height * 0.030f), blendMode = BlendMode.Plus)
        }
    }, contentAlignment = Alignment.Center) {}
}

private fun Modifier.drawModelCardGlassSurface(style: ModelCardGlassStyle, theme: ModelCardPrismTheme, selected: Boolean, selection: Float, energy: Float, materialPress: Float, delayed: Float, stackReveal: Float, center: Offset, seed: Float, direction: Float, band: Int, strength: Float): Modifier = drawWithCache {
    fun s(value: Float, max: Float = 8f) = value.coerceIn(0f, max)
    val radius = minOf(size.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
    val corner = CornerRadius(radius, radius)
    val press = materialPress.coerceIn(0f, 1.12f)
    val compression = modelSmooth((press / 0.72f).coerceIn(0f, 1f))
    val optics = modelSmooth((maxOf(delayed, press * 0.72f) / 1.04f).coerceIn(0f, 1f))
    val selectedGlow = selection.coerceIn(0f, 1f)
    val collapsedReveal = modelSmooth(stackReveal.coerceIn(0f, 1f))
    val stackBoost = collapsedReveal * if (selected) 0.52f else 1.24f
    val themeBoost = (theme.themeWeight * (0.78f + selectedGlow * 0.88f + stackBoost * 0.46f)).coerceIn(0f, 1.90f)
    val body = energy * s(style.bodyAlpha, 6f) * 1.08f
    val mist = energy * s(style.innerMist, 6f) * 1.18f
    val rimPower = energy * 1.12f
    val materialCenter = Offset(size.width * (0.52f + (center.x - 0.5f) * compression * 0.068f), size.height * (0.50f + (center.y - 0.5f) * compression * 0.104f))
    val bodyVeil = Brush.verticalGradient(listOf(Color.White.copy(alpha = (0.034f + selectedGlow * 0.014f) * body), theme.main.copy(alpha = (0.038f + selectedGlow * 0.030f + optics * 0.014f) * body), theme.deep.copy(alpha = 0.038f * body), Color(0xFF000713).copy(alpha = 0.118f * body)), 0f, size.height)
    val mistBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.012f * mist), theme.bright.copy(alpha = 0.024f * mist), theme.prismC.copy(alpha = 0.014f * mist), Color.Transparent, Color(0xFF000713).copy(alpha = 0.014f * mist)), Offset(size.width * 0.08f, 0f), Offset(size.width * 0.94f, size.height))
    val colorLift = Brush.linearGradient(listOf(Color.Transparent, theme.prismA.copy(alpha = (0.050f + 0.036f * selectedGlow + 0.018f * stackBoost).coerceIn(0f, 0.18f)), theme.main.copy(alpha = (0.062f + 0.050f * selectedGlow + 0.026f * themeBoost).coerceIn(0f, 0.22f)), theme.prismB.copy(alpha = (0.040f + 0.036f * selectedGlow).coerceIn(0f, 0.16f)), theme.gold.copy(alpha = (0.022f + 0.018f * selectedGlow).coerceIn(0f, 0.10f)), Color.Transparent), Offset(size.width * -0.10f, size.height * 0.02f), Offset(size.width * 1.06f, size.height * 0.98f))
    val topBloom = Brush.radialGradient(listOf(Color.White.copy(alpha = (0.075f + 0.040f * selectedGlow + 0.030f * optics).coerceIn(0f, 0.18f)), theme.bright.copy(alpha = (0.064f + 0.052f * selectedGlow + 0.030f * themeBoost).coerceIn(0f, 0.22f)), theme.main.copy(alpha = (0.026f + 0.040f * selectedGlow).coerceIn(0f, 0.16f)), Color.Transparent), Offset(size.width * 0.18f, size.height * 0.22f), maxOf(size.width, size.height) * 0.56f)
    val aura = Brush.linearGradient(listOf(theme.main.copy(alpha = (0.072f + 0.085f * themeBoost) * selectedGlow * s(style.selectedAura)), theme.bright.copy(alpha = 0.032f * selectedGlow * s(style.selectedAura)), theme.prismB.copy(alpha = 0.040f * selectedGlow * s(style.selectedAura)), theme.gold.copy(alpha = 0.020f * selectedGlow * s(style.selectedAura)), Color.Transparent), Offset(size.width * -0.14f, size.height * -0.20f), Offset(size.width * 1.08f, size.height * 1.10f))
    val pressureBend = Brush.radialGradient(listOf(Color.Transparent, theme.deep.copy(alpha = 0.082f * compression), Color(0xFF00040D).copy(alpha = 0.102f * compression), Color.Transparent), materialCenter, maxOf(size.width, size.height) * (0.66f + 0.08f * compression))
    val prismField = Brush.linearGradient(listOf(Color.Transparent, theme.prismA.copy(alpha = 0.060f * optics), theme.main.copy(alpha = (0.092f + 0.105f * themeBoost) * optics), theme.prismB.copy(alpha = 0.052f * optics), theme.gold.copy(alpha = 0.040f * optics), Color.Transparent), Offset(size.width * -0.16f, size.height * -0.12f), Offset(size.width * 1.14f, size.height * 1.10f))
    val outerRim = Brush.linearGradient(listOf(theme.bright.copy(alpha = 0.360f * rimPower * s(style.outerRim)), Color(0xFFF1FFFF).copy(alpha = 0.130f * rimPower * s(style.outerRim)), theme.main.copy(alpha = (0.240f + 0.210f * themeBoost) * rimPower * s(style.outerRim)), Color.Transparent, theme.deep.copy(alpha = 0.160f * rimPower * s(style.bottomShadow)), theme.prismC.copy(alpha = 0.115f * rimPower * s(style.outerRim))), Offset.Zero, Offset(size.width, size.height))
    val selectedRainbow = Brush.linearGradient(listOf(theme.main.copy(alpha = (0.840f + 0.520f * themeBoost) * s(style.selectedRainbowRim) * selectedGlow), theme.bright.copy(alpha = 0.260f * s(style.selectedRainbowRim) * selectedGlow), theme.prismA.copy(alpha = 0.230f * s(style.selectedRainbowRim) * selectedGlow), theme.prismB.copy(alpha = 0.215f * s(style.selectedRainbowRim) * selectedGlow), theme.prismC.copy(alpha = 0.260f * s(style.selectedRainbowRim) * selectedGlow), Color.Transparent), Offset(size.width * -0.08f, 0f), Offset(size.width * 1.02f, size.height * 0.78f))
    val stackRim = Brush.linearGradient(listOf(Color.Transparent, theme.prismC.copy(alpha = 0.070f * rimPower * stackBoost), theme.main.copy(alpha = (0.175f + 0.130f * themeBoost) * rimPower * stackBoost), theme.bright.copy(alpha = 0.420f * rimPower * stackBoost)), Offset(size.width * 0.64f, size.height * 0.24f), Offset(size.width, size.height))
    onDrawWithContent {
        if (selectedGlow > 0.001f) drawRoundRect(brush = aura, topLeft = Offset(-4.2.dp.toPx(), -4.2.dp.toPx()), size = Size(size.width + 8.4.dp.toPx(), size.height + 8.4.dp.toPx()), cornerRadius = CornerRadius(radius + 4.2.dp.toPx(), radius + 4.2.dp.toPx()), blendMode = BlendMode.Screen)
        drawRoundRect(mistBrush, size = size, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(bodyVeil, size = size, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(colorLift, size = size, cornerRadius = corner, blendMode = BlendMode.Screen)
        drawRoundRect(topBloom, size = size, cornerRadius = corner, blendMode = BlendMode.Screen)
        if (press > 0.001f) { drawRoundRect(pressureBend, size = size, cornerRadius = corner, blendMode = BlendMode.Multiply); drawRoundRect(prismField, size = size, cornerRadius = corner, blendMode = BlendMode.Screen) }
        drawContent()
        if (selectedGlow > 0.001f) drawRoundRect(brush = selectedRainbow, topLeft = Offset(0.52.dp.toPx(), 0.52.dp.toPx()), size = Size(size.width - 1.04.dp.toPx(), size.height - 1.04.dp.toPx()), cornerRadius = corner, style = Stroke(1.72.dp.toPx()), blendMode = BlendMode.Plus)
        drawRoundRect(brush = outerRim, topLeft = Offset(0.56.dp.toPx(), 0.56.dp.toPx()), size = Size(size.width - 1.12.dp.toPx(), size.height - 1.12.dp.toPx()), cornerRadius = corner, style = Stroke(1.12.dp.toPx()), blendMode = BlendMode.Screen)
        if (collapsedReveal > 0.001f) drawRoundRect(brush = stackRim, topLeft = Offset(0.56.dp.toPx(), 0.56.dp.toPx()), size = Size(size.width - 1.12.dp.toPx(), size.height - 1.12.dp.toPx()), cornerRadius = corner, style = Stroke(1.32.dp.toPx()), blendMode = BlendMode.Screen)
        if (press > 0.001f) {
            val flow = modelSmooth((maxOf(press, delayed) / 0.62f).coerceIn(0f, 1f))
            val shift = (seed - 0.5f) * 0.36f
            val sweepX = if (direction >= 0f) -0.24f + shift + flow * 1.42f else 1.24f + shift - flow * 1.42f
            val startY = when (band % 4) { 0 -> 0.02f; 1 -> 0.74f; 2 -> 0.10f; else -> 0.18f }
            val endY = when (band % 4) { 0 -> 0.26f; 1 -> 0.98f; 2 -> 0.92f; else -> 0.58f }
            val bandAlpha = modelSmooth((press / 0.50f).coerceIn(0f, 1f)) * strength.coerceIn(0.70f, 1.68f)
            val flowingRim = Brush.linearGradient(listOf(Color.Transparent, theme.main.copy(alpha = 0.540f * bandAlpha), theme.bright.copy(alpha = 0.400f * bandAlpha), theme.prismA.copy(alpha = 0.325f * bandAlpha), theme.prismB.copy(alpha = 0.300f * bandAlpha), theme.prismC.copy(alpha = 0.285f * bandAlpha), Color.Transparent), Offset(size.width * (sweepX - 0.30f), size.height * startY), Offset(size.width * (sweepX + 0.26f), size.height * endY))
            drawRoundRect(brush = flowingRim, topLeft = Offset(0.52.dp.toPx(), 0.52.dp.toPx()), size = Size(size.width - 1.04.dp.toPx(), size.height - 1.04.dp.toPx()), cornerRadius = corner, style = Stroke(1.52.dp.toPx()), blendMode = BlendMode.Plus)
        }
    }
}

private fun modelDockingProgress(phase: Float, overshoot: Float): Float {
    val t = phase.coerceIn(0f, 1f)
    val out = overshoot.coerceIn(0.030f, 0.070f)
    return when {
        t < 0.16f -> modelLerpRawFloat(0f, 0.34f, modelEaseOutCubic(t / 0.16f))
        t < 0.54f -> modelLerpRawFloat(0.34f, 0.88f, modelSmoother((t - 0.16f) / 0.38f))
        t < 0.74f -> modelLerpRawFloat(0.88f, 1f + out, modelEaseOutCubic((t - 0.54f) / 0.20f))
        t < 0.88f -> modelLerpRawFloat(1f + out, 1f + out * 0.34f, modelSmoother((t - 0.74f) / 0.14f))
        else -> modelLerpRawFloat(1f + out * 0.34f, 1f, modelSmoother((t - 0.88f) / 0.12f))
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
private fun modelLerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun modelLerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun modelLerpRawFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction
private fun modelSmooth(value: Float): Float { val x = value.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
