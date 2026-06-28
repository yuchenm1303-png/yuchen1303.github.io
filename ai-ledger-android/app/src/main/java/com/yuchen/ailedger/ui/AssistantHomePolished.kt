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
// AI_LEDGER_SOURCE_SEGMENT_2_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_2_END

// AI_LEDGER_SOURCE_SEGMENT_3_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_3_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_3_END

// AI_LEDGER_SOURCE_SEGMENT_4_BEGIN
// AI_LEDGER_SOURCE_SEGMENT_4_CONTENT
// AI_LEDGER_SOURCE_SEGMENT_4_END

