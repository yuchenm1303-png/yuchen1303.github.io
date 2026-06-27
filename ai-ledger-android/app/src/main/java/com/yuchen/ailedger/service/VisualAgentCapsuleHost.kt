package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.min

private data class CapsuleRenderSnapshot(
    val progress: AgentOverlayProgress,
    val target: VisualAgentHudTarget?,
    val hiddenForCapture: Boolean,
)

private enum class CapsuleSpeaker { GuiPlus, User }

private data class CapsuleConversationTurn(
    val speaker: CapsuleSpeaker,
    val text: String,
)

/**
 * Interactive accessibility-overlay companion for [VisualAgentHudHost].
 *
 * The full-screen visual WebView remains completely touch-through. This host owns a second,
 * tightly-sized TYPE_ACCESSIBILITY_OVERLAY window, so only the visible capsule/dialogue surface
 * receives touch and GUI Plus communication no longer needs SYSTEM_ALERT_WINDOW permission.
 */
internal class VisualAgentCapsuleHost(
    private val service: AccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val density = service.resources.displayMetrics.density.coerceAtLeast(1f)

    private var rootView: CapsuleGlassLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var layoutAnimator: ValueAnimator? = null

    private var titleView: TextView? = null
    private var metaView: TextView? = null
    private var statusOrb: CapsuleStatusOrbView? = null
    private var chevronView: CapsuleChevronView? = null
    private var pauseResumeView: CapsulePauseResumeView? = null
    private var bodyView: LinearLayout? = null
    private var actionView: TextView? = null
    private var resultView: TextView? = null
    private var conversationView: TextView? = null
    private var conversationScroll: ScrollView? = null
    private var promptView: TextView? = null
    private var inputView: EditText? = null
    private var primaryButton: TextView? = null
    private var secondaryButton: TextView? = null

    private var started = false
    private var hiddenForCapture = false
    private var expanded = false
    private var lastAppliedExpanded = false
    private var inputFocused = false
    private var currentTaskId = 0L
    private var lastPendingInputId = 0L
    private var lastPendingConfirmationId = 0L
    private var lastAutoExpandKey: String? = null
    private var lastModelDialogue = ""
    private var wasPaused = false
    private var awaitingAgentReply = false
    private var latestProgress = AgentOverlayProgress()
    private var latestPresentation = VisualAgentCapsuleStateResolver.resolve(latestProgress, null)
    private val conversationTurns = mutableListOf<CapsuleConversationTurn>()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                AgentRuntimeController.progress,
                VisualAgentHudRuntime.target,
                AgentRuntimeController.overlayHiddenForCapture,
            ) { progress, target, hidden ->
                CapsuleRenderSnapshot(progress, target, hidden)
            }.collect(::render)
        }
    }

    fun destroy() {
        if (!started && rootView == null) return
        started = false
        scope.cancel()
        destroyWindow()
    }

    private fun render(snapshot: CapsuleRenderSnapshot) {
        val presentation = VisualAgentCapsuleStateResolver.resolve(snapshot.progress, snapshot.target)
        if (!presentation.active) {
            if (rootView != null) destroyWindow()
            resetConversation(0L)
            return
        }

        latestProgress = snapshot.progress
        latestPresentation = presentation
        if (snapshot.progress.taskId != currentTaskId) {
            resetConversation(snapshot.progress.taskId)
            expanded = false
            lastAppliedExpanded = false
        }

        syncConversation(snapshot.progress)
        presentation.autoExpandKey?.let { key ->
            if (key != lastAutoExpandKey) {
                lastAutoExpandKey = key
                expanded = true
            }
        }

        if (!createWindow()) return
        updateHeader(presentation)
        updateBody(snapshot.progress, presentation)
        setHiddenForCapture(snapshot.hiddenForCapture)
        applyExpandedLayout(animate = !snapshot.hiddenForCapture)
    }

    private fun createWindow(): Boolean {
        if (rootView != null) return true
        val wm = windowManager ?: return false
        val screenWidth = service.resources.displayMetrics.widthPixels
        val collapsedWidth = min(dp(COLLAPSED_WIDTH_DP), screenWidth - dp(20f))
        val collapsedHeight = dp(COLLAPSED_HEIGHT_DP)

        val root = CapsuleGlassLayout(service).apply {
            elevation = dp(18f).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "视觉智能体灵动胶囊"
        }
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val header = createHeader()
        val body = createBody()
        bodyView = body
        content.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, collapsedHeight),
        )
        content.addView(
            body,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val params = WindowManager.LayoutParams(
            collapsedWidth,
            collapsedHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            capsuleWindowFlags(hidden = hiddenForCapture, wantsInputFocus = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topWindowInsetPx()
            alpha = 1f
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

        return runCatching { wm.addView(root, params) }
            .onSuccess {
                rootView = root
                layoutParams = params
                root.setExpansionProgress(0f)
                root.setHintEnabled(!hiddenForCapture)
                body.visibility = View.INVISIBLE
                animateWindowEntrance(root)
            }
            .onFailure { error ->
                root.stopAnimations()
                AgentRuntimeController.noteDiagnostic(
                    "灵动胶囊创建失败：${error.message ?: error.javaClass.simpleName}"
                )
            }
            .isSuccess
    }

    private fun createHeader(): View {
        val header = FrameLayout(service).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "展开或收起 GUI Plus 对话"
            setOnClickListener { setExpanded(!expanded, animate = true) }
            setOnTouchListener(HeaderPressFeedback())
        }
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13f), dp(4f), dp(7f), dp(5f))
        }
        statusOrb = CapsuleStatusOrbView(service).also { orb ->
            row.addView(orb, LinearLayout.LayoutParams(dp(12f), dp(12f)).apply {
                marginEnd = dp(8f)
            })
        }
        titleView = text("正在观察页面", 12.1f, Color.WHITE, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }.also { title ->
            row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        metaView = text("Step 1 / 5", 9.8f, Color.argb(182, 203, 219, 234)).also { meta ->
            row.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(7f)
            })
        }
        chevronView = CapsuleChevronView(service).also { chevron ->
            row.addView(chevron, LinearLayout.LayoutParams(dp(20f), dp(28f)).apply {
                marginStart = dp(2f)
            })
        }
        row.addView(
            View(service).apply { setBackgroundColor(Color.argb(36, 232, 246, 255)) },
            LinearLayout.LayoutParams(dp(1f).coerceAtLeast(1), dp(23f)).apply {
                marginStart = dp(1f)
                marginEnd = dp(5f)
            },
        )
        pauseResumeView = CapsulePauseResumeView(service).apply {
            contentDescription = "暂停智能体"
            setOnClickListener {
                if (!latestPresentation.canPauseOrResume) return@setOnClickListener
                if (latestProgress.userTakeoverPaused) {
                    AgentRuntimeController.resumeFromUserTakeover()
                } else {
                    AgentRuntimeController.pauseForUserTakeover()
                }
            }
        }.also { control ->
            row.addView(control, LinearLayout.LayoutParams(dp(34f), dp(34f)))
        }
        header.addView(
            row,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        header.addView(
            View(service).apply {
                background = roundedBackground(
                    fill = Color.argb(174, 172, 226, 255),
                    stroke = Color.TRANSPARENT,
                    radiusDp = 2f,
                )
            },
            FrameLayout.LayoutParams(dp(15f), dp(2f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(3f)
            },
        )
        return header
    }

    private fun createBody(): LinearLayout {
        val body = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13f), dp(4f), dp(13f), dp(11f))
            alpha = 0f
            translationY = -dp(8f).toFloat()
        }
        val actionCard = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11f), dp(8f), dp(11f), dp(8f))
            background = roundedBackground(
                fill = Color.argb(34, 239, 248, 255),
                stroke = Color.argb(45, 188, 230, 255),
                radiusDp = 17f,
            )
        }
        actionCard.addView(sectionLabel("当前状态"))
        actionView = text("等待任务", 12f, Color.WHITE, bold = true).applyReadable(2).also {
            actionCard.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
            })
        }
        resultView = text("GUI Plus 正在准备下一步操作", 10f, Color.argb(218, 221, 237, 249)).applyReadable(3).also {
            actionCard.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
            })
        }
        body.addView(actionCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        body.addView(
            sectionLabel("与 GUI Plus 沟通"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
                bottomMargin = dp(4f)
            },
        )
        conversationView = text("等待 GUI Plus 发来消息…", 10.4f, Color.argb(232, 235, 245, 255)).apply {
            setLineSpacing(dp(1.2f).toFloat(), 1.04f)
            setTextIsSelectable(true)
            setPadding(dp(9f), dp(7f), dp(9f), dp(7f))
        }
        conversationScroll = ScrollView(service).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = roundedBackground(
                fill = Color.argb(26, 255, 255, 255),
                stroke = Color.argb(40, 168, 222, 255),
                radiusDp = 15f,
            )
            addView(
                conversationView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        body.addView(
            conversationScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CONVERSATION_HEIGHT_DP)),
        )

        promptView = text("", 10.2f, Color.argb(244, 255, 232, 190), bold = true).applyReadable(6).apply {
            visibility = View.GONE
            setPadding(dp(9f), dp(7f), dp(9f), dp(7f))
            background = roundedBackground(
                fill = Color.argb(48, 255, 190, 92),
                stroke = Color.argb(74, 255, 224, 148),
                radiusDp = 14f,
            )
        }
        body.addView(
            promptView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(7f)
            },
        )

        inputView = EditText(service).apply {
            textSize = 12.4f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 221, 238, 250))
            hint = "补充刚才的操作或下一步要求"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(USER_REPLY_MAX_CHARS))
            setPadding(dp(11f), dp(8f), dp(11f), dp(8f))
            background = roundedBackground(
                fill = Color.argb(36, 255, 255, 255),
                stroke = Color.argb(66, 194, 231, 255),
                radiusDp = 15f,
            )
            visibility = View.GONE
            setOnTouchListener { _, _ ->
                enableInputFocus()
                false
            }
        }
        body.addView(
            inputView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_HEIGHT_DP)).apply {
                topMargin = dp(7f)
            },
        )

        val actionRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        secondaryButton = actionButton("收起", primary = false) { handleSecondaryAction() }.also {
            actionRow.addView(it, LinearLayout.LayoutParams(0, dp(38f), 1f).apply {
                marginEnd = dp(8f)
            })
        }
        primaryButton = actionButton("暂停并沟通", primary = true) { handlePrimaryAction() }.also {
            actionRow.addView(it, LinearLayout.LayoutParams(0, dp(38f), 1f))
        }
        body.addView(
            actionRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8f)
            },
        )
        body.addView(
            text("停止本次任务", 9.7f, Color.argb(202, 255, 184, 213), bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8f), dp(6f), dp(8f), dp(5f))
                isClickable = true
                setOnClickListener { AgentRuntimeController.stopTaskByUser() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
            },
        )
        return body
    }

    private fun updateHeader(presentation: VisualAgentCapsulePresentation) {
        animateTextSwap(titleView, presentation.title)
        metaView.setTextIfChanged(presentation.meta)
        val accent = accentColorFor(presentation)
        rootView?.setAccentColor(accent)
        statusOrb?.setAccentColor(accent)
        pauseResumeView?.setAccentColor(accent)
        pauseResumeView?.setPaused(presentation.paused)
        pauseResumeView?.setInteractionEnabled(presentation.canPauseOrResume)
        pauseResumeView?.contentDescription = when {
            !presentation.canPauseOrResume -> "当前正在等待用户处理"
            presentation.paused -> "恢复智能体执行"
            else -> "暂停智能体"
        }
    }

    private fun updateBody(
        progress: AgentOverlayProgress,
        presentation: VisualAgentCapsulePresentation,
    ) {
        actionView.setTextIfChanged(progress.currentAction.ifBlank { presentation.title })
        resultView.setTextIfChanged(
            progress.lastResult.takeIf(String::isNotBlank)
                ?: progress.logs.lastOrNull()?.cleanCapsuleText()
                ?: "GUI Plus 正在根据页面证据规划下一步。"
        )

        val transcript = buildConversationTranscript(progress)
        val transcriptChanged = conversationView?.text?.toString() != transcript
        conversationView.setTextIfChanged(transcript)

        val pendingInput = progress.pendingUserInput
        val pendingConfirmation = progress.pendingConfirmation
        val prompt = when {
            pendingConfirmation != null -> pendingConfirmation.message
            pendingInput?.sensitive == true ->
                "请在目标应用中亲自完成密码、验证码或身份校验。智能体不会读取或回传具体内容，完成后点击“已完成，继续”。"
            else -> ""
        }
        promptView.setTextIfChanged(prompt)
        promptView.setVisibilityIfChanged(if (prompt.isBlank()) View.GONE else View.VISIBLE)
        inputView.setVisibilityIfChanged(if (presentation.showConversationInput) View.VISIBLE else View.GONE)
        inputView?.hint = when {
            progress.userTakeoverPaused -> "告诉 GUI Plus 你刚才做了什么，或下一步希望怎样处理"
            else -> pendingInput?.hint?.takeIf(String::isNotBlank) ?: "补充选择、条件或要求"
        }
        if (!presentation.showConversationInput && inputFocused) hideKeyboardAndReleaseFocus()

        primaryButton.setTextIfChanged(
            when {
                pendingConfirmation != null -> pendingConfirmation.positiveText
                presentation.showSensitiveCompletion -> "已完成，继续"
                pendingInput != null -> pendingInput.positiveText
                progress.userTakeoverPaused -> "发送给 GUI Plus"
                else -> "暂停并沟通"
            }
        )
        secondaryButton.setTextIfChanged(
            when {
                pendingConfirmation != null -> pendingConfirmation.negativeText
                pendingInput != null -> pendingInput.negativeText
                progress.userTakeoverPaused -> "恢复执行"
                else -> "收起"
            }
        )
        if (transcriptChanged) {
            conversationScroll?.post { conversationScroll?.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun syncConversation(progress: AgentOverlayProgress) {
        val modelDialogue = latestModelDialogue(progress.logs)
        if (modelDialogue.isNotBlank() && modelDialogue != lastModelDialogue) {
            lastModelDialogue = modelDialogue
            awaitingAgentReply = false
            appendTurn(CapsuleSpeaker.GuiPlus, modelDialogue)
        }
        progress.pendingUserInput?.let { pending ->
            if (pending.id != lastPendingInputId) {
                lastPendingInputId = pending.id
                awaitingAgentReply = false
                appendTurn(CapsuleSpeaker.GuiPlus, pending.message)
            }
        }
        progress.pendingConfirmation?.let { pending ->
            if (pending.id != lastPendingConfirmationId) {
                lastPendingConfirmationId = pending.id
                appendTurn(CapsuleSpeaker.GuiPlus, pending.message)
            }
        }
        if (progress.userTakeoverPaused && !wasPaused) {
            appendTurn(
                CapsuleSpeaker.GuiPlus,
                "自动操作已暂停。你可以手动处理当前页面，并把页面变化或下一步要求发给我。",
            )
        } else if (!progress.userTakeoverPaused && wasPaused && progress.running) {
            appendTurn(CapsuleSpeaker.GuiPlus, "已恢复自动执行，接管期间的补充会优先进入下一轮规划。")
        }
        if (!progress.running && progress.pendingUserInput == null) awaitingAgentReply = false
        wasPaused = progress.userTakeoverPaused
    }

    private fun handlePrimaryAction() {
        val progress = latestProgress
        val pendingConfirmation = progress.pendingConfirmation
        val pendingInput = progress.pendingUserInput
        when {
            pendingConfirmation != null -> AgentRuntimeController.confirmPendingRiskAction()
            pendingInput?.sensitive == true -> {
                appendTurn(CapsuleSpeaker.User, "已在目标应用中完成敏感输入")
                AgentRuntimeController.submitPendingUserInput(PRIVATE_COMPLETION_TOKEN)
            }
            pendingInput != null -> submitPendingInput()
            progress.userTakeoverPaused -> submitTakeoverGuidance()
            progress.running -> AgentRuntimeController.pauseForUserTakeover()
        }
    }

    private fun handleSecondaryAction() {
        val progress = latestProgress
        when {
            progress.pendingConfirmation != null -> AgentRuntimeController.cancelPendingRiskAction()
            progress.pendingUserInput != null -> AgentRuntimeController.cancelPendingUserInput()
            progress.userTakeoverPaused -> AgentRuntimeController.resumeFromUserTakeover()
            else -> setExpanded(false, animate = true)
        }
    }

    private fun submitPendingInput() {
        val value = inputView?.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        appendTurn(CapsuleSpeaker.User, value)
        inputView?.setText("")
        awaitingAgentReply = true
        hideKeyboardAndReleaseFocus()
        AgentRuntimeController.submitPendingUserInput(value)
    }

    private fun submitTakeoverGuidance() {
        val value = inputView?.text?.toString()?.trim().orEmpty()
        if (value.isBlank() || !AgentTakeoverDialogueBridge.submit(value)) return
        appendTurn(CapsuleSpeaker.User, value)
        appendTurn(
            CapsuleSpeaker.GuiPlus,
            "补充已加入下一轮规划上下文。完成手动操作后，点击右上角播放按钮或“恢复执行”。",
        )
        inputView?.setText("")
        hideKeyboardAndReleaseFocus()
        updateBody(latestProgress, latestPresentation)
    }

    private fun appendTurn(speaker: CapsuleSpeaker, raw: String) {
        val value = raw.cleanCapsulePanelText().take(MAX_TURN_TEXT_CHARS)
        if (value.isBlank()) return
        val last = conversationTurns.lastOrNull()
        if (last?.speaker == speaker && last.text == value) return
        conversationTurns += CapsuleConversationTurn(speaker, value)
        while (conversationTurns.size > MAX_CONVERSATION_TURNS) conversationTurns.removeAt(0)
    }

    private fun buildConversationTranscript(progress: AgentOverlayProgress): String {
        if (conversationTurns.isEmpty()) {
            return buildString {
                append("GUI Plus\n")
                append(
                    progress.lastResult.takeIf(String::isNotBlank)
                        ?: "正在执行任务。点击右侧暂停按钮，可以随时接管并补充要求。"
                )
            }
        }
        return conversationTurns.joinToString("\n\n") { turn ->
            val speaker = if (turn.speaker == CapsuleSpeaker.GuiPlus) "GUI Plus" else "你"
            "$speaker\n${turn.text}"
        } + if (awaitingAgentReply) "\n\nGUI Plus\n正在理解你的回复…" else ""
    }

    private fun latestModelDialogue(logs: List<String>): String {
        val tail = logs.takeLast(14)
        if (tail.lastOrNull()?.let { it.startsWith("模型：") || it.startsWith("模型续：") } != true) return ""
        val reversed = tail.asReversed().takeWhile { it.startsWith("模型：") || it.startsWith("模型续：") }
        return reversed.asReversed().joinToString("") {
            it.removePrefix("模型：").removePrefix("模型续：").trim()
        }.cleanCapsulePanelText()
    }

    private fun resetConversation(taskId: Long) {
        currentTaskId = taskId
        lastPendingInputId = 0L
        lastPendingConfirmationId = 0L
        lastAutoExpandKey = null
        lastModelDialogue = ""
        wasPaused = false
        awaitingAgentReply = false
        conversationTurns.clear()
        inputView?.setText("")
    }

    private fun setExpanded(value: Boolean, animate: Boolean) {
        if (expanded == value && lastAppliedExpanded == value) return
        expanded = value
        if (!value) hideKeyboardAndReleaseFocus()
        applyExpandedLayout(animate)
    }

    private fun applyExpandedLayout(animate: Boolean) {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val collapsedWidth = min(dp(COLLAPSED_WIDTH_DP), screenWidth - dp(20f))
        val expandedWidth = min(dp(EXPANDED_WIDTH_DP), screenWidth - dp(20f))
        val collapsedHeight = dp(COLLAPSED_HEIGHT_DP)
        val maxHeight = (screenHeight - topWindowInsetPx() - dp(18f)).coerceAtLeast(collapsedHeight)
        val expandedHeight = min(dp(expandedHeightDp(latestPresentation)), maxHeight)
        val targetWidth = if (expanded) expandedWidth else collapsedWidth
        val targetHeight = if (expanded) expandedHeight else collapsedHeight

        if (expanded) bodyView?.visibility = View.VISIBLE
        val needsAnimation = animate && !hiddenForCapture &&
            (params.width != targetWidth || params.height != targetHeight || lastAppliedExpanded != expanded)
        if (!needsAnimation) {
            layoutAnimator?.cancel()
            params.width = targetWidth
            params.height = targetHeight
            runCatching { windowManager?.updateViewLayout(root, params) }
            val progress = if (expanded) 1f else 0f
            root.setExpansionProgress(progress)
            chevronView?.setExpansionProgress(progress)
            bodyView?.alpha = progress
            bodyView?.translationY = -dp(8f).toFloat() * (1f - progress)
            if (!expanded) bodyView?.visibility = View.INVISIBLE
            lastAppliedExpanded = expanded
            root.setHintEnabled(!expanded && !hiddenForCapture)
            return
        }

        layoutAnimator?.cancel()
        val startWidth = params.width
        val startHeight = params.height
        val startExpansion = root.expansionProgress
        val endExpansion = if (expanded) 1f else 0f
        layoutAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (expanded) EXPAND_DURATION_MS else COLLAPSE_DURATION_MS
            interpolator = if (expanded) EXPAND_INTERPOLATOR else COLLAPSE_INTERPOLATOR
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                params.width = lerp(startWidth, targetWidth, fraction)
                params.height = lerp(startHeight, targetHeight, fraction)
                runCatching { windowManager?.updateViewLayout(root, params) }
                val expansion = lerp(startExpansion, endExpansion, fraction)
                root.setExpansionProgress(expansion)
                chevronView?.setExpansionProgress(expansion)
                val bodyProgress = if (expanded) {
                    ((fraction - 0.14f) / 0.64f).coerceIn(0f, 1f)
                } else {
                    (1f - fraction / 0.44f).coerceIn(0f, 1f)
                }
                bodyView?.alpha = bodyProgress
                bodyView?.translationY = -dp(8f).toFloat() * (1f - bodyProgress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!expanded) bodyView?.visibility = View.INVISIBLE
                    lastAppliedExpanded = expanded
                    root.setHintEnabled(!expanded && !hiddenForCapture)
                }
            })
            start()
        }
    }

    private fun expandedHeightDp(presentation: VisualAgentCapsulePresentation): Float = when (presentation.mode) {
        VisualAgentCapsuleMode.PendingInput -> if (presentation.showSensitiveCompletion) 430f else 448f
        VisualAgentCapsuleMode.PendingConfirmation -> 420f
        VisualAgentCapsuleMode.UserTakeover -> 448f
        VisualAgentCapsuleMode.Running -> 342f
        VisualAgentCapsuleMode.Idle -> 310f
    }

    private fun setHiddenForCapture(hidden: Boolean) {
        val root = rootView ?: run {
            hiddenForCapture = hidden
            return
        }
        if (hiddenForCapture == hidden && root.visibility == if (hidden) View.INVISIBLE else View.VISIBLE) return
        hiddenForCapture = hidden
        root.animate().cancel()
        layoutAnimator?.cancel()
        updateWindowFlags(hidden = hidden, wantsInputFocus = inputFocused && !hidden)
        if (hidden) {
            root.setHintEnabled(false)
            root.alpha = 0f
            root.visibility = View.INVISIBLE
        } else {
            root.visibility = View.VISIBLE
            root.alpha = 1f
            root.setHintEnabled(!expanded)
            applyExpandedLayout(animate = false)
        }
    }

    private fun enableInputFocus() {
        if (inputFocused || hiddenForCapture) return
        inputFocused = true
        updateWindowFlags(hidden = false, wantsInputFocus = true)
        inputView?.post {
            val edit = inputView ?: return@post
            edit.requestFocus()
            edit.setSelection(edit.text?.length ?: 0)
            val manager = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboardAndReleaseFocus() {
        val edit = inputView
        if (edit != null) {
            val manager = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.hideSoftInputFromWindow(edit.windowToken, 0)
            edit.clearFocus()
        }
        inputFocused = false
        updateWindowFlags(hidden = hiddenForCapture, wantsInputFocus = false)
    }

    private fun updateWindowFlags(hidden: Boolean, wantsInputFocus: Boolean) {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val flags = capsuleWindowFlags(hidden, wantsInputFocus)
        if (params.flags == flags) return
        params.flags = flags
        runCatching { windowManager?.updateViewLayout(root, params) }
    }

    private fun capsuleWindowFlags(hidden: Boolean, wantsInputFocus: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!wantsInputFocus) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (hidden) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun destroyWindow() {
        layoutAnimator?.cancel()
        layoutAnimator = null
        hideKeyboardAndReleaseFocus()
        rootView?.stopAnimations()
        rootView?.animate()?.cancel()
        rootView?.let { runCatching { windowManager?.removeView(it) } }
        rootView = null
        layoutParams = null
        titleView = null
        metaView = null
        statusOrb = null
        chevronView = null
        pauseResumeView = null
        bodyView = null
        actionView = null
        resultView = null
        conversationView = null
        conversationScroll = null
        promptView = null
        inputView = null
        primaryButton = null
        secondaryButton = null
        inputFocused = false
        expanded = false
        lastAppliedExpanded = false
    }

    private fun animateWindowEntrance(view: View) {
        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.translationY = -dp(7f).toFloat()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(280L)
            .setInterpolator(SOFT_OUT)
            .start()
    }

    private fun animateTextSwap(view: TextView?, value: String) {
        val target = view ?: return
        if (target.text?.toString() == value) return
        target.animate().cancel()
        target.animate()
            .alpha(0f)
            .translationY(-dp(3f).toFloat())
            .setDuration(70L)
            .withEndAction {
                target.text = value
                target.translationY = dp(4f).toFloat()
                target.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(125L)
                    .setInterpolator(SOFT_OUT)
                    .start()
            }
            .start()
    }

    private fun accentColorFor(presentation: VisualAgentCapsulePresentation): Int = when (presentation.mode) {
        VisualAgentCapsuleMode.PendingInput -> Color.rgb(116, 218, 255)
        VisualAgentCapsuleMode.PendingConfirmation -> Color.rgb(255, 201, 108)
        VisualAgentCapsuleMode.UserTakeover -> Color.rgb(255, 209, 104)
        VisualAgentCapsuleMode.Idle -> Color.rgb(173, 196, 222)
        VisualAgentCapsuleMode.Running -> when (presentation.phase) {
            VisualAgentCapsulePhase.Observe -> Color.rgb(92, 221, 255)
            VisualAgentCapsulePhase.Analyze -> Color.rgb(150, 132, 255)
            VisualAgentCapsulePhase.Move -> Color.rgb(88, 232, 194)
            VisualAgentCapsulePhase.Tap -> Color.rgb(238, 132, 255)
            VisualAgentCapsulePhase.Verify -> Color.rgb(119, 239, 181)
        }
    }

    private fun topWindowInsetPx(): Int {
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (resourceId > 0) service.resources.getDimensionPixelSize(resourceId) else dp(24f)
        return statusBar + dp(6f)
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(service).apply {
            text = value
            textSize = sp
            setTextColor(color)
            includeFontPadding = false
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun sectionLabel(value: String): TextView =
        text(value, 8.3f, Color.argb(196, 155, 226, 246), bold = true).apply {
            letterSpacing = 0.14f
            maxLines = 1
        }

    private fun TextView.applyReadable(maxLines: Int): TextView {
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(dp(1f).toFloat(), 1.03f)
        return this
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit): TextView =
        text(label, 10.5f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(8f), dp(1f))
            background = if (primary) {
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.argb(198, 55, 154, 210), Color.argb(190, 88, 214, 187)),
                ).apply {
                    cornerRadius = dp(19f).toFloat()
                    setStroke(dp(1f).coerceAtLeast(1), Color.argb(100, 188, 242, 255))
                }
            } else {
                roundedBackground(
                    fill = Color.argb(34, 255, 255, 255),
                    stroke = Color.argb(50, 220, 238, 255),
                    radiusDp = 19f,
                )
            }
            setOnClickListener { onClick() }
            setOnTouchListener(ButtonPressFeedback())
        }

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (Color.alpha(stroke) > 0) setStroke(dp(1f).coerceAtLeast(1), stroke)
        }

    private fun String.cleanCapsuleText(): String = trim().replace(Regex("\\s+"), " ")

    private fun String.cleanCapsulePanelText(): String =
        replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trim().replace(Regex("[\\t ]+"), " ") }
            .trim()

    private fun TextView?.setTextIfChanged(value: CharSequence) {
        val view = this ?: return
        if (!TextUtils.equals(view.text, value)) view.text = value
    }

    private fun View?.setVisibilityIfChanged(value: Int) {
        val view = this ?: return
        if (view.visibility != value) view.visibility = value
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()

    private fun lerp(start: Int, end: Int, fraction: Float): Int =
        (start + (end - start) * fraction).toInt()

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private inner class HeaderPressFeedback : View.OnTouchListener {
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> rootView?.animate()
                    ?.scaleX(0.985f)?.scaleY(0.985f)?.setDuration(80L)?.start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> rootView?.animate()
                    ?.scaleX(1f)?.scaleY(1f)?.setDuration(150L)?.setInterpolator(SOFT_OUT)?.start()
            }
            return false
        }
    }

    private inner class ButtonPressFeedback : View.OnTouchListener {
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                    .scaleX(1f).scaleY(1f).setDuration(130L).setInterpolator(SOFT_OUT).start()
            }
            return false
        }
    }

    companion object {
        private const val COLLAPSED_WIDTH_DP = 232f
        private const val EXPANDED_WIDTH_DP = 352f
        private const val COLLAPSED_HEIGHT_DP = 48f
        private const val CONVERSATION_HEIGHT_DP = 116f
        private const val INPUT_HEIGHT_DP = 68f
        private const val USER_REPLY_MAX_CHARS = 2_000
        private const val MAX_CONVERSATION_TURNS = 18
        private const val MAX_TURN_TEXT_CHARS = 2_400
        private const val PRIVATE_COMPLETION_TOKEN = "__user_completed_private_step__"
        private const val EXPAND_DURATION_MS = 360L
        private const val COLLAPSE_DURATION_MS = 285L
        private val SOFT_OUT = DecelerateInterpolator(1.55f)
        private val EXPAND_INTERPOLATOR = PathInterpolator(0.18f, 0.88f, 0.22f, 1f)
        private val COLLAPSE_INTERPOLATOR = PathInterpolator(0.32f, 0f, 0.2f, 1f)
    }
}

private class CapsuleGlassLayout(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accentColor = Color.rgb(92, 221, 255)
    private var hintAnimator: ValueAnimator? = null
    private var hintPosted = false
    private var hintEnabled = false
    private var hintProgress = -1f
    var expansionProgress: Float = 0f
        private set

    private val hintRunnable = Runnable {
        hintPosted = false
        if (!hintEnabled || expansionProgress > 0.02f || !isShown) return@Runnable
        hintAnimator?.cancel()
        hintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            interpolator = PathInterpolator(0.2f, 0.75f, 0.2f, 1f)
            addUpdateListener {
                hintProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hintProgress = -1f
                    invalidate()
                    scheduleHint()
                }
            })
            start()
        }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun setAccentColor(color: Int) {
        if (accentColor == color) return
        accentColor = color
        invalidate()
    }

    fun setExpansionProgress(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (expansionProgress == next) return
        expansionProgress = next
        invalidate()
    }

    fun setHintEnabled(enabled: Boolean) {
        if (hintEnabled == enabled) return
        hintEnabled = enabled
        if (enabled) {
            scheduleHint(initial = true)
        } else {
            removeCallbacks(hintRunnable)
            hintPosted = false
            hintAnimator?.cancel()
            hintAnimator = null
            hintProgress = -1f
            invalidate()
        }
    }

    fun stopAnimations() {
        setHintEnabled(false)
    }

    private fun scheduleHint(initial: Boolean = false) {
        if (!hintEnabled || hintPosted) return
        hintPosted = true
        postDelayed(hintRunnable, if (initial) 1_200L else 5_800L)
    }

    override fun onDraw(canvas: Canvas) {
        val inset = dp(0.75f)
        rect.set(inset, inset, width - inset, height - inset)
        val collapsedRadius = min(width, height) / 2f
        val expandedRadius = dp(24f)
        val radius = collapsedRadius + (expandedRadius - collapsedRadius) * expansionProgress

        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(
                Color.argb(240, 10, 19, 36),
                Color.argb(232, 25, 39, 65),
                Color.argb(241, 9, 16, 33),
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        borderPaint.color = withAlpha(accentColor, 94)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        highlightPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(94, 246, 252, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(dp(7f), dp(2f), width - dp(7f), dp(4.2f)),
            dp(2f),
            dp(2f),
            highlightPaint,
        )

        if (hintProgress >= 0f) {
            val center = (-0.25f + hintProgress * 1.5f) * width
            val half = width * 0.2f
            sheenPaint.shader = LinearGradient(
                center - half,
                0f,
                center + half,
                height.toFloat(),
                intArrayOf(Color.TRANSPARENT, withAlpha(accentColor, 38), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            val clipPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRect(rect, sheenPaint)
            canvas.restore()
        }
        super.onDraw(canvas)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Float): Float = value * density
}

private class CapsuleStatusOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accentColor = Color.rgb(92, 221, 255)
    private var pulse = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_350L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = DecelerateInterpolator(1.4f)
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        paint.color = Color.argb(
            (42 + 40 * pulse).toInt(),
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor),
        )
        canvas.drawCircle(cx, cy, min(width, height) * (0.42f + 0.08f * pulse), paint)
        paint.color = accentColor
        canvas.drawCircle(cx, cy, min(width, height) * 0.27f, paint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

private class CapsuleChevronView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 222, 238, 250)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setExpansionProgress(progress: Float) {
        rotation = 180f * progress.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val dx = 4f * density
        val dy = 2.6f * density
        val path = Path().apply {
            moveTo(cx - dx, cy - dy)
            lineTo(cx, cy + dy)
            lineTo(cx + dx, cy - dy)
        }
        canvas.drawPath(path, paint)
    }
}

private class CapsulePauseResumeView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accentColor = Color.rgb(92, 221, 255)
    private var morph = 0f
    private var morphAnimator: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.91f).scaleY(0.91f).setDuration(75L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                    .scaleX(1f).scaleY(1f).setDuration(150L)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
            false
        }
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    fun setInteractionEnabled(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.42f
    }

    fun setPaused(paused: Boolean) {
        val target = if (paused) 1f else 0f
        if (morph == target) return
        morphAnimator?.cancel()
        morphAnimator = ValueAnimator.ofFloat(morph, target).apply {
            duration = 190L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                morph = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.43f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(
            34,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor),
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = Color.argb(
            92,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor),
        )
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((235 * (1f - morph)).toInt(), 244, 250, 255)
        val barWidth = 2.2f * density
        val barHeight = 9f * density
        canvas.drawRoundRect(
            cx - 4f * density,
            cy - barHeight / 2f,
            cx - 4f * density + barWidth,
            cy + barHeight / 2f,
            barWidth,
            barWidth,
            paint,
        )
        canvas.drawRoundRect(
            cx + 1.8f * density,
            cy - barHeight / 2f,
            cx + 1.8f * density + barWidth,
            cy + barHeight / 2f,
            barWidth,
            barWidth,
            paint,
        )

        paint.color = Color.argb((235 * morph).toInt(), 244, 250, 255)
        canvas.drawPath(
            Path().apply {
                moveTo(cx - 3.2f * density, cy - 5f * density)
                lineTo(cx + 5.2f * density, cy)
                lineTo(cx - 3.2f * density, cy + 5f * density)
                close()
            },
            paint,
        )
    }

    override fun onDetachedFromWindow() {
        morphAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
