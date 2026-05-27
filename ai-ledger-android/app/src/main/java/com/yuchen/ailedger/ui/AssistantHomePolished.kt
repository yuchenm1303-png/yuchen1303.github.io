package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 68.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssistantEntrance(delayMs = 0, initialOffsetY = -10, initialScale = 0.98f) {
            AssistantHeroV2(
                state = state,
                onOpenTools = onOpenTools,
                onOpenSettings = onOpenSettings
            )
        }
        AssistantEntrance(delayMs = 46, initialOffsetY = 16, initialScale = 0.965f) {
            ModelAndNetworkPanel(
                state = state,
                onModelSelected = onModelSelected,
                onToggleOnline = onToggleOnline
            )
        }
        AssistantEntrance(
            delayMs = 92,
            modifier = Modifier.weight(1f),
            initialOffsetY = 30,
            initialScale = 0.955f
        ) {
            ChatPanelV2(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                onDraftCommand = onDraftCommand,
                onPickImage = onPickImage
            )
        }
        AssistantEntrance(delayMs = 138, initialOffsetY = 18, initialScale = 0.965f) {
            ComposerBarV2(
                state = state,
                onComposerChange = onComposerChange,
                onSend = onSend,
                onPickImage = onPickImage
            )
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
        exit = fadeOut(tween(100)) +
            scaleOut(targetScale = 0.985f, animationSpec = tween(120))
    ) {
        content()
    }
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
    onModelSelected: (ChatModel) -> Unit,
    onToggleOnline: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModelSelectorChip(
                state = state,
                expanded = expanded,
                modifier = Modifier.weight(1.35f),
                onClick = { if (!state.isSending) expanded = !expanded }
            )
            NetworkChipV2(
                state = state,
                modifier = Modifier.weight(0.82f),
                onClick = { if (!state.isSending) onToggleOnline() }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(100)) +
                expandVertically(spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) +
                scaleIn(
                    initialScale = 0.90f,
                    transformOrigin = TransformOrigin(0.82f, 0f),
                    animationSpec = spring(dampingRatio = 0.64f, stiffness = Spring.StiffnessMediumLow)
                ),
            exit = fadeOut(tween(120)) +
                shrinkVertically(tween(170, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
                scaleOut(
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(0.82f, 0f),
                    animationSpec = tween(170, easing = FastOutSlowInEasing)
                )
        ) {
            ModelChooserSheet(
                state = state,
                onSelected = { model ->
                    onModelSelected(model)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ModelSelectorChip(state: AssistantUiState, expanded: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "model-folder-stack-loop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "model-folder-stack-phase"
    )
    val expandedProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "model-folder-stack-expand"
    )
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(48.dp), GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize()) {
            ModelSelectorChipOptics(phase, expandedProgress, state.motionIntensity)
            Row(
                Modifier.fillMaxSize().padding(start = 14.dp, end = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("AI", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text("模型", color = Color.White.copy(alpha = 0.46f + expandedProgress * 0.10f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(state.selectedModel.label, color = Color.White.copy(alpha = 0.94f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.isSending) {
                    ThinkingDotsV2(size = 4, color = Color.White.copy(alpha = 0.62f))
                } else {
                    FoldedModelCardStack(
                        models = ChatModel.entries,
                        selected = state.selectedModel,
                        expandedProgress = expandedProgress,
                        phase = phase
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSelectorChipOptics(phase: Float, expandedProgress: Float, motionIntensity: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val motion = motionIntensity.coerceIn(0f, 1.2f)
        val cycle = phase * 2f * PI.toFloat()
        val corner = CornerRadius(h / 2f, h / 2f)
        val power = (0.70f + expandedProgress * 0.66f) * motion
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8DF9EA).copy(alpha = 0.045f * power),
                    Color(0xFF8CA0FF).copy(alpha = 0.038f * power),
                    Color.Transparent
                ),
                center = Offset(w * (0.78f + 0.08f * sin(cycle)), h * (0.42f + 0.12f * cos(cycle * 0.7f))),
                radius = w * 0.42f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF72DC).copy(alpha = 0.040f * power),
                    Color(0xFFFFE08A).copy(alpha = 0.036f * power),
                    Color(0xFF7DFFF0).copy(alpha = 0.050f * power),
                    Color.Transparent
                ),
                start = Offset(w * (phase - 0.52f), -h * 0.2f),
                end = Offset(w * (phase + 0.36f), h * 1.15f)
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Plus
        )
    }
}

@Composable
private fun FoldedModelCardStack(models: List<ChatModel>, selected: ChatModel, expandedProgress: Float, phase: Float) {
    val pulse = ((sin(phase * 2f * PI.toFloat()) + 1f) * 0.5f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 34.dp)
            .graphicsLayer {
                scaleX = 1f + pulse * 0.022f + expandedProgress * 0.060f
                scaleY = 1f + pulse * 0.018f - expandedProgress * 0.030f
                translationY = expandedProgress * 3.0f
                rotationZ = expandedProgress * 4.0f
            },
        contentAlignment = Alignment.Center
    ) {
        val preview = buildList {
            add(selected)
            models.filter { it != selected }.take(3).forEach { add(it) }
        }
        preview.take(4).reversed().forEachIndexed { reversedIndex, model ->
            val stackIndex = 3 - reversedIndex
            val offsetX = (stackIndex * -3).dp
            val offsetY = (stackIndex * 2).dp
            val alpha = 0.42f + stackIndex * 0.14f
            MiniStackCard(
                model = model,
                selected = model == selected,
                alpha = alpha,
                modifier = Modifier
                    .size(width = 30.dp, height = 23.dp)
                    .graphicsLayer {
                        translationX = offsetX.toPx()
                        translationY = offsetY.toPx()
                        rotationZ = -5f + stackIndex * 2.8f + expandedProgress * (stackIndex - 1.5f) * 4f
                    }
            )
        }
    }
}

@Composable
private fun MiniStackCard(model: ChatModel, selected: Boolean, alpha: Float, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = if (selected) listOf(
                        Color(0xFF8DF9EA).copy(alpha = 0.42f * alpha),
                        Color(0xFF9FA8FF).copy(alpha = 0.34f * alpha),
                        Color(0xFFFF82DF).copy(alpha = 0.22f * alpha)
                    ) else listOf(
                        Color.White.copy(alpha = 0.18f * alpha),
                        Color(0xFF8096FF).copy(alpha = 0.20f * alpha),
                        Color(0xFF101A42).copy(alpha = 0.52f * alpha)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                size = Size(w, h),
                cornerRadius = corner,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (selected) 0.25f else 0.12f),
                size = Size(w, h),
                cornerRadius = corner,
                style = Stroke(width = 0.7.dp.toPx()),
                blendMode = BlendMode.Screen
            )
        }
        Text(
            text = model.shortLabel.take(1),
            color = Color.White.copy(alpha = if (selected) 0.88f else 0.54f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center)
        )
    }
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
private fun ModelChooserSheet(state: AssistantUiState, onSelected: (ChatModel) -> Unit) {
    val transition = rememberInfiniteTransition(label = "model-folder-sheet-loop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6800, easing = LinearEasing), RepeatMode.Restart),
        label = "model-folder-sheet-phase"
    )
    GlassPanel(state.quality, state.glassIntensity * 1.02f, state.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Box(Modifier.fillMaxWidth().modelChooserSurfaceOptics(phase, state.motionIntensity)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("选择模型", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("折叠卡片展开成模型气泡，点一下立即切换。", color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (state.onlineEnabled) "联网模式" else "本地/云端默认", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                ChatModel.entries.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { columnIndex, model ->
                            val index = rowIndex * 2 + columnIndex
                            ModelOptionCard(
                                model = model,
                                selected = model == state.selectedModel,
                                index = index,
                                state = state,
                                modifier = Modifier.weight(1f)
                            ) { onSelected(model) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun Modifier.modelChooserSurfaceOptics(phase: Float, motionIntensity: Float): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1.2f)
    val cycle = phase * 2f * PI.toFloat()
    val corner = CornerRadius(26.dp.toPx(), 26.dp.toPx())
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF8DF9EA).copy(alpha = 0.050f * motion),
                Color(0xFF7D8DFF).copy(alpha = 0.038f * motion),
                Color.Transparent
            ),
            center = Offset(w * (0.78f + 0.08f * cos(cycle)), h * (0.24f + 0.10f * sin(cycle))),
            radius = maxOf(w, h) * 0.54f
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF72DC).copy(alpha = 0.040f * motion),
                Color(0xFFFFE08A).copy(alpha = 0.034f * motion),
                Color(0xFF7DFFF0).copy(alpha = 0.052f * motion),
                Color.Transparent
            ),
            start = Offset(w * (phase - 0.60f), -h * 0.12f),
            end = Offset(w * (phase + 0.38f), h * 1.10f)
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Plus
    )
    drawContent()
}

@Composable
private fun ModelOptionCard(model: ChatModel, selected: Boolean, index: Int, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    var visible by remember(model) { mutableStateOf(false) }
    LaunchedEffect(model) {
        delay(48L * index)
        visible = true
    }
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "model-option-folder-reveal"
    )
    val transition = rememberInfiniteTransition(label = "model-option-${model.id}")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (selected) 2600 else 4600, easing = LinearEasing), RepeatMode.Restart),
        label = "model-option-phase-${model.id}"
    )
    val pulse = if (selected) ((sin(phase * 2f * PI.toFloat()) + 1f) * 0.5f).coerceIn(0f, 1f) else 0f
    val settle = reveal.coerceIn(0f, 1f)
    val overshoot = ((reveal - 1f) / 0.10f).coerceIn(0f, 1f)
    val flyX = if (index % 2 == 0) 92f else 32f
    val flyY = -76f + index * 9f
    PressableGlass(
        state.quality,
        state.glassIntensity * if (selected) 1.10f else 0.92f,
        state.motionIntensity,
        22,
        modifier
            .height(58.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.86f, 0.12f)
                alpha = settle
                translationX = (1f - settle) * flyX
                translationY = (1f - settle) * flyY - pulse * 0.8f
                scaleX = 0.62f + settle * 0.38f + overshoot * 0.032f + pulse * 0.010f
                scaleY = 0.60f + settle * 0.40f - overshoot * 0.020f + pulse * 0.008f
                rotationZ = (1f - settle) * (if (index % 2 == 0) 8f else -5f)
            },
        if (selected) GlassRole.Floating else GlassRole.Chip,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize().modelOptionPrism(selected, pulse, state.motionIntensity)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val dot = if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.34f)
                        drawCircle(
                            color = dot.copy(alpha = if (selected) 0.86f + pulse * 0.14f else 0.42f),
                            radius = size.minDimension * if (selected) (0.34f + pulse * 0.08f) else 0.25f,
                            center = Offset(size.width / 2f, size.height / 2f),
                            blendMode = BlendMode.Screen
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(model.shortLabel, color = Color.White.copy(alpha = if (selected) 0.98f else 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(model.id, color = Color.White.copy(alpha = if (selected) 0.48f else 0.36f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun Modifier.modelOptionPrism(selected: Boolean, pulse: Float, motionIntensity: Float): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1.2f)
    val corner = CornerRadius(22.dp.toPx(), 22.dp.toPx())
    if (selected) {
        val power = (0.78f + pulse * 0.46f) * motion
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8DFFF3).copy(alpha = 0.085f * power),
                    Color(0xFFFF78E2).copy(alpha = 0.045f * power),
                    Color.Transparent
                ),
                center = Offset(w * 0.78f, h * 0.36f),
                radius = maxOf(w, h) * 0.56f
            ),
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Screen
        )
    }
    drawContent()
    if (selected) {
        val power = (0.80f + pulse * 0.46f) * motion
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * power),
                    Color(0xFF8DF9EA).copy(alpha = 0.13f * power),
                    Color.Transparent,
                    Color(0xFFFF8BE8).copy(alpha = 0.060f * power)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
            cornerRadius = corner,
            style = Stroke(width = 0.82.dp.toPx() + pulse * 0.42.dp.toPx()),
            blendMode = BlendMode.Plus
        )
    }
}

@Composable
private fun ChatPanelV2(state: AssistantUiState, modifier: Modifier, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier.fillMaxWidth(), GlassRole.Shell) {
        Box(Modifier.fillMaxSize()) {
            RainbowChatGlassOverlay(
                quality = state.quality,
                motionIntensity = state.motionIntensity,
                modifier = Modifier.matchParentSize()
            )
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ChatStatusV2(if (state.isSending) "正在思考" else "可上下滑动")
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 5.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(state.messages, key = { it.id }) { message -> AnimatedMessageBubbleV2(message, state) }
                    item { StarterSuggestionsV2(state, onDraftCommand, onPickImage) }
                }
            }
        }
    }
}

@Composable
private fun RainbowChatGlassOverlay(
    quality: RenderQuality,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "chat-rainbow-glass-overlay")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(7200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "chat-rainbow-overlay-phase"
    )
    val activePhase = if (quality.enableMotion && motionIntensity > 0.02f) phase else 0.16f
    Box(modifier.chatPanelPrismOverlay(activePhase, motionIntensity))
}

private fun Modifier.chatPanelPrismOverlay(phase: Float, motionIntensity: Float): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val cycle = phase * 2f * PI.toFloat()
    val sweepX = 0.50f + 0.32f * sin(cycle)
    val glowY = 0.44f + 0.08f * sin(cycle + 1.4f)
    val corner = CornerRadius(30.dp.toPx(), 30.dp.toPx())
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF65FFF0).copy(alpha = 0.018f * motion),
                Color(0xFFFF8FE7).copy(alpha = 0.014f * motion),
                Color.Transparent
            ),
            center = Offset(w * 0.74f, h * glowY),
            radius = maxOf(w, h) * 0.54f
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
                Color(0xFFFFF0A8).copy(alpha = 0.010f * motion),
                Color(0xFF76FFF1).copy(alpha = 0.018f * motion),
                Color(0xFFFF72D2).copy(alpha = 0.012f * motion),
                Color.Transparent
            ),
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
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = if (fromUser) 0.56f else 0.70f, stiffness = Spring.StiffnessMediumLow),
        label = "message-bubble-prism-reveal"
    )
    val sendingPulse by animateFloatAsState(
        targetValue = if (message.status == MessageStatus.Sending && !fromUser) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "message-bubble-sending-pulse"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val rawReveal = reveal.coerceIn(0f, 1.10f)
                val settle = rawReveal.coerceIn(0f, 1f)
                val overshoot = ((rawReveal - 1f) / 0.10f).coerceIn(0f, 1f)
                val capsuleStretch = overshoot * if (fromUser) 1f else 0.72f
                transformOrigin = if (fromUser) TransformOrigin(0.92f, 0.82f) else TransformOrigin(0.08f, 0.22f)
                alpha = settle
                scaleX = 0.76f + settle * 0.24f + capsuleStretch * 0.018f + sendingPulse * 0.004f
                scaleY = 0.64f + settle * 0.36f - capsuleStretch * 0.020f + sendingPulse * 0.006f
                translationX = (1f - settle) * if (fromUser) 34f else -26f
                translationY = (1f - settle) * if (fromUser) 14f else -8f
            }
        ) {
            MessageBubbleV2(message, state, revealProgress = reveal.coerceIn(0f, 1.18f))
        }
    }
}

@Composable
private fun MessageBubbleV2(message: ChatMessage, state: AssistantUiState, revealProgress: Float) {
    val fromUser = message.role == MessageRole.User
    val fill = if (fromUser) 0.76f else 0.90f
    val sending = message.status == MessageStatus.Sending && !fromUser
    val bubbleRadius = if (fromUser) 26 else 28
    val transition = rememberInfiniteTransition(label = "message-bubble-optics-loop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(if (sending) 1680 else 3600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "message-bubble-optics-phase"
    )
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (fromUser) 1.03f else if (sending) 1.01f else 0.94f,
        motionIntensity = state.motionIntensity,
        radius = bubbleRadius,
        modifier = Modifier.fillMaxWidth(fill),
        role = if (fromUser) GlassRole.Floating else GlassRole.Card
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .messageBubblePrismOptics(
                    reveal = revealProgress,
                    phase = phase,
                    fromUser = fromUser,
                    sending = sending,
                    failed = message.status == MessageStatus.Failed,
                    motionIntensity = state.motionIntensity,
                    radiusDp = bubbleRadius
                )
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("正在思考", color = Color.White.copy(alpha = 0.84f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        ThinkingDotsV2(size = 7, color = Color(0xFF8DF9EA).copy(alpha = 0.92f))
                    }
                } else {
                    Text(
                        text = messageText(message),
                        color = messageTextColor(message, fromUser),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium
                    )
                }
                if (!fromUser) MessageBadgeV2(message)
            }
        }
    }
}

private fun Modifier.messageBubblePrismOptics(
    reveal: Float,
    phase: Float,
    fromUser: Boolean,
    sending: Boolean,
    failed: Boolean,
    motionIntensity: Float,
    radiusDp: Int
): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val r = reveal.coerceIn(0f, 1.18f)
    val revealGlow = (1f - ((r - 0.82f) / 0.36f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val active = if (sending) 1f else revealGlow
    val cycle = phase * 2f * PI.toFloat()
    val sweep = if (fromUser) 1f - phase else phase
    val sweepX = -0.30f + sweep * 1.58f
    val center = if (fromUser) {
        Offset(w * (0.82f - 0.08f * sin(cycle)), h * 0.70f)
    } else {
        Offset(w * (0.16f + 0.08f * sin(cycle)), h * (0.25f + 0.10f * sin(cycle + 1.4f)))
    }
    val radiusPx = radiusDp.dp.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val accentA = if (failed) Color(0xFFFF9A9A) else if (fromUser) Color(0xFF9EB7FF) else Color(0xFF8DF9EA)
    val accentB = if (failed) Color(0xFFFFD166) else if (fromUser) Color(0xFFFF8FE7) else Color(0xFFFFF0A8)
    val accentC = if (fromUser) Color(0xFF76FFF1) else Color(0xFFFF72D2)

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.040f + 0.050f * active) * motion),
                accentA.copy(alpha = (0.040f + 0.100f * active) * motion),
                accentB.copy(alpha = (0.018f + 0.072f * active) * motion),
                Color.Transparent
            ),
            center = center,
            radius = maxOf(w, h) * (0.34f + 0.18f * active)
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
                Color(0xFF000817).copy(alpha = 0.018f + 0.036f * active),
                Color(0xFF00030A).copy(alpha = 0.030f + 0.042f * if (sending) 1f else 0f)
            ),
            center = Offset(w * 0.50f, h * 0.60f),
            radius = maxOf(w, h) * 0.92f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Multiply
    )

    if (sending) {
        val flow = phase - phase.toInt()
        val bandX = -0.74f + flow * 2.18f + 0.055f * sin(cycle * 1.70f)
        val bandY = 0.50f + 0.08f * sin(cycle * 1.13f + 0.90f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFF58D2).copy(alpha = 0.040f * motion),
                    Color(0xFFFFF0A8).copy(alpha = 0.086f * motion),
                    Color(0xFF62FFF0).copy(alpha = 0.128f * motion),
                    Color(0xFF8EA2FF).copy(alpha = 0.082f * motion),
                    Color(0xFFFF82E4).copy(alpha = 0.052f * motion),
                    Color.Transparent
                ),
                start = Offset(w * (bandX - 0.62f), h * (1.16f + bandY * 0.06f)),
                end = Offset(w * (bandX + 0.44f), -h * (0.22f + bandY * 0.06f))
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
                    Color.White.copy(alpha = 0.026f * motion),
                    Color(0xFF8DFFF3).copy(alpha = 0.116f * motion),
                    Color(0xFFFFF0A8).copy(alpha = 0.064f * motion),
                    Color.Transparent
                ),
                start = Offset(w * (bandX - 0.28f), h * 1.06f),
                end = Offset(w * (bandX + 0.18f), -h * 0.10f)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = corner,
            blendMode = BlendMode.Plus
        )
    }

    drawContent()

    val rimInset = 0.68.dp.toPx()
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))
    val rimRadius = (radiusPx - rimInset).coerceAtLeast(1f)
    val rimCorner = CornerRadius(rimRadius, rimRadius)
    val cornerPulse = if (sending) ((sin(cycle * 0.92f) + 1f) * 0.50f).coerceIn(0f, 1f) else revealGlow
    val prismPower = (0.14f + 0.42f * active + 0.12f * cornerPulse) * motion

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.050f * motion),
                accentB.copy(alpha = 0.110f * prismPower),
                accentC.copy(alpha = 0.150f * prismPower),
                Color.Transparent,
                accentA.copy(alpha = 0.116f * prismPower),
                Color.White.copy(alpha = 0.030f * motion)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.58.dp.toPx() + 0.44.dp.toPx() * active),
        blendMode = BlendMode.Screen
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                accentC.copy(alpha = (0.18f + 0.30f * active) * motion),
                Color.White.copy(alpha = (0.11f + 0.24f * active) * motion),
                accentB.copy(alpha = (0.15f + 0.24f * active) * motion),
                Color.Transparent
            ),
            start = Offset(w * (sweepX - 0.22f), 0f),
            end = Offset(w * (sweepX + 0.28f), h)
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.62.dp.toPx() + 0.92.dp.toPx() * active),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.070f + 0.080f * active),
                accentA.copy(alpha = 0.026f + 0.046f * active),
                Color.Transparent,
                Color(0xFF000819).copy(alpha = 0.036f + 0.036f * active)
            ),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = rimCorner,
        style = Stroke(width = 0.46.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    if (sending) {
        val breathing = ((sin(cycle) + 1f) * 0.50f).coerceIn(0f, 1f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF8DF9EA).copy(alpha = 0.12f + 0.10f * breathing),
                    Color(0xFFFFF0A8).copy(alpha = 0.08f + 0.08f * breathing),
                    Color(0xFFFF72D2).copy(alpha = 0.07f + 0.06f * breathing),
                    Color.Transparent
                ),
                start = Offset(w * (0.08f + 0.16f * breathing), 0f),
                end = Offset(w * (0.92f - 0.18f * breathing), h * 0.18f)
            ),
            topLeft = Offset(rimInset * 1.6f, rimInset * 1.6f),
            size = Size(w - rimInset * 3.2f, h - rimInset * 3.2f),
            cornerRadius = rimCorner,
            style = Stroke(width = 0.66.dp.toPx()),
            blendMode = BlendMode.Plus
        )
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
    val focusPop by animateFloatAsState(
        targetValue = if (text.isNotBlank()) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "composer-pop"
    )
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
            if (state.isSending) {
                ThinkingDotsV2(size = 6, color = Color.White.copy(alpha = 0.90f))
            } else {
                Text("↑", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
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
    val transition = rememberInfiniteTransition(label = "thinking-glass-pearls-v2")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1060, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "thinking-glass-pearls-phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val wave = ((sin(phase * 2f * PI.toFloat() + index * 1.34f) + 1f) / 2f).coerceIn(0f, 1f)
            val pulse = ((sin(phase * 2f * PI.toFloat() + index * 1.34f - 0.74f) + 1f) / 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        translationY = -5.6f * wave
                        alpha = 0.54f + 0.46f * wave
                        scaleX = 0.76f + 0.42f * wave
                        scaleY = 0.72f + 0.30f * pulse
                    }
                    .thinkingPearlOptics(color = color, wave = wave, index = index)
            )
        }
    }
}

private fun Modifier.thinkingPearlOptics(color: Color, wave: Float, index: Int): Modifier = drawWithContent {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val corner = CornerRadius(w * 0.50f, h * 0.50f)
    val accent = when (index % 3) {
        0 -> Color(0xFF8DF9EA)
        1 -> Color(0xFFFFF0A8)
        else -> Color(0xFFFF8FE7)
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f + 0.20f * wave),
                color.copy(alpha = 0.28f + 0.18f * wave),
                accent.copy(alpha = 0.30f + 0.26f * wave),
                Color.Transparent
            ),
            center = Offset(w * 0.34f, h * 0.26f),
            radius = maxOf(w, h) * 0.86f
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
                Color.White.copy(alpha = 0.32f + 0.16f * wave),
                accent.copy(alpha = 0.38f + 0.24f * wave),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        ),
        topLeft = Offset(0.45.dp.toPx(), 0.45.dp.toPx()),
        size = Size(w - 0.90.dp.toPx(), h - 0.90.dp.toPx()),
        cornerRadius = corner,
        style = Stroke(width = 0.55.dp.toPx()),
        blendMode = BlendMode.Plus
    )
}

@Composable
private fun PulseDotV2(active: Boolean, color: Color) {
    if (!active) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = 0.68f }
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse-dot-v2")
    val pulse by transition.animateFloat(
        initialValue = 0.76f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(animation = tween(860, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse-dot-value-v2"
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                alpha = 0.96f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
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
