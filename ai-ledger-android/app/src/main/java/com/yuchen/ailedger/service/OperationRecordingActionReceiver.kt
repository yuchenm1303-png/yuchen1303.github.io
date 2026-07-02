package com.yuchen.ailedger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OperationRecordingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            ACTION_FINISH -> OperationRecordingStopReason.NotificationFinished
            ACTION_CANCEL -> OperationRecordingStopReason.UserCancelled
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                OperationLearningRecordingCoordinator.stop(
                    context = context.applicationContext,
                    reason = reason,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FINISH = "com.yuchen.ailedger.action.FINISH_OPERATION_RECORDING"
        const val ACTION_CANCEL = "com.yuchen.ailedger.action.CANCEL_OPERATION_RECORDING"
    }
}
