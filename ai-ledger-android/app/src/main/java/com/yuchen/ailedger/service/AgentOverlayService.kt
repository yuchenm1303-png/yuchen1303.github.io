package com.yuchen.ailedger.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
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
import kotlin.math.min

class AgentOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootView: AgentCapsulePanelView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null

    private var statusDotView: AgentStatusOrbView? = null
    private var titleView: TextView? = null
    private var stateChipView: TextView? = null
    private var compactLineView: TextView? = null
    private var contentGroup: LinearLayout? = null
    private var actionLabelView: TextView? = null
    private var actionTitleView: TextView? = null
    private var actionSubtitleView: TextView? = null
    private var railView: AgentCapsuleRailView? = null
    private var resultView: TextView? = null
    private var latestView: TextView? = null
    private var choicePanel: LinearLayout? = null
    private var choiceTitleView: TextView? = null
    private var choiceMessageView: TextView? = null
    private var primaryChoiceView: TextView? = null
    private var secondaryChoiceView: TextView? = null
    private var logsPanel: LinearLayout? = null
    private var logsView: TextView? = null
    private var collapseView: TextView? = null
    private var detailView: TextView? = null
    private var stopView: TextView? = null

    private var density: Float = 1f
    private var isExpanded: Boolean = true
    private var logsExpanded: Boolean = false
    private var latestProgress: AgentOverlayProgress = AgentOverlayProgress()
    private var latestMode: AgentOverlayMode = AgentOverlayMode.Idle
    private var latestActionText: String = ""
    private var latestCompactText: String = ""

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
        snapAnimator?.cancel()
        statusDotView?.stopPulse()
        railView?.stopSweep()
        rootView?.stopSweep()
        rootView?.let { runCatching { windowManager?.removeView(it) } }
        rootView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        if (rootView != null) return
        val wm = windowManager ?: return
        val panel = AgentCapsulePanelView(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (AgentRuntimeController.overlayHiddenForCapture.value) View.INVISIBLE else View.VISIBLE
            alpha = if (AgentRuntimeController.overlayHiddenForCapture.value) 0f else 1f
            setPadding(dp(13f), dp(12f), dp(13f), dp(12f))
            elevation = dp(18f).toFloat()
            isClickable = true
        }

        val header = createHeaderRow()
        compactLineView = text("等待任务", 11.5f, Color.argb(218, 230, 239, 255), bold = true).apply {
            visibility = View.GONE
            includeFontPadding = false
            maxLines = 1
            setPadding(dp(2f), dp(7f), dp(2f), 0)
        }

        actionLabelView = text("CURRENT ACTION", 8.8f, Color.argb(205, 152, 231, 234), bold = true).apply {
            includeFontPadding = false
            letterSpacing = 0.18f
        }
        actionTitleView = text("等待任务", 15.6f, Color.WHITE, bold = true).apply {
            maxLines = 2
            setLineSpacing(dp(1f).toFloat(), 1.03f)
        }
        actionSubtitleView = text("轻点面板可收起，拖动可移动位置。", 10.6f, Color.argb(184, 225, 235, 255)).apply {
            maxLines = 2
        }
        railView = AgentCapsuleRailView(this).apply { visibility = View.GONE }

        val actionCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13f), dp(12f), dp(13f), dp(10f))
            background = capsuleBackground(
                fill = Color.argb(54, 255, 255, 255),
                stroke = Color.argb(54, 215, 235, 255),
                radiusDp = 22f
            )
            addView(actionLabelView)
            addView(actionTitleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(9f)
            })
            addView(actionSubtitleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            })
            addView(railView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14f)).apply {
                topMargin = dp(12f)
            })
        }

        resultView = text("", 10.7f, Color.argb(222, 246, 250, 255)).apply { maxLines = 2 }
        latestView = text("", 9.5f, Color.argb(164, 222, 234, 255)).apply {
            maxLines = 1
            includeFontPadding = false
        }
        val resultCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13f), dp(10f), dp(13f), dp(10f))
            background = capsuleBackground(
                fill = Color.argb(42, 255, 255, 255),
                stroke = Color.argb(36, 230, 240, 255),
                radiusDp = 19f
            )
            addView(resultView)
            addView(latestView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            })
        }

        choicePanel = createChoicePanel()
        val controls = createControlRow()
        logsPanel = createLogsPanel()

        contentGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, 0)
            addView(actionCapsule)
            addView(resultCapsule, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(9f)
            })
            addView(choicePanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10f)
            })
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10f)
            })
            addView(logsPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            })
        }

        panel.addView(header)
        panel.addView(compactLineView)
        panel.addView(contentGroup)
        panel.setOnTouchListener(DragTouchListener())

        val params = WindowManager.LayoutParams(
            dp(EXPANDED_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(14f)
            y = dp(88f)
        }

        rootView = panel
        layoutParams = params
        runCatching { wm.addView(panel, params) }
            .onSuccess {
                updateProgress(AgentRuntimeController.progress.value)
                if (!AgentRuntimeController.overlayHiddenForCapture.value) animateEntrance(panel)
            }
            .onFailure { stopSelf() }
    }

    private fun createHeaderRow(): LinearLayout {
        statusDotView = AgentStatusOrbView(this)
        titleView = text("AI 智能体", 14.4f, Color.WHITE, bold = true).apply {
            includeFontPadding = false
            letterSpacing = 0.02f
        }
        stateChipView = text("待命", 10.2f, Color.argb(232, 232, 246, 255), bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(10f), 0, dp(10f), dp(1f))
        }
        val openView = iconChip("↗") { openMainApp() }
        val closeView = iconChip("×") { stopSelf() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusDotView, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(9f) })
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stateChipView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(27f)).apply { marginStart = dp(8f) })
            addView(openView, LinearLayout.LayoutParams(dp(34f), dp(30f)).apply { marginStart = dp(7f) })
            addView(closeView, LinearLayout.LayoutParams(dp(34f), dp(30f)).apply { marginStart = dp(5f) })
        }
    }

    private fun createControlRow(): LinearLayout {
        collapseView = capsuleButton("收起", ButtonTone.Ghost) { toggleExpanded() }
        detailView = capsuleButton("详情", ButtonTone.Ghost) { toggleLogsExpanded() }
        stopView = capsuleButton("停止", ButtonTone.Danger) { AgentRuntimeController.stopTaskByUser() }.apply {
            visibility = View.GONE
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(collapseView, LinearLayout.LayoutParams(0, dp(37f), 1f).apply { marginEnd = dp(8f) })
            addView(detailView, LinearLayout.LayoutParams(0, dp(37f), 1f).apply { marginEnd = dp(8f) })
            addView(stopView, LinearLayout.LayoutParams(0, dp(37f), 1f))
        }
    }

    private fun createChoicePanel(): LinearLayout {
        choiceTitleView = text("需要确认", 13f, Color.argb(255, 255, 235, 190), bold = true)
        choiceMessageView = text("", 10.7f, Color.argb(232, 255, 244, 222)).apply {
            maxLines = 4
            setLineSpacing(dp(1f).toFloat(), 1.05f)
        }
        secondaryChoiceView = capsuleButton("取消任务", ButtonTone.GhostWarm) {
            AgentRuntimeController.choosePendingAction(false)
        }
        primaryChoiceView = capsuleButton("继续执行", ButtonTone.PrimaryWarm) {
            AgentRuntimeController.choosePendingAction(true)
        }
        val choiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(secondaryChoiceView, LinearLayout.LayoutParams(0, dp(38f), 1f).apply { marginEnd = dp(9f) })
            addView(primaryChoiceView, LinearLayout.LayoutParams(0, dp(38f), 1f))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(13f), dp(12f), dp(13f), dp(13f))
            background = capsuleBackground(
                fill = Color.argb(92, 116, 70, 38),
                stroke = Color.argb(112, 255, 214, 132),
                radiusDp = 24f
            )
            addView(choiceTitleView)
            addView(choiceMessageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
                bottomMargin = dp(11f)
            })
            addView(choiceRow)
        }
    }

    private fun createLogsPanel(): LinearLayout {
        logsView = text("", 9.3f, Color.argb(176, 224, 235, 255)).apply {
            maxLines = 6
            setLineSpacing(dp(1f).toFloat(), 1.05f)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = capsuleBackground(
                fill = Color.argb(36, 0, 12, 38),
                stroke = Color.argb(38, 215, 230, 255),
                radiusDp = 18f
            )
            addView(logsView)
        }
    }

    private fun updateProgress(progress: AgentOverlayProgress) {
        latestProgress = progress
        val pending = progress.pendingConfirmation
        val mode = when {
            pending != null -> AgentOverlayMode.Confirm
            !progress.enabled -> AgentOverlayMode.Paused
            progress.running -> AgentOverlayMode.Running
            else -> AgentOverlayMode.Idle
        }
        updateMode(mode)

        val actionText = when {
            pending != null -> pending.actionText.ifBlank { "高风险动作确认" }
            progress.currentAction.isNotBlank() -> progress.currentAction
            else -> "等待任务"
        }
        val compactText = when {
            pending != null -> "需要确认 · ${pending.actionText}".take(34)
            progress.running -> "执行中 · $actionText".take(34)
            !progress.enabled -> "已暂停 · 点击回到应用"
            else -> actionText.take(34)
        }

        titleView?.text = progress.title
        stateChipView?.apply {
            text = when (mode) {
                AgentOverlayMode.Running -> "执行中"
                AgentOverlayMode.Confirm -> "待确认"
                AgentOverlayMode.Paused -> "已关闭"
                AgentOverlayMode.Idle -> progress.status.ifBlank { "待命" }
            }
            background = stateChipBackground(mode)
        }
        actionLabelView?.text = when (mode) {
            AgentOverlayMode.Confirm -> "CONFIRM ACTION"
            AgentOverlayMode.Running -> "CURRENT ACTION"
            AgentOverlayMode.Paused -> "AGENT PAUSED"
            AgentOverlayMode.Idle -> "READY"
        }
        actionTitleView?.setAnimatedText(actionText, latestActionText)
        compactLineView?.setAnimatedText(compactText, latestCompactText)
        latestActionText = actionText
        latestCompactText = compactText

        actionSubtitleView?.text = when (mode) {
            AgentOverlayMode.Confirm -> "这一步需要确认后，智能体才会继续执行。"
            AgentOverlayMode.Running -> "正在观察屏幕，验证目标并执行下一步。"
            AgentOverlayMode.Paused -> "自动执行已暂停，可以回到应用中重新开启。"
            AgentOverlayMode.Idle -> "轻点面板可收起，拖动可移动位置。"
        }
        resultView?.text = progress.lastResult.takeIf { it.isNotBlank() }?.let { "上一步 · $it" } ?: "上一步 · 暂无执行结果"
        latestView?.text = progress.logs.lastOrNull()?.let { "最近 · $it" } ?: "最近 · 暂无运行日志"
        logsView?.text = progress.logs.takeLast(6).joinToString("\n") { "• $it" }.ifBlank { "暂无详细日志" }
        detailView?.text = if (logsExpanded) "收起日志" else "详情"
        stopView?.visibility = if (progress.running) View.VISIBLE else View.GONE

        if (pending != null) {
            if (!isExpanded) isExpanded = true
            choiceTitleView?.text = pending.title
            choiceMessageView?.text = pending.message
            primaryChoiceView?.text = pending.positiveText
            secondaryChoiceView?.text = pending.negativeText
        }
        refreshExpandedState(animated = true)
    }

    private fun updateMode(mode: AgentOverlayMode) {
        if (latestMode == mode) return
        latestMode = mode
        rootView?.setMode(mode)
        statusDotView?.setMode(mode)
        railView?.setMode(mode)
        val active = mode == AgentOverlayMode.Running || mode == AgentOverlayMode.Confirm
        if (active) {
            rootView?.startSweep()
            railView?.startSweep()
        } else {
            rootView?.stopSweep()
            railView?.stopSweep()
        }
        rootView?.animateModePulse()
    }

    private fun toggleExpanded() {
        if (latestProgress.pendingConfirmation != null) return
        isExpanded = !isExpanded
        if (!isExpanded) logsExpanded = false
        refreshExpandedState(animated = true)
    }

    private fun toggleLogsExpanded() {
        logsExpanded = !logsExpanded
        refreshExpandedState(animated = true)
    }

    private fun refreshExpandedState(animated: Boolean) {
        val pending = latestProgress.pendingConfirmation
        val expanded = isExpanded || pending != null
        contentGroup.setVisibleAnimated(expanded && !AgentRuntimeController.overlayHiddenForCapture.value)
        compactLineView.setVisibleAnimated(!expanded && !AgentRuntimeController.overlayHiddenForCapture.value)
        railView.setVisibleAnimated(expanded && latestProgress.running && pending == null)
        choicePanel.setVisibleAnimated(expanded && pending != null)
        logsPanel.setVisibleAnimated(expanded && logsExpanded)
        collapseView?.text = if (expanded) "收起" else "展开"
        detailView?.visibility = if (expanded) View.VISIBLE else View.GONE
        detailView?.text = if (logsExpanded) "收起日志" else "详情"
        val targetWidth = when {
            pending != null -> CONFIRM_WIDTH_DP
            expanded -> EXPANDED_WIDTH_DP
            else -> COMPACT_WIDTH_DP
        }
        updateWindowWidth(dp(targetWidth), animated)
    }

    private fun updateWindowWidth(targetWidth: Int, animated: Boolean) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        if (params.width == targetWidth) return
        params.width = targetWidth
        runCatching { windowManager?.updateViewLayout(view, params) }
        if (animated) {
            view.animate().cancel()
            view.scaleX = 0.988f
            view.scaleY = 0.988f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(SOFT_OUT)
                .start()
        }
    }

    private fun setHiddenForCleanCapture(hidden: Boolean) {
        val view = rootView ?: return
        view.animate().cancel()
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
            refreshExpandedState(animated = false)
        }
    }

    private fun animateEntrance(view: View) {
        view.alpha = 0f
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.translationY = -dp(8f).toFloat()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(OvershootInterpolator(1.02f))
            .start()
    }

    private fun View?.setVisibleAnimated(visible: Boolean) {
        val view = this ?: return
        view.animate().cancel()
        if (visible) {
            if (view.visibility != View.VISIBLE) {
                view.alpha = 0f
                view.translationY = dp(4f).toFloat()
                view.visibility = View.VISIBLE
            }
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160L)
                .setInterpolator(SOFT_OUT)
                .setListener(null)
                .start()
        } else if (view.visibility == View.VISIBLE) {
            view.animate()
                .alpha(0f)
                .translationY(dp(4f).toFloat())
                .setDuration(110L)
                .setInterpolator(SOFT_OUT)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.visibility = View.GONE
                        view.alpha = 1f
                        view.translationY = 0f
                        view.animate().setListener(null)
                    }
                })
                .start()
        }
    }

    private fun TextView.setAnimatedText(newText: String, oldText: String) {
        if (newText == oldText && text.toString() == newText) return
        animate().cancel()
        animate()
            .alpha(0f)
            .translationY(-dp(4f).toFloat())
            .setDuration(80L)
            .setInterpolator(SOFT_OUT)
            .withEndAction {
                text = newText
                translationY = dp(4f).toFloat()
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(140L)
                    .setInterpolator(SOFT_OUT)
                    .start()
            }
            .start()
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            includeFontPadding = true
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun iconChip(value: String, onClick: () -> Unit): TextView {
        return text(value, 17f, Color.argb(228, 245, 250, 255), bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = capsuleBackground(
                fill = Color.argb(28, 255, 255, 255),
                stroke = Color.argb(36, 230, 240, 255),
                radiusDp = 15f
            )
            setOnClickListener { onClick() }
        }
    }

    private fun capsuleButton(label: String, tone: ButtonTone, onClick: () -> Unit): TextView {
        return text(label, 11.6f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(10f), 0, dp(10f), dp(1f))
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
            cornerRadius = dp(19f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun stateChipBackground(mode: AgentOverlayMode): GradientDrawable {
        val colors = when (mode) {
            AgentOverlayMode.Running -> intArrayOf(Color.argb(72, 95, 255, 218), Color.argb(44, 96, 148, 255))
            AgentOverlayMode.Confirm -> intArrayOf(Color.argb(88, 255, 184, 90), Color.argb(46, 255, 112, 92))
            AgentOverlayMode.Paused -> intArrayOf(Color.argb(48, 195, 202, 218), Color.argb(26, 132, 142, 160))
            AgentOverlayMode.Idle -> intArrayOf(Color.argb(42, 214, 228, 255), Color.argb(24, 160, 190, 255))
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(14f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1), Color.argb(48, 235, 248, 255))
        }
    }

    private fun capsuleBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun snapToNearestEdge(view: View) {
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val edge = dp(10f)
        val targetX = if (params.x + view.width / 2 < screenWidth / 2) edge else max(edge, screenWidth - view.width - edge)
        val maxY = max(edge, screenHeight - view.height - dp(28f))
        val targetY = params.y.coerceIn(edge, maxY)
        val startX = params.x
        val startY = params.y
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = SOFT_OUT
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                params.x = (startX + (targetX - startX) * t).toInt()
                params.y = (startY + (targetY - startY) * t).toInt()
                runCatching { wm.updateViewLayout(view, params) }
            }
            start()
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
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
                    snapAnimator?.cancel()
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
                        toggleExpanded()
                    } else {
                        snapToNearestEdge(view)
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val COMPACT_WIDTH_DP = 218f
        private const val EXPANDED_WIDTH_DP = 306f
        private const val CONFIRM_WIDTH_DP = 318f
        private val SOFT_OUT: TimeInterpolator = DecelerateInterpolator(1.55f)

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

private enum class AgentOverlayMode { Idle, Running, Confirm, Paused }
private enum class ButtonTone { Ghost, Danger, PrimaryWarm, GhostWarm }

private class AgentCapsulePanelView(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val rect = RectF()
    private val clipPath = Path()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode: AgentOverlayMode = AgentOverlayMode.Idle
    private var sweepAnimator: ValueAnimator? = null
    private var sweepProgress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        clipToPadding = false
    }

    fun setMode(value: AgentOverlayMode) {
        if (mode == value) return
        mode = value
        invalidate()
    }

    fun animateModePulse() {
        animate().cancel()
        scaleX = 0.994f
        scaleY = 0.994f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator(1.55f))
            .start()
    }

    fun startSweep() {
        if (sweepAnimator?.isStarted == true) return
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { sweepProgress = it.animatedValue as Float }
            start()
        }
    }

    fun stopSweep() {
        sweepAnimator?.cancel()
        sweepAnimator = null
        sweepProgress = 0f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.onDraw(canvas)
            return
        }
        val inset = 1.4f * density
        val radius = min(36f * density, h / 2f - inset).coerceAtLeast(22f * density)
        rect.set(inset, inset, w - inset, h - inset)
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        shadowPaint.style = Paint.Style.FILL
        shadowPaint.color = when (mode) {
            AgentOverlayMode.Confirm -> Color.argb(54, 255, 156, 78)
            AgentOverlayMode.Running -> Color.argb(48, 80, 178, 255)
            AgentOverlayMode.Paused -> Color.argb(36, 112, 128, 160)
            AgentOverlayMode.Idle -> Color.argb(38, 90, 136, 255)
        }
        shadowPaint.setShadowLayer(22f * density, 0f, 8f * density, shadowPaint.color)
        canvas.drawRoundRect(rect, radius, radius, shadowPaint)
        shadowPaint.clearShadowLayer()

        fillPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            panelColors(),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        fillPaint.shader = null

        canvas.save()
        canvas.clipPath(clipPath)
        drawSoftGlows(canvas, w, h)
        drawBroadSweep(canvas, w, h)
        canvas.restore()

        borderPaint.strokeWidth = 1.1f * density
        borderPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            intArrayOf(Color.argb(150, 226, 250, 255), borderColor(), Color.argb(56, 255, 255, 255)),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        borderPaint.shader = null

        borderPaint.strokeWidth = 0.55f * density
        borderPaint.color = Color.argb(38, 255, 255, 255)
        val inner = RectF(rect.left + 2.4f * density, rect.top + 2.4f * density, rect.right - 2.4f * density, rect.bottom - 2.4f * density)
        canvas.drawRoundRect(inner, radius - 2.4f * density, radius - 2.4f * density, borderPaint)
        super.onDraw(canvas)
    }

    private fun drawSoftGlows(canvas: Canvas, w: Float, h: Float) {
        glowPaint.shader = RadialGradient(
            w * 0.06f,
            h * 0.02f,
            max(w, h) * 0.62f,
            intArrayOf(Color.argb(58, 184, 255, 244), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = RadialGradient(
            w * 0.92f,
            h * 0.92f,
            max(w, h) * 0.76f,
            intArrayOf(cornerGlow(), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = null
    }

    private fun drawBroadSweep(canvas: Canvas, w: Float, h: Float) {
        if (mode != AgentOverlayMode.Running && mode != AgentOverlayMode.Confirm) return
        val sweepWidth = 150f * density
        val start = -w + sweepProgress * (w * 2.15f)
        val core = if (mode == AgentOverlayMode.Confirm) Color.argb(58, 255, 210, 126) else Color.argb(52, 168, 238, 255)
        sweepPaint.shader = LinearGradient(
            start,
            0f,
            start + sweepWidth,
            0f,
            intArrayOf(Color.TRANSPARENT, core, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.rotate(-15f, w / 2f, h / 2f)
        canvas.drawRect(start, -h, start + sweepWidth, h * 2.0f, sweepPaint)
        canvas.restore()
        sweepPaint.shader = null
    }

    private fun panelColors(): IntArray {
        return when (mode) {
            AgentOverlayMode.Confirm -> intArrayOf(Color.argb(224, 32, 28, 52), Color.argb(206, 86, 58, 72), Color.argb(224, 22, 18, 44))
            AgentOverlayMode.Running -> intArrayOf(Color.argb(224, 12, 22, 48), Color.argb(202, 32, 50, 92), Color.argb(222, 14, 18, 48))
            AgentOverlayMode.Paused -> intArrayOf(Color.argb(218, 18, 22, 40), Color.argb(198, 38, 42, 64), Color.argb(218, 16, 18, 36))
            AgentOverlayMode.Idle -> intArrayOf(Color.argb(220, 14, 24, 52), Color.argb(198, 34, 44, 82), Color.argb(218, 16, 18, 46))
        }
    }

    private fun borderColor(): Int {
        return when (mode) {
            AgentOverlayMode.Confirm -> Color.argb(136, 255, 210, 126)
            AgentOverlayMode.Running -> Color.argb(128, 152, 235, 255)
            AgentOverlayMode.Paused -> Color.argb(78, 210, 220, 245)
            AgentOverlayMode.Idle -> Color.argb(96, 195, 218, 255)
        }
    }

    private fun cornerGlow(): Int {
        return when (mode) {
            AgentOverlayMode.Confirm -> Color.argb(56, 255, 138, 72)
            AgentOverlayMode.Running -> Color.argb(46, 120, 118, 255)
            AgentOverlayMode.Paused -> Color.argb(30, 175, 190, 220)
            AgentOverlayMode.Idle -> Color.argb(36, 118, 134, 255)
        }
    }
}

private class AgentStatusOrbView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode: AgentOverlayMode = AgentOverlayMode.Idle
    private var pulse = 0f
    private var animator: ValueAnimator? = null

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun setMode(value: AgentOverlayMode) {
        mode = value
        if (value == AgentOverlayMode.Running || value == AgentOverlayMode.Confirm) startPulse() else stopPulse()
        invalidate()
    }

    fun startPulse() {
        if (animator?.isStarted == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1220L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator(1.35f)
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        animator?.cancel()
        animator = null
        pulse = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val color = when (mode) {
            AgentOverlayMode.Running -> Color.argb(255, 116, 255, 224)
            AgentOverlayMode.Confirm -> Color.argb(255, 255, 196, 104)
            AgentOverlayMode.Paused -> Color.argb(230, 170, 184, 206)
            AgentOverlayMode.Idle -> Color.argb(240, 176, 220, 255)
        }
        val radius = min(width, height) * 0.25f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((44 + 42 * pulse).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, radius * (2.0f + pulse * 0.55f), paint)
        paint.color = color
        paint.setShadowLayer((7f + 4f * pulse) * density, 0f, 0f, color)
        canvas.drawCircle(cx, cy, radius * (1f + 0.1f * pulse), paint)
        paint.clearShadowLayer()
    }
}

private class AgentCapsuleRailView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode: AgentOverlayMode = AgentOverlayMode.Running
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setMode(value: AgentOverlayMode) {
        mode = value
        invalidate()
    }

    fun startSweep() {
        if (animator?.isStarted == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopSweep() {
        animator?.cancel()
        animator = null
        progress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat().coerceAtLeast(1f)
        val cy = h / 2f
        val baseHeight = 5.5f * density
        rect.set(0f, cy - baseHeight / 2f, width.toFloat(), cy + baseHeight / 2f)
        paint.shader = null
        paint.color = Color.argb(38, 225, 240, 255)
        canvas.drawRoundRect(rect, baseHeight / 2f, baseHeight / 2f, paint)

        val shimmerWidth = max(90f * density, width * 0.38f)
        val start = -shimmerWidth + progress * (width + shimmerWidth * 2f)
        val core = if (mode == AgentOverlayMode.Confirm) Color.argb(210, 255, 205, 118) else Color.argb(210, 132, 248, 255)
        paint.shader = LinearGradient(
            start,
            0f,
            start + shimmerWidth,
            0f,
            intArrayOf(Color.TRANSPARENT, core, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, baseHeight / 2f, baseHeight / 2f, paint)
        paint.shader = null
    }
}
