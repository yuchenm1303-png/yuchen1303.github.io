package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.data.AgentAnalyticsRepository
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
                    // 只提前创建账号相关单例；真实网络与磁盘工作仍由各仓库自己的 IO Scope 执行。
                    // 这样打开账号和记忆详情时不会在同一帧集中初始化多个仓库。
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
            // 首屏稳定后生成一次不可变账单快照，并提前创建轻量 HUD 参数存储。
            // 不预热图片、表情或 OpenGL，不与首页首次渲染争抢 CPU 与内存带宽。
            LedgerStore(applicationContext).warmUp()
            VisualAgentHudTuningStore.get(applicationContext)
        }

        // 内置表情由真实消息按需加载。冷启动阶段禁止全量解压、解码 19 张 WebP，
        // 避免与首页 Compose 入场、背景纹理生成和 OpenGL 首次编译争抢 CPU 与内存带宽。
        applicationScope.launch {
            // 空闲启动不需要立即扫描诊断历史或统计数据库。正常前台启动等待首屏稳定；
            // 如果进程由后台组件拉起，则最多等待 5 秒后初始化，避免永久依赖 Compose 帧时钟。
            val diagnosticsAfterStartup = async {
                withTimeoutOrNull(5_000L) {
                    StartupPerformanceGate.awaitDeferredBusinessWindow()
                }
                AgentAnalyticsRepository.get(applicationContext)
                VisualIntelligenceDiagnosticsStore.get(applicationContext)
            }

            AgentRuntimeController.progress.collectLatest { progress ->
                AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                // 统计只旁路读取已经发布的进度，不参与动作、完成许可或无障碍状态机。
                AgentAnalyticsRuntime.observeProgress(progress)
                val visualDiagnostics = if (progress.taskId > 0L || progress.running) {
                    // 用户在首屏稳定前立即发起任务时，诊断与统计都必须立刻可用，不能漏掉任务开头。
                    AgentAnalyticsRepository.get(applicationContext)
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
