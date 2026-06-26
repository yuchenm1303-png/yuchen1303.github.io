package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.VisualAgentHudOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AiLedgerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        applicationScope.launch {
            AgentRuntimeController.progress.collectLatest { progress ->
                if (
                    progress.enabled ||
                    progress.running ||
                    progress.pendingConfirmation != null ||
                    progress.pendingUserInput != null
                ) {
                    VisualAgentHudOverlayService.ensureStarted(this@AiLedgerApplication)
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
