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
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

private enum class VisualHudRegion(val queryValue: String) {
    EdgeTop("edge-top"),
    EdgeBottom("edge-bottom"),
    EdgeLeft("edge-left"),
    EdgeRight("edge-right"),
    Pointer("pointer"),
}

private data class VisualHudCssRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

private data class VisualHudWindowGeometry(
    val xPx: Int,
    val yPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val xCss: Float,
    val yCss: Float,
    val widthCss: Float,
    val heightCss: Float,
)

private data class VisualHudBubblePlacement(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private data class VisualHudSceneGeometry(
    val screenWidthCss: Float,
    val screenHeightCss: Float,
    val pointXCss: Float,
    val pointYCss: Float,
    val bubble: VisualHudBubblePlacement,
    val windows: Map<VisualHudRegion, VisualHudWindowGeometry>,
)

private data class VisualHudWindowHost(
    val region: VisualHudRegion,
    val view: WebView,
    val params: WindowManager.LayoutParams,
    var geometry: VisualHudWindowGeometry,
    var pageReady: Boolean = false,
    var pendingPayload: String? = null,
)

/**
 * Single HUD architecture backed by the approved HTML/CSS/SVG assets.
 *
 * HarmonyOS can treat a full-screen accessibility overlay as one full-screen input-obscuring
 * surface even when FLAG_NOT_TOUCHABLE is present. To keep the real app usable, the virtual HUD
 * canvas is clipped into four narrow edge windows plus one local pointer/info window. No Android
 * Canvas or Compose duplicate is introduced; every window loads the same web implementation and
 * only exposes its assigned region.
 */
internal class VisualAgentHudHost(
    private val service: AccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tuningStore = VisualAgentHudTuningStore.get(service.applicationContext)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val windows = linkedMapOf<VisualHudRegion, VisualHudWindowHost>()

    private var lastClickRevision = 0L
    private var lastPreviewGeneration = 0L
    private var overlayContentActive = false
    private var captureSuppressed = false
    private var presentationRevision = 0L
    private var overlayCreationFailed = false
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
        if (!started && windows.isEmpty()) return
        started = false
        tuningStore.setPreviewEnabled(false)
        scope.cancel()
        destroyAllWindows()
    }

    private fun updateOverlay(snapshot: VisualHudRenderSnapshot) {
        val progress = snapshot.progress
        val tuning = snapshot.tuning
        val matchingTarget = snapshot.target?.takeIf { it.taskId == progress.taskId }
        val realHudActive = shouldPresentRuntime(progress)
        val sampleMode = tuning.previewEnabled && !realHudActive
        val contentActive = realHudActive || sampleMode

        if (!contentActive) {
            if (windows.isNotEmpty() || overlayCreationFailed) destroyAllWindows()
            return
        }

        val scene = computeSceneGeometry(
            progress = progress,
            target = matchingTarget,
            tuning = tuning,
            sampleMode = sampleMode,
        )
        if (!ensureWindows(scene)) return
        updateWindowGeometries(scene)
        setOverlayPresentation(hiddenForCapture = snapshot.hiddenForCapture)

        val visibleToUser = !snapshot.hiddenForCapture
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

        val basePayload = buildPayload(
            progress = progress,
            target = matchingTarget,
            tuning = tuning,
            scene = scene,
            visible = true,
            phase = phase,
            sampleMode = sampleMode,
            clickRevision = if (resultPulse) progress.updatedAt else 0L,
            autoClickAfterMs = if (sampleGenerationChanged) SAMPLE_CLICK_DELAY_MS else 0L,
        )
        dispatchScenePayload(basePayload, scene)
    }

    private fun computeSceneGeometry(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        tuning: VisualAgentHudTuningState,
        sampleMode: Boolean,
    ): VisualHudSceneGeometry {
        val metrics = service.resources.displayMetrics
        val density = metrics.density.coerceAtLeast(1f)
        val screenWidthPx = metrics.widthPixels.coerceAtLeast(1)
        val screenHeightPx = metrics.heightPixels.coerceAtLeast(1)
        val screenWidthCss = screenWidthPx / density
        val screenHeightCss = screenHeightPx / density

        val xNorm = if (sampleMode) {
            SAMPLE_CURSOR_X
        } else {
            when {
                target == null -> DEFAULT_CURSOR_X
                target.normalized -> target.x
                else -> target.x / screenWidthPx.toFloat()
            }
        }.coerceIn(0f, 1f)
        val yNorm = if (sampleMode) {
            SAMPLE_CURSOR_Y
        } else {
            when {
                target == null -> DEFAULT_CURSOR_Y
                target.normalized -> target.y
                else -> target.y / screenHeightPx.toFloat()
            }
        }.coerceIn(0f, 1f)
        val pointX = xNorm * screenWidthCss
        val pointY = yNorm * screenHeightCss
        val parameters = tuning.parameters
        val bubble = computeBubblePlacement(pointX, pointY, screenWidthCss, screenHeightCss, parameters)

        val edgeEnvelope = (
            parameters.edgeInset +
                max(
                    parameters.edgeHaloWidth + parameters.edgeHaloBlur * 2f,
                    parameters.edgeCastDepth + parameters.edgeCastBlur * 2f,
                ) + EDGE_CLIP_PADDING_CSS
            ).coerceAtLeast(MIN_EDGE_WINDOW_CSS)

        var topHeight = max(edgeEnvelope, TOP_WINDOW_MIN_CSS)
            .coerceAtMost(screenHeightCss * MAX_VERTICAL_EDGE_FRACTION)
        var bottomHeight = max(edgeEnvelope, BOTTOM_WINDOW_MIN_CSS)
            .coerceAtMost(screenHeightCss * MAX_VERTICAL_EDGE_FRACTION)
        val maxVerticalTotal = screenHeightCss * MAX_TOTAL_VERTICAL_EDGE_FRACTION
        if (topHeight + bottomHeight > maxVerticalTotal) {
            val scale = maxVerticalTotal / (topHeight + bottomHeight)
            topHeight *= scale
            bottomHeight *= scale
        }
        val sideWidth = edgeEnvelope.coerceAtMost(screenWidthCss * MAX_HORIZONTAL_EDGE_FRACTION)

        val cursorRadius = max(
            MIN_POINTER_RADIUS_CSS,
            max(
                parameters.size * max(abs(parameters.scaleX), abs(parameters.scaleY)) * 0.82f +
                    parameters.glowBlur * 3f,
                parameters.auraSize * 0.5f + parameters.auraBlur * 2f + 12f,
            )
        )
        val pointerRect = unionRect(
            VisualHudCssRect(
                left = pointX - cursorRadius,
                top = pointY - cursorRadius,
                right = pointX + cursorRadius,
                bottom = pointY + cursorRadius,
            ),
            VisualHudCssRect(
                left = bubble.x,
                top = bubble.y,
                right = bubble.x + bubble.width,
                bottom = bubble.y + bubble.height,
            ),
        ).expand(POINTER_WINDOW_PADDING_CSS)
            .clampTo(screenWidthCss, screenHeightCss)

        val cssRects = linkedMapOf(
            VisualHudRegion.EdgeTop to VisualHudCssRect(0f, 0f, screenWidthCss, topHeight),
            VisualHudRegion.EdgeBottom to VisualHudCssRect(
                0f,
                (screenHeightCss - bottomHeight).coerceAtLeast(0f),
                screenWidthCss,
                screenHeightCss,
            ),
            VisualHudRegion.EdgeLeft to VisualHudCssRect(
                0f,
                topHeight,
                sideWidth,
                (screenHeightCss - bottomHeight).coerceAtLeast(topHeight + 1f),
            ),
            VisualHudRegion.EdgeRight to VisualHudCssRect(
                (screenWidthCss - sideWidth).coerceAtLeast(0f),
                topHeight,
                screenWidthCss,
                (screenHeightCss - bottomHeight).coerceAtLeast(topHeight + 1f),
            ),
            VisualHudRegion.Pointer to pointerRect,
        )
        val geometries = cssRects.mapValues { (_, rect) ->
            rect.toWindowGeometry(density, screenWidthPx, screenHeightPx)
        }
        return VisualHudSceneGeometry(
            screenWidthCss = screenWidthCss,
            screenHeightCss = screenHeightCss,
            pointXCss = pointX,
            pointYCss = pointY,
            bubble = bubble,
            windows = geometries,
        )
    }

    private fun computeBubblePlacement(
        pointX: Float,
        pointY: Float,
        screenWidth: Float,
        screenHeight: Float,
        parameters: VisualAgentHudParameters,
    ): VisualHudBubblePlacement {
        val bubbleScale = parameters.infoBubbleScale.coerceAtLeast(0.1f)
        val visualWidth = min(parameters.infoBubbleWidth, (screenWidth - 24f).coerceAtLeast(80f)) * bubbleScale
        val visualHeight = (BUBBLE_ESTIMATED_HEIGHT_CSS * bubbleScale)
            .coerceIn(BUBBLE_MIN_VISUAL_HEIGHT_CSS, min(BUBBLE_MAX_VISUAL_HEIGHT_CSS, screenHeight - 24f))
        val cursorRadius = max(
            18f,
            max(
                parameters.size * max(abs(parameters.scaleX), abs(parameters.scaleY)) * 0.62f,
                parameters.auraSize * 0.18f,
            )
        )
        val gap = max(10f, parameters.size * 0.22f)
        val candidates = listOf(
            Triple(pointX + cursorRadius + gap, pointY + gap, 0),
            Triple(pointX - cursorRadius - gap - visualWidth, pointY + gap, 1),
            Triple(pointX + cursorRadius + gap, pointY - gap - visualHeight, 2),
            Triple(pointX - cursorRadius - gap - visualWidth, pointY - gap - visualHeight, 3),
            Triple(pointX - visualWidth * 0.5f, pointY + cursorRadius + gap, 4),
            Triple(pointX - visualWidth * 0.5f, pointY - cursorRadius - gap - visualHeight, 5),
        )
        val safeLeft = 12f
        val safeTop = 12f
        val maxX = max(safeLeft, screenWidth - 12f - visualWidth)
        val maxY = max(safeTop, screenHeight - 12f - visualHeight)
        var bestX = safeLeft
        var bestY = safeTop
        var bestScore = Float.MAX_VALUE
        candidates.forEach { candidate ->
            val candidateX = candidate.first
            val candidateY = candidate.second
            val priority = candidate.third
            val overflow =
                max(0f, safeLeft - candidateX) +
                    max(0f, candidateX + visualWidth - (screenWidth - 12f)) +
                    max(0f, safeTop - candidateY) +
                    max(0f, candidateY + visualHeight - (screenHeight - 12f))
            val x = candidateX.coerceIn(safeLeft, maxX)
            val y = candidateY.coerceIn(safeTop, maxY)
            val bubbleRect = VisualHudCssRect(x, y, x + visualWidth, y + visualHeight)
            val cursorRect = VisualHudCssRect(
                pointX - cursorRadius,
                pointY - cursorRadius,
                pointX + cursorRadius,
                pointY + cursorRadius,
            )
            val overlap = overlapArea(bubbleRect, cursorRect)
            val distance = kotlin.math.hypot(
                (x + visualWidth * 0.5f - pointX).toDouble(),
                (y + visualHeight * 0.5f - pointY).toDouble(),
            ).toFloat()
            val score = overflow * 900f + overlap * 120f + distance * 0.035f + priority * 2f
            if (score < bestScore) {
                bestScore = score
                bestX = x
                bestY = y
            }
        }
        return VisualHudBubblePlacement(bestX, bestY, visualWidth, visualHeight)
    }

    private fun overlapArea(a: VisualHudCssRect, b: VisualHudCssRect): Float {
        val width = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val height = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        return width * height
    }

    private fun unionRect(a: VisualHudCssRect, b: VisualHudCssRect): VisualHudCssRect =
        VisualHudCssRect(
            left = min(a.left, b.left),
            top = min(a.top, b.top),
            right = max(a.right, b.right),
            bottom = max(a.bottom, b.bottom),
        )

    private fun VisualHudCssRect.expand(amount: Float): VisualHudCssRect =
        VisualHudCssRect(left - amount, top - amount, right + amount, bottom + amount)

    private fun VisualHudCssRect.clampTo(screenWidth: Float, screenHeight: Float): VisualHudCssRect {
        val clampedLeft = left.coerceIn(0f, screenWidth)
        val clampedTop = top.coerceIn(0f, screenHeight)
        val clampedRight = right.coerceIn(clampedLeft + 1f, screenWidth.coerceAtLeast(clampedLeft + 1f))
        val clampedBottom = bottom.coerceIn(clampedTop + 1f, screenHeight.coerceAtLeast(clampedTop + 1f))
        return VisualHudCssRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }

    private fun VisualHudCssRect.toWindowGeometry(
        density: Float,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): VisualHudWindowGeometry {
        val xPx = floor(left * density).toInt().coerceIn(0, (screenWidthPx - 1).coerceAtLeast(0))
        val yPx = floor(top * density).toInt().coerceIn(0, (screenHeightPx - 1).coerceAtLeast(0))
        val rightPx = ceil(right * density).toInt().coerceIn(xPx + 1, screenWidthPx)
        val bottomPx = ceil(bottom * density).toInt().coerceIn(yPx + 1, screenHeightPx)
        return VisualHudWindowGeometry(
            xPx = xPx,
            yPx = yPx,
            widthPx = (rightPx - xPx).coerceAtLeast(1),
            heightPx = (bottomPx - yPx).coerceAtLeast(1),
            xCss = xPx / density,
            yCss = yPx / density,
            widthCss = (rightPx - xPx) / density,
            heightCss = (bottomPx - yPx) / density,
        )
    }

    private fun ensureWindows(scene: VisualHudSceneGeometry): Boolean {
        if (overlayCreationFailed) return false
        val wm = windowManager ?: return false
        for (region in VisualHudRegion.entries) {
            if (windows.containsKey(region)) continue
            val geometry = scene.windows.getValue(region)
            val host = createWindow(wm, region, geometry) ?: run {
                overlayCreationFailed = true
                destroyAllWindows(resetFailure = false)
                val message = "系统未能创建视觉 HUD 局部无障碍浮层，请重新开启无障碍服务后重试。"
                Toast.makeText(service, message, Toast.LENGTH_LONG).show()
                AgentRuntimeController.noteDiagnostic(message)
                return false
            }
            windows[region] = host
            host.view.loadUrl("$ASSET_URL?region=${region.queryValue}")
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWindow(
        wm: WindowManager,
        region: VisualHudRegion,
        geometry: VisualHudWindowGeometry,
    ): VisualHudWindowHost? {
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
                    val host = windows[region] ?: return
                    host.pageReady = true
                    host.pendingPayload?.let { payload ->
                        host.pendingPayload = null
                        dispatchPayload(host, payload)
                    }
                }
            }
            alpha = 0f
            visibility = View.INVISIBLE
        }
        val params = WindowManager.LayoutParams(
            geometry.widthPx,
            geometry.heightPx,
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
            x = geometry.xPx
            y = geometry.yPx
            alpha = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        return runCatching {
            wm.addView(view, params)
            VisualHudWindowHost(region, view, params, geometry)
        }.getOrElse {
            view.stopLoading()
            view.destroy()
            null
        }
    }

    private fun updateWindowGeometries(scene: VisualHudSceneGeometry) {
        val wm = windowManager ?: return
        windows.forEach { (region, host) ->
            val geometry = scene.windows.getValue(region)
            if (host.geometry == geometry) return@forEach
            host.geometry = geometry
            host.params.x = geometry.xPx
            host.params.y = geometry.yPx
            host.params.width = geometry.widthPx
            host.params.height = geometry.heightPx
            runCatching { wm.updateViewLayout(host.view, host.params) }
        }
    }

    private fun destroyAllWindows(resetFailure: Boolean = true) {
        presentationRevision += 1L
        overlayContentActive = false
        captureSuppressed = false
        if (resetFailure) overlayCreationFailed = false
        val wm = windowManager
        windows.values.forEach { host ->
            host.view.animate().cancel()
            runCatching { wm?.removeView(host.view) }
            host.view.stopLoading()
            host.view.loadUrl("about:blank")
            host.view.clearHistory()
            host.view.removeAllViews()
            host.view.destroy()
        }
        windows.clear()
    }

    private fun setOverlayPresentation(hiddenForCapture: Boolean) {
        if (windows.isEmpty()) return
        val nextSuppressed = hiddenForCapture
        val activeChanged = !overlayContentActive
        val suppressionChanged = captureSuppressed != nextSuppressed
        if (!activeChanged && !suppressionChanged) return

        overlayContentActive = true
        captureSuppressed = nextSuppressed
        presentationRevision += 1L
        val revision = presentationRevision
        windows.values.forEach { host ->
            val view = host.view
            view.animate().cancel()
            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                view.onResume()
            }
            if (nextSuppressed) {
                updateWindowAlpha(host, 1f)
                if (view.alpha <= 0.001f) {
                    view.alpha = 0f
                    updateWindowAlpha(host, 0f)
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
                                updateWindowAlpha(host, 0f)
                            }
                        }
                        .start()
                }
            } else {
                updateWindowAlpha(host, 1f)
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
    }

    private fun updateWindowAlpha(host: VisualHudWindowHost, alpha: Float) {
        if (host.params.alpha == alpha) return
        host.params.alpha = alpha
        runCatching { windowManager?.updateViewLayout(host.view, host.params) }
    }

    private fun dispatchScenePayload(basePayload: JSONObject, scene: VisualHudSceneGeometry) {
        windows.values.forEach { host ->
            val geometry = scene.windows.getValue(host.region)
            val payload = JSONObject(basePayload.toString())
                .put("region", host.region.queryValue)
                .put("screenWidth", scene.screenWidthCss.toDouble())
                .put("screenHeight", scene.screenHeightCss.toDouble())
                .put("viewportX", geometry.xCss.toDouble())
                .put("viewportY", geometry.yCss.toDouble())
                .put("viewportWidth", geometry.widthCss.toDouble())
                .put("viewportHeight", geometry.heightCss.toDouble())
                .toString()
            sendPayload(host, payload)
        }
    }

    private fun sendPayload(host: VisualHudWindowHost, payload: String) {
        if (!host.pageReady) {
            host.pendingPayload = payload
            return
        }
        dispatchPayload(host, payload)
    }

    private fun dispatchPayload(host: VisualHudWindowHost, payload: String) {
        host.view.evaluateJavascript("window.VisualHud&&window.VisualHud.update($payload);", null)
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
        scene: VisualHudSceneGeometry,
        visible: Boolean,
        phase: Int,
        sampleMode: Boolean,
        clickRevision: Long,
        autoClickAfterMs: Long,
    ): JSONObject {
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
            .put("xNorm", (scene.pointXCss / scene.screenWidthCss).toDouble())
            .put("yNorm", (scene.pointYCss / scene.screenHeightCss).toDouble())
            .put("bubbleX", scene.bubble.x.toDouble())
            .put("bubbleY", scene.bubble.y.toDouble())
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
    }

    companion object {
        private const val ASSET_URL = "file:///android_asset/visual_agent_hud_runtime.html"
        private const val TARGET_MOVE_VISIBLE_MS = 1_200L
        private const val SAMPLE_CLICK_DELAY_MS = 820L
        private const val HUD_CAPTURE_FADE_OUT_MS = 84L
        private const val HUD_RESTORE_FADE_MS = 128L
        private val HUD_CAPTURE_HIDE_INTERPOLATOR = AccelerateInterpolator(1.25f)
        private val HUD_RESTORE_INTERPOLATOR = DecelerateInterpolator(1.55f)

        private const val EDGE_CLIP_PADDING_CSS = 12f
        private const val MIN_EDGE_WINDOW_CSS = 18f
        private const val TOP_WINDOW_MIN_CSS = 92f
        private const val BOTTOM_WINDOW_MIN_CSS = 112f
        private const val MAX_VERTICAL_EDGE_FRACTION = 0.38f
        private const val MAX_TOTAL_VERTICAL_EDGE_FRACTION = 0.72f
        private const val MAX_HORIZONTAL_EDGE_FRACTION = 0.42f
        private const val MIN_POINTER_RADIUS_CSS = 54f
        private const val POINTER_WINDOW_PADDING_CSS = 14f
        private const val BUBBLE_ESTIMATED_HEIGHT_CSS = 178f
        private const val BUBBLE_MIN_VISUAL_HEIGHT_CSS = 88f
        private const val BUBBLE_MAX_VISUAL_HEIGHT_CSS = 290f

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
