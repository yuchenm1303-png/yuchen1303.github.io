package com.yuchen.ailedger.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

internal enum class ProjectPreviewDisplayMode {
    Inline,
    Fullscreen,
}

/**
 * Shared WebView runtime for project artifacts.
 *
 * It only affects the isolated preview process. Project source files are never rewritten. Wide
 * layouts are fitted to the available viewport, pinch zoom remains available, and touch gestures
 * stay with the WebView instead of being intercepted by the surrounding Compose message list.
 */
@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Suppress("DEPRECATION")
internal fun WebView.configureProjectPreviewRuntime(mode: ProjectPreviewDisplayMode) {
    setBackgroundColor(AndroidColor.TRANSPARENT)
    isVerticalScrollBarEnabled = true
    isHorizontalScrollBarEnabled = true
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    isNestedScrollingEnabled = true
    isFocusable = true
    isFocusableInTouchMode = true

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

        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        textZoom = 100

        cacheMode = WebSettings.LOAD_NO_CACHE
        loadsImagesAutomatically = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
    }
    setInitialScale(0)

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> {
                view.requestFocus()
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }

    if (mode == ProjectPreviewDisplayMode.Fullscreen) {
        requestFocus()
    }
}

/**
 * Adds preview-only viewport compatibility to HTML responses.
 *
 * Missing viewport metadata is supplied before the page is parsed. A tiny runtime check only
 * unlocks an axis when the rendered document is actually larger than the viewport, so normal
 * responsive pages keep their own scrolling model while oversized generated pages remain usable.
 */
internal fun File.openProjectPreviewStream(mimeType: String): InputStream {
    if (extension.lowercase() !in setOf("html", "htm") && mimeType != "text/html") {
        return FileInputStream(this)
    }

    val source = readText(Charsets.UTF_8)
    val viewport = if (VIEWPORT_PATTERN.containsMatchIn(source)) {
        ""
    } else {
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, minimum-scale=0.25, maximum-scale=5, user-scalable=yes\">"
    }
    val compatibility = viewport + PREVIEW_COMPATIBILITY_SCRIPT
    val patched = when {
        HEAD_OPEN_PATTERN.containsMatchIn(source) -> HEAD_OPEN_PATTERN.replaceFirst(source) { match ->
            match.value + compatibility
        }
        HTML_OPEN_PATTERN.containsMatchIn(source) -> HTML_OPEN_PATTERN.replaceFirst(source) { match ->
            match.value + "<head>$compatibility</head>"
        }
        else -> "<!doctype html><html><head>$compatibility</head><body>$source</body></html>"
    }
    return ByteArrayInputStream(patched.toByteArray(Charsets.UTF_8))
}

private val VIEWPORT_PATTERN = Regex(
    "<meta[^>]+name\\s*=\\s*['\"]viewport['\"][^>]*>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HEAD_OPEN_PATTERN = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
private val HTML_OPEN_PATTERN = Regex("<html(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)

private const val PREVIEW_COMPATIBILITY_SCRIPT = """
<script id="ai-ledger-preview-compat">
(function () {
  function unlockOverflowWhenNeeded() {
    var root = document.documentElement;
    var body = document.body;
    if (!root || !body) return;
    var viewportWidth = Math.max(root.clientWidth || 0, window.innerWidth || 0);
    var viewportHeight = Math.max(root.clientHeight || 0, window.innerHeight || 0);
    var contentWidth = Math.max(root.scrollWidth || 0, body.scrollWidth || 0);
    var contentHeight = Math.max(root.scrollHeight || 0, body.scrollHeight || 0);
    if (contentWidth > viewportWidth + 2) {
      root.style.setProperty('overflow-x', 'auto', 'important');
      body.style.setProperty('overflow-x', 'auto', 'important');
    }
    if (contentHeight > viewportHeight + 2) {
      root.style.setProperty('overflow-y', 'auto', 'important');
      body.style.setProperty('overflow-y', 'auto', 'important');
    }
  }
  window.addEventListener('load', function () {
    unlockOverflowWhenNeeded();
    setTimeout(unlockOverflowWhenNeeded, 120);
    setTimeout(unlockOverflowWhenNeeded, 600);
  }, { once: true });
  if (window.ResizeObserver) {
    new ResizeObserver(unlockOverflowWhenNeeded).observe(document.documentElement);
  }
})();
</script>
"""