package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.data.AssistantAccountSessionRuntime
import com.yuchen.ailedger.data.AssistantCustomInstructionsRepository
import com.yuchen.ailedger.data.AssistantMemoryDiagnostics
import com.yuchen.ailedger.data.AssistantMemoryMutationRuntime
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.data.StockWatchlistRepository
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.data.UserProfileRepository
import com.yuchen.ailedger.data.switchAccount
import com.yuchen.ailedger.service.AgentAnalyticsRuntime
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.VisualAgentHudTuningStore
import com.yuchen.ailedger.service.VisualIntelligenceDiagnosticsStore
import com.yuchen.ailedger.ui.StartupPerformanceGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AiLedgerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // 三大指数是功能页屏幕内最高优先级数据。这里只恢复极小的本地缓存并启动独立 IO 预热，
        // 不加载股票详情、市场榜单、图片或 OpenGL，也不占用主线程做网络工作。
        ToolsMarketHeroStore.prewarm(applicationContext)

        applicationScope.launch {
            var authenticatedSettingsRepositoriesReady = false
            SupabaseAuthRepository.get(applicationContext).state.collectLatest { accountState ->
                val ticket = AssistantAccountSessionRuntime.updateSession(
                    accountState.session?.takeIf { accountState.isLoggedIn },
                )
                AssistantMemoryMutationRuntime.switchAccount(ticket)
                AssistantMemoryDiagnostics.switchAccount(ticket)

                if (accountState.isLoggedIn && !authenticatedSettingsRepositoriesReady) {
                    withTimeoutOrNull(5_000L) {
                        StartupPerformanceGate.awaitDeferredBusinessWindow()
                    }
                    UserProfileRepository.get(applicationContext)
                    AssistantMemoryRepository.get(applicationContext)
                    AssistantCustomInstructionsRepository.get(applicationContext)
                    StockWatchlistRepository.get(applicationContext)
                    authenticatedSettingsRepositoriesReady = true
                }
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(5_000L) {
                StartupPerformanceGate.awaitDeferredBusinessWindow()
            }
            LedgerStore(applicationContext).warmUp()
            VisualAgentHudTuningStore.get(applicationContext)
        }

        applicationScope.launch {
            // 视觉诊断仍在首屏稳定后准备；智能体统计数据库改为严格按需创建：
            // 只有真实聊天、真实智能体任务或统计页面订阅时才打开 Room，空闲启动不再做统计迁移和清理。
            val diagnosticsAfterStartup = async {
                withTimeoutOrNull(5_000L) {
                    StartupPerformanceGate.awaitDeferredBusinessWindow()
                }
                VisualIntelligenceDiagnosticsStore.get(applicationContext)
            }

            AgentRuntimeController.progress.collectLatest { progress ->
                AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                AgentAnalyticsRuntime.observeProgress(progress)
                val visualDiagnostics = if (progress.taskId > 0L || progress.running) {
                    VisualIntelligenceDiagnosticsStore.get(applicationContext)
                } else {
                    diagnosticsAfterStartup.await()
                }
                visualDiagnostics.observeProgress(progress)
            }
        }
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}
