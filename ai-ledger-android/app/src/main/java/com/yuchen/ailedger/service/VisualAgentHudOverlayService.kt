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

private data class VisualHudRenderSnapshot(
    val progress: AgentOverlayProgress,
    val target: VisualAgentHudTarget?,
    val hiddenForCapture: Boolean,
    val tuning: VisualAgentHudTuningState,
)

/**
 * Full-screen visual-agent HUD rendered by the approved web implementation itself.
 * Android only supplies live state; CSS, SVG, filters, masks and animations stay in assets.
 */
class VisualAgentHudOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tuningStore by lazy(LazyThreadSafetyMode.NONE) {
        VisualAgentHudTuningStore.get(applicationContext)
    }
    private var windowManager: WindowManager? = null
    private var webView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pageReady = false
    private var pendingPayload: String? = null
    private var lastClickRevision = 0L
    private var lastPreviewGeneration = 0L
    private var overlayVisible = false

    override fun onCreate() {
        super.onCreate()
        val previewRequested = tuningStore.state.value.previewEnabled
        if ((!AgentOverlayService.isOverlaySwitchEnabled() && !previewRequested) || !canDrawOverlays(this)) {
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
                tuningStore.state,
            ) { progress, target, hidden, tuning ->
                VisualHudRenderSnapshot(progress, target, hidden, tuning)
            }.collect { snapshot ->
                updateOverlay(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val previewRequested = tuningStore.state.value.previewEnabled
        if ((!AgentOverlayService.isOverlaySwitchEnabled() && !previewRequested) || !canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (webView == null) createOverlay()
        updateOverlay(
            VisualHudRenderSnapshot(
                progress = AgentRuntimeController.progress.value,
                target = VisualAgentHudRuntime.target.value,
                hiddenForCapture = AgentRuntimeController.overlayHiddenForCapture.value,
                tuning = tuningStore.state.value,
            )
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
            // During screenshots, actual device actions and idle time the full-screen window is
            // transparent. The HUD itself never handles touch input.
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

    private fun updateOverlay(snapshot: VisualHudRenderSnapshot) {
        val progress = snapshot.progress
        val tuning = snapshot.tuning
        val matchingTarget = snapshot.target?.takeIf { it.taskId == progress.taskId }
        val realHudActive = AgentOverlayService.isOverlaySwitchEnabled() && (
            progress.running ||
                progress.pendingConfirmation != null ||
                progress.pendingUserInput != null ||
                progress.userTakeoverPaused
            )
        val sampleMode = tuning.previewEnabled && !realHudActive
        val visible = !snapshot.hiddenForCapture && (realHudActive || sampleMode)
        setOverlayActive(visible)

        val lastLog = progress.logs.lastOrNull().orEmpty()
        val phase = if (sampleMode) SAMPLE_PHASE else phaseOf(progress, matchingTarget, lastLog)
        val resultPulse = visible && !sampleMode &&
            matchingTarget?.positioned == true &&
            lastLog.startsWith("结果：") &&
            progress.updatedAt > lastClickRevision
        if (resultPulse) lastClickRevision = progress.updatedAt

        val sampleGenerationChanged = sampleMode &&
            tuning.previewGeneration > 0L &&
            tuning.previewGeneration != lastPreviewGeneration
        if (sampleGenerationChanged) lastPreviewGeneration = tuning.previewGeneration

        sendPayload(
            buildPayload(
                progress = progress,
                target = matchingTarget,
                tuning = tuning,
                visible = visible,
                phase = phase,
                sampleMode = sampleMode,
                clickRevision = if (resultPulse) progress.updatedAt else 0L,
                autoClickAfterMs = if (sampleGenerationChanged) SAMPLE_CLICK_DELAY_MS else 0L,
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
        tuning: VisualAgentHudTuningState,
        visible: Boolean,
        phase: Int,
        sampleMode: Boolean,
        clickRevision: Long,
        autoClickAfterMs: Long,
    ): String {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val xNorm = if (sampleMode) {
            SAMPLE_CURSOR_X
        } else {
            when {
                target == null -> DEFAULT_CURSOR_X
                target.normalized -> target.x
                else -> target.x / width.toFloat()
            }
        }.coerceIn(0f, 1f)
        val yNorm = if (sampleMode) {
            SAMPLE_CURSOR_Y
        } else {
            when {
                target == null -> DEFAULT_CURSOR_Y
                target.normalized -> target.y
                else -> target.y / height.toFloat()
            }
        }.coerceIn(0f, 1f)

        val bubbleTitle = if (sampleMode) {
            "准备点击“视觉智能样本”"
        } else {
            progress.currentAction.ifBlank { "正在执行视觉任务" }
        }
        val thought = if (sampleMode) {
            "样本已开启。拖动设置页参数时，边缘光、SVG 光标、热点和点击反馈会实时更新。"
        } else {
            target?.detail
                ?.takeIf(String::isNotBlank)
                ?: progress.lastResult.takeIf(String::isNotBlank)
                ?: "正在根据页面证据选择下一步操作。"
        }
        val source = if (sampleMode) {
            "样本预览"
        } else {
            when (target?.actionType) {
                "tap_node" -> "视觉识别 + 节点"
                "tap_xy" -> "视觉识别"
                else -> "智能体执行"
            }
        }

        return JSONObject()
            .put("visible", visible)
            .put("xNorm", xNorm.toDouble())
            .put("yNorm", yNorm.toDouble())
            .put("phase", phase)
            .put("title", if (sampleMode) "正在调试视觉智能" else "")
            .put("meta", if (sampleMode) "样本预览" else "")
            .put("bubbleTitle", bubbleTitle)
            .put("currentAction", if (sampleMode) bubbleTitle else progress.currentAction)
            .put("thought", thought)
            .put("result", if (sampleMode) "所有参数已接入网页版 HUD" else progress.lastResult)
            .put("confidence", if (sampleMode) "94%" else if (target?.positioned == true) "已定位" else "—")
            .put("actionSource", source)
            .put("debugLatency", if (sampleMode) "mode: live_parameter_preview" else "latency_total: —")
            .put("autoClickAfterMs", autoClickAfterMs)
            .put("clickRevision", clickRevision)
            .put("parameters", tuning.parameters.toJson())
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
        private const val SAMPLE_CLICK_DELAY_MS = 820L
        private const val SAMPLE_PHASE = 2
        private const val DEFAULT_CURSOR_X = 0.52f
        private const val DEFAULT_CURSOR_Y = 0.46f
        private const val SAMPLE_CURSOR_X = 0.72f
        private const val SAMPLE_CURSOR_Y = 0.27f

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun ensureStarted(context: Context): Boolean {
            val appContext = context.applicationContext
            val previewRequested = VisualAgentHudTuningStore.get(appContext).state.value.previewEnabled
            if ((!AgentOverlayService.isOverlaySwitchEnabled() && !previewRequested) || !canDrawOverlays(appContext)) {
                return false
            }
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
