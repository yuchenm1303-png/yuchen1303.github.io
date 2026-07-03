package com.yuchen.ailedger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlanRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        restoreScope.launch {
            try {
                PlanScheduler(appContext).restoreEnabledTasks()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
