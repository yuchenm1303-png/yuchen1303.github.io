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
    private var overlayPermissionRequestLaunched = false

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        applicationScope.launch {
            AgentRuntimeController.progress.collectLatest { progress ->
                val shouldEnsureOverlays =
                    progress.enabled ||
                        progress.running ||
                        progress.pendingConfirmation != null ||
                        progress.pendingUserInput != null
                if (!shouldEnsureOverlays) return@collectLatest

                if (AgentOverlayService.canDrawOverlays(this@AiLedgerApplication)) {
                    overlayPermissionRequestLaunched = false
                    // startService is idempotent. Retrying on subsequent state updates is intentional:
                    // a previous attempt may have happened while Android still considered the app
                    // backgrounded or before the overlay permission result became visible.
                    runCatching {
                        AgentOverlayService.ensureStarted(this@AiLedgerApplication)
                    }
                    VisualAgentHudOverlayService.ensureStarted(this@AiLedgerApplication)
                } else if (!overlayPermissionRequestLaunched) {
                    overlayPermissionRequestLaunched = true
                    runCatching {
                        AgentOverlayService.requestPermissionIfNeeded(this@AiLedgerApplication)
                    }
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
