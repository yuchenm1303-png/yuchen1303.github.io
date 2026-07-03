package com.yuchen.ailedger

import android.app.Application
import android.content.Context
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

        // 启动阶段只恢复三大指数的极小本地缓存。网络报价、分时曲线与持久化刷新
        // 严格等到功能页真正可见后再启动，避免用户停留首页时产生无意义网络与 JSON 负载。
        ToolsMarketHeroStore.initialize(applicationContext)

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
                    // 聊天与记忆链真正依赖的账户仓库在稳定窗口后准备；股票自选、
                    // 账本和 HUD 调参等功能仓库保持严格按需创建。
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

                // 空闲进程不再提前创建视觉诊断仓库。真实任务开始后才初始化，
                // 终态进度仍携带 taskId，因此不会丢失任务结束诊断。
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
