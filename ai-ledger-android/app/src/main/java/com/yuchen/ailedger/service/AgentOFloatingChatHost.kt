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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
 * WebView 直接运行用户确认的 V8.4 网页视觉；Android 只负责系统窗口、屏幕边界、输入法、
 * 截图隐藏和真实跨应用拖动。无限符号 Agent 仍由 VisualAgentCapsuleHost 独立控制。
 */
internal class AgentOFloatingChatHost(
    private val service: AccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val inputMethodManager =
        service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density.coerceAtLeast(1f)
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var webView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var started = false
    private var pageReady = false
    private var expanded = false
    private var hiddenForCapture = false
    private var wantsInputFocus = false

    private var pendingSnapshot: AgentORenderSnapshot? = null
    private var lastDispatchedSnapshot: AgentORenderSnapshot? = null
    private var lastDispatchedPayload: String? = null
    private var snapshotFramePosted = false
    private var forceSnapshotDispatch = false

    private var dragStartX = 0
    private var dragStartY = 0
    private var pendingDragX = 0
    private var pendingDragY = 0
    private var dragFramePosted = false

    private var collapsedPointerId = MotionEvent.INVALID_POINTER_ID
    private var collapsedStartRawX = 0f
    private var collapsedStartRawY = 0f
    private var collapsedDragStartX = 0
    private var collapsedDragStartY = 0
    private var collapsedMoved = false

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
        // 珠态不可见任何聊天内容，不序列化消息，也不跨 JS 桥派发状态。
        if (expanded) scheduleSnapshotDispatch()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface", "ClickableViewAccessibility")
    private fun createWindow(): Boolean {
        if (webView != null) return true
        val wm = windowManager ?: return false
        val size = dp(COLLAPSED_WINDOW_DP)
        val metrics = service.resources.displayMetrics

        val view = WebView(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background?.alpha = 0
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
            }
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isLongClickable = false
            setOnLongClickListener { true }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Agent O 悬浮对话"
            setOnTouchListener { _, event ->
                handleCollapsedWindowTouch(event)
                // WebView 仍需收到同一事件流，用于原版珠态形变与点击展开。
                false
            }
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
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = true

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    lastDispatchedPayload = null
                    lastDispatchedSnapshot = null
                    if (expanded) scheduleSnapshotDispatch(force = true)
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
                (metrics.heightPixels - bottomWindowInsetPx() - size)
                    .coerceAtLeast(topWindowInsetPx())
            )
            alpha = if (hiddenForCapture) 0f else 1f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
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
                pendingDragX = params.x
                pendingDragY = params.y
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
        lastDispatchedSnapshot = null
        lastDispatchedPayload = null
        expanded = false
        wantsInputFocus = false
        snapshotFramePosted = false
        forceSnapshotDispatch = false
        dragFramePosted = false
        collapsedPointerId = MotionEvent.INVALID_POINTER_ID
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

    private fun scheduleSnapshotDispatch(force: Boolean = false) {
        if (!expanded || !pageReady) return
        forceSnapshotDispatch = forceSnapshotDispatch || force
        if (snapshotFramePosted) return
        snapshotFramePosted = true
        webView?.postOnAnimation {
            snapshotFramePosted = false
            val snapshot = pendingSnapshot ?: return@postOnAnimation
            val shouldForce = forceSnapshotDispatch
            forceSnapshotDispatch = false
            dispatchSnapshotNow(snapshot, shouldForce)
        }
    }

    private fun dispatchSnapshotNow(snapshot: AgentORenderSnapshot, force: Boolean) {
        if (!expanded || !pageReady) return
        val previous = lastDispatchedSnapshot
        if (!force && previous != null && canPatchLastMessage(previous, snapshot)) {
            val message = snapshot.assistantState.messages.last()
            val script = buildString {
                append("window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.patchMessage&&")
                append("window.GuiPlusFloatingChat.patchMessage(")
                append(JSONObject.quote(message.id)).append(',')
                append(JSONObject.quote(message.text)).append(',')
                append(JSONObject.quote(message.status.webValue))
                append(");")
            }
            webView?.evaluateJavascript(script, null)
            lastDispatchedSnapshot = snapshot
            return
        }

        val payload = buildSnapshotJson(snapshot).toString()
        if (!force && payload == lastDispatchedPayload) return
        webView?.evaluateJavascript(
            "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.hydrate(" +
                "$payload,{connected:true,forceBottom:$force});",
            null,
        )
        lastDispatchedPayload = payload
        lastDispatchedSnapshot = snapshot
    }

    private fun canPatchLastMessage(
        previous: AgentORenderSnapshot,
        next: AgentORenderSnapshot,
    ): Boolean {
        if (previous.visualAgentEnabled != next.visualAgentEnabled ||
            previous.hiddenForCapture != next.hiddenForCapture
        ) return false
        val oldState = previous.assistantState
        val newState = next.assistantState
        if (oldState.onlineEnabled != newState.onlineEnabled ||
            oldState.isSending != newState.isSending ||
            oldState.selectedModelLabel != newState.selectedModelLabel ||
            oldState.composerText != newState.composerText ||
            oldState.composerAttachments != newState.composerAttachments ||
            oldState.messages.size != newState.messages.size ||
            oldState.messages.isEmpty()
        ) return false
        if (oldState.messages.dropLast(1) != newState.messages.dropLast(1)) return false

        val oldLast = oldState.messages.last()
        val newLast = newState.messages.last()
        return oldLast.id == newLast.id &&
            oldLast.copy(
                text = newLast.text,
                status = newLast.status,
                errorText = newLast.errorText,
            ) == newLast
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
            .put(
                "attachment",
                state.composerAttachments.firstOrNull()?.toJson() ?: JSONObject.NULL,
            )
            .put("messages", JSONArray().apply {
                state.messages.forEach { put(it.toJson()) }
            })
            .put("memory", JSONObject().put("loading", false).put("items", JSONArray()))
            .put("skills", JSONObject().put("loading", false).put("items", JSONArray()))
    }

    private fun ChatMessage.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("role", if (role == MessageRole.User) "user" else "assistant")
        .put("text", text)
        .put("status", status.webValue)
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

    private val MessageStatus.webValue: String
        get() = when (this) {
            MessageStatus.Sending -> "sending"
            MessageStatus.Failed -> "failed"
            MessageStatus.Sent -> "sent"
        }

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
            "window.ready" -> if (expanded) scheduleSnapshotDispatch(force = true)
            "window.form" -> setExpanded(payload.optInt("form", 0) == 2)
            "window.dragStart" -> {
                // 珠态由 Android raw MotionEvent 直接拖动；桥接拖动只服务展开工具栏。
                if (!expanded) return
                layoutParams?.let { params ->
                    dragStartX = params.x
                    dragStartY = params.y
                    pendingDragX = params.x
                    pendingDragY = params.y
                }
            }
            "window.drag" -> {
                if (!expanded) return
                scheduleDragPixels(
                    x = dragStartX + dp(payload.optDouble("dx", 0.0).toFloat()),
                    y = dragStartY + dp(payload.optDouble("dy", 0.0).toFloat()),
                )
            }
            "window.dragEnd" -> if (expanded) applyPendingDrag()
            "composer.focus" -> enableInputFocus()
            "composer.blur" -> disableInputFocus()
            else -> AssistantFloatingChatBridge.dispatch(action, payload)
        }
    }

    private fun handleCollapsedWindowTouch(event: MotionEvent) {
        if (expanded || hiddenForCapture) return
        val params = layoutParams ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                collapsedPointerId = event.getPointerId(0)
                collapsedStartRawX = event.rawX
                collapsedStartRawY = event.rawY
                collapsedDragStartX = params.x
                collapsedDragStartY = params.y
                collapsedMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(collapsedPointerId)
                if (index < 0) return
                val dx = event.rawX - collapsedStartRawX
                val dy = event.rawY - collapsedStartRawY
                if (!collapsedMoved && dx * dx + dy * dy >= touchSlop * touchSlop) {
                    collapsedMoved = true
                }
                if (collapsedMoved) {
                    scheduleDragPixels(
                        x = collapsedDragStartX + dx.roundToInt(),
                        y = collapsedDragStartY + dy.roundToInt(),
                    )
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (collapsedMoved) applyPendingDrag()
                collapsedPointerId = MotionEvent.INVALID_POINTER_ID
                collapsedMoved = false
            }
        }
    }

    private fun scheduleDragPixels(x: Int, y: Int) {
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
        val availableHeight =
            (metrics.heightPixels - topInset - bottomInset - margin * 2).coerceAtLeast(dp(1f))
        val oldCenterX = params.x + params.width / 2
        val oldCenterY = params.y + params.height / 2

        val targetWidth: Int
        val targetHeight: Int
        if (value) {
            val logicalWidth = dp(EXPANDED_LOGICAL_WIDTH_DP)
            val logicalHeight = dp(EXPANDED_LOGICAL_HEIGHT_DP)
            val scale = minOf(
                1f,
                availableWidth / logicalWidth.toFloat(),
                availableHeight / logicalHeight.toFloat(),
            )
            targetWidth = (logicalWidth * scale).roundToInt().coerceAtLeast(dp(240f))
            targetHeight = (logicalHeight * scale).roundToInt().coerceAtLeast(dp(309f))
        } else {
            targetWidth = dp(COLLAPSED_WINDOW_DP)
            targetHeight = dp(COLLAPSED_WINDOW_DP)
        }

        params.width = targetWidth
        params.height = targetHeight
        if (value) {
            params.x = margin + ((availableWidth - targetWidth) / 2).coerceAtLeast(0)
            params.y = topInset + margin +
                ((availableHeight - targetHeight) / 2).coerceAtLeast(0)
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
        if (value) view.post { scheduleSnapshotDispatch(force = true) }
    }

    private fun applyCaptureVisibility(hidden: Boolean) {
        if (hiddenForCapture == hidden &&
            webView?.visibility == if (hidden) View.INVISIBLE else View.VISIBLE
        ) return
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
            view.evaluateJavascript(
                "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.suspend&&" +
                    "window.GuiPlusFloatingChat.suspend();",
                null,
            )
            view.onPause()
        } else {
            view.onResume()
            view.evaluateJavascript(
                "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.resume&&" +
                    "window.GuiPlusFloatingChat.resume();",
                null,
            )
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
        return flags
    }

    private fun topWindowInsetPx(): Int {
        val resourceId =
            service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar =
            if (resourceId > 0) service.resources.getDimensionPixelSize(resourceId) else dp(24f)
        return statusBar + dp(4f)
    }

    private fun bottomWindowInsetPx(): Int {
        val resourceId =
            service.resources.getIdentifier("navigation_bar_height", "dimen", "android")
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
                .put(
                    "payload",
                    runCatching { JSONObject(payload) }.getOrDefault(JSONObject()),
                )
            mainHandler.post { handleNativeEnvelope(envelope.toString()) }
        }

        @JavascriptInterface
        fun usesNativeWindowDrag(): Boolean = true
    }

    companion object {
        private const val ASSET_URL =
            "file:///android_asset/agent_o_floating_chat_runtime.html"
        private const val NATIVE_BRIDGE_NAME = "GuiPlusNative"
        private const val BRIDGE_SOURCE = "gui-plus-floating-chat"

        private const val COLLAPSED_WINDOW_DP = 170f
        private const val EXPANDED_LOGICAL_WIDTH_DP = 560f
        private const val EXPANDED_LOGICAL_HEIGHT_DP = 720f
        private const val EXPANDED_SCREEN_MARGIN_DP = 8f
    }
}
