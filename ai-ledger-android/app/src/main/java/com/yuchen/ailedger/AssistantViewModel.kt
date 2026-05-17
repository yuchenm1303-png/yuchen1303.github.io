package com.yuchen.ailedger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.yuchen.ailedger.data.AssistantRepository
import com.yuchen.ailedger.data.PreviewAssistantRepository
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.AiWorkerClient

class AssistantViewModel(
    private val repository: AssistantRepository = PreviewAssistantRepository(),
    private val aiWorkerClient: AiWorkerClient = AiWorkerClient()
) : ViewModel() {
    var uiState by mutableStateOf(repository.initialState())
        private set

    val aiEndpoint: String
        get() = aiWorkerClient.endpoint

    fun selectTab(tab: AppTab) {
        uiState = uiState.copy(currentTab = tab)
    }

    fun selectQuality(quality: RenderQuality) {
        uiState = uiState.copy(quality = quality)
    }
}
