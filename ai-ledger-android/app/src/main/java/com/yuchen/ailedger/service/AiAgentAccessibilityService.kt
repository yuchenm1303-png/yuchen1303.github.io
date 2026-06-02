package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class AiAgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        publishObservation()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> publishObservation()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ScreenObservationStore.markDisabled()
        super.onDestroy()
    }

    private fun publishObservation() {
        val root = rootInActiveWindow ?: run {
            ScreenObservationStore.markDisabled()
            return
        }
        val packageName = root.packageName?.toString().orEmpty()
        val windowTitle = root.text?.toString().orEmpty()
        val nodes = collectNodes(root)
        ScreenObservationStore.update(
            ScreenObservation(
                enabled = true,
                packageName = packageName,
                windowTitle = windowTitle,
                updatedAt = System.currentTimeMillis(),
                textItems = nodes.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }.distinct().take(40),
                clickableItems = nodes.filter { it.clickable }.take(30),
                inputItems = nodes.filter { it.editable }.take(12),
                scrollableItems = nodes.filter { it.scrollable }.take(12),
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
            if (depth < MAX_DEPTH) {
                for (childIndex in 0 until node.childCount) {
                    val child = node.getChild(childIndex) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        return result
    }

    companion object {
        private const val MAX_NODES = 160
        private const val MAX_DEPTH = 8
    }
}
