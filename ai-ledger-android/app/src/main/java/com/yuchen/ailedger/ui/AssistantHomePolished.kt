// AI_LEDGER_SOURCE_SEGMENT_1_BEGIN
package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.data.createWelcomeMessage
import com.yuchen.ailedger.model.ChatAttachment
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.ComposerAttachment
import com.yuchen.ailedger.model.ComposerAttachmentStatus
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.LocalOpenGLGlassSurfaceAnchor
import com.yuchen.ailedger.ui.gl.OpenGLGlassSurfaceAnchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

@Immutable
private data class ChatPanelUiState(
    val messages: List<ChatMessage>,
    val isSending: Boolean,
    val hasComposerAttachment: Boolean,
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float
)

@Immutable
private data class ComposerBarUiState(
    val text: String,
    val attachment: ComposerAttachment?,
    val preparingAttachment: Boolean,
    val hasReadyAttachment: Boolean,
    val isSending: Boolean,
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float
) {
    val canSubmit: Boolean
        get() = !isSending && !preparingAttachment && (text.isNotBlank() || hasReadyAttachment)

    val placeholder: String
        get() = when {
            preparingAttachment -> "正在准备..."
            attachment?.isReady == true -> "输入配文..."
            else -> "和我说点什么..."
        }
}

@Stable
internal class AssistantHomeMotionClock(
    private val frameNanos: State<Long>,
    private val motionIntensity: Float
) {
    fun phase(periodMs: Long, offset: Float = 0f): Float {
        if (motionIntensity <= 0.02f) return 0f
        val periodNanos = (periodMs.coerceAtLeast(1L) * 1_000_000L).coerceAtLeast(1L)
        val base = (frameNanos.value % periodNanos).toFloat() / periodNanos.toFloat()
        val shifted = base + offset
        return shifted - shifted.toInt()
    }

    fun pingPong(periodMs: Long, offset: Float = 0f): Float {
        val phase = phase(periodMs, offset)
        return if (phase <= 0.5f) phase * 2f else (1f - phase) * 2f
    }
}

@Composable
private fun rememberAssistantHomeMotionClock(
    motionIntensity: Float,
    active: Boolean
): AssistantHomeMotionClock {
    val frameNanos = rememberAssistantHomeFrameNanos(motionIntensity, active)
    return remember(frameNanos, motionIntensity) {
        AssistantHomeMotionClock(frameNanos = frameNanos, motionIntensity = motionIntensity)
    }
}

@Composable
private fun rememberAssistantHomeFrameNanos(
    motionIntensity: Float,
    active: Boolean
): State<Long> {
    val frameNanos = remember { mutableStateOf(0L) }
    LaunchedEffect(motionIntensity, active) {
        if (!active || motionIntensity <= 0.02f) return@LaunchedEffect
        PerformanceRuntimeMetrics.recordAssistantClockStart()
        try {
            while (true) {
                withFrameNanos { nanos ->
                    frameNanos.value = nanos
                    PerformanceRuntimeMetrics.recordAssistantClockTick()
                }
            }
        } finally {
            PerformanceRuntimeMetrics.recordAssistantClockStop()
        }
    }
    return frameNanos
}

@Composable
internal fun AssistantScreenV2(
    state: AssistantHomeUiState,
    bottomPadding: Dp = 68.dp,
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
    onRetryMessage: (String) -> Unit,
    onClearMessages: () -> Unit
) {
    var modelPanelExpanded by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }
    var modelAnchorHeld by remember { mutableStateOf(false) }
    var keyboardAnchorHeld by remember { mutableStateOf(false) }

    val collapsedPanelHeight = 58.dp
    val modelRowCount = ((ChatModel.entries.size + 1) / 2).coerceAtLeast(1)
    val expandedPanelHeight = (64 + 74 * (modelRowCount - 1)).dp
    val modelPanelVisualHeight by animateDpAsState(
        targetValue = if (modelPanelExpanded) expandedPanelHeight else collapsedPanelHeight,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "model-stack-panel-height"
    )
    val modelExpandDelta = if (modelPanelVisualHeight > collapsedPanelHeight) {
        modelPanelVisualHeight - collapsedPanelHeight
    } else {
        0.dp
    }

    LaunchedEffect(modelPanelExpanded) {
        if (modelPanelExpanded) {
            modelAnchorHeld = true
        } else {
            delay(760L)
            if (!modelPanelExpanded) modelAnchorHeld = false
        }
    }

    LaunchedEffect(composerFocused) {
        if (composerFocused) {
            keyboardAnchorHeld = true
        } else {
            delay(760L)
            if (!composerFocused) keyboardAnchorHeld = false
        }
    }

    val shellAnchor = when {
        modelAnchorHeld && keyboardAnchorHeld -> OpenGLGlassSurfaceAnchor.Center
        modelAnchorHeld -> OpenGLGlassSurfaceAnchor.Bottom
        keyboardAnchorHeld -> OpenGLGlassSurfaceAnchor.Top
        else -> OpenGLGlassSurfaceAnchor.Center
    }

    val chatPanelState = remember(
        state.messages,
        state.isSending,
        state.composerAttachments,
        state.quality,
        state.glassIntensity,
        state.motionIntensity
    ) {
        ChatPanelUiState(
            messages = state.messages,
            isSending = state.isSending,
            hasComposerAttachment = state.composerAttachments.isNotEmpty(),
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity
        )
    }

    val composerBarState = remember(
        state.composerText,
        state.composerAttachments,
        state.isSending,
        state.quality,
        state.glassIntensity,
        state.motionIntensity
    ) {
        val latestAttachment = state.composerAttachments.lastOrNull()
        ComposerBarUiState(
            text = state.composerText,
            attachment = latestAttachment,
            preparingAttachment = state.composerAttachments.any {
                it.status == ComposerAttachmentStatus.Preparing || it.status == ComposerAttachmentStatus.Uploading
            },
            hasReadyAttachment = state.composerAttachments.any { it.isReady },
            isSending = state.isSending,
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity
        )
    }

    val modelPanelState = remember(
        state.selectedModel,
        state.selectedModelLabel,
        state.onlineEnabled,
        state.isSending,
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
        state.modelCardGlassStyle
    ) {
        ModelSelectorUiState(
            selectedModel = state.selectedModel,
            selectedModelLabel = state.selectedModelLabel,
            onlineEnabled = state.onlineEnabled,
            isSending = state.isSending,
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            modelCardGlassStyle = state.modelCardGlassStyle
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssistantEntrance(delayMs = 0, initialOffsetY = -10, initialScale = 0.98f) {
            AssistantHeroV2()
        }

        AssistantEntrance(
            delayMs = 110,
            modifier = Modifier.zIndex(4f),
            initialOffsetY = 16,
            initialScale = 0.965f
        ) {
            ModelAndNetworkPanel(
                state = modelPanelState,
                expanded = modelPanelExpanded,
                panelHeight = modelPanelVisualHeight,
                layoutHeight = collapsedPanelHeight,
                onExpandedChange = { modelPanelExpanded = it },
                onModelSelected = onModelSelected,
                onToggleOnline = onToggleOnline
            )
        }

        AssistantEntrance(
            delayMs = 220,
            modifier = Modifier.weight(1f),
            initialOffsetY = 30,
            initialScale = 0.955f
        ) {
            CompositionLocalProvider(LocalOpenGLGlassSurfaceAnchor provides shellAnchor) {
                ChatPanelV2(
                    state = chatPanelState,
                    modifier = Modifier.fillMaxWidth(),
                    viewportTopInset = modelExpandDelta,
                    onCopyMessage = onCopyMessage,
                    onRetryMessage = onRetryMessage,
                    onClearMessages = onClearMessages
                )
            }
        }

        AssistantEntrance(delayMs = 340, initialOffsetY = 18, initialScale = 0.965f) {
            ComposerBarV2(
                state = composerBarState,
                onComposerChange = onComposerChange,
                onSend = onSend,
                onStopGenerating = onStopGenerating,
                onPickImage = onPickImage,
                onComposerFocusChange = { composerFocused = it }
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
    val pageActive = LocalPageActive.current
    val pageLeaving = LocalPageLeaving.current
    val activationTick = LocalPageActivationTick.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(pageActive, pageLeaving, activationTick, delayMs) {
        if (pageActive) {
            visible = false
            yield()
            if (delayMs > 0L) delay(delayMs)
            visible = true
        } else {
            if (pageLeaving && delayMs > 0L) {
                delay((delayMs / 12L).coerceAtMost(26L))
            }
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY } +
            scaleIn(initialScale = initialScale, animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(92)) +
            slideOutVertically(tween(104)) { (-initialOffsetY / 3).coerceIn(-10, 10) } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(112))
    ) { content() }
}

@Composable
private fun AssistantHeroV2() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("AI ASSISTANT", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("AI 助手", color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
        Text("直接说需求，我来帮你拆成动作。", color = Color.White.copy(alpha = 0.54f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModelAndNetworkPanel(
    state: ModelSelectorUiState,
    expanded: Boolean,
    panelHeight: Dp,
    layoutHeight: Dp,
    onExpandedChange: (Boolean) -> Unit,
    onModelSelected: (ChatModel) -> Unit,
    onToggleOnline: () -> Unit
) {
    FixedHeightOverflowSlot(
        layoutHeight = layoutHeight,
        visualHeight = panelHeight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
        ) {
            UnifiedParentModelStackSelector(
                state = state,
                expanded = expanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight),
                onToggleExpanded = {
                    if (!state.isSending) onExpandedChange(!expanded)
                },
                onSelected = { model ->
                    if (!state.isSending) {
                        onModelSelected(model)
                        onExpandedChange(false)
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
}

@Composable
private fun FixedHeightOverflowSlot(
    layoutHeight: Dp,
    visualHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val layoutHeightPx = layoutHeight.roundToPx().coerceAtLeast(1)
        val visualHeightPx = visualHeight.roundToPx().coerceAtLeast(layoutHeightPx)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth.coerceAtLeast(1)
        val panelConstraints = constraints.copy(
            minWidth = width,
            maxWidth = width,
            minHeight = visualHeightPx,
            maxHeight = visualHeightPx
        )
        val placeables = measurables.map { it.measure(panelConstraints) }
        layout(width, layoutHeightPx) {
            placeables.forEach { placeable -> placeable.placeRelative(0, 0) }
        }
    }
}

@Composable
private fun ChatPanelV2(
    state: ChatPanelUiState,
    modifier: Modifier,
    viewportTopInset: Dp = 0.dp,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onClearMessages: () -> Unit
) {
    val listState = rememberLazyListState()
    val bubbleLayerState = rememberChatBubbleLayerState()
    var revealedMessageIds by remember { mutableStateOf(emptySet<String>()) }
    var streamedMessageIds by remember { mutableStateOf(emptySet<String>()) }
    var streamRevealCompletedMessageIds by remember { mutableStateOf(emptySet<String>()) }
    var collapsedLongReplyMessageIds by remember { mutableStateOf(emptySet<String>()) }
    val sourceMessages = state.messages
    val messages = remember(sourceMessages) {
        sourceMessages.ifEmpty {
            listOf(
                createWelcomeMessage(
                    id = "assistant-welcome-cleared-${System.nanoTime()}"
                )
            )
        }
    }
    val activeMessageIds = remember(messages) { messages.map { it.id }.toSet() }
    LaunchedEffect(activeMessageIds) {
        bubbleLayerState.removeMissing(activeMessageIds)
// AI_LEDGER_SOURCE_SEGMENT_1_END

// AI_LEDGER_SOURCE_SEGMENT_2_BEGIN
        if (revealedMessageIds.any { it !in activeMessageIds }) {
            revealedMessageIds = revealedMessageIds.intersect(activeMessageIds)
        }
        if (streamedMessageIds.any { it !in activeMessageIds }) {
            streamedMessageIds = streamedMessageIds.intersect(activeMessageIds)
        }
        if (streamRevealCompletedMessageIds.any { it !in activeMessageIds }) {
            streamRevealCompletedMessageIds = streamRevealCompletedMessageIds.intersect(activeMessageIds)
        }
        if (collapsedLongReplyMessageIds.any { it !in activeMessageIds }) {
            collapsedLongReplyMessageIds = collapsedLongReplyMessageIds.intersect(activeMessageIds)
        }
    }
    val liveStreamingMessageIds = remember(messages) {
        messages
            .filter { message ->
                message.role == MessageRole.Assistant &&
                    message.status == MessageStatus.Sending &&
                    hasStreamingLiveTextV2(messageText(message))
            }
            .map { it.id }
            .toSet()
    }
    LaunchedEffect(liveStreamingMessageIds) {
        if (liveStreamingMessageIds.isNotEmpty()) {
            streamedMessageIds = streamedMessageIds + liveStreamingMessageIds
        }
    }

    val lastMessage = messages.lastOrNull()
    val lastMessageId = lastMessage?.id
    val lastMessageStatus = lastMessage?.status
    val lastMessageIndex = messages.lastIndex
    val lastActionableMessage = remember(messages) {
        messages.lastOrNull { isActionableCloudAssistantMessageV2(it) }
    }
    val lastActionableMessageId = lastActionableMessage?.id
    val hasSendingAssistantMessage = remember(messages) {
        messages.any { it.role == MessageRole.Assistant && it.status == MessageStatus.Sending }
    }
    val pendingStreamMotion = streamedMessageIds.any { it !in streamRevealCompletedMessageIds }
    val pendingRevealMotion = lastActionableMessage?.let { message ->
        message.id !in revealedMessageIds &&
            message.id !in streamedMessageIds &&
            message.status == MessageStatus.Sent &&
            messageText(message).length > 24
    } == true
    val motionClockActive = state.motionIntensity > 0.02f && (
        state.isSending ||
            hasSendingAssistantMessage ||
            pendingStreamMotion ||
            pendingRevealMotion
        )
    val motionClock = rememberAssistantHomeMotionClock(
        motionIntensity = state.motionIntensity,
        active = motionClockActive
    )
    SideEffect { PerformanceRuntimeMetrics.recordAssistantComposition() }

    LaunchedEffect(lastMessageId, state.isSending) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (state.isSending || lastMessageStatus == MessageStatus.Sending) {
            listState.scrollToItem(lastMessageIndex)
        } else {
            listState.animateScrollToItem(lastMessageIndex)
        }
    }

    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = modifier.fillMaxWidth(),
        role = GlassRole.Shell,
        viewportTopInset = viewportTopInset
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .clipToBounds()
        ) {
            RainbowChatGlassOverlay(
                quality = state.quality,
                motionIntensity = state.motionIntensity,
                modifier = Modifier.matchParentSize()
            )
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ClearChatButtonV2(
                        enabled = sourceMessages.isNotEmpty(),
                        onClick = onClearMessages
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    ChatBubbleMaterialLayerHost(
                        layerState = bubbleLayerState,
                        listState = listState,
                        messages = messages,
                        motionClock = motionClock,
                        motionIntensity = state.motionIntensity,
                        modifier = Modifier.matchParentSize()
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            AnimatedMessageBubbleV2(
                                message = message,
                                bubbleLayerState = bubbleLayerState,
                                motionClock = motionClock,
                                showActions = message.id == lastActionableMessageId,
                                revealAlreadyPlayed = message.id in revealedMessageIds || message.id in streamedMessageIds,
                                wasStreamed = message.id in streamedMessageIds,
                                streamRevealAlreadyCompleted = message.id in streamRevealCompletedMessageIds,
                                longReplyExpanded = message.id !in collapsedLongReplyMessageIds,
                                onRevealCompleted = { id -> revealedMessageIds = revealedMessageIds + id },
                                onStreamRevealCompleted = { id -> streamRevealCompletedMessageIds = streamRevealCompletedMessageIds + id },
                                onLongReplyExpandedChange = { id, expanded ->
                                    collapsedLongReplyMessageIds = if (expanded) {
                                        collapsedLongReplyMessageIds - id
                                    } else {
                                        collapsedLongReplyMessageIds + id
                                    }
                                },
                                onCopyMessage = onCopyMessage,
                                onRetryMessage = onRetryMessage
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleMaterialLayerHost(
    layerState: ChatBubbleLayerState,
    listState: LazyListState,
    messages: List<ChatMessage>,
    motionClock: AssistantHomeMotionClock,
    motionIntensity: Float,
    modifier: Modifier = Modifier
) {
    ChatBubbleMaterialLayer(
        layerState = layerState,
        listState = listState,
        messages = messages,
        motionClock = motionClock,
        motionIntensity = motionIntensity,
        modifier = modifier
    )
}

private fun phaseOffsetForMessage(id: String): Float = ((id.hashCode() ushr 1) % 997) / 997f

private fun phaseSpeedForMessage(id: String): Float {
    val bucket = (id.hashCode() ushr 2) % 7
    return 0.82f + bucket * 0.055f
}

@Composable
private fun AnimatedMessageBubbleV2(
    message: ChatMessage,
    bubbleLayerState: ChatBubbleLayerState,
    motionClock: AssistantHomeMotionClock,
    showActions: Boolean,
    revealAlreadyPlayed: Boolean,
    wasStreamed: Boolean,
    streamRevealAlreadyCompleted: Boolean,
    longReplyExpanded: Boolean,
    onRevealCompleted: (String) -> Unit,
    onStreamRevealCompleted: (String) -> Unit,
    onLongReplyExpandedChange: (String, Boolean) -> Unit,
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
            bubbleLayerState = bubbleLayerState,
            motionClock = motionClock,
            appear = appear,
            showActions = showActions,
            revealAlreadyPlayed = revealAlreadyPlayed,
            wasStreamed = wasStreamed,
            streamRevealAlreadyCompleted = streamRevealAlreadyCompleted,
            longReplyExpanded = longReplyExpanded,
            onRevealCompleted = onRevealCompleted,
            onStreamRevealCompleted = onStreamRevealCompleted,
            onLongReplyExpandedChange = onLongReplyExpandedChange,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage
        )
    }
}

@Composable
private fun MessageBubbleV2(
    message: ChatMessage,
    bubbleLayerState: ChatBubbleLayerState,
    motionClock: AssistantHomeMotionClock,
    appear: Float = 1f,
    showActions: Boolean,
    revealAlreadyPlayed: Boolean,
    wasStreamed: Boolean,
    streamRevealAlreadyCompleted: Boolean,
    longReplyExpanded: Boolean,
    onRevealCompleted: (String) -> Unit,
    onStreamRevealCompleted: (String) -> Unit,
    onLongReplyExpandedChange: (String, Boolean) -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val fromUser = message.role == MessageRole.User
    val sending = message.status == MessageStatus.Sending && !fromUser
    val bubbleRadius = if (fromUser) 26 else 28
    val phaseOffset = remember(message.id) { phaseOffsetForMessage(message.id) }
    val speedFactor = remember(message.id) { phaseSpeedForMessage(message.id) }
    val visual = chatBubbleVisualTransform(appear, fromUser)

    val rawText = remember(message.id, message.text, message.status, message.errorText) { messageText(message) }
    val textColor = remember(message.id, message.status, fromUser) { messageTextColor(message, fromUser) }
    val hasLiveStreamingText = remember(rawText) { hasStreamingLiveTextV2(rawText) }
    val streamRevealShouldAnimate = !fromUser && hasLiveStreamingText && !streamRevealAlreadyCompleted && (sending || wasStreamed)
    val smoothStreamingState = rememberFluidStreamingTextStateV2(
        messageId = message.id,
        targetText = rawText,
        enabled = streamRevealShouldAnimate
    )
    val smoothStreamingFinished = streamRevealAlreadyCompleted || smoothStreamingState.finished
    val smoothStreamingActive = !fromUser && hasLiveStreamingText && (streamRevealShouldAnimate || streamRevealAlreadyCompleted || wasStreamed)
    val shouldReveal = !fromUser &&
        !sending &&
        !revealAlreadyPlayed &&
        showActions &&
        message.status == MessageStatus.Sent &&
        rawText.length > 24
    val revealState = rememberRevealTextStateV2(message.id, rawText, shouldReveal)
    val revealedText = revealState.first
    val baseRevealFinished = revealState.second
    val revealFinished = if (smoothStreamingActive) smoothStreamingFinished else baseRevealFinished
    val revealActive = shouldReveal && !baseRevealFinished

    LaunchedEffect(message.id, shouldReveal, baseRevealFinished) {
        if (shouldReveal && baseRevealFinished) onRevealCompleted(message.id)
    }
    LaunchedEffect(message.id, hasLiveStreamingText, streamRevealShouldAnimate, smoothStreamingState.finished) {
        if (hasLiveStreamingText && streamRevealShouldAnimate && smoothStreamingState.finished) {
            onStreamRevealCompleted(message.id)
        }
    }

    val longReply = !fromUser && !sending && rawText.length >= 520
    val expanded = !longReply || longReplyExpanded
    val displayBaseText = when {
        streamRevealShouldAnimate -> smoothStreamingState.text
        streamRevealAlreadyCompleted && hasLiveStreamingText -> rawText
        sending -> rawText
        else -> revealedText
    }
    val displayText = remember(displayBaseText, longReply, expanded) {
        if (longReply && !expanded) displayBaseText.take(420).trimEnd() + "…" else displayBaseText
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (sending) 0.88f else 1f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "message-content-state-alpha"
    )
    val thinkingSweepActive = sending && !fromUser && isThinkingSweepPlaceholderV2(rawText)
    val thinkingSweepStrength by animateFloatAsState(
        targetValue = if (thinkingSweepActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (sending) 680 else 300,
            easing = FastOutSlowInEasing
        ),
        label = "message-thinking-sweep-strength"
    )

    SideEffect {
        PerformanceRuntimeMetrics.recordMessageBubbleComposition()
        bubbleLayerState.updateBubbleVisual(
            id = message.id,
            fromUser = fromUser,
            status = message.status,
            appear = appear,
            phaseOffset = phaseOffset,
            speedFactor = speedFactor,
            radiusDp = bubbleRadius,
            thinkingSweepStrength = thinkingSweepStrength
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
                    transformOrigin = TransformOrigin(visual.originX, visual.originY)
                    scaleX = visual.scaleX
                    scaleY = visual.scaleY
                    translationX = visual.translationX
                    translationY = visual.translationY
                }
                .clip(RoundedCornerShape(bubbleRadius.dp))
        ) {
            Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (sending) {
                StreamingAssistantContentV2(
                    message = message,
                    motionClock = motionClock,
                    smoothState = if (streamRevealShouldAnimate) smoothStreamingState else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha }
                )
            } else {
                if (streamRevealShouldAnimate && !smoothStreamingFinished) {
                    StreamingLivePlainTextV2(
                        text = displayText,
                        revealHead = smoothStreamingState.revealHead,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium,
                        motionClock = motionClock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                } else {
                    GeneratingMessageContentV2(
                        text = displayText,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium,
                        active = revealActive,
                        motionClock = motionClock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                    )
                }
                if (revealActive) {
                    TypewriterTrailV2(motionClock)
                }
                if (longReply && revealFinished) {
                    LongReplyToggleV2(expanded = expanded) {
                        onLongReplyExpandedChange(message.id, !expanded)
                    }
                }
            }

            if (message.attachments.isNotEmpty()) {
                MessageAttachmentListV2(message.attachments)
            }

            if (!fromUser) {
                MessageBadgeV2(message)
            } else if (message.hasImageAttachments) {
                MessageUserAttachmentBadgeV2(message)
            }

            if (fromUser && rawText.isNotBlank()) {
                UserMessageActionsV2(copyText = rawText, onCopyMessage = onCopyMessage)
            }

            if (!fromUser && !sending && revealFinished && message.status == MessageStatus.Sent) {
                MessageDataCards(message)
            }

            if (showActions && !fromUser && !sending && revealFinished) {
                MessageActionsV2(message, copyText = rawText, onCopyMessage = onCopyMessage, onRetryMessage = onRetryMessage)
            }
        }
    }
    }
}

@Composable
private fun StreamingAssistantContentV2(
    message: ChatMessage,
    motionClock: AssistantHomeMotionClock,
    smoothState: FluidStreamingTextVisualStateV2? = null,
    modifier: Modifier = Modifier
) {
    val targetText = remember(message.id, message.text, message.status, message.errorText) { messageText(message) }
    val hasLiveText = remember(targetText) { hasStreamingLiveTextV2(targetText) }
    val displayText = smoothState?.text?.takeIf { it.isNotBlank() } ?: targetText
    val revealHead = smoothState?.revealHead ?: displayText.length.toFloat()
    val useFullRichStreaming = remember(targetText) { shouldUseFullRichStreamingV2(targetText) }
    val progressLabel = rememberCloudProgressLabelV2(message.id, hasLiveText)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hasLiveText) {
            if (useFullRichStreaming) {
// AI_LEDGER_SOURCE_SEGMENT_2_END

// AI_LEDGER_SOURCE_SEGMENT_3_BEGIN
                OptimizedRichMessageContent(
                    text = displayText,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                StreamingLivePlainTextV2(
                    text = displayText,
                    revealHead = revealHead,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    motionClock = motionClock,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SweepingProgressTextV2(
                    text = progressLabel,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    motionClock = motionClock
                )
                ThinkingDotsV2(size = 4, color = Color.White.copy(alpha = 0.62f), motionClock = motionClock)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SweepingProgressTextV2(
                    text = progressLabel,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    motionClock = motionClock
                )
                ThinkingDotsV2(size = 7, color = Color.White.copy(alpha = 0.66f), motionClock = motionClock)
            }
        }
    }
}

@Immutable
private data class FluidStreamingTextVisualStateV2(
    val text: String,
    val revealHead: Float,
    val finished: Boolean
)

@Composable
private fun StreamingLivePlainTextV2(
    text: String,
    revealHead: Float? = null,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    motionClock: AssistantHomeMotionClock,
    modifier: Modifier = Modifier
) {
    val phase = motionClock.phase(1480L)
    val head = remember(text, revealHead) {
        (revealHead ?: text.length.toFloat()).coerceIn(0f, text.length.toFloat())
    }
    val annotated = remember(text, color, head, phase) {
        buildStreamingDevelopingTextV2(
            text = text,
            revealHead = head,
            color = color,
            phase = phase
        )
    }
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
private fun rememberFluidStreamingTextStateV2(
    messageId: String,
    targetText: String,
    enabled: Boolean
): FluidStreamingTextVisualStateV2 {
    var revealHead by remember(messageId) { mutableStateOf(if (enabled) 0f else targetText.length.toFloat()) }

    LaunchedEffect(messageId, targetText, enabled) {
        if (!enabled) {
            revealHead = targetText.length.toFloat()
            return@LaunchedEffect
        }
        if (targetText.isBlank()) {
            revealHead = 0f
            return@LaunchedEffect
        }
        if (revealHead > targetText.length || (revealHead > 1f && !targetText.startsWith(targetText.take(revealHead.toInt().coerceAtMost(targetText.length))))) {
            revealHead = revealHead.coerceIn(0f, targetText.length.toFloat())
        }

        var lastFrameNanos = 0L
        while (revealHead < targetText.length - 0.01f) {
            val frameNanos = withFrameNanos { it }
            val dt = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.006f, 0.048f)
            }
            lastFrameNanos = frameNanos

            val backlog = targetText.length - revealHead
            val speed = fluidRevealCharsPerSecondV2(backlog)
            val pauseFactor = fluidPauseFactorV2(targetText, revealHead)
            val next = revealHead + speed * pauseFactor * dt
            revealHead = minOf(targetText.length.toFloat(), next)

            if (targetText.length - revealHead < 0.35f) {
                revealHead = targetText.length.toFloat()
            }
        }
    }

    val head = revealHead.coerceIn(0f, targetText.length.toFloat())
    val previewEnd = fluidPreviewEndV2(targetText, head)
    val textForLayout = remember(targetText, previewEnd) { targetText.take(previewEnd) }
    return FluidStreamingTextVisualStateV2(
        text = textForLayout,
        revealHead = head.coerceAtMost(textForLayout.length.toFloat()),
        finished = head >= targetText.length - 0.01f
    )
}

private fun fluidRevealCharsPerSecondV2(backlog: Float): Float = when {
    backlog >= 220f -> 190f
    backlog >= 120f -> 150f
    backlog >= 56f -> 118f
    backlog >= 20f -> 92f
    else -> 68f
}

private fun fluidPauseFactorV2(text: String, revealHead: Float): Float {
    val index = revealHead.toInt().coerceIn(0, text.length)
    if (index <= 0 || index > text.length) return 1f
    val last = text[index - 1]
    val local = revealHead - index.toFloat()
    val justCrossed = local in 0f..0.38f
    if (!justCrossed) return 1f
    return when (last) {
        '\n' -> 0.36f
        '。', '！', '？', '.', '!', '?' -> 0.48f
        '，', ',', '；', ';', '：', ':' -> 0.68f
        else -> 1f
    }
}

private fun fluidPreviewEndV2(text: String, revealHead: Float): Int {
    if (text.isBlank()) return 0
    val preview = when {
        text.length >= 1200 -> 20
        text.length >= 520 -> 18
        else -> 14
    }
    val base = revealHead.toInt() + if (revealHead > revealHead.toInt().toFloat()) 1 else 0
    return safeStreamingEndV2(text, (base + preview).coerceIn(1, text.length))
}

private fun buildStreamingDevelopingTextV2(
    text: String,
    revealHead: Float,
    color: Color,
    phase: Float
) = buildAnnotatedString {
    if (text.isEmpty()) return@buildAnnotatedString

    val head = revealHead.coerceIn(0f, text.length.toFloat())
    val stableEnd = safeStreamingEndV2(text, (head - 22f).toInt().coerceAtLeast(0))
    if (stableEnd > 0) {
        withStyle(SpanStyle(color = color.copy(alpha = 0.86f))) {
            append(text.substring(0, stableEnd))
        }
    }

    val softStart = stableEnd
    val softEnd = text.length
    val shimmerCenter = 0.34f + 0.32f * sin((phase * 2f * PI).toFloat())
    for (index in softStart until softEnd) {
        val distance = head - index.toFloat()
        val alpha = fluidGlyphAlphaV2(distance)
        if (alpha <= 0.006f) continue

        val glow = if (distance in -2f..10f) {
            (1f - kotlin.math.abs(distance - shimmerCenter * 8f) / 12f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val glyphColor = blendStreamingGlyphColorV2(color, alpha, glow)
        withStyle(SpanStyle(color = glyphColor)) {
            append(text[index])
        }
    }
}

private fun fluidGlyphAlphaV2(distanceFromHead: Float): Float {
    return when {
        distanceFromHead >= 14f -> 0.86f
        distanceFromHead >= 8f -> 0.76f + (distanceFromHead - 8f) / 6f * 0.10f
        distanceFromHead >= 0f -> 0.18f + distanceFromHead / 8f * 0.58f
        distanceFromHead >= -6f -> 0.055f + (distanceFromHead + 6f) / 6f * 0.125f
        distanceFromHead >= -14f -> 0.018f + (distanceFromHead + 14f) / 8f * 0.037f
        else -> 0f
    }.coerceIn(0f, 0.92f)
}

private fun blendStreamingGlyphColorV2(base: Color, alpha: Float, glow: Float): Color {
    val glowAlpha = (0.08f * glow).coerceIn(0f, 0.10f)
    return Color(
        red = (base.red * (1f - glowAlpha) + 1f * glowAlpha).coerceIn(0f, 1f),
        green = (base.green * (1f - glowAlpha) + 1f * glowAlpha).coerceIn(0f, 1f),
        blue = (base.blue * (1f - glowAlpha) + 1f * glowAlpha).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun safeStreamingEndV2(text: String, end: Int): Int {
    val safe = end.coerceIn(0, text.length)
    if (safe in 1 until text.length && Character.isHighSurrogate(text[safe - 1]) && Character.isLowSurrogate(text[safe])) {
        return safe + 1
    }
    return safe
}

private fun shouldUseFullRichStreamingV2(text: String): Boolean {
    if (text.length > 360) return false
    val clean = text.trim()
    if (clean.contains("$$") || clean.contains("\\(") || clean.contains("\\[") || clean.contains("```")) return true
    val lines = clean.lines()
    return lines.any { line ->
        val trimmed = line.trim()
        trimmed.length >= 5 && trimmed.startsWith("|") && trimmed.endsWith("|")
    }
}

@Composable
private fun SweepingProgressTextV2(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    motionClock: AssistantHomeMotionClock
) {
    val phase = motionClock.phase(1880L)
    val startX = phase * 420f - 260f
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.88f),
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.42f),
            Color.White.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.88f)
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + 260f, 0f)
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
private fun rememberRevealTextStateV2(messageId: String, text: String, enabled: Boolean): Pair<String, Boolean> {
    var visibleCount by remember(messageId, text) { mutableStateOf(if (enabled) 0 else text.length) }
    LaunchedEffect(messageId, text, enabled) {
        if (!enabled) {
            visibleCount = text.length
            return@LaunchedEffect
        }
        visibleCount = 0
        delay(120)
        while (visibleCount < text.length) {
            visibleCount = nextRevealBoundaryV2(text, visibleCount)
            delay(revealDelayV2(text.length, visibleCount))
        }
    }
    val safeCount = visibleCount.coerceIn(0, text.length)
    return text.take(safeCount) to (safeCount >= text.length)
}

private fun nextRevealBoundaryV2(text: String, current: Int): Int {
    if (current >= text.length) return text.length
    val baseStep = when {
        text.length > 1600 -> 180
        text.length > 900 -> 140
        text.length > 420 -> 96
        else -> 68
    }
    val softTarget = (current + baseStep).coerceAtMost(text.length)
    val punctuationWindowEnd = (softTarget + 44).coerceAtMost(text.length)
    val paragraph = text.indexOf('\n', startIndex = softTarget).takeIf { it in softTarget until punctuationWindowEnd }
    if (paragraph != null) return (paragraph + 1).coerceAtMost(text.length)
    val punctuation = findFirstRevealBreakV2(text, softTarget, punctuationWindowEnd)
    if (punctuation > 0) return punctuation.coerceAtMost(text.length)
    return softTarget
}

private fun findFirstRevealBreakV2(text: String, start: Int, end: Int): Int {
    val breaks = setOf('。', '！', '？', '；', '.', '!', '?', ';', '，', ',')
    for (index in start until end) {
        if (text[index] in breaks) return index + 1
    }
    return -1
}

private fun revealDelayV2(total: Int, index: Int): Long = when {
    total > 1600 -> 230L
    total > 900 -> 210L
    total > 420 -> 190L
    index < 120 -> 180L
    else -> 200L
}

@Composable
private fun GeneratingMessageContentV2(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    active: Boolean,
    motionClock: AssistantHomeMotionClock,
    modifier: Modifier = Modifier
) {
    if (!active) {
        OptimizedRichMessageContent(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }
    val phase = motionClock.phase(1450L)
    val tailSize = 150.coerceAtMost(text.length)
    val stableText = text.dropLast(tailSize)
    val fadingText = text.takeLast(tailSize)
    val diagonalStart = phase * 360f - 140f
    val tailBrush = Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.18f),
            color.copy(alpha = 0.38f),
            color.copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.90f),
            color.copy(alpha = 0.64f),
            color.copy(alpha = 0.34f)
        ),
        start = Offset(diagonalStart, 44f),
        end = Offset(diagonalStart + 260f, -20f)
    )
    val annotated = buildAnnotatedString {
        append(stableText)
        withStyle(SpanStyle(brush = tailBrush)) {
            append(fadingText)
        }
    }
    Text(
        text = annotated,
        color = color.copy(alpha = 0.84f),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
private fun TypewriterTrailV2(motionClock: AssistantHomeMotionClock) {
    val breath = 0.50f + FastOutSlowInEasing.transform(motionClock.pingPong(1280L)) * 0.50f
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 0.20f + breath * 0.12f },
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "正在生成",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(Modifier.size(5.dp))
        ThinkingDotsV2(size = 3, color = Color.White.copy(alpha = 0.42f), motionClock = motionClock)
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

private fun isThinkingSweepPlaceholderV2(text: String): Boolean {
    val clean = text.trim()
    return isThinkingPlaceholderV2(clean) ||
        clean == "正在理解视觉附件…" ||
        clean == "正在理解视觉附件" ||
        clean == "正在执行手机智能体任务…" ||
        clean == "正在执行手机智能体任务"
}

@Composable
private fun MessageAttachmentListV2(attachments: List<ChatAttachment>) {
    val visibleAttachments = remember(attachments) { attachments.take(3) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleAttachments.forEach { attachment ->
            val attachmentMeta = remember(attachment.width, attachment.height, attachment.sizeBytes) {
                val dimensions = if (attachment.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
                val size = attachment.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已压缩"
                "$dimensions · $size"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF8DF9EA).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("IMG", color = Color(0xFF8DF9EA).copy(alpha = 0.88f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("视觉附件", color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(attachmentMeta, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MessageBadgeV2(message: ChatMessage) {
    val text = remember(message.id, message.status, message.modelLabel, message.source, message.version) {
        messageBadgeTextV2(message)
    } ?: return
    val badgeColor = remember(message.id, message.status, message.source, message.model, message.modelLabel, message.hasImageAttachments) {
        badgeColorV2(message)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
// AI_LEDGER_SOURCE_SEGMENT_3_END

// AI_LEDGER_SOURCE_SEGMENT_4_BEGIN
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(badgeColor.copy(alpha = 0.82f)))
        Text(text, color = badgeColor.copy(alpha = 0.70f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageUserAttachmentBadgeV2(message: ChatMessage) {
    val attachment = message.attachments.firstOrNull()
    val attachmentMeta = remember(attachment?.width, attachment?.height, attachment?.sizeBytes) {
        val dimensions = if (attachment?.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
        val size = attachment?.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已发送"
        "$dimensions · $size"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF8DF9EA).copy(alpha = 0.82f)))
        Text("视觉附件 · $attachmentMeta", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageActionsV2(
    message: ChatMessage,
    copyText: String,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val retryColor = remember(message.status) {
        if (message.status == MessageStatus.Failed) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.50f)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextActionV2("复制") { onCopyMessage(copyText) }
        Spacer(Modifier.size(12.dp))
        TextActionV2(
            text = "重试",
            color = retryColor
        ) { onRetryMessage(message.id) }
    }
}

@Composable
private fun TextActionV2(text: String, color: Color = Color.White.copy(alpha = 0.50f), onClick: () -> Unit) {
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
private fun ComposerBarV2(
    state: ComposerBarUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGenerating: () -> Unit,
    onPickImage: () -> Unit,
    onComposerFocusChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    val inputMethodManager = remember(view) {
        view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
    }

    val canSubmit = state.canSubmit
    val textIsNotBlank = state.text.isNotBlank()
    val keyboardSendAction = remember(canSubmit, textIsNotBlank, inputMethodManager, view, onSend) {
        if (!canSubmit) {
            {}
        } else {
            {
                if (textIsNotBlank) {
                    inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
                }
                onSend()
            }
        }
    }
    val buttonAction = remember(state.isSending, onStopGenerating, keyboardSendAction) {
        if (state.isSending) onStopGenerating else keyboardSendAction
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        RoundIconButtonV2(
            text = "+",
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            size = 48,
            onClick = onPickImage
        )
        ComposerInputV2(
            state = state,
            onTextChange = onComposerChange,
            onSend = keyboardSendAction,
            onFocusChange = onComposerFocusChange,
            modifier = Modifier.weight(1f)
        )
        SendButtonV2(
            state = state,
            enabled = state.isSending || state.canSubmit,
            onClick = buttonAction
        )
    }
}

@Composable
private fun ComposerInputV2(
    state: ComposerBarUiState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    val attachment = state.attachment
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(48.dp), GlassRole.Card) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (attachment != null) {
                InlineComposerAttachmentChipV2(
                    attachment = attachment,
                    modifier = Modifier.weight(0.95f)
                )
            }
            Box(
                modifier = Modifier
                    .weight(if (attachment == null) 1f else 0.82f)
                    .height(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = state.text,
                    onValueChange = { if (!state.isSending) onTextChange(it) },
                    singleLine = true,
                    enabled = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color.White.copy(alpha = 0.86f)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) }
                )
                androidx.compose.animation.AnimatedVisibility(visible = state.text.isBlank(), enter = fadeIn(tween(160)), exit = fadeOut(tween(100))) {
                    Text(state.placeholder, color = Color.White.copy(alpha = 0.42f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun InlineComposerAttachmentChipV2(
    attachment: ComposerAttachment,
    modifier: Modifier = Modifier
) {
    val progress = attachment.progress.coerceIn(0f, 1f)
    val statusText = remember(attachment.status) {
        when (attachment.status) {
            ComposerAttachmentStatus.Preparing -> "准备中"
            ComposerAttachmentStatus.Uploading -> "上传中"
            ComposerAttachmentStatus.Ready -> "已就绪"
            ComposerAttachmentStatus.Failed -> "失败"
        }
    }
    val attachmentMeta = remember(attachment.width, attachment.height, attachment.sizeBytes, progress) {
        val dimensions = if (attachment.width != null && attachment.height != null) {
            "${attachment.width}×${attachment.height}"
        } else {
            "图片"
        }
        val sizeText = attachment.sizeBytes?.let { "${max(1, it / 1024)}KB" } ?: "${(progress * 100).toInt()}%"
        "$dimensions · $sizeText"
    }
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (attachment.status == ComposerAttachmentStatus.Failed) {
                    Color(0xFFFFB4B4).copy(alpha = 0.14f)
                } else {
                    Color(0xFF8DF9EA).copy(alpha = 0.13f)
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "IMG",
                    color = if (attachment.status == ComposerAttachmentStatus.Failed) Color(0xFFFFB4B4) else Color(0xFF8DF9EA),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "$statusText · $attachmentMeta",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceAtLeast(if (attachment.status == ComposerAttachmentStatus.Failed) 1f else 0.08f))
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (attachment.status == ComposerAttachmentStatus.Failed) {
                                Color(0xFFFFB4B4)
                            } else {
                                Color(0xFF8DF9EA).copy(alpha = 0.84f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun SendButtonV2(state: ComposerBarUiState, enabled: Boolean, onClick: () -> Unit) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (enabled) 1.02f else 0.82f,
        state.motionIntensity,
        999,
        Modifier.size(48.dp),
        if (enabled) GlassRole.Floating else GlassRole.Chip,
        onClick = { if (enabled) onClick() }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.isSending) "Ⅱ" else "↑",
                color = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                fontSize = if (state.isSending) 19.sp else 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun RoundIconButtonV2(
    text: String,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    size: Int = 40,
    onClick: () -> Unit
) {
    PressableGlass(quality, glassIntensity * 0.96f, motionIntensity, 999, Modifier.size(size.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = if (text == "+") 25.sp else 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ClearChatButtonV2(enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 0.64f else 0.26f
    Text(
        text = "清空",
        color = Color.White.copy(alpha = alpha),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.075f else 0.035f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        maxLines = 1
    )
}

@Composable
private fun UserMessageActionsV2(copyText: String, onCopyMessage: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextActionV2("复制") { onCopyMessage(copyText) }
    }
}

@Composable
private fun ChatStatusV2(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThinkingDotsV2(size: Int, color: Color, motionClock: AssistantHomeMotionClock) {
    val phase = motionClock.phase(1060L)
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
                    .assistantHomeThinkingPearlSurface(color = color, wave = wave, index = index)
            )
        }
    }
}

private fun Modifier.assistantHomeThinkingPearlSurface(color: Color, wave: Float, index: Int): Modifier {
    val highlightAlpha = (0.22f + wave * 0.26f).coerceIn(0f, 0.56f)
    val bodyAlpha = (0.48f + wave * 0.30f).coerceIn(0f, 0.88f)
    val shadowAlpha = (0.18f + index * 0.025f).coerceIn(0f, 0.30f)
    return clip(RoundedCornerShape(999.dp))
        .background(
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha),
                    color.copy(alpha = bodyAlpha),
                    color.copy(alpha = shadowAlpha)
                ),
                center = Offset(3.8f + index * 1.2f, 3.2f),
                radius = 18f + wave * 8f
            )
        )
}

@Composable
private fun PulseDotV2(active: Boolean, color: Color, motionClock: AssistantHomeMotionClock) {
    if (!active) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = 0.68f }.clip(RoundedCornerShape(999.dp)).background(color))
        return
    }
    val pulse = 0.76f + FastOutSlowInEasing.transform(motionClock.pingPong(860L)) * 0.46f
    Box(Modifier.size(8.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.96f }.clip(RoundedCornerShape(999.dp)).background(color))
}

private fun messageText(message: ChatMessage): String = when (message.status) {
    MessageStatus.Sending -> message.text.ifBlank { "正在思考…" }
    MessageStatus.Failed -> message.errorText ?: message.text.ifBlank { "云端请求失败，请稍后再试。" }
    MessageStatus.Sent -> stripInlineSourceTextV2(message.text)
}

private fun stripInlineSourceTextV2(text: String): String {
    val sourceBlockIndex = text.indexOf("\n\n联网来源")
    if (sourceBlockIndex >= 0) return text.substring(0, sourceBlockIndex).trimEnd()
    val sourceLineIndex = text.indexOf("\n联网来源")
    if (sourceLineIndex >= 0) return text.substring(0, sourceLineIndex).trimEnd()
    return text
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
    "qwen", "qwen_chat", "qwen_ai", "dashscope_qwen", "qwen_vision" -> "Qwen"
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
            message.hasImageAttachments -> Color(0xFF8DF9EA)
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
// AI_LEDGER_SOURCE_SEGMENT_4_END

