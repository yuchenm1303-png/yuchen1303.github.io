package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiAgentAccessibilityService : AccessibilityService() {
    @Volatile private var lastWindowHintAtMs: Long = 0L
    @Volatile private var lastWindowHintKey: String = ""
    @Volatile private var currentAccessibilityMode: AccessibilityRuntimeMode = AccessibilityRuntimeMode.Idle
    @Volatile private var foregroundNotificationStarted: Boolean = false

    private val modeLock = Any()
    private var workingSessionDepth: Int = 0
    private var taskSessionDepth: Int = 0

    private val screenshotExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ai-agent-screenshot").apply { isDaemon = true }
    }
    private val deviceShellBridge by lazy(LazyThreadSafetyMode.NONE) {
        DeviceShellBridge(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        configureIdleServiceInfo(force = true)
        ScreenObservationStore.markConnectedWaitingForWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 只有短暂 Working 窗口才处理事件，Idle 完全忽略。
        if (!isWorkingMode()) return
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || isSystemSurfacePackage(packageName)) return

        val windowTitle = event.text?.firstOrNull()?.toString().orEmpty()
        val now = System.currentTimeMillis()
        val key = "$packageName|$windowTitle"
        if (key == lastWindowHintKey && now - lastWindowHintAtMs < WINDOW_HINT_THROTTLE_MS) return

        lastWindowHintKey = key
        lastWindowHintAtMs = now
        ScreenObservationStore.updateWindowHint(packageName, windowTitle)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        screenshotExecutor.shutdownNow()
        stopTaskForegroundNotification()
        ScreenObservationStore.markDisabled()
        super.onDestroy()
    }

    private fun isWorkingMode(): Boolean = currentAccessibilityMode == AccessibilityRuntimeMode.Working

    private fun configureIdleServiceInfo(force: Boolean = false) {
        if (!force && currentAccessibilityMode == AccessibilityRuntimeMode.Idle) return
        val current = serviceInfo ?: return
        current.eventTypes = IDLE_EVENT_TYPES
        current.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        current.flags = IDLE_ACCESSIBILITY_FLAGS
        current.notificationTimeout = IDLE_NOTIFICATION_TIMEOUT_MS
        serviceInfo = current
        lastWindowHintKey = ""
        lastWindowHintAtMs = 0L
        currentAccessibilityMode = AccessibilityRuntimeMode.Idle
        if (taskSessionDepth == 0 && workingSessionDepth == 0) stopTaskForegroundNotification()
    }

    private fun configureWorkingServiceInfo() {
        if (currentAccessibilityMode == AccessibilityRuntimeMode.Working) return
        val current = serviceInfo ?: return
        current.eventTypes = WORKING_EVENT_TYPES
        current.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        current.flags = WORKING_ACCESSIBILITY_FLAGS
        current.notificationTimeout = WORKING_NOTIFICATION_TIMEOUT_MS
        serviceInfo = current
        currentAccessibilityMode = AccessibilityRuntimeMode.Working
    }

    private fun beginTaskWorkingSession() {
        synchronized(modeLock) {
            taskSessionDepth += 1
            startTaskForegroundNotification()
            // 彻底轻量化：任务会话只保留通知，不常驻 Working。
            // 真正需要窗口/节点/截图时，由 withWorkingAccessibilityMode() 短暂开启，结束立即回 Idle。
            configureIdleServiceInfo(force = true)
        }
    }

    private fun endTaskWorkingSession() {
        synchronized(modeLock) {
            taskSessionDepth = (taskSessionDepth - 1).coerceAtLeast(0)
            if (workingSessionDepth == 0) configureIdleServiceInfo(force = true)
        }
    }

    private fun beginWorkingSession() {
        synchronized(modeLock) {
            workingSessionDepth += 1
            configureWorkingServiceInfo()
        }
    }

    private fun endWorkingSession() {
        synchronized(modeLock) {
            workingSessionDepth = (workingSessionDepth - 1).coerceAtLeast(0)
            if (workingSessionDepth == 0) configureIdleServiceInfo(force = true)
        }
    }

    private inline fun <T> withWorkingAccessibilityMode(block: () -> T): T {
        beginWorkingSession()
        return try {
            block()
        } finally {
            endWorkingSession()
        }
    }

    private fun captureSnapshotInternal(forceVisual: Boolean = false): ScreenObservation = withWorkingAccessibilityMode {
        val now = System.currentTimeMillis()
        val startedAt = SystemClock.elapsedRealtime()
        val nodeLimit = if (forceVisual) VISUAL_AFFORDANCE_NODES else MAX_SNAPSHOT_NODES
        val nodeBudgetMs = if (forceVisual) VISUAL_AFFORDANCE_BUDGET_MS else SNAPSHOT_NODE_BUDGET_MS
        val selected = selectBestRootCapture(limit = nodeLimit, timeBudgetMs = nodeBudgetMs)

        val nodeObservation = if (selected != null) {
            val nodes = selected.capture.handles.map { it.observed }
            val nodeMs = SystemClock.elapsedRealtime() - startedAt
            val title = buildString {
                append(selected.windowTitle)
                append(" · nodeMs=").append(nodeMs)
                if (selected.capture.truncated) append(" · 节点截断")
                if (forceVisual) append(" · 极简视觉节点")
            }.take(120)

            ScreenObservation(
                enabled = true,
                serviceConnected = true,
                packageName = selected.packageName,
                windowTitle = title,
                updatedAt = now,
                textItems = nodes.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }
                    .distinct()
                    .take(TEXT_LIMIT),
                allItems = nodes.distinctBy { it.bounds + it.text + it.className }
                    .take(ALL_NODE_LIMIT),
                clickableItems = nodes.filter { it.clickable }
                    .distinctBy { it.bounds + it.text }
                    .take(CLICKABLE_LIMIT),
                inputItems = nodes.filter { it.editable }
                    .distinctBy { it.bounds }
                    .take(INPUT_LIMIT),
                scrollableItems = nodes.filter { it.scrollable }
                    .distinctBy { it.bounds }
                    .take(SCROLLABLE_LIMIT),
                nodeCount = selected.capture.rawNodeCount,
                capturedNodeCount = selected.capture.handles.size,
            )
        } else {
            ScreenObservation(enabled = true, serviceConnected = true, updatedAt = now)
        }

        val visual = if (forceVisual) captureVisualObservation("forced") else null
        nodeObservation.copy(visual = visual)
    }

    private fun captureVisualObservation(reason: String): ScreenVisualObservation {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ScreenVisualObservation(
                available = false,
                source = "unsupported",
                reason = "takeScreenshot requires Android 11+",
            )
        }
        return runCatching { captureDisplayScreenshotCompat(reason) }.getOrElse { error ->
            ScreenVisualObservation(
                available = false,
                source = "error",
                reason = error.message ?: "screenshot_failed",
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureDisplayScreenshotCompat(reason: String): ScreenVisualObservation {
        AgentRuntimeController.beginCleanVisualCapture()
        return try {
            SystemClock.sleep(OVERLAY_HIDE_BEFORE_SCREENSHOT_MS)
            val latch = CountDownLatch(1)
            var result = ScreenVisualObservation(available = false, source = "pending", reason = reason)
            takeScreenshot(
                0,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        result = screenshot.toBitmapCopy()?.toVisualObservation(reason)
                            ?: ScreenVisualObservation(
                                available = false,
                                source = "empty",
                                reason = "screenshot bitmap empty",
                            )
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        result = ScreenVisualObservation(
                            available = false,
                            source = "error",
                            reason = "screenshot error=$errorCode",
                        )
                        latch.countDown()
                    }
                },
            )
            latch.await(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            result
        } finally {
            AgentRuntimeController.endCleanVisualCapture()
        }
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
        val encoded = try {
            VisualScreenshotEncoder.encode(this)
        } finally {
            recycle()
        }
        return ScreenVisualObservation(
            available = true,
            mimeType = "image/jpeg",
            width = encoded.width,
            height = encoded.height,
            displayWidth = originalWidth,
            displayHeight = originalHeight,
            base64Jpeg = Base64.encodeToString(encoded.bytes, Base64.NO_WRAP),
            source = "accessibility_takeScreenshot_high_resolution",
            reason = "$reason · ${encoded.width}x${encoded.height} · q${encoded.quality} · ${encoded.bytes.size / 1024}KB",
            capturedAt = System.currentTimeMillis(),
        )
    }

    private fun selectBestRoot(): AccessibilityNodeInfo? {
        return selectBestRootCapture(
            limit = ROOT_SELECTION_SAMPLE_NODES,
            timeBudgetMs = ROOT_SELECTION_BUDGET_MS,
        )?.root ?: rootInActiveWindow
    }

    private fun selectBestRootCapture(
        limit: Int,
        timeBudgetMs: Long = NODE_CAPTURE_DEFAULT_BUDGET_MS,
    ): RootCapture? {
        val deadlineMs = SystemClock.elapsedRealtime() + timeBudgetMs
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { candidates += it }
        runCatching {
            windows.orEmpty().forEach { window ->
                window.root?.let { candidates += it }
            }
        }

        val distinctCandidates = candidates
            .distinctBy { rootIdentity(it) }
            .sortedByDescending { rootPriority(it) }
            .take(MAX_ROOT_CANDIDATES)

        var best: RootCapture? = null
        for (root in distinctCandidates) {
            if (SystemClock.elapsedRealtime() >= deadlineMs) break
            val capture = collectNodeHandles(root, limit, deadlineMs)
            val packageName = root.packageName?.toString().orEmpty()
            val textCount = capture.handles.count { it.observed.text.isNotBlank() }
            val clickableCount = capture.handles.count { it.observed.clickable }
            val inputCount = capture.handles.count { it.observed.editable }
            val scrollableCount = capture.handles.count { it.observed.scrollable }
            val ownOverlayPenalty = if (packageName == applicationContext.packageName) OWN_OVERLAY_WINDOW_PENALTY else 0
            val systemSurfacePenalty = if (isSystemSurfacePackage(packageName)) SYSTEM_SURFACE_WINDOW_PENALTY else 0
            val contentAppBonus = if (packageName.isNotBlank() && !isSystemSurfacePackage(packageName) && packageName != applicationContext.packageName) {
                CONTENT_APP_WINDOW_BONUS
            } else {
                0
            }
            val truncatedPenalty = if (capture.truncated) TRUNCATED_CAPTURE_PENALTY else 0

            val item = RootCapture(
                root = root,
                packageName = packageName,
                windowTitle = root.text?.toString().orEmpty(),
                capture = capture,
                score = capture.rawNodeCount * 2 +
                    capture.handles.size * 8 +
                    textCount * 10 +
                    clickableCount * 12 +
                    inputCount * 16 +
                    scrollableCount * 10 +
                    contentAppBonus -
                    ownOverlayPenalty -
                    systemSurfacePenalty -
                    truncatedPenalty,
            )
            if (best == null || item.score > best.score) best = item
        }
        return best
    }

    private fun rootIdentity(root: AccessibilityNodeInfo): String {
        val rect = Rect()
        runCatching { root.getBoundsInScreen(rect) }
        return listOf(
            root.packageName?.toString().orEmpty(),
            root.className?.toString().orEmpty(),
            rect.flattenToString(),
        ).joinToString("|")
    }

    private fun rootPriority(root: AccessibilityNodeInfo): Int {
        val packageName = root.packageName?.toString().orEmpty()
        val isContent = packageName.isNotBlank() &&
            !isSystemSurfacePackage(packageName) &&
            packageName != applicationContext.packageName
        return when {
            isContent -> 3
            packageName == applicationContext.packageName -> 1
            else -> 0
        }
    }

    private fun isSystemSurfacePackage(packageName: String): Boolean {
        return packageName in SYSTEM_SURFACE_PACKAGES
    }

    private fun collectNodeHandles(
        root: AccessibilityNodeInfo,
        limit: Int = MAX_EXECUTION_NODES,
        deadlineMs: Long = SystemClock.elapsedRealtime() + NODE_CAPTURE_DEFAULT_BUDGET_MS,
    ): NodeCapture {
        val result = mutableListOf<NodeHandle>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var rawCount = 0
        var index = 0
        var truncated = false

        while (queue.isNotEmpty() && result.size < limit) {
            if (SystemClock.elapsedRealtime() >= deadlineMs) {
                truncated = true
                break
            }
            val (node, depth) = queue.removeFirst()
            rawCount += 1
            node.toHandleOrNull("n$index", deadlineMs)?.let { handle ->
                result.add(handle)
                index += 1
            }
            if (depth < MAX_DEPTH && SystemClock.elapsedRealtime() < deadlineMs) {
                val childLimit = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
                for (childIndex in 0 until childLimit) {
                    if (SystemClock.elapsedRealtime() >= deadlineMs || queue.size >= MAX_PENDING_NODE_QUEUE) {
                        truncated = true
                        break
                    }
                    node.getChild(childIndex)?.let { queue.add(it to depth + 1) }
                }
                if (node.childCount > childLimit) truncated = true
            }
        }

        if (queue.isNotEmpty() || result.size >= limit) truncated = true
        return NodeCapture(rawNodeCount = rawCount, handles = result, truncated = truncated)
    }

    private fun AccessibilityNodeInfo.directText(): String {
        return text?.toString()?.takeIf { it.isNotBlank() }
            ?: contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: hintText?.toString()?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun AccessibilityNodeInfo.safeText(
        deadlineMs: Long = SystemClock.elapsedRealtime() + CHILD_TEXT_BUDGET_MS,
    ): String {
        val direct = directText()
        if (direct.isNotBlank()) return direct
        return if (SystemClock.elapsedRealtime() < deadlineMs) {
            collectChildText(CHILD_TEXT_FALLBACK_LIMIT, deadlineMs)
        } else {
            ""
        }
    }

    private fun AccessibilityNodeInfo.collectChildText(
        limit: Int,
        deadlineMs: Long = SystemClock.elapsedRealtime() + CHILD_TEXT_BUDGET_MS,
    ): String {
        val parts = mutableListOf<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val childLimit = childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (childIndex in 0 until childLimit) {
            getChild(childIndex)?.let { queue.add(it to 0) }
        }

        while (queue.isNotEmpty() && parts.size < limit && SystemClock.elapsedRealtime() < deadlineMs) {
            val (child, depth) = queue.removeFirst()
            val text = child.directText()
            if (text.isNotBlank()) parts += text.take(24)
            if (depth < CHILD_TEXT_MAX_DEPTH) {
                val nestedLimit = child.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
                for (childIndex in 0 until nestedLimit) {
                    if (SystemClock.elapsedRealtime() >= deadlineMs || queue.size >= MAX_CHILD_TEXT_QUEUE) break
                    child.getChild(childIndex)?.let { queue.add(it to depth + 1) }
                }
            }
        }

        return parts.distinct().joinToString(" ").take(80)
    }

    private fun executeStepInternal(step: CloudAgentStep): AgentExecutionResult {
        SystemClock.sleep(OVERLAY_HIDE_BEFORE_ACTION_MS)
        return if (step.requiresWindowContent()) {
            withWorkingAccessibilityMode { executeStepInternalUnchecked(step) }
        } else {
            executeStepInternalUnchecked(step)
        }
    }

    private fun executeStepInternalUnchecked(step: CloudAgentStep): AgentExecutionResult {
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

    private fun CloudAgentStep.requiresWindowContent(): Boolean {
        return type in setOf("tap_node", "input_text", "scroll")
    }

    private fun executeOpenApp(step: CloudAgentStep): AgentExecutionResult {
        val packageName = step.packageName?.takeIf { it.isNotBlank() }
            ?: step.appName?.takeIf { it.isNotBlank() }?.let { resolvePackageByLabel(it) }
            ?: return AgentExecutionResult(ok = false, message = "缺少应用包名", shouldContinue = false)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return AgentExecutionResult(ok = false, message = "无法启动应用：$packageName", shouldContinue = false)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return runCatching {
            startActivity(intent)
            AgentExecutionResult(ok = true, message = "已打开${step.appName ?: packageName}")
        }.getOrElse {
            AgentExecutionResult(ok = false, message = "打开应用失败：${it.message ?: packageName}", shouldContinue = false)
        }
    }

    private fun resolvePackageByLabel(label: String): String? {
        val query = label.trim()
        if (query.isBlank()) return null
        val apps = packageManager.getInstalledApplications(0)
        return apps.firstOrNull { app ->
            runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault("")
                .equals(query, ignoreCase = true)
        }?.packageName ?: apps.firstOrNull { app ->
            runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault("")
                .contains(query, ignoreCase = true)
        }?.packageName
    }

    private fun executeGlobalActionStep(action: Int, label: String): AgentExecutionResult {
        val ok = performGlobalAction(action)
        return AgentExecutionResult(ok = ok, message = if (ok) "已执行：$label" else "执行失败：$label", shouldContinue = ok)
    }

    private fun executeTapNode(step: CloudAgentStep): AgentExecutionResult {
        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        step.targetText?.takeIf { it.isNotBlank() }?.let { text ->
            findNodeByTextFast(root, text)?.let { return tapRect(it.bounds, "已点击：$text") }
        }
        val handles = collectNodeHandles(
            root,
            MAX_EXECUTION_NODES,
            SystemClock.elapsedRealtime() + EXECUTION_NODE_BUDGET_MS,
        ).handles
        val target = findTargetHandle(handles, step)
            ?: return AgentExecutionResult(false, "没有找到目标节点：${step.targetText ?: step.targetNodeId ?: ""}", false)
        performClickSmart(target.node)?.let { ok ->
            return AgentExecutionResult(ok = ok, message = if (ok) "已点击节点 ${target.observed.id}" else "节点点击失败", shouldContinue = ok)
        }
        return tapRect(target.bounds, "已点击节点 ${target.observed.id}")
    }

    private fun executeTapXY(step: CloudAgentStep): AgentExecutionResult {
        val x = step.x ?: return AgentExecutionResult(false, "缺少点击 x 坐标", false)
        val y = step.y ?: return AgentExecutionResult(false, "缺少点击 y 坐标", false)
        val point = normalizeTapPoint(x, y)
        return dispatchTap(point.x, point.y, if (point.wasScaled) {
            "视觉坐标 ${point.x.toInt()},${point.y.toInt()}（${point.source}）"
        } else {
            "点击坐标 ${point.x.toInt()},${point.y.toInt()}"
        })
    }

    private fun normalizeTapPoint(rawX: Float, rawY: Float): NormalizedTapPoint {
        val reference = currentTapReferenceFrame()
        return if (rawX in 0f..1.05f && rawY in 0f..1.05f) {
            NormalizedTapPoint(
                x = rawX.coerceIn(0f, 1f) * reference.width,
                y = rawY.coerceIn(0f, 1f) * reference.height,
                wasScaled = true,
                source = "归一化坐标 ${"%.3f".format(rawX)},${"%.3f".format(rawY)}→${reference.label}",
            )
        } else {
            NormalizedTapPoint(rawX, rawY, false, reference.label)
        }
    }

    private fun currentTapReferenceFrame(): TapReferenceFrame {
        val metrics = resources.displayMetrics
        return TapReferenceFrame(
            width = metrics.widthPixels.toFloat(),
            height = metrics.heightPixels.toFloat(),
            label = "屏幕 ${metrics.widthPixels}x${metrics.heightPixels}",
        )
    }

    private fun executeInputText(step: CloudAgentStep): AgentExecutionResult {
        val text = step.text ?: return AgentExecutionResult(false, "缺少输入文本", false)
        val focusedDirect = step.shouldUseFocusedDirectInput
        if (focusedDirect) SystemClock.sleep(INPUT_DIRECT_FOCUS_SETTLE_MS)
        setInputClipboard(text)

        var lastCandidateCount = 0
        var shellFallbackAttempted = false
        repeat(INPUT_FOCUS_RETRY_COUNT) { attempt ->
            val candidateNodes = collectInputCandidateNodes(step)
            lastCandidateCount = maxOf(lastCandidateCount, candidateNodes.size)

            for (node in candidateNodes) {
                val result = tryInputTextOnNode(node, text)
                if (result != null) return result
            }

            if (focusedDirect && !shellFallbackAttempted) {
                shellFallbackAttempted = true
                if (pasteIntoCurrentFocusViaShell()) {
                    return AgentExecutionResult(
                        ok = true,
                        message = "已通过当前焦点剪贴板通道输入文字",
                        shouldContinue = true,
                    )
                }
            }

            if (attempt < INPUT_FOCUS_RETRY_COUNT - 1) {
                SystemClock.sleep(INPUT_FOCUS_RETRY_DELAY_MS)
            }
        }

        return AgentInputRecoveryPolicy.onInputFailure(step, lastCandidateCount)
    }

    private fun collectInputCandidateNodes(step: CloudAgentStep): List<AccessibilityNodeInfo> {
        val roots = collectInputRoots()
        val candidateNodes = mutableListOf<AccessibilityNodeInfo>()
        val candidateKeys = linkedSetOf<String>()

        fun addCandidate(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val key = inputNodeIdentity(node)
            if (candidateKeys.add(key)) candidateNodes += node
        }

        // focused_direct 的核心语义是“复用目标应用当前焦点”。先跨所有窗口寻找真实输入焦点，
        // 不再只依赖 selectBestRoot() 选出的单个根节点。
        roots.forEach { root ->
            addCandidate(runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull())
        }
        roots.forEach { root ->
            addCandidate(runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) }.getOrNull())
        }

        roots.forEach { root ->
            val deadlineMs = SystemClock.elapsedRealtime() + INPUT_NODE_SCAN_BUDGET_MS
            val handles = collectNodeHandles(root, MAX_EXECUTION_NODES, deadlineMs).handles
            addCandidate(findTargetHandle(handles, step)?.node)
            handles.filter { it.node.isFocused || it.node.isAccessibilityFocused }.forEach { addCandidate(it.node) }
            handles.filter { it.observed.editable }.forEach { addCandidate(it.node) }
        }

        if (step.shouldUseFocusedDirectInput) {
            roots.forEach { root ->
                collectDirectInputNodes(
                    root = root,
                    limit = DIRECT_INPUT_NODE_LIMIT,
                    deadlineMs = SystemClock.elapsedRealtime() + INPUT_NODE_SCAN_BUDGET_MS,
                ).forEach(::addCandidate)
            }
        }
        return candidateNodes
    }

    private fun collectInputRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots += it }
        runCatching {
            windows.orEmpty().forEach { window -> window.root?.let { roots += it } }
        }
        val preferredPackage = rootInActiveWindow?.packageName?.toString().orEmpty()
        return roots
            .distinctBy { rootIdentity(it) }
            .filterNot { it.packageName?.toString() == applicationContext.packageName }
            .sortedByDescending { root ->
                val packageName = root.packageName?.toString().orEmpty()
                when {
                    packageName == preferredPackage && packageName.isNotBlank() -> 4
                    packageName.isNotBlank() && !isSystemSurfacePackage(packageName) -> 3
                    packageName.isNotBlank() -> 2
                    else -> 1
                }
            }
    }

    private fun collectDirectInputNodes(
        root: AccessibilityNodeInfo,
        limit: Int,
        deadlineMs: Long,
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && result.size < limit && SystemClock.elapsedRealtime() < deadlineMs) {
            val (node, depth) = queue.removeFirst()
            val actionIds = runCatching { node.actionList.map { it.id }.toSet() }.getOrDefault(emptySet())
            val className = node.className?.toString().orEmpty().lowercase()
            val inputLikeClass = className.contains("edittext") ||
                className.contains("textfield") ||
                className.contains("textinput")
            val supportsInputAction = AccessibilityNodeInfo.ACTION_SET_TEXT in actionIds ||
                AccessibilityNodeInfo.ACTION_PASTE in actionIds
            if (
                node.isEditable || node.isFocused || node.isAccessibilityFocused ||
                inputLikeClass || supportsInputAction
            ) {
                result += node
            }
            if (depth < DIRECT_INPUT_MAX_DEPTH) {
                val childLimit = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
                for (childIndex in 0 until childLimit) {
                    if (SystemClock.elapsedRealtime() >= deadlineMs || queue.size >= MAX_PENDING_NODE_QUEUE) break
                    node.getChild(childIndex)?.let { queue.add(it to depth + 1) }
                }
            }
        }
        return result
    }

    private fun inputNodeIdentity(node: AccessibilityNodeInfo): String {
        val rect = Rect()
        runCatching { node.getBoundsInScreen(rect) }
        return listOf(
            node.packageName?.toString().orEmpty(),
            node.className?.toString().orEmpty(),
            runCatching { node.viewIdResourceName }.getOrNull().orEmpty(),
            rect.flattenToString(),
            node.text?.toString().orEmpty(),
            node.hintText?.toString().orEmpty(),
        ).joinToString("|")
    }

    private fun tryInputTextOnNode(node: AccessibilityNodeInfo, text: String): AgentExecutionResult? {
        runCatching { node.refresh() }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)) {
            return AgentExecutionResult(ok = true, message = "已通过 SET_TEXT 输入文字", shouldContinue = true)
        }
        if (pasteTextOnNode(node)) {
            return AgentExecutionResult(ok = true, message = "已通过剪贴板粘贴输入文字", shouldContinue = true)
        }

        var parent = runCatching { node.parent }.getOrNull()
        repeat(INPUT_PARENT_FALLBACK_DEPTH) {
            val current = parent ?: return@repeat
            runCatching { current.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            if (runCatching { current.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)) {
                return AgentExecutionResult(ok = true, message = "已通过父级输入节点 SET_TEXT 输入文字", shouldContinue = true)
            }
            if (pasteTextOnNode(current)) {
                return AgentExecutionResult(ok = true, message = "已通过父级输入节点粘贴文字", shouldContinue = true)
            }
            parent = runCatching { current.parent }.getOrNull()
        }
        return null
    }

    private fun setInputClipboard(text: String): Boolean {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("AI 输入", text))
            true
        }.getOrDefault(false)
    }

    private fun pasteTextOnNode(node: AccessibilityNodeInfo): Boolean {
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        SystemClock.sleep(INPUT_PASTE_FOCUS_DELAY_MS)
        return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)
    }

    private fun pasteIntoCurrentFocusViaShell(): Boolean {
        SystemClock.sleep(INPUT_PASTE_FOCUS_DELAY_MS)
        val result = deviceShellBridge.runEnhancedCommand(
            title = "向当前输入焦点粘贴文字",
            command = "input keyevent $KEYCODE_PASTE",
            timeoutMs = INPUT_SHELL_TIMEOUT_MS,
        )
        if (result.ok) SystemClock.sleep(INPUT_SHELL_SETTLE_MS)
        return result.ok
    }

    private fun executeScroll(step: CloudAgentStep): AgentExecutionResult {
        if (step.indicatesBackNavigation()) {
            return executeGlobalActionStep(GLOBAL_ACTION_BACK, "返回上一界面（纠正 scroll 返回语义）")
        }
        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        val handles = collectNodeHandles(
            root,
            MAX_EXECUTION_NODES,
            SystemClock.elapsedRealtime() + EXECUTION_NODE_BUDGET_MS,
        ).handles
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
        if (step.indicatesBackNavigation()) {
            return executeGlobalActionStep(GLOBAL_ACTION_BACK, "返回上一界面（纠正 swipe 返回语义）")
        }
        val direction = step.direction.orEmpty().lowercase().ifBlank { "up" }
        val reference = currentTapReferenceFrame()
        val w = reference.width
        val h = reference.height
        val (startX, startY, endX, endY) = when (direction) {
            "down" -> listOf(w * 0.5f, h * 0.32f, w * 0.5f, h * 0.72f)
            "left" -> listOf(w * 0.78f, h * 0.5f, w * 0.22f, h * 0.5f)
            "right" -> listOf(w * 0.22f, h * 0.5f, w * 0.78f, h * 0.5f)
            else -> listOf(w * 0.5f, h * 0.72f, w * 0.5f, h * 0.32f)
        }
        val ok = dispatchSwipe(startX, startY, endX, endY, step.durationMs ?: DEFAULT_SWIPE_MS)
        return AgentExecutionResult(
            ok = ok,
            message = if (ok) "已滑动：$direction" else "滑动失败：$direction",
            shouldContinue = ok,
        )
    }

    private fun CloudAgentStep.indicatesBackNavigation(): Boolean {
        val raw = listOfNotNull(reason, targetText, text, direction)
            .joinToString(" ")
            .lowercase()
        if (raw.isBlank()) return false
        val backIntent = listOf(
            "返回上一",
            "返回上个",
            "返回到上一",
            "回到上一",
            "上一界面",
            "上一页面",
            "上一层",
            "返回微信",
            "go back",
            "back to",
            "previous screen",
            "previous page",
        ).any { raw.contains(it) }
        val explicitScrollIntent = listOf(
            "查看更多",
            "看新消息",
            "看旧消息",
            "浏览",
            "滚动",
            "滑动查看",
            "scroll",
            "swipe to view",
        ).any { raw.contains(it) }
        return backIntent && !explicitScrollIntent
    }

    private fun findNodeByTextFast(root: AccessibilityNodeInfo, targetText: String): NodeHandle? {
        val deadlineMs = SystemClock.elapsedRealtime() + QUICK_TEXT_NODE_BUDGET_MS
        val exact = root.findAccessibilityNodeInfosByText(targetText).orEmpty()
            .mapNotNull { it.toHandleOrNull("n-fast", deadlineMs) }
            .firstOrNull { it.observed.text == targetText || it.observed.text.contains(targetText, ignoreCase = true) }
        if (exact != null) return exact
        return collectNodeHandles(root, FAST_TEXT_FALLBACK_NODES, deadlineMs)
            .handles
            .firstOrNull { it.observed.text == targetText || it.observed.text.contains(targetText, ignoreCase = true) }
    }

    private fun AccessibilityNodeInfo.toHandleOrNull(
        id: String,
        deadlineMs: Long = SystemClock.elapsedRealtime() + NODE_HANDLE_BUDGET_MS,
    ): NodeHandle? {
        if (SystemClock.elapsedRealtime() >= deadlineMs) return null

        val clickableNode = nearestClickableNode()
        val scrollableNode = nearestScrollableNode()
        val actionNode = when {
            isEditable -> this
            clickableNode != null -> clickableNode
            scrollableNode != null -> scrollableNode
            else -> this
        }
        val text = safeText(deadlineMs).ifBlank { actionNode.safeText(deadlineMs) }
        val hasUsefulSignal = text.isNotBlank() || clickableNode != null || isEditable || scrollableNode != null
        if (!hasUsefulSignal) return null

        val rect = Rect()
        actionNode.getBoundsInScreen(rect)
        if (rect.isEmpty) getBoundsInScreen(rect)
        if (rect.isEmpty) return null

        return NodeHandle(
            observed = ObservedScreenNode(
                id = id,
                text = text.take(80),
                className = actionNode.className?.toString().orEmpty().substringAfterLast('.').take(32),
                bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                clickable = clickableNode != null,
                editable = isEditable,
                scrollable = scrollableNode != null,
            ),
            node = actionNode,
            bounds = rect,
        )
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
        step.targetNodeId?.let { id ->
            handles.firstOrNull { it.observed.id == id }?.let { return it }
        }
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
        val finalMessage = if (safeX != x || safeY != y) {
            "$successMessage · 实际落点 ${safeX.toInt()},${safeY.toInt()}（边界保护）"
        } else {
            "$successMessage · 实际落点 ${safeX.toInt()},${safeY.toInt()}"
        }
        return AgentExecutionResult(ok = ok, message = if (ok) finalMessage else "点击手势提交失败", shouldContinue = ok)
    }

    private fun safeTapPoint(x: Float, y: Float): Pair<Float, Float> {
        val reference = currentTapReferenceFrame()
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val minX = 2f * density
        val maxX = (reference.width - 2f * density).coerceAtLeast(minX)
        val minY = 2f * density
        val maxY = (reference.height - 2f * density).coerceAtLeast(minY)
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

    private fun startTaskForegroundNotification() {
        if (foregroundNotificationStarted) return
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
            foregroundNotificationStarted = true
        } catch (_: Throwable) {
            foregroundNotificationStarted = false
        }
    }

    private fun stopTaskForegroundNotification() {
        if (!foregroundNotificationStarted) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {
        } finally {
            foregroundNotificationStarted = false
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

    private enum class AccessibilityRuntimeMode { Idle, Working }

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
        val truncated: Boolean = false,
    )

    private data class NodeHandle(
        val observed: ObservedScreenNode,
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
    )

    private data class NormalizedTapPoint(
        val x: Float,
        val y: Float,
        val wasScaled: Boolean,
        val source: String,
    )

    private data class TapReferenceFrame(
        val width: Float,
        val height: Float,
        val label: String,
    )

    companion object {
        @Volatile private var activeService: AiAgentAccessibilityService? = null

        fun isConnected(): Boolean = activeService != null

        fun beginTaskSession() {
            activeService?.beginTaskWorkingSession()
        }

        fun endTaskSession() {
            activeService?.endTaskWorkingSession()
        }

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

        private const val WINDOW_HINT_THROTTLE_MS = 900L

        // Idle 绝对低负载：不监听事件、不持有 flags。
        private const val IDLE_EVENT_TYPES = 0
        private const val IDLE_ACCESSIBILITY_FLAGS = 0

        // Working 只在截图、节点读取、点击、输入的短时间窗口开启。
        private const val WORKING_EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        private const val WORKING_ACCESSIBILITY_FLAGS = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        private const val IDLE_NOTIFICATION_TIMEOUT_MS = 10_000L
        private const val WORKING_NOTIFICATION_TIMEOUT_MS = 120L

        private const val OWN_OVERLAY_WINDOW_PENALTY = 10_000
        private const val SYSTEM_SURFACE_WINDOW_PENALTY = 40_000
        private const val CONTENT_APP_WINDOW_BONUS = 4_000
        private const val TRUNCATED_CAPTURE_PENALTY = 1_600
        private val SYSTEM_SURFACE_PACKAGES = setOf("android", "com.android.systemui")

        private const val AGENT_CHANNEL_ID = "ai_agent_accessibility_status"
        private const val AGENT_NOTIFICATION_ID = 7301

        // 彻底轻量化后的节点规模与时间预算。
        private const val MAX_SNAPSHOT_NODES = 48
        private const val VISUAL_AFFORDANCE_NODES = 20
        private const val MAX_EXECUTION_NODES = 72
        private const val ROOT_SELECTION_SAMPLE_NODES = 24
        private const val FAST_TEXT_FALLBACK_NODES = 28
        private const val MAX_ROOT_CANDIDATES = 2
        private const val MAX_DEPTH = 5
        private const val MAX_CHILDREN_PER_NODE = 8
        private const val MAX_PENDING_NODE_QUEUE = 64
        private const val CHILD_TEXT_FALLBACK_LIMIT = 1
        private const val CHILD_TEXT_MAX_DEPTH = 1
        private const val MAX_CHILD_TEXT_QUEUE = 10
        private const val TEXT_LIMIT = 24
        private const val ALL_NODE_LIMIT = 36
        private const val CLICKABLE_LIMIT = 16
        private const val INPUT_LIMIT = 4
        private const val SCROLLABLE_LIMIT = 4
        private const val CLICK_PARENT_DEPTH = 5
        private const val SCROLL_PARENT_DEPTH = 5

        private const val DEFAULT_TAP_MS = 42L
        private const val DEFAULT_SWIPE_MS = 260L
        private const val DEFAULT_WAIT_MS = 500L
        private const val INPUT_FOCUS_RETRY_COUNT = 3
        private const val INPUT_FOCUS_RETRY_DELAY_MS = 150L
        private const val INPUT_DIRECT_FOCUS_SETTLE_MS = 90L
        private const val INPUT_PASTE_FOCUS_DELAY_MS = 55L
        private const val INPUT_SHELL_SETTLE_MS = 90L
        private const val INPUT_SHELL_TIMEOUT_MS = 1_200L
        private const val INPUT_NODE_SCAN_BUDGET_MS = 210L
        private const val DIRECT_INPUT_NODE_LIMIT = 24
        private const val DIRECT_INPUT_MAX_DEPTH = 6
        private const val INPUT_PARENT_FALLBACK_DEPTH = 2
        private const val KEYCODE_PASTE = 279

        private const val SCREENSHOT_TIMEOUT_MS = 2_200L
        private const val OVERLAY_HIDE_BEFORE_ACTION_MS = 90L
        private const val OVERLAY_HIDE_BEFORE_SCREENSHOT_MS = 150L

        private const val SNAPSHOT_NODE_BUDGET_MS = 220L
        private const val VISUAL_AFFORDANCE_BUDGET_MS = 100L
        private const val ROOT_SELECTION_BUDGET_MS = 120L
        private const val EXECUTION_NODE_BUDGET_MS = 190L
        private const val QUICK_TEXT_NODE_BUDGET_MS = 100L
        private const val NODE_CAPTURE_DEFAULT_BUDGET_MS = 180L
        private const val NODE_HANDLE_BUDGET_MS = 22L
        private const val CHILD_TEXT_BUDGET_MS = 6L
    }
}
