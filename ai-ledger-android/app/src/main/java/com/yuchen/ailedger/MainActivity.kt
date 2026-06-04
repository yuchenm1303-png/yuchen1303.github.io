package com.yuchen.ailedger

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yuchen.ailedger.ui.AiAssistantNativeApp
import com.yuchen.ailedger.ui.StartupMetrics

private const val ENABLE_STARTUP_FRAME_MONITOR = false
private const val ENABLE_STARTUP_METRICS_OVERLAY = false

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (ENABLE_STARTUP_FRAME_MONITOR) {
            StartupMetrics.markOnce("Activity onCreate")
            StartupMetrics.startFrameMonitor()
        }
        super.onCreate(savedInstanceState)
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("super.onCreate 完成")
        prepareWindow(window)
        requestHighRefreshRate(window)
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("窗口透明布局完成")
        installImeFocusReset(window)
        if (ENABLE_STARTUP_FRAME_MONITOR) installFirstFrameProbe(window.decorView)
        if (ENABLE_STARTUP_METRICS_OVERLAY) installStartupMetricsOverlay(window.decorView)
        setContent {
            if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("Compose 首次进入")
            AiAssistantNativeApp()
        }
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("setContent 调用完成")
    }

    private fun prepareWindow(window: Window) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun requestHighRefreshRate(window: Window) {
        val display = currentDisplay() ?: return
        val bestMode = display.supportedModes
            .filter { it.refreshRate > 60f }
            .maxByOrNull { it.refreshRate }
            ?: run {
                if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("高刷请求：未发现高刷模式")
                return
            }

        val attrs = window.attributes
        attrs.preferredDisplayModeId = bestMode.modeId
        attrs.preferredRefreshRate = bestMode.refreshRate
        window.attributes = attrs
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("高刷请求：${bestMode.refreshRate.toInt()}Hz")
    }

    private fun currentDisplay(): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
    }

    private fun installImeFocusReset(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        var imeWasVisible = false
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val imeVisible = insets.isVisible(WindowInsets.Type.ime())
            if (imeWasVisible && !imeVisible) {
                window.currentFocus?.clearFocus()
                view.clearFocus()
            }
            imeWasVisible = imeVisible
            insets
        }
    }

    private fun installFirstFrameProbe(root: View) {
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                }
                StartupMetrics.markOnce("首帧 preDraw")
                root.post { StartupMetrics.markOnce("首帧后主线程回调") }
                return true
            }
        })
    }

    private fun installStartupMetricsOverlay(root: View) {
        val parent = root as? ViewGroup ?: return
        val handler = Handler(Looper.getMainLooper())
        val overlay = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA101A35.toInt())
            setPadding(12, 8, 12, 8)
            maxLines = 18
            isClickable = true
            alpha = 0.94f
        }
        var expanded = false

        fun updateText() {
            val events = StartupMetrics.events
            val last = events.lastOrNull()
            val frames = StartupMetrics.frameStats
            val warmup = StartupMetrics.warmupState
            overlay.text = if (!expanded) {
                "性能 ${last?.elapsedMs ?: 0}ms · ${frames.compactLabel()} · $warmup"
            } else {
                buildString {
                    append("性能监测\n")
                    append(frames.compactLabel()).append('\n')
                    append("页面：").append(warmup).append('\n')
                    append("启动：").append(last?.elapsedMs ?: 0).append("ms\n\n")
                    append("时间线\n")
                    if (events.isEmpty()) {
                        append("暂无数据")
                    } else {
                        events.takeLast(12).forEach { event ->
                            append(event.compactLabel())
                            append("  ")
                            append(event.name)
                            append('\n')
                        }
                    }
                    append("\n点我收起")
                }
            }
        }

        overlay.setOnClickListener {
            expanded = !expanded
            updateText()
        }

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = getStatusBarHeightPx() + 12.dpPx()
            rightMargin = 10.dpPx()
        }
        parent.addView(overlay, layoutParams)
        updateText()

        val ticker = object : Runnable {
            override fun run() {
                updateText()
                handler.postDelayed(this, 500L)
            }
        }
        handler.post(ticker)
        overlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                handler.removeCallbacks(ticker)
            }
        })
    }

    private fun getStatusBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 24.dpPx()
    }

    private fun Int.dpPx(): Int = (this * resources.displayMetrics.density).toInt()
}
