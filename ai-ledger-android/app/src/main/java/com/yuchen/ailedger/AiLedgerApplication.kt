package com.yuchen.ailedger

import android.app.Application
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
import com.yuchen.ailedger.service.AgentAnalyticsRuntime
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
            AgentRuntimeController.progress.collectLatest { progress ->
                AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                AgentAnalyticsRuntime.observeProgress(progress)
                if (progress.taskId > 0L || progress.running) {
                    VisualIntelligenceDiagnosticsStore.get(applicationContext)
                        .observeProgress(progress)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}
