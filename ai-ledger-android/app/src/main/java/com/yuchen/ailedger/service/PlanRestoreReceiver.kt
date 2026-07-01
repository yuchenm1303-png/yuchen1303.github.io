package com.yuchen.ailedger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlanRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PlanScheduler(context).restoreEnabledTasks()
    }
}
