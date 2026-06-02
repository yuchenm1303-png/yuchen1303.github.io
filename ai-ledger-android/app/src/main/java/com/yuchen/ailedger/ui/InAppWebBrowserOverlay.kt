package com.yuchen.ailedger.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.model.AssistantUiState

data class WebPreviewSource(
    val title: String,
    val url: String,
    val domain: String = ""
)

val LocalWebSourceOpener = compositionLocalOf<(WebPreviewSource) -> Unit> { {} }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppWebBrowserOverlay(
    target: WebPreviewSource?,
    state: AssistantUiState,
    onDismiss: () -> Unit
) {
    val safeTarget = target
    AnimatedVisibility(
        visible = safeTarget != null,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.975f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.985f, animationSpec = tween(140))
    ) {
        if (safeTarget == null) return@AnimatedVisibility

        var progress by remember(safeTarget.url) { mutableStateOf(0) }
        var pageTitle by remember(safeTarget.url) { mutableStateOf(safeTarget.title) }
        var currentUrl by remember(safeTarget.url) { mutableStateOf(safeTarget.url) }
        var canGoBack by remember(safeTarget.url) { mutableStateOf(false) }
        var webViewRef by remember(safeTarget.url) { mutableStateOf<WebView?>(null) }
        val progressFraction by animateFloatAsState(
            targetValue = progress.coerceIn(0, 100) / 100f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            label = "in-app-web-progress"
        )

        BackHandler(enabled = true) {
            val view = webViewRef
            if (view != null && view.canGoBack()) {
                view.goBack()
                canGoBack = view.canGoBack()
            } else {
                onDismiss()
            }
        }

        DisposableEffect(safeTarget.url) {
            onDispose {
                webViewRef?.stopLoading()
                webViewRef = null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .padding(horizontal = 14.dp, vertical = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.09f),
                                Color.Black.copy(alpha = 0.20f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color(0xFF8DF9EA).copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        shape = RoundedCornerShape(34.dp)
                    )
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    WebBrowserTopBar(
                        title = pageTitle.ifBlank { safeTarget.title },
                        url = currentUrl,
                        domain = safeTarget.domain,
                        canGoBack = canGoBack,
                        onBack = {
                            webViewRef?.goBack()
                            canGoBack = webViewRef?.canGoBack() == true
                        },
                        onReload = { webViewRef?.reload() },
                        onClose = onDismiss
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(3.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF8DF9EA).copy(alpha = 0.86f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    setBackgroundColor(AndroidColor.WHITE)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress
                                            pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: pageTitle
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            progress = 5
                                            currentUrl = url ?: currentUrl
                                            canGoBack = view?.canGoBack() == true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            progress = 100
                                            currentUrl = url ?: currentUrl
                                            pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: pageTitle
                                            canGoBack = view?.canGoBack() == true
                                        }
                                    }
                                    webViewRef = this
                                    loadUrl(safeTarget.url)
                                }
                            },
                            update = { view ->
                                webViewRef = view
                                if (view.url != safeTarget.url && currentUrl == safeTarget.url) {
                                    view.loadUrl(safeTarget.url)
                                }
                            }
                        )
                    }

                    Text(
                        text = "网页内容由原站点提供。若页面无法加载，可稍后重试或打开外部浏览器。",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    state.quality.hashCode()
}

@Composable
private fun WebBrowserTopBar(
    title: String,
    url: String,
    domain: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit
) {
    val displayDomain = domain.ifBlank { url.removePrefix("https://").removePrefix("http://").substringBefore("/") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.11f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BrowserRoundButton(text = "‹", enabled = canGoBack, onClick = onBack)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title.ifBlank { "网页预览" },
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF8DF9EA).copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "来源",
                        color = Color(0xFF8DF9EA).copy(alpha = 0.86f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = displayDomain,
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        BrowserRoundButton(text = "↻", enabled = true, onClick = onReload)
        BrowserRoundButton(text = "×", enabled = true, onClick = onClose)
    }
}

@Composable
private fun BrowserRoundButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 0.92f else 0.30f
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.055f))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (text == "×") 20.sp else 19.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}
