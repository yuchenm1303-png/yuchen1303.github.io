package com.yuchen.ailedger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuchen.ailedger.service.ProjectPreviewEntry
import com.yuchen.ailedger.service.ProjectWorkspaceStore
import java.io.ByteArrayInputStream
import java.io.File

private const val PROJECT_PREVIEW_HOST = "project.ai-ledger.local"
private const val PROJECT_PREVIEW_PATH = "/open"

class ProjectPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK

        val target = parseTarget(intent?.data)
        val preview = target?.let { (projectId, revisionId) ->
            runCatching {
                ProjectWorkspaceStore(this).resolvePreviewEntry(projectId, revisionId)
            }.getOrNull()
        }

        setContent {
            AccessibilitySilentComposeRoot {
                ProjectPreviewScreen(
                    preview = preview,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        fun canOpen(url: String): Boolean = parseTarget(runCatching { Uri.parse(url) }.getOrNull()) != null

        fun createIntent(context: Context, url: String): Intent = Intent(context, ProjectPreviewActivity::class.java).apply {
            data = Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        private fun parseTarget(uri: Uri?): Pair<String, String?>? {
            if (uri == null || uri.scheme != "https" || uri.host != PROJECT_PREVIEW_HOST || uri.path != PROJECT_PREVIEW_PATH) return null
            val projectId = uri.getQueryParameter("projectId")?.trim().orEmpty()
            if (!projectId.matches(Regex("project_[a-zA-Z0-9]{8,40}"))) return null
            val revisionId = uri.getQueryParameter("revision")
                ?.trim()
                ?.takeIf { it.matches(Regex("rev_\\d{6}")) }
            return projectId to revisionId
        }
    }
}

@Composable
private fun ProjectPreviewScreen(
    preview: ProjectPreviewEntry?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var pageError by remember(preview?.revisionId) { mutableStateOf<String?>(null) }

    DisposableEffect(preview?.project?.projectId, preview?.revisionId) {
        onDispose {
            activeWebView?.stopLoading()
            activeWebView?.loadUrl("about:blank")
            activeWebView?.clearHistory()
            activeWebView?.removeAllViews()
            activeWebView?.destroy()
            activeWebView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A10))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B1019))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewTopAction("返回", onClose)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = preview?.project?.name ?: "项目预览",
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview?.let { "${it.revisionId} · 本地隔离预览" } ?: "项目不可用",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            if (preview != null) {
                PreviewTopAction("刷新") {
                    pageError = null
                    activeWebView?.reload()
                }
            }
        }

        if (preview == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("项目预览不可用", color = Color.White.copy(alpha = 0.88f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text(
                        "项目可能已被删除，或当前版本文件不完整。",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        createProjectWebView(
                            context = context,
                            preview = preview,
                            onError = { pageError = it },
                            onRenderProcessGone = onClose,
                        ).also { webView ->
                            activeWebView = webView
                            webView.loadUrl(Uri.fromFile(preview.entryFile).toString())
                        }
                    },
                    update = { webView -> activeWebView = webView },
                )
                pageError?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFFFC1C1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE61A1013))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewTopAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.78f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Suppress("SetJavaScriptEnabled", "DEPRECATION")
private fun createProjectWebView(
    context: Context,
    preview: ProjectPreviewEntry,
    onError: (String) -> Unit,
    onRenderProcessGone: () -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(AndroidColor.TRANSPARENT)
    settings.apply {
        javaScriptEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        domStorageEnabled = true
        databaseEnabled = false
        saveFormData = false
        allowFileAccess = true
        allowContentAccess = false
        setAllowFileAccessFromFileURLs(false)
        setAllowUniversalAccessFromFileURLs(false)
        blockNetworkLoads = true
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
    webViewClient = LocalProjectWebViewClient(
        projectRoot = preview.projectRoot,
        onError = onError,
        onRenderProcessGone = onRenderProcessGone,
    )
}

private class LocalProjectWebViewClient(
    projectRoot: File,
    private val onError: (String) -> Unit,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {
    private val canonicalRoot = projectRoot.canonicalFile

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return !isAllowed(request?.url)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return !isAllowed(runCatching { Uri.parse(url) }.getOrNull())
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        return if (isAllowed(request?.url)) null else blockedResponse()
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onError("页面加载失败：${error?.description?.toString().orEmpty().take(100)}")
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.destroy()
        onRenderProcessGone()
        return true
    }

    private fun isAllowed(uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "about", "data", "blob" -> true
            "file" -> {
                val path = uri.path ?: return false
                val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
                file == canonicalRoot || file.path.startsWith(canonicalRoot.path + File.separator)
            }
            else -> false
        }
    }

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0)),
    )
}
