package com.yuchen.ailedger.ui

import androidx.compose.runtime.Immutable
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
    val agentEnabled: Boolean,
    val isSending: Boolean
)

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
)
