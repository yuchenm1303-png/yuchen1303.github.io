package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AssistantUiState
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

@Immutable
private data class ModelPanelUiState(
    val selectedModel: ChatModel,
    val selectedModelLabel: String,
    val onlineEnabled: Boolean,
    val isSending: Boolean,
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float,
    val modelCardGlassStyle: com.yuchen.ailedger.model.ModelCardGlassStyle
)

private var assistantHomeEntrancePlayedInProcess = false

@Composable
fun AssistantScreenV2(
    state: AssistantUiState,
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
    onRetryMessage: (String) -> Unit
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
        ModelPanelUiState(
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
    val modelSelectorState = rememberModelSelectorLegacyState(state, modelPanelState)

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
            delayMs = 46,
            modifier = Modifier.zIndex(4f),
            initialOffsetY = 16,
            initialScale = 0.965f
        ) {
            ModelAndNetworkPanel(
                state = modelPanelState,
                selectorState = modelSelectorState,
                expanded = modelPanelExpanded,
                panelHeight = modelPanelVisualHeight,
                layoutHeight = collapsedPanelHeight,
                onExpandedChange = { modelPanelExpanded = it },
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
            CompositionLocalProvider(LocalOpenGLGlassSurfaceAnchor provides shellAnchor) {
                ChatPanelV2(
                    state = chatPanelState,
                    modifier = Modifier.fillMaxWidth(),
                    viewportTopInset = modelExpandDelta,
                    onDraftCommand = onDraftCommand,
                    onPickImage = onPickImage,
                    onCopyMessage = onCopyMessage,
                    onRetryMessage = onRetryMessage
                )
            }
        }

        AssistantEntrance(delayMs = 138, initialOffsetY = 18, initialScale = 0.965f) {
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
    var visible by remember { mutableStateOf(assistantHomeEntrancePlayedInProcess) }
    LaunchedEffect(Unit) {
        if (!assistantHomeEntrancePlayedInProcess) {
            if (delayMs > 0L) delay(delayMs)
            assistantHomeEntrancePlayedInProcess = true
        }
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY },
        exit = fadeOut(tween(100))
    ) { content() }
}

@Composable
private fun AssistantHeroV2() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        MaterialText("AI ASSISTANT", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        MaterialText("AI 助手", color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black)
        MaterialText("直接说需求，我来帮你拆成动作。", color = Color.White.copy(alpha = 0.54f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModelAndNetworkPanel(
    state: ModelPanelUiState,
    selectorState: AssistantUiState,
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
                state = selectorState,
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
                state = selectorState,
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
private fun rememberModelSelectorLegacyState(
    source: AssistantUiState,
    modelState: ModelPanelUiState
): AssistantUiState {
    return remember(
        modelState.selectedModel,
        modelState.selectedModelLabel,
        modelState.onlineEnabled,
        modelState.isSending,
        modelState.quality,
        modelState.glassIntensity,
        modelState.motionIntensity,
        modelState.modelCardGlassStyle
    ) {
        source.copy(
            selectedModel = modelState.selectedModel,
            selectedModelLabel = modelState.selectedModelLabel,
            onlineEnabled = modelState.onlineEnabled,
            isSending = modelState.isSending,
            quality = modelState.quality,
            glassIntensity = modelState.glassIntensity,
            motionIntensity = modelState.motionIntensity,
            modelCardGlassStyle = modelState.modelCardGlassStyle
        )
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
    onDraftCommand: (String) -> Unit,
    onPickImage: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val chatPhase = rememberChatMotionPhaseState(state.motionIntensity)
    val bubbleLayerState = rememberChatBubbleLayerState()
    val messages = state.messages
    val lastMessage = messages.lastOrNull()
    val lastMessageId = lastMessage?.id
    val lastMessageStatus = lastMessage?.status
    val lastMessageIndex = messages.lastIndex
    val lastActionableMessageId = remember(messages) {
        messages.lastOrNull { isActionableCloudAssistantMessageV2(it) }?.id
    }
    val statusText = when {
        state.isSending -> "正在接收"
        state.hasComposerAttachment -> "附件待发送"
        else -> "可上下滑动"
    }

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
                    MaterialText("对话", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    ChatStatusV2(statusText)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    ChatBubbleMaterialLayer(
                        layerState = bubbleLayerState,
                        listState = listState,
                        messages = messages,
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
                        items(messages, key = { it.id }) { message ->
                            MessageBubbleV2(
                                message = message,
                                chatPhase = chatPhase,
                                bubbleLayerState = bubbleLayerState,
                                showActions = message.id == lastActionableMessageId,
                                onCopyMessage = onCopyMessage,
                                onRetryMessage = onRetryMessage
                            )
                        }
                        item {
                            StarterSuggestionsV2(
                                visible = messages.size <= 2,
                                quality = state.quality,
                                glassIntensity = state.glassIntensity,
                                motionIntensity = state.motionIntensity,
                                onDraftCommand = onDraftCommand,
                                onPickImage = onPickImage
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterSuggestionsV2(
    visible: Boolean,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onDraftCommand: (String) -> Unit,
    onPickImage: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.72f)) { it / 2 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 2 }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 2.dp)) {
            MaterialText("可以这样说", color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                SuggestionButtonV2("记一笔", quality, glassIntensity, motionIntensity, Modifier.weight(1f)) {
                    onDraftCommand("记一笔 午饭 18 元")
                }
                SuggestionButtonV2("设提醒", quality, glassIntensity, motionIntensity, Modifier.weight(1f)) {
                    onDraftCommand("今晚 9 点半提醒我复盘")
                }
                SuggestionButtonV2("上传图片", quality, glassIntensity, motionIntensity, Modifier.weight(1f), onClick = onPickImage)
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

private fun phaseOffsetForMessage(id: String): Float = ((id.hashCode() ushr 1) % 997) / 997f

private fun phaseSpeedForMessage(id: String): Float {
    val bucket = (id.hashCode() ushr 2) % 7
    return 0.82f + bucket * 0.055f
}

@Composable
private fun MessageBubbleV2(
    message: ChatMessage,
    chatPhase: State<Float>,
    bubbleLayerState: ChatBubbleLayerState,
    showActions: Boolean,
    onCopyMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val fromUser = message.role == MessageRole.User
    val sending = message.status == MessageStatus.Sending && !fromUser
    val bubbleRadius = if (fromUser) 26 else 28
    val displayText = remember(message.id, message.text, message.status, message.errorText) { messageText(message) }
    val textColor = remember(message.id, message.status, fromUser) { messageTextColor(message, fromUser) }
    val phaseOffset = remember(message.id) { phaseOffsetForMessage(message.id) }
    val speedFactor = remember(message.id) { phaseSpeedForMessage(message.id) }
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow),
        label = "message-light-bubble-appear"
    )
    val visual = chatBubbleVisualTransform(appear, fromUser)

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        MaterialText(displayText, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        ThinkingDotsV2(size = 5, color = Color.White.copy(alpha = 0.62f))
                    }
                } else {
                    RichMessageContent(
                        text = displayText,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium
                    )
                }

                if (message.attachments.isNotEmpty()) {
                    MessageAttachmentListV2(message.attachments)
                }

               if (!fromUser) {
    MessageBadgeV2(message)
} else if (message.hasImageAttachments) {
    MessageUserAttachmentBadgeV2(message)
}

if (!fromUser && !sending && message.status == MessageStatus.Sent) {
    MessageDataCards(message)
}

if (showActions && !fromUser && !sending) {
    MessageActionsV2(message, onCopyMessage, onRetryMessage)
}
            }
        }
    }
}

@Composable
private fun MessageAttachmentListV2(attachments: List<ChatAttachment>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.take(3).forEach { attachment ->
            val dimensions = if (attachment.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
            val size = attachment.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已压缩"
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
                    MaterialText("IMG", color = Color(0xFF8DF9EA).copy(alpha = 0.88f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    MaterialText("视觉附件", color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    MaterialText("$dimensions · $size", color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MessageBadgeV2(message: ChatMessage) {
    val text = messageBadgeTextV2(message) ?: return
    val badgeColor = badgeColorV2(message)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(badgeColor.copy(alpha = 0.82f)))
        MaterialText(text, color = badgeColor.copy(alpha = 0.70f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageUserAttachmentBadgeV2(message: ChatMessage) {
    val attachment = message.attachments.firstOrNull()
    val dimensions = if (attachment?.width != null && attachment.height != null) "${attachment.width}×${attachment.height}" else "图片"
    val size = attachment?.sizeBytes?.let { "${max(1, it / 1024)} KB" } ?: "已发送"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF8DF9EA).copy(alpha = 0.82f)))
        MaterialText("视觉附件 · $dimensions · $size", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageActionsV2(message: ChatMessage, onCopyMessage: (String) -> Unit, onRetryMessage: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextActionV2("复制") { onCopyMessage(messageText(message)) }
        Spacer(Modifier.size(12.dp))
        TextActionV2(
            text = "重试",
            color = if (message.status == MessageStatus.Failed) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.50f)
        ) { onRetryMessage(message.id) }
    }
}

@Composable
private fun TextActionV2(text: String, color: Color = Color.White.copy(alpha = 0.50f), onClick: () -> Unit) {
    MaterialText(
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

    val keyboardSendAction = if (!state.canSubmit) ({}) else {
        {
            if (state.text.isNotBlank()) {
                inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
            }
            onSend()
        }
    }
    val buttonAction = if (state.isSending) onStopGenerating else keyboardSendAction

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
                if (state.text.isBlank()) {
                    MaterialText(state.placeholder, color = Color.White.copy(alpha = 0.42f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val statusText = when (attachment.status) {
        ComposerAttachmentStatus.Preparing -> "准备中"
        ComposerAttachmentStatus.Uploading -> "上传中"
        ComposerAttachmentStatus.Ready -> "已就绪"
        ComposerAttachmentStatus.Failed -> "失败"
    }
    val dimensions = if (attachment.width != null && attachment.height != null) {
        "${attachment.width}×${attachment.height}"
    } else {
        "图片"
    }
    val sizeText = attachment.sizeBytes?.let { "${max(1, it / 1024)}KB" } ?: "${(progress * 100).toInt()}%"
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
                MaterialText(
                    text = "IMG",
                    color = if (attachment.status == ComposerAttachmentStatus.Failed) Color(0xFFFFB4B4) else Color(0xFF8DF9EA),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                MaterialText(
                    text = "$statusText · $dimensions · $sizeText",
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
            MaterialText(
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
            MaterialText(text, color = Color.White.copy(alpha = 0.92f), fontSize = if (text == "+") 25.sp else 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SuggestionButtonV2(
    text: String,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PressableGlass(quality, glassIntensity * 0.92f, motionIntensity, 20, modifier.height(38.dp), GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MaterialText(text, color = Color.White.copy(alpha = 0.84f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun ChatStatusV2(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MaterialText(text, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        translationY = -5.6f * wave
                        alpha = 0.54f + 0.46f * wave
                        scaleX = 0.76f + 0.42f * wave
                        scaleY = 0.76f + 0.42f * wave
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
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
