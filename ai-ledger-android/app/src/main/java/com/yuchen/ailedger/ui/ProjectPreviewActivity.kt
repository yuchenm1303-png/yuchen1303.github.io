package com.yuchen.ailedger.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
private const val PROJECT_RUNTIME_DOMAIN = "project.ai-ledger.local"

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
                            webView.loadUrl(projectRuntimeEntryUrl(preview))
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

private fun createProjectWebView(
    context: Context,
    preview: ProjectPreviewEntry,
    onError: (String) -> Unit,
    onRenderProcessGone: () -> Unit,
): WebView = WebView(context).apply {
    configureProjectPreviewRuntime(ProjectPreviewDisplayMode.Fullscreen)
    webViewClient = LocalProjectWebViewClient(
        projectId = preview.project.projectId,
        projectRoot = preview.projectRoot,
        onError = onError,
        onRenderProcessGone = onRenderProcessGone,
    )
}

private class LocalProjectWebViewClient(
    projectId: String,
    projectRoot: File,
    private val onError: (String) -> Unit,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {
    private val canonicalRoot = projectRoot.canonicalFile
    private val allowedHost = projectRuntimeHost(projectId)

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return !isAllowedNavigation(request?.url)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return !isAllowedNavigation(runCatching { Uri.parse(url) }.getOrNull())
    }

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
        if (request?.isForMainFrame == true) {
            onError("页面加载失败：${error?.description?.toString().orEmpty().take(100)}")
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.destroy()
        onRenderProcessGone()
        return true
    }

    private fun isAllowedNavigation(uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "about", "data", "blob" -> true
            "https" -> uri.host == allowedHost
            else -> false
        }
    }

    private fun serveProjectFile(uri: Uri): WebResourceResponse {
        val relativePath = Uri.decode(uri.encodedPath.orEmpty())
            .trimStart('/')
            .ifBlank { "index.html" }
        val file = runCatching { File(canonicalRoot, relativePath).canonicalFile }.getOrNull()
            ?: return blockedResponse(404, "Not Found")
        if (!file.isFile || (file != canonicalRoot && !file.path.startsWith(canonicalRoot.path + File.separator))) {
            return blockedResponse(404, "Not Found")
        }
        val mimeType = mimeTypeFor(file)
        val encoding = if (
            mimeType.startsWith("text/") ||
            mimeType.contains("javascript") ||
            mimeType == "application/json" ||
            mimeType == "image/svg+xml"
        ) {
            "utf-8"
        } else {
            null
        }
        val headers = linkedMapOf(
            "Cache-Control" to "no-store",
            "Content-Security-Policy" to "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'",
            "Cross-Origin-Resource-Policy" to "same-origin",
            "Referrer-Policy" to "no-referrer",
            "X-Content-Type-Options" to "nosniff",
        )
        return runCatching {
            WebResourceResponse(
                mimeType,
                encoding,
                200,
                "OK",
                headers,
                file.openProjectPreviewStream(mimeType),
            )
        }.getOrElse {
            blockedResponse(500, "Read Failed")
        }
    }

    private fun mimeTypeFor(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "html", "htm" -> "text/html"
                "js", "mjs" -> "text/javascript"
                "json", "map" -> "application/json"
                "svg" -> "image/svg+xml"
                "webp" -> "image/webp"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                else -> "application/octet-stream"
            }
    }

    private fun blockedResponse(statusCode: Int = 403, reason: String = "Blocked"): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        statusCode,
        reason,
        mapOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
        ),
        ByteArrayInputStream(ByteArray(0)),
    )
}

private fun projectRuntimeEntryUrl(preview: ProjectPreviewEntry): String {
    val entryPath = preview.project.entryFile.trim('/').ifBlank { "index.html" }
    return Uri.Builder()
        .scheme("https")
        .authority(projectRuntimeHost(preview.project.projectId))
        .appendEncodedPath(entryPath.split('/').joinToString("/") { Uri.encode(it) })
        .build()
        .toString()
}

private fun projectRuntimeHost(projectId: String): String {
    val safeLabel = projectId
        .lowercase()
        .replace('_', '-')
        .filter { it.isLetterOrDigit() || it == '-' }
        .take(50)
        .trim('-')
        .ifBlank { "project" }
    return "$safeLabel.$PROJECT_RUNTIME_DOMAIN"
}
