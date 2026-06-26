package com.yuchen.ailedger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VisualAgentHudOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var hudView: VisualAgentHudOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        createOverlay()
        scope.launch {
            combine(
                AgentRuntimeController.progress,
                VisualAgentHudRuntime.target,
                AgentRuntimeController.overlayHiddenForCapture,
            ) { progress, target, hidden -> Triple(progress, target, hidden) }
                .collect { (progress, target, hidden) ->
                    updateOverlay(progress, target, hidden)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (hudView == null) createOverlay()
        updateOverlay(
            AgentRuntimeController.progress.value,
            VisualAgentHudRuntime.target.value,
            AgentRuntimeController.overlayHiddenForCapture.value,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        hudView?.stopAnimation()
        hudView?.let { runCatching { windowManager?.removeView(it) } }
        hudView = null
        layoutParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        if (hudView != null) return
        val wm = windowManager ?: return
        val view = VisualAgentHudOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Keep the untrusted application-overlay window within Android's touch-through
            // obscuring-opacity limit. All actual interaction stays in the separate small panel.
            alpha = MAX_TOUCH_THROUGH_ALPHA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        hudView = view
        layoutParams = params
        runCatching { wm.addView(view, params) }
            .onFailure {
                hudView = null
                layoutParams = null
                stopSelf()
            }
    }

    private fun updateOverlay(
        progress: AgentOverlayProgress,
        target: VisualAgentHudTarget?,
        hiddenForCapture: Boolean,
    ) {
        val view = hudView ?: return
        val matchingTarget = target?.takeIf { it.taskId == progress.taskId }
        val lastLog = progress.logs.lastOrNull().orEmpty()

        // noteAction() appends the exact currentAction immediately after acquiring the clean-
        // capture lease. That lets this presentation-only window remain visible for the real
        // action while every screenshot/fresh-observation lease still hides it completely.
        val executingRealAction = progress.running &&
            progress.currentAction.isNotBlank() &&
            lastLog == progress.currentAction
        val visuallyHidden = hiddenForCapture && !executingRealAction

        val currentLooksLikeTap = progress.currentAction.contains("点击")
        val baseTarget = matchingTarget ?: VisualAgentHudTarget(
            taskId = progress.taskId,
            revision = progress.taskId,
            x = 0.52f,
            y = 0.46f,
            normalized = true,
            positioned = false,
            detail = progress.lastResult.take(180),
        )
        val displayTarget = if (currentLooksLikeTap) {
            baseTarget
        } else {
            baseTarget.copy(
                positioned = false,
                actionType = "",
                targetText = "",
                detail = progress.lastResult.take(180),
            )
        }
        view.submit(progress, displayTarget, visuallyHidden)
    }

    companion object {
        private const val MAX_TOUCH_THROUGH_ALPHA = 0.8f

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun ensureStarted(context: Context): Boolean {
            if (!canDrawOverlays(context)) return false
            return runCatching {
                context.startService(Intent(context, VisualAgentHudOverlayService::class.java))
                true
            }.getOrDefault(false)
        }
    }
}
