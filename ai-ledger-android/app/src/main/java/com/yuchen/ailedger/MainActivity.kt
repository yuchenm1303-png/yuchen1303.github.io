package com.yuchen.ailedger

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val nativeMessagesState = mutableStateOf(initialNativeChatMessages())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareWindow(window)

        setContent {
            AiLedgerNativeShell(
                createWebView = { onGlassMode ->
                    AiLedgerWebViewFactory.create(
                        activity = this,
                        onWebViewReady = { webView = it },
                        onGlassMode = onGlassMode,
                        onHaptic = { style -> NativeHaptics.perform(this, style) },
                        onOpenView = { view -> openWebView(view) },
                        onWebPageReady = { syncNativeChatFromWeb() },
                    )
                },
                nativeMessages = nativeMessagesState.value,
                onNavSelected = { view -> openWebView(view) },
                onHaptic = { style -> NativeHaptics.perform(this, style) },
                onPromptSubmit = { text -> submitPromptToWeb(text) },
            )
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

    private fun submitPromptToWeb(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        nativeMessagesState.value = nativeMessagesState.value + NativeChatMessage(
            id = "native-user-${System.currentTimeMillis()}",
            role = "user",
            content = clean,
        )
        val encoded = JSONObject.quote(clean)
        webView?.evaluateJavascript(
            """
            (() => {
              const text = $encoded;
              if (window.AiAssistantRuntime?.ask) {
                window.AiAssistantRuntime.ask(text);
                return true;
              }
              const input = document.querySelector('#aiInput');
              const form = document.querySelector('#chatForm');
              if (input && form) {
                input.value = text;
                input.dispatchEvent(new Event('input', { bubbles: true }));
                form.requestSubmit?.();
                return true;
              }
              return false;
            })();
            """.trimIndent(),
            null,
        )
        window.decorView.postDelayed({ syncNativeChatFromWeb() }, 260)
        window.decorView.postDelayed({ syncNativeChatFromWeb() }, 900)
    }

    private fun syncNativeChatFromWeb() {
        webView?.evaluateJavascript("localStorage.getItem('ai-ledger-chat-v2')") { raw ->
            val parsed = parseNativeChatMessages(raw)
            nativeMessagesState.value = parsed
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
}
