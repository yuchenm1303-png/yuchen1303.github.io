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
    private var rootView: AgentGlassPanelView? = null
    private var bodyContainer: LinearLayout? = null
    private var titleView: TextView? = null
    private var statusPillView: TextView? = null
    private var compactActionView: TextView? = null
    private var actionEyebrowView: TextView? = null
    private var actionView: TextView? = null
    private var subtitleView: TextView? = null
    private var resultView: TextView? = null
    private var latestLogView: TextView? = null
    private var logPanel: LinearLayout? = null
    private var logView: TextView? = null
    private var detailToggleView: TextView? = null
    private var collapseView: TextView? = null
    private var stopTaskView: TextView? = null
    private var choicePanel: LinearLayout? = null
    private var choiceTitleView: TextView? = null
    private var choiceMessageView: TextView? = null
    private var primaryChoiceView: TextView? = null
    private var secondaryChoiceView: TextView? = null
    private var statusDotView: AgentStatusDotView? = null
    private var progressLineView: AgentShimmerLineView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private var isExpanded: Boolean = true
    private var logsExpanded: Boolean = false
    private var latestProgress: AgentOverlayProgress = AgentOverlayProgress()
    private var latestActionText: String = ""
    private var latestCompactText: String = ""
    private var latestVisualMode: AgentPanelVisualMode = AgentPanelVisualMode.Idle
    private var density: Float = 1f

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
            AgentRuntimeController.progress.collectLatest { progress -> updateProgress(progress) }
        }
        scope.launch {
            AgentRuntimeController.overlayHiddenForCapture.collectLatest { hidden -> setHiddenForCleanCapture(hidden) }
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
        progressLineView?.stopSweep()
        rootView?.stopSweep()
        rootView?.let { view -> runCatching { windowManager?.removeView(view) } }
        rootView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        if (rootView != null) return
        val wm = windowManager ?: return
        val panel = AgentGlassPanelView(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (AgentRuntimeController.overlayHiddenForCapture.value) View.INVISIBLE else View.VISIBLE
            alpha = if (AgentRuntimeController.overlayHiddenForCapture.value) 0f else 1f
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            elevation = dp(18f).toFloat()
            isClickable = true
        }

        statusDotView = AgentStatusDotView(this)
        titleView = overlayText("AI 智能体", 14f, Color.WHITE, bold = true).apply {
            letterSpacing = 0.03f
        }
        statusPillView = overlayText("待命", 10f, Color.argb(230, 226, 244, 255), bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        }
        val openAppView = headerIcon("↗") { openMainApp() }
        val closeView = headerIcon("×") { stopSelf() }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusDotView, LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(7f) })
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(statusPillView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24f)).apply { marginStart = dp(8f) })
            addView(openAppView, LinearLayout.LayoutParams(dp(30f), dp(28f)).apply { marginStart = dp(5f) })
            addView(closeView, LinearLayout.LayoutParams(dp(30f), dp(28f)))
            setOnClickListener { toggleExpanded() }
        }

        compactActionView = overlayText("等待任务", 11f, Color.argb(218, 226, 238, 255), bold = true).apply {
            maxLines = 1
            includeFontPadding = false
            visibility = View.GONE
            setPadding(dp(2f), dp(7f), dp(2f), dp(1f))
        }

        actionEyebrowView = overlayText("CURRENT ACTION", 9f, Color.argb(210, 157, 232, 235), bold = true).apply {
            letterSpacing = 0.16f
            includeFontPadding = false
        }
        actionView = overlayText("等待任务", 15f, Color.WHITE, bold = true).apply {
            maxLines = 2
            setLineSpacing(dp(1f).toFloat(), 1.02f)
            setPadding(0, dp(5f), 0, 0)
        }
        subtitleView = overlayText("轻点面板可收起，拖动可移动位置。", 10.5f, Color.argb(188, 220, 231, 255)).apply {
            maxLines = 2
            setPadding(0, dp(3f), 0, 0)
        }
        progressLineView = AgentShimmerLineView(this).apply {
            visibility = View.GONE
        }

        resultView = overlayText("", 10.5f, Color.argb(212, 245, 249, 255)).apply {
            maxLines = 2
        }
        latestLogView = overlayText("", 9.5f, Color.argb(174, 220, 232, 255)).apply {
            maxLines = 1
            setPadding(0, dp(5f), 0, 0)
        }
        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            background = roundedBackground(
                fill = Color.argb(56, 255, 255, 255),
                stroke = Color.argb(46, 210, 235, 255),
                radius = dp(15f).toFloat()
            )
            addView(resultView)
            addView(latestLogView)
        }

        choiceTitleView = overlayText("需要确认", 12f, Color.WHITE, bold = true)
        choiceMessageView = overlayText("", 10.5f, Color.argb(232, 255, 245, 224)).apply {
            maxLines = 4
            setLineSpacing(dp(1f).toFloat(), 1.05f)
        }
        secondaryChoiceView = actionButton("取消任务", ButtonTone.Ghost) {
            AgentRuntimeController.choosePendingAction(false)
        }
        primaryChoiceView = actionButton("继续执行", ButtonTone.Primary) {
            AgentRuntimeController.choosePendingAction(true)
        }
        val choiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(secondaryChoiceView, LinearLayout.LayoutParams(0, dp(35f), 1f).apply { marginEnd = dp(8f) })
            addView(primaryChoiceView, LinearLayout.LayoutParams(0, dp(35f), 1f))
        }
        choicePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(11f), dp(10f), dp(11f), dp(11f))
            background = roundedBackground(
                fill = Color.argb(110, 110, 66, 34),
                stroke = Color.argb(145, 255, 208, 118),
                radius = dp(18f).toFloat()
            )
            addView(choiceTitleView)
            addView(choiceMessageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5f)
                bottomMargin = dp(10f)
            })
            addView(choiceRow)
        }

        collapseView = actionButton("收起", ButtonTone.Ghost) { toggleExpanded() }
        detailToggleView = actionButton("详情", ButtonTone.Ghost) { toggleLogsExpanded() }
        stopTaskView = actionButton("停止", ButtonTone.Danger) { AgentRuntimeController.stopTaskByUser() }.apply {
            visibility = View.GONE
        }
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(collapseView, LinearLayout.LayoutParams(0, dp(34f), 1f).apply { marginEnd = dp(8f) })
            addView(detailToggleView, LinearLayout.LayoutParams(0, dp(34f), 1f).apply { marginEnd = dp(8f) })
            addView(stopTaskView, LinearLayout.LayoutParams(0, dp(34f), 1f))
        }

        logView = overlayText("", 9.2f, Color.argb(178, 225, 235, 255)).apply {
            maxLines = 6
            setLineSpacing(dp(1f).toFloat(), 1.05f)
        }
        logPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            background = roundedBackground(
                fill = Color.argb(42, 8, 18, 48),
                stroke = Color.argb(52, 210, 230, 255),
                radius = dp(14f).toFloat()
            )
            addView(logView)
        }

        bodyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, 0)
            addView(actionEyebrowView)
            addView(actionView)
            addView(subtitleView)
            addView(progressLineView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6f)).apply {
                topMargin = dp(10f)
                bottomMargin = dp(8f)
            })
            addView(resultCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
            })
            addView(choicePanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10f)
            })
            addView(controlRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10f)
            })
            addView(logPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            })
        }

        panel.addView(brandRow)
        panel.addView(compactActionView)
        panel.addView(bodyContainer)
        panel.setOnTouchListener(DragTouchListener())

        val params = WindowManager.LayoutParams(
            dp(EXPANDED_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12f)
            y = dp(86f)
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

    private fun updateProgress(progress: AgentOverlayProgress) {
        latestProgress = progress
        val pending = progress.pendingConfirmation
        val mode = when {
            pending != null -> AgentPanelVisualMode.Confirm
            !progress.enabled -> AgentPanelVisualMode.Paused
            progress.running -> AgentPanelVisualMode.Running
            else -> AgentPanelVisualMode.Idle
        }
        updateVisualMode(mode)

        titleView?.text = progress.title
        statusPillView?.apply {
            text = when (mode) {
                AgentPanelVisualMode.Running -> "执行中"
                AgentPanelVisualMode.Confirm -> "待确认"
                AgentPanelVisualMode.Paused -> "已关闭"
                AgentPanelVisualMode.Idle -> progress.status.ifBlank { "待命" }
            }
            background = statusPillBackground(mode)
        }
        actionEyebrowView?.text = when (mode) {
            AgentPanelVisualMode.Confirm -> "CONFIRM ACTION"
            AgentPanelVisualMode.Running -> "CURRENT ACTION"
            AgentPanelVisualMode.Paused -> "AGENT PAUSED"
            AgentPanelVisualMode.Idle -> "READY"
        }

        val actionText = when {
            pending != null -> pending.actionText.ifBlank { "高风险动作确认" }
            progress.currentAction.isNotBlank() -> progress.currentAction
            else -> "等待任务"
        }
        val compactText = when {
            pending != null -> "需要确认 · ${pending.actionText}".take(32)
            progress.running -> "正在执行 · $actionText".take(32)
            !progress.enabled -> "自动执行已暂停"
            else -> actionText.take(32)
        }
        actionView?.setAnimatedText(actionText, latestActionText)
        compactActionView?.setAnimatedText(compactText, latestCompactText)
        latestActionText = actionText
        latestCompactText = compactText

        subtitleView?.text = when (mode) {
            AgentPanelVisualMode.Confirm -> "这一步需要你确认后，智能体才会继续执行。"
            AgentPanelVisualMode.Running -> "正在观察屏幕、验证目标并执行下一步。"
            AgentPanelVisualMode.Paused -> "智能体自动执行已暂停，可以回到应用中重新开启。"
            AgentPanelVisualMode.Idle -> "轻点面板可收起，拖动可移动位置。"
        }
        resultView?.text = progress.lastResult.takeIf { it.isNotBlank() }?.let { "上一步：$it" } ?: "上一步：暂无执行结果"
        latestLogView?.text = progress.logs.lastOrNull()?.let { "最近：$it" } ?: "最近：暂无运行日志"
        logView?.text = progress.logs.takeLast(6).joinToString("\n") { "• $it" }.ifBlank { "暂无详细日志" }
        detailToggleView?.text = if (logsExpanded) "收起日志" else "详情"
        stopTaskView?.visibility = if (progress.running) View.VISIBLE else View.GONE

        choicePanel.setVisibleAnimated(pending != null && isExpanded)
        if (pending != null) {
            choiceTitleView?.text = pending.title
            choiceMessageView?.text = pending.message
            primaryChoiceView?.text = pending.positiveText
            secondaryChoiceView?.text = pending.negativeText
            if (!isExpanded) isExpanded = true
        }
        refreshExpandedState(animated = true)
    }

    private fun updateVisualMode(mode: AgentPanelVisualMode) {
        if (latestVisualMode == mode) return
        latestVisualMode = mode
        rootView?.setVisualMode(mode)
        statusDotView?.setVisualMode(mode)
        progressLineView?.setVisualMode(mode)
        if (mode == AgentPanelVisualMode.Running || mode == AgentPanelVisualMode.Confirm) {
            rootView?.startSweep()
            progressLineView?.startSweep()
        } else {
            rootView?.stopSweep()
            progressLineView?.stopSweep()
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
        bodyContainer.setVisibleAnimated(expanded && !AgentRuntimeController.overlayHiddenForCapture.value)
        compactActionView.setVisibleAnimated(!expanded && !AgentRuntimeController.overlayHiddenForCapture.value)
        logPanel.setVisibleAnimated(expanded && logsExpanded)
        progressLineView.setVisibleAnimated(expanded && latestProgress.running && pending == null)
        choicePanel.setVisibleAnimated(expanded && pending != null)
        collapseView?.text = if (expanded) "收起" else "展开"
        detailToggleView?.visibility = if (expanded) View.VISIBLE else View.GONE
        detailToggleView?.text = if (logsExpanded) "收起日志" else "详情"
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
            view.scaleX = 0.985f
            view.scaleY = 0.985f
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
                view.scaleX = 0.985f
                view.scaleY = 0.985f
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
            .setInterpolator(OvershootInterpolator(1.04f))
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
            .translationY(-dp(5f).toFloat())
            .setDuration(86L)
            .setInterpolator(SOFT_OUT)
            .withEndAction {
                text = newText
                translationY = dp(5f).toFloat()
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(145L)
                    .setInterpolator(SOFT_OUT)
                    .start()
            }
            .start()
    }

    private fun overlayText(textValue: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = sp
            setTextColor(color)
            includeFontPadding = true
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun headerIcon(textValue: String, onClick: () -> Unit): TextView {
        return overlayText(textValue, 17f, Color.argb(226, 255, 255, 255), bold = true).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = roundedBackground(Color.argb(28, 255, 255, 255), Color.argb(42, 220, 235, 255), dp(13f).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun actionButton(textValue: String, tone: ButtonTone, onClick: () -> Unit): TextView {
        return overlayText(textValue, 11.5f, Color.WHITE, bold = true).apply {
            text = textValue
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8f), 0, dp(8f), dp(1f))
            background = buttonBackground(tone)
            setOnClickListener { onClick() }
        }
    }

    private fun buttonBackground(tone: ButtonTone): GradientDrawable {
        val colors = when (tone) {
            ButtonTone.Primary -> intArrayOf(Color.argb(232, 50, 160, 130), Color.argb(222, 84, 105, 238))
            ButtonTone.Danger -> intArrayOf(Color.argb(218, 196, 61, 92), Color.argb(214, 112, 48, 112))
            ButtonTone.Ghost -> intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(44, 170, 205, 255))
        }
        val stroke = when (tone) {
            ButtonTone.Primary -> Color.argb(120, 216, 255, 245)
            ButtonTone.Danger -> Color.argb(116, 255, 205, 222)
            ButtonTone.Ghost -> Color.argb(68, 230, 240, 255)
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(16f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun statusPillBackground(mode: AgentPanelVisualMode): GradientDrawable {
        val colors = when (mode) {
            AgentPanelVisualMode.Running -> intArrayOf(Color.argb(96, 88, 240, 218), Color.argb(72, 116, 128, 255))
            AgentPanelVisualMode.Confirm -> intArrayOf(Color.argb(112, 255, 184, 88), Color.argb(76, 255, 112, 92))
            AgentPanelVisualMode.Paused -> intArrayOf(Color.argb(70, 190, 198, 216), Color.argb(42, 130, 140, 160))
            AgentPanelVisualMode.Idle -> intArrayOf(Color.argb(60, 210, 224, 255), Color.argb(36, 160, 190, 255))
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(12f).toFloat()
            setStroke(dp(1f).coerceAtLeast(1), Color.argb(58, 235, 248, 255))
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
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
        snapAnimator?.cancel()
        val startX = params.x
        val startY = params.y
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
                    view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(90L).start()
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
        private const val COMPACT_WIDTH_DP = 205f
        private const val EXPANDED_WIDTH_DP = 312f
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

private enum class AgentPanelVisualMode { Idle, Running, Confirm, Paused }
private enum class ButtonTone { Primary, Danger, Ghost }

private class AgentGlassPanelView(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val rect = RectF()
    private val clipPath = Path()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var visualMode: AgentPanelVisualMode = AgentPanelVisualMode.Idle
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

    fun setVisualMode(mode: AgentPanelVisualMode) {
        if (visualMode == mode) return
        visualMode = mode
        invalidate()
    }

    fun animateModePulse() {
        animate().cancel()
        scaleX = 0.992f
        scaleY = 0.992f
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
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator -> sweepProgress = animator.animatedValue as Float }
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
        val inset = 1.5f * density
        val radius = 24f * density
        rect.set(inset, inset, w - inset, h - inset)
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        shadowPaint.style = Paint.Style.FILL
        shadowPaint.color = when (visualMode) {
            AgentPanelVisualMode.Confirm -> Color.argb(58, 255, 157, 90)
            AgentPanelVisualMode.Running -> Color.argb(58, 85, 190, 255)
            AgentPanelVisualMode.Paused -> Color.argb(42, 120, 132, 160)
            AgentPanelVisualMode.Idle -> Color.argb(44, 94, 142, 255)
        }
        shadowPaint.setShadowLayer(22f * density, 0f, 8f * density, shadowPaint.color)
        canvas.drawRoundRect(rect, radius, radius, shadowPaint)
        shadowPaint.clearShadowLayer()

        fillPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            panelFillColors(),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        fillPaint.shader = null

        canvas.save()
        canvas.clipPath(clipPath)
        glowPaint.shader = RadialGradient(
            w * 0.12f,
            h * 0.02f,
            max(w, h) * 0.62f,
            intArrayOf(Color.argb(84, 210, 255, 250), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = RadialGradient(
            w * 0.9f,
            h * 1.05f,
            max(w, h) * 0.7f,
            intArrayOf(panelCornerGlow(), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = null
        drawSweep(canvas, w, h)
        canvas.restore()

        borderPaint.strokeWidth = 1.15f * density
        borderPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            intArrayOf(Color.argb(172, 219, 255, 255), panelBorderColor(), Color.argb(72, 255, 255, 255)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        borderPaint.shader = null

        borderPaint.strokeWidth = 0.55f * density
        borderPaint.color = Color.argb(55, 255, 255, 255)
        val inner = RectF(rect.left + 2f * density, rect.top + 2f * density, rect.right - 2f * density, rect.bottom - 2f * density)
        canvas.drawRoundRect(inner, radius - 2f * density, radius - 2f * density, borderPaint)

        super.onDraw(canvas)
    }

    private fun drawSweep(canvas: Canvas, width: Float, height: Float) {
        if (visualMode != AgentPanelVisualMode.Running && visualMode != AgentPanelVisualMode.Confirm) return
        val sweepWidth = 72f * density
        val start = -width + sweepProgress * (width * 2.35f)
        val color = if (visualMode == AgentPanelVisualMode.Confirm) Color.argb(76, 255, 210, 128) else Color.argb(72, 184, 248, 255)
        sweepPaint.shader = LinearGradient(
            start,
            0f,
            start + sweepWidth,
            0f,
            intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.rotate(-16f, width / 2f, height / 2f)
        canvas.drawRect(start, -height, start + sweepWidth, height * 2f, sweepPaint)
        canvas.restore()
        sweepPaint.shader = null
    }

    private fun panelFillColors(): IntArray {
        return when (visualMode) {
            AgentPanelVisualMode.Confirm -> intArrayOf(Color.argb(232, 44, 36, 72), Color.argb(224, 96, 60, 78), Color.argb(232, 28, 30, 68))
            AgentPanelVisualMode.Running -> intArrayOf(Color.argb(232, 20, 35, 76), Color.argb(224, 42, 65, 125), Color.argb(232, 30, 22, 66))
            AgentPanelVisualMode.Paused -> intArrayOf(Color.argb(226, 26, 31, 54), Color.argb(218, 46, 50, 78), Color.argb(226, 25, 27, 50))
            AgentPanelVisualMode.Idle -> intArrayOf(Color.argb(230, 20, 32, 68), Color.argb(218, 40, 48, 96), Color.argb(226, 26, 23, 60))
        }
    }

    private fun panelBorderColor(): Int {
        return when (visualMode) {
            AgentPanelVisualMode.Confirm -> Color.argb(156, 255, 201, 120)
            AgentPanelVisualMode.Running -> Color.argb(150, 150, 242, 255)
            AgentPanelVisualMode.Paused -> Color.argb(96, 210, 220, 245)
            AgentPanelVisualMode.Idle -> Color.argb(118, 195, 218, 255)
        }
    }

    private fun panelCornerGlow(): Int {
        return when (visualMode) {
            AgentPanelVisualMode.Confirm -> Color.argb(64, 255, 138, 82)
            AgentPanelVisualMode.Running -> Color.argb(58, 118, 122, 255)
            AgentPanelVisualMode.Paused -> Color.argb(38, 180, 190, 220)
            AgentPanelVisualMode.Idle -> Color.argb(46, 116, 134, 255)
        }
    }
}

private class AgentStatusDotView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var visualMode: AgentPanelVisualMode = AgentPanelVisualMode.Idle
    private var pulse: Float = 0f
    private var pulseAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setVisualMode(mode: AgentPanelVisualMode) {
        visualMode = mode
        if (mode == AgentPanelVisualMode.Running || mode == AgentPanelVisualMode.Confirm) startPulse() else stopPulse()
        invalidate()
    }

    fun startPulse() {
        if (pulseAnimator?.isStarted == true) return
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1180L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator(1.35f)
            addUpdateListener { animator ->
                pulse = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulse = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.24f
        val color = when (visualMode) {
            AgentPanelVisualMode.Running -> Color.argb(255, 117, 255, 226)
            AgentPanelVisualMode.Confirm -> Color.argb(255, 255, 198, 108)
            AgentPanelVisualMode.Paused -> Color.argb(230, 178, 190, 210)
            AgentPanelVisualMode.Idle -> Color.argb(240, 180, 220, 255)
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.setShadowLayer((8f + 4f * pulse) * density, 0f, 0f, color)
        canvas.drawCircle(cx, cy, base * (1f + 0.12f * pulse), paint)
        paint.clearShadowLayer()
        paint.color = Color.argb((46 + 62 * pulse).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, base * (2.0f + 0.65f * pulse), paint)
    }
}

private class AgentShimmerLineView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var visualMode: AgentPanelVisualMode = AgentPanelVisualMode.Running
    private var progress: Float = 0f
    private var animator: ValueAnimator? = null

    fun setVisualMode(mode: AgentPanelVisualMode) {
        visualMode = mode
        invalidate()
    }

    fun startSweep() {
        if (animator?.isStarted == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                progress = valueAnimator.animatedValue as Float
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
        super.onDraw(canvas)
        val h = height.toFloat().coerceAtLeast(1f)
        val radius = h / 2f
        rect.set(0f, h * 0.28f, width.toFloat(), h * 0.72f)
        paint.shader = null
        paint.color = Color.argb(48, 225, 240, 255)
        canvas.drawRoundRect(rect, radius, radius, paint)
        val shimmerWidth = max(42f * density, width * 0.25f)
        val start = -shimmerWidth + progress * (width + shimmerWidth * 2f)
        val core = if (visualMode == AgentPanelVisualMode.Confirm) Color.argb(210, 255, 207, 120) else Color.argb(210, 135, 248, 255)
        paint.shader = LinearGradient(
            start,
            0f,
            start + shimmerWidth,
            0f,
            intArrayOf(Color.TRANSPARENT, core, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
    }
}
