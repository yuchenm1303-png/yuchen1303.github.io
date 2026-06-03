package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import java.util.ArrayDeque

class AiAgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        ScreenObservationStore.markConnectedWaitingForWindow()
        startAgentForegroundNotification()
        updateWindowHintFromRoot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            updateWindowHintFromRoot(event.packageName?.toString().orEmpty())
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        ScreenObservationStore.markDisabled()
        super.onDestroy()
    }

    private fun updateWindowHintFromRoot(packageNameFromEvent: String = "") {
        val root = rootInActiveWindow
        val packageName = packageNameFromEvent.ifBlank { root?.packageName?.toString().orEmpty() }
        val windowTitle = root?.text?.toString().orEmpty()
        if (packageName.isBlank()) {
            ScreenObservationStore.markConnectedWaitingForWindow()
        } else {
            ScreenObservationStore.updateWindowHint(packageName, windowTitle)
        }
    }

    private fun captureSnapshotInternal(): ScreenObservation {
        val now = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return ScreenObservation(
            enabled = true,
            serviceConnected = true,
            updatedAt = now,
        )
        val packageName = root.packageName?.toString().orEmpty()
        val windowTitle = root.text?.toString().orEmpty()
        val nodes = collectNodes(root)
        return ScreenObservation(
            enabled = true,
            serviceConnected = true,
            packageName = packageName,
            windowTitle = windowTitle,
            updatedAt = now,
            textItems = nodes.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }.distinct().take(TEXT_LIMIT),
            clickableItems = nodes.filter { it.clickable }.take(CLICKABLE_LIMIT),
            inputItems = nodes.filter { it.editable }.take(INPUT_LIMIT),
            scrollableItems = nodes.filter { it.scrollable }.take(SCROLLABLE_LIMIT),
            nodeCount = nodes.size,
        )
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<ObservedScreenNode> {
        val result = mutableListOf<ObservedScreenNode>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var index = 0
        while (queue.isNotEmpty() && result.size < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            val text = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?: node.hintText?.toString().orEmpty()
            val hasUsefulSignal = text.isNotBlank() || node.isClickable || node.isLongClickable || node.isEditable || node.isScrollable
            if (hasUsefulSignal) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                result.add(
                    ObservedScreenNode(
                        id = "n${index++}",
                        text = text.take(80),
                        className = node.className?.toString().orEmpty().substringAfterLast('.').take(32),
                        bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                        clickable = node.isClickable || node.isLongClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                    )
                )
            }
            if (depth < MAX_DEPTH) {
                for (childIndex in 0 until node.childCount) {
                    val child = node.getChild(childIndex) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        return result
    }

    private fun startAgentForegroundNotification() {
        ensureNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, AGENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.ai_agent_accessibility_notification_title))
            .setContentText(getString(R.string.ai_agent_accessibility_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        try {
            startForeground(AGENT_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Some ROMs restrict foreground promotion for accessibility services. The service still works after user authorization.
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AGENT_CHANNEL_ID,
            "AI 助手智能体",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持 AI 助手智能体待命，用于用户主动发起的屏幕观察。"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        @Volatile private var activeService: AiAgentAccessibilityService? = null

        fun captureFreshSnapshot(): ScreenObservation {
            val service = activeService ?: return ScreenObservation(updatedAt = System.currentTimeMillis())
            val snapshot = service.captureSnapshotInternal()
            ScreenObservationStore.update(snapshot)
            return snapshot
        }

        private const val AGENT_CHANNEL_ID = "ai_agent_accessibility_status"
        private const val AGENT_NOTIFICATION_ID = 7301
        private const val MAX_NODES = 90
        private const val MAX_DEPTH = 6
        private const val TEXT_LIMIT = 30
        private const val CLICKABLE_LIMIT = 24
        private const val INPUT_LIMIT = 8
        private const val SCROLLABLE_LIMIT = 8
    }
}
