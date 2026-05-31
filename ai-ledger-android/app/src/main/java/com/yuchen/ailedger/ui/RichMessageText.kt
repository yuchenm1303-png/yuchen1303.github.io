package com.yuchen.ailedger.ui

import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

private val richMessageTokenRegex = Regex(
    pattern = """(\*\*.+?\*\*)|(\\\\\(.+?\\\\\))|(\\\\\[.+?\\\\\])|(\$\$.+?\$\$)|(?m)^\s{0,3}#{1,3}\s+|(?m)^\s*---+\s*$|(?m)^\s*[-*]\s+""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
)

private val inlineMathRegex = Regex(
    pattern = """\\\((.+?)\\\)""",
    options = setOf(RegexOption.DOT_MATCHES_ALL)
)

private val fencedMathRegex = Regex(
    pattern = """\$\$(.+?)\$\$""",
    options = setOf(RegexOption.DOT_MATCHES_ALL)
)

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val resolvedColor = when {
        color != Color.Unspecified -> color
        style.color != Color.Unspecified -> style.color
        else -> Color.White.copy(alpha = 0.86f)
    }
    val allowRichRendering = maxLines == Int.MAX_VALUE &&
        minLines == 1 &&
        overflow == TextOverflow.Clip &&
        richMessageTokenRegex.containsMatchIn(text)

    if (allowRichRendering) {
        RichMessageContent(
            text = text,
            modifier = modifier,
            color = resolvedColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        )
        return
    }

    MaterialText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun RichMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    val density = LocalDensity.current
    val resolvedColor = if (color != Color.Unspecified) color else Color.White.copy(alpha = 0.86f)
    val cssFontPx = remember(fontSize, density) {
        if (fontSize != TextUnit.Unspecified) {
            (with(density) { fontSize.toPx() } / density.density).coerceAtLeast(12f)
        } else {
            13.2f
        }
    }
    val cssLineHeightPx = remember(lineHeight, cssFontPx, density) {
        if (lineHeight != TextUnit.Unspecified) {
            (with(density) { lineHeight.toPx() } / density.density).coerceAtLeast(cssFontPx + 2f)
        } else {
            cssFontPx * 1.42f
        }
    }

    if (richMessageTokenRegex.containsMatchIn(text)) {
        RichMessageText(
            text = text,
            textColor = resolvedColor,
            modifier = modifier.fillMaxWidth(),
            baseFontPx = cssFontPx,
            lineHeightPx = cssLineHeightPx,
            baseFontWeight = if (fontWeight == FontWeight.Bold || fontWeight == FontWeight.ExtraBold || fontWeight == FontWeight.Black) 700 else 500
        )
        return
    }

    MaterialText(
        text = text,
        modifier = modifier,
        color = resolvedColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight
    )
}

@Composable
private fun RichMessageText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    baseFontPx: Float,
    lineHeightPx: Float,
    baseFontWeight: Int
) {
    val density = LocalDensity.current
    val minHeightPx = with(density) { 22.dp.roundToPx() }
    var contentHeightPx by remember(text, density.density, density.fontScale, baseFontPx, lineHeightPx) {
        mutableIntStateOf(minHeightPx)
    }
    val html = remember(text, textColor, baseFontPx, lineHeightPx, baseFontWeight) {
        buildRichMessageHtml(
            markdown = text,
            textColor = textColor,
            baseFontPx = baseFontPx,
            lineHeightPx = lineHeightPx,
            baseFontWeight = baseFontWeight
        )
    }

    AndroidView(
        modifier = modifier.height(with(density) { maxOf(contentHeightPx, minHeightPx).toDp() }),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isLongClickable = false
                isHapticFeedbackEnabled = false
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadsImagesAutomatically = true
                settings.defaultTextEncodingName = "utf-8"

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        scheduleHeightMeasurements(view) { measured ->
                            if (measured > 0 && measured != contentHeightPx) {
                                contentHeightPx = maxOf(measured, minHeightPx)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest?): Boolean = true

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean = true
                }

                tag = html
                loadDataWithBaseURL(
                    "https://app.local/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(
                    "https://app.local/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            } else {
                scheduleHeightMeasurements(webView) { measured ->
                    if (measured > 0 && measured != contentHeightPx) {
                        contentHeightPx = maxOf(measured, minHeightPx)
                    }
                }
            }
        }
    )
}

private fun scheduleHeightMeasurements(
    webView: WebView,
    onMeasured: (Int) -> Unit
) {
    val delays = longArrayOf(0L, 60L, 160L, 320L, 720L)
    delays.forEach { delayMillis ->
        webView.postDelayed(
            { measureContentHeight(webView, onMeasured) },
            delayMillis
        )
    }
}

private fun measureContentHeight(
    webView: WebView,
    onMeasured: (Int) -> Unit
) {
    webView.evaluateJavascript(
        """
        (function() {
            var body = document.body;
            var html = document.documentElement;
            var value = Math.max(
                body ? body.scrollHeight : 0,
                body ? body.offsetHeight : 0,
                html ? html.scrollHeight : 0,
                html ? html.offsetHeight : 0
            );
            return String(value || 0);
        })();
        """.trimIndent()
    ) { rawValue ->
        val cssHeight = rawValue
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.toFloatOrNull()
            ?: return@evaluateJavascript

        val measuredPx = (cssHeight * webView.resources.displayMetrics.density)
            .roundToInt()
            .coerceAtLeast(1)

        onMeasured(measuredPx)
    }
}

private fun buildRichMessageHtml(
    markdown: String,
    textColor: Color,
    baseFontPx: Float,
    lineHeightPx: Float,
    baseFontWeight: Int
): String {
    val colorCss = textColor.toCssRgba()
    val bodyHtml = markdownToHtml(markdown)

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta
                name="viewport"
                content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"
            />
            <link
                rel="stylesheet"
                href="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.css"
            />
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                    color: $colorCss;
                    overflow: hidden;
                }

                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                }

                #content {
                    font-size: ${baseFontPx}px;
                    line-height: ${lineHeightPx}px;
                    font-weight: $baseFontWeight;
                    word-break: break-word;
                    overflow-wrap: anywhere;
                }

                p {
                    margin: 0 0 8px 0;
                }

                p:last-child {
                    margin-bottom: 0;
                }

                h1, h2, h3 {
                    margin: 0 0 8px 0;
                    line-height: 1.28;
                    font-weight: 800;
                    color: $colorCss;
                }

                h1 { font-size: ${baseFontPx * 1.08f}px; }
                h2 { font-size: ${baseFontPx * 1.04f}px; }
                h3 { font-size: ${baseFontPx * 1.01f}px; }

                strong {
                    font-weight: 800;
                }

                code {
                    font-family: "SFMono-Regular", Menlo, monospace;
                    font-size: 0.95em;
                    background: rgba(255, 255, 255, 0.10);
                    padding: 0.10em 0.34em;
                    border-radius: 0.42em;
                }

                hr {
                    border: none;
                    height: 1px;
                    margin: 8px 0;
                    background: rgba(255, 255, 255, 0.18);
                }

                ul {
                    margin: 0 0 8px 0;
                    padding-left: 1.1em;
                }

                li {
                    margin: 0 0 4px 0;
                }

                .math-host {
                    display: block;
                    margin: 6px 0;
                    overflow-x: auto;
                    overflow-y: hidden;
                }

                .katex {
                    color: $colorCss;
                    font-size: 1.00em;
                }

                .katex-display {
                    margin: 0.18em 0;
                    overflow-x: auto;
                    overflow-y: hidden;
                    padding: 0.10em 0;
                }
            </style>
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/katex.min.js"></script>
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.10/dist/contrib/auto-render.min.js"></script>
        </head>
        <body>
            <div id="content">$bodyHtml</div>
            <script>
                window.addEventListener("load", function() {
                    try {
                        if (window.renderMathInElement) {
                            renderMathInElement(document.getElementById("content"), {
                                delimiters: [
                                    { left: "$$", right: "$$", display: true },
                                    { left: "\\\\[", right: "\\\\]", display: true },
                                    { left: "\\\\(", right: "\\\\)", display: false }
                                ],
                                throwOnError: false,
                                strict: "ignore"
                            });
                        }
                    } catch (error) {
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun markdownToHtml(markdown: String): String {
    val tokens = linkedMapOf<String, String>()
    fun stash(value: String): String {
        val key = "@@TOKEN_${tokens.size}@@"
        tokens[key] = value
        return key
    }

    val normalized = preprocessDisplayMathBlocks(markdown, ::escapeHtml, ::stash)
        .trim()

    if (normalized.isBlank()) return "<p>……</p>"

    val builder = StringBuilder()
    val paragraphLines = mutableListOf<String>()
    val listItems = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        val content = paragraphLines.joinToString("<br/>") { formatInlineMarkdown(it, ::stash) }
        builder.append("<p>").append(content).append("</p>")
        paragraphLines.clear()
    }

    fun flushList() {
        if (listItems.isEmpty()) return
        builder.append("<ul>")
        listItems.forEach { item ->
            builder.append("<li>")
                .append(formatInlineMarkdown(item, ::stash))
                .append("</li>")
        }
        builder.append("</ul>")
        listItems.clear()
    }

    normalized.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> {
                flushParagraph()
                flushList()
            }
            trimmed.matches(Regex("""---+""")) -> {
                flushParagraph()
                flushList()
                builder.append("<hr/>")
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                flushList()
                builder.append("<h3>")
                    .append(formatInlineMarkdown(trimmed.removePrefix("### ").trim(), ::stash))
                    .append("</h3>")
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                flushList()
                builder.append("<h2>")
                    .append(formatInlineMarkdown(trimmed.removePrefix("## ").trim(), ::stash))
                    .append("</h2>")
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                flushList()
                builder.append("<h1>")
                    .append(formatInlineMarkdown(trimmed.removePrefix("# ").trim(), ::stash))
                    .append("</h1>")
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                listItems += trimmed.drop(2).trim()
            }
            else -> {
                flushList()
                paragraphLines += line
            }
        }
    }

    flushParagraph()
    flushList()

    var html = builder.toString().ifBlank { "<p>……</p>" }
    tokens.forEach { (key, value) ->
        html = html.replace(key, value)
    }
    return html
}

private fun preprocessDisplayMathBlocks(
    source: String,
    escape: (String) -> String,
    stash: (String) -> String
): String {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val output = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        when (trimmed) {
            "\\[" -> {
                index += 1
                val body = StringBuilder()
                while (index < lines.size && lines[index].trim() != "\\]") {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[index].trimEnd())
                    index += 1
                }
                val token = stash("<div class=\"math-host\">\\[${escape(body.toString())}\\]</div>")
                output += token
                if (index < lines.size && lines[index].trim() == "\\]") index += 1
            }
            "$$" -> {
                index += 1
                val body = StringBuilder()
                while (index < lines.size && lines[index].trim() != "$$") {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[index].trimEnd())
                    index += 1
                }
                val token = stash("<div class=\"math-host\">$$${escape(body.toString())}$$</div>")
                output += token
                if (index < lines.size && lines[index].trim() == "$$") index += 1
            }
            else -> {
                output += lines[index]
                index += 1
            }
        }
    }
    return output.joinToString("\n")
}

private fun formatInlineMarkdown(
    text: String,
    stash: (String) -> String
): String {
    var working = text

    working = Regex("""`([^`]+)`""").replace(working) { match ->
        stash("<code>${escapeHtml(match.groupValues[1])}</code>")
    }

    working = inlineMathRegex.replace(working) { match ->
        stash("\\(${escapeHtml(match.groupValues[1])}\\)")
    }

    working = fencedMathRegex.replace(working) { match ->
        stash("<span class=\"math-host\">$$${escapeHtml(match.groupValues[1])}$$</span>")
    }

    working = escapeHtml(working)

    working = Regex("""\*\*(.+?)\*\*""").replace(working) {
        "<strong>${it.groupValues[1]}</strong>"
    }

    return working
}

private fun escapeHtml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

private fun Color.toCssRgba(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    val a = alpha.coerceIn(0f, 1f)
    return "rgba($r, $g, $b, $a)"
}
