package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AssistantScreenV2(
    state: AssistantUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGenerating: () -> Unit,
    onDraftCommand: (String) -> Unit,
    onModelSelected: (ChatModel) -> Unit,
    onPickImage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleOnline: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 68.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssistantEntrance(delayMs = 0, initialOffsetY = -10, initialScale = 0.98f) {
            AssistantHeroV2(state = state)
        }
        AssistantEntrance(delayMs = 46, initialOffsetY = 16, initialScale = 0.965f) {
            ModelAndNetworkPanel(
                state = state,
                onModelSelected = onModelSelected,
                onToggleOnline = onToggleOnline
            )
        }
        AssistantEntrance(delayMs = 92, modifier = Modifier.weight(1f), initialOffsetY = 30, initialScale = 0.955f) {
            ChatPanelV2(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                onDraftCommand = onDraftCommand,
                onPickImage = onPickImage,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage
            )
        }
        AssistantEntrance(delayMs = 138, initialOffsetY = 18, initialScale = 0.965f) {
            ComposerBarV2(
                state = state,
                onComposerChange = onComposerChange,
                onSend = onSend,
                onStopGenerating = onStopGenerating,
                onPickImage = onPickImage
            )
        }
    }
    onOpenTools.hashCode()
    onOpenSettings.hashCode()
    onCopyMessage.hashCode()
    onRetryMessage.hashCode()
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
private fun AssistantHeroV2(state: AssistantUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("AI ASSISTANT", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("AI 助手", color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
        Text("直接说需求，我来帮你拆成动作。", color = Color.White.copy(alpha = 0.54f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    state.quality.hashCode()
}

@Composable
private fun ModelAndNetworkPanel(
    state: AssistantUiState,
    onModelSelected: (ChatModel) -> Unit,
    onToggleOnline: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modelRowCount = ((ChatModel.entries.size + 1) / 2).coerceAtLeast(1)
    val expandedPanelHeight = (64 + 74 * (modelRowCount - 1)).dp
    val panelHeight by animateDpAsState(
        targetValue = if (expanded) expandedPanelHeight else 58.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "model-stack-panel-height"
    )
    Box(
        modifier = Modifier.fillMaxWidth().height(panelHeight)
    ) {
        UnifiedParentModelStackSelector(
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
        NetworkDropletCapsule(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.30f)
                .height(58.dp),
            enabled = !state.isSending,
            onClick = onToggleOnline
        )
    }
}

@Composable
private fun ChatPanelV2(
    state: AssistantUiState,
    modifier: Modifier,
    onDraftCommand: (String) -> Unit,
    onPickImage: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val chatPhase = rememberChatMotionPhaseState(state.motionIntensity)
    val bubbleLayerState = rememberChatBubbleLayerState()
    val activeMessageIds = remember(state.messages) { state.messages.map { it.id }.toSet() }
    SideEffect { bubbleLayerState.removeMissing(activeMessageIds) }
    val lastMessageId = state.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, modifier.fillMaxWidth(), GlassRole.Shell) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .clipToBounds()
        ) {
            RainbowChatGlassOverlay(quality = state.quality, motionIntensity = state.motionIntensity, modifier = Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ChatStatusV2(if (state.isSending) "正在接收" else "可上下滑动")
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    val lastActionableMessageId = remember(state.messages) {
                        state.messages.lastOrNull { isActionableCloudAssistantMessageV2(it) }?.id
                    }
                    ChatBubbleMaterialLayer(
                        layerState = bubbleLayerState,
                        listState = listState,
                        messages = state.messages,
                        phase = chatPhase.value,
                        motionIntensity = state.motionIntensity,
                        modifier = Modifier.matchParentSize()
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            AnimatedMessageBubbleV2(
                                message = message,
                                state = state,
                                chatPhase = chatPhase,
                                bubbleLayerState = bubbleLayerState,
                                showActions = message.id == lastActionableMessageId,
                                onCopyMessage = onCopyMessage,
                                onRetryMessage = onRetryMessage
                            )
                        }
                        item { StarterSuggestionsV2(state, onDraftCommand, onPickImage) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterSuggestionsV2(state: AssistantUiState, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    AnimatedVisibility(
        visible = state.messages.size <= 2,
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
private fun rememberChatMotionPhaseState(motionIntensity: Float): State<Float> {
    if (motionIntensity <= 0.02f) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "shared-chat-motion-clock")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(7200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "shared-chat-motion-phase"
    )
}

private fun phaseOffsetForMessage(id: String): Float {
    return ((id.hashCode() ushr 1) % 997) / 997f
}

private fun phaseSpeedForMessage(id: String): Float {
    val bucket = (id.hashCode() ushr 2) % 7
    return 0.82f + bucket * 0.055f
}

@Composable
private fun AnimatedMessageBubbleV2(
    message: ChatMessage,
    state: AssistantUiState,
    chatPhase: State<Float>,
    bubbleLayerState: ChatBubbleLayerState,
    showActions: Boolean,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val fromUser = message.role == MessageRole.User
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.48f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "message-light-bubble-q-appear"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        MessageBubbleV2(
            message = message,
            state = state,
            chatPhase = chatPhase,
            bubbleLayerState = bubbleLayerState,
            appear = appear,
            showActions = showActions,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage
        )
    }
}

@Composable
private fun MessageBubbleV2(
    message: ChatMessage,
    state: AssistantUiState,
    chatPhase: State<Float>,
    bubbleLayerState: ChatBubbleLayerState,
    appear: Float = 1f,
    showActions: Boolean,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val fromUser = message.role == MessageRole.User
    val sending = message.status == MessageStatus.Sending && !fromUser
    val bubbleRadius = if (fromUser) 26 else 28
    val phaseOffset = remember(message.id) { phaseOffsetForMessage(message.id) }
    val speedFactor = remember(message.id) { phaseSpeedForMessage(message.id) }
    val visual = chatBubbleVisualTransform(appear, fromUser)
    val rawText = messageText(message)
    val shouldTypewriter = !fromUser && !sending && showActions && message.status == MessageStatus.Sent && rawText.length > 24
    val typewriterState = rememberTypewriterTextStateV2(message.id, rawText, shouldTypewriter)
    val animatedRawText = typewriterState.first
    val typewriterFinished = typewriterState.second
    val typewriterActive = shouldTypewriter && !typewriterFinished
    val longReply = !fromUser && !sending && rawText.length >= 520
    var expanded by remember(message.id) { mutableStateOf(true) }
    val displayBaseText = if (sending) rawText else animatedRawText
    val displayText = if (longReply && !expanded) displayBaseText.take(420).trimEnd() + "…" else displayBaseText
    val contentAlpha by animateFloatAsState(
        targetValue = if (sending) 0.88f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "message-content-state-alpha"
    )
    SideEffect {
        bubbleLayerState.updateBubbleVisual(
            id = message.id,
            fromUser = fromUser,
            status = message.status,
            appear = appear,
            phaseOffset = phaseOffset,
            speedFactor = speedFactor,
            radiusDp = bubbleRadius
        )
    }
    DisposableEffect(message.id) {
        onDispose { bubbleLayerState.removeBubble(message.id) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (fromUser) 0.76f else 0.90f)
                .graphicsLayer {
                    alpha = visual.alpha
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(visual.originX, visual.originY)
                    scaleX = visual.scaleX
                    scaleY = visual.scaleY
                    translationX = visual.translationX
                    translationY = visual.translationY
                }
                .clip(RoundedCornerShape(bubbleRadius.dp))
        ) {
            Column(
                Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize(animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sending) {
                    StreamingAssistantContentV2(
                        message = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                } else {
                    GeneratingMessageContentV2(
                        text = displayText,
                        color = messageTextColor(message, fromUser),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium,
                        active = typewriterActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                    if (typewriterActive) {
                        TypewriterTrailV2()
                    }
                    if (longReply && typewriterFinished) {
                        LongReplyToggleV2(expanded = expanded) { expanded = !expanded }
                    }
                }
                if (!fromUser) MessageBadgeV2(message)
                if (showActions && !fromUser && !sending && typewriterFinished) {
                    MessageActionsV2(
                        message = message,
                        onCopyMessage = onCopyMessage,
                        onRetryMessage = onRetryMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantContentV2(message: ChatMessage, modifier: Modifier = Modifier) {
    val text = messageText(message)
    val hasLiveText = hasStreamingLiveTextV2(text)
    val progressLabel = rememberCloudProgressLabelV2(message.id, hasLiveText)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasLiveText) {
            RichMessageContent(
                text = text,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SweepingProgressTextV2(progressLabel, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold)
                ThinkingDotsV2(size = 4, color = Color(0xFF8DF9EA).copy(alpha = 0.76f))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SweepingProgressTextV2(progressLabel, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                ThinkingDotsV2(size = 7, color = Color(0xFF8DF9EA).copy(alpha = 0.92f))
            }
        }
    }
}

@Composable
private fun SweepingProgressTextV2(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight
) {
    val transition = rememberInfiniteTransition(label = "progress-label-sweep")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1760, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "progress-label-sweep-phase"
    )
    val startX = phase * 340f - 210f
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.44f),
            Color.White.copy(alpha = 0.72f),
            Color(0xFF8DF9EA).copy(alpha = 0.92f),
            Color.White.copy(alpha = 0.56f),
            Color.White.copy(alpha = 0.36f)
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + 190f, 0f)
    )
    Text(
        text = text,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun rememberCloudProgressLabelV2(messageId: String, hasLiveText: Boolean): String {
    var stage by remember(messageId) { mutableStateOf(0) }
    LaunchedEffect(messageId, hasLiveText) {
        if (hasLiveText) {
            stage = 3
            return@LaunchedEffect
        }
        stage = 0
        delay(760)
        stage = 1
        delay(1320)
        stage = 2
    }
    return if (hasLiveText) {
        "正在接收"
    } else {
        when (stage) {
            0 -> "正在连接云端"
            1 -> "云端处理中"
            else -> "正在整理回复"
        }
    }
}

@Composable
private fun rememberTypewriterTextStateV2(messageId: String, text: String, enabled: Boolean): Pair<String, Boolean> {
    var visibleCount by remember(messageId, text) { mutableStateOf(if (enabled) 0 else text.length) }
    LaunchedEffect(messageId, text, enabled) {
        if (!enabled) {
            visibleCount = text.length
            return@LaunchedEffect
        }
        visibleCount = 0
        delay(90)
        while (visibleCount < text.length) {
            visibleCount = (visibleCount + typewriterStepV2(text.length, visibleCount)).coerceAtMost(text.length)
            delay(typewriterDelayV2(text.length, visibleCount))
        }
    }
    val safeCount = visibleCount.coerceIn(0, text.length)
    return text.take(safeCount) to (safeCount >= text.length)
}

private fun typewriterStepV2(total: Int, index: Int): Int = when {
    total > 1600 -> 44
    total > 800 -> 34
    total > 320 -> 24
    index < 120 -> 12
    else -> 16
}

private fun typewriterDelayV2(total: Int, index: Int): Long = when {
    index < 120 -> 96L
    total > 800 -> 82L
    total > 320 -> 88L
    else -> 104L
}

@Composable
private fun GeneratingMessageContentV2(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    if (!active) {
        RichMessageContent(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "message-wide-fade-tail")
    val breath by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(720, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "message-wide-fade-tail-alpha"
    )
    val tailSize = 58.coerceAtMost(text.length)
    val stableText = text.dropLast(tailSize)
    val fadingText = text.takeLast(tailSize)
    val annotated = buildAnnotatedString {
        append(stableText)
        withStyle(SpanStyle(color = color.copy(alpha = 0.32f + breath * 0.58f))) {
            append(fadingText)
        }
    }
    Text(
        text = annotated,
        color = color.copy(alpha = 0.82f),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
private fun TypewriterTrailV2() {
    val transition = rememberInfiniteTransition(label = "assistant-typewriter-trail")
    val breath by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1120, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "assistant-typewriter-trail-alpha"
    )
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.26f + breath * 0.15f },
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "正在生成",
            color = Color(0xFF8DF9EA).copy(alpha = 0.50f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(Modifier.size(5.dp))
        ThinkingDotsV2(size = 3, color = Color(0xFF8DF9EA).copy(alpha = 0.58f))
    }
}

@Composable
private fun LongReplyToggleV2(expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (expanded) "收起长回复 ︿" else "展开全文 ﹀",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

private fun hasStreamingLiveTextV2(text: String): Boolean {
    return text.isNotBlank() && !isThinkingPlaceholderV2(text)
}

private fun isThinkingPlaceholderV2(text: String): Boolean {
    val clean = text.trim()
    return clean == "正在思考…" || clean == "正在重新生成…" || clean == "正在思考" || clean == "正在重新生成"
}

@Composable
private fun MessageActionsV2(
    message: ChatMessage,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextActionV2("复制") { onCopyMessage(messageText(message)) }
        Spacer(Modifier.size(12.dp))
        TextActionV2(
            text = "重试",
            color = if (message.status == MessageStatus.Failed) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.50f)
        ) { onRetryMessage(message.id) }
    }
}

@Composable
private fun TextActionV2(
    text: String,
    color: Color = Color.White.copy(alpha = 0.50f),
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun isActionableCloudAssistantMessageV2(message: ChatMessage): Boolean {
    if (message.role != MessageRole.Assistant) return false
    if (message.status == MessageStatus.Sending) return false
    return when (message.source) {
        null, "", "local", "local_ledger", "local_mobile" -> false
        else -> true
    }
}

@Composable
private fun MessageBadgeV2(message: ChatMessage) {
    val text = messageBadgeTextV2(message) ?: return
    val badgeColor = badgeColorV2(message)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(badgeColor.copy(alpha = 0.82f)))
        Text(text, color = badgeColor.copy(alpha = 0.70f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ComposerBarV2(
    state: AssistantUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGenerating: () -> Unit,
    onPickImage: () -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val inputMethodManager = remember(view) {
        view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
    }
    val keyboardSendAction = if (state.isSending) ({}) else {
        {
            if (state.composerText.isNotBlank()) {
                inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
            }
            onSend()
        }
    }
    val buttonAction = if (state.isSending) onStopGenerating else keyboardSendAction
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RoundIconButtonV2("+", state, size = 48, onClick = onPickImage)
        ComposerInputV2(state, state.composerText, onComposerChange, keyboardSendAction, Modifier.weight(1f), "和我说点什么...")
        SendButtonV2(state, onClick = buttonAction)
    }
}

@Composable
private fun ComposerInputV2(state: AssistantUiState, text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier, placeholder: String) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(48.dp), GlassRole.Card) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = text,
                onValueChange = { if (!state.isSending) onTextChange(it) },
                singleLine = true,
                enabled = true,
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
            Text(if (state.isSending) "Ⅱ" else "↑", color = Color.White, fontSize = if (state.isSending) 19.sp else 22.sp, fontWeight = FontWeight.Black)
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
                    .thinkingPearlSurface(color = color, wave = wave, index = index)
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
    val pulse by transition.animateFloat(
        initialValue = 0.76f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(animation = tween(860, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse-dot-value-v2"
    )
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
    "qwen", "qwen_chat", "qwen_ai", "dashscope_qwen" -> "Qwen"
    "kimi", "nvidia_chat" -> "Qwen"
    "deepseek", "deepseek_chat", "deepseek_v4" -> "DeepSeek"
    "gpt_oss", "nvidia_gpt_oss" -> "GPT OSS"
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
    MessageStatus.Sent -> {
        val signal = modelSignalV2(message)
        when {
            message.source in listOf("web_search_tool", "tavily_web_search", "tavily_ai_summary") -> Color(0xFF8DF9EA)
            message.source in listOf("cloud_fetch_failed", "cloud_error_normalized") -> Color(0xFFFFB4B4)
            signal.contains("qwen") || signal.contains("kimi") || signal.contains("千问") -> Color(0xFF8DF9EA)
            signal.contains("deepseek") -> Color(0xFF22D3EE)
            signal.contains("gemini") -> Color(0xFF6AE4FF)
            signal.contains("mistral") -> Color(0xFFFFC247)
            signal.contains("worker") || signal.contains("llama") -> Color(0xFFFF7B5C)
            signal.contains("gpt") || signal.contains("oss") -> Color(0xFF34D399)
            else -> Color.White
        }
    }
}

private fun modelSignalV2(message: ChatMessage): String {
    return listOfNotNull(message.model, message.modelLabel, message.source)
        .joinToString(" ")
        .lowercase()
}
