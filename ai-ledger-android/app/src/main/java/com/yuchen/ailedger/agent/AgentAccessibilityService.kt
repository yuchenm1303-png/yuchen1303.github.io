package com.yuchen.ailedger.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        activeService = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    fun executeStep(step: JSONObject): JSONObject {
        val type = step.optString("type", step.optString("action", "")).lowercase()
        val requestedText = step.optString("text", step.optString("value", ""))
        val ok = when (type) {
            "tap_xy", "click", "tap" -> tap(step.optDouble01("x"), step.optDouble01("y"))
            "long_press", "longpress" -> longPress(step.optDouble01("x"), step.optDouble01("y"), step.optLong("durationMs", 650L))
            "swipe" -> swipe(step)
            "scroll" -> scroll(step.optString("direction", "down"))
            "input_text", "type" -> inputText(step.optString("text", step.optString("value", "")))
            "tap_node" -> tapNode(step)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "wait", "finish", "need_user_help" -> true
            else -> false
        }
        return JSONObject()
            .put("ok", ok)
            .put("type", type)
            .put("service", "accessibility")
            .put("requestedText", requestedText)
            .put("postObserve", observeScreen())
            .put("reason", if (ok) "accepted" else "unsupported_or_failed")
    }

    fun observeScreen(): JSONObject {
        val root = rootInActiveWindow
        val nodes = JSONArray()
        val texts = JSONArray()
        val clickableNodes = JSONArray()
        val inputNodes = JSONArray()
        val scrollableNodes = JSONArray()
        if (root != null) collectNodes(root, nodes, 0, 90)
        for (i in 0 until nodes.length()) {
            val item = nodes.optJSONObject(i) ?: continue
            val text = item.optString("text").ifBlank { item.optString("contentDescription") }
            if (text.isNotBlank()) texts.put(text)
            if (item.optBoolean("clickable")) clickableNodes.put(item)
            if (item.optBoolean("editable")) inputNodes.put(item)
            if (item.optBoolean("scrollable")) scrollableNodes.put(item)
        }
        return JSONObject()
            .put("ok", true)
            .put("serviceEnabled", true)
            .put("packageName", root?.packageName?.toString() ?: "")
            .put("currentApp", root?.packageName?.toString() ?: "")
            .put("nodeCount", nodes.length())
            .put("nodes", nodes)
            .put("texts", texts)
            .put("clickableNodes", clickableNodes)
            .put("inputNodes", inputNodes)
            .put("scrollableNodes", scrollableNodes)
            .put("screenshotAvailable", android.os.Build.VERSION.SDK_INT >= 30)
    }

    private fun tap(x: Double, y: Double): Boolean {
        val (px, py) = normalizedToPixels(x, y) ?: return false
        val path = Path().apply { moveTo(px, py) }
        return dispatch(path, 0L, 70L)
    }

    private fun longPress(x: Double, y: Double, durationMs: Long): Boolean {
        val (px, py) = normalizedToPixels(x, y) ?: return false
        val path = Path().apply { moveTo(px, py) }
        return dispatch(path, 0L, durationMs.coerceIn(350L, 1800L))
    }

    private fun swipe(step: JSONObject): Boolean {
        val x1 = step.optDouble01("x", 0.5)
        val y1 = step.optDouble01("y", if (step.optString("direction") == "up") 0.72 else 0.35)
        val x2 = step.optDouble01("x2", x1)
        val y2 = step.optDouble01("y2", if (step.optString("direction") == "up") 0.28 else 0.72)
        val start = normalizedToPixels(x1, y1) ?: return false
        val end = normalizedToPixels(x2, y2) ?: return false
        val path = Path().apply {
            moveTo(start.first, start.second)
            lineTo(end.first, end.second)
        }
        return dispatch(path, 0L, step.optLong("durationMs", 420L).coerceIn(180L, 1200L))
    }

    private fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow
        val scrollAction = if (direction.lowercase() == "up") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        if (performFirstScrollable(root, scrollAction)) return true
        val synthetic = JSONObject()
            .put("direction", if (direction.lowercase() == "up") "down" else "up")
        return swipe(synthetic)
    }

    private fun inputText(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow
        val candidates = buildList {
            findInputFocus(root)?.let { add(it) }
            findFocusedEditable(root)?.let { add(it) }
            findFocusedNode(root)?.let { add(it) }
            findFirstEditable(root)?.let { add(it) }
            findFirstActionNode(root, AccessibilityNodeInfo.ACTION_SET_TEXT)?.let { add(it) }
            findFirstActionNode(root, AccessibilityNodeInfo.ACTION_PASTE)?.let { add(it) }
        }.distinctBy { System.identityHashCode(it) }

        for (target in candidates) {
            if (setNodeText(target, text)) return true
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("ai-ledger-agent", text))
        for (target in candidates) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (target.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true
        }
        return AgentInputMethodService.commitText(text)
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        return false
    }

    private fun tapNode(step: JSONObject): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodeId = step.optString("targetNodeId", step.optString("nodeId", ""))
        val targetText = step.optString("targetText", step.optString("text", ""))
        val node = when {
            nodeId.isNotBlank() -> root.findAccessibilityNodeInfosByViewId(nodeId).firstOrNull()
            targetText.isNotBlank() -> findNodeByText(root, targetText)
            else -> null
        } ?: return false
        return performClick(node) || clickNodeCenter(node)
    }

    private fun clickNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        val path = Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
        return dispatch(path, 0L, 70L)
    }

    private fun performClick(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        repeat(6) {
            if (current == null) return false
            if (current?.isClickable == true && current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            current = current?.parent
        }
        return false
    }

    private fun dispatch(path: Path, startMs: Long, durationMs: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, startMs, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun normalizedToPixels(x: Double, y: Double): Pair<Float, Float>? {
        if (!x.isFinite() || !y.isFinite()) return null
        val metrics = resources.displayMetrics
        return Pair(
            (x.coerceIn(0.0, 1.0) * metrics.widthPixels).toFloat(),
            (y.coerceIn(0.0, 1.0) * metrics.heightPixels).toFloat(),
        )
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: JSONArray, depth: Int, max: Int) {
        if (out.length() >= max || depth > 8) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isEditable) {
            out.put(
                JSONObject()
                    .put("text", text.take(80))
                    .put("contentDescription", desc.take(80))
                    .put("viewId", node.viewIdResourceName ?: "")
                    .put("className", node.className?.toString() ?: "")
                    .put("clickable", node.isClickable)
                    .put("editable", node.isEditable)
                    .put("focused", node.isFocused)
                    .put("scrollable", node.isScrollable)
                    .put("actions", JSONArray(node.actionList.map { it.id }))
                    .put("bounds", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNodes(it, out, depth + 1, max) }
            if (out.length() >= max) return
        }
    }

    private fun performFirstScrollable(node: AccessibilityNodeInfo?, action: Int): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.performAction(action)) return true
        for (i in 0 until node.childCount) {
            if (performFirstScrollable(node.getChild(i), action)) return true
        }
        return false
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            findFocusedEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findInputFocus(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            findFocusedNode(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            findFirstEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findFirstActionNode(node: AccessibilityNodeInfo?, actionId: Int): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.actionList.any { it.id == actionId }) return node
        for (i in 0 until node.childCount) {
            findFirstActionNode(node.getChild(i), actionId)?.let { return it }
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val haystack = "${node.text ?: ""} ${node.contentDescription ?: ""}"
        if (haystack.contains(text, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i), text)?.let { return it }
        }
        return null
    }

    private fun JSONObject.optDouble01(name: String, fallback: Double = Double.NaN): Double {
        val value = if (has(name)) optDouble(name, fallback) else fallback
        return if (value.isFinite()) value.coerceIn(0.0, 1.0) else value
    }

    companion object {
        private var activeService: WeakReference<AgentAccessibilityService>? = null

        fun isEnabled(): Boolean = activeService?.get() != null

        fun execute(rawStep: String?): JSONObject {
            val service = activeService?.get()
                ?: return JSONObject().put("ok", false).put("error", "accessibility_service_not_enabled")
            val step = runCatching { JSONObject(rawStep ?: "{}") }.getOrElse { JSONObject() }
            return service.executeStep(step)
        }

        fun observe(): JSONObject {
            val service = activeService?.get()
                ?: return JSONObject().put("ok", false).put("error", "accessibility_service_not_enabled")
            return service.observeScreen()
        }
    }
}
