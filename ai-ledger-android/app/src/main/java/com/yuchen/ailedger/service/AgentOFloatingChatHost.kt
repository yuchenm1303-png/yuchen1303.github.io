package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ComposerAttachment
import com.yuchen.ailedger.model.ComposerAttachmentStatus
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AgentORenderSnapshot(
    val enabled: Boolean,
    val assistantState: AssistantUiState,
    val visualAgentEnabled: Boolean,
    val hiddenForCapture: Boolean,
)

/**
 * Agent O 普通聊天悬浮窗。
 *
 * 这是独立于 [VisualAgentCapsuleHost] 的第二个紧尺寸 TYPE_ACCESSIBILITY_OVERLAY：
 * - Agent O 开关只控制本窗口；
 * - 无限符号 Agent 开关继续控制视觉智能体 HUD 与原生智能体浮窗；
 * - 聊天请求和消息状态始终来自 [AssistantFloatingChatBridge]，本类不建立网络链。
 *
 * WebView 只承载网页版的原始视觉和动效。窗口定位、屏幕边界、输入法和跨应用拖动由
 * Android 宿主负责，不在网页中重画第二套界面。
 */
internal class AgentOFloatingChatHost(
    private val service: AccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val inputMethodManager = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density.coerceAtLeast(1f)

    private var webView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var started = false
    private var pageReady = false
    private var expanded = false
    private var hiddenForCapture = false
    private var wantsInputFocus = false
    private var pendingSnapshot: AgentORenderSnapshot? = null
    private var lastDispatchedPayload: String? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var pendingDragX = 0
    private var pendingDragY = 0
    private var dragFramePosted = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                AgentOFloatingChatController.enabled,
                AssistantFloatingChatBridge.state,
                AgentRuntimeController.enabled,
                AgentRuntimeController.overlayHiddenForCapture,
            ) { enabled, assistantState, visualAgentEnabled, hidden ->
                AgentORenderSnapshot(enabled, assistantState, visualAgentEnabled, hidden)
            }.collect(::render)
        }
    }

    fun destroy() {
        if (!started && webView == null) return
        started = false
        scope.cancel()
        destroyWindow()
    }

    private fun render(snapshot: AgentORenderSnapshot) {
        pendingSnapshot = snapshot
        if (!snapshot.enabled) {
            destroyWindow()
            return
        }
        if (!createWindow()) return
        applyCaptureVisibility(snapshot.hiddenForCapture)
        dispatchSnapshot(snapshot)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun createWindow(): Boolean {
        if (webView != null) return true
        val wm = windowManager ?: return false
        val size = dp(COLLAPSED_WINDOW_DP)
        val metrics = service.resources.displayMetrics

        val view = WebView(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background?.alpha = 0
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isLongClickable = false
            setOnLongClickListener { true }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Agent O 悬浮对话"
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                loadsImagesAutomatically = false
                blockNetworkImage = true
                blockNetworkLoads = true
                allowContentAccess = false
                allowFileAccess = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                textZoom = 100
                mediaPlaybackRequiresUserGesture = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
            }
            addJavascriptInterface(NativeBridge(), NATIVE_BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    lastDispatchedPayload = null
                    pendingSnapshot?.let(::dispatchSnapshot)
                    applyRuntimePauseState()
                }
            }
            alpha = 0f
            visibility = View.INVISIBLE
            loadUrl(ASSET_URL)
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            windowFlags(hidden = hiddenForCapture, wantsInputFocus = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - size - dp(12f)).coerceAtLeast(0)
            y = (topWindowInsetPx() + dp(72f)).coerceAtMost(
                (metrics.heightPixels - bottomWindowInsetPx() - size).coerceAtLeast(topWindowInsetPx())
            )
            alpha = if (hiddenForCapture) 0f else 1f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
                setFitInsetsIgnoringVisibility(true)
            }
        }

        return runCatching { wm.addView(view, params) }
            .onSuccess {
                webView = view
                layoutParams = params
                view.visibility = if (hiddenForCapture) View.INVISIBLE else View.VISIBLE
                view.alpha = if (hiddenForCapture) 0f else 1f
                if (!hiddenForCapture) view.onResume()
            }
            .onFailure { error ->
                view.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
                view.stopLoading()
                view.destroy()
                AgentRuntimeController.noteDiagnostic(
                    "Agent O 悬浮对话创建失败：${error.message ?: error.javaClass.simpleName}"
                )
            }
            .isSuccess
    }

    private fun destroyWindow() {
        pageReady = false
        pendingSnapshot = null
        lastDispatchedPayload = null
        expanded = false
        wantsInputFocus = false
        dragFramePosted = false
        webView?.let { view ->
            view.animate().cancel()
            inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
            runCatching { windowManager?.removeView(view) }
            view.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
        layoutParams = null
    }

    private fun dispatchSnapshot(snapshot: AgentORenderSnapshot, force: Boolean = false) {
        val payload = buildSnapshotJson(snapshot).toString()
        if (!pageReady) {
            pendingSnapshot = snapshot
            return
        }
        if (!force && payload == lastDispatchedPayload) return
        webView?.evaluateJavascript(
            "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.hydrate($payload,{connected:true,forceBottom:${force}});",
            null,
        )
        lastDispatchedPayload = payload
    }

    private fun buildSnapshotJson(snapshot: AgentORenderSnapshot): JSONObject {
        val state = snapshot.assistantState
        return JSONObject()
            .put("workspaceEnabled", false)
            .put("agentEnabled", snapshot.visualAgentEnabled)
            .put("onlineEnabled", state.onlineEnabled)
            .put("isSending", state.isSending)
            .put("selectedModelLabel", state.selectedModelLabel)
            .put("composerText", state.composerText)
            .put("attachment", state.composerAttachments.firstOrNull()?.toJson() ?: JSONObject.NULL)
            .put("messages", JSONArray().apply { state.messages.forEach { put(it.toJson()) } })
            .put("memory", JSONObject().put("loading", false).put("items", JSONArray()))
            .put("skills", JSONObject().put("loading", false).put("items", JSONArray()))
    }

    private fun ChatMessage.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("role", if (role == MessageRole.User) "user" else "assistant")
        .put("text", text)
        .put("status", when (status) {
            MessageStatus.Sending -> "sending"
            MessageStatus.Failed -> "failed"
            MessageStatus.Sent -> "sent"
        })
        .put("source", source.orEmpty())
        .put("modelLabel", modelLabel ?: model.orEmpty())
        .put("createdAt", createdAt)
        .put("errorText", errorText.orEmpty())
        .put("attachments", JSONArray().apply {
            attachments.forEach { attachment ->
                put(
                    JSONObject()
                        .put("id", attachment.id)
                        .put("fileName", attachment.fileName ?: "视觉附件")
                        .put("mimeType", attachment.mimeType)
                )
            }
        })
        .put("structuredData", structuredData?.let { data ->
            JSONObject()
                .put("title", data.title)
                .put("subtitle", data.subtitle.orEmpty())
                .put("metrics", JSONArray().apply {
                    data.metrics.take(4).forEach { metric ->
                        put(
                            JSONObject()
                                .put("label", metric.label)
                                .put("value", metric.value)
                                .put("unit", metric.unit.orEmpty())
                        )
                    }
                })
        })
        .put("webSources", JSONArray().apply {
            webSources.take(3).forEach { source ->
                put(
                    JSONObject()
                        .put("title", source.title)
                        .put("domain", source.domain)
                        .put("url", source.url)
                )
            }
        })

    private fun ComposerAttachment.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fileName", fileName ?: "视觉附件")
        .put("mimeType", mimeType)
        .put("status", status.name.lowercase())
        .put("statusLabel", when (status) {
            ComposerAttachmentStatus.Preparing -> "正在处理"
            ComposerAttachmentStatus.Ready -> "已准备"
            ComposerAttachmentStatus.Uploading -> "正在上传"
            ComposerAttachmentStatus.Failed -> errorText ?: "处理失败"
        })

    private fun handleNativeEnvelope(raw: String) {
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (envelope.optString("source") != BRIDGE_SOURCE) return
        val action = envelope.optString("action")
        val payload = envelope.optJSONObject("payload") ?: JSONObject()
        when (action) {
            "window.ready" -> pendingSnapshot?.let { dispatchSnapshot(it, force = true) }
            "window.form" -> setExpanded(payload.optInt("form", 0) == 2)
            "window.dragStart" -> {
                layoutParams?.let { params ->
                    dragStartX = params.x
                    dragStartY = params.y
                    pendingDragX = params.x
                    pendingDragY = params.y
                }
            }
            "window.drag" -> scheduleDrag(
                x = dragStartX + dp(payload.optDouble("dx", 0.0).toFloat()),
                y = dragStartY + dp(payload.optDouble("dy", 0.0).toFloat()),
            )
            "window.dragEnd" -> applyPendingDrag()
            "composer.focus" -> enableInputFocus()
            "composer.blur" -> disableInputFocus()
            else -> AssistantFloatingChatBridge.dispatch(action, payload)
        }
    }

    private fun scheduleDrag(x: Int, y: Int) {
        val params = layoutParams ?: return
        val metrics = service.resources.displayMetrics
        pendingDragX = x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        pendingDragY = y.coerceIn(
            topWindowInsetPx(),
            (metrics.heightPixels - bottomWindowInsetPx() - params.height)
                .coerceAtLeast(topWindowInsetPx()),
        )
        if (dragFramePosted) return
        dragFramePosted = true
        webView?.postOnAnimation {
            dragFramePosted = false
            applyPendingDrag()
        }
    }

    private fun applyPendingDrag() {
        val view = webView ?: return
        val params = layoutParams ?: return
        if (params.x == pendingDragX && params.y == pendingDragY) return
        params.x = pendingDragX
        params.y = pendingDragY
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        val view = webView ?: return
        val params = layoutParams ?: return
        val metrics = service.resources.displayMetrics
        val topInset = topWindowInsetPx()
        val bottomInset = bottomWindowInsetPx()
        val margin = dp(EXPANDED_SCREEN_MARGIN_DP)
        val availableWidth = (metrics.widthPixels - margin * 2).coerceAtLeast(dp(1f))
        val availableHeight = (metrics.heightPixels - topInset - bottomInset - margin * 2)
            .coerceAtLeast(dp(1f))
        val oldCenterX = params.x + params.width / 2
        val oldCenterY = params.y + params.height / 2

        val targetWidth = if (value) {
            minOf(dp(EXPANDED_MAX_WIDTH_DP), availableWidth)
        } else {
            dp(COLLAPSED_WINDOW_DP)
        }
        val targetHeight = if (value) {
            minOf(dp(EXPANDED_HEIGHT_DP), availableHeight)
        } else {
            dp(COLLAPSED_WINDOW_DP)
        }

        params.width = targetWidth
        params.height = targetHeight
        if (value) {
            // 展开窗口始终放进安全显示区中间。网页版面板在窗口内部保持原始比例，
            // 不再沿珠态右上角坐标硬撑开，从根源上避免顶部和右侧被屏幕裁掉。
            params.x = margin + ((availableWidth - targetWidth) / 2).coerceAtLeast(0)
            params.y = topInset + margin + ((availableHeight - targetHeight) / 2).coerceAtLeast(0)
        } else {
            params.x = (oldCenterX - targetWidth / 2)
                .coerceIn(0, (metrics.widthPixels - targetWidth).coerceAtLeast(0))
            params.y = (oldCenterY - targetHeight / 2).coerceIn(
                topInset,
                (metrics.heightPixels - bottomInset - targetHeight).coerceAtLeast(topInset),
            )
        }
        pendingDragX = params.x
        pendingDragY = params.y
        expanded = value
        if (!value) disableInputFocus()
        params.flags = windowFlags(hiddenForCapture, wantsInputFocus)
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun applyCaptureVisibility(hidden: Boolean) {
        if (hiddenForCapture == hidden && webView?.visibility == if (hidden) View.INVISIBLE else View.VISIBLE) return
        hiddenForCapture = hidden
        val view = webView ?: return
        val params = layoutParams ?: return
        params.flags = windowFlags(hidden, wantsInputFocus && !hidden)
        params.alpha = if (hidden) 0f else 1f
        view.alpha = params.alpha
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        runCatching { windowManager?.updateViewLayout(view, params) }
        applyRuntimePauseState()
    }

    private fun applyRuntimePauseState() {
        val view = webView ?: return
        if (hiddenForCapture) {
            view.evaluateJavascript("window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.suspend&&window.GuiPlusFloatingChat.suspend();", null)
            view.onPause()
        } else {
            view.onResume()
            view.evaluateJavascript("window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.resume&&window.GuiPlusFloatingChat.resume();", null)
        }
    }

    private fun enableInputFocus() {
        if (hiddenForCapture) return
        wantsInputFocus = true
        updateWindowFlags()
        val view = webView ?: return
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.post {
            view.evaluateJavascript(
                "var e=document.querySelector('.composer-input');if(e)e.focus();",
                null,
            )
            inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun disableInputFocus() {
        val view = webView
        inputMethodManager?.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
        wantsInputFocus = false
        updateWindowFlags()
    }

    private fun updateWindowFlags() {
        val view = webView ?: return
        val params = layoutParams ?: return
        val next = windowFlags(hiddenForCapture, wantsInputFocus && !hiddenForCapture)
        if (params.flags == next) return
        params.flags = next
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun windowFlags(hidden: Boolean, wantsInputFocus: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!wantsInputFocus) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (hidden) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        // FLAG_BLUR_BEHIND 会让部分 Android / OEM 把整块显示屏作为模糊区域。
        // 局部玻璃质感只由网页版 glass-shell 自身承担，宿主绝不再开启全屏系统模糊。
        return flags
    }

    private fun topWindowInsetPx(): Int {
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (resourceId > 0) service.resources.getDimensionPixelSize(resourceId) else dp(24f)
        return statusBar + dp(4f)
    }

    private fun bottomWindowInsetPx(): Int {
        val resourceId = service.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) service.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private inner class NativeBridge {
        @JavascriptInterface
        fun postMessage(raw: String) {
            mainHandler.post { handleNativeEnvelope(raw) }
        }

        @JavascriptInterface
        fun dispatch(action: String, payload: String) {
            val envelope = JSONObject()
                .put("source", BRIDGE_SOURCE)
                .put("action", action)
                .put("payload", runCatching { JSONObject(payload) }.getOrDefault(JSONObject()))
            mainHandler.post { handleNativeEnvelope(envelope.toString()) }
        }
    }

    companion object {
        private const val ASSET_URL = "file:///android_asset/agent_o_floating_chat_runtime.html"
        private const val NATIVE_BRIDGE_NAME = "GuiPlusNative"
        private const val BRIDGE_SOURCE = "gui-plus-floating-chat"
        private const val COLLAPSED_WINDOW_DP = 170f
        private const val EXPANDED_MAX_WIDTH_DP = 528f
        private const val EXPANDED_HEIGHT_DP = 430f
        private const val EXPANDED_SCREEN_MARGIN_DP = 6f
    }
}
