package com.yuchen.ailedger

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.yuchen.ailedger.data.AgentAnalyticsOwnerRuntime
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantCustomInstructionsRepository
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.UserProfileRepository
import com.yuchen.ailedger.data.switchAccount
import com.yuchen.ailedger.model.ChatAttachmentPayloadStore
import com.yuchen.ailedger.service.AgentAnalyticsRuntime
import com.yuchen.ailedger.service.AgentOverlayProgress
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.VisualIntelligenceDiagnosticsStore
import com.yuchen.ailedger.ui.StartupPerformanceGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AiLedgerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        AgentAnalyticsOwnerRuntime.initialize(applicationContext)

        applicationScope.launch {
            var authenticatedSettingsRepositoriesReady = false
            SupabaseAuthRepository.get(applicationContext).state.collectLatest { accountState ->
                val activeSession = accountState.session?.takeIf { accountState.isLoggedIn }
                AgentAnalyticsOwnerRuntime.switchAccount(applicationContext, activeSession)

                val ticket = AssistantAccountSessionRuntime.updateSession(activeSession)
                AssistantMemoryMutationRuntime.switchAccount(ticket)
                AssistantMemoryDiagnostics.switchAccount(ticket)

                if (accountState.isLoggedIn && !authenticatedSettingsRepositoriesReady) {
                    withTimeoutOrNull(5_000L) {
                        StartupPerformanceGate.awaitDeferredBusinessWindow()
                    }
                    UserProfileRepository.get(applicationContext)
                    AssistantMemoryRepository.get(applicationContext)
                    AssistantCustomInstructionsRepository.get(applicationContext)
                    authenticatedSettingsRepositoriesReady = true
                }
            }
        }

        applicationScope.launch {
            var previousAnalyticsProgress = AgentOverlayProgress()
            var previousOverlaySyncKey: OverlaySyncKey? = null
            var diagnosticsStore: VisualIntelligenceDiagnosticsStore? = null

            AgentRuntimeController.progress.collectLatest { progress ->
                val overlaySyncKey = OverlaySyncKey(
                    enabled = progress.enabled,
                    running = progress.running,
                    hasTask = progress.taskId > 0L,
                    awaitingConfirmation = progress.pendingConfirmation != null,
                    awaitingUserInput = progress.pendingUserInput != null,
                    userTakeoverPaused = progress.userTakeoverPaused,
                )
                if (overlaySyncKey != previousOverlaySyncKey) {
                    AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                    previousOverlaySyncKey = overlaySyncKey
                }

                val terminalAnalyticsProgress = previousAnalyticsProgress.takeIf { previous ->
                    previous.taskId > 0L &&
                        previous.running &&
                        progress.taskId == 0L &&
                        !progress.running
                }?.let { previous ->
                    progress.copy(
                        taskId = previous.taskId,
                        currentAction = previous.currentAction,
                        logs = (previous.logs + progress.logs).takeLast(24),
                    )
                }
                AgentAnalyticsRuntime.observeProgress(terminalAnalyticsProgress ?: progress)
                previousAnalyticsProgress = progress

                if (progress.taskId > 0L || progress.running) {
                    val store = diagnosticsStore
                        ?: VisualIntelligenceDiagnosticsStore.get(applicationContext).also {
                            diagnosticsStore = it
                        }
                    store.observeProgress(progress)
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val appHiddenOrWorse = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        val runtimePressure = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (appHiddenOrWorse || runtimePressure) {
            ChatAttachmentPayloadStore.trimMemory(aggressive = appHiddenOrWorse)
        }
    }

    override fun onLowMemory() {
        ChatAttachmentPayloadStore.trimMemory(aggressive = true)
        super.onLowMemory()
    }

    private data class OverlaySyncKey(
        val enabled: Boolean,
        val running: Boolean,
        val hasTask: Boolean,
        val awaitingConfirmation: Boolean,
        val awaitingUserInput: Boolean,
        val userTakeoverPaused: Boolean,
    )

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}
