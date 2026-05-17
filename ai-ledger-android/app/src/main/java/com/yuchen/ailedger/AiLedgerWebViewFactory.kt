package com.yuchen.ailedger

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

object AiLedgerWebViewFactory {
    const val APP_URL = "https://yuchenm1303-png.github.io/yuchen1303.github.io/ai-ledger/?native=1"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        activity: Activity,
        onWebViewReady: (WebView) -> Unit,
        onGlassMode: (GlassMode) -> Unit,
        onHaptic: (String) -> Unit,
        onOpenView: (String) -> Unit,
    ): WebView {
        return WebView(activity).apply {
            onWebViewReady(this)
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
                onHaptic = onHaptic,
                onOpenView = onOpenView,
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
                    onOpenView("ai")
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(APP_URL)
        }
    }

    fun injectNativeShellBootstrap(view: WebView) {
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
}
