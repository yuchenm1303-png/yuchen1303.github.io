package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.data.StockMarketStageRepository
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
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

        // 内置表情由真实消息按需加载。冷启动阶段禁止全量解压、解码 19 张 WebP，
        // 避免与首页 Compose 入场、背景纹理生成和 OpenGL 首次编译争抢 CPU 与内存带宽。
        applicationScope.launch {
            // 空闲启动不需要立即扫描诊断历史。正常前台启动等待首屏稳定；如果进程由后台组件
            // 拉起，则最多等待 5 秒后初始化，避免永久依赖 Compose 帧时钟。
            val diagnosticsAfterStartup = async {
                withTimeoutOrNull(5_000L) {
                    StartupPerformanceGate.awaitDeferredBusinessWindow()
                }
                VisualIntelligenceDiagnosticsStore.get(applicationContext)
            }

            AgentRuntimeController.progress.collectLatest { progress ->
                AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                val visualDiagnostics = if (progress.taskId > 0L || progress.running) {
                    // 用户在首屏稳定前立即发起任务时，诊断能力必须立刻可用，不能漏掉任务开头。
                    VisualIntelligenceDiagnosticsStore.get(applicationContext)
                } else {
                    diagnosticsAfterStartup.await()
                }
                visualDiagnostics.observeProgress(progress)
            }
        }

        // 股票代理使用 Render 免费实例，长时间空闲后可能休眠。只有 Compose 前台首屏真正稳定后
        // 才顺序预热“指数→市场宽度→榜单板块”，避免三个 URL 在冷启动时同时争抢连接。
        // 后台组件或无障碍空闲态拉起进程时不会产生股票网络请求。
        applicationScope.launch(Dispatchers.IO) {
            val foregroundReady = withTimeoutOrNull(15_000L) {
                StartupPerformanceGate.awaitDeferredBusinessWindow()
                true
            } == true
            if (foregroundReady) {
                StockMarketStageRepository.prewarmMarketHome()
            }
        }
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}
