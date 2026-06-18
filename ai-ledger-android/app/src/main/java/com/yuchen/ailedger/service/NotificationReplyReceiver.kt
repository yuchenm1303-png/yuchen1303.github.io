package com.yuchen.ailedger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REPLY -> {
                val reply = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_TEXT_REPLY)
                    ?.toString()
                    .orEmpty()
                enqueuePrompt(context, reply)
            }
            ACTION_RETRY -> {
                val failedPrompt = NotificationChatStore.load(context).failedPrompt.orEmpty()
                enqueuePrompt(context, failedPrompt)
            }
            ACTION_STOP -> {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                NotificationChatStore.stopPending(context)
                ChatNotificationManager.showPersistentChatEntry(context)
            }
            ACTION_CLEAR -> {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                NotificationChatStore.clear(context)
                ChatNotificationManager.showPersistentChatEntry(context)
            }
        }
    }

    private fun enqueuePrompt(context: Context, prompt: String) {
        val request = NotificationChatStore.enqueuePrompt(context, prompt) ?: return
        ChatNotificationManager.showPersistentChatEntry(context)

        val input = Data.Builder()
            .putString(NotificationChatWorker.KEY_USER_MESSAGE_ID, request.userMessageId)
            .putString(NotificationChatWorker.KEY_PENDING_MESSAGE_ID, request.pendingMessageId)
            .putString(NotificationChatWorker.KEY_PROMPT, request.prompt)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationChatWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    companion object {
        const val KEY_TEXT_REPLY = "notification_text_reply"
        const val ACTION_REPLY = "com.yuchen.ailedger.notification.REPLY"
        const val ACTION_RETRY = "com.yuchen.ailedger.notification.RETRY"
        const val ACTION_STOP = "com.yuchen.ailedger.notification.STOP"
        const val ACTION_CLEAR = "com.yuchen.ailedger.notification.CLEAR"
        const val UNIQUE_WORK_NAME = "notification_chat_queue"
        private const val WORK_TAG = "notification_chat"
    }
}
