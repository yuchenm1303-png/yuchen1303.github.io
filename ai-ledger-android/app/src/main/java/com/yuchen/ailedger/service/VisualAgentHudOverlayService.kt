package com.yuchen.ailedger.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Full-screen visual-agent HUD rendered by the approved web implementation itself.
 * Android only supplies live state; CSS, SVG, filters, masks and animations stay in assets.
 */
class VisualAgentHudOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var webView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pageReady = false
    private var pendingPayload: String? = null
    private var lastClickRevision = 0L
    private var overlayVisible = false

    override fun onCreate() {
        super.onCreate()
        if (!AgentOverlayService.isOverlaySwitchEnabled() || !canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        createOverlay()
        scope.launch {
            combine(
                AgentRuntimeController.progress,
                VisualAgentHudRuntime.target,
                AgentRuntimeController.overlayHiddenForCapture,
            ) { progress, target, hidden -> Triple(progress, target, hidden) }
                .collect { (progress, target, hidden) ->
                    updateOverlay(progress, target, hidden)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AgentOverlayService.isOverlaySwitchEnabled() || !canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (webView == null) createOverlay()
        updateOverlay(
            AgentRuntimeController.progress.value,
            VisualAgentHudRuntime.target.value,
            AgentRuntimeController.overlayHiddenForCapture.value,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        pageReady = false
        pendingPayload = null
        overlayVisible = false
        webView?.let { view ->
            runCatching { windowManager?.removeView(view) }
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
        layoutParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOverlay() {
        if (webView != null) return
        val wm = windowManager ?: return
        val view = WebView(this).apply {
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
                textZoom = 100
                mediaPlaybackRequiresUserGesture = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // The full-opacity web rendering is enabled only while the HUD is actually visible.
            // During screenshots, real actions and idle time the window alpha is zero, so Android
            // does not treat the full-screen overlay as an obscuring touch surface.
            alpha = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        runCatching { wm.addView(view, params) }
            .onSuccess {
                webView = view
                layoutParams = params
            }
            .onFailure {
                view.destroy()
                webView = null
                layoutParams = null
                stopSelf()
            }
    }

    private fun updateOverlay(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        hiddenForCapture: Boolean,
    ) {
        val matchingTarget = target?.takeIf { it.taskId == progress.taskId }
        val visible = AgentOverlayService.isOverlaySwitchEnabled() && !hiddenForCapture && (
            progress.running ||
                progress.pendingConfirmation != null ||
                progress.pendingUserInput != null ||
                progress.userTakeoverPaused
            )
        setOverlayActive(visible)

        val lastLog = progress.logs.lastOrNull().orEmpty()
        val phase = phaseOf(progress, matchingTarget, lastLog)
        val resultPulse = visible &&
            matchingTarget?.positioned == true &&
            lastLog.startsWith("结果：") &&
            progress.updatedAt > lastClickRevision
        if (resultPulse) lastClickRevision = progress.updatedAt
        sendPayload(
            buildPayload(
                progress = progress,
                target = matchingTarget,
                visible = visible,
                phase = phase,
                clickRevision = if (resultPulse) progress.updatedAt else 0L,
            )
        )
    }

    private fun setOverlayActive(visible: Boolean) {
        val view = webView ?: return
        if (overlayVisible == visible) return
        overlayVisible = visible
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (visible) view.onResume() else view.onPause()
        val params = layoutParams ?: return
        val nextAlpha = if (visible) 1f else 0f
        if (params.alpha != nextAlpha) {
            params.alpha = nextAlpha
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
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
        if (lastLog.startsWith("结果：") || progress.status == "重新规划") return 4
        if (target?.positioned == true &&
            target.actionType in setOf("tap_xy", "tap_node") &&
            System.currentTimeMillis() - target.plannedAt <= TARGET_MOVE_VISIBLE_MS
        ) return 2
        if (lastLog == progress.currentAction && progress.currentAction.isNotBlank()) return 3
        if (lastLog.startsWith("模型") ||
            progress.lastResult.contains("分析") ||
            progress.lastResult.contains("GUI Plus") ||
            progress.lastResult.contains("VisualDirect")
        ) return 1
        return 0
    }

    private fun buildPayload(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        visible: Boolean,
        phase: Int,
        clickRevision: Long,
    ): String {
        val metrics = resources.displayMetrics
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
            .put("autoClickAfterMs", 0L)
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

    companion object {
        private const val ASSET_URL = "file:///android_asset/visual_agent_hud_runtime.html"
        private const val TARGET_MOVE_VISIBLE_MS = 1_200L
        private const val DEFAULT_CURSOR_X = 0.52f
        private const val DEFAULT_CURSOR_Y = 0.46f

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun ensureStarted(context: Context): Boolean {
            if (!AgentOverlayService.isOverlaySwitchEnabled() || !canDrawOverlays(context)) return false
            val appContext = context.applicationContext
            return runCatching {
                appContext.startService(Intent(appContext, VisualAgentHudOverlayService::class.java))
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, VisualAgentHudOverlayService::class.java))
        }
    }
}
