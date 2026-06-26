package com.yuchen.ailedger.service

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Hosts the approved web HUD without re-drawing it in Android Canvas.
 * The WebView runs the original CSS, SVG filters, gradients, masks and keyframes from assets.
 */
internal class VisualAgentHudWebOverlay(
    private val service: AiAgentAccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var webView: WebView? = null
    private var pageReady = false
    private var pendingPayload: String? = null
    private var delayedClickJob: Job? = null
    private var scheduledClickRevision: Long = 0L
    private var latestProgress = AgentOverlayProgress()
    private var latestTarget: VisualAgentHudTarget? = null
    private var latestHiddenForCapture = false

    init {
        createWebOverlay()
        scope.launch {
            combine(
                AgentRuntimeController.progress,
                VisualAgentHudRuntime.target,
                AgentRuntimeController.overlayHiddenForCapture,
            ) { progress, target, hidden -> Triple(progress, target, hidden) }
                .collect { (progress, target, hidden) ->
                    latestProgress = progress
                    latestTarget = target?.takeIf { it.taskId == progress.taskId }
                    latestHiddenForCapture = hidden
                    renderCurrentState()
                }
        }
    }

    fun destroy() {
        delayedClickJob?.cancel()
        delayedClickJob = null
        scope.cancel()
        pageReady = false
        pendingPayload = null
        webView?.let { view ->
            runCatching { windowManager?.removeView(view) }
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebOverlay() {
        if (webView != null || windowManager == null) return
        val view = WebView(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background?.alpha = 0
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
            isHapticFeedbackEnabled = false
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                loadsImagesAutomatically = true
                blockNetworkImage = true
                allowContentAccess = false
                allowFileAccess = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                mediaPlaybackRequiresUserGesture = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    pendingPayload?.let { payload ->
                        pendingPayload = null
                        dispatchPayload(payload)
                    }
                }
            }
            visibility = View.INVISIBLE
            loadUrl(ASSET_URL)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess { webView = view }
            .onFailure {
                view.destroy()
                webView = null
            }
    }

    private fun renderCurrentState() {
        val progress = latestProgress
        val target = latestTarget
        val lastLog = progress.logs.lastOrNull().orEmpty()
        val executingRealAction = progress.running &&
            progress.currentAction.isNotBlank() &&
            lastLog == progress.currentAction
        val visuallyHidden = latestHiddenForCapture && !executingRealAction
        val visible = !visuallyHidden && (
            progress.running ||
                progress.pendingConfirmation != null ||
                progress.pendingUserInput != null ||
                progress.userTakeoverPaused
            )

        if (!visible) {
            delayedClickJob?.cancel()
            delayedClickJob = null
            scheduledClickRevision = 0L
            setWebViewActive(false)
            sendPayload(buildPayload(progress, target, visible = false, phaseOverride = null, clickRevision = 0L))
            return
        }

        setWebViewActive(true)
        val phase = phaseOf(progress, target, lastLog)
        sendPayload(buildPayload(progress, target, visible = true, phaseOverride = phase, clickRevision = 0L))
        scheduleClickPulseIfNeeded(progress, target, phase)
    }

    private fun phaseOf(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        lastLog: String,
    ): Int {
        if (progress.pendingConfirmation != null ||
            progress.pendingUserInput != null ||
            progress.userTakeoverPaused
        ) return 1
        if (!progress.running) return 4
        if (lastLog == progress.currentAction && progress.currentAction.isNotBlank()) {
            return if (target?.positioned == true &&
                target.actionType in setOf("tap_xy", "tap_node")
            ) 2 else 3
        }
        if (lastLog.startsWith("结果：") || progress.status == "重新规划") return 4
        if (lastLog.startsWith("模型") ||
            progress.lastResult.contains("分析") ||
            progress.lastResult.contains("GUI Plus") ||
            progress.lastResult.contains("VisualDirect")
        ) return 1
        return 0
    }

    private fun scheduleClickPulseIfNeeded(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        phase: Int,
    ) {
        if (phase != 2 || target?.positioned != true || target.revision <= 0L) {
            if (phase != 2) {
                delayedClickJob?.cancel()
                delayedClickJob = null
            }
            return
        }
        if (scheduledClickRevision == target.revision) return
        scheduledClickRevision = target.revision
        delayedClickJob?.cancel()
        delayedClickJob = scope.launch {
            delay(CURSOR_MOVE_DURATION_MS)
            val currentProgress = latestProgress
            val currentTarget = latestTarget
            if (!currentProgress.running ||
                latestHiddenForCapture ||
                currentTarget?.revision != target.revision
            ) return@launch
            sendPayload(
                buildPayload(
                    progress = currentProgress,
                    target = currentTarget,
                    visible = true,
                    phaseOverride = 3,
                    clickRevision = target.revision,
                )
            )
        }
    }

    private fun buildPayload(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        visible: Boolean,
        phaseOverride: Int?,
        clickRevision: Long,
    ): String {
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val xNorm = when {
            target == null -> DEFAULT_CURSOR_X
            target.normalized -> target.x
            else -> target.x / width.toFloat()
        }.coerceIn(0f, 1f)
        val yNorm = when {
            target == null -> DEFAULT_CURSOR_Y
            target.normalized -> target.y
            else -> target.y / height.toFloat()
        }.coerceIn(0f, 1f)
        val phase = phaseOverride ?: 0
        val thought = target?.detail
            ?.takeIf(String::isNotBlank)
            ?: progress.lastResult.takeIf(String::isNotBlank)
            ?: "正在根据页面证据选择下一步操作。"
        val source = when (target?.actionType) {
            "tap_node" -> "视觉识别 + 节点"
            "tap_xy" -> "视觉识别"
            else -> "智能体执行"
        }
        return JSONObject()
            .put("visible", visible)
            .put("xNorm", xNorm.toDouble())
            .put("yNorm", yNorm.toDouble())
            .put("phase", phase)
            .put("bubbleTitle", progress.currentAction.ifBlank { "正在执行视觉任务" })
            .put("currentAction", progress.currentAction)
            .put("thought", thought)
            .put("result", progress.lastResult)
            .put("confidence", if (target?.positioned == true) "已定位" else "—")
            .put("actionSource", source)
            .put("debugLatency", "latency_total: —")
            .put("autoClickAfterMs", if (phase == 2) CURSOR_MOVE_DURATION_MS else 0L)
            .put("clickRevision", clickRevision)
            .toString()
    }

    private fun sendPayload(payload: String) {
        if (!pageReady) {
            pendingPayload = payload
            return
        }
        dispatchPayload(payload)
    }

    private fun dispatchPayload(payload: String) {
        webView?.evaluateJavascript("window.VisualHud&&window.VisualHud.update($payload);", null)
    }

    private fun setWebViewActive(active: Boolean) {
        webView?.let { view ->
            if (active) {
                if (view.visibility != View.VISIBLE) {
                    view.onResume()
                    view.resumeTimers()
                    view.visibility = View.VISIBLE
                }
            } else if (view.visibility != View.INVISIBLE) {
                view.visibility = View.INVISIBLE
                view.onPause()
            }
        }
    }

    companion object {
        private const val ASSET_URL = "file:///android_asset/visual_agent_hud_runtime.html"
        private const val CURSOR_MOVE_DURATION_MS = 820L
        private const val DEFAULT_CURSOR_X = 0.52f
        private const val DEFAULT_CURSOR_Y = 0.46f
    }
}
