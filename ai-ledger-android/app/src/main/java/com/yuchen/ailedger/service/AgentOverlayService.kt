package com.yuchen.ailedger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

class AgentOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null
    private var titleView: TextView? = null
    private var statusView: TextView? = null
    private var actionView: TextView? = null
    private var resultView: TextView? = null
    private var logView: TextView? = null
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
            AgentRuntimeController.progress.collectLatest { progress -> updateProgress(progress) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (rootView == null) createOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        rootView?.let { view -> runCatching { windowManager?.removeView(view) } }
        rootView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlay() {
        if (rootView != null) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (11 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(236, 20, 35, 76), Color.argb(220, 54, 70, 126), Color.argb(226, 32, 22, 64))
            ).apply {
                cornerRadius = 22 * density
                setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.argb(142, 190, 240, 255))
            }
            elevation = 12 * density
        }
        titleView = TextView(this).apply {
            text = "AI 智能体"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        statusView = TextView(this).apply {
            setTextColor(Color.argb(214, 160, 255, 238))
            textSize = 10f
        }
        actionView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
        }
        resultView = TextView(this).apply {
            setTextColor(Color.argb(210, 255, 255, 255))
            textSize = 10f
            maxLines = 2
        }
        logView = TextView(this).apply {
            setTextColor(Color.argb(178, 225, 235, 255))
            textSize = 9f
            maxLines = 5
        }
        val closeView = TextView(this).apply {
            text = "×"
            textSize = 18f
            setTextColor(Color.argb(220, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding((8 * density).toInt(), 0, 0, 0)
            setOnClickListener { stopSelf() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeView, LinearLayout.LayoutParams((30 * density).toInt(), (28 * density).toInt()))
        }
        panel.addView(header)
        panel.addView(statusView)
        panel.addView(actionView)
        panel.addView(resultView)
        panel.addView(logView)
        panel.setOnTouchListener(DragTouchListener())
        val params = WindowManager.LayoutParams(
            (248 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (92 * density).toInt()
        }
        rootView = panel
        layoutParams = params
        runCatching { wm.addView(panel, params) }.onFailure { stopSelf() }
    }

    private fun updateProgress(progress: AgentOverlayProgress) {
        titleView?.text = progress.title
        statusView?.text = "${if (progress.enabled) "已开启" else "已关闭"} · ${progress.status}"
        actionView?.text = progress.currentAction
        resultView?.text = progress.lastResult.takeIf { it.isNotBlank() }?.let { "结果：$it" }.orEmpty()
        logView?.text = progress.logs.takeLast(4).joinToString("\n") { "• $it" }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            val wm = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downRawX) < 10f && abs(event.rawY - downRawY) < 10f) openMainApp()
                    return true
                }
            }
            return false
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    companion object {
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
