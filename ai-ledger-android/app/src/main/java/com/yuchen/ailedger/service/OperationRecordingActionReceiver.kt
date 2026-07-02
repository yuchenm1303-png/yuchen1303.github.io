package com.yuchen.ailedger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OperationRecordingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FINISH -> OperationLearningRecordingCoordinator.requestStop(
                context = context.applicationContext,
                reason = OperationRecordingStopReason.NotificationFinished,
            )
            ACTION_CANCEL -> OperationLearningRecordingCoordinator.requestStop(
                context = context.applicationContext,
                reason = OperationRecordingStopReason.UserCancelled,
            )
        }
    }

    companion object {
        const val ACTION_FINISH = "com.yuchen.ailedger.action.FINISH_OPERATION_RECORDING"
        const val ACTION_CANCEL = "com.yuchen.ailedger.action.CANCEL_OPERATION_RECORDING"
    }
}
