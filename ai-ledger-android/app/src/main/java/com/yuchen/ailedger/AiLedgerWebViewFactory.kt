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
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
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
            settings.offscreenPreRaster = true

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
              document.documentElement.dataset.nativeGlassMode = 'safe';
              document.body?.classList.add('native-shell', 'native-shell-webview', 'native-composer-active', 'assistant-lite-motion', 'assistant-balanced-performance');
              const styleId = 'ai-ledger-native-shell-css';
              if (!document.getElementById(styleId)) {
                const style = document.createElement('style');
                style.id = styleId;
                style.textContent = `
                  html.native-shell,
                  html.native-shell body,
                  body.native-shell {
                    background: transparent !important;
                    overflow-x: hidden !important;
                  }

                  body.native-shell {
                    padding-bottom: 0 !important;
                    overscroll-behavior: none !important;
                  }

                  html.native-shell .bottom-nav,
                  body.native-shell .bottom-nav,
                  body.native-shell.native-composer-active .chat-composer,
                  body.native-shell.native-composer-active #aiModeHint,
                  body.native-shell.native-composer-active .quick-tags.chat-tags {
                    display: none !important;
                  }

                  html.native-shell .scene-backdrop,
                  html.native-shell .ambient,
                  body.native-shell .scene-backdrop,
                  body.native-shell .ambient {
                    display: none !important;
                    visibility: hidden !important;
                    animation: none !important;
                    transform: none !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                  }

                  html.native-shell .app-shell,
                  body.native-shell .app-shell {
                    padding-bottom: 18px !important;
                    contain: layout paint style !important;
                  }

                  html.native-shell .view,
                  body.native-shell .view {
                    padding-bottom: 18px !important;
                    animation: none !important;
                  }

                  body.native-shell.native-composer-active .chat-shell {
                    grid-template-rows: auto minmax(260px, 1fr) !important;
                    min-height: min(620px, calc(100vh - 146px)) !important;
                    padding-bottom: 14px !important;
                  }

                  body.native-shell.native-composer-active .chat-messages {
                    min-height: 280px !important;
                    padding-bottom: 16px !important;
                  }

                  html.native-shell .reveal,
                  html.native-shell [class*="delay-"],
                  body.native-shell .reveal,
                  body.native-shell [class*="delay-"] {
                    opacity: 1 !important;
                    transform: none !important;
                    animation: none !important;
                    transition-duration: 120ms !important;
                  }

                  html.native-shell .glass-card,
                  html.native-shell .chat-shell,
                  html.native-shell .chat-composer,
                  html.native-shell .tag-btn,
                  html.native-shell .ghost-btn,
                  html.native-shell .mini-ghost-btn,
                  html.native-shell .model-picker-btn,
                  html.native-shell .mobile-command-card,
                  body.native-shell .glass-card,
                  body.native-shell .chat-shell,
                  body.native-shell .chat-composer,
                  body.native-shell .tag-btn,
                  body.native-shell .ghost-btn,
                  body.native-shell .mini-ghost-btn,
                  body.native-shell .model-picker-btn,
                  body.native-shell .mobile-command-card {
                    backdrop-filter: none !important;
                    -webkit-backdrop-filter: none !important;
                    will-change: auto !important;
                  }

                  html.native-shell .glass-card,
                  html.native-shell .chat-shell,
                  body.native-shell .glass-card,
                  body.native-shell .chat-shell {
                    background:
                      linear-gradient(145deg, rgba(255,255,255,.118), rgba(255,255,255,.040) 58%, rgba(255,255,255,.026)),
                      rgba(12, 22, 42, .46) !important;
                    box-shadow: 0 10px 26px rgba(0,0,0,.12), inset 0 .7px 0 rgba(255,255,255,.20) !important;
                  }

                  html.native-shell *::before,
                  html.native-shell *::after,
                  body.native-shell *::before,
                  body.native-shell *::after {
                    animation-duration: 1ms !important;
                    animation-iteration-count: 1 !important;
                  }
                `;
                document.head.appendChild(style);
              }

              const notify = () => window.AiLedgerNativeBridge?.notifyReady?.();

              function loadScriptOnce(id, src, onload) {
                const old = document.getElementById(id);
                if (old) {
                  onload?.();
                  return;
                }
                const script = document.createElement('script');
                script.id = id;
                script.src = src;
                script.onload = () => onload?.();
                script.onerror = () => console.warn('[native-shell] script load failed:', src);
                document.head.appendChild(script);
              }

              loadScriptOnce('ai-ledger-native-bridge-loader', './native-bridge.js?v=20260517-1', () => {
                notify();
                loadScriptOnce('ai-ledger-native-command-executor-loader', './native-command-executor.js?v=20260517-1');
              });
            })();
            """.trimIndent(),
            null,
        )
    }
}
