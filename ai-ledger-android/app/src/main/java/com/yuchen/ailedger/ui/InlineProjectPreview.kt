package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.service.ProjectPreviewEntry
import com.yuchen.ailedger.service.ProjectWorkspaceStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INLINE_PROJECT_RUNTIME_DOMAIN = "project.ai-ledger.local"

private object InlineProjectPreviewCoordinator {
    var activeKey by mutableStateOf<String?>(null)
        private set

    fun activate(key: String) {
        activeKey = key
    }

    fun claimIfEmpty(key: String) {
        if (activeKey == null) activeKey = key
    }

    fun release(key: String) {
        if (activeKey == key) activeKey = null
    }
}

@Composable
internal fun InlineProjectPreview(
    descriptor: ProjectPreviewDescriptor,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preview by produceState<ProjectPreviewEntry?>(
        initialValue = null,
        key1 = descriptor.projectId,
        key2 = descriptor.revisionId,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ProjectWorkspaceStore(context.applicationContext)
                    .resolvePreviewEntry(descriptor.projectId, descriptor.revisionId)
            }.getOrNull()
        }
    }
    val activeKey = InlineProjectPreviewCoordinator.activeKey
    val isActive = activeKey == descriptor.stableKey
    var webView by remember(descriptor.stableKey) { mutableStateOf<WebView?>(null) }
    var loadError by remember(descriptor.stableKey) { mutableStateOf(false) }

    LaunchedEffect(descriptor.stableKey, activeKey) {
        InlineProjectPreviewCoordinator.claimIfEmpty(descriptor.stableKey)
    }
    DisposableEffect(descriptor.stableKey, isActive) {
        onDispose {
            if (isActive) InlineProjectPreviewCoordinator.release(descriptor.stableKey)
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.clearHistory()
            webView?.removeAllViews()
            webView?.destroy()
            webView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(188.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF080D15)),
        contentAlignment = Alignment.Center,
    ) {
        val resolved = preview
        when {
            resolved == null -> Text(
                text = "正在准备网页预览…",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            !isActive -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.035f))
                    .clickable {
                        loadError = false
                        InlineProjectPreviewCoordinator.activate(descriptor.stableKey)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "点击启用交互预览",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        createInlineProjectWebView(
                            context = viewContext,
                            preview = resolved,
                            onError = { loadError = true },
                            onRenderProcessGone = {
                                loadError = true
                                InlineProjectPreviewCoordinator.release(descriptor.stableKey)
                            },
                        ).also { created ->
                            webView = created
                            created.loadUrl(inlineProjectEntryUrl(resolved))
                        }
                    },
                    update = { webView = it },
                )
                if (loadError) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xE6141118))
                            .clickable {
                                loadError = false
                                webView?.reload()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "预览加载失败，点击重试",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Suppress("SetJavaScriptEnabled", "DEPRECATION")
private fun createInlineProjectWebView(
    context: Context,
    preview: ProjectPreviewEntry,
    onError: () -> Unit,
    onRenderProcessGone: () -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(AndroidColor.TRANSPARENT)
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = WebView.OVER_SCROLL_NEVER
    settings.apply {
        javaScriptEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        domStorageEnabled = true
        databaseEnabled = false
        saveFormData = false
        allowFileAccess = false
        allowContentAccess = false
        setAllowFileAccessFromFileURLs(false)
        setAllowUniversalAccessFromFileURLs(false)
        blockNetworkLoads = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        setGeolocationEnabled(false)
        setSupportMultipleWindows(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        cacheMode = WebSettings.LOAD_NO_CACHE
        loadsImagesAutomatically = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
    }
    webViewClient = InlineProjectWebViewClient(
        projectId = preview.project.projectId,
        projectRoot = preview.projectRoot,
        onError = onError,
        onRenderProcessGone = onRenderProcessGone,
    )
}

private class InlineProjectWebViewClient(
    projectId: String,
    projectRoot: File,
    private val onError: () -> Unit,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {
    private val canonicalRoot = projectRoot.canonicalFile
    private val allowedHost = inlineProjectRuntimeHost(projectId)

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        !isAllowedNavigation(request?.url)

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        !isAllowedNavigation(runCatching { Uri.parse(url) }.getOrNull())

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return blockedResponse()
        if (uri.scheme == "https" && uri.host == allowedHost && request.method.equals("GET", ignoreCase = true)) {
            return serveProjectFile(uri)
        }
        return when (uri.scheme?.lowercase()) {
            "about", "data", "blob" -> null
            else -> blockedResponse()
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) onError()
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.destroy()
        onRenderProcessGone()
        return true
    }

    private fun isAllowedNavigation(uri: Uri?): Boolean = when (uri?.scheme?.lowercase()) {
        "about", "data", "blob" -> true
        "https" -> uri.host == allowedHost
        else -> false
    }

    private fun serveProjectFile(uri: Uri): WebResourceResponse {
        val relativePath = Uri.decode(uri.encodedPath.orEmpty()).trimStart('/').ifBlank { "index.html" }
        val file = runCatching { File(canonicalRoot, relativePath).canonicalFile }.getOrNull()
            ?: return blockedResponse(404, "Not Found")
        if (!file.isFile || !file.path.startsWith(canonicalRoot.path + File.separator)) {
            return blockedResponse(404, "Not Found")
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: when (file.extension.lowercase()) {
                "js", "mjs" -> "text/javascript"
                "json", "map" -> "application/json"
                "svg" -> "image/svg+xml"
                "webp" -> "image/webp"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                else -> "application/octet-stream"
            }
        val encoding = if (mime.startsWith("text/") || mime.contains("javascript") || mime == "application/json" || mime == "image/svg+xml") "utf-8" else null
        return runCatching {
            WebResourceResponse(
                mime,
                encoding,
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Content-Security-Policy" to "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'",
                    "Cross-Origin-Resource-Policy" to "same-origin",
                    "Referrer-Policy" to "no-referrer",
                    "X-Content-Type-Options" to "nosniff",
                ),
                FileInputStream(file),
            )
        }.getOrElse { blockedResponse(500, "Read Failed") }
    }

    private fun blockedResponse(code: Int = 403, reason: String = "Blocked") = WebResourceResponse(
        "text/plain",
        "utf-8",
        code,
        reason,
        mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
        ByteArrayInputStream(ByteArray(0)),
    )
}

private fun inlineProjectEntryUrl(preview: ProjectPreviewEntry): String {
    val entryPath = preview.project.entryFile.trim('/').ifBlank { "index.html" }
    return Uri.Builder()
        .scheme("https")
        .authority(inlineProjectRuntimeHost(preview.project.projectId))
        .appendEncodedPath(entryPath.split('/').joinToString("/") { Uri.encode(it) })
        .build()
        .toString()
}

private fun inlineProjectRuntimeHost(projectId: String): String {
    val safeLabel = projectId.lowercase().replace('_', '-').filter { it.isLetterOrDigit() || it == '-' }
        .take(50).trim('-').ifBlank { "project" }
    return "$safeLabel.$INLINE_PROJECT_RUNTIME_DOMAIN"
}
