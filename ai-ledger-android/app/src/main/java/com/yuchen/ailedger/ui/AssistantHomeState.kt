package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ChatModel
import com.yuchen.ailedger.model.ComposerAttachment
import com.yuchen.ailedger.model.ModelCardGlassStyle
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality

@Immutable
internal data class AssistantHomeUiState(
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float,
    val rainbowPrismStyle: RainbowPrismStyle,
    val modelCardGlassStyle: ModelCardGlassStyle,
    val messages: List<ChatMessage>,
    val composerText: String,
    val composerAttachments: List<ComposerAttachment>,
    val selectedModel: ChatModel,
    val selectedModelLabel: String,
    val onlineEnabled: Boolean,
    val isSending: Boolean
) {
    val modelSelectorState: ModelSelectorUiState
        get() = ModelSelectorUiState(
            selectedModel = selectedModel,
            selectedModelLabel = selectedModelLabel,
            onlineEnabled = onlineEnabled,
            isSending = isSending,
            quality = quality,
            glassIntensity = glassIntensity,
            motionIntensity = motionIntensity,
            modelCardGlassStyle = modelCardGlassStyle
        )

    fun toVisualAssistantUiState(): AssistantUiState = AssistantUiState(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        rainbowPrismStyle = rainbowPrismStyle,
        modelCardGlassStyle = modelCardGlassStyle,
        messages = messages,
        composerText = composerText,
        composerAttachments = composerAttachments,
        selectedModel = selectedModel,
        selectedModelLabel = selectedModelLabel,
        onlineEnabled = onlineEnabled,
        isSending = isSending
    )
}

@Immutable
internal data class ModelSelectorUiState(
    val selectedModel: ChatModel,
    val selectedModelLabel: String,
    val onlineEnabled: Boolean,
    val isSending: Boolean,
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float,
    val modelCardGlassStyle: ModelCardGlassStyle
) {
    fun toVisualAssistantUiState(): AssistantUiState = AssistantUiState(
        selectedModel = selectedModel,
        selectedModelLabel = selectedModelLabel,
        onlineEnabled = onlineEnabled,
        isSending = isSending,
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        modelCardGlassStyle = modelCardGlassStyle
    )
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
    onRetryMessage: (String) -> Unit
) {
    val visualState = remember(state) { state.toVisualAssistantUiState() }
    AssistantScreenV2(
        state = visualState,
        bottomPadding = bottomPadding,
        onComposerChange = onComposerChange,
        onSend = onSend,
        onStopGenerating = onStopGenerating,
        onDraftCommand = onDraftCommand,
        onModelSelected = onModelSelected,
        onPickImage = onPickImage,
        onOpenTools = onOpenTools,
        onOpenSettings = onOpenSettings,
        onToggleOnline = onToggleOnline,
        onCopyMessage = onCopyMessage,
        onRetryMessage = onRetryMessage
    )
}
