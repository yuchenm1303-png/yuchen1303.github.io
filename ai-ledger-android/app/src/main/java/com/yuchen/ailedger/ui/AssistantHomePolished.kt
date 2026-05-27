package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    var modelExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 68.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssistantEntrance(delayMs = 0, initialOffsetY = -10, initialScale = 0.98f) {
            AssistantHeroV2(state, onOpenTools, onOpenSettings)
        }
        AssistantEntrance(delayMs = 46, initialOffsetY = 16, initialScale = 0.965f) {
            ModelAndNetworkPanel(
                state = state,
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it },
                onModelSelected = onModelSelected,
                onToggleOnline = onToggleOnline
            )
        }
        AssistantEntrance(delayMs = 92, modifier = Modifier.weight(1f), initialOffsetY = 30, initialScale = 0.955f) {
            ChatPanelV2(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = modelExpanded) { modelExpanded = false },
                onDraftCommand = onDraftCommand,
                onPickImage = onPickImage
            )
        }
        AssistantEntrance(delayMs = 138, initialOffsetY = 18, initialScale = 0.965f) {
            Box(Modifier.clickable(enabled = modelExpanded) { modelExpanded = false }) {
                ComposerBarV2(state, onComposerChange, onSend, onPickImage)
            }
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
private fun AssistantHeroV2(
    state: AssistantUiState,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
private fun ModelAndNetworkPanel(
    state: AssistantUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModelSelected: (ChatModel) -> Unit,
    onToggleOnline: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ModelFolderSelector(
            state = state,
            expanded = expanded,
            modifier = Modifier.weight(1.35f),
            onToggle = { if (!state.isSending) onExpandedChange(!expanded) },
            onSelected = { model -> if (!state.isSending) onModelSelected(model) }
        )
        NetworkChipV2(
            state = state,
            modifier = Modifier.weight(0.82f).zIndex(2f),
            onClick = { if (!state.isSending) onToggleOnline() }
        )
    }
}

@Composable
private fun ModelFolderSelector(
    state: AssistantUiState,
    expanded: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    onSelected: (ChatModel) -> Unit
) {
    val models = ChatModel.entries
    val rows = ((models.size + 1) / 2).coerceAtLeast(1)
    val targetHeight = if (expanded) (rows * 66 + 4).toFloat() else 58f
    val heightDp by animateFloatAsState(
        targetValue = targetHeight,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "model-folder-height"
    )
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "model-folder-progress"
    )
    val loop = rememberInfiniteTransition(label = "model-folder-loop")
    val phase by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart),
        label = "model-folder-phase"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(heightDp.dp)
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        val gap = 8.dp
        val cardW = (maxWidth - gap) / 2f
        val cardH = 58.dp
        val stackScale = 0.54f
        val baseX = maxWidth - cardW - 4.dp
        val baseY = 0.dp
        val selectedIndex = models.indexOf(state.selectedModel).coerceAtLeast(0)

        models.forEachIndexed { index, model ->
            val frontOffset = if (index == selectedIndex) 0 else index + 1
            val stackX = baseX - (frontOffset * 6).dp
            val stackY = baseY + (frontOffset * 3).dp
            val stackRot = -7f + frontOffset * 2.4f
            val col = index % 2
            val row = index / 2
            val gridX = (cardW + gap) * col
            val gridY = (row * 66).dp
            val delay = index * 0.045f
            val local = ((progress - delay) / (1f - delay * 0.72f)).coerceIn(0f, 1f)
            val smooth = local * local * (3f - 2f * local)
            val arc = sin((smooth * PI).toFloat()) * (-12f - index * 1.2f)
            val selectedPulse = if (model == state.selectedModel) ((sin(phase * 2f * PI.toFloat()) + 1f) * 0.5f) else 0f
            val z = if (expanded) smooth + index * 0.02f else (100 - frontOffset).toFloat()
            val labelAlpha = (0.20f + smooth * 0.80f).coerceIn(0.20f, 1f)

            Box(
                modifier = Modifier
                    .size(width = cardW, height = cardH)
                    .zIndex(z)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.82f, 0.16f)
                        val stackPxX = stackX.toPx()
                        val stackPxY = stackY.toPx()
                        val gridPxX = gridX.toPx()
                        val gridPxY = gridY.toPx()
                        translationX = stackPxX + (gridPxX - stackPxX) * smooth
                        translationY = stackPxY + (gridPxY - stackPxY) * smooth + arc
                        scaleX = stackScale + (1f - stackScale) * smooth + selectedPulse * 0.010f
                        scaleY = stackScale + (1f - stackScale) * smooth + selectedPulse * 0.008f
                        rotationZ = stackRot * (1f - smooth)
                        alpha = 0.62f + 0.38f * smooth
                    }
            ) {
                ModelMorphCard(
                    model = model,
                    selected = model == state.selectedModel,
                    expanded = expanded,
                    progress = progress,
                    labelAlpha = labelAlpha,
                    pulse = selectedPulse,
                    phase = phase,
                    motionIntensity = state.motionIntensity,
                    onClick = { onSelected(model) }
                )
            }
        }
    }
}

@Composable
private fun ModelMorphCard(
    model: ChatModel,
    selected: Boolean,
    expanded: Boolean,
    progress: Float,
    labelAlpha: Float,
    pulse: Float,
    phase: Float,
    motionIntensity: Float,
    onClick: () -> Unit
) {
    val radius = if (expanded || progress > 0.45f) 22 else 18
    PressableGlass(
        quality = RenderQuality.Balanced,
        glassIntensity = if (selected) 1.08f else 0.92f,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = Modifier.fillMaxSize(),
        role = if (selected) GlassRole.Floating else GlassRole.Chip,
        onClick = onClick
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .modelMorphCardOptics(selected, pulse, phase, motionIntensity, radius)
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = (if (selected) Color(0xFF8DF9EA) else Color.White).copy(alpha = if (selected) 0.92f else 0.35f),
                            radius = size.minDimension * if (selected) (0.34f + pulse * 0.08f) else 0.25f,
                            center = Offset(size.width / 2f, size.height / 2f),
                            blendMode = BlendMode.Screen
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = model.shortLabel,
                        color = Color.White.copy(alpha = (if (selected) 0.98f else 0.78f) * labelAlpha),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.id,
                        color = Color.White.copy(alpha = (if (selected) 0.50f else 0.34f) * labelAlpha),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun Modifier.modelMorphCardOptics(
    selected: Boolean,
    pulse: Float,
    phase: Float,
    motionIntensity: Float,
    radiusDp: Int
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1.25f)
    val cycle = phase * 2f * PI.toFloat()
    val corner = CornerRadius(radiusDp.dp.toPx(), radiusDp.dp.toPx())
    val power = (if (selected) 0.95f + pulse * 0.50f else 0.50f) * motion
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF8DFFF3).copy(alpha = 0.075f * power),
                Color(0xFF8C9BFF).copy(alpha = 0.050f * power),
                Color(0xFFFF78E2).copy(alpha = 0.032f * power),
                Color.Transparent
            ),
            center = Offset(w * (0.76f + 0.08f * sin(cycle)), h * (0.32f + 0.10f * cos(cycle * 0.8f))),
            radius = maxOf(w, h) * 0.58f
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawContent()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.110f * power),
                Color(0xFF8DF9EA).copy(alpha = 0.100f * power),
                Color.Transparent,
                Color(0xFFFFE08A).copy(alpha = 0.055f * power),
                Color(0xFFFF82DF).copy(alpha = 0.065f * power)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(width = 0.72.dp.toPx() + if (selected) pulse * 0.42.dp.toPx() else 0f),
        blendMode = BlendMode.Plus
    )
    val sweep = (phase * 1.12f) % 1f
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF72DC).copy(alpha = 0.026f * power),
                Color(0xFFFFE08A).copy(alpha = 0.040f * power),
                Color(0xFF7DFFF0).copy(alpha = 0.055f * power),
                Color.Transparent
            ),
            start = Offset(w * (sweep - 0.45f), -h * 0.16f),
            end = Offset(w * (sweep + 0.30f), h * 1.10f)
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
}

@Composable
private fun NetworkChipV2(state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    val accent = if (state.onlineEnabled) Color(0xFF8DF9EA) else Color(0xFF9EB7FF)
    PressableGlass(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 999, modifier.height(48.dp), GlassRole.Chip, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulseDotV2(active = state.onlineEnabled || state.isSending, color = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("联网", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (state.onlineEnabled) "已开启" else "已关闭", color = Color.White.copy(alpha = 0.94f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChatPanelV2(state: AssistantUiState, modifier: Modifier, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    val listState = rememberLazyListState()
    val rainbowTransition = rememberInfiniteTransition(label = "chat-shell-rainbow")
    val rainbowPhase by rainbowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(7600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "chat-shell-rainbow-phase"
    )
    val responsePulse by animateFloatAsState(
        targetValue = if (state.isSending) 1f else 0.42f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "chat-shell-rainbow-response"
    )
    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier.fillMaxWidth(), GlassRole.Shell) {
        Box(
            Modifier
                .fillMaxSize()
                .chatShellRainbowOptics(
                    phase = rainbowPhase,
                    activity = responsePulse,
                    motionIntensity = state.motionIntensity
                )
        ) {
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ChatStatusV2(if (state.isSending) "正在思考" else "可上下滑动")
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message -> AnimatedMessageBubbleV2(message, state) }
                    item { StarterSuggestionsV2(state, onDraftCommand, onPickImage) }
                }
            }
        }
    }
}

private fun Modifier.chatShellRainbowOptics(
    phase: Float,
    activity: Float,
    motionIntensity: Float
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val t = if (motionIntensity > 0.02f) phase - phase.toInt() else 0.18f
    val active = (0.38f + activity.coerceIn(0f, 1f) * 0.62f).coerceIn(0f, 1f)
    val radiusPx = 30.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val shimmerX = -0.32f + t * 1.64f
    val reverseX = 1.28f - t * 1.54f
    val glowCenter = Offset(w * (0.16f + 0.68f * t), h * (0.18f + 0.08f * sin(t * 2f * PI.toFloat())))
    val prismCenter = Offset(w * (0.76f - 0.42f * t), h * (0.78f - 0.16f * sin((t + 0.25f) * 2f * PI.toFloat())))
    val baseAlpha = (0.090f + 0.052f * active).coerceIn(0f, 0.18f)
    val rimAlpha = (0.155f + 0.120f * active).coerceIn(0f, 0.34f)
    val lensAlpha = (0.045f + 0.110f * active).coerceIn(0f, 0.20f)

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8BD9).copy(alpha = baseAlpha * 0.52f),
                Color(0xFF8DFFF3).copy(alpha = baseAlpha * 0.36f),
                Color.Transparent
            ),
            center = glowCenter,
            radius = maxOf(w, h) * 0.92f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFF0A8).copy(alpha = lensAlpha * 0.56f),
                Color(0xFF74FFF1).copy(alpha = lensAlpha * 0.42f),
                Color(0xFFAEB7FF).copy(alpha = lensAlpha * 0.28f),
                Color.Transparent
            ),
            center = prismCenter,
            radius = maxOf(w, h) * (0.62f + 0.18f * active)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF6FD6).copy(alpha = 0.048f * active),
                Color(0xFFFFE08A).copy(alpha = 0.036f * active),
                Color(0xFF6DFFF0).copy(alpha = 0.052f * active),
                Color(0xFF91A3FF).copy(alpha = 0.040f * active),
                Color.Transparent
            ),
            start = Offset(w * (shimmerX - 0.56f), -h * 0.08f),
            end = Offset(w * (shimmerX + 0.52f), h * 1.08f)
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF000713).copy(alpha = 0.018f + 0.018f * active),
                Color(0xFF00040D).copy(alpha = 0.052f + 0.038f * active)
            ),
            center = Offset(w * 0.52f, h * 0.52f),
            radius = maxOf(w, h) * 1.06f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Multiply
    )

    drawContent()

    val inset = 0.74.dp.toPx()
    val rimSize = Size(w - inset * 2f, h - inset * 2f)
    val rimCorner = CornerRadius((radiusPx - inset).coerceAtLeast(0f), (radiusPx - inset).coerceAtLeast(0f))
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF64D8).copy(alpha = rimAlpha * 0.92f),
                Color(0xFFFFE27A).copy(alpha = rimAlpha * 0.68f),
                Color(0xFF67FFF0).copy(alpha = rimAlpha * 0.96f),
                Color(0xFF9CA7FF).copy(alpha = rimAlpha * 0.72f),
                Color.Transparent
            ),
            start = Offset(w * (shimmerX - 0.30f), h * -0.04f),
            end = Offset(w * (shimmerX + 0.36f), h * 1.02f)
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 1.22.dp.toPx() + 0.42.dp.toPx() * active),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.145f * active),
                Color(0xFFB9FFF8).copy(alpha = 0.078f * active),
                Color.Transparent
            ),
            start = Offset(w * (reverseX - 0.24f), 0f),
            end = Offset(w * (reverseX + 0.20f), h * 0.30f)
        ),
        topLeft = Offset(inset * 1.6f, inset * 1.6f),
        size = Size(w - inset * 3.2f, h - inset * 3.2f),
        cornerRadius = rimCorner,
        style = Stroke(width = 0.72.dp.toPx()),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.102f * active),
                Color(0xFFFFA6E7).copy(alpha = 0.050f * active),
                Color(0xFF77FFF2).copy(alpha = 0.036f * active),
                Color.Transparent
            ),
            center = glowCenter,
            radius = maxOf(w, h) * 0.40f
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.070f + 0.060f * active),
                Color(0xFFFFDFF7).copy(alpha = 0.022f + 0.026f * active),
                Color.Transparent,
                Color(0xFF000817).copy(alpha = 0.046f + 0.030f * active)
            ),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset(inset, inset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.64.dp.toPx()),
        blendMode = BlendMode.Screen
    )
}
@Composable
private fun RainbowChatGlassOverlay(quality: RenderQuality, motionIntensity: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chat-rainbow-glass-overlay")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart), label = "chat-rainbow-overlay-phase")
    val activePhase = if (quality.enableMotion && motionIntensity > 0.02f) phase else 0.16f
    Box(modifier.chatPanelPrismOverlay(activePhase, motionIntensity))
}

private fun Modifier.chatPanelPrismOverlay(phase: Float, motionIntensity: Float): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val cycle = phase * 2f * PI.toFloat()
    val sweepX = 0.50f + 0.32f * sin(cycle)
    val corner = CornerRadius(30.dp.toPx(), 30.dp.toPx())
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF65FFF0).copy(alpha = 0.018f * motion), Color(0xFFFF8FE7).copy(alpha = 0.014f * motion), Color.Transparent),
            center = Offset(w * 0.74f, h * (0.44f + 0.08f * sin(cycle + 1.4f))),
            radius = maxOf(w, h) * 0.54f
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color(0xFFFFF0A8).copy(alpha = 0.010f * motion), Color(0xFF76FFF1).copy(alpha = 0.018f * motion), Color(0xFFFF72D2).copy(alpha = 0.012f * motion), Color.Transparent),
            start = Offset(w * (sweepX - 0.24f), 0f),
            end = Offset(w * (sweepX + 0.18f), h * 0.72f)
        ),
        topLeft = Offset(0.72.dp.toPx(), 0.72.dp.toPx()),
        size = Size(w - 1.44.dp.toPx(), h - 1.44.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(width = 0.48.dp.toPx()),
        blendMode = BlendMode.Plus
    )
    drawContent()
}

@Composable
private fun StarterSuggestionsV2(state: AssistantUiState, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    AnimatedVisibility(
        visible = !state.isSending && state.messages.size <= 2,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.72f)) { it / 2 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 2 }
    ) {
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
            slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { width -> if (fromUser) width / 4 else -width / 4 } +
            scaleIn(initialScale = 0.96f, animationSpec = tween(260, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.98f, animationSpec = tween(120))
    ) { MessageBubbleV2(message, state) }
}

@Composable
private fun MessageBubbleV2(message: ChatMessage, state: AssistantUiState) {
    val fromUser = message.role == MessageRole.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity * if (fromUser) 1.03f else 0.94f,
            motionIntensity = state.motionIntensity,
            radius = 24,
            modifier = Modifier.fillMaxWidth(if (fromUser) 0.76f else 0.90f),
            role = if (fromUser) GlassRole.Floating else GlassRole.Card
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.status == MessageStatus.Sending && !fromUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("正在思考", color = Color.White.copy(alpha = 0.84f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        ThinkingDotsV2(size = 7, color = Color(0xFF8DF9EA).copy(alpha = 0.92f))
                    }
                } else {
                    Text(messageText(message), color = messageTextColor(message, fromUser), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium)
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
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                enabled = !state.isSending,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Color.White.copy(alpha = 0.86f)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
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
            if (state.isSending) ThinkingDotsV2(size = 6, color = Color.White.copy(alpha = 0.90f))
            else Text("↑", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RoundIconButtonV2(text: String, state: AssistantUiState, size: Int = 40, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 999, Modifier.size(size.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = if (text == "+") 25.sp else 15.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
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
    val transition = rememberInfiniteTransition(label = "thinking-glass-pearls-v2")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing), RepeatMode.Restart), label = "thinking-glass-pearls-phase")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val wave = ((sin(phase * 2f * PI.toFloat() + index * 1.34f) + 1f) / 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer { translationY = -5.6f * wave; alpha = 0.54f + 0.46f * wave; scaleX = 0.76f + 0.42f * wave; scaleY = 0.72f + 0.30f * wave }
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
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
    val pulse by transition.animateFloat(0.76f, 1.22f, infiniteRepeatable(tween(860, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse-dot-value-v2")
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
