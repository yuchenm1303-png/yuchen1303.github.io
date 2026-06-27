package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
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
 * The single visual-HUD host. It belongs to the connected AccessibilityService and therefore uses
 * TYPE_ACCESSIBILITY_OVERLAY instead of SYSTEM_ALERT_WINDOW. The web assets remain the only HUD
 * implementation; Android only owns lifecycle, state delivery and screenshot suppression.
 */
internal class VisualAgentHudHost(
    private val service: AccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tuningStore = VisualAgentHudTuningStore.get(service.applicationContext)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var webView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pageReady = false
    private var pendingPayload: String? = null
    private var lastClickRevision = 0L
    private var lastPreviewGeneration = 0L
    private var overlayContentActive = false
    private var captureSuppressed = false
    private var presentationRevision = 0L
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                AgentRuntimeController.progress,
                VisualAgentHudRuntime.target,
                AgentRuntimeController.overlayHiddenForCapture,
                tuningStore.state,
            ) { progress, target, hidden, tuning ->
                VisualHudRenderSnapshot(progress, target, hidden, tuning)
            }.collect(::updateOverlay)
        }
    }

    fun destroy() {
        if (!started && webView == null) return
        started = false
        scope.cancel()
        destroyOverlay()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOverlay(): Boolean {
        if (webView != null) return true
        val wm = windowManager ?: return false
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
            alpha = 0f
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
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        return runCatching { wm.addView(view, params) }
            .onSuccess {
                webView = view
                layoutParams = params
            }
            .onFailure { error ->
                view.stopLoading()
                view.destroy()
                AgentRuntimeController.noteDiagnostic(
                    "视觉 HUD 无障碍浮层创建失败：${error.message ?: error.javaClass.simpleName}"
                )
            }
            .isSuccess
    }

    private fun destroyOverlay() {
        presentationRevision += 1L
        pageReady = false
        pendingPayload = null
        overlayContentActive = false
        captureSuppressed = false
        webView?.let { view ->
            view.animate().cancel()
            runCatching { windowManager?.removeView(view) }
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
        layoutParams = null
    }

    private fun updateOverlay(snapshot: VisualHudRenderSnapshot) {
        val progress = snapshot.progress
        val tuning = snapshot.tuning
        val matchingTarget = snapshot.target?.takeIf { it.taskId == progress.taskId }
        val realHudActive = shouldPresentRuntime(progress)
        val sampleMode = tuning.previewEnabled && !realHudActive
        val contentActive = realHudActive || sampleMode

        if (!contentActive) {
            if (webView != null) destroyOverlay()
            return
        }
        if (!createOverlay()) return

        val visibleToUser = !snapshot.hiddenForCapture
        setOverlayPresentation(hiddenForCapture = snapshot.hiddenForCapture)

        val lastLog = progress.logs.lastOrNull().orEmpty()
        val phase = if (sampleMode) SAMPLE_PHASE else phaseOf(progress, matchingTarget, lastLog)
        val resultPulse = visibleToUser && !sampleMode &&
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
                visible = true,
                phase = phase,
                sampleMode = sampleMode,
                clickRevision = if (resultPulse) progress.updatedAt else 0L,
                autoClickAfterMs = if (sampleGenerationChanged) SAMPLE_CLICK_DELAY_MS else 0L,
            )
        )
    }

    private fun setOverlayPresentation(hiddenForCapture: Boolean) {
        val view = webView ?: return
        val nextSuppressed = hiddenForCapture
        val activeChanged = !overlayContentActive
        val suppressionChanged = captureSuppressed != nextSuppressed
        if (!activeChanged && !suppressionChanged) return

        overlayContentActive = true
        captureSuppressed = nextSuppressed
        presentationRevision += 1L
        val revision = presentationRevision
        view.animate().cancel()

        if (nextSuppressed) {
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                view.onResume()
            }
            updateWindowAlpha(1f)
            if (view.alpha <= 0.001f) {
                view.alpha = 0f
                updateWindowAlpha(0f)
            } else {
                view.animate()
                    .alpha(0f)
                    .setDuration(HUD_CAPTURE_FADE_OUT_MS)
                    .setInterpolator(HUD_CAPTURE_HIDE_INTERPOLATOR)
                    .withEndAction {
                        if (
                            presentationRevision == revision &&
                            overlayContentActive &&
                            captureSuppressed
                        ) {
                            view.alpha = 0f
                            updateWindowAlpha(0f)
                        }
                    }
                    .start()
            }
        } else {
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                view.onResume()
            }
            updateWindowAlpha(1f)
            if (view.alpha < 0.999f) {
                view.animate()
                    .alpha(1f)
                    .setDuration(HUD_RESTORE_FADE_MS)
                    .setInterpolator(HUD_RESTORE_INTERPOLATOR)
                    .withEndAction {
                        if (
                            presentationRevision == revision &&
                            overlayContentActive &&
                            !captureSuppressed
                        ) {
                            view.alpha = 1f
                        }
                    }
                    .start()
            } else {
                view.alpha = 1f
            }
        }
    }

    private fun updateWindowAlpha(alpha: Float) {
        val view = webView ?: return
        val params = layoutParams ?: return
        if (params.alpha == alpha) return
        params.alpha = alpha
        runCatching { windowManager?.updateViewLayout(view, params) }
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
        val metrics = service.resources.displayMetrics
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
        private const val HUD_CAPTURE_FADE_OUT_MS = 84L
        private const val HUD_RESTORE_FADE_MS = 128L
        private val HUD_CAPTURE_HIDE_INTERPOLATOR = AccelerateInterpolator(1.25f)
        private val HUD_RESTORE_INTERPOLATOR = DecelerateInterpolator(1.55f)
        private const val SAMPLE_PHASE = 2
        private const val DEFAULT_CURSOR_X = 0.52f
        private const val DEFAULT_CURSOR_Y = 0.46f
        private const val SAMPLE_CURSOR_X = 0.72f
        private const val SAMPLE_CURSOR_Y = 0.27f

        private fun shouldPresentRuntime(progress: AgentOverlayProgress): Boolean =
            progress.running ||
                progress.pendingConfirmation != null ||
                progress.pendingUserInput != null ||
                progress.userTakeoverPaused
    }
}
