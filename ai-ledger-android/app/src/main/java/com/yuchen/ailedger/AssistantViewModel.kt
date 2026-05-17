package com.yuchen.ailedger

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuchen.ailedger.data.AssistantPreferencesStore
import com.yuchen.ailedger.data.AssistantRepository
import com.yuchen.ailedger.data.PreviewAssistantRepository
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.AiWorkerClient
import kotlinx.coroutines.launch

class AssistantViewModel(
    application: Application,
    private val repository: AssistantRepository,
    private val preferencesStore: AssistantPreferencesStore,
    private val aiWorkerClient: AiWorkerClient
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = PreviewAssistantRepository(),
        preferencesStore = AssistantPreferencesStore(application),
        aiWorkerClient = AiWorkerClient()
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

    fun selectQuality(quality: RenderQuality) {
        uiState = uiState.copy(quality = quality)
        viewModelScope.launch { preferencesStore.setRenderQuality(quality) }
    }

    fun setShowPreviewConversation(showPreviewConversation: Boolean) {
        uiState = uiState.copy(showPreviewConversation = showPreviewConversation)
        viewModelScope.launch { preferencesStore.setShowPreviewConversation(showPreviewConversation) }
    }

    fun setBackgroundTheme(backgroundTheme: BackgroundTheme) {
        uiState = uiState.copy(backgroundTheme = backgroundTheme)
        viewModelScope.launch { preferencesStore.setBackgroundTheme(backgroundTheme) }
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