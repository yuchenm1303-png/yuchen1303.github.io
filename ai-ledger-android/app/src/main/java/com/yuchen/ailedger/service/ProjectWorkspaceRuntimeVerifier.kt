package com.yuchen.ailedger.service

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

private const val PROJECT_RUNTIME_VERIFIER_VERSION = "webview-runtime-smoke-v1"
private const val PROJECT_RUNTIME_DOMAIN = "project.ai-ledger.local"
private const val PROJECT_RUNTIME_TIMEOUT_MS = 12_000L
private const val PROJECT_RUNTIME_PROBE_DELAY_MS = 350L
private const val PROJECT_RUNTIME_PROBE_RETRY_DELAY_MS = 450L
private const val PROJECT_RUNTIME_PROBE_ATTEMPTS = 5
private const val PROJECT_RUNTIME_CACHE_SIZE = 32

/**
 * Executes a real, isolated WebView smoke test for the exact project revision before delivery.
 *
 * Static validation remains responsible for source-level rules. This verifier supplies environment
 * ground truth: the page must actually load, JavaScript must not fail, and meaningful content must
 * become visible inside a phone-sized viewport. It never edits project files or makes aesthetic
 * decisions.
 */
internal class ProjectWorkspaceRuntimeVerifier(
    context: Context,
    private val store: ProjectWorkspaceStore,
) {
    private val appContext = context.applicationContext

    fun validate(projectId: String, revisionId: String? = null): List<AgentVerificationIssue> {
        val preview = store.resolveRuntimeEntry(projectId, revisionId)
        val cacheKey = "$PROJECT_RUNTIME_VERIFIER_VERSION:${preview.project.projectId}:${preview.revisionId}"
        synchronized(cacheLock) {
            resultCache[cacheKey]?.let { cached ->
                return cached.map { it.copy() }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return listOf(
                issue(
                    code = "runtime_verifier_main_thread",
                    severity = AgentVerificationSeverity.Error,
                    message = "运行时验证无法在主线程同步执行。",
                    suggestion = "请在后台工具执行线程重新运行项目验证。",
                ),
            )
        }

        val result = runWebViewSmokeTest(preview)
        synchronized(cacheLock) {
            resultCache[cacheKey] = result.map { it.copy() }
            while (resultCache.size > PROJECT_RUNTIME_CACHE_SIZE) {
                resultCache.entries.firstOrNull()?.key?.let(resultCache::remove)
            }
        }
        return result
    }

    private fun runWebViewSmokeTest(preview: ProjectPreviewEntry): List<AgentVerificationIssue> {
        val done = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val issuesRef = AtomicReference<List<AgentVerificationIssue>>(emptyList())
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            var webView: WebView? = null
            val collectedErrors = mutableListOf<AgentVerificationIssue>()

            fun finish(extraIssues: List<AgentVerificationIssue> = emptyList()) {
                if (!finished.compareAndSet(false, true)) return
                val merged = (collectedErrors + extraIssues)
                    .distinctBy { "${it.code}|${it.message}|${it.file}|${it.line}" }
                    .take(40)
                issuesRef.set(merged)
                webView?.let { view ->
                    runCatching { view.stopLoading() }
                    runCatching { view.loadUrl("about:blank") }
                    runCatching { view.clearHistory() }
                    runCatching { view.removeAllViews() }
                    runCatching { view.destroy() }
                }
                webView = null
                done.countDown()
            }

            try {
                val runtimeHost = projectRuntimeHost(preview.project.projectId)
                webView = WebView(appContext).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.setGeolocationEnabled(false)
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, 1080, 1920)
                }

                webView?.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val item = consoleMessage ?: return false
                        if (item.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            collectedErrors += issue(
                                code = "runtime_javascript_error",
                                severity = AgentVerificationSeverity.Error,
                                message = "网页运行时 JavaScript 报错：${item.message().take(240)}",
                                file = item.sourceId().substringAfterLast('/').takeIf(String::isNotBlank),
                                line = item.lineNumber().takeIf { it > 0 },
                                suggestion = "修复脚本错误后重新运行项目验证。",
                            )
                        }
                        return true
                    }
                }

                webView?.webViewClient = object : WebViewClient() {
                    private var pageFinished = false

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return !isAllowedNavigation(request?.url, runtimeHost)
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return !isAllowedNavigation(runCatching { Uri.parse(url) }.getOrNull(), runtimeHost)
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val uri = request?.url ?: return blockedResponse()
                        if (
                            uri.scheme.equals("https", ignoreCase = true) &&
                            uri.host == runtimeHost &&
                            request.method.equals("GET", ignoreCase = true)
                        ) {
                            return serveProjectFile(preview.projectRoot, uri)
                        }
                        return when (uri.scheme?.lowercase()) {
                            "about", "data", "blob" -> null
                            else -> blockedResponse()
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            finish(
                                listOf(
                                    issue(
                                        code = "runtime_page_load_failed",
                                        severity = AgentVerificationSeverity.Error,
                                        message = "网页主文档加载失败：${error?.description?.toString().orEmpty().take(180)}",
                                        suggestion = "检查入口文件和本地资源引用后重新验证。",
                                    ),
                                ),
                            )
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.url?.host == runtimeHost && (errorResponse?.statusCode ?: 200) >= 400) {
                            collectedErrors += issue(
                                code = "runtime_local_resource_failed",
                                severity = AgentVerificationSeverity.Error,
                                message = "网页运行时无法加载本地资源：${request.url.encodedPath.orEmpty().take(180)}（HTTP ${errorResponse?.statusCode}）。",
                                suggestion = "创建缺失文件或修正 HTML/CSS/JavaScript 中的相对路径。",
                            )
                        }
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        finish(
                            listOf(
                                issue(
                                    code = "runtime_renderer_crashed",
                                    severity = AgentVerificationSeverity.Error,
                                    message = "网页渲染进程在验证期间异常退出。",
                                    suggestion = "减少高负载动画、超大画布或无限循环脚本后重新验证。",
                                ),
                            ),
                        )
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (pageFinished || view == null || finished.get()) return
                        pageFinished = true
                        handler.postDelayed(
                            { probeVisibleRuntime(view, 1, collectedErrors, ::finish) },
                            PROJECT_RUNTIME_PROBE_DELAY_MS,
                        )
                    }
                }

                webView?.loadUrl(projectRuntimeEntryUrl(preview))
                handler.postDelayed(
                    {
                        if (!finished.get()) {
                            finish(
                                listOf(
                                    issue(
                                        code = "runtime_timeout",
                                        severity = AgentVerificationSeverity.Error,
                                        message = "网页在 ${PROJECT_RUNTIME_TIMEOUT_MS / 1000} 秒内没有完成运行时验证。",
                                        suggestion = "检查阻塞主线程、无限循环、过重动画或永不结束的初始化逻辑。",
                                    ),
                                ),
                            )
                        }
                    },
                    PROJECT_RUNTIME_TIMEOUT_MS,
                )
            } catch (error: Throwable) {
                finish(
                    listOf(
                        issue(
                            code = "runtime_verifier_failed",
                            severity = AgentVerificationSeverity.Error,
                            message = "无法启动网页运行时验证：${error.message?.take(180) ?: error::class.java.simpleName}",
                            suggestion = "确认系统 WebView 可用后重新验证。",
                        ),
                    ),
                )
            }
        }

        if (!done.await(PROJECT_RUNTIME_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS)) {
            return listOf(
                issue(
                    code = "runtime_timeout",
                    severity = AgentVerificationSeverity.Error,
                    message = "网页运行时验证等待超时。",
                    suggestion = "检查系统 WebView 和页面初始化逻辑。",
                ),
            )
        }
        return issuesRef.get()
    }

    private fun probeVisibleRuntime(
        webView: WebView,
        attempt: Int,
        collectedErrors: List<AgentVerificationIssue>,
        finish: (List<AgentVerificationIssue>) -> Unit,
    ) {
        if (attempt > PROJECT_RUNTIME_PROBE_ATTEMPTS) {
            finish(
                listOf(
                    issue(
                        code = "runtime_probe_failed",
                        severity = AgentVerificationSeverity.Error,
                        message = "网页运行后无法取得有效的可见性检测结果。",
                        suggestion = "检查页面是否持续重载、阻塞主线程或破坏了文档根节点。",
                    ),
                ),
            )
            return
        }
        webView.evaluateJavascript(RUNTIME_PROBE_SCRIPT) { encoded ->
            val probe = decodeJavascriptObject(encoded)
            if (probe == null || probe.has("probeError")) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { probeVisibleRuntime(webView, attempt + 1, collectedErrors, finish) },
                    PROJECT_RUNTIME_PROBE_RETRY_DELAY_MS,
                )
                return@evaluateJavascript
            }

            val readyState = probe.optString("readyState")
            val visibleMeaningful = probe.optInt("visibleMeaningful", 0)
            val visibleInteractive = probe.optInt("visibleInteractive", 0)
            val visibleMedia = probe.optInt("visibleMedia", 0)
            val bodyTextLength = probe.optInt("bodyTextLength", 0)
            val viewportWidth = probe.optInt("viewportWidth", 0)
            val viewportHeight = probe.optInt("viewportHeight", 0)
            val documentWidth = probe.optInt("documentWidth", 0)
            val documentHeight = probe.optInt("documentHeight", 0)
            val rootOpacity = probe.optDouble("rootOpacity", 1.0)
            val bodyOpacity = probe.optDouble("bodyOpacity", 1.0)
            val rootDisplay = probe.optString("rootDisplay")
            val bodyDisplay = probe.optString("bodyDisplay")
            val rootVisibility = probe.optString("rootVisibility")
            val bodyVisibility = probe.optString("bodyVisibility")

            val documentReady = readyState == "complete" || readyState == "interactive"
            val viewportReady = viewportWidth > 0 && viewportHeight > 0
            val hasVisibleContent = visibleMeaningful > 0 || visibleInteractive > 0 || visibleMedia > 0
            val rootHidden = rootDisplay == "none" || bodyDisplay == "none" ||
                rootVisibility == "hidden" || bodyVisibility == "hidden" ||
                rootOpacity <= 0.01 || bodyOpacity <= 0.01

            if ((!documentReady || !viewportReady || !hasVisibleContent) && attempt < PROJECT_RUNTIME_PROBE_ATTEMPTS) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { probeVisibleRuntime(webView, attempt + 1, collectedErrors, finish) },
                    PROJECT_RUNTIME_PROBE_RETRY_DELAY_MS,
                )
                return@evaluateJavascript
            }

            val issues = mutableListOf<AgentVerificationIssue>()
            if (!documentReady || !viewportReady) {
                issues += issue(
                    code = "runtime_document_not_ready",
                    severity = AgentVerificationSeverity.Error,
                    message = "网页文档没有进入可交互状态，或运行时视口尺寸无效。",
                    suggestion = "检查页面初始化脚本和文档结构。",
                )
            }
            if (rootHidden) {
                issues += issue(
                    code = "runtime_root_hidden",
                    severity = AgentVerificationSeverity.Error,
                    message = "网页根节点或 body 在运行后仍被隐藏。",
                    suggestion = "不要让核心内容依赖 JavaScript 动画才能显示；脚本失败时也应保持正文可见。",
                )
            }
            if (!hasVisibleContent) {
                issues += issue(
                    code = if (bodyTextLength == 0) "runtime_content_empty" else "runtime_no_visible_content",
                    severity = AgentVerificationSeverity.Error,
                    message = if (bodyTextLength == 0) {
                        "网页运行后没有可交付的正文、媒体或交互内容。"
                    } else {
                        "网页包含内容，但手机视口内没有检测到可见的正文、媒体或交互元素。"
                    },
                    suggestion = "确保核心内容位于首屏视口内，并移除导致整体透明、隐藏或移出屏幕的样式。",
                )
            }
            if (documentWidth > viewportWidth + 24) {
                issues += issue(
                    code = "runtime_horizontal_overflow",
                    severity = AgentVerificationSeverity.Warning,
                    message = "网页实际宽度为 ${documentWidth}px，超过 ${viewportWidth}px 手机验证视口，可能出现横向裁切。",
                    suggestion = "使用 width:100%、max-width 和响应式断点，避免固定桌面画布。",
                )
            }
            if (documentHeight <= 1) {
                issues += issue(
                    code = "runtime_document_height_invalid",
                    severity = AgentVerificationSeverity.Error,
                    message = "网页运行后的文档高度无效。",
                    suggestion = "检查根容器高度、绝对定位和折叠布局。",
                )
            }
            if (collectedErrors.none { it.severity == AgentVerificationSeverity.Error } && issues.none { it.severity == AgentVerificationSeverity.Error }) {
                issues += issue(
                    code = "runtime_smoke_test_passed",
                    severity = AgentVerificationSeverity.Info,
                    message = "网页已在隔离 WebView 中完成真实加载，并检测到手机视口内的可见内容。",
                )
            }
            finish(issues)
        }
    }

    private fun serveProjectFile(projectRoot: File, uri: Uri): WebResourceResponse {
        val canonicalRoot = runCatching { projectRoot.canonicalFile }.getOrNull()
            ?: return blockedResponse(500, "Invalid Root")
        val relativePath = Uri.decode(uri.encodedPath.orEmpty()).trimStart('/').ifBlank { "index.html" }
        val file = runCatching { File(canonicalRoot, relativePath).canonicalFile }.getOrNull()
            ?: return blockedResponse(404, "Not Found")
        if (!file.isFile || !file.path.startsWith(canonicalRoot.path + File.separator)) {
            return blockedResponse(404, "Not Found")
        }
        val mimeType = mimeTypeFor(file)
        val encoding = if (
            mimeType.startsWith("text/") ||
            mimeType.contains("javascript") ||
            mimeType == "application/json" ||
            mimeType == "image/svg+xml"
        ) "utf-8" else null
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
                file.inputStream(),
            )
        }.getOrElse { blockedResponse(500, "Read Failed") }
    }

    private fun isAllowedNavigation(uri: Uri?, runtimeHost: String): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "about", "data", "blob" -> true
            "https" -> uri.host == runtimeHost
            else -> false
        }
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

    private fun mimeTypeFor(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "html", "htm" -> "text/html"
                "css" -> "text/css"
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
        mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
        ByteArrayInputStream(ByteArray(0)),
    )

    private fun decodeJavascriptObject(encoded: String?): JSONObject? {
        val raw = encoded?.trim().orEmpty()
        if (raw.isBlank() || raw == "null" || raw == "undefined") return null
        return runCatching {
            val decoded = if (raw.startsWith('"')) JSONArray("[$raw]").getString(0) else raw
            JSONObject(decoded)
        }.getOrNull()
    }

    private fun issue(
        code: String,
        severity: AgentVerificationSeverity,
        message: String,
        file: String? = null,
        line: Int? = null,
        suggestion: String? = null,
    ): AgentVerificationIssue = AgentVerificationIssue(
        code = code,
        severity = severity,
        message = message.take(500),
        file = file,
        line = line,
        suggestion = suggestion?.take(500),
    )

    companion object {
        private val cacheLock = Any()
        private val resultCache = linkedMapOf<String, List<AgentVerificationIssue>>()

        private val RUNTIME_PROBE_SCRIPT = """
            (() => {
              try {
                const root = document.documentElement;
                const body = document.body;
                if (!root || !body) return JSON.stringify({ readyState: document.readyState, missingBody: true });
                const viewportWidth = Math.max(window.innerWidth || 0, root.clientWidth || 0);
                const viewportHeight = Math.max(window.innerHeight || 0, root.clientHeight || 0);
                const tagsToIgnore = new Set(['SCRIPT','STYLE','META','LINK','HEAD','TITLE','NOSCRIPT','TEMPLATE']);
                let visibleMeaningful = 0;
                let visibleInteractive = 0;
                let visibleMedia = 0;
                const elements = Array.from(body.querySelectorAll('*'));
                for (const el of elements) {
                  if (tagsToIgnore.has(el.tagName)) continue;
                  const style = getComputedStyle(el);
                  if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity || 1) <= 0.01) continue;
                  const rect = el.getBoundingClientRect();
                  if (rect.width < 1 || rect.height < 1) continue;
                  const inViewport = rect.bottom > 0 && rect.right > 0 && rect.top < viewportHeight && rect.left < viewportWidth;
                  if (!inViewport) continue;
                  const tag = el.tagName;
                  const interactive = ['BUTTON','A','INPUT','SELECT','TEXTAREA','SUMMARY'].includes(tag) || el.getAttribute('role') === 'button';
                  const media = ['IMG','SVG','CANVAS','VIDEO','AUDIO','IFRAME'].includes(tag);
                  const directText = Array.from(el.childNodes).some(node => node.nodeType === Node.TEXT_NODE && node.textContent.trim().length > 0);
                  if (interactive) visibleInteractive += 1;
                  if (media) visibleMedia += 1;
                  if (interactive || media || directText) visibleMeaningful += 1;
                }
                const rootStyle = getComputedStyle(root);
                const bodyStyle = getComputedStyle(body);
                return JSON.stringify({
                  readyState: document.readyState,
                  viewportWidth,
                  viewportHeight,
                  documentWidth: Math.max(root.scrollWidth, body.scrollWidth, root.clientWidth, body.clientWidth),
                  documentHeight: Math.max(root.scrollHeight, body.scrollHeight, root.clientHeight, body.clientHeight),
                  bodyTextLength: (body.innerText || '').trim().length,
                  visibleMeaningful,
                  visibleInteractive,
                  visibleMedia,
                  rootDisplay: rootStyle.display,
                  bodyDisplay: bodyStyle.display,
                  rootVisibility: rootStyle.visibility,
                  bodyVisibility: bodyStyle.visibility,
                  rootOpacity: Number(rootStyle.opacity || 1),
                  bodyOpacity: Number(bodyStyle.opacity || 1)
                });
              } catch (error) {
                return JSON.stringify({ probeError: String(error && error.message || error) });
              }
            })();
        """.trimIndent()
    }
}
