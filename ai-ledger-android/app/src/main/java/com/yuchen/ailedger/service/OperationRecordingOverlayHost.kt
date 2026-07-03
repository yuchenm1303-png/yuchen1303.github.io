package com.yuchen.ailedger.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.yuchen.ailedger.MainActivity
import com.yuchen.ailedger.R
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class RecordingOverlaySnapshot(
    val recording: OperationRecordingState,
    val hiddenForCapture: Boolean,
)

/**
 * 操作演示期间显示的原生无障碍浮窗。
 *
 * 它只承担用户可见状态、完成/取消控制和通知栏兜底，不参与演示理解、步骤规划或节点扫描。
 * 截图时跟随统一 clean-capture 状态短暂隐藏，避免浮窗进入视觉证据。
 */
internal class OperationRecordingOverlayHost(
    private val service: AccessibilityService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val notificationManager = service.getSystemService(NotificationManager::class.java)
    private val density = service.resources.displayMetrics.density.coerceAtLeast(1f)

    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var titleView: TextView? = null
    private var metaView: TextView? = null
    private var finishView: TextView? = null
    private var cancelView: TextView? = null
    private var started = false
    private var hiddenForCapture = false
    private var latestState = OperationRecordingState()
    private var tickerJob: Job? = null
    private var lastNotificationKey = ""

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                OperationLearningRecordingCoordinator.state,
                AgentRuntimeController.overlayHiddenForCapture,
            ) { recording, hidden ->
                RecordingOverlaySnapshot(recording, hidden)
            }.collect(::render)
        }
    }

    fun destroy() {
        if (!started && rootView == null) return
        started = false
        tickerJob?.cancel()
        tickerJob = null
        removeWindow()
        cancelNotification()
        scope.cancel()
    }

    private fun render(snapshot: RecordingOverlaySnapshot) {
        latestState = snapshot.recording
        hiddenForCapture = snapshot.hiddenForCapture
        if (!shouldPresent(snapshot.recording)) {
            tickerJob?.cancel()
            tickerJob = null
            removeWindow()
            cancelNotification()
            return
        }

        if (!createWindow()) return
        updateContent()
        applyCaptureVisibility(snapshot.hiddenForCapture)
        publishNotificationIfNeeded()
        ensureTicker()
    }

    private fun createWindow(): Boolean {
        if (rootView != null) return true
        val wm = windowManager ?: return false
        val screenWidth = service.resources.displayMetrics.widthPixels
        val width = minOf(dp(356f), screenWidth - dp(16f)).coerceAtLeast(dp(260f))
        val height = dp(58f)

        val root = FrameLayout(service).apply {
            background = roundedBackground(
                fill = Color.argb(232, 17, 23, 48),
                stroke = Color.argb(82, 141, 249, 234),
                radiusDp = 22f,
            )
            elevation = dp(18f).toFloat()
            contentDescription = "操作学习录制浮窗"
        }
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(7f), dp(7f), dp(7f))
        }
        val statusDot = TextView(service).apply {
            text = "●"
            textSize = 13f
            setTextColor(Color.rgb(255, 112, 130))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        row.addView(statusDot, LinearLayout.LayoutParams(dp(18f), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginEnd = dp(7f)
        })

        val labels = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = TextView(service).apply {
            textSize = 12.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            includeFontPadding = false
        }.also { labels.addView(it) }
        metaView = TextView(service).apply {
            textSize = 9.8f
            setTextColor(Color.argb(178, 220, 234, 244))
            maxLines = 1
            includeFontPadding = false
        }.also { labels.addView(it) }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(7f)
        })

        finishView = actionButton(
            label = "完成",
            fill = Color.argb(42, 141, 249, 234),
            stroke = Color.argb(98, 141, 249, 234),
            textColor = Color.rgb(184, 255, 246),
        ) {
            OperationLearningRecordingCoordinator.requestStop(
                context = service.applicationContext,
                reason = OperationRecordingStopReason.UserFinished,
            )
        }.also { row.addView(it, LinearLayout.LayoutParams(dp(58f), dp(38f)).apply { marginEnd = dp(5f) }) }

        cancelView = actionButton(
            label = "取消",
            fill = Color.argb(30, 255, 166, 178),
            stroke = Color.argb(68, 255, 166, 178),
            textColor = Color.rgb(255, 186, 196),
        ) {
            OperationLearningRecordingCoordinator.requestStop(
                context = service.applicationContext,
                reason = OperationRecordingStopReason.UserCancelled,
            )
        }.also { row.addView(it, LinearLayout.LayoutParams(dp(52f), dp(38f))) }

        root.addView(
            row,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            windowFlags(hidden = hiddenForCapture),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topWindowInsetPx() + dp(8f)
            alpha = if (hiddenForCapture) 0f else 1f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
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
                root.alpha = if (hiddenForCapture) 0f else 1f
            }
            .onFailure { error ->
                AgentRuntimeController.noteDiagnostic(
                    "操作学习录制浮窗创建失败：${error.message ?: error.javaClass.simpleName}",
                )
            }
            .isSuccess
    }

    private fun updateContent() {
        val state = latestState
        val stopping = state.phase == OperationRecordingPhase.Stopping
        titleView?.text = when (state.phase) {
            OperationRecordingPhase.Starting -> "正在准备视觉演示"
            OperationRecordingPhase.Stopping -> "正在生成 Skill"
            else -> "正在学习：${state.workflowTitle.take(16)}"
        }
        metaView?.text = buildString {
            append(formatDuration(state.startedAtMillis ?: 0L))
            append(" · ")
            append(state.capturedEventCount)
            append(" 帧")
        }
        finishView?.apply {
            isEnabled = !stopping
            alpha = if (stopping) 0.45f else 1f
            text = if (stopping) "处理中" else "完成"
        }
        cancelView?.apply {
            isEnabled = !stopping
            alpha = if (stopping) 0.45f else 1f
        }
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive && shouldPresent(latestState)) {
                delay(1_000L)
                updateContent()
            }
        }
    }

    private fun applyCaptureVisibility(hidden: Boolean) {
        val root = rootView ?: return
        val params = layoutParams ?: return
        params.flags = windowFlags(hidden)
        params.alpha = if (hidden) 0f else 1f
        root.alpha = params.alpha
        root.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        runCatching { windowManager?.updateViewLayout(root, params) }
    }

    private fun removeWindow() {
        rootView?.let { view -> runCatching { windowManager?.removeView(view) } }
        rootView = null
        layoutParams = null
        titleView = null
        metaView = null
        finishView = null
        cancelView = null
        lastNotificationKey = ""
    }

    private fun publishNotificationIfNeeded() {
        val state = latestState
        val key = "${state.phase}|${state.workflowId}|${state.capturedEventCount}"
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        ensureNotificationChannel()
        val startedAtMillis = state.startedAtMillis ?: 0L

        val openIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            service,
            OPEN_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val finishPendingIntent = PendingIntent.getBroadcast(
            service,
            FINISH_REQUEST_CODE,
            Intent(service, OperationRecordingActionReceiver::class.java).apply {
                action = OperationRecordingActionReceiver.ACTION_FINISH
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            service,
            CANCEL_REQUEST_CODE,
            Intent(service, OperationRecordingActionReceiver::class.java).apply {
                action = OperationRecordingActionReceiver.ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在学习：${state.workflowTitle.take(24)}")
            .setContentText("已采集 ${state.capturedEventCount} 帧视觉证据")
            .setContentIntent(openPendingIntent)
            .setWhen(startedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis())
            .setUsesChronometer(startedAtMillis > 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.mipmap.ic_launcher, "结束并生成", finishPendingIntent)
            .addAction(R.mipmap.ic_launcher, "取消并删除", cancelPendingIntent)
            .build()
        runCatching { notificationManager?.notify(NOTIFICATION_ID, notification) }
    }

    private fun cancelNotification() {
        lastNotificationKey = ""
        runCatching { notificationManager?.cancel(NOTIFICATION_ID) }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "操作学习录制",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示视觉 Skill 演示的状态以及结束和取消操作。"
                setShowBadge(false)
            },
        )
    }

    private fun actionButton(
        label: String,
        fill: Int,
        stroke: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): TextView = TextView(service).apply {
        text = label
        textSize = 10.5f
        setTextColor(textColor)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        background = roundedBackground(fill, stroke, 15f)
        setOnClickListener { if (isEnabled) onClick() }
    }

    private fun formatDuration(startedAtMillis: Long): String {
        if (startedAtMillis <= 0L) return "00:00"
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L) / 1_000L)
        val minutes = elapsedSeconds / 60L
        val seconds = elapsedSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun windowFlags(hidden: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (hidden) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun roundedBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }

    private fun topWindowInsetPx(): Int {
        val identifier = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (identifier > 0) service.resources.getDimensionPixelSize(identifier) else 0
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    companion object {
        private const val CHANNEL_ID = "operation_learning_recording"
        private const val NOTIFICATION_ID = 13042
        private const val OPEN_REQUEST_CODE = 13043
        private const val FINISH_REQUEST_CODE = 13044
        private const val CANCEL_REQUEST_CODE = 13045

        private fun shouldPresent(state: OperationRecordingState): Boolean =
            state.phase == OperationRecordingPhase.Starting ||
                state.phase == OperationRecordingPhase.Recording ||
                state.phase == OperationRecordingPhase.Stopping
    }
}
