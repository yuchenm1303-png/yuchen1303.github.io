package com.yuchen.ailedger

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareWindow(window)

        setContent {
            AiLedgerNativeShell(
                createWebView = { onGlassMode -> createAiLedgerWebView(this, onGlassMode) },
                onNavSelected = { view -> openWebView(view) },
                onHaptic = { style -> performNativeHaptic(style) },
            )
        }
    }

    private fun prepareWindow(window: Window) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAiLedgerWebView(
        activity: Activity,
        onGlassMode: (GlassMode) -> Unit,
    ): WebView {
        return WebView(activity).apply {
            webView = this
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            val bridge = AiLedgerNativeBridge(
                activity = activity,
                onGlassMode = onGlassMode,
                onHaptic = ::performNativeHaptic,
                onOpenView = ::openWebView,
            )
            addJavascriptInterface(bridge, "AiLedgerNative")
            addJavascriptInterface(bridge, "AiLedgerAndroid")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectNativeShellBootstrap(view)
                    openWebView("ai")
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(APP_URL)
        }
    }

    private fun injectNativeShellBootstrap(view: WebView) {
        view.evaluateJavascript(
            """
            (() => {
              document.documentElement.classList.add('native-shell');
              document.body?.classList.add('native-shell');
              const styleId = 'ai-ledger-native-shell-css';
              if (!document.getElementById(styleId)) {
                const style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                  .native-shell .bottom-nav { display: none !important; }
                  .native-shell body { background: transparent !important; }
                  .native-shell .app-shell { padding-bottom: 18px !important; }
                  .native-shell .view { padding-bottom: 18px !important; }
                  .native-shell .scene-backdrop,
                  .native-shell .ambient { pointer-events: none !important; }
                `;
                document.head.appendChild(style);
              }

              const notify = () => window.AiLedgerNativeBridge?.notifyReady?.();
              if (window.AiLedgerNativeBridge) {
                notify();
                return;
              }

              const scriptId = 'ai-ledger-native-bridge-loader';
              if (!document.getElementById(scriptId)) {
                const script = document.createElement('script');
                script.id = scriptId;
                script.src = './native-bridge.js?v=20260517-1';
                script.onload = notify;
                script.onerror = () => console.warn('[native-shell] native-bridge.js load failed');
                document.head.appendChild(script);
              }
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun openWebView(viewName: String) {
        val cleanView = viewName.replace("'", "")
        webView?.evaluateJavascript(
            """
            (() => {
              const view = '$cleanView';
              window.AiAssistantViews?.open?.(view);
              const button = document.querySelector('.nav-btn[data-view="' + view + '"]');
              if (button) button.click();
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun performNativeHaptic(style: String) {
        val feedback = when (style) {
            "heavy" -> HapticFeedbackConstants.LONG_PRESS
            "tick" -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        window.decorView.performHapticFeedback(feedback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onBackPressed() {
        val currentWebView = webView
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val APP_URL = "https://yuchenm1303-png.github.io/yuchen1303.github.io/ai-ledger/?native=1"
    }
}
