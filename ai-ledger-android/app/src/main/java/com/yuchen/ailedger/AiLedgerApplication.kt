package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.service.AgentOverlayService
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
                // Visual presentation follows the visual runtime only; it is independent from the
                // interactive floating-window switch.
                VisualAgentHudOverlayService.syncForProgress(this@AiLedgerApplication, progress)
                // The interactive floating window stays manual except for a new GUI Plus request
                // that explicitly needs user input/help.
                AgentOverlayService.syncForProgress(this@AiLedgerApplication, progress)
            }
        }
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun contextOrNull(): Context? = appContext
    }
}
