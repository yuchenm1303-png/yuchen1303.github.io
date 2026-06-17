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
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.ui.StartupPerformanceGate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object ChatNotificationManager {
    private const val CHANNEL_ID = "ai_chat_entry"
    private const val CHANNEL_NAME = "AI 助手常驻入口"
    private const val NOTIFICATION_ID = 1303
    private const val EMPTY_NOTIFICATION_SIGNATURE = "empty"
    private const val STARTUP_GATE_TIMEOUT_MS = 5_000L
    const val ACTION_OPEN_CHAT = "com.yuchen.ailedger.action.OPEN_CHAT"

    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "ChatNotificationBuilder"
        ).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val pendingLock = Any()

    @Volatile
    private var channelReady = false

    @Volatile
    private var lastNotificationSignature: String? = null

    private var pendingRequest: NotificationRequest? = null
    private var drainScheduled = false

    /**
     * Notification construction is deliberately removed from the cold-start caller. Repeated message
     * updates are coalesced into the newest snapshot, and the first build waits for the UI/OpenGL
     * stabilization window. This keeps channel creation, MessagingStyle allocation and binder work
     * away from the frames that mount the glass scene.
     */
    fun showPersistentChatEntry(
        context: Context,
        messages: List<ChatMessage> = emptyList(),
        force: Boolean = false
    ) {
        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) return

        val request = NotificationRequest(
            context = appContext,
            messages = messages,
            force = force
        )
        val shouldSchedule = synchronized(pendingLock) {
            val previous = pendingRequest
            pendingRequest = if (previous == null) {
                request
            } else {
                request.copy(force = request.force || previous.force)
            }
            if (drainScheduled) {
                false
            } else {
                drainScheduled = true
                true
            }
        }
        if (!shouldSchedule) return

        scope.launch {
            withTimeoutOrNull(STARTUP_GATE_TIMEOUT_MS) {
                StartupPerformanceGate.awaitDeferredBusinessWindow()
            }
            drainPendingRequests()
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun drainPendingRequests() {
        while (true) {
            val request = synchronized(pendingLock) {
                val next = pendingRequest
                pendingRequest = null
                if (next == null) drainScheduled = false
                next
            } ?: return
            publishPersistentChatEntry(request)
        }
    }

    private fun publishPersistentChatEntry(request: NotificationRequest) {
        val appContext = request.context
        if (!canPostNotifications(appContext)) return

        val visibleMessages = request.messages
            .asSequence()
            .filter { it.text.isNotBlank() }
            .filterNot { it.status == MessageStatus.Sending }
            .takeLastCompat(6)

        val signature = visibleMessages.notificationSignature()
        if (!request.force && signature == lastNotificationSignature) return

        ensureChannel(appContext)
        if (!request.force && signature == lastNotificationSignature) return

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
        lastNotificationSignature = signature
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || channelReady) return
        synchronized(this) {
            if (channelReady) return
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
            channelReady = true
        }
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

    private fun Sequence<ChatMessage>.takeLastCompat(count: Int): List<ChatMessage> {
        if (count <= 0) return emptyList()
        val buffer = ArrayDeque<ChatMessage>(count)
        forEach { message ->
            if (buffer.size == count) buffer.removeFirst()
            buffer.addLast(message)
        }
        return buffer.toList()
    }

    private fun List<ChatMessage>.notificationSignature(): String {
        if (isEmpty()) return EMPTY_NOTIFICATION_SIGNATURE
        return joinToString(separator = "|") { message ->
            buildString {
                append(message.id)
                append(':')
                append(message.role.name)
                append(':')
                append(message.status.name)
                append(':')
                append(message.createdAt)
                append(':')
                append(message.text.toNotificationLine())
            }
        }
    }

    private fun String.toNotificationLine(): String {
        return replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(140)
            .ifBlank { "点击继续和 AI 助手聊天。" }
    }

    private data class NotificationRequest(
        val context: Context,
        val messages: List<ChatMessage>,
        val force: Boolean
    )
}
