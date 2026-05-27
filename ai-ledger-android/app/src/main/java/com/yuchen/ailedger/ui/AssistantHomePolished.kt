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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

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
    val panelHeight by animateDpAsState(
        targetValue = if (expanded) 224.dp else 58.dp,
        animationSpec = spring(dampingRatio = 0.64f, stiffness = Spring.StiffnessLow),
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
    BoxWithConstraints(modifier = modifier) {
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
            val expandedX = when (index) {
                1, 3 -> halfWidth + gap
                else -> 0.dp
            }
            val expandedY = when (index) {
                2, 3 -> rowStep
                4 -> rowStep * 2f
                else -> 0.dp
            }
            val expandedWidth = if (index == 4) maxWidth else halfWidth
            val collapsedX = if (selected) 0.dp else (stackRank * 5).dp
            val collapsedY = if (selected) 0.dp else (stackRank * 1.6f).dp
            val collapsedAlpha = if (selected) 1f else 0.48f + (4 - stackRank).coerceAtLeast(0) * 0.080f
            val staggerRank = when (index) {
                0 -> 0
                2 -> 1
                1 -> 2
                3 -> 3
                else -> 4
            }
            val z = if (expanded) 30f - index else if (selected) 50f else 40f - stackRank
            ModelStackCard(
                model = model,
                selected = selected,
                state = state,
                expanded = expanded,
                index = index,
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
    index: Int,
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
            durationMillis = if (expanded) 680 else 420,
            delayMillis = if (expanded) staggerRank * 46 else (ChatModel.entries.lastIndex - staggerRank) * 24,
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
    val curveT = eased.coerceIn(0f, 1f)
    val inverse = 1f - curveT
    val arcDepth = with(density) { (32.dp + (staggerRank * 2).dp).toPx() }
    val controlX = startX + dx * 0.38f
    val controlY = maxOf(startY, endY) + arcDepth
    val curveX = inverse * inverse * startX + 2f * inverse * curveT * controlX + curveT * curveT * endX
    val curveY = inverse * inverse * startY + 2f * inverse * curveT * controlY + curveT * curveT * endY
    val speedPulse = modelStackSpeedPulse(cardProgress)
    val brake = modelStackArrivalBrake(cardProgress)
    val returnBrake = modelStackReturnBrake(cardProgress)
    val overshootPx = with(density) { 2.6.dp.toPx() } * brake - with(density) { 1.8.dp.toPx() } * returnBrake
    val tx = curveX + dx / distance * overshootPx
    val ty = curveY + dy / distance * overshootPx
    val settled = cardProgress < 0.025f || cardProgress > 0.985f
    val capsuleScaleX = modelStackCapsuleScaleX(cardProgress)
    val capsuleScaleY = modelStackCapsuleScaleY(cardProgress)
    val selectedPulse by animateFloatAsState(
        targetValue = if (selected && settled) 1.008f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow),
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
            shadowElevation = if (settled && selected) 0.24f else 0.03f + 0.06f * speedPulse
        }

    ModelFrostCapsule(
        model = model,
        selected = selected,
        state = state,
        modifier = transformModifier,
        expansionProgress = cardProgress.coerceIn(0f, 1f),
        settled = settled,
        stackRank = stackRank,
        onClick = onClick
    )
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction.coerceIn(0f, 1f)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)

private fun modelStackMotionEase(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

private fun modelStackSpeedPulse(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return sin(p * PI.toFloat()).coerceAtLeast(0f)
}

private fun modelStackArrivalBrake(progress: Float): Float {
    val p = ((progress - 0.78f) / 0.22f).coerceIn(0f, 1f)
    return sin(p * PI.toFloat()).coerceAtLeast(0f)
}

private fun modelStackReturnBrake(progress: Float): Float {
    val p = ((0.24f - progress) / 0.24f).coerceIn(0f, 1f)
    return sin(p * PI.toFloat()).coerceAtLeast(0f)
}

private fun modelStackCapsuleScaleX(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    val speed = modelStackSpeedPulse(p)
    val arrive = modelStackArrivalBrake(p)
    val back = modelStackReturnBrake(p)
    return 1f - 0.070f * speed + 0.030f * arrive + 0.014f * back
}

private fun modelStackCapsuleScaleY(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p < 0.025f || p > 0.985f) return 1f
    val speed = modelStackSpeedPulse(p)
    val arrive = modelStackArrivalBrake(p)
    val back = modelStackReturnBrake(p)
    return 1f - 0.115f * speed + 0.040f * arrive + 0.018f * back
}

@Composable
private fun ModelFrostCapsule(
    model: ChatModel,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    expansionProgress: Float,
    settled: Boolean,
    stackRank: Int,
    onClick: () -> Unit
) {
    val radius = 30
    val shape = RoundedCornerShape(radius.dp)
    val moving = if (settled) 0f else 1f
    val selectedEnergy = if (selected) 1f else 0f
    val stackEnergy = if (stackRank > 0) 0.42f else 1f
    val expandedSettled = settled && expansionProgress > 0.985f
    val collapsedTopSettled = settled && expansionProgress < 0.025f && selected
    val useFullGlass = expandedSettled || collapsedTopSettled

    if (useFullGlass) {
        val role = if (selected) GlassRole.Floating else GlassRole.Card
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity * (0.92f + selectedEnergy * 0.16f),
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = modifier,
            role = role,
            onClick = onClick
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
                ModelCapsuleChrome(
                    selected = selected,
                    expansionProgress = expansionProgress,
                    moving = 0f,
                    stackEnergy = stackEnergy,
                    shape = shape
                )
                ModelStackCardContent(model = model, selected = selected, expansionProgress = expansionProgress)
            }
        }
    } else {
        LightweightModelCapsule(
            model = model,
            selected = selected,
            state = state,
            modifier = modifier,
            expansionProgress = expansionProgress,
            moving = moving,
            stackEnergy = stackEnergy,
            shape = shape,
            showContent = selected || expansionProgress > 0.035f,
            onClick = onClick
        )
    }
}

@Composable
private fun LightweightModelCapsule(
    model: ChatModel,
    selected: Boolean,
    state: AssistantUiState,
    modifier: Modifier,
    expansionProgress: Float,
    moving: Float,
    stackEnergy: Float,
    shape: RoundedCornerShape,
    showContent: Boolean,
    onClick: () -> Unit
) {
    val selectedEnergy = if (selected) 1f else 0f
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = (0.042f + selectedEnergy * 0.018f + moving * 0.010f) * maxOf(stackEnergy, 0.58f)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !state.isSending,
                onClick = onClick
            )
    ) {
        ModelCapsuleChrome(
            selected = selected,
            expansionProgress = expansionProgress,
            moving = moving,
            stackEnergy = stackEnergy,
            shape = shape
        )
        if (showContent) {
            ModelStackCardContent(model = model, selected = selected, expansionProgress = expansionProgress)
        }
    }
}

@Composable
private fun ModelCapsuleChrome(
    selected: Boolean,
    expansionProgress: Float,
    moving: Float,
    stackEnergy: Float,
    shape: RoundedCornerShape
) {
    val selectedEnergy = if (selected) 1f else 0f
    Box(
        Modifier
            .fillMaxSize()
            .border(
                width = if (selected) 1.22.dp else 0.86.dp,
                color = if (selected) {
                    Color(0xFF8DF9EA).copy(alpha = 0.48f + 0.18f * expansionProgress + 0.07f * moving)
                } else {
                    Color.White.copy(alpha = (0.18f + 0.10f * expansionProgress + 0.04f * moving) * stackEnergy)
                },
                shape = shape
            )
    )
    Box(
        Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(shape)
            .background(
                Color.White.copy(alpha = 0.010f + selectedEnergy * 0.018f + moving * 0.006f)
            )
    )
}

@Composable
private fun ModelStackCardContent(model: ChatModel, selected: Boolean, expansionProgress: Float) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(if (selected) 9.dp else 7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) Color(0xFF8DF9EA) else Color.White.copy(alpha = 0.32f + 0.20f * expansionProgress))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(model.shortLabel, color = Color.White.copy(alpha = if (selected) 0.96f else 0.78f), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(model.id, color = Color.White.copy(alpha = if (selected) 0.54f else 0.38f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInHorizontally(spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)) { width -> if (fromUser) width / 3 else -width / 3 } +
            scaleIn(initialScale = 0.90f, animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f, animationSpec = tween(120))
    ) {
        MessageBubbleV2(message, state)
    }
}

@Composable
private fun MessageBubbleV2(message: ChatMessage, state: AssistantUiState) {
    val fromUser = message.role == MessageRole.User
    val fill = if (fromUser) 0.76f else 0.90f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity * if (fromUser) 1.03f else 0.94f,
            motionIntensity = state.motionIntensity,
            radius = 22,
            modifier = Modifier.fillMaxWidth(fill),
            role = if (fromUser) GlassRole.Floating else GlassRole.Card
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.status == MessageStatus.Sending && !fromUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("正在思考", color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        ThinkingDotsV2(size = 6, color = Color(0xFF8DF9EA).copy(alpha = 0.88f))
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
    val transition = rememberInfiniteTransition(label = "thinking-dots-v2")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(920, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "thinking-phase-v2"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val wave = ((sin(phase * 2f * PI.toFloat() + index * 1.45f) + 1f) / 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        translationY = -6f * wave
                        alpha = 0.32f + 0.68f * wave
                        scaleX = 0.72f + 0.34f * wave
                        scaleY = 0.72f + 0.34f * wave
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
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
