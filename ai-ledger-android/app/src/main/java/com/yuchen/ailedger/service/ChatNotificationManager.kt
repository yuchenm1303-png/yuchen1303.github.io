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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
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
     * Notification construction stays outside the cold-start caller. Repeated message updates are
     * coalesced into the newest snapshot, and the first build waits for the UI/OpenGL stabilization
     * window. SharedPreferences merge, MessagingStyle allocation and binder work all run here.
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
                request.copy(
                    messages = if (request.messages.isNotEmpty()) request.messages else previous.messages,
                    force = request.force || previous.force
                )
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

        val snapshot = if (request.messages.isEmpty()) {
            NotificationChatStore.load(appContext)
        } else {
            NotificationChatStore.mergeAppMessages(appContext, request.messages)
        }
        val visibleMessages = snapshot.messages
            .asSequence()
            .filter { it.text.isNotBlank() }
            .takeLastCompat(6)

        val signature = buildString {
            append(visibleMessages.notificationSignature())
            append("|pending=").append(snapshot.pendingCount)
            append("|error=").append(snapshot.lastError.orEmpty())
        }
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
            style.addMessage("点击“提问”直接和 AI 对话。", System.currentTimeMillis(), assistant)
        } else {
            visibleMessages.forEach { message ->
                val sender = if (message.role == MessageRole.User) user else assistant
                style.addMessage(message.text.toNotificationLine(), message.createdAt, sender)
            }
        }

        val title = when {
            snapshot.isProcessing -> "AI Ledger 助手 · 正在思考"
            snapshot.lastError != null -> "AI Ledger 助手 · 请求失败"
            else -> "AI Ledger 助手"
        }
        val latestText = when {
            snapshot.isProcessing -> "正在处理通知栏请求…"
            snapshot.lastError != null -> snapshot.lastError.toNotificationLine()
            else -> visibleMessages.lastOrNull()?.text?.toNotificationLine()
                ?: "随时可以提问，点击卡片打开 App。"
        }
        val subText = when {
            snapshot.pendingCount > 1 -> "通知栏快捷对话 · ${snapshot.pendingCount} 个请求排队"
            snapshot.isProcessing -> "通知栏快捷对话 · 自动模型"
            else -> "通知栏快捷对话 · 自动模型"
        }

        val publicVersion = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle("AI Ledger 助手")
            .setContentText("点击查看对话或快速提问")
            .setContentIntent(buildOpenAppIntent(appContext))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(title)
            .setContentText(latestText)
            .setSubText(subText)
            .setStyle(style)
            .setContentIntent(buildOpenAppIntent(appContext))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .addAction(buildReplyAction(appContext))

        when {
            snapshot.isProcessing -> builder.addAction(
                buildBroadcastAction(
                    appContext,
                    NotificationReplyReceiver.ACTION_STOP,
                    "停止",
                    REQUEST_STOP
                )
            )
            snapshot.lastError != null && !snapshot.failedPrompt.isNullOrBlank() -> builder.addAction(
                buildBroadcastAction(
                    appContext,
                    NotificationReplyReceiver.ACTION_RETRY,
                    "重试",
                    REQUEST_RETRY
                )
            )
            visibleMessages.isNotEmpty() -> builder.addAction(
                buildBroadcastAction(
                    appContext,
                    NotificationReplyReceiver.ACTION_CLEAR,
                    "清空",
                    REQUEST_CLEAR
                )
            )
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification_ai,
                "打开 App",
                buildOpenAppIntent(appContext)
            ).build()
        )

        notify(appContext, builder.build())
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
                description = "在通知栏直接使用 AI 助手"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
            channelReady = true
        }
    }

    private fun buildReplyAction(context: Context): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("向 AI 提问")
            .build()
        val intent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = NotificationReplyReceiver.ACTION_REPLY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_REPLY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_ai,
            "提问",
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun buildBroadcastAction(
        context: Context,
        action: String,
        title: String,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationReplyReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_ai,
            title,
            pendingIntent
        ).build()
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
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
            .take(180)
            .ifBlank { "点击继续和 AI 助手聊天。" }
    }

    private data class NotificationRequest(
        val context: Context,
        val messages: List<ChatMessage>,
        val force: Boolean
    )

    private const val REQUEST_OPEN = 1303
    private const val REQUEST_REPLY = 1304
    private const val REQUEST_RETRY = 1305
    private const val REQUEST_STOP = 1306
    private const val REQUEST_CLEAR = 1307
}
