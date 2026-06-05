package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiAgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        configureServiceInfo()
        ScreenObservationStore.markConnectedWaitingForWindow()
        startAgentForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        configureServiceInfo()
        val packageName = event?.packageName?.toString().orEmpty()
        val windowTitle = event?.text?.firstOrNull()?.toString().orEmpty()
        if (packageName.isNotBlank()) ScreenObservationStore.updateWindowHint(packageName, windowTitle)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        ScreenObservationStore.markDisabled()
        super.onDestroy()
    }

    private fun configureServiceInfo() {
        val current = serviceInfo ?: return
        current.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
            AccessibilityEvent.TYPE_VIEW_CLICKED or
            AccessibilityEvent.TYPE_VIEW_FOCUSED
        current.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        current.flags = current.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        current.notificationTimeout = 80L
        serviceInfo = current
    }

    private fun captureSnapshotInternal(forceVisual: Boolean = false): ScreenObservation {
        configureServiceInfo()
        val now = System.currentTimeMillis()
        val selected = selectBestRootCapture(limit = MAX_SNAPSHOT_NODES)
        val nodeObservation = if (selected != null) {
            val nodes = selected.capture.handles.map { it.observed }
            ScreenObservation(
                enabled = true,
                serviceConnected = true,
                packageName = selected.packageName,
                windowTitle = selected.windowTitle,
                updatedAt = now,
                textItems = nodes.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }.distinct().take(TEXT_LIMIT),
                clickableItems = nodes.filter { it.clickable }.distinctBy { it.bounds + it.text }.take(CLICKABLE_LIMIT),
                inputItems = nodes.filter { it.editable }.distinctBy { it.bounds }.take(INPUT_LIMIT),
                scrollableItems = nodes.filter { it.scrollable }.distinctBy { it.bounds }.take(SCROLLABLE_LIMIT),
                nodeCount = selected.capture.rawNodeCount,
            )
        } else {
            ScreenObservation(
                enabled = true,
                serviceConnected = true,
                updatedAt = now,
            )
        }
        val shouldCaptureVisual = forceVisual || shouldUseVisualFallback(nodeObservation)
        val visual = if (shouldCaptureVisual) captureVisualObservation(reasonForVisualFallback(nodeObservation, forceVisual)) else null
        return nodeObservation.copy(visual = visual)
    }

    private fun shouldUseVisualFallback(observation: ScreenObservation): Boolean {
        return observation.nodeCount <= 8 || observation.textItems.isEmpty() || observation.clickableItems.isEmpty()
    }

    private fun reasonForVisualFallback(observation: ScreenObservation, forceVisual: Boolean): String {
        if (forceVisual) return "forced"
        return "low_accessibility_confidence:nodes=${observation.nodeCount},texts=${observation.textItems.size},clickable=${observation.clickableItems.size}"
    }

    private fun captureVisualObservation(reason: String): ScreenVisualObservation {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ScreenVisualObservation(available = false, source = "unsupported", reason = "takeScreenshot requires Android 11+")
        }
        return runCatching { captureDisplayScreenshotCompat(reason) }.getOrElse { error ->
            ScreenVisualObservation(available = false, source = "error", reason = error.message ?: "screenshot_failed")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureDisplayScreenshotCompat(reason: String): ScreenVisualObservation {
        val latch = CountDownLatch(1)
        var result = ScreenVisualObservation(available = false, source = "pending", reason = reason)
        val executor = Executors.newSingleThreadExecutor()
        try {
            takeScreenshot(
                0,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        result = screenshot.toBitmapCopy()?.toVisualObservation(reason)
                            ?: ScreenVisualObservation(available = false, source = "empty", reason = "screenshot bitmap empty")
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        result = ScreenVisualObservation(available = false, source = "error", reason = "screenshot error=$errorCode")
                        latch.countDown()
                    }
                },
            )
            latch.await(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            executor.shutdown()
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toBitmapCopy(): Bitmap? {
        val buffer = hardwareBuffer ?: return null
        return try {
            Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            buffer.close()
        }
    }

    private fun Bitmap.toVisualObservation(reason: String): ScreenVisualObservation {
        val originalWidth = width
        val originalHeight = height
        val longSide = maxOf(originalWidth, originalHeight).coerceAtLeast(1)
        val scale = (VISION_MAX_LONG_SIDE.toFloat() / longSide.toFloat()).coerceAtMost(1f)
        val targetWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(1)
        val scaled = if (targetWidth != originalWidth || targetHeight != originalHeight) {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        } else this
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, output)
        if (scaled !== this) scaled.recycle()
        recycle()
        return ScreenVisualObservation(
            available = true,
            mimeType = "image/jpeg",
            width = targetWidth,
            height = targetHeight,
            base64Jpeg = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
            source = "accessibility_takeScreenshot",
            reason = reason,
            capturedAt = System.currentTimeMillis(),
        )
    }

    private fun selectBestRoot(): AccessibilityNodeInfo? {
        return selectBestRootCapture(limit = ROOT_SELECTION_SAMPLE_NODES)?.root ?: rootInActiveWindow
    }

    private fun selectBestRootCapture(limit: Int): RootCapture? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { candidates += it }
        windows.orEmpty().forEach { window -> window.root?.let { candidates += it } }
        return candidates
            .distinctBy { root ->
                val rect = Rect()
                root.getBoundsInScreen(rect)
                listOf(root.packageName?.toString().orEmpty(), root.className?.toString().orEmpty(), rect.flattenToString()).joinToString("|")
            }
            .mapNotNull { root ->
                val capture = collectNodeHandles(root, limit)
                val packageName = root.packageName?.toString().orEmpty()
                val textCount = capture.handles.count { it.observed.text.isNotBlank() }
                val clickableCount = capture.handles.count { it.observed.clickable }
                val inputCount = capture.handles.count { it.observed.editable }
                val scrollableCount = capture.handles.count { it.observed.scrollable }
                val ownOverlayPenalty = if (packageName == applicationContext.packageName) 10_000 else 0
                val score = capture.rawNodeCount * 2 +
                    capture.handles.size * 8 +
                    textCount * 10 +
                    clickableCount * 12 +
                    inputCount * 16 +
                    scrollableCount * 10 -
                    ownOverlayPenalty
                RootCapture(
                    root = root,
                    packageName = packageName,
                    windowTitle = root.text?.toString().orEmpty(),
                    capture = capture,
                    score = score,
                )
            }
            .maxByOrNull { it.score }
    }

    private fun collectNodeHandles(root: AccessibilityNodeInfo, limit: Int = MAX_EXECUTION_NODES): NodeCapture {
        val result = mutableListOf<NodeHandle>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var rawCount = 0
        var index = 0
        while (queue.isNotEmpty() && result.size < limit) {
            val (node, depth) = queue.removeFirst()
            rawCount += 1
            node.toHandleOrNull("n${index}")?.let { handle ->
                result.add(handle)
                index += 1
            }
            if (depth < MAX_DEPTH) {
                for (childIndex in 0 until node.childCount) {
                    val child = node.getChild(childIndex) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        while (queue.isNotEmpty()) {
            rawCount += 1
            queue.removeFirst()
        }
        return NodeCapture(rawNodeCount = rawCount, handles = result)
    }

    private fun AccessibilityNodeInfo.safeText(): String {
        return text?.toString()?.takeIf { it.isNotBlank() }
            ?: contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: hintText?.toString()?.takeIf { it.isNotBlank() }
            ?: collectChildText(CHILD_TEXT_FALLBACK_LIMIT)
    }

    private fun AccessibilityNodeInfo.collectChildText(limit: Int): String {
        val parts = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (childIndex in 0 until childCount) getChild(childIndex)?.let { queue.add(it) }
        while (queue.isNotEmpty() && parts.size < limit) {
            val child = queue.removeFirst()
            val text = child.text?.toString()?.takeIf { it.isNotBlank() }
                ?: child.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?: child.hintText?.toString()?.takeIf { it.isNotBlank() }
            if (!text.isNullOrBlank()) parts += text.take(24)
            for (childIndex in 0 until child.childCount) child.getChild(childIndex)?.let { queue.add(it) }
        }
        return parts.distinct().joinToString(" ").take(80)
    }

    private fun executeStepInternal(step: CloudAgentStep): AgentExecutionResult {
        configureServiceInfo()
        return when (step.type) {
            "open_app" -> executeOpenApp(step)
            "back" -> executeGlobalActionStep(GLOBAL_ACTION_BACK, "返回")
            "home" -> executeGlobalActionStep(GLOBAL_ACTION_HOME, "回到桌面")
            "recents" -> executeGlobalActionStep(GLOBAL_ACTION_RECENTS, "打开最近任务")
            "notifications" -> executeGlobalActionStep(GLOBAL_ACTION_NOTIFICATIONS, "下拉通知栏")
            "quick_settings" -> executeGlobalActionStep(GLOBAL_ACTION_QUICK_SETTINGS, "打开快捷设置")
            "tap_node" -> executeTapNode(step)
            "tap_xy" -> executeTapXY(step)
            "input_text" -> executeInputText(step)
            "scroll" -> executeScroll(step)
            "swipe" -> executeSwipe(step)
            "wait" -> AgentExecutionResult(ok = true, message = "等待 ${step.durationMs ?: DEFAULT_WAIT_MS}ms")
            "finish" -> AgentExecutionResult(ok = true, message = "任务完成", shouldContinue = false)
            "need_user_help" -> AgentExecutionResult(ok = false, message = step.reason ?: "需要用户协助", shouldContinue = false)
            else -> AgentExecutionResult(ok = false, message = "不支持的动作：${step.type}", shouldContinue = false)
        }
    }

    private fun executeOpenApp(step: CloudAgentStep): AgentExecutionResult {
        val explicitPackage = step.packageName?.takeIf { it.isNotBlank() }
        val app = explicitPackage?.let { InstalledAppEntry(step.appName ?: it, it) }
            ?: step.appName?.let { InstalledAppIndex(this).findBestApp(it) }
            ?: step.targetText?.let { InstalledAppIndex(this).findBestApp(it) }
        val packageName = app?.packageName ?: return AgentExecutionResult(false, "没有找到要打开的应用：${step.appName ?: step.targetText ?: "未知"}", false)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AgentExecutionResult(false, "应用没有可启动入口：$packageName", false)
        return runCatching {
            startActivity(launchIntent)
            AgentExecutionResult(true, "已打开${app.label.ifBlank { packageName }}")
        }.getOrElse { AgentExecutionResult(false, "打开应用失败：${it.message ?: packageName}", false) }
    }

    private fun executeGlobalActionStep(action: Int, label: String): AgentExecutionResult {
        val ok = performGlobalAction(action)
        return AgentExecutionResult(ok = ok, message = if (ok) "已执行：$label" else "执行失败：$label", shouldContinue = ok)
    }

    private fun executeTapNode(step: CloudAgentStep): AgentExecutionResult {
        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        val quickText = step.targetText?.takeIf { it.isNotBlank() }
        if (quickText != null) {
            findNodeByTextFast(root, quickText)?.let { handle ->
                performClickSmart(handle.node)?.let { return AgentExecutionResult(true, "已快速点击 ${handle.observed.text.ifBlank { quickText }}") }
                return tapRect(handle.bounds, "已坐标点击 ${handle.observed.text.ifBlank { quickText }}")
            }
        }
        val handles = collectNodeHandles(root, MAX_EXECUTION_NODES).handles
        val target = findTargetHandle(handles, step)
            ?: return AgentExecutionResult(false, "没有找到目标节点：${step.targetNodeId ?: step.targetText ?: "未知"}", false)
        performClickSmart(target.node)?.let {
            return AgentExecutionResult(true, "已点击节点 ${target.observed.id} ${target.observed.text}".trim())
        }
        return tapRect(target.bounds, "已坐标点击节点 ${target.observed.id}")
    }

    private fun executeTapXY(step: CloudAgentStep): AgentExecutionResult {
        val x = step.x ?: return AgentExecutionResult(false, "缺少点击坐标 x", false)
        val y = step.y ?: return AgentExecutionResult(false, "缺少点击坐标 y", false)
        return dispatchTap(x, y, "已点击坐标 ${x.toInt()},${y.toInt()}")
    }

    private fun executeInputText(step: CloudAgentStep): AgentExecutionResult {
        val text = step.text?.takeIf { it.isNotBlank() } ?: return AgentExecutionResult(false, "缺少输入内容", false)
        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        val handles = collectNodeHandles(root, MAX_EXECUTION_NODES).handles
        val target = findTargetHandle(handles, step) ?: handles.firstOrNull { it.observed.editable }
            ?: return AgentExecutionResult(false, "没有找到输入框", false)
        target.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return AgentExecutionResult(ok = ok, message = if (ok) "已输入文字" else "输入文字失败", shouldContinue = ok)
    }

    private fun executeScroll(step: CloudAgentStep): AgentExecutionResult {
        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        val handles = collectNodeHandles(root, MAX_EXECUTION_NODES).handles
        val target = findTargetHandle(handles, step) ?: handles.firstOrNull { it.observed.scrollable }
        val direction = step.direction.orEmpty().lowercase()
        val action = if (direction in setOf("up", "left", "backward", "previous")) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        if (target?.node?.isScrollable == true && target.node.performAction(action)) {
            return AgentExecutionResult(true, "已滚动节点 ${target.observed.id}")
        }
        return executeSwipe(step.copy(type = "swipe", direction = direction.ifBlank { "up" }))
    }

    private fun executeSwipe(step: CloudAgentStep): AgentExecutionResult {
        val direction = step.direction.orEmpty().lowercase().ifBlank { "up" }
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float
        when (direction) {
            "down" -> { startX = w * 0.5f; startY = h * 0.32f; endX = w * 0.5f; endY = h * 0.72f }
            "left" -> { startX = w * 0.78f; startY = h * 0.5f; endX = w * 0.22f; endY = h * 0.5f }
            "right" -> { startX = w * 0.22f; startY = h * 0.5f; endX = w * 0.78f; endY = h * 0.5f }
            else -> { startX = w * 0.5f; startY = h * 0.72f; endX = w * 0.5f; endY = h * 0.32f }
        }
        val ok = dispatchSwipe(startX, startY, endX, endY, step.durationMs ?: DEFAULT_SWIPE_MS)
        return AgentExecutionResult(ok = ok, message = if (ok) "已滑动：$direction" else "滑动失败：$direction", shouldContinue = ok)
    }

    private fun findNodeByTextFast(root: AccessibilityNodeInfo, targetText: String): NodeHandle? {
        val exact = root.findAccessibilityNodeInfosByText(targetText).orEmpty()
            .mapNotNull { it.toHandleOrNull("n-fast") }
            .firstOrNull { it.observed.text == targetText || it.observed.text.contains(targetText, ignoreCase = true) }
        if (exact != null) return exact
        return collectNodeHandles(root, FAST_TEXT_FALLBACK_NODES).handles.firstOrNull { handle ->
            handle.observed.text == targetText || handle.observed.text.contains(targetText, ignoreCase = true)
        }
    }

    private fun AccessibilityNodeInfo.toHandleOrNull(id: String): NodeHandle? {
        val clickableNode = nearestClickableNode()
        val scrollableNode = nearestScrollableNode()
        val actionNode = when {
            isEditable -> this
            clickableNode != null -> clickableNode
            scrollableNode != null -> scrollableNode
            else -> this
        }
        val text = safeText().ifBlank { actionNode.safeText() }
        val hasUsefulSignal = text.isNotBlank() || clickableNode != null || isEditable || scrollableNode != null
        if (!hasUsefulSignal) return null
        val rect = Rect()
        actionNode.getBoundsInScreen(rect)
        if (rect.isEmpty) getBoundsInScreen(rect)
        if (rect.isEmpty) return null
        val observed = ObservedScreenNode(
            id = id,
            text = text.take(80),
            className = actionNode.className?.toString().orEmpty().substringAfterLast('.').take(32),
            bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
            clickable = clickableNode != null,
            editable = isEditable,
            scrollable = scrollableNode != null,
        )
        return NodeHandle(observed = observed, node = actionNode, bounds = rect)
    }

    private fun AccessibilityNodeInfo.nearestClickableNode(): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        while (current != null && depth < CLICK_PARENT_DEPTH) {
            if (current.isClickable || current.isLongClickable) return current
            current = current.parent
            depth += 1
        }
        return null
    }

    private fun AccessibilityNodeInfo.nearestScrollableNode(): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        while (current != null && depth < SCROLL_PARENT_DEPTH) {
            if (current.isScrollable) return current
            current = current.parent
            depth += 1
        }
        return null
    }

    private fun performClickSmart(node: AccessibilityNodeInfo): Boolean? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < CLICK_PARENT_DEPTH) {
            if ((current.isClickable || current.isLongClickable) && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            depth += 1
        }
        return null
    }

    private fun findTargetHandle(handles: List<NodeHandle>, step: CloudAgentStep): NodeHandle? {
        step.targetNodeId?.let { id -> handles.firstOrNull { it.observed.id == id }?.let { return it } }
        val targetText = step.targetText?.takeIf { it.isNotBlank() } ?: step.text?.takeIf { it.isNotBlank() }
        if (targetText != null) {
            handles.firstOrNull { it.observed.text == targetText }?.let { return it }
            handles.firstOrNull { it.observed.text.contains(targetText, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun tapRect(rect: Rect, successMessage: String): AgentExecutionResult {
        if (rect.isEmpty) return AgentExecutionResult(false, "目标区域无效", false)
        return dispatchTap(rect.centerX().toFloat(), rect.centerY().toFloat(), successMessage)
    }

    private fun dispatchTap(x: Float, y: Float, successMessage: String): AgentExecutionResult {
        val (safeX, safeY) = safeTapPoint(x, y)
        val path = Path().apply { moveTo(safeX, safeY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_TAP_MS))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        return AgentExecutionResult(ok = ok, message = if (ok) successMessage else "点击手势提交失败", shouldContinue = ok)
    }

    private fun safeTapPoint(x: Float, y: Float): Pair<Float, Float> {
        val metrics = resources.displayMetrics
        val density = metrics.density.coerceAtLeast(1f)
        val minX = 4f * density
        val maxX = (metrics.widthPixels - 4f * density).coerceAtLeast(minX)
        val minY = 24f * density
        val maxY = (metrics.heightPixels - 28f * density).coerceAtLeast(minY)
        return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(160L, 900L)))
            .build()
        return dispatchGesture(gesture, null, null)
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

    private data class RootCapture(
        val root: AccessibilityNodeInfo,
        val packageName: String,
        val windowTitle: String,
        val capture: NodeCapture,
        val score: Int,
    )

    private data class NodeCapture(
        val rawNodeCount: Int,
        val handles: List<NodeHandle>,
    )

    private data class NodeHandle(
        val observed: ObservedScreenNode,
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
    )

    companion object {
        @Volatile private var activeService: AiAgentAccessibilityService? = null

        fun captureFreshSnapshot(forceVisual: Boolean = false): ScreenObservation {
            val service = activeService ?: return ScreenObservation(updatedAt = System.currentTimeMillis())
            val snapshot = service.captureSnapshotInternal(forceVisual = forceVisual)
            ScreenObservationStore.update(snapshot)
            return snapshot
        }

        fun executeStep(step: CloudAgentStep): AgentExecutionResult {
            val service = activeService ?: return AgentExecutionResult(false, "无障碍服务未连接", false)
            return service.executeStepInternal(step)
        }

        private const val AGENT_CHANNEL_ID = "ai_agent_accessibility_status"
        private const val AGENT_NOTIFICATION_ID = 7301
        private const val MAX_SNAPSHOT_NODES = 240
        private const val MAX_EXECUTION_NODES = 260
        private const val ROOT_SELECTION_SAMPLE_NODES = 120
        private const val FAST_TEXT_FALLBACK_NODES = 140
        private const val MAX_DEPTH = 14
        private const val CHILD_TEXT_FALLBACK_LIMIT = 8
        private const val TEXT_LIMIT = 60
        private const val CLICKABLE_LIMIT = 56
        private const val INPUT_LIMIT = 12
        private const val SCROLLABLE_LIMIT = 16
        private const val CLICK_PARENT_DEPTH = 12
        private const val SCROLL_PARENT_DEPTH = 14
        private const val DEFAULT_TAP_MS = 48L
        private const val DEFAULT_SWIPE_MS = 300L
        private const val DEFAULT_WAIT_MS = 650L
        private const val VISION_MAX_LONG_SIDE = 960
        private const val VISION_JPEG_QUALITY = 68
        private const val SCREENSHOT_TIMEOUT_MS = 1200L
    }
}
