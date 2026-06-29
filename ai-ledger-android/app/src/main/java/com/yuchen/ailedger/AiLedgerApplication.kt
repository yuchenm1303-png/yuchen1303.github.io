package com.yuchen.ailedger

import android.app.Application
import android.content.Context
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
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}
