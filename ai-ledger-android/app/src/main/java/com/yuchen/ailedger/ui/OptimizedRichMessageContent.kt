package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.DynamicDrawableSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import ru.noties.jlatexmath.JLatexMathDrawable

private val optimizedHeadingRegex = Regex("""^\s*(#{1,6})\s+(.+?)\s*$""")
private val optimizedBulletRegex = Regex("""^\s*[-*•]\s+(.+?)\s*$""")
private val optimizedQuoteRegex = Regex("""^\s*>\s*(.+?)\s*$""")
private val optimizedSampleLabelRegex = Regex("""^\s*【样本\d+】\s*$""")
private val optimizedTableRowRegex = Regex("""^\s*\|(.+)\|\s*$""")
private val optimizedTableDividerRegex = Regex("""^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$""")
private val optimizedHorizontalRuleRegex = Regex("""^\s*---+\s*$""")
private val optimizedDisplayBracketFormulaRegex = Regex("""(?s)\\\[(.+?)\\\]""")
private val optimizedDisplayDollarFormulaRegex = Regex("""(?s)\$\$(.+?)\$\$""")
private val optimizedInlineFormulaRegex = Regex("""(?s)\\\((.+?)\\\)""")
private val optimizedBoldRegex = Regex("""\*\*(.+?)\*\*""")
private val optimizedCodeRegex = Regex("""`([^`]+)`""")
private val optimizedCitationRegex = Regex("""\[(\d{1,2})]""")
private val optimizedTokenRegex = Regex("""(@@FORMULA_\d+@@)|(@@CODE_\d+@@)|(@@BOLD_\d+@@)""")
private val optimizedSentenceHeadingBoundaryRegex = Regex("""([。！？!?；;：:]|\.)\s+(#{1,6}\s+)""")
private val optimizedSentenceBulletBoundaryRegex = Regex("""([。！？!?；;]|\.)\s+([-*•]\s+)""")
private val optimizedLeadingFormulaGapRegex = Regex("""\n{2,}(@@FORMULA_\d+@@)""")
private val optimizedTrailingFormulaGapRegex = Regex("""(@@FORMULA_\d+@@)\n{2,}""")

private data class OptimizedFormulaToken(
    val key: String,
    val latex: String,
    val display: Boolean
)

private data class OptimizedRichTextRenderKey(
    val text: String,
    val textColor: Int,
    val textSizeBits: Int,
    val baseFontWeight: Int,
    val lineHeightBits: Int,
    val stickerSizePx: Int,
    val stickerVerticalOffsetPx: Int,
    val stickerHorizontalGapPx: Int,
    val stickerLineExtraPx: Int,
    val densityBits: Int
)

private sealed interface OptimizedInlineObject {
    val start: Int
    val endExclusive: Int

    data class Sticker(
        override val start: Int,
        override val endExclusive: Int,
        val assetKey: String?
    ) : OptimizedInlineObject

    data class Citation(
        override val start: Int,
        override val endExclusive: Int,
        val number: String
    ) : OptimizedInlineObject
}

private object OptimizedRichMessageTextCache {
    private const val MaxEntries = 64
    private val entries = object : LinkedHashMap<OptimizedRichTextRenderKey, CharSequence>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<OptimizedRichTextRenderKey, CharSequence>?
        ): Boolean = size > MaxEntries
    }

    fun getOrPut(key: OptimizedRichTextRenderKey, build: () -> CharSequence): CharSequence {
        synchronized(entries) {
            entries[key]?.let { return it }
        }
        val built = build()
        synchronized(entries) {
            return entries.getOrPut(key) { built }
        }
    }
}

@Composable
fun OptimizedRichMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val stickerLayout = InlineStickerDisplaySettings.layoutPreferences(context)
    val resolvedColor = if (color != Color.Unspecified) color else Color.White.copy(alpha = 0.86f)
    val textSizePx = remember(fontSize, density) {
        if (fontSize != TextUnit.Unspecified) {
            with(density) { fontSize.toPx() }.coerceAtLeast(12f)
        } else {
            with(density) { 14f * fontScale }.coerceAtLeast(12f)
        }
    }
    val lineHeightPx = remember(lineHeight, textSizePx, density) {
        if (lineHeight != TextUnit.Unspecified) {
            with(density) { lineHeight.toPx() }.coerceAtLeast(textSizePx + 2f)
        } else {
            textSizePx * 1.28f
        }
    }
    val hasInlineStickerMarker = remember(text) { InlineStickerAssets.containsProtocolMarker(text) }
    val hasRichMarkup = remember(text, hasInlineStickerMarker) {
        hasInlineStickerMarker || optimizedCitationRegex.containsMatchIn(text) || optimizedMayContainRichMarkup(text)
    }

    if (shouldUseLegacyMobileCommandPanel(text)) {
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

    if (!hasRichMarkup) {
        MaterialText(
            text = text,
            modifier = modifier,
            color = resolvedColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        )
        return
    }

    val stickerSizePx = remember(density, stickerLayout.sizeDp) {
        with(density) { stickerLayout.sizeDp.dp.toPx() }
            .roundToInt()
            .coerceAtLeast(1)
    }
    val stickerVerticalOffsetPx = remember(density, stickerLayout.verticalOffsetDp) {
        with(density) { stickerLayout.verticalOffsetDp.dp.toPx() }.roundToInt()
    }
    val stickerHorizontalGapPx = remember(density, stickerLayout.horizontalGapDp) {
        with(density) { stickerLayout.horizontalGapDp.dp.toPx() }
            .roundToInt()
            .coerceAtLeast(0)
    }
    val stickerLineExtraPx = remember(density, stickerLayout.lineExtraDp) {
        with(density) { stickerLayout.lineExtraDp.dp.toPx() }
            .roundToInt()
            .coerceAtLeast(0)
    }
    val baseFontWeight = if (
        fontWeight == FontWeight.Bold ||
        fontWeight == FontWeight.ExtraBold ||
        fontWeight == FontWeight.Black
    ) {
        Typeface.BOLD
    } else {
        Typeface.NORMAL
    }
    val textColorArgb = resolvedColor.toArgb()
    val renderKey = remember(
        text,
        textColorArgb,
        textSizePx,
        lineHeightPx,
        baseFontWeight,
        stickerSizePx,
        stickerVerticalOffsetPx,
        stickerHorizontalGapPx,
        stickerLineExtraPx,
        density.density
    ) {
        OptimizedRichTextRenderKey(
            text = text,
            textColor = textColorArgb,
            textSizeBits = textSizePx.toBits(),
            baseFontWeight = baseFontWeight,
            lineHeightBits = lineHeightPx.toBits(),
            stickerSizePx = stickerSizePx,
            stickerVerticalOffsetPx = stickerVerticalOffsetPx,
            stickerHorizontalGapPx = stickerHorizontalGapPx,
            stickerLineExtraPx = stickerLineExtraPx,
            densityBits = density.density.toBits()
        )
    }
    val richText = remember(context, renderKey) {
        OptimizedRichMessageTextCache.getOrPut(renderKey) {
            buildOptimizedRichMessageSpannable(
                context = context,
                raw = text,
                textColor = textColorArgb,
                textSizePx = textSizePx,
                baseFontWeight = baseFontWeight,
                stickerSizePx = stickerSizePx,
                stickerVerticalOffsetPx = stickerVerticalOffsetPx,
                stickerHorizontalGapPx = stickerHorizontalGapPx,
                stickerLineExtraPx = stickerLineExtraPx,
                density = density.density
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            OptimizedRichTextView(viewContext).apply {
                includeFontPadding = false
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            textView.bind(
                key = renderKey,
                richText = richText,
                textColor = textColorArgb,
                textSizePx = textSizePx,
                lineHeightPx = lineHeightPx
            )
        }
    )
}

private class OptimizedRichTextView(context: Context) : TextView(context) {
    private var boundKey: OptimizedRichTextRenderKey? = null
    private val stickerRequests = mutableMapOf<String, InlineStickerLoadHandle>()

    fun bind(
        key: OptimizedRichTextRenderKey,
        richText: CharSequence,
        textColor: Int,
        textSizePx: Float,
        lineHeightPx: Float
    ) {
        if (boundKey == key) return
        cancelStickerRequests()
        setTextColor(textColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
        setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
        text = richText
        boundKey = key
        bindStickerSpans()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindStickerSpans()
    }

    override fun onDetachedFromWindow() {
        cancelStickerRequests()
        super.onDetachedFromWindow()
    }

    private fun bindStickerSpans() {
        val spanned = text as? Spanned ?: return
        val spans = spanned.getSpans(0, spanned.length, InlineStickerSpan::class.java)
        if (spans.isEmpty()) return

        spans.groupBy { it.assetKey }.forEach { (assetKey, keySpans) ->
            InlineStickerAssets.cachedBitmap(assetKey)?.let { cached ->
                var changed = false
                keySpans.forEach { changed = it.updateBitmap(cached) || changed }
                if (changed) invalidate()
                return@forEach
            }
            if (stickerRequests.containsKey(assetKey)) return@forEach

            val weakView = WeakReference(this)
            val handle = InlineStickerAssets.requestBitmap(assetKey) { bitmap ->
                val view = weakView.get() ?: return@requestBitmap
                view.stickerRequests.remove(assetKey)
                if (bitmap == null) return@requestBitmap
                var changed = false
                keySpans.forEach { changed = it.updateBitmap(bitmap) || changed }
                if (changed) {
                    view.invalidate()
                    view.requestLayout()
                }
            }
            stickerRequests[assetKey] = handle
        }
    }

    private fun cancelStickerRequests() {
        stickerRequests.values.forEach(InlineStickerLoadHandle::cancel)
        stickerRequests.clear()
    }
}

private fun shouldUseLegacyMobileCommandPanel(text: String): Boolean {
    val clean = text.trim()
    if (clean.isBlank()) return false
    return (clean.contains("动作：") && clean.contains("详情：")) ||
        clean.startsWith("已取消这个手机动作：") ||
        clean.contains("执行结果：") ||
        clean.contains("执行失败：")
}

private fun optimizedMayContainRichMarkup(text: String): Boolean {
    for (ch in text) {
        when (ch) {
            '*', '\\', '$', '#', '-', '|', '【', '>', '`', '[', '~' -> return true
        }
    }
    return false
}

private fun buildOptimizedRichMessageSpannable(
    context: Context,
    raw: String,
    textColor: Int,
    textSizePx: Float,
    baseFontWeight: Int,
    stickerSizePx: Int,
    stickerVerticalOffsetPx: Int,
    stickerHorizontalGapPx: Int,
    stickerLineExtraPx: Int,
    density: Float
): CharSequence {
    val normalized = sanitizeOptimizedRichTextSource(raw)
    val (tokenized, formulaTokens) = extractOptimizedFormulaTokens(normalized)
    val builder = SpannableStringBuilder()

    tokenized.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            appendOptimizedCompactBlankLine(builder)
            return@forEach
        }

        val formulaToken = formulaTokens[trimmed]
        if (formulaToken != null) {
            appendOptimizedDisplayFormula(builder, context, formulaToken, textColor, textSizePx)
            return@forEach
        }

        when {
            optimizedHorizontalRuleRegex.matches(trimmed) -> {
                appendOptimizedCompactSeparator(builder)
                appendOptimizedStyled(builder, "────────", RelativeSizeSpan(0.96f), StyleSpan(Typeface.NORMAL))
            }
            optimizedSampleLabelRegex.matches(trimmed) -> {
                appendOptimizedCompactSeparator(builder)
                appendOptimizedStyled(builder, trimmed, RelativeSizeSpan(0.92f), OptimizedWeightSpan(Typeface.BOLD))
            }
            optimizedQuoteRegex.matches(trimmed) -> {
                val content = optimizedQuoteRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                appendOptimizedCompactSeparator(builder)
                appendOptimizedInline(builder, content, context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
            optimizedHeadingRegex.matches(trimmed) -> {
                val match = optimizedHeadingRegex.matchEntire(trimmed)!!
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val headingText = match.groupValues[2].trim()
                val size = when (level) {
                    1 -> 1.05f
                    2 -> 1.04f
                    3 -> 1.02f
                    else -> 1.00f
                }
                appendOptimizedCompactSeparator(builder)
                val start = builder.length
                appendOptimizedInline(builder, headingText, context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
                if (builder.length > start) {
                    builder.setSpan(RelativeSizeSpan(size), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(OptimizedWeightSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            optimizedBulletRegex.matches(trimmed) -> {
                val content = optimizedBulletRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                appendOptimizedCompactSeparator(builder)
                builder.append("• ")
                appendOptimizedInline(builder, content, context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
            optimizedTableDividerRegex.matches(trimmed) -> Unit
            optimizedTableRowRegex.matches(trimmed) -> {
                val cells = optimizedTableRowRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    appendOptimizedCompactSeparator(builder)
                    appendOptimizedInline(builder, cells.joinToString("  ·  "), context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
                }
            }
            else -> {
                appendOptimizedCompactSeparator(builder)
                appendOptimizedInline(builder, line.trim(), context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
        }
    }

    trimOptimizedTrailingNewlines(builder)
    if (baseFontWeight == Typeface.BOLD && builder.isNotEmpty()) {
        builder.setSpan(OptimizedWeightSpan(Typeface.NORMAL), 0, builder.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }
    return builder
}

private fun sanitizeOptimizedRichTextSource(source: String): String {
    var text = source.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace("\\\\(", "\\(")
        .replace("\\\\)", "\\)")
        .replace("\\\\[", "\\[")
        .replace("\\\\]", "\\]")
    text = text.replace(Regex("""\\\\([A-Za-z])""")) { match ->
        "\\${match.groupValues[1]}"
    }
    text = normalizeMarkdownBoundariesAfterInlineStickers(text)
    text = text.replace(optimizedSentenceHeadingBoundaryRegex, "$1\n$2")
    text = text.replace(optimizedSentenceBulletBoundaryRegex, "$1\n$2")
    text = text.replace(Regex("""\n{3,}"""), "\n\n")
    return text.trim()
}

private fun normalizeMarkdownBoundariesAfterInlineStickers(source: String): String {
    val markers = InlineStickerAssets.findProtocolMarkers(source)
    if (markers.isEmpty()) return source

    val builder = StringBuilder(source.length + markers.size)
    var cursor = 0
    markers.forEach { marker ->
        val start = marker.start.coerceIn(cursor, source.length)
        val end = marker.endExclusive.coerceIn(start, source.length)
        if (start > cursor) builder.append(source.substring(cursor, start))
        builder.append(source.substring(start, end))
        cursor = end

        var probe = cursor
        while (probe < source.length && (source[probe] == ' ' || source[probe] == '\t')) {
            probe++
        }
        if (probe > cursor && isMarkdownBlockPrefixAt(source, probe)) {
            builder.append('\n')
            cursor = probe
        }
    }
    if (cursor < source.length) builder.append(source.substring(cursor))
    return builder.toString()
}

private fun isMarkdownBlockPrefixAt(source: String, index: Int): Boolean {
    return isMarkdownHeadingPrefixAt(source, index) ||
        isMarkdownBulletPrefixAt(source, index) ||
        isMarkdownQuotePrefixAt(source, index) ||
        isMarkdownTablePrefixAt(source, index) ||
        isMarkdownCodeFencePrefixAt(source, index) ||
        isMarkdownDisplayFormulaPrefixAt(source, index)
}

private fun isMarkdownHeadingPrefixAt(source: String, index: Int): Boolean {
    if (index !in source.indices || source[index] != '#') return false
    var count = 0
    while (index + count < source.length && source[index + count] == '#') {
        count++
    }
    return count in 1..6 && index + count < source.length && source[index + count].isWhitespace()
}

private fun isMarkdownBulletPrefixAt(source: String, index: Int): Boolean {
    if (index !in source.indices) return false
    val ch = source[index]
    return (ch == '-' || ch == '*' || ch == '•') && index + 1 < source.length && source[index + 1].isWhitespace()
}

private fun isMarkdownQuotePrefixAt(source: String, index: Int): Boolean {
    return index in source.indices &&
        source[index] == '>' &&
        index + 1 < source.length &&
        source[index + 1].isWhitespace()
}

private fun isMarkdownTablePrefixAt(source: String, index: Int): Boolean {
    if (index !in source.indices || source[index] != '|') return false
    val lineEnd = source.indexOf('\n', index).let { if (it < 0) source.length else it }
    return source.indexOf('|', index + 1).let { it in (index + 1) until lineEnd }
}

private fun isMarkdownCodeFencePrefixAt(source: String, index: Int): Boolean {
    return source.startsWith("```", index) || source.startsWith("~~~", index)
}

private fun isMarkdownDisplayFormulaPrefixAt(source: String, index: Int): Boolean {
    return source.startsWith("$$", index) || source.startsWith("\\[", index)
}

private fun extractOptimizedFormulaTokens(source: String): Pair<String, Map<String, OptimizedFormulaToken>> {
    val tokens = linkedMapOf<String, OptimizedFormulaToken>()
    var counter = 0
    fun nextKey(): String = "@@FORMULA_${counter++}@@"

    var working = source
    working = optimizedDisplayBracketFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedFormulaToken(key, match.groupValues[1].trim(), true)
        "\n$key\n"
    }
    working = optimizedDisplayDollarFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedFormulaToken(key, match.groupValues[1].trim(), true)
        "\n$key\n"
    }
    working = optimizedInlineFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedFormulaToken(key, match.groupValues[1].trim(), false)
        key
    }
    working = optimizedLeadingFormulaGapRegex.replace(working, "\n$1")
    working = optimizedTrailingFormulaGapRegex.replace(working, "$1\n")
    return working to tokens
}

private fun appendOptimizedInline(
    builder: SpannableStringBuilder,
    source: String,
    context: Context,
    formulaTokens: Map<String, OptimizedFormulaToken>,
    textColor: Int,
    textSizePx: Float,
    stickerSizePx: Int,
    stickerVerticalOffsetPx: Int,
    stickerHorizontalGapPx: Int,
    stickerLineExtraPx: Int,
    density: Float
) {
    val codeTokens = linkedMapOf<String, String>()
    val boldTokens = linkedMapOf<String, String>()
    var codeIndex = 0
    var boldIndex = 0
    var working = source

    working = optimizedCodeRegex.replace(working) { match ->
        val key = "@@CODE_${codeIndex++}@@"
        codeTokens[key] = match.groupValues[1]
        key
    }
    working = optimizedBoldRegex.replace(working) { match ->
        val key = "@@BOLD_${boldIndex++}@@"
        boldTokens[key] = match.groupValues[1]
        key
    }

    fun appendObjects(value: String) {
        appendOptimizedTextWithInlineObjects(
            builder = builder,
            source = value,
            textSizePx = textSizePx,
            stickerSizePx = stickerSizePx,
            stickerVerticalOffsetPx = stickerVerticalOffsetPx,
            stickerHorizontalGapPx = stickerHorizontalGapPx,
            stickerLineExtraPx = stickerLineExtraPx,
            density = density
        )
    }

    var cursor = 0
    optimizedTokenRegex.findAll(working).forEach { match ->
        if (match.range.first > cursor) appendObjects(working.substring(cursor, match.range.first))
        when (val token = match.value) {
            in formulaTokens.keys -> {
                formulaTokens[token]?.let { formula ->
                    appendOptimizedFormula(builder, context, formula.latex, formula.display, textColor, textSizePx)
                }
            }
            else -> when {
                token.startsWith("@@CODE_") -> {
                    appendOptimizedStyledInline(
                        builder = builder,
                        text = codeTokens[token].orEmpty(),
                        span = OptimizedTypefaceSpanCompat(Typeface.MONOSPACE),
                        textSizePx = textSizePx,
                        stickerSizePx = stickerSizePx,
                        stickerVerticalOffsetPx = stickerVerticalOffsetPx,
                        stickerHorizontalGapPx = stickerHorizontalGapPx,
                        stickerLineExtraPx = stickerLineExtraPx,
                        density = density
                    )
                }
                token.startsWith("@@BOLD_") -> {
                    appendOptimizedStyledInline(
                        builder = builder,
                        text = boldTokens[token].orEmpty(),
                        span = OptimizedWeightSpan(Typeface.BOLD),
                        textSizePx = textSizePx,
                        stickerSizePx = stickerSizePx,
                        stickerVerticalOffsetPx = stickerVerticalOffsetPx,
                        stickerHorizontalGapPx = stickerHorizontalGapPx,
                        stickerLineExtraPx = stickerLineExtraPx,
                        density = density
                    )
                }
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < working.length) appendObjects(working.substring(cursor))
}

private fun appendOptimizedTextWithInlineObjects(
    builder: SpannableStringBuilder,
    source: String,
    textSizePx: Float,
    stickerSizePx: Int,
    stickerVerticalOffsetPx: Int,
    stickerHorizontalGapPx: Int,
    stickerLineExtraPx: Int,
    density: Float
) {
    if (source.isEmpty()) return

    val objects = mutableListOf<OptimizedInlineObject>()
    InlineStickerAssets.findProtocolMarkers(source).forEach { marker ->
        objects += OptimizedInlineObject.Sticker(
            start = marker.start,
            endExclusive = marker.endExclusive,
            assetKey = marker.assetKey
        )
    }
    optimizedCitationRegex.findAll(source).forEach { match ->
        objects += OptimizedInlineObject.Citation(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            number = match.groupValues[1]
        )
    }

    val ordered = objects
        .sortedWith(compareBy<OptimizedInlineObject> { it.start }.thenByDescending { it.endExclusive })
        .fold(mutableListOf<OptimizedInlineObject>()) { accepted, item ->
            if (accepted.none { item.start < it.endExclusive && item.endExclusive > it.start }) {
                accepted += item
            }
            accepted
        }

    if (ordered.isEmpty()) {
        builder.append(source)
        return
    }

    var cursor = 0
    ordered.forEach { item ->
        if (item.start > cursor) builder.append(source.substring(cursor, item.start))
        when (item) {
            is OptimizedInlineObject.Sticker -> {
                val assetKey = item.assetKey
                if (assetKey != null) {
                    val start = builder.length
                    builder.append('\uFFFC')
                    builder.setSpan(
                        InlineStickerSpan(
                            assetKey = assetKey,
                            sizePx = stickerSizePx,
                            verticalOffsetPx = stickerVerticalOffsetPx,
                            horizontalGapPx = stickerHorizontalGapPx,
                            lineExtraPx = stickerLineExtraPx,
                            initialBitmap = InlineStickerAssets.cachedBitmap(assetKey)
                        ),
                        start,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            is OptimizedInlineObject.Citation -> {
                val start = builder.length
                builder.append('\uFFFC')
                builder.setSpan(
                    InlineCitationSpan(
                        number = item.number,
                        textSizePx = textSizePx,
                        density = density
                    ),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        cursor = max(cursor, item.endExclusive)
    }
    if (cursor < source.length) builder.append(source.substring(cursor))
}

private fun appendOptimizedStyledInline(
    builder: SpannableStringBuilder,
    text: String,
    span: Any,
    textSizePx: Float,
    stickerSizePx: Int,
    stickerVerticalOffsetPx: Int,
    stickerHorizontalGapPx: Int,
    stickerLineExtraPx: Int,
    density: Float
) {
    if (text.isEmpty()) return
    val start = builder.length
    appendOptimizedTextWithInlineObjects(
        builder, text, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density
    )
    if (builder.length > start) {
        builder.setSpan(span, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun appendOptimizedDisplayFormula(
    builder: SpannableStringBuilder,
    context: Context,
    token: OptimizedFormulaToken,
    textColor: Int,
    textSizePx: Float
) {
    trimOptimizedExtraBlankBeforeBlock(builder)
    appendOptimizedCompactSeparator(builder)
    appendOptimizedFormula(builder, context, token.latex, true, textColor, textSizePx)
    builder.append('\n')
}

private fun appendOptimizedFormula(
    builder: SpannableStringBuilder,
    context: Context,
    latex: String,
    display: Boolean,
    textColor: Int,
    textSizePx: Float
) {
    val cleanLatex = latex.replace('\n', ' ').trim()
    if (cleanLatex.isBlank()) return
    try {
        val drawable = JLatexMathDrawable.builder(cleanLatex)
            .textSize(if (display) textSizePx * 0.82f else textSizePx * 0.84f)
            .color(textColor)
            .align(if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
            .padding(0)
            .build()
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val start = builder.length
        builder.append('\uFFFC')
        builder.setSpan(
            OptimizedFormulaDrawableSpan(drawable, display, textSizePx),
            start,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    } catch (_: Throwable) {
        builder.append(cleanLatex)
    }
}

private fun appendOptimizedStyled(
    builder: SpannableStringBuilder,
    text: String,
    vararg spans: Any
) {
    if (text.isEmpty()) return
    val start = builder.length
    builder.append(text)
    spans.forEach { span ->
        builder.setSpan(span, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun appendOptimizedCompactSeparator(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (builder.last() != '\n') builder.append('\n')
}

private fun appendOptimizedCompactBlankLine(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (!builder.endsWithOptimized("\n\n")) {
        if (!builder.endsWithOptimized("\n")) builder.append('\n')
        builder.append('\n')
    }
}

private fun trimOptimizedExtraBlankBeforeBlock(builder: SpannableStringBuilder) {
    while (builder.endsWithOptimized("\n\n")) {
        builder.delete(builder.length - 1, builder.length)
    }
}

private fun trimOptimizedTrailingNewlines(builder: SpannableStringBuilder) {
    while (builder.isNotEmpty() && builder.last() == '\n') {
        builder.delete(builder.length - 1, builder.length)
    }
}

private fun CharSequence.endsWithOptimized(value: String): Boolean {
    if (length < value.length) return false
    return substring(length - value.length, length) == value
}

private class OptimizedWeightSpan(private val typefaceStyle: Int) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)

    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = Typeface.create(textPaint.typeface, typefaceStyle)
    }
}

private class OptimizedTypefaceSpanCompat(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)

    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = typeface
    }
}

private class InlineStickerSpan(
    val assetKey: String,
    private val sizePx: Int,
    private val verticalOffsetPx: Int,
    private val horizontalGapPx: Int,
    private val lineExtraPx: Int,
    initialBitmap: Bitmap?
) : ReplacementSpan() {
    @Volatile
    private var bitmap: Bitmap? = initialBitmap
    private val destination = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun updateBitmap(value: Bitmap): Boolean {
        if (bitmap === value) return false
        bitmap = value
        return true
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val original = paint.fontMetricsInt
            val textCenter = (original.ascent + original.descent) / 2 + verticalOffsetPx
            val halfSize = sizePx / 2
            fm.ascent = min(original.ascent, textCenter - halfSize - lineExtraPx)
            fm.top = min(original.top, fm.ascent)
            fm.descent = max(original.descent, textCenter + halfSize + lineExtraPx)
            fm.bottom = max(original.bottom, fm.descent)
        }
        return sizePx + horizontalGapPx * 2
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val loaded = bitmap ?: return
        val fm = paint.fontMetricsInt
        val textCenter = y + (fm.ascent + fm.descent) / 2f + verticalOffsetPx
        val stickerTop = textCenter - sizePx / 2f
        val left = x + horizontalGapPx
        destination.set(
            left,
            stickerTop,
            left + sizePx,
            stickerTop + sizePx
        )
        canvas.drawBitmap(loaded, null, destination, bitmapPaint)
    }
}

private class InlineCitationSpan(
    private val number: String,
    textSizePx: Float,
    density: Float
) : ReplacementSpan() {
    private val chipHeight = (textSizePx * (16f / 14f)).roundToInt().coerceAtLeast(1)
    private val minWidth = (density * if (number.length >= 2) 25f else 19f).roundToInt().coerceAtLeast(1)
    private val horizontalPadding = (density * 5f).roundToInt().coerceAtLeast(1)
    private val radius = chipHeight / 2f
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E8DF9EA }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEB8DF9EA.toInt()
        textSize = textSizePx * (9f / 14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bounds = RectF()

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val measured = numberPaint.measureText(number).roundToInt() + horizontalPadding * 2
        if (fm != null) {
            val original = paint.fontMetricsInt
            val textHeight = original.descent - original.ascent
            val chipTop = original.ascent + (textHeight - chipHeight) / 2
            val chipBottom = chipTop + chipHeight
            fm.ascent = min(original.ascent, chipTop)
            fm.top = min(original.top, chipTop)
            fm.descent = max(original.descent, chipBottom)
            fm.bottom = max(original.bottom, chipBottom)
        }
        return max(minWidth, measured)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val width = getSize(paint, text, start, end, null)
        val fm = paint.fontMetricsInt
        val textHeight = fm.descent - fm.ascent
        val chipTop = y + fm.ascent + (textHeight - chipHeight) / 2f
        bounds.set(x, chipTop, x + width, chipTop + chipHeight)
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)

        val numberFm = numberPaint.fontMetrics
        val numberBaseline = bounds.centerY() - (numberFm.ascent + numberFm.descent) / 2f
        canvas.drawText(number, bounds.centerX(), numberBaseline, numberPaint)
    }
}

private class OptimizedFormulaDrawableSpan(
    private val drawable: Drawable,
    private val display: Boolean,
    private val textSizePx: Float
) : DynamicDrawableSpan(ALIGN_BASELINE) {
    override fun getDrawable(): Drawable = drawable

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        if (fm != null) {
            val paintFm = paint.fontMetricsInt
            if (display) {
                val pad = (textSizePx * 0.02f).toInt().coerceAtLeast(1)
                fm.ascent = -rect.height() - pad
                fm.descent = pad
                fm.top = fm.ascent
                fm.bottom = fm.descent
            } else {
                val textHeight = paintFm.descent - paintFm.ascent
                val extra = ((rect.height() - textHeight) / 2).coerceAtLeast(0)
                fm.ascent = paintFm.ascent - extra
                fm.descent = paintFm.descent + extra
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
        }
        return rect.width()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val transY = if (display) {
            y - drawable.bounds.height()
        } else {
            val paintFm = paint.fontMetricsInt
            val textHeight = paintFm.descent - paintFm.ascent
            val centeredY = y + paintFm.ascent + (textHeight - drawable.bounds.height()) / 2
            centeredY + (textSizePx * 0.04f).toInt()
        }
        canvas.save()
        canvas.translate(x, transY.toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}
