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
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private var inputConversationScroll: ScrollView? = null
    private var inputConversationView: TextView? = null
    private var inputAwaitingView: TextView? = null
    private var inputPrivateHintView: TextView? = null
    private var inputEditText: EditText? = null
    private var inputButtonsRow: LinearLayout? = null
    private var inputPrimaryView: TextView? = null
    private var inputSecondaryView: TextView? = null

    private var stopView: TextView? = null
    private var collapseView: TextView? = null
    private var takeoverView: TextView? = null
    private var resumeView: TextView? = null
    private var contentGroup: LinearLayout? = null

    private val interactionTurns = mutableListOf<OverlayInteractionTurn>()
    private var lastInteractionRequestId: Long = 0L
    private var awaitingAgentReply: Boolean = false
    private var interactionRevision: Long = 0L

    private var density: Float = 1f
    private var expanded: Boolean = true
    private var latestProgress: AgentOverlayProgress = AgentOverlayProgress()

    private var lastRenderedModeTone: OverlayModeTone? = null
    private var lastRenderedLogs: List<String> = emptyList()
    private var lastRenderedConfirmation: AgentPendingConfirmation? = null
    private var lastRenderedInteractionKey: OverlayInteractionRenderKey? = null
    private var lastRenderedLayoutState: OverlayLayoutState? = null

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
            if (latestProgress.pendingConfirmation == null && latestProgress.pendingUserInput == null && !awaitingAgentReply) {
                expanded = !expanded
                lastRenderedLayoutState = null
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
                invalidateRenderCache()
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
        confirmMessageView = text("", 10.4f, Color.argb(232, 255, 244, 222)).applyReadable(maxLines = 8, lineSpacingExtraDp = 1f)
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
        inputTitleView = text("与 GUI Plus 沟通", 12.4f, Color.argb(255, 224, 244, 255), bold = true).applyReadable(maxLines = 1)
        inputConversationView = text("", 10.8f, Color.argb(238, 238, 247, 255)).apply {
            setLineSpacing(dp(1.4f).toFloat(), 1.04f)
            setTextIsSelectable(true)
            setPadding(dp(2f), dp(2f), dp(4f), dp(4f))
        }
        inputConversationScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            background = chipBackground(Color.argb(28, 255, 255, 255), Color.argb(42, 178, 224, 255), 16f)
            setPadding(dp(9f), dp(8f), dp(7f), dp(8f))
            addView(inputConversationView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        inputAwaitingView = text("GUI Plus 正在理解你的回复…", 10.2f, Color.argb(214, 183, 232, 255), bold = true).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2f), dp(7f), dp(2f), dp(2f))
        }
        inputPrivateHintView = text(
            "这是敏感输入。请在目标应用中手动完成密码、验证码或身份校验；App 不会读取或回传具体内容。完成后点击下方按钮。",
            10.2f,
            Color.argb(238, 255, 232, 188),
            bold = true,
        ).apply {
            visibility = View.GONE
            setLineSpacing(dp(1f).toFloat(), 1.03f)
            setPadding(dp(2f), dp(8f), dp(2f), dp(2f))
        }
        inputEditText = EditText(this).apply {
            textSize = 13.2f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(160, 232, 244, 255))
            hint = "可以分行说明你的选择、条件或补充信息"
            setSingleLine(false)
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(USER_REPLY_MAX_CHARS))
            setPadding(dp(11f), dp(9f), dp(11f), dp(9f))
            background = chipBackground(Color.argb(38, 255, 255, 255), Color.argb(66, 215, 235, 255), 16f)
        }
        inputSecondaryView = capsuleButton("停止任务", ButtonTone.GhostWarm) {
            AgentRuntimeController.cancelPendingUserInput()
        }
        inputPrimaryView = capsuleButton("发送给 GUI Plus", ButtonTone.PrimaryWarm) {
            submitPendingInteraction()
        }
        inputButtonsRow = LinearLayout(this).apply {
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
            addView(inputConversationScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(CONVERSATION_HEIGHT_DP)).apply {
                topMargin = dp(8f)
            })
            addView(inputAwaitingView)
            addView(inputPrivateHintView)
            addView(inputEditText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(INPUT_HEIGHT_DP)).apply {
                topMargin = dp(9f)
                bottomMargin = dp(10f)
            })
            addView(inputButtonsRow)
        }
    }

    private fun submitPendingInteraction() {
        val pending = latestProgress.pendingUserInput ?: return
        val submittedText = if (pending.sensitive) {
            PRIVATE_COMPLETION_TOKEN
        } else {
            inputEditText?.text?.toString()?.trim().orEmpty()
        }
        if (submittedText.isBlank()) return

        val visibleReply = if (pending.sensitive) {
            "已在目标应用中完成敏感输入"
        } else {
            submittedText.take(USER_REPLY_MAX_CHARS)
        }
        appendInteractionTurn(OverlayInteractionRole.User, visibleReply)
        inputEditText?.setText("")
        hideKeyboard()
        awaitingAgentReply = true
        AgentRuntimeController.submitPendingUserInput(submittedText)
        renderInteractionPanelIfNeeded(null, requestFocus = false)
        lastRenderedLayoutState = null
        refreshExpandedState()
    }

    private fun updateProgress(progress: AgentOverlayProgress) {
        val previous = latestProgress
        if (progress.running && !previous.running && progress.pendingUserInput == null) {
            resetInteractionConversation()
        }
        latestProgress = progress

        val pending = progress.pendingConfirmation
        val pendingInput = progress.pendingUserInput
        val newPendingInput = pendingInput != null && pendingInput.id != lastInteractionRequestId
        if (newPendingInput) {
            lastInteractionRequestId = pendingInput!!.id
            awaitingAgentReply = false
            appendInteractionTurn(OverlayInteractionRole.GuiPlus, pendingInput.message)
        }
        if (!progress.running && pendingInput == null && awaitingAgentReply) {
            awaitingAgentReply = false
            interactionRevision += 1L
        }

        val modeTone = when {
            pendingInput != null -> OverlayModeTone.PendingInput
            pending != null -> OverlayModeTone.PendingConfirmation
            awaitingAgentReply && progress.running -> OverlayModeTone.AwaitingReply
            progress.userTakeoverPaused -> OverlayModeTone.UserTakeover
            !progress.enabled -> OverlayModeTone.Disabled
            progress.running -> OverlayModeTone.Running
            else -> OverlayModeTone.Idle
        }
        val modeText = when (modeTone) {
            OverlayModeTone.PendingInput -> if (pendingInput?.sensitive == true) "隐私接管" else "待回复"
            OverlayModeTone.PendingConfirmation -> "待确认"
            OverlayModeTone.AwaitingReply -> "理解中"
            OverlayModeTone.UserTakeover -> "接管中"
            OverlayModeTone.Disabled -> "已关闭"
            OverlayModeTone.Running -> "执行中"
            OverlayModeTone.Idle -> progress.status.ifBlank { "待命" }
        }
        titleView.setTextIfChanged(progress.title)
        stateView.setTextIfChanged(modeText)
        if (lastRenderedModeTone != modeTone) {
            stateView?.background = modeBackground(modeTone)
            lastRenderedModeTone = modeTone
        }

        val actionText = pendingInput?.actionText
            ?: pending?.actionText?.ifBlank { "高风险动作确认" }
            ?: progress.currentAction.ifBlank { "等待任务" }
        actionView.setTextIfChanged(actionText.cleanOverlayText().limitOverlayText(ACTION_TEXT_LIMIT))
        resultView.setTextIfChanged(
            progress.lastResult.takeIf { it.isNotBlank() }
                ?.let { "结果：${it.cleanOverlayText().limitOverlayText(RESULT_TEXT_LIMIT)}" }
                ?: "结果：暂无执行结果"
        )
        latestView.setTextIfChanged(
            progress.logs.lastOrNull()
                ?.let { "最近：${it.cleanOverlayText().limitOverlayText(LATEST_TEXT_LIMIT)}" }
                ?: "最近：暂无运行日志"
        )
        if (progress.logs != lastRenderedLogs) {
            logsView.setTextIfChanged(buildLogText(progress.logs))
            lastRenderedLogs = progress.logs.toList()
        }
        stopView.setVisibilityIfChanged(
            if (progress.running || pending != null || pendingInput != null) View.VISIBLE else View.GONE
        )

        if (pending != null) {
            expanded = true
            if (pending != lastRenderedConfirmation) {
                confirmTitleView.setTextIfChanged(pending.title)
                confirmMessageView.setTextIfChanged(
                    pending.message.cleanPanelText().limitOverlayText(CONFIRM_MESSAGE_TEXT_LIMIT)
                )
                confirmPrimaryView.setTextIfChanged(pending.positiveText)
                confirmSecondaryView.setTextIfChanged(pending.negativeText)
                lastRenderedConfirmation = pending
            }
        } else {
            lastRenderedConfirmation = null
        }
        if (pendingInput != null || interactionTurns.isNotEmpty()) {
            expanded = true
            renderInteractionPanelIfNeeded(pendingInput, requestFocus = newPendingInput)
        }
        refreshExpandedState()
    }

    private fun renderInteractionPanelIfNeeded(
        pendingInput: AgentPendingUserInput?,
        requestFocus: Boolean,
    ) {
        val key = OverlayInteractionRenderKey(
            pendingInput = pendingInput,
            awaitingAgentReply = awaitingAgentReply,
            revision = interactionRevision,
        )
        if (key == lastRenderedInteractionKey) return

        inputTitleView.setTextIfChanged(
            when {
                pendingInput?.sensitive == true -> "需要你完成隐私操作"
                pendingInput != null -> pendingInput.title.ifBlank { "与 GUI Plus 沟通" }
                awaitingAgentReply -> "与 GUI Plus 沟通"
                else -> "GUI Plus 对话记录"
            }
        )
        val transcript = buildInteractionTranscript()
        val transcriptChanged = inputConversationView?.text?.toString() != transcript
        inputConversationView.setTextIfChanged(transcript)
        inputAwaitingView.setVisibilityIfChanged(
            if (awaitingAgentReply && pendingInput == null) View.VISIBLE else View.GONE
        )
        inputPrivateHintView.setVisibilityIfChanged(
            if (pendingInput?.sensitive == true) View.VISIBLE else View.GONE
        )
        inputEditText.setVisibilityIfChanged(
            if (pendingInput != null && !pendingInput.sensitive) View.VISIBLE else View.GONE
        )
        inputButtonsRow.setVisibilityIfChanged(if (pendingInput != null) View.VISIBLE else View.GONE)
        val hint = pendingInput?.hint?.takeIf { it.isNotBlank() }
            ?: "可以分行说明你的选择、条件或补充信息"
        if (inputEditText?.hint?.toString() != hint) inputEditText?.hint = hint
        inputPrimaryView.setTextIfChanged(
            if (pendingInput?.sensitive == true) "已完成，继续" else "发送给 GUI Plus"
        )
        inputSecondaryView.setTextIfChanged(
            pendingInput?.negativeText?.takeIf { it.isNotBlank() } ?: "停止任务"
        )
        if (requestFocus && pendingInput != null && !pendingInput.sensitive) requestInputFocus()
        if (transcriptChanged) {
            inputConversationScroll?.post { inputConversationScroll?.fullScroll(View.FOCUS_DOWN) }
        }
        lastRenderedInteractionKey = key
    }

    private fun appendInteractionTurn(role: OverlayInteractionRole, text: String) {
        val clean = text.cleanPanelText().take(INTERACTION_TURN_TEXT_LIMIT)
        if (clean.isBlank()) return
        val last = interactionTurns.lastOrNull()
        if (last?.role == role && last.text == clean) return
        interactionTurns += OverlayInteractionTurn(role, clean)
        while (interactionTurns.size > MAX_INTERACTION_TURNS) interactionTurns.removeAt(0)
        interactionRevision += 1L
        lastRenderedInteractionKey = null
        lastRenderedLayoutState = null
    }

    private fun buildInteractionTranscript(): String {
        if (interactionTurns.isEmpty()) return "等待 GUI Plus 发来问题…"
        return interactionTurns.joinToString("\n\n") { turn ->
            val speaker = if (turn.role == OverlayInteractionRole.GuiPlus) "GUI Plus" else "你"
            "$speaker\n${turn.text}"
        }
    }

    private fun resetInteractionConversation() {
        val changed = interactionTurns.isNotEmpty() || lastInteractionRequestId != 0L || awaitingAgentReply
        interactionTurns.clear()
        lastInteractionRequestId = 0L
        awaitingAgentReply = false
        inputEditText?.setText("")
        if (changed) interactionRevision += 1L
        lastRenderedInteractionKey = null
        lastRenderedLayoutState = null
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
        val hasInteraction = interactionTurns.isNotEmpty()
        val interactionActive = hasInteraction && (pendingInput != null || awaitingAgentReply || latestProgress.running)
        val forceExpanded = pending != null || pendingInput != null || paused || interactionActive
        val shouldExpand = expanded || forceExpanded
        val showLogs = shouldExpand && !hasInteraction && latestProgress.logs.isNotEmpty() && !hidden
        val state = OverlayLayoutState(
            showContent = shouldExpand && !hidden,
            showConfirmation = shouldExpand && pending != null && !hidden,
            showInput = shouldExpand && hasInteraction && !hidden,
            showLogs = showLogs,
            showCollapse = pending == null && pendingInput == null && !paused && !awaitingAgentReply,
            showTakeover = latestProgress.running && !paused && pending == null && pendingInput == null && !awaitingAgentReply,
            showResume = paused,
            collapseText = if (shouldExpand) "收起" else "展开",
            hidden = hidden,
            targetWidth = if (shouldExpand || forceExpanded) dp(EXPANDED_WIDTH_DP) else dp(COMPACT_WIDTH_DP),
        )
        if (state == lastRenderedLayoutState) return

        contentGroup.setVisibilityIfChanged(if (state.showContent) View.VISIBLE else View.GONE)
        confirmPanel.setVisibilityIfChanged(if (state.showConfirmation) View.VISIBLE else View.GONE)
        inputPanel.setVisibilityIfChanged(if (state.showInput) View.VISIBLE else View.GONE)
        logsCard.setVisibilityIfChanged(if (state.showLogs) View.VISIBLE else View.GONE)
        logsView.setVisibilityIfChanged(if (state.showLogs) View.VISIBLE else View.GONE)
        collapseView.setVisibilityIfChanged(if (state.showCollapse) View.VISIBLE else View.GONE)
        takeoverView.setVisibilityIfChanged(if (state.showTakeover) View.VISIBLE else View.GONE)
        resumeView.setVisibilityIfChanged(if (state.showResume) View.VISIBLE else View.GONE)
        collapseView.setTextIfChanged(state.collapseText)
        updateWindowMode(state.hidden)
        updateWindowWidth(state.targetWidth)
        lastRenderedLayoutState = state
    }

    private fun updateWindowWidth(targetWidth: Int) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        if (params.width == targetWidth) return
        params.width = targetWidth
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun overlayWindowFlags(touchThrough: Boolean, wantsInputFocus: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!wantsInputFocus) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (touchThrough) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun updateWindowMode(touchThrough: Boolean) {
        val params = layoutParams ?: return
        val view = rootView ?: return
        val pendingInput = latestProgress.pendingUserInput
        val wantsInputFocus = pendingInput != null && !pendingInput.sensitive && !touchThrough
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
            lastRenderedLayoutState = null
            refreshExpandedState()
        }
    }

    private fun requestInputFocus() {
        val edit = inputEditText ?: return
        edit.post {
            edit.requestFocus()
            edit.setSelection(edit.text?.length ?: 0)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val edit = inputEditText ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(edit.windowToken, 0)
        edit.clearFocus()
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

    private fun modeBackground(tone: OverlayModeTone): GradientDrawable = when (tone) {
        OverlayModeTone.PendingInput ->
            chipBackground(Color.argb(78, 80, 168, 230), Color.argb(132, 148, 232, 255), 14f)
        OverlayModeTone.PendingConfirmation ->
            chipBackground(Color.argb(88, 255, 184, 90), Color.argb(132, 255, 224, 135), 14f)
        OverlayModeTone.AwaitingReply ->
            chipBackground(Color.argb(72, 92, 170, 220), Color.argb(96, 166, 224, 255), 14f)
        OverlayModeTone.UserTakeover ->
            chipBackground(Color.argb(70, 255, 210, 104), Color.argb(110, 255, 230, 150), 14f)
        OverlayModeTone.Running ->
            chipBackground(Color.argb(72, 95, 255, 218), Color.argb(86, 164, 255, 232), 14f)
        OverlayModeTone.Disabled ->
            chipBackground(Color.argb(48, 195, 202, 218), Color.argb(54, 214, 224, 242), 14f)
        OverlayModeTone.Idle ->
            chipBackground(Color.argb(42, 214, 228, 255), Color.argb(48, 235, 248, 255), 14f)
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
            intArrayOf(Color.argb(226, 14, 24, 52), Color.argb(206, 32, 50, 92), Color.argb(224, 14, 18, 48)),
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

    private fun String.cleanPanelText(): String {
        return replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { line -> line.trim().replace(Regex("[\\t ]+"), " ") }
            .trim()
    }

    private fun String.limitOverlayText(limit: Int): String {
        if (length <= limit) return this
        return take((limit - 1).coerceAtLeast(1)).trimEnd() + "…"
    }

    private fun TextView?.setTextIfChanged(value: CharSequence) {
        val view = this ?: return
        if (!TextUtils.equals(view.text, value)) view.text = value
    }

    private fun View?.setVisibilityIfChanged(value: Int) {
        val view = this ?: return
        if (view.visibility != value) view.visibility = value
    }

    private fun invalidateRenderCache() {
        lastRenderedModeTone = null
        lastRenderedLogs = emptyList()
        lastRenderedConfirmation = null
        lastRenderedInteractionKey = null
        lastRenderedLayoutState = null
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
                        if (latestProgress.pendingConfirmation == null && latestProgress.pendingUserInput == null && !awaitingAgentReply) {
                            expanded = !expanded
                            lastRenderedLayoutState = null
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
        private const val CONVERSATION_HEIGHT_DP = 176f
        private const val INPUT_HEIGHT_DP = 86f
        private const val ACTION_TEXT_LIMIT = 120
        private const val RESULT_TEXT_LIMIT = 180
        private const val LATEST_TEXT_LIMIT = 120
        private const val LOG_LINE_TEXT_LIMIT = 260
        private const val CONFIRM_MESSAGE_TEXT_LIMIT = 800
        private const val OVERLAY_LOG_LINES = 24
        private const val MAX_INTERACTION_TURNS = 16
        private const val INTERACTION_TURN_TEXT_LIMIT = 2_400
        private const val USER_REPLY_MAX_CHARS = 2_000
        private const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"
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

private data class OverlayInteractionTurn(
    val role: OverlayInteractionRole,
    val text: String,
)

private data class OverlayInteractionRenderKey(
    val pendingInput: AgentPendingUserInput?,
    val awaitingAgentReply: Boolean,
    val revision: Long,
)

private data class OverlayLayoutState(
    val showContent: Boolean,
    val showConfirmation: Boolean,
    val showInput: Boolean,
    val showLogs: Boolean,
    val showCollapse: Boolean,
    val showTakeover: Boolean,
    val showResume: Boolean,
    val collapseText: String,
    val hidden: Boolean,
    val targetWidth: Int,
)

private enum class OverlayInteractionRole { GuiPlus, User }
private enum class OverlayModeTone { PendingInput, PendingConfirmation, AwaitingReply, UserTakeover, Running, Disabled, Idle }
private enum class ButtonTone { Ghost, Danger, PrimaryWarm, GhostWarm }
