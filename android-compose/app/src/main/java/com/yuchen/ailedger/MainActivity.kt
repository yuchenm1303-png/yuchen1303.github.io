package com.yuchen.ailedger

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF050815),
                    surface = Color(0xFF050815)
                )
            ) {
                AppModeSwitcher(activity = this)
            }
        }
    }
}

@Composable
private fun AppModeSwitcher(activity: MainActivity) {
    var nativePreview by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (nativePreview) {
            NativeLiquidPreviewScreen()
        } else {
            LegacyWebAppScreen(activity = activity)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 46.dp, end = 12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF9DEEFF).copy(alpha = 0.88f),
                            Color(0xFFB8A8FF).copy(alpha = 0.78f)
                        )
                    )
                )
                .clickable { nativePreview = !nativePreview }
                .padding(horizontal = 13.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (nativePreview) "旧版" else "原生",
                color = Color(0xFF061428),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LegacyWebAppScreen(activity: MainActivity) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050815))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewRef = this
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(LegacyAndroidBridge(activity), "AILEDGER_ANDROID")

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.userAgentString = settings.userAgentString + " AiLedgerAndroidCompose/legacy-full-ui"

                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return handleExternalUrl(activity, request.url)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            return handleExternalUrl(activity, Uri.parse(url))
                        }
                    }

                    loadUrl("file:///android_asset/index.html")
                }
            },
            update = { webViewRef = it }
        )
    }
}

private fun handleExternalUrl(activity: MainActivity, uri: Uri): Boolean {
    val scheme = uri.scheme.orEmpty().lowercase()
    if (scheme == "file" || scheme == "http" || scheme == "https") return false

    return runCatching {
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    }.getOrElse {
        Toast.makeText(activity, "没有找到可打开此链接的应用", Toast.LENGTH_SHORT).show()
        true
    }
}

private class LegacyAndroidBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun toast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        activity.runOnUiThread {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure {
                Toast.makeText(activity, "无法打开链接", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
