package com.yuchen.ailedger.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus

object ChatNotificationManager {
    private const val CHANNEL_ID = "ai_chat_entry"
    private const val CHANNEL_NAME = "AI 助手常驻入口"
    private const val NOTIFICATION_ID = 1303
    const val ACTION_OPEN_CHAT = "com.yuchen.ailedger.action.OPEN_CHAT"

    fun showPersistentChatEntry(
        context: Context,
        messages: List<ChatMessage> = emptyList()
    ) {
        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) return
        ensureChannel(appContext)

        val visibleMessages = messages
            .filter { it.text.isNotBlank() }
            .filterNot { it.status == MessageStatus.Sending }
            .takeLast(6)

        val user = Person.Builder().setName("你").build()
        val assistant = Person.Builder()
            .setName("AI Ledger 助手")
            .setBot(true)
            .build()

        val style = NotificationCompat.MessagingStyle(user)
            .setConversationTitle("AI Ledger 助手")
            .setGroupConversation(false)

        if (visibleMessages.isEmpty()) {
            style.addMessage("点击继续和 AI 助手聊天。", System.currentTimeMillis(), assistant)
        } else {
            visibleMessages.forEach { message ->
                val sender = if (message.role == MessageRole.User) user else assistant
                style.addMessage(message.text.toNotificationLine(), message.createdAt, sender)
            }
        }

        val latestText = visibleMessages.lastOrNull()?.text?.toNotificationLine()
            ?: "常驻聊天入口，点击回到 App。"

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle("AI Ledger 助手")
            .setContentText(latestText)
            .setStyle(style)
            .setContentIntent(buildOpenAppIntent(appContext))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .build()

        notify(appContext, notification)
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在通知栏保留 AI 助手聊天入口"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun String.toNotificationLine(): String {
        return replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(140)
            .ifBlank { "点击继续和 AI 助手聊天。" }
    }
}
