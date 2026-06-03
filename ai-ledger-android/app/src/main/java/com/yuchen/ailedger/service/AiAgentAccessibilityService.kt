package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class AiAgentAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPublish = false
    private var lastPublishAt = 0L
    private var lastSignature = ""

    private val publishRunnable = Runnable {
        pendingPublish = false
        publishObservation(force = false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenObservationStore.markConnectedWaitingForWindow()
        schedulePublish(delayMs = 0L, force = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> schedulePublish(delayMs = 0L, force = true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> schedulePublish(delayMs = CONTENT_DEBOUNCE_MS, force = false)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(publishRunnable)
        ScreenObservationStore.markDisabled()
        super.onDestroy()
    }

    private fun schedulePublish(delayMs: Long, force: Boolean) {
        if (force) {
            mainHandler.removeCallbacks(publishRunnable)
            pendingPublish = false
            publishObservation(force = true)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastPublishAt < MIN_PUBLISH_INTERVAL_MS) {
            if (!pendingPublish) {
                pendingPublish = true
                mainHandler.postDelayed(publishRunnable, CONTENT_DEBOUNCE_MS)
            }
            return
        }
        mainHandler.removeCallbacks(publishRunnable)
        pendingPublish = true
        mainHandler.postDelayed(publishRunnable, delayMs)
    }

    private fun publishObservation(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPublishAt < MIN_PUBLISH_INTERVAL_MS) return
        val root = rootInActiveWindow ?: run {
            ScreenObservationStore.markConnectedWaitingForWindow()
            return
        }
        val packageName = root.packageName?.toString().orEmpty()
        val windowTitle = root.text?.toString().orEmpty()
        val nodes = collectNodes(root)
        val textItems = nodes.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }.distinct().take(TEXT_LIMIT)
        val clickableItems = nodes.filter { it.clickable }.take(CLICKABLE_LIMIT)
        val inputItems = nodes.filter { it.editable }.take(INPUT_LIMIT)
        val scrollableItems = nodes.filter { it.scrollable }.take(SCROLLABLE_LIMIT)
        val signature = buildSignature(packageName, nodes.size, textItems, clickableItems, inputItems, scrollableItems)
        if (!force && signature == lastSignature) return
        lastSignature = signature
        lastPublishAt = now
        ScreenObservationStore.update(
            ScreenObservation(
                enabled = true,
                serviceConnected = true,
                packageName = packageName,
                windowTitle = windowTitle,
                updatedAt = now,
                textItems = textItems,
                clickableItems = clickableItems,
                inputItems = inputItems,
                scrollableItems = scrollableItems,
                nodeCount = nodes.size,
            )
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
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val hasUsefulSignal = text.isNotBlank() || node.isClickable || node.isLongClickable || node.isEditable || node.isScrollable
            if (hasUsefulSignal) {
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

    private fun buildSignature(
        packageName: String,
        nodeCount: Int,
        textItems: List<String>,
        clickableItems: List<ObservedScreenNode>,
        inputItems: List<ObservedScreenNode>,
        scrollableItems: List<ObservedScreenNode>,
    ): String {
        return buildString {
            append(packageName).append('|').append(nodeCount)
            append('|').append(textItems.take(8).joinToString("#"))
            append('|').append(clickableItems.take(8).joinToString("#") { "${it.text}@${it.bounds}" })
            append('|').append(inputItems.take(4).joinToString("#") { it.bounds })
            append('|').append(scrollableItems.take(4).joinToString("#") { it.bounds })
        }
    }

    companion object {
        private const val MIN_PUBLISH_INTERVAL_MS = 650L
        private const val CONTENT_DEBOUNCE_MS = 420L
        private const val MAX_NODES = 90
        private const val MAX_DEPTH = 6
        private const val TEXT_LIMIT = 30
        private const val CLICKABLE_LIMIT = 24
        private const val INPUT_LIMIT = 8
        private const val SCROLLABLE_LIMIT = 8
    }
}
