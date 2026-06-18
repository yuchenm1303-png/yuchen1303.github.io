package com.yuchen.ailedger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.yuchen.ailedger.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class AgentOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var titleView: TextView? = null
    private var stateView: TextView? = null
    private var actionView: TextView? = null
    private var resultView: TextView? = null
    private var latestView: TextView? = null
    private var logsView: TextView? = null
    private var logsCard: LinearLayout? = null

    private var confirmPanel: LinearLayout? = null
    private var confirmTitleView: TextView? = null
    private var confirmMessageView: TextView? = null
    private var confirmPrimaryView: TextView? = null
    private var confirmSecondaryView: TextView? = null

    private var inputPanel: LinearLayout? = null
    private var inputTitleView: TextView? = null
    private var inputMessageView: TextView? = null
    private var inputEditText: EditText? = null
    private var inputPrimaryView: TextView? = null
    private var inputSecondaryView: TextView? = null

    private var stopView: TextView? = null
    private var collapseView: TextView? = null
    private var takeoverView: TextView? = null
    private var resumeView: TextView? = null
    private var contentGroup: LinearLayout? = null

    private var density: Float = 1f
    private var expanded: Boolean = true
    private var latestProgress: AgentOverlayProgress = AgentOverlayProgress()

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays(this)) {
            stopSelf()
            return
        }
        density = resources.displayMetrics.density.coerceAtLeast(1f)
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        createOverlay()
        scope.launch {
            AgentRuntimeController.progress.collectLatest { updateProgress(it) }
        }
        scope.launch {
            AgentRuntimeController.overlayHiddenForCapture.collectLatest { setHiddenForCleanCapture(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (rootView == null) createOverlay()
        setHiddenForCleanCapture(AgentRuntimeController.overlayHiddenForCapture.value)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        rootView?.animate()?.cancel()
        rootView?.let { runCatching { windowManager?.removeView(it) } }
        rootView = null
        layoutParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        if (rootView != null) return
        val wm = windowManager ?: return
        val hidden = AgentRuntimeController.overlayHiddenForCapture.value
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            alpha = if (hidden) 0f else 1f
            setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
            background = panelBackground()
            elevation = dp(18f).toFloat()
            isClickable = true
            setOnTouchListener(DragTouchListener())
        }

        panel.addView(createHeaderRow())

        actionView = text("等待任务", 13.4f, Color.WHITE, bold = true).applyReadable(maxLines = 3, lineSpacingExtraDp = 1.4f)
        resultView = text("结果：暂无执行结果", 10.25f, Color.argb(232, 236, 246, 255)).applyReadable(maxLines = 4, lineSpacingExtraDp = 1.1f)
        latestView = text("最近：暂无运行日志", 9.7f, Color.argb(188, 222, 235, 255)).applyReadable(maxLines = 2, lineSpacingExtraDp = 0.7f)
        logsView = text("暂无详细日志", 9.45f, Color.argb(188, 222, 235, 255)).applyReadable(maxLines = 10, lineSpacingExtraDp = 0.8f)

        confirmPanel = createConfirmPanel()
        inputPanel = createInputPanel()

        stopView = capsuleButton("停止", ButtonTone.Danger) { AgentRuntimeController.stopTaskByUser() }
        collapseView = capsuleButton("收起", ButtonTone.Ghost) {
            if (latestProgress.pendingConfirmation == null && latestProgress.pendingUserInput == null) {
                expanded = !expanded
                refreshExpandedState()
            }
        }
        takeoverView = capsuleButton("接管", ButtonTone.GhostWarm) {
            AgentRuntimeController.pauseForUserTakeover()
        }
        resumeView = capsuleButton("恢复", ButtonTone.PrimaryWarm) {
            AgentRuntimeController.resumeFromUserTakeover()
        }

        val actionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = cardBackground()
            addView(sectionLabel("当前动作"))
            addView(actionView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            })
            addView(resultView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(7f)
            })
            addView(latestView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            })
        }

        logsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(9f), dp(12f), dp(10f))
            background = logCardBackground()
            addView(sectionLabel("运行记录"))
            addView(logsView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            })
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(collapseView, LinearLayout.LayoutParams(0, dp(35f), 1f).apply { marginEnd = dp(6f) })
            addView(takeoverView, LinearLayout.LayoutParams(0, dp(35f), 1f).apply { marginEnd = dp(6f) })
            addView(resumeView, LinearLayout.LayoutParams(0, dp(35f), 1f).apply { marginEnd = dp(6f) })
            addView(stopView, LinearLayout.LayoutParams(0, dp(35f), 1f))
        }

        contentGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10f), 0, 0)
            addView(actionCard)
            addView(confirmPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(9f)
            })
            addView(inputPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(9f)
            })
            addView(logsCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            })
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(9f)
            })
        }

        panel.addView(contentGroup)

        val params = WindowManager.LayoutParams(
            dp(EXPANDED_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            overlayWindowFlags(touchThrough = hidden, wantsInputFocus = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12f)
            y = dp(82f)
        }

        rootView = panel
        layoutParams = params
        runCatching { wm.addView(panel, params) }
            .onSuccess {
                updateProgress(AgentRuntimeController.progress.value)
                if (!hidden) animateEntrance(panel)
            }
            .onFailure { stopSelf() }
    }

    private fun createHeaderRow(): LinearLayout {
        titleView = text("AI 智能体", 13.4f, Color.WHITE, bold = true).apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        stateView = text("待命", 9.5f, Color.argb(232, 232, 246, 255), bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(9f), 0, dp(9f), dp(1f))
            minWidth = dp(48f)
            background = chipBackground(Color.argb(42, 214, 228, 255), Color.argb(48, 235, 248, 255), 14f)
        }
        val openView = iconChip("↗") { openMainApp() }
        val closeView = iconChip("×") { stopSelf() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stateView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(26f)).apply { marginStart = dp(7f) })
            addView(openView, LinearLayout.LayoutParams(dp(32f), dp(28f)).apply { marginStart = dp(6f) })
            addView(closeView, LinearLayout.LayoutParams(dp(32f), dp(28f)).apply { marginStart = dp(5f) })
        }
    }

    private fun createConfirmPanel(): LinearLayout {
        confirmTitleView = text("需要确认", 12.4f, Color.argb(255, 255, 235, 190), bold = true).applyReadable(maxLines = 1)
        confirmMessageView = text("", 10.4f, Color.argb(232, 255, 244, 222)).applyReadable(maxLines = 5, lineSpacingExtraDp = 1f)
        confirmSecondaryView = capsuleButton("取消任务", ButtonTone.GhostWarm) {
            AgentRuntimeController.choosePendingAction(false)
        }
        confirmPrimaryView = capsuleButton("继续执行", ButtonTone.PrimaryWarm) {
            AgentRuntimeController.choosePendingAction(true)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(confirmSecondaryView, LinearLayout.LayoutParams(0, dp(37f), 1f).apply { marginEnd = dp(8f) })
            addView(confirmPrimaryView, LinearLayout.LayoutParams(0, dp(37f), 1f))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12f), dp(11f), dp(12f), dp(12f))
            background = chipBackground(Color.argb(92, 116, 70, 38), Color.argb(112, 255, 214, 132), 22f)
            addView(confirmTitleView)
            addView(confirmMessageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(7f)
                bottomMargin = dp(10f)
            })
            addView(row)
        }
    }

    private fun createInputPanel(): LinearLayout {
        inputTitleView = text("需要你输入", 12.4f, Color.argb(255, 224, 244, 255), bold = true).applyReadable(maxLines = 1)
        inputMessageView = text("", 10.4f, Color.argb(230, 232, 244, 255)).applyReadable(maxLines = 5, lineSpacingExtraDp = 1f)
        inputEditText = EditText(this).apply {
            textSize = 13.2f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(160, 232, 244, 255))
            hint = "请输入内容"
            setSingleLine(true)
            setPadding(dp(11f), 0, dp(11f), 0)
            background = chipBackground(Color.argb(38, 255, 255, 255), Color.argb(66, 215, 235, 255), 16f)
        }
        inputSecondaryView = capsuleButton("取消任务", ButtonTone.GhostWarm) {
            AgentRuntimeController.cancelPendingUserInput()
        }
        inputPrimaryView = capsuleButton("确认输入", ButtonTone.PrimaryWarm) {
            AgentRuntimeController.submitPendingUserInput(inputEditText?.text?.toString().orEmpty())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(inputSecondaryView, LinearLayout.LayoutParams(0, dp(37f), 1f).apply { marginEnd = dp(8f) })
            addView(inputPrimaryView, LinearLayout.LayoutParams(0, dp(37f), 1f))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12f), dp(11f), dp(12f), dp(12f))
            background = chipBackground(Color.argb(78, 30, 76, 106), Color.argb(112, 148, 232, 255), 22f)
            addView(inputTitleView)
            addView(inputMessageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(7f)
                bottomMargin = dp(8f)
            })
            addView(inputEditText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40f)).apply {
                bottomMargin = dp(10f)
            })
            addView(row)
        }
    }

    private fun updateProgress(progress: AgentOverlayProgress) {
        latestProgress = progress
        val pending = progress.pendingConfirmation
        val pendingInput = progress.pendingUserInput
        val modeText = when {
            pendingInput != null -> "待输入"
            pending != null -> "待确认"
            progress.userTakeoverPaused -> "接管中"
            !progress.enabled -> "已关闭"
            progress.running -> "执行中"
            else -> progress.status.ifBlank { "待命" }
        }
        titleView?.text = progress.title
        stateView?.text = modeText
        stateView?.background = when {
            pendingInput != null -> chipBackground(Color.argb(78, 80, 168, 230), Color.argb(132, 148, 232, 255), 14f)
            pending != null -> chipBackground(Color.argb(88, 255, 184, 90), Color.argb(132, 255, 224, 135), 14f)
            progress.userTakeoverPaused -> chipBackground(Color.argb(70, 255, 210, 104), Color.argb(110, 255, 230, 150), 14f)
            progress.running -> chipBackground(Color.argb(72, 95, 255, 218), Color.argb(86, 164, 255, 232), 14f)
            !progress.enabled -> chipBackground(Color.argb(48, 195, 202, 218), Color.argb(54, 214, 224, 242), 14f)
            else -> chipBackground(Color.argb(42, 214, 228, 255), Color.argb(48, 235, 248, 255), 14f)
        }
        val actionText = pendingInput?.actionText
            ?: pending?.actionText?.ifBlank { "高风险动作确认" }
            ?: progress.currentAction.ifBlank { "等待任务" }
        actionView?.text = actionText.cleanOverlayText().limitOverlayText(ACTION_TEXT_LIMIT)
        resultView?.text = progress.lastResult.takeIf { it.isNotBlank() }
            ?.let { "结果：${it.cleanOverlayText().limitOverlayText(RESULT_TEXT_LIMIT)}" }
            ?: "结果：暂无执行结果"
        latestView?.text = progress.logs.lastOrNull()
            ?.let { "最近：${it.cleanOverlayText().limitOverlayText(LATEST_TEXT_LIMIT)}" }
            ?: "最近：暂无运行日志"
        logsView?.text = buildLogText(progress.logs)
        stopView?.visibility = if (progress.running || pending != null || pendingInput != null) View.VISIBLE else View.GONE

        if (pending != null) {
            expanded = true
            confirmTitleView?.text = pending.title
            confirmMessageView?.text = pending.message.cleanOverlayText().limitOverlayText(PANEL_MESSAGE_TEXT_LIMIT)
            confirmPrimaryView?.text = pending.positiveText
            confirmSecondaryView?.text = pending.negativeText
        }
        if (pendingInput != null) {
            expanded = true
            inputTitleView?.text = pendingInput.title
            inputMessageView?.text = pendingInput.message.cleanOverlayText().limitOverlayText(PANEL_MESSAGE_TEXT_LIMIT)
            inputPrimaryView?.text = pendingInput.positiveText
            inputSecondaryView?.text = pendingInput.negativeText
            inputEditText?.hint = pendingInput.hint
            requestInputFocus()
        }
        refreshExpandedState()
    }

    private fun buildLogText(logs: List<String>): String {
        if (logs.isEmpty()) return "暂无详细日志"
        return logs.takeLast(OVERLAY_LOG_LINES).joinToString("\n") { raw ->
            "• ${raw.cleanOverlayText().limitOverlayText(LOG_LINE_TEXT_LIMIT)}"
        }
    }

    private fun refreshExpandedState() {
        val hidden = AgentRuntimeController.overlayHiddenForCapture.value
        val pending = latestProgress.pendingConfirmation
        val pendingInput = latestProgress.pendingUserInput
        val paused = latestProgress.userTakeoverPaused
        val forceExpanded = pending != null || pendingInput != null || paused
        val shouldExpand = expanded || forceExpanded
        contentGroup?.visibility = if (shouldExpand && !hidden) View.VISIBLE else View.GONE
        confirmPanel?.visibility = if (shouldExpand && pending != null && !hidden) View.VISIBLE else View.GONE
        inputPanel?.visibility = if (shouldExpand && pendingInput != null && !hidden) View.VISIBLE else View.GONE
        logsCard?.visibility = if (shouldExpand && latestProgress.logs.isNotEmpty() && !hidden) View.VISIBLE else View.GONE
        logsView?.visibility = if (shouldExpand && latestProgress.logs.isNotEmpty() && !hidden) View.VISIBLE else View.GONE
        collapseView?.visibility = if (pending == null && pendingInput == null && !paused) View.VISIBLE else View.GONE
        takeoverView?.visibility = if (latestProgress.running && !paused && pending == null && pendingInput == null) View.VISIBLE else View.GONE
        resumeView?.visibility = if (paused) View.VISIBLE else View.GONE
        collapseView?.text = if (shouldExpand) "收起" else "展开"
        updateWindowMode(hidden)
        updateWindowWidth(if (shouldExpand || forceExpanded) dp(EXPANDED_WIDTH_DP) else dp(COMPACT_WIDTH_DP))
    }

    private fun updateWindowWidth(targetWidth: Int) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        if (params.width == targetWidth) return
        params.width = targetWidth
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun overlayWindowFlags(touchThrough: Boolean, wantsInputFocus: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!wantsInputFocus) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (touchThrough) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun updateWindowMode(touchThrough: Boolean) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        val wantsInputFocus = latestProgress.pendingUserInput != null && !touchThrough
        val newFlags = overlayWindowFlags(touchThrough, wantsInputFocus)
        if (params.flags == newFlags) return
        params.flags = newFlags
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun setHiddenForCleanCapture(hidden: Boolean) {
        val view = rootView ?: return
        view.animate().cancel()
        updateWindowMode(hidden)
        if (hidden) {
            view.alpha = 0f
            view.visibility = View.INVISIBLE
        } else {
            view.visibility = View.VISIBLE
            if (view.alpha < 1f) {
                view.scaleX = 0.99f
                view.scaleY = 0.99f
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(145L)
                    .setInterpolator(SOFT_OUT)
                    .start()
            }
            refreshExpandedState()
        }
    }

    private fun requestInputFocus() {
        val edit = inputEditText ?: return
        edit.post {
            edit.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun animateEntrance(view: View) {
        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.translationY = -dp(8f).toFloat()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(SOFT_OUT)
            .start()
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun sectionLabel(value: String): TextView {
        return text(value, 8.2f, Color.argb(205, 152, 231, 234), bold = true).apply {
            includeFontPadding = false
            letterSpacing = 0.16f
            maxLines = 1
        }
    }

    private fun TextView.applyReadable(maxLines: Int, lineSpacingExtraDp: Float = 0.8f): TextView {
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(dp(lineSpacingExtraDp).toFloat(), 1.02f)
        return this
    }

    private fun iconChip(value: String, onClick: () -> Unit): TextView {
        return text(value, 16.2f, Color.argb(228, 245, 250, 255), bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = chipBackground(Color.argb(28, 255, 255, 255), Color.argb(36, 230, 240, 255), 15f)
            setOnClickListener { onClick() }
        }
    }

    private fun capsuleButton(label: String, tone: ButtonTone, onClick: () -> Unit): TextView {
        return text(label, 10.8f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8f), 0, dp(8f), dp(1f))
            background = buttonBackground(tone)
            setOnClickListener { onClick() }
        }
    }

    private fun buttonBackground(tone: ButtonTone): GradientDrawable {
        val colors = when (tone) {
            ButtonTone.PrimaryWarm -> intArrayOf(Color.argb(178, 186, 118, 46), Color.argb(155, 255, 214, 98))
            ButtonTone.GhostWarm -> intArrayOf(Color.argb(52, 255, 238, 218), Color.argb(32, 255, 201, 142))
            ButtonTone.Danger -> intArrayOf(Color.argb(126, 172, 48, 98), Color.argb(110, 88, 42, 106))
            ButtonTone.Ghost -> intArrayOf(Color.argb(40, 255, 255, 255), Color.argb(24, 160, 204, 255))
        }
        val stroke = when (tone) {
            ButtonTone.PrimaryWarm -> Color.argb(132, 255, 224, 135)
            ButtonTone.GhostWarm -> Color.argb(72, 255, 224, 170)
            ButtonTone.Danger -> Color.argb(86, 255, 178, 214)
            ButtonTone.Ghost -> Color.argb(54, 230, 240, 255)
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(18f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun panelBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(226, 14, 24, 52), Color.argb(206, 32, 50, 92), Color.argb(224, 14, 18, 48))
        ).apply {
            cornerRadius = dp(26f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1), Color.argb(112, 195, 230, 255))
        }
    }

    private fun cardBackground(): GradientDrawable {
        return chipBackground(Color.argb(56, 255, 255, 255), Color.argb(58, 215, 235, 255), 21f)
    }

    private fun logCardBackground(): GradientDrawable {
        return chipBackground(Color.argb(34, 255, 255, 255), Color.argb(42, 180, 224, 255), 20f)
    }

    private fun chipBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun String.cleanOverlayText(): String {
        return trim()
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
    }

    private fun String.limitOverlayText(limit: Int): String {
        if (length <= limit) return this
        return take((limit - 1).coerceAtLeast(1)).trimEnd() + "…"
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()

    private inner class DragTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            val wm = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    view.animate().scaleX(0.988f).scaleY(0.988f).setDuration(90L).start()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > dp(4f) || abs(dy) > dp(4f)) dragging = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140L).setInterpolator(SOFT_OUT).start()
                    if (!dragging && abs(event.rawX - downRawX) < dp(8f) && abs(event.rawY - downRawY) < dp(8f)) {
                        if (latestProgress.pendingConfirmation == null && latestProgress.pendingUserInput == null) {
                            expanded = !expanded
                            refreshExpandedState()
                        }
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val edge = dp(10f)
                        params.x = if (params.x + view.width / 2 < screenWidth / 2) edge else max(edge, screenWidth - view.width - edge)
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val COMPACT_WIDTH_DP = 236f
        private const val EXPANDED_WIDTH_DP = 362f
        private const val ACTION_TEXT_LIMIT = 120
        private const val RESULT_TEXT_LIMIT = 180
        private const val LATEST_TEXT_LIMIT = 120
        private const val LOG_LINE_TEXT_LIMIT = 260
        private const val PANEL_MESSAGE_TEXT_LIMIT = 260
        private const val OVERLAY_LOG_LINES = 24
        private val SOFT_OUT = DecelerateInterpolator(1.55f)

        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun ensureStarted(context: Context): Boolean {
            if (!canDrawOverlays(context)) return false
            context.startService(Intent(context, AgentOverlayService::class.java))
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentOverlayService::class.java))
        }

        fun requestPermissionIfNeeded(context: Context): Boolean {
            if (canDrawOverlays(context)) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            return false
        }
    }
}

private enum class ButtonTone { Ghost, Danger, PrimaryWarm, GhostWarm }
