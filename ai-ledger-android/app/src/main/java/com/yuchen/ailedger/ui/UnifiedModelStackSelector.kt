package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
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
        val visuals = remember { mutableStateListOf<UnifiedModelCardVisual>() }
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

        Box(Modifier.fillMaxSize().drawUnifiedModelRimGlass(visuals, style)) {
            models.forEachIndexed { index, model ->
                val selected = model == selectedModel
                val stackRank = if (selected) 0 else behindModels.indexOf(model) + 1
                val column = index % 2
                val row = index / 2
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
                    animationSpec = tween(
                        durationMillis = if (selected) 660 else 310,
                        delayMillis = if (selected) 36 else 0,
                        easing = FastOutSlowInEasing
                    ),
                    label = "model-card-selection-light-${model.id}"
                )
                val selectedPulse by animateFloatAsState(
                    targetValue = if (selected) 1.010f else 1f,
                    animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow),
                    label = "model-card-selected-${model.id}"
                )

                val p = unifiedModelStackEase(targetProgress)
                val selectionRaw = selectionProgress.coerceIn(0f, 1f)
                val textLight = unifiedModelStackSmooth(selectionRaw)
                val bodyLight = unifiedModelStackPhase(selectionRaw, 0.00f, 0.30f)
                val edgeLight = unifiedModelStackPhase(selectionRaw, 0.06f, 0.54f)
                val glintLight = unifiedModelStackPhase(selectionRaw, 0.10f, 0.48f)
                val rainbowLight = unifiedModelStackPhase(selectionRaw, 0.22f, 0.84f)
                val auraLight = unifiedModelStackPhase(selectionRaw, 0.34f, 1.00f)
                val edgeIgnite = sin(selectionRaw.coerceIn(0f, 1f) * PI.toFloat()).coerceAtLeast(0f)

                val currentWidth = unifiedLerpDp(collapsedWidth, halfWidth, p)
                val currentHeight = unifiedLerpDp(collapsedHeight, expandedHeight, p)
                val expandedX = if (column == 1) halfWidth + gap else 0.dp
                val expandedY = rowStep * row.toFloat()
                val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
                val collapsedY = if (selected) 0.dp else (stackRank * 1.6f).dp
                val tx = unifiedLerpFloat(with(density) { collapsedX.toPx() }, with(density) { expandedX.toPx() }, p)
                val ty = unifiedLerpFloat(with(density) { collapsedY.toPx() }, with(density) { expandedY.toPx() }, p)
                val alpha = unifiedLerpFloat(if (selected) 1f else 0.52f, 1f, p)
                val wPx = with(density) { currentWidth.toPx() } * selectedPulse
                val hPx = with(density) { currentHeight.toPx() } * selectedPulse
                val unselectedEnergy = 0.50f * style.unselectedEnergy.coerceIn(0f, 5f)
                val energy = if (selected) unifiedLerpFloat(unselectedEnergy, 1f, edgeLight) else unselectedEnergy

                visuals.add(
                    UnifiedModelCardVisual(
                        left = tx,
                        top = ty,
                        width = wPx,
                        height = hPx,
                        alpha = alpha,
                        selected = selected,
                        text = textLight,
                        body = bodyLight,
                        edge = edgeLight,
                        rainbow = rainbowLight,
                        aura = auraLight,
                        glint = glintLight,
                        ignite = edgeIgnite,
                        stackEnergy = energy,
                        lens = 0.20f * edgeLight,
                        press = 0f
                    )
                )

                Box(
                    modifier = Modifier
                        .width(currentWidth)
                        .height(currentHeight)
                        .zIndex(if (selected) 50f else 30f - index)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = tx
                            translationY = ty
                            scaleX = selectedPulse
                            scaleY = selectedPulse
                            this.alpha = alpha
                            shadowElevation = 0f
                        }
                        .clickable(
                            interactionSource = remember(model.id) { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (state.isSending) return@clickable
                            if (expanded) onSelected(model) else onToggleExpanded()
                        }
                ) {
                    UnifiedModelCardContent(model, textLight, targetProgress, style.dotGlow)
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
    val text: Float,
    val body: Float,
    val edge: Float,
    val rainbow: Float,
    val aura: Float,
    val glint: Float,
    val ignite: Float,
    val stackEnergy: Float,
    val lens: Float,
    val press: Float
)

@Composable
private fun UnifiedModelCardContent(model: ChatModel, selection: Float, expansionProgress: Float, dotGlow: Float) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ModelStatusDot(selection = selection, expansionProgress = expansionProgress, dotGlow = dotGlow)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                model.shortLabel,
                color = Color.White.copy(alpha = unifiedLerpFloat(0.88f, 0.985f, selection)),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                model.id,
                color = Color.White.copy(alpha = unifiedLerpFloat(0.46f, 0.62f, selection)),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModelStatusDot(selection: Float, expansionProgress: Float, dotGlow: Float) {
    val glow = dotGlow.coerceIn(0f, 6f)
    val selectedLight = selection.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .size(20.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val selectedGlow = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f * glow * selectedLight),
                        Color(0xFF77FFF0).copy(alpha = 0.24f * glow * selectedLight),
                        Color(0xFFFF7BDA).copy(alpha = 0.12f * glow * selectedLight),
                        Color(0xFFFFE48A).copy(alpha = 0.08f * glow * selectedLight),
                        Color.Transparent
                    ), center = center, radius = size.minDimension * 0.70f
                )
                val idleGlow = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = (0.070f + 0.045f * expansionProgress) * glow * (1f - selectedLight * 0.45f)), Color.Transparent),
                    center = center, radius = size.minDimension * 0.48f
                )
                onDrawBehind {
                    drawCircle(idleGlow, radius = size.minDimension * 0.40f, center = center, blendMode = BlendMode.Screen)
                    if (selectedLight > 0.001f) drawCircle(selectedGlow, radius = size.minDimension * 0.52f, center = center, blendMode = BlendMode.Screen)
                    drawCircle(Color.White.copy(alpha = 0.74f * (1f - selectedLight)), radius = size.minDimension * 0.17f, center = center)
                    drawCircle(Color(0xFF8DF9EA).copy(alpha = selectedLight), radius = size.minDimension * (0.17f + 0.055f * selectedLight), center = center)
                }
            },
        contentAlignment = Alignment.Center
    ) {}
}

private fun Modifier.drawUnifiedModelRimGlass(visuals: List<UnifiedModelCardVisual>, style: ModelCardGlassStyle): Modifier = drawWithCache {
    fun s(value: Float, max: Float = 8f) = value.coerceIn(0f, max)
    onDrawWithContent {
        fun drawBody(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            val energy = v.stackEnergy.coerceIn(0f, 8f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val bodyLight = v.body.coerceIn(0f, 1f)
            val lens = unifiedModelStackSmooth(v.lens.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val radiusBase = minOf(v.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
            val corner = CornerRadius(radiusBase, radiusBase)
            val bodyAlpha = energy * alpha * s(style.bodyAlpha, 6f)
            val mistAlpha = energy * alpha * s(style.innerMist, 6f)
            val bodySize = Size(v.width, v.height)
            val selectedAura = s(style.selectedAura, 8f)
            val auraRamp = v.aura.coerceIn(0f, 1f)
            val selectedRainbowAura = Brush.linearGradient(
                listOf(
                    Color(0xFF74FFF1).copy(alpha = 0.085f * alpha * selectedAura * auraRamp),
                    Color(0xFFFF7BDA).copy(alpha = 0.058f * alpha * selectedAura * auraRamp),
                    Color(0xFFFFE58A).copy(alpha = 0.042f * alpha * selectedAura * auraRamp),
                    Color(0xFF8EA2FF).copy(alpha = 0.054f * alpha * selectedAura * auraRamp),
                    Color.Transparent
                ), Offset(v.width * -0.10f, v.height * -0.18f), Offset(v.width * 0.96f, v.height * 1.02f)
            )
            val innerMist = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = (0.010f + bodyLight * 0.006f) * mistAlpha),
                    Color(0xFFDFFBFF).copy(alpha = (0.018f + bodyLight * 0.010f) * mistAlpha),
                    Color(0xFF9FB6FF).copy(alpha = 0.006f * mistAlpha),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = 0.014f * mistAlpha)
                ), Offset(v.width * 0.08f, 0f), Offset(v.width * 0.94f, v.height)
            )
            val bodyVeil = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = (0.052f + bodyLight * 0.016f + lens * 0.012f) * bodyAlpha),
                    Color(0xFFB8F7FF).copy(alpha = (0.020f + bodyLight * 0.010f) * bodyAlpha),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = 0.115f * bodyAlpha)
                ), 0f, v.height
            )
            val surfaceClear = Brush.radialGradient(
                listOf(Color.Transparent, Color(0xFF031026).copy(alpha = 0.024f * bodyAlpha), Color(0xFF00040C).copy(alpha = 0.056f * bodyAlpha)),
                center = Offset(v.width * 0.50f, v.height * 0.58f), radius = maxSide * 0.78f
            )
            withTransform({ translate(v.left, v.top) }) {
                if (auraRamp > 0.001f && selectedAura > 0.001f) {
                    drawRoundRect(selectedRainbowAura, topLeft = Offset(-3.2.dp.toPx(), -3.2.dp.toPx()), size = Size(v.width + 6.4.dp.toPx(), v.height + 6.4.dp.toPx()), cornerRadius = CornerRadius(radiusBase + 3.2.dp.toPx(), radiusBase + 3.2.dp.toPx()), blendMode = BlendMode.Screen)
                }
                if (mistAlpha > 0.001f) drawRoundRect(innerMist, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(bodyVeil, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Screen)
                drawRoundRect(surfaceClear, size = bodySize, cornerRadius = corner, blendMode = BlendMode.Multiply)
            }
        }

        fun drawRim(v: UnifiedModelCardVisual) {
            val alpha = v.alpha.coerceIn(0f, 1f)
            val energy = v.stackEnergy.coerceIn(0f, 8f)
            if (alpha <= 0.01f || v.width <= 1f || v.height <= 1f) return
            val edgeLight = v.edge.coerceIn(0f, 1f)
            val rainbowLight = v.rainbow.coerceIn(0f, 1f)
            val glintLight = v.glint.coerceIn(0f, 1f)
            val ignite = v.ignite.coerceIn(0f, 1f)
            val lens = unifiedModelStackSmooth(v.lens.coerceIn(0f, 1f))
            val maxSide = maxOf(v.width, v.height)
            val radiusBase = minOf(v.height * 0.42f, 30.dp.toPx()) * style.radiusScale.coerceIn(0.20f, 3f)
            val corner = CornerRadius(radiusBase, radiusBase)
            val rimInset = 0.62.dp.toPx()
            val innerInset = 1.55.dp.toPx()
            val rimSize = Size((v.width - rimInset * 2f).coerceAtLeast(1f), (v.height - rimInset * 2f).coerceAtLeast(1f))
            val innerSize = Size((v.width - innerInset * 2f).coerceAtLeast(1f), (v.height - innerInset * 2f).coerceAtLeast(1f))
            val rimPower = energy * alpha
            val selectedRim = (0.050f + edgeLight * 0.160f + lens * 0.020f) * rimPower
            val outer = s(style.outerRim, 8f)
            val top = s(style.topHairline, 8f) * (1f + edgeLight * 0.14f + ignite * 0.10f)
            val inner = s(style.innerDepth, 8f)
            val bottom = s(style.bottomShadow, 8f)
            val rainbow = s(style.selectedRainbowRim, 8f)
            val halo = s(style.selectedOuterHalo, 8f)
            val glint = s(style.edgeGlint, 10f)
            val glintRamp = (0.20f + glintLight * 0.92f + ignite * 0.18f).coerceIn(0f, 1.22f)

            val outerRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = (0.320f * rimPower + selectedRim * 0.18f) * outer),
                    Color(0xFFF1FFFF).copy(alpha = 0.150f * rimPower * outer),
                    Color(0xFFB7FFF7).copy(alpha = 0.115f * rimPower * outer),
                    Color.White.copy(alpha = 0.060f * rimPower * outer),
                    Color.Transparent,
                    Color(0xFF010817).copy(alpha = 0.225f * rimPower * bottom),
                    Color.White.copy(alpha = 0.062f * rimPower * outer)
                ), Offset.Zero, Offset(v.width, v.height)
            )
            val selectedRainbowRim = Brush.linearGradient(
                listOf(
                    Color(0xFF77FFF0).copy(alpha = 0.64f * alpha * rainbow * rainbowLight),
                    Color.White.copy(alpha = 0.42f * alpha * rainbow * rainbowLight),
                    Color(0xFFFF7BDA).copy(alpha = 0.36f * alpha * rainbow * rainbowLight),
                    Color(0xFFFFE58A).copy(alpha = 0.20f * alpha * rainbow * rainbowLight),
                    Color(0xFF8EA2FF).copy(alpha = 0.22f * alpha * rainbow * rainbowLight),
                    Color.Transparent
                ), Offset(v.width * -0.06f, v.height * -0.02f), Offset(v.width * 0.98f, v.height * 0.72f)
            )
            val selectedOuterRainbowHalo = Brush.linearGradient(
                listOf(
                    Color(0xFF77FFF0).copy(alpha = 0.115f * alpha * halo * rainbowLight),
                    Color(0xFFFF7BDA).copy(alpha = 0.074f * alpha * halo * rainbowLight),
                    Color(0xFFFFE58A).copy(alpha = 0.044f * alpha * halo * rainbowLight),
                    Color.Transparent,
                    Color(0xFF8EA2FF).copy(alpha = 0.052f * alpha * halo * rainbowLight)
                ), Offset(v.width * -0.10f, v.height * -0.20f), Offset(v.width * 1.06f, v.height * 1.10f)
            )
            val topHairline = Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.020f * rimPower * top),
                    Color.White.copy(alpha = (0.470f * rimPower + selectedRim * 0.34f) * top),
                    Color(0xFFFFF3C8).copy(alpha = (0.125f * rimPower + selectedRim * 0.12f) * top),
                    Color(0xFFE9FFFF).copy(alpha = (0.210f * rimPower + selectedRim * 0.30f) * top),
                    Color.White.copy(alpha = 0.080f * rimPower * top),
                    Color.Transparent
                ), 0f, v.width
            )
            val innerDepth = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.072f * rimPower * inner),
                    Color.Transparent,
                    Color(0xFF000713).copy(alpha = 0.235f * rimPower * inner),
                    Color(0xFF89FFF3).copy(alpha = (0.044f * rimPower + selectedRim * 0.16f) * inner)
                ), Offset(v.width * 0.08f, 0f), Offset(v.width * 0.92f, v.height)
            )
            val bottomWeight = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF00040C).copy(alpha = 0.300f * rimPower * bottom)), v.height * 0.42f, v.height)
            val centerX = v.width * 0.035f * style.edgeGlintCenterX.coerceIn(-3f, 5f)
            val centerY = -v.height * 0.085f * style.edgeGlintCenterY.coerceIn(-3f, 5f)
            val cornerEdgeGlint = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = (0.680f * rimPower + selectedRim * 0.28f) * glint * glintRamp),
                    Color(0xFFEFFFFF).copy(alpha = (0.245f * rimPower + selectedRim * 0.16f) * glint * glintRamp),
                    Color(0xFF9DFFF4).copy(alpha = (0.145f * rimPower + selectedRim * 0.16f) * glint * glintRamp),
                    Color.Transparent
                ), center = Offset(centerX, centerY), radius = maxSide * 0.082f * style.edgeGlintRadius.coerceIn(0.05f, 5f)
            )
            withTransform({ translate(v.left, v.top) }) {
                if (rainbowLight > 0.001f) {
                    if (halo > 0.001f) drawRoundRect(selectedOuterRainbowHalo, topLeft = Offset(-1.55.dp.toPx(), -1.55.dp.toPx()), size = Size(v.width + 3.10.dp.toPx(), v.height + 3.10.dp.toPx()), cornerRadius = CornerRadius(radiusBase + 1.55.dp.toPx(), radiusBase + 1.55.dp.toPx()), style = Stroke(2.15.dp.toPx()), blendMode = BlendMode.Screen)
                    if (rainbow > 0.001f) drawRoundRect(selectedRainbowRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(1.16.dp.toPx()), blendMode = BlendMode.Plus)
                }
                drawRoundRect(outerRim, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.96.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(topHairline, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.82.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(innerDepth, topLeft = Offset(innerInset, innerInset), size = innerSize, cornerRadius = corner, style = Stroke(0.54.dp.toPx()), blendMode = BlendMode.Screen)
                drawRoundRect(bottomWeight, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(0.92.dp.toPx()), blendMode = BlendMode.Multiply)
                drawRoundRect(cornerEdgeGlint, topLeft = Offset(rimInset, rimInset), size = rimSize, cornerRadius = corner, style = Stroke(1.22.dp.toPx()), blendMode = BlendMode.Screen)
            }
        }

        visuals.forEach { drawBody(it) }
        drawContent()
        visuals.forEach { drawRim(it) }
    }
}

private fun unifiedLerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun unifiedLerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)
private fun unifiedModelStackSmooth(value: Float): Float { val x = value.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
private fun unifiedModelStackPhase(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return unifiedModelStackSmooth(((value - start) / (end - start)).coerceIn(0f, 1f))
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
