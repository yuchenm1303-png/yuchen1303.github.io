package com.yuchen.ailedger

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AssistantPreferencesStore
import com.yuchen.ailedger.data.AssistantRepository
import com.yuchen.ailedger.data.CustomBackgroundStore
import com.yuchen.ailedger.data.PreviewAssistantRepository
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.AiWorkerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AssistantViewModel(
    application: Application,
    private val repository: AssistantRepository,
    private val preferencesStore: AssistantPreferencesStore,
    private val aiWorkerClient: AiWorkerClient,
    private val customBackgroundStore: CustomBackgroundStore
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = PreviewAssistantRepository(),
        preferencesStore = AssistantPreferencesStore(application),
        aiWorkerClient = AiWorkerClient(),
        customBackgroundStore = CustomBackgroundStore(application)
    )

    var uiState by mutableStateOf(repository.initialState())
        private set

    init {
        viewModelScope.launch {
            preferencesStore.preferencesFlow.collect { preferences ->
                uiState = uiState.copy(
                    quality = preferences.quality,
                    showPreviewConversation = preferences.showPreviewConversation,
                    glassPreset = preferences.glassPreset,
                    backgroundTheme = preferences.backgroundTheme,
                    customBackgroundPath = preferences.customBackgroundPath,
                    glassIntensity = preferences.glassIntensity,
                    motionIntensity = preferences.motionIntensity
                )
            }
        }
    }

    val aiEndpoint: String
        get() = aiWorkerClient.endpoint

    fun selectTab(tab: AppTab) {
        uiState = uiState.copy(currentTab = tab)
    }

    fun updateComposer(text: String) {
        uiState = uiState.copy(composerText = text)
    }

    fun submitComposer() {
        val text = uiState.composerText.trim()
        if (text.isBlank()) return
        sendUserCommand(text)
    }

    fun sendUserCommand(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val userMessage = ChatMessage(
            id = "user-${System.currentTimeMillis()}",
            text = cleanText,
            role = MessageRole.User
        )
        val assistantMessage = ChatMessage(
            id = "assistant-${System.currentTimeMillis() + 1}",
            text = buildLocalAssistantReply(cleanText),
            role = MessageRole.Assistant
        )
        uiState = uiState.copy(
            messages = uiState.messages + userMessage + assistantMessage,
            composerText = ""
        )
    }

    fun insertCommandDraft(text: String) {
        uiState = uiState.copy(composerText = text)
    }

    fun cycleModel() {
        val next = when (uiState.selectedModelLabel) {
            "Gemini 2.5 Flash" -> "Gemini 2.5 Pro"
            "Gemini 2.5 Pro" -> "本地执行优先"
            else -> "Gemini 2.5 Flash"
        }
        uiState = uiState.copy(selectedModelLabel = next)
        appendAssistantNotice("已切换为 $next。")
    }

    fun onImagePickedForAssistant(uri: Uri?) {
        if (uri == null) return
        appendAssistantNotice("已选择图片。下一步可以把它接入识图接口，先在这里保留图片输入入口。")
    }

    fun appendAssistantNotice(text: String) {
        uiState = uiState.copy(
            messages = uiState.messages + ChatMessage(
                id = "assistant-${System.currentTimeMillis()}",
                text = text,
                role = MessageRole.Assistant
            )
        )
    }

    private fun buildLocalAssistantReply(command: String): String {
        val lower = command.lowercase()
        return when {
            command.contains("记") || command.contains("支出") || command.contains("收入") || command.contains("账") ->
                "收到。我先把它识别为记账任务，后面会继续接入金额、分类和账单保存。"
            command.contains("提醒") || command.contains("闹钟") ->
                "可以，我会把这句话当成提醒任务。你也可以点下方“设提醒”直接打开系统闹钟入口。"
            command.contains("导航") || command.contains("回家") || lower.contains("map") ->
                "我理解为导航任务。点“回家”会调用系统地图，后面可以在设置里配置家庭地址。"
            command.contains("图片") || command.contains("识图") || command.contains("照片") ->
                "可以识图。点右上角“识图”或输入框左侧加号，选择图片后就能继续处理。"
            else -> "我在。这个版本先把消息发送、滑动聊天和快捷动作跑通，后面再接入真正的 AI 回复。"
        }
    }

    fun selectQuality(quality: RenderQuality) {
        uiState = uiState.copy(quality = quality)
        viewModelScope.launch { preferencesStore.setRenderQuality(quality) }
    }

    fun setShowPreviewConversation(showPreviewConversation: Boolean) {
        uiState = uiState.copy(showPreviewConversation = showPreviewConversation)
        viewModelScope.launch { preferencesStore.setShowPreviewConversation(showPreviewConversation) }
    }

    fun setBackgroundTheme(backgroundTheme: BackgroundTheme) {
        uiState = uiState.copy(backgroundTheme = backgroundTheme, customBackgroundPath = null)
        viewModelScope.launch {
            preferencesStore.setBackgroundTheme(backgroundTheme)
            preferencesStore.setCustomBackgroundPath(null)
        }
    }

    fun importCustomBackground(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { customBackgroundStore.saveFromUri(uri) }
            uiState = uiState.copy(customBackgroundPath = path)
            preferencesStore.setCustomBackgroundPath(path)
        }
    }

    fun clearCustomBackground() {
        uiState = uiState.copy(customBackgroundPath = null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { customBackgroundStore.clearCustomBackground() }
            preferencesStore.setCustomBackgroundPath(null)
        }
    }

    fun setBackdropDebugParams(params: BackdropDebugParams) {
        uiState = uiState.copy(backdropParams = params)
    }

    fun updateBackdropDebugParams(block: (BackdropDebugParams) -> BackdropDebugParams) {
        setBackdropDebugParams(block(uiState.backdropParams))
    }

    fun setGlassBorderStyle(style: GlassBorderStyle) {
        uiState = uiState.copy(glassBorderStyle = style)
    }

    fun updateGlassBorderStyle(block: (GlassBorderStyle) -> GlassBorderStyle) {
        setGlassBorderStyle(block(uiState.glassBorderStyle))
    }

    fun setGlassIntensity(value: Float) {
        val clamped = value.coerceIn(0.6f, 1.4f)
        uiState = uiState.copy(glassIntensity = clamped, glassPreset = detectPreset(clamped, uiState.motionIntensity))
        viewModelScope.launch { preferencesStore.setGlassIntensity(clamped) }
    }

    fun setMotionIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1.4f)
        uiState = uiState.copy(motionIntensity = clamped, glassPreset = detectPreset(uiState.glassIntensity, clamped))
        viewModelScope.launch { preferencesStore.setMotionIntensity(clamped) }
    }

    fun setGlassPreset(preset: GlassPreset) {
        uiState = uiState.copy(
            glassPreset = preset,
            glassIntensity = preset.glassIntensity,
            motionIntensity = preset.motionIntensity
        )
        viewModelScope.launch {
            preferencesStore.setGlassPreset(preset)
            preferencesStore.setGlassIntensity(preset.glassIntensity)
            preferencesStore.setMotionIntensity(preset.motionIntensity)
        }
    }

    private fun detectPreset(glass: Float, motion: Float): GlassPreset {
        return GlassPreset.entries.minByOrNull {
            val dg = glass - it.glassIntensity
            val dm = motion - it.motionIntensity
            dg * dg + dm * dm
        } ?: GlassPreset.Liquid
    }
}