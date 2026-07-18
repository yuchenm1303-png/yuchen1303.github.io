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

private data class AgentOFixedFrame(
    val width: Int,
    val height: Int,
    val scale: Float,
    val safeX: Int,
    val safeY: Int,
)

private data class AgentOVisibleBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private enum class AgentOWindowPhase {
    Collapsed,
    Expanding,
    Expanded,
    Collapsing,
}

/**
 * Agent O 普通聊天悬浮窗。
 *
 * V8.4 WebView 在创建后始终使用同一个固定舞台和同一个 Surface。展开/折叠只改变网页内部
 * 原版几何，不在动画中修改 WindowManager 宽高、重建 WebView viewport 或触发 WebGL
 * surface 重分配。珠态使用独立紧尺寸触摸窗，主 WebView 在珠态完全不可触摸，因此固定舞台
 * 的透明区域不会阻挡下方 App。
 *
 * Android FLAG_BLUR_BEHIND 在部分厂商系统会退化为整屏模糊，因此这里永久禁用跨窗口模糊。
 * 玻璃背景、边缘光与局部雾化只由原版网页 glass-shell 自身绘制。
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
    private var orbTouchView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var orbTouchParams: WindowManager.LayoutParams? = null
    private var fixedFrame: AgentOFixedFrame? = null

    private var started = false
    private var pageReady = false
    private var phase = AgentOWindowPhase.Collapsed
    private var transitionRevision = 0L
    private var hiddenForCapture = false
    private var wantsInputFocus = false
    private var lastOrbTouchActive: Boolean? = null

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
    private var pendingOrbVelocity = 0f

    private var collapsedPointerId = MotionEvent.INVALID_POINTER_ID
    private var collapsedStartRawX = 0f
    private var collapsedStartRawY = 0f
    private var collapsedLastRawX = 0f
    private var collapsedLastEventTime = 0L
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
        if (phase == AgentOWindowPhase.Expanded) scheduleSnapshotDispatch()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface", "ClickableViewAccessibility")
    private fun createWindow(): Boolean {
        if (webView != null) return true
        val wm = windowManager ?: return false
        val frame = calculateFixedFrame()
        fixedFrame = frame

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
                    if (phase == AgentOWindowPhase.Expanded) {
                        scheduleSnapshotDispatch(force = true)
                    }
                    applyRuntimePauseState()
                }
            }
            alpha = 0f
            visibility = View.INVISIBLE
            loadUrl(ASSET_URL)
        }

        val params = WindowManager.LayoutParams(
            frame.width,
            frame.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            mainWindowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = frame.safeX
            y = frame.safeY
            alpha = if (hiddenForCapture) 0f else 1f
            // 保持固定 WebView viewport；输入法只平移可见区域，不触发舞台缩放与 WebGL 重建。
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            applyCutoutContract()
        }

        val touchSize = orbTouchSizePx(frame)
        val touchView = View(service).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Agent O 玻璃珠，拖动移动，点击展开"
            isClickable = true
            isFocusable = false
            setOnTouchListener { _, event -> handleCollapsedTouch(event) }
        }
        val touchParams = WindowManager.LayoutParams(
            touchSize,
            touchSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            orbTouchWindowFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            applyCutoutContract()
        }

        return runCatching {
            wm.addView(view, params)
            webView = view
            layoutParams = params
            pendingDragX = params.x
            pendingDragY = params.y

            updateOrbTouchCoordinates(touchParams, params, frame)
            wm.addView(touchView, touchParams)
            orbTouchView = touchView
            orbTouchParams = touchParams
            lastOrbTouchActive = null

            view.visibility = if (hiddenForCapture) View.INVISIBLE else View.VISIBLE
            view.alpha = if (hiddenForCapture) 0f else 1f
            if (!hiddenForCapture) view.onResume()
            applyOrbTouchState()
        }.onFailure { error ->
            runCatching { wm.removeView(touchView) }
            runCatching { wm.removeView(view) }
            view.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
            view.stopLoading()
            view.destroy()
            webView = null
            orbTouchView = null
            layoutParams = null
            orbTouchParams = null
            fixedFrame = null
            lastOrbTouchActive = null
            AgentRuntimeController.noteDiagnostic(
                "Agent O 悬浮对话创建失败：${error.message ?: error.javaClass.simpleName}"
            )
        }.isSuccess
    }

    private fun WindowManager.LayoutParams.applyCutoutContract() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            setFitInsetsIgnoringVisibility(true)
        }
    }

    private fun destroyWindow() {
        pageReady = false
        pendingSnapshot = null
        lastDispatchedSnapshot = null
        lastDispatchedPayload = null
        phase = AgentOWindowPhase.Collapsed
        transitionRevision = 0L
        wantsInputFocus = false
        lastOrbTouchActive = null
        snapshotFramePosted = false
        forceSnapshotDispatch = false
        dragFramePosted = false
        pendingOrbVelocity = 0f
        collapsedPointerId = MotionEvent.INVALID_POINTER_ID

        orbTouchView?.let { touch ->
            runCatching { windowManager?.removeView(touch) }
        }
        orbTouchView = null
        orbTouchParams = null

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
        fixedFrame = null
    }

    private fun scheduleSnapshotDispatch(force: Boolean = false) {
        if (phase != AgentOWindowPhase.Expanded || !pageReady) return
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
        if (phase != AgentOWindowPhase.Expanded || !pageReady) return
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
            "window.ready" -> if (phase == AgentOWindowPhase.Expanded) {
                scheduleSnapshotDispatch(force = true)
            }
            "window.transition" -> handleWindowTransition(payload)
            // 兼容旧资源的最终状态回调；新动画链不再依赖 window.form。
            "window.form" -> {
                val expanded = payload.optInt("form", 0) == 2
                handleWindowTransition(
                    JSONObject()
                        .put("revision", transitionRevision + 1L)
                        .put("state", if (expanded) "expanded" else "collapsed")
                )
            }
            "window.dragStart" -> beginExpandedPanelDrag()
            "window.drag" -> moveExpandedPanelDrag(
                dxCss = payload.optDouble("dx", 0.0),
                dyCss = payload.optDouble("dy", 0.0),
            )
            "window.dragEnd" -> endExpandedPanelDrag()
            "composer.focus" -> if (phase == AgentOWindowPhase.Expanded) enableInputFocus()
            "composer.blur" -> disableInputFocus()
            else -> AssistantFloatingChatBridge.dispatch(action, payload)
        }
    }

    private fun handleWindowTransition(payload: JSONObject) {
        val revision = payload.optLong("revision", transitionRevision)
        if (revision < transitionRevision) return
        transitionRevision = revision
        when (payload.optString("state")) {
            "expanding" -> {
                phase = AgentOWindowPhase.Expanding
                wantsInputFocus = false
                applyMainWindowState()
                applyOrbTouchState()
            }
            "expanded" -> {
                phase = AgentOWindowPhase.Expanded
                applyMainWindowState()
                applyOrbTouchState()
                scheduleSnapshotDispatch(force = true)
            }
            "collapsing" -> {
                phase = AgentOWindowPhase.Collapsing
                disableInputFocus()
                applyMainWindowState()
                applyOrbTouchState()
            }
            "collapsed" -> {
                phase = AgentOWindowPhase.Collapsed
                wantsInputFocus = false
                applyMainWindowState()
                updateOrbTouchLayout()
                applyOrbTouchState()
            }
        }
    }

    private fun handleCollapsedTouch(event: MotionEvent): Boolean {
        if (phase != AgentOWindowPhase.Collapsed || hiddenForCapture) return false
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                collapsedPointerId = event.getPointerId(0)
                collapsedStartRawX = event.rawX
                collapsedStartRawY = event.rawY
                collapsedLastRawX = event.rawX
                collapsedLastEventTime = event.eventTime
                collapsedDragStartX = params.x
                collapsedDragStartY = params.y
                collapsedMoved = false
                evaluateOrbScript(
                    "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.nativeOrbDown&&" +
                        "window.GuiPlusFloatingChat.nativeOrbDown();"
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(collapsedPointerId)
                if (index < 0) return true
                val dx = event.rawX - collapsedStartRawX
                val dy = event.rawY - collapsedStartRawY
                if (!collapsedMoved && dx * dx + dy * dy >= touchSlop * touchSlop) {
                    collapsedMoved = true
                }
                val dt = (event.eventTime - collapsedLastEventTime).coerceAtLeast(8L)
                pendingOrbVelocity = (event.rawX - collapsedLastRawX) / density / dt.toFloat()
                collapsedLastRawX = event.rawX
                collapsedLastEventTime = event.eventTime
                if (collapsedMoved) {
                    scheduleCollapsedDrag(
                        x = collapsedDragStartX + dx.roundToInt(),
                        y = collapsedDragStartY + dy.roundToInt(),
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (collapsedMoved) {
                    applyPendingDrag()
                    updateOrbTouchLayout()
                    evaluateOrbScript(
                        "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.nativeOrbUp&&" +
                            "window.GuiPlusFloatingChat.nativeOrbUp(true);"
                    )
                } else {
                    beginExpansionFromCurrentBeadPosition()
                }
                collapsedPointerId = MotionEvent.INVALID_POINTER_ID
                collapsedMoved = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                applyPendingDrag()
                updateOrbTouchLayout()
                evaluateOrbScript(
                    "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.nativeOrbCancel&&" +
                        "window.GuiPlusFloatingChat.nativeOrbCancel();"
                )
                collapsedPointerId = MotionEvent.INVALID_POINTER_ID
                collapsedMoved = false
                return true
            }
        }
        return true
    }

    private fun beginExpansionFromCurrentBeadPosition() {
        val view = webView ?: return
        val params = layoutParams ?: return
        val frame = fixedFrame ?: return
        val oldX = params.x
        val oldY = params.y
        val (targetX, targetY) = clampExpandedWindowPosition(oldX, oldY, frame)
        val scalePx = density * frame.scale
        val rebaseXCss = (oldX - targetX) / scalePx
        val rebaseYCss = (oldY - targetY) / scalePx

        phase = AgentOWindowPhase.Expanding
        applyOrbTouchState()
        params.x = targetX
        params.y = targetY
        pendingDragX = targetX
        pendingDragY = targetY
        runCatching { windowManager?.updateViewLayout(view, params) }

        evaluateOrbScript(
            "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.nativeOrbTap&&" +
                "window.GuiPlusFloatingChat.nativeOrbTap($rebaseXCss,$rebaseYCss);"
        )
    }

    private fun evaluateOrbScript(script: String) {
        if (!pageReady) return
        webView?.evaluateJavascript(script, null)
    }

    private fun beginExpandedPanelDrag() {
        if (phase != AgentOWindowPhase.Expanded) return
        layoutParams?.let { params ->
            dragStartX = params.x
            dragStartY = params.y
            pendingDragX = params.x
            pendingDragY = params.y
        }
    }

    private fun moveExpandedPanelDrag(dxCss: Double, dyCss: Double) {
        if (phase != AgentOWindowPhase.Expanded) return
        val frame = fixedFrame ?: return
        val x = dragStartX + dp(dxCss.toFloat())
        val y = dragStartY + dp(dyCss.toFloat())
        val (clampedX, clampedY) = clampExpandedWindowPosition(x, y, frame)
        pendingDragX = clampedX
        pendingDragY = clampedY
        // 网页端已按 requestAnimationFrame 合并；这里直接提交，避免再延迟一帧。
        applyPendingDrag()
    }

    private fun endExpandedPanelDrag() {
        if (phase == AgentOWindowPhase.Expanded) applyPendingDrag()
    }

    private fun scheduleCollapsedDrag(x: Int, y: Int) {
        val frame = fixedFrame ?: return
        val metrics = service.resources.displayMetrics
        val margin = dp(COLLAPSED_SCREEN_MARGIN_DP)
        val radius = orbVisualRadiusPx(frame)
        val centerX = frame.width / 2
        val centerY = orbCenterYPx(frame)
        pendingDragX = x.coerceIn(
            margin + radius - centerX,
            metrics.widthPixels - margin - radius - centerX,
        )
        pendingDragY = y.coerceIn(
            topWindowInsetPx() + margin + radius - centerY,
            metrics.heightPixels - bottomWindowInsetPx() - margin - radius - centerY,
        )
        scheduleDragFrame()
    }

    private fun scheduleDragFrame() {
        if (dragFramePosted) return
        dragFramePosted = true
        webView?.postOnAnimation {
            dragFramePosted = false
            applyPendingDrag()
            if (phase == AgentOWindowPhase.Collapsed) {
                val velocity = pendingOrbVelocity
                pendingOrbVelocity = 0f
                evaluateOrbScript(
                    "window.GuiPlusFloatingChat&&window.GuiPlusFloatingChat.nativeOrbMove&&" +
                        "window.GuiPlusFloatingChat.nativeOrbMove($velocity);"
                )
            }
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

    private fun calculateFixedFrame(): AgentOFixedFrame {
        val metrics = service.resources.displayMetrics
        val topInset = topWindowInsetPx()
        val bottomInset = bottomWindowInsetPx()
        val margin = dp(EXPANDED_SCREEN_MARGIN_DP)
        val availableWidth = (metrics.widthPixels - margin * 2).coerceAtLeast(dp(1f))
        val availableHeight =
            (metrics.heightPixels - topInset - bottomInset - margin * 2).coerceAtLeast(dp(1f))
        val logicalWidth = dp(FIXED_LOGICAL_WIDTH_DP)
        val logicalHeight = dp(FIXED_LOGICAL_HEIGHT_DP)
        val scale = minOf(
            1f,
            availableWidth / logicalWidth.toFloat(),
            availableHeight / logicalHeight.toFloat(),
        )
        val width = (logicalWidth * scale).roundToInt().coerceAtLeast(dp(250f))
        val height = (logicalHeight * scale).roundToInt().coerceAtLeast(dp(198f))
        return AgentOFixedFrame(
            width = width,
            height = height,
            scale = scale,
            safeX = margin + ((availableWidth - width) / 2).coerceAtLeast(0),
            safeY = topInset + margin + ((availableHeight - height) / 2).coerceAtLeast(0),
        )
    }

    private fun orbTouchSizePx(frame: AgentOFixedFrame): Int =
        (dp(ORB_TOUCH_LOGICAL_DP) * frame.scale).roundToInt().coerceAtLeast(dp(56f))

    private fun orbVisualRadiusPx(frame: AgentOFixedFrame): Int =
        (dp(ORB_VISUAL_DIAMETER_DP) * frame.scale * 0.5f).roundToInt()

    private fun orbCenterYPx(frame: AgentOFixedFrame): Int =
        (dp(ORB_CENTER_Y_LOGICAL_DP) * frame.scale).roundToInt()

    private fun expandedPanelBoundsPx(frame: AgentOFixedFrame): AgentOVisibleBounds {
        val panelWidth = (EXPANDED_PANEL_WIDTH_LOGICAL_DP * density * frame.scale).roundToInt()
        val panelHeight = (EXPANDED_PANEL_HEIGHT_LOGICAL_DP * density * frame.scale).roundToInt()
        val left = ((frame.width - panelWidth) / 2).coerceAtLeast(0)
        val top = (EXPANDED_PANEL_TOP_LOGICAL_DP * density * frame.scale).roundToInt()
            .coerceAtLeast(0)
        return AgentOVisibleBounds(
            left = left,
            top = top,
            right = left + panelWidth,
            bottom = top + panelHeight,
        )
    }

    private fun clampExpandedWindowPosition(
        x: Int,
        y: Int,
        frame: AgentOFixedFrame,
    ): Pair<Int, Int> {
        val metrics = service.resources.displayMetrics
        val margin = dp(EXPANDED_SCREEN_MARGIN_DP)
        val panel = expandedPanelBoundsPx(frame)
        val minX = margin - panel.left
        val maxX = metrics.widthPixels - margin - panel.right
        val minY = topWindowInsetPx() + margin - panel.top
        val maxY = metrics.heightPixels - bottomWindowInsetPx() - margin - panel.bottom
        val clampedX = if (minX <= maxX) x.coerceIn(minX, maxX) else (minX + maxX) / 2
        val clampedY = if (minY <= maxY) y.coerceIn(minY, maxY) else (minY + maxY) / 2
        return clampedX to clampedY
    }

    private fun updateOrbTouchCoordinates(
        touchParams: WindowManager.LayoutParams,
        mainParams: WindowManager.LayoutParams,
        frame: AgentOFixedFrame,
    ) {
        touchParams.x = mainParams.x + frame.width / 2 - touchParams.width / 2
        touchParams.y = mainParams.y + orbCenterYPx(frame) - touchParams.height / 2
    }

    private fun updateOrbTouchLayout() {
        val touch = orbTouchView ?: return
        val touchParams = orbTouchParams ?: return
        val mainParams = layoutParams ?: return
        val frame = fixedFrame ?: return
        updateOrbTouchCoordinates(touchParams, mainParams, frame)
        runCatching { windowManager?.updateViewLayout(touch, touchParams) }
    }

    private fun applyCaptureVisibility(hidden: Boolean) {
        if (hiddenForCapture == hidden &&
            webView?.visibility == if (hidden) View.INVISIBLE else View.VISIBLE
        ) return
        hiddenForCapture = hidden
        val view = webView ?: return
        val params = layoutParams ?: return
        params.alpha = if (hidden) 0f else 1f
        view.alpha = params.alpha
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        applyMainWindowState(forceLayout = true)
        applyOrbTouchState()
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
        if (hiddenForCapture || phase != AgentOWindowPhase.Expanded) return
        if (!wantsInputFocus) {
            wantsInputFocus = true
            applyMainWindowState()
        }
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
        if (!wantsInputFocus) return
        wantsInputFocus = false
        applyMainWindowState()
    }

    private fun applyMainWindowState(forceLayout: Boolean = false) {
        val view = webView ?: return
        val params = layoutParams ?: return
        val nextFlags = mainWindowFlags()
        if (!forceLayout && params.flags == nextFlags) return
        params.flags = nextFlags
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun applyOrbTouchState() {
        val touch = orbTouchView ?: return
        val params = orbTouchParams ?: return
        val active = phase == AgentOWindowPhase.Collapsed && !hiddenForCapture
        if (lastOrbTouchActive == active) return
        lastOrbTouchActive = active
        params.flags = orbTouchWindowFlags()
        params.alpha = if (active) 1f else 0f
        touch.visibility = if (active) View.VISIBLE else View.INVISIBLE
        touch.alpha = params.alpha
        runCatching { windowManager?.updateViewLayout(touch, params) }
    }

    private fun mainWindowFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val touchable = phase == AgentOWindowPhase.Expanded && !hiddenForCapture
        val focusable = touchable && wantsInputFocus
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return flags
    }

    private fun orbTouchWindowFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (phase != AgentOWindowPhase.Collapsed || hiddenForCapture) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
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
        fun beginPanelDrag() {
            mainHandler.post(::beginExpandedPanelDrag)
        }

        @JavascriptInterface
        fun movePanelDrag(dx: Double, dy: Double) {
            mainHandler.post { moveExpandedPanelDrag(dx, dy) }
        }

        @JavascriptInterface
        fun endPanelDrag() {
            mainHandler.post(::endExpandedPanelDrag)
        }

        @JavascriptInterface
        fun usesNativeWindowDrag(): Boolean = true
    }

    companion object {
        private const val ASSET_URL =
            "file:///android_asset/agent_o_floating_chat_runtime.html"
        private const val NATIVE_BRIDGE_NAME = "GuiPlusNative"
        private const val BRIDGE_SOURCE = "gui-plus-floating-chat"

        private const val FIXED_LOGICAL_WIDTH_DP = 620f
        private const val FIXED_LOGICAL_HEIGHT_DP = 490f
        private const val EXPANDED_SCREEN_MARGIN_DP = 8f
        private const val COLLAPSED_SCREEN_MARGIN_DP = 8f

        // V8.4 固定舞台内的真实可见面板为 500×360，顶部位于 71；边界按面板而非透明舞台计算。
        private const val EXPANDED_PANEL_WIDTH_LOGICAL_DP = 500f
        private const val EXPANDED_PANEL_HEIGHT_LOGICAL_DP = 360f
        private const val EXPANDED_PANEL_TOP_LOGICAL_DP = 71f

        // V8.4 原舞台坐标：固定舞台顶部偏移 30，珠态中心位于 720 高舞台的中线。
        private const val ORB_CENTER_Y_LOGICAL_DP = 390f
        private const val ORB_VISUAL_DIAMETER_DP = 116f
        private const val ORB_TOUCH_LOGICAL_DP = 146f
    }
}