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
import androidx.lifecycle.lifecycleScope
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.VisualAgentHudOverlayService
import com.yuchen.ailedger.ui.AccessibilitySilentComposeRoot
import com.yuchen.ailedger.ui.AiAssistantNativeApp
import com.yuchen.ailedger.ui.StartupMetrics
import com.yuchen.ailedger.ui.StartupPerformanceGate
import kotlinx.coroutines.launch

private const val ENABLE_STARTUP_FRAME_MONITOR = false
private const val ENABLE_STARTUP_METRICS_OVERLAY = false

class MainActivity : ComponentActivity() {
    private val accessibilityShieldRunnable = Runnable {
        applyAccessibilityPerformanceShield(window.decorView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupMetrics.configure(
            enabled = ENABLE_STARTUP_FRAME_MONITOR || ENABLE_STARTUP_METRICS_OVERLAY
        )
        if (ENABLE_STARTUP_FRAME_MONITOR) {
            StartupMetrics.markOnce("Activity onCreate")
            StartupMetrics.startFrameMonitor()
        }
        super.onCreate(savedInstanceState)
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("super.onCreate 完成")
        prepareWindow(window)
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("窗口透明布局完成")
        installImeFocusReset(window)
        installAccessibilityPerformanceShield(window.decorView)
        if (ENABLE_STARTUP_FRAME_MONITOR) installFirstFrameProbe(window.decorView)
        if (ENABLE_STARTUP_METRICS_OVERLAY) installStartupMetricsOverlay(window.decorView)
        setContent {
            if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("Compose 首次进入")
            AccessibilitySilentComposeRoot {
                AiAssistantNativeApp()
            }
        }
        reinforceAccessibilityPerformanceShield(window.decorView)
        scheduleHighRefreshRate(window)
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("setContent 调用完成")
    }

    override fun onResume() {
        super.onResume()
        reinforceAccessibilityPerformanceShield(window.decorView)
        ensureAgentOverlaysAfterPermissionReturn()
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(accessibilityShieldRunnable)
        super.onDestroy()
    }

    private fun ensureAgentOverlaysAfterPermissionReturn() {
        if (!AgentOverlayService.canDrawOverlays(this)) return

        // The full-screen visual HUD is an independent presentation runtime. It may stay prepared
        // and invisible while idle, regardless of the interactive floating-window switch.
        VisualAgentHudOverlayService.ensureStarted(this)

        // Restore a manual switch request after returning from Android's overlay permission page.
        AgentOverlayService.restoreManualEnableAfterPermission(this)

        val progress = AgentRuntimeController.progress.value
        // A closed interactive overlay may be opened automatically only for an explicit GUI Plus
        // user-help/input request. Agent enabled/running/confirmation states cannot open it.
        AgentOverlayService.syncForProgress(this, progress)
        if (AgentOverlayService.isOverlaySwitchEnabled()) {
            AgentOverlayService.ensureStarted(this)
        }
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

    private fun installAccessibilityPerformanceShield(root: View) {
        // IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS 在根节点即可屏蔽整棵后代树。
        // 不再递归遍历 Compose 子 View，避免首帧后对复杂视图树进行重复主线程扫描。
        applyAccessibilityPerformanceShield(root)
        if (ENABLE_STARTUP_FRAME_MONITOR) StartupMetrics.markOnce("首帧前无障碍性能屏蔽完成")
    }

    private fun reinforceAccessibilityPerformanceShield(root: View) {
        applyAccessibilityPerformanceShield(root)
        root.removeCallbacks(accessibilityShieldRunnable)
        root.post(accessibilityShieldRunnable)
    }

    private fun applyAccessibilityPerformanceShield(root: View) {
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            root.isFocusedByDefault = false
            root.isScreenReaderFocusable = false
        }
    }

    private fun scheduleHighRefreshRate(window: Window) {
        lifecycleScope.launch {
            // 显示模式切换可能触发 Surface/RenderThread 调整，必须离开首帧和 OpenGL 编译窗口。
            StartupPerformanceGate.awaitDeferredBusinessWindow()
            if (!isFinishing && !isDestroyed) requestHighRefreshRate(window)
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
        overlay.setOnClickListener { expanded = !expanded; updateText() }
        parent.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply { leftMargin = 24; topMargin = 64 }
        )
        val ticker = object : Runnable {
            override fun run() {
                updateText()
                handler.postDelayed(this, 450L)
            }
        }
        ticker.run()
    }
}
