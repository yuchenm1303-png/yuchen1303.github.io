package com.yuchen.ailedger

import android.app.Application
import android.content.Context
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
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
                // The interactive floating window stays manual except for a new GUI Plus request
                // that explicitly needs user input/help. The visual HUD is owned by the connected
                // accessibility service and does not need an application-level service launch.
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
