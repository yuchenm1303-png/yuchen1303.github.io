package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.VisualIntelligenceDiagnosticsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AiLedgerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // 内置表情由真实消息按需加载。冷启动阶段禁止全量解压、解码 19 张 WebP，
        // 避免与首页 Compose 入场、背景纹理生成和 OpenGL 首次编译争抢 CPU 与内存带宽。
        applicationScope.launch {
            // 诊断存储会读取 SharedPreferences、创建线程并扫描历史目录，全部移出主线程。
            val visualDiagnostics = VisualIntelligenceDiagnosticsStore.get(applicationContext)
            AgentRuntimeController.progress.collectLatest { progress ->
                AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
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
