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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

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

            addJavascriptInterface(
                AiLedgerNativeBridge(
                    activity = activity,
                    onGlassMode = onGlassMode,
                    onHaptic = ::performNativeHaptic,
                    onOpenView = ::openWebView,
                ),
                "AiLedgerNative",
            )
            addJavascriptInterface(
                AiLedgerNativeBridge(
                    activity = activity,
                    onGlassMode = onGlassMode,
                    onHaptic = ::performNativeHaptic,
                    onOpenView = ::openWebView,
                ),
                "AiLedgerAndroid",
            )

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectNativeShellCss(view)
                    openWebView("ai")
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(APP_URL)
        }
    }

    private fun injectNativeShellCss(view: WebView) {
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
                `;
                document.head.appendChild(style);
              }
              window.AiLedgerNativeBridge?.notifyReady?.();
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

enum class GlassMode {
    Basic,
    Blur,
    Liquid,
    Safe;

    companion object {
        fun from(value: String?): GlassMode = when (value?.lowercase()) {
            "blur" -> Blur
            "liquid" -> Liquid
            "safe" -> Safe
            else -> Basic
        }
    }
}

private class AiLedgerNativeBridge(
    private val activity: Activity,
    private val onGlassMode: (GlassMode) -> Unit,
    private val onHaptic: (String) -> Unit,
    private val onOpenView: (String) -> Unit,
) {
    @JavascriptInterface
    fun getCapabilities(): String {
        return JSONObject()
            .put("nativeGlass", true)
            .put("haptic", true)
            .put("postMessage", true)
            .put("openView", true)
            .put("glassModes", "basic,blur,liquid,safe")
            .toString()
    }

    @JavascriptInterface
    fun haptic(style: String?) {
        activity.runOnUiThread { onHaptic(style ?: "light") }
    }

    @JavascriptInterface
    fun postMessage(message: String?) {
        if (message.isNullOrBlank()) return
        val data = runCatching { JSONObject(message) }.getOrNull() ?: return
        val type = data.optString("type")
        val payload = data.optJSONObject("payload") ?: JSONObject()

        activity.runOnUiThread {
            when (type) {
                "haptic" -> onHaptic(payload.optString("style", "light"))
                "setGlassMode" -> onGlassMode(GlassMode.from(payload.optString("mode", "basic")))
                "openView" -> onOpenView(payload.optString("view", "ai"))
                "closeQuickAi" -> activity.finish()
                "openFullApp" -> onOpenView("ai")
            }
        }
    }

    @JavascriptInterface
    fun setGlassMode(mode: String?) {
        activity.runOnUiThread { onGlassMode(GlassMode.from(mode)) }
    }
}

@Composable
private fun AiLedgerNativeShell(
    createWebView: ((GlassMode) -> Unit) -> WebView,
    onNavSelected: (String) -> Unit,
    onHaptic: (String) -> Unit,
) {
    var selectedView by remember { mutableStateOf("ai") }
    var glassMode by remember { mutableStateOf(GlassMode.Basic) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF07142E),
                        androidx.compose.ui.graphics.Color(0xFF11294C),
                        androidx.compose.ui.graphics.Color(0xFF07101F),
                    ),
                ),
            ),
    ) {
        LiquidAmbientBackground(glassMode = glassMode)

        AndroidView(
            factory = { createWebView { mode -> glassMode = mode } },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
        )

        NativeTopBadge(
            glassMode = glassMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(top = 8.dp, end = 14.dp),
        )

        NativeGlassBottomNav(
            selectedView = selectedView,
            glassMode = glassMode,
            onSelected = { item ->
                selectedView = item.view
                onHaptic("tick")
                onNavSelected(item.view)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun LiquidAmbientBackground(glassMode: GlassMode) {
    val alpha by animateFloatAsState(
        targetValue = if (glassMode == GlassMode.Safe) 0.32f else 0.58f,
        animationSpec = spring(stiffness = 90f, dampingRatio = 0.82f),
        label = "ambientAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0x886AD7FF),
                        androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    radius = 820f,
                ),
            ),
    )
}

@Composable
private fun NativeTopBadge(
    glassMode: GlassMode,
    modifier: Modifier = Modifier,
) {
    val label = when (glassMode) {
        GlassMode.Basic -> "原生玻璃 Basic"
        GlassMode.Blur -> "原生玻璃 Blur"
        GlassMode.Liquid -> "原生玻璃 Liquid"
        GlassMode.Safe -> "流畅优先 Safe"
    }

    Surface(
        modifier = modifier.shadow(12.dp, CircleShape, clip = false),
        shape = CircleShape,
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class NativeNavItem(
    val view: String,
    val icon: String,
    val label: String,
)

@Composable
private fun NativeGlassBottomNav(
    selectedView: String,
    glassMode: GlassMode,
    onSelected: (NativeNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NativeNavItem("ai", "✦", "AI"),
        NativeNavItem("tools", "▦", "功能"),
        NativeNavItem("settings", "⚙", "设置"),
    )
    val corner by animateDpAsState(
        targetValue = if (glassMode == GlassMode.Liquid) 32.dp else 26.dp,
        animationSpec = spring(stiffness = 140f, dampingRatio = 0.78f),
        label = "navCorner",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .shadow(30.dp, RoundedCornerShape(corner), clip = false),
        shape = RoundedCornerShape(corner),
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (glassMode == GlassMode.Safe) 0.15f else 0.20f),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f),
                            androidx.compose.ui.graphics.Color(0x226AD7FF),
                        ),
                    ),
                )
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NativeGlassNavButton(
                    item = item,
                    selected = selectedView == item.view,
                    glassMode = glassMode,
                    onClick = { onSelected(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NativeGlassNavButton(
    item: NativeNavItem,
    selected: Boolean,
    glassMode: GlassMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 0.96f,
        animationSpec = spring(stiffness = 210f, dampingRatio = 0.72f),
        label = "navScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.84f),
        label = "navAlpha",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            androidx.compose.ui.graphics.Color.White.copy(alpha = if (glassMode == GlassMode.Safe) 0.24f else 0.31f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = spring(stiffness = 170f, dampingRatio = 0.78f),
        label = "navBg",
    )

    Box(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f),
                        RoundedCornerShape(22.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.icon,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = if (selected) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (selected) {
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = item.label,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
