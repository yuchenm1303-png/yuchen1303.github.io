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
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class AiAgentAccessibilityService : AccessibilityService() {
    @Volatile private var lastWindowHintAtMs: Long = 0L
    @Volatile private var lastWindowHintKey: String = ""
    @Volatile private var currentAccessibilityMode: AccessibilityRuntimeMode = AccessibilityRuntimeMode.Idle
    @Volatile private var foregroundNotificationStarted: Boolean = false

    private val modeLock = Any()
    private var workingSessionDepth: Int = 0
    private var taskSessionDepth: Int = 0
    private val reusableExecutionCaptureState = VisualExecutionCaptureState<RootCapture>(
        elapsedRealtime = SystemClock::elapsedRealtime,
        ttlMs = REUSABLE_EXECUTION_CAPTURE_TTL_MS,
    )

    private val screenshotExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ai-agent-screenshot").apply { isDaemon = true }
    }
    private val deviceShellBridge by lazy(LazyThreadSafetyMode.NONE) {
        DeviceShellBridge(applicationContext)
    }
    private var visualHudHost: VisualAgentHudHost? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        reusableExecutionCaptureState.clear()
        visualHudHost?.destroy()
        visualHudHost = VisualAgentHudHost(this).also { it.start() }
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
        reusableExecutionCaptureState.clear()
        visualHudHost?.destroy()
        visualHudHost = null
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
            reusableExecutionCaptureState.clear()
            taskSessionDepth += 1
            startTaskForegroundNotification()
            // 彻底轻量化：任务会话只保留通知，不常驻 Working。
            // 真正需要窗口/节点/截图时，由 withWorkingAccessibilityMode() 短暂开启，结束立即回 Idle。
            configureIdleServiceInfo(force = true)
        }
    }

    private fun endTaskWorkingSession() {
        synchronized(modeLock) {
            reusableExecutionCaptureState.clear()
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

    private suspend fun <T> withWorkingAccessibilityModeSuspending(block: suspend () -> T): T {
        beginWorkingSession()
        return try {
            block()
        } finally {
            endWorkingSession()
        }
    }

    private fun captureSnapshotInternal(forceVisual: Boolean = false): ScreenObservation = withWorkingAccessibilityMode {
        reusableExecutionCaptureState.clear()
        val now = System.currentTimeMillis()
        val startedAt = SystemClock.elapsedRealtime()
        val nodeLimit = if (forceVisual) VISUAL_AFFORDANCE_NODES else MAX_SNAPSHOT_NODES
        val nodeBudgetMs = if (forceVisual) VISUAL_AFFORDANCE_BUDGET_MS else SNAPSHOT_NODE_BUDGET_MS
        val selected = selectBestRootCapture(limit = nodeLimit, timeBudgetMs = nodeBudgetMs)
        val nodeFinishedAt = SystemClock.elapsedRealtime()

        val nodeObservation = if (selected != null) {
            val nodes = selected.capture.handles.map { it.observed }
            val nodeMs = nodeFinishedAt - startedAt
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

        if (!forceVisual && selected != null) {
            reusableExecutionCaptureState.store(
                packageName = selected.packageName,
                rootIdentity = rootIdentity(selected.root),
                value = selected,
            )
        }
        val visual = if (forceVisual) captureVisualObservation("forced") else null
        val finishedAt = SystemClock.elapsedRealtime()
        Log.d(
            VISUAL_LOOP_PERF_TAG,
            "snapshot forceVisual=$forceVisual package=${nodeObservation.packageName} " +
                "nodes=${nodeObservation.capturedNodeCount}/${nodeObservation.nodeCount} " +
                "nodeMs=${nodeFinishedAt - startedAt} visualMs=${finishedAt - nodeFinishedAt} totalMs=${finishedAt - startedAt}",
        )
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
        val startedAt = SystemClock.elapsedRealtime()
        AgentRuntimeController.beginCleanVisualCapture()
        return try {
            val hideWaitMs = AgentRuntimeController.cleanVisualCaptureSettleRemaining(
                OVERLAY_HIDE_BEFORE_SCREENSHOT_MS,
            )
            if (hideWaitMs > 0L) SystemClock.sleep(hideWaitMs)
            val requestStartedAt = SystemClock.elapsedRealtime()
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
            val completed = latch.await(SCREENSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val finishedAt = SystemClock.elapsedRealtime()
            Log.d(
                VISUAL_LOOP_PERF_TAG,
                "screenshot reason=$reason hideWaitMs=$hideWaitMs requestMs=${finishedAt - requestStartedAt} " +
                    "totalMs=${finishedAt - startedAt} completed=$completed source=${result.source}",
            )
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
        val startedAt = SystemClock.elapsedRealtime()
        val originalWidth = width
        val originalHeight = height
        val encoded = try {
            VisualScreenshotEncoder.encode(this)
        } finally {
            recycle()
        }
        val base64StartedAt = SystemClock.elapsedRealtime()
        val base64Jpeg = Base64.encodeToString(encoded.bytes, Base64.NO_WRAP)
        val finishedAt = SystemClock.elapsedRealtime()
        Log.d(
            VISUAL_LOOP_PERF_TAG,
            "encode original=${originalWidth}x$originalHeight output=${encoded.width}x${encoded.height} " +
                "quality=${encoded.quality} bytes=${encoded.bytes.size} encodeMs=${encoded.encodeMs} " +
                "base64Ms=${finishedAt - base64StartedAt} totalMs=${finishedAt - startedAt} " +
                "compressPasses=${encoded.compressionPasses} scalePasses=${encoded.scalePasses}",
        )
        return ScreenVisualObservation(
            available = true,
            mimeType = "image/jpeg",
            width = encoded.width,
            height = encoded.height,
            displayWidth = originalWidth,
            displayHeight = originalHeight,
            base64Jpeg = base64Jpeg,
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

    private suspend fun executeStepInternal(step: CloudAgentStep): AgentExecutionResult {
        // 让出主线程给悬浮窗先应用 INVISIBLE/NOT_TOUCHABLE，避免点击被自己的浮窗截获。
        delay(OVERLAY_HIDE_BEFORE_ACTION_MS)
        return if (step.requiresWindowContent()) {
            withWorkingAccessibilityModeSuspending {
                val reusableCapture = takeReusableExecutionCapture(step)
                executeStepInternalUnchecked(step, reusableCapture)
            }
        } else {
            reusableExecutionCaptureState.clear()
            executeStepInternalUnchecked(step, null)
        }
    }

    private fun takeReusableExecutionCapture(step: CloudAgentStep): RootCapture? {
        if (!step.requiresWindowContent()) return null
        val currentRoot = rootInActiveWindow ?: run {
            reusableExecutionCaptureState.clear()
            Log.d(VISUAL_LOOP_PERF_TAG, "freshNodeReuse type=${step.type} hit=false reason=no_active_root")
            return null
        }
        val packageName = currentRoot.packageName?.toString().orEmpty()
        val rootKey = rootIdentity(currentRoot)
        val capture = reusableExecutionCaptureState.take(packageName, rootKey)
        Log.d(
            VISUAL_LOOP_PERF_TAG,
            "freshNodeReuse type=${step.type} hit=${capture != null} package=$packageName nodes=${capture?.capture?.handles?.size ?: 0}",
        )
        return capture
    }

    private suspend fun executeStepInternalUnchecked(
        step: CloudAgentStep,
        reusableCapture: RootCapture?,
    ): AgentExecutionResult {
        return when (step.type) {
            "open_app" -> executeOpenApp(step)
            "back" -> executeGlobalActionStep(GLOBAL_ACTION_BACK, "返回")
            "home" -> executeGlobalActionStep(GLOBAL_ACTION_HOME, "回到桌面")
            "recents" -> executeGlobalActionStep(GLOBAL_ACTION_RECENTS, "打开最近任务")
            "notifications" -> executeGlobalActionStep(GLOBAL_ACTION_NOTIFICATIONS, "下拉通知栏")
            "quick_settings" -> executeGlobalActionStep(GLOBAL_ACTION_QUICK_SETTINGS, "打开快捷设置")
            "tap_node" -> executeTapNode(step, reusableCapture)
            "tap_xy" -> executeTapXY(step)
            "input_text" -> executeInputText(step, reusableCapture)
            "scroll" -> executeScroll(step, reusableCapture)
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

    private suspend fun executeTapNode(
        step: CloudAgentStep,
        reusableCapture: RootCapture?,
    ): AgentExecutionResult {
        val reusableTarget = reusableCapture?.capture?.handles
            ?.let { handles -> findTargetHandle(handles, step) }
            ?.let { handle -> refreshReusableHandle(handle, step) }
        if (reusableTarget != null) {
            performClickSmart(reusableTarget.node)?.let { ok ->
                return AgentExecutionResult(
                    ok = ok,
                    message = if (ok) {
                        "已点击节点 ${reusableTarget.observed.id} · freshNodeReuse=true"
                    } else {
                        "节点点击失败 · freshNodeReuse=true"
                    },
                    shouldContinue = ok,
                )
            }
            return tapRect(
                reusableTarget.bounds,
                "已点击节点 ${reusableTarget.observed.id} · freshNodeReuse=true",
            )
        }

        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        val targetText = step.targetText?.takeIf { it.isNotBlank() }
        if (targetText != null) {
            val fastTarget = findNodeByTextFast(root, targetText)
            if (fastTarget != null) return tapRect(fastTarget.bounds, "已点击：$targetText")
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

    private fun refreshReusableHandle(
        handle: NodeHandle,
        step: CloudAgentStep,
    ): NodeHandle? {
        val refreshed = runCatching { handle.node.refresh() }.getOrDefault(false)
        if (!refreshed) return null
        val rebuilt = handle.node.toHandleOrNull(
            id = handle.observed.id,
            deadlineMs = SystemClock.elapsedRealtime() + NODE_HANDLE_BUDGET_MS,
        ) ?: return null
        return rebuilt.takeIf { candidate ->
            findTargetHandle(listOf(candidate), step) != null ||
                (step.type == "scroll" && candidate.observed.scrollable) ||
                (step.type == "input_text" && candidate.observed.editable)
        }
    }

    private suspend fun executeTapXY(step: CloudAgentStep): AgentExecutionResult {
        val rawX = step.x ?: return AgentExecutionResult(false, "缺少点击 x 坐标", false)
        val rawY = step.y ?: return AgentExecutionResult(false, "缺少点击 y 坐标", false)
        val args = step.toolArgs
        val currentReference = currentTapReferenceFrame()
        val currentFrame = currentReference.toVisualDisplayFrame()
        val expectedFrame = args?.let { source ->
            val width = source.optInt(TRACE_DISPLAY_WIDTH).takeIf { it > 0 }
            val height = source.optInt(TRACE_DISPLAY_HEIGHT).takeIf { it > 0 }
            if (width != null && height != null) VisualDisplayFrame(width, height) else null
        }
        val alreadyMaterialized = args?.optString(TRACE_PIXEL_MAPPING_PROTOCOL) ==
            VisualCoordinateProtocol.pixelMappingProtocol &&
            args.has(TRACE_MATERIALIZED_X) &&
            args.has(TRACE_MATERIALIZED_Y)
        val resolution = VisualCoordinateProtocol.resolveForExecution(
            rawX = rawX,
            rawY = rawY,
            currentFrame = currentFrame,
            expectedFrame = expectedFrame,
            alreadyMaterialized = alreadyMaterialized,
        )
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "tap_coordinate_execution",
            details = JSONObject().apply {
                put("valid", resolution.valid)
                put("reason", resolution.reason)
                put("rawX", rawX)
                put("rawY", rawY)
                put("alreadyMaterialized", alreadyMaterialized)
                put("scaledFromNormalized", resolution.scaledFromNormalized)
                put("frameMatched", resolution.frameMatched)
                put("sourceFrame", expectedFrame?.label ?: JSONObject.NULL)
                put("currentFrame", currentFrame.label)
                put("resolvedX", resolution.point?.x ?: JSONObject.NULL)
                put("resolvedY", resolution.point?.y ?: JSONObject.NULL)
            },
        )
        val point = resolution.point
        if (!resolution.valid || point == null) {
            return AgentExecutionResult(
                ok = false,
                message = "视觉坐标已取消：${resolution.reason} · sourceFrame=${expectedFrame?.label ?: "unknown"} · currentFrame=${currentFrame.label}",
                shouldContinue = true,
            )
        }
        val source = if (resolution.scaledFromNormalized) {
            "归一化坐标 ${formatCoordinate(rawX)},${formatCoordinate(rawY)}→${currentReference.label}"
        } else {
            "${VisualCoordinateProtocol.coordinateSpace} · ${currentReference.label}"
        }
        return dispatchTap(
            x = point.x,
            y = point.y,
            successMessage = "视觉坐标 ${formatCoordinate(point.x)},${formatCoordinate(point.y)}（$source）",
            allowBoundaryAdjustment = false,
        )
    }

    @Suppress("DEPRECATION")
    private fun currentTapReferenceFrame(): TapReferenceFrame {
        val realMetrics = DisplayMetrics()
        val display = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            runCatching { display.getRealMetrics(realMetrics) }
            if (realMetrics.widthPixels > 0 && realMetrics.heightPixels > 0) {
                return TapReferenceFrame(
                    width = realMetrics.widthPixels,
                    height = realMetrics.heightPixels,
                    label = "物理屏幕 ${realMetrics.widthPixels}x${realMetrics.heightPixels}",
                )
            }
        }
        val fallback = resources.displayMetrics
        return TapReferenceFrame(
            width = fallback.widthPixels,
            height = fallback.heightPixels,
            label = "兼容屏幕 ${fallback.widthPixels}x${fallback.heightPixels}",
        )
    }

    private fun executeInputText(
        step: CloudAgentStep,
        reusableCapture: RootCapture?,
    ): AgentExecutionResult {
        val text = step.text ?: return AgentExecutionResult(false, "缺少输入文本", false)
        val focusedDirect = step.shouldUseFocusedDirectInput
        if (focusedDirect) SystemClock.sleep(INPUT_DIRECT_FOCUS_SETTLE_MS)
        setInputClipboard(text)

        val reusableCandidates = reusableInputCandidates(step, reusableCapture)
        for (node in reusableCandidates) {
            val result = tryInputTextOnNode(node, text)
            if (result != null) {
                return result.copy(message = "${result.message} · freshNodeReuse=true")
            }
        }

        var lastCandidateCount = reusableCandidates.size
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

    private fun reusableInputCandidates(
        step: CloudAgentStep,
        reusableCapture: RootCapture?,
    ): List<AccessibilityNodeInfo> {
        val handles = reusableCapture?.capture?.handles.orEmpty()
        if (handles.isEmpty()) return emptyList()
        val ordered = buildList {
            findTargetHandle(handles, step)?.let(::add)
            handles.filter { it.node.isFocused || it.node.isAccessibilityFocused }.forEach(::add)
            handles.filter { it.observed.editable }.forEach(::add)
        }.distinctBy { it.observed.id + it.observed.bounds + it.observed.text }
        return ordered.mapNotNull { handle ->
            val refreshed = runCatching { handle.node.refresh() }.getOrDefault(false)
            handle.node.takeIf { refreshed }
        }
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

    private suspend fun executeScroll(
        step: CloudAgentStep,
        reusableCapture: RootCapture?,
    ): AgentExecutionResult {
        if (step.indicatesBackNavigation()) {
            return executeGlobalActionStep(GLOBAL_ACTION_BACK, "返回上一界面（纠正 scroll 返回语义）")
        }
        val direction = step.direction.orEmpty().lowercase()
        val action = if (direction in setOf("up", "left", "backward", "previous")) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        val reusableTarget = reusableCapture?.capture?.handles?.let { handles ->
            (findTargetHandle(handles, step) ?: handles.firstOrNull { it.observed.scrollable })
                ?.let { handle -> refreshReusableHandle(handle, step) }
        }
        if (
            reusableTarget?.node?.isScrollable == true &&
            runCatching { reusableTarget.node.performAction(action) }.getOrDefault(false)
        ) {
            return AgentExecutionResult(true, "已滚动节点 ${reusableTarget.observed.id} · freshNodeReuse=true")
        }

        val root = selectBestRoot() ?: return AgentExecutionResult(false, "无法读取当前屏幕", false)
        val handles = collectNodeHandles(
            root,
            MAX_EXECUTION_NODES,
            SystemClock.elapsedRealtime() + EXECUTION_NODE_BUDGET_MS,
        ).handles
        val target = findTargetHandle(handles, step) ?: handles.firstOrNull { it.observed.scrollable }
        if (target?.node?.isScrollable == true && target.node.performAction(action)) {
            return AgentExecutionResult(true, "已滚动节点 ${target.observed.id}")
        }
        return executeSwipe(step.copy(type = "swipe", direction = direction.ifBlank { "up" }))
    }

    private suspend fun executeSwipe(step: CloudAgentStep): AgentExecutionResult {
        if (step.indicatesBackNavigation()) {
            return executeGlobalActionStep(GLOBAL_ACTION_BACK, "返回上一界面（纠正 swipe 返回语义）")
        }
        val direction = step.direction.orEmpty().lowercase().ifBlank { "up" }
        val reference = currentTapReferenceFrame()
        val w = reference.width.toFloat()
        val h = reference.height.toFloat()
        val (startX, startY, endX, endY) = when (direction) {
            "down" -> listOf(w * 0.5f, h * 0.32f, w * 0.5f, h * 0.72f)
            "left" -> listOf(w * 0.78f, h * 0.5f, w * 0.22f, h * 0.5f)
            "right" -> listOf(w * 0.22f, h * 0.5f, w * 0.78f, h * 0.5f)
            else -> listOf(w * 0.5f, h * 0.72f, w * 0.5f, h * 0.32f)
        }
        val durationMs = (step.durationMs ?: DEFAULT_SWIPE_MS).coerceIn(160L, 900L)
        val outcome = dispatchSwipe(startX, startY, endX, endY, durationMs)
        return VisualGestureExecutionPolicy.swipeResult(outcome, direction)
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

    private suspend fun tapRect(rect: Rect, successMessage: String): AgentExecutionResult {
        if (rect.isEmpty) return AgentExecutionResult(false, "目标区域无效", false)
        return dispatchTap(
            x = rect.centerX().toFloat(),
            y = rect.centerY().toFloat(),
            successMessage = successMessage,
            allowBoundaryAdjustment = true,
        )
    }

    private suspend fun dispatchTap(
        x: Float,
        y: Float,
        successMessage: String,
        allowBoundaryAdjustment: Boolean,
    ): AgentExecutionResult {
        val frame = currentTapReferenceFrame().toVisualDisplayFrame()
        val requestedInBounds = VisualCoordinateProtocol.contains(x, y, frame)
        val resolved = when {
            requestedInBounds -> VisualCoordinatePoint(x, y)
            allowBoundaryAdjustment -> VisualCoordinateProtocol.clipPhysicalPoint(x, y, frame)
            else -> null
        }
        if (resolved == null) {
            return AgentExecutionResult(
                ok = false,
                message = "坐标执行已取消：physical_coordinate_out_of_bounds · requested=${formatCoordinate(x)},${formatCoordinate(y)} · currentFrame=${frame.label}",
                shouldContinue = true,
            )
        }
        val path = Path().apply { moveTo(resolved.x, resolved.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, DEFAULT_TAP_MS))
            .build()
        val adjusted = resolved.x != x || resolved.y != y
        val finalMessage = if (adjusted) {
            "$successMessage · 实际落点 ${formatCoordinate(resolved.x)},${formatCoordinate(resolved.y)}（边界保护）"
        } else {
            "$successMessage · 实际落点 ${formatCoordinate(resolved.x)},${formatCoordinate(resolved.y)}"
        }
        VisualIntelligenceDiagnosticsStore.currentOrNull()?.recordDiagnosticEvent(
            type = "tap_gesture_dispatch",
            details = JSONObject().apply {
                put("requestedX", x)
                put("requestedY", y)
                put("executedX", resolved.x)
                put("executedY", resolved.y)
                put("displayFrame", frame.label)
                put("boundaryAdjusted", adjusted)
                put("allowBoundaryAdjustment", allowBoundaryAdjustment)
            },
        )
        val outcome = dispatchGestureAndAwait(
            gesture = gesture,
            timeoutMs = GESTURE_COMPLETION_TIMEOUT_MS,
        )
        return VisualGestureExecutionPolicy.tapResult(outcome, finalMessage)
    }

    private suspend fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
    ): VisualGestureDispatchOutcome {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGestureAndAwait(
            gesture = gesture,
            timeoutMs = maxOf(GESTURE_COMPLETION_TIMEOUT_MS, durationMs + GESTURE_COMPLETION_GRACE_MS),
        )
    }

    private suspend fun dispatchGestureAndAwait(
        gesture: GestureDescription,
        timeoutMs: Long,
    ): VisualGestureDispatchOutcome {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val resolved = AtomicBoolean(false)
                fun complete(outcome: VisualGestureDispatchOutcome) {
                    if (resolved.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(outcome)
                    }
                }

                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        complete(VisualGestureDispatchOutcome.Completed)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        complete(VisualGestureDispatchOutcome.Cancelled)
                    }
                }
                val accepted = runCatching { dispatchGesture(gesture, callback, null) }.getOrDefault(false)
                if (!accepted) complete(VisualGestureDispatchOutcome.Rejected)
                continuation.invokeOnCancellation { resolved.set(true) }
            }
        } ?: VisualGestureDispatchOutcome.TimedOut
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

    private fun formatCoordinate(value: Float): String =
        String.format(Locale.US, "%.3f", value)

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

    private data class TapReferenceFrame(
        val width: Int,
        val height: Int,
        val label: String,
    ) {
        fun toVisualDisplayFrame(): VisualDisplayFrame = VisualDisplayFrame(width, height)
    }

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

        suspend fun executeStep(step: CloudAgentStep): AgentExecutionResult {
            val service = activeService ?: return AgentExecutionResult(false, "无障碍服务未连接", false)
            return service.executeStepInternal(step)
        }

        private const val VISUAL_LOOP_PERF_TAG = "VisualLoopPerf"
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
        private const val GESTURE_COMPLETION_TIMEOUT_MS = 1_500L
        private const val GESTURE_COMPLETION_GRACE_MS = 650L

        private const val SNAPSHOT_NODE_BUDGET_MS = 220L
        private const val VISUAL_AFFORDANCE_BUDGET_MS = 100L
        private const val ROOT_SELECTION_BUDGET_MS = 120L
        private const val EXECUTION_NODE_BUDGET_MS = 190L
        private const val QUICK_TEXT_NODE_BUDGET_MS = 100L
        private const val NODE_CAPTURE_DEFAULT_BUDGET_MS = 180L
        private const val NODE_HANDLE_BUDGET_MS = 22L
        private const val CHILD_TEXT_BUDGET_MS = 6L
        private const val REUSABLE_EXECUTION_CAPTURE_TTL_MS = 800L

        private const val TRACE_PIXEL_MAPPING_PROTOCOL = "__androidPixelMappingProtocol"
        private const val TRACE_MATERIALIZED_X = "__androidMaterializedX"
        private const val TRACE_MATERIALIZED_Y = "__androidMaterializedY"
        private const val TRACE_DISPLAY_WIDTH = "__androidDisplayWidth"
        private const val TRACE_DISPLAY_HEIGHT = "__androidDisplayHeight"
    }
}
