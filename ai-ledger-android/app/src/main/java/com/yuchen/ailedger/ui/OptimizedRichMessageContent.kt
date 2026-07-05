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

private val optimizedStickerHeadingRegex = Regex("""^\s*(#{1,6})\s*(.+?)\s*$""")
private val optimizedStickerBulletRegex = Regex("""^\s*[-*•]\s+(.+?)\s*$""")
private val optimizedStickerQuoteRegex = Regex("""^\s*>\s*(.+?)\s*$""")
private val optimizedStickerSampleLabelRegex = Regex("""^\s*【样本\d+】\s*$""")
private val optimizedStickerTableRowRegex = Regex("""^\s*\|(.+)\|\s*$""")
private val optimizedStickerTableDividerRegex = Regex("""^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$""")
private val optimizedStickerHorizontalRuleRegex = Regex("""---+""")
private val optimizedStickerDisplayBracketFormulaRegex = Regex("""(?s)\\\[(.+?)\\\]""")
private val optimizedStickerDisplayDollarFormulaRegex = Regex("""(?s)\$\$(.+?)\$\$""")
private val optimizedStickerInlineDollarFormulaRegex = Regex("""(?<!\\)\$(?!\$|\s)([^\n$]+?)(?<!\\)\$""")
private val optimizedStickerInlineFormulaRegex = Regex("""(?s)\\\((.+?)\\\)""")
private val optimizedStickerBoldRegex = Regex("""\*\*(.+?)\*\*""")
private val optimizedStickerCodeRegex = Regex("""`([^`]+)`""")
private val optimizedStickerCitationRegex = Regex("""\[(\d{1,2})]""")
private val optimizedStickerTokenRegex = Regex("""(@@FORMULA_\d+@@)|(@@CODE_\d+@@)|(@@BOLD_\d+@@)""")
private val optimizedStickerRichTokenRegex = Regex(
    pattern = """(\*\*.+?\*\*)|(\\\(.+?\\\))|(\\\[.+?\\\])|(\$\$.+?\$\$)|((?<!\\)\$(?!\$|\s)[^\n$]+?(?<!\\)\$)|(?m)^\s{0,3}#{1,6}\s+|(?m)^\s*---+\s*$|(?m)^\s*[-*•]\s+|(?m)^\s*\|.+\|\s*$|(?m)^\s*【样本\d+】\s*$|(?m)^\s*>\s+""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
)
private val optimizedStickerLeadingFormulaGapRegex = Regex("""\n{2,}(@@FORMULA_\d+@@)""")
private val optimizedStickerTrailingFormulaGapRegex = Regex("""(@@FORMULA_\d+@@)\n{2,}""")

private data class OptimizedStickerFormulaToken(
    val key: String,
    val latex: String,
    val display: Boolean
)

private data class OptimizedStickerRenderKey(
    val text: String,
    val textColor: Int,
    val textSizeBits: Int,
    val lineHeightBits: Int,
    val baseFontWeight: Int,
    val stickerSizePx: Int,
    val stickerVerticalOffsetPx: Int,
    val stickerHorizontalGapPx: Int,
    val stickerLineExtraPx: Int,
    val densityBits: Int
)

private sealed interface OptimizedStickerInlineObject {
    val start: Int
    val endExclusive: Int

    data class Sticker(
        override val start: Int,
        override val endExclusive: Int,
        val assetKey: String?
    ) : OptimizedStickerInlineObject

    data class Citation(
        override val start: Int,
        override val endExclusive: Int,
        val number: String
    ) : OptimizedStickerInlineObject
}

private object OptimizedStickerTextCache {
    private const val MaxEntries = 64
    private val entries = object : LinkedHashMap<OptimizedStickerRenderKey, CharSequence>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<OptimizedStickerRenderKey, CharSequence>?
        ): Boolean = size > MaxEntries
    }

    fun getOrPut(key: OptimizedStickerRenderKey, build: () -> CharSequence): CharSequence {
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
    val context = LocalContext.current
    val hasInlineStickerMarker = remember(text) { InlineStickerAssets.containsProtocolMarker(text) }

    if (!hasInlineStickerMarker) {
        RichMessageContent(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        )
        return
    }

    val density = LocalDensity.current
    val stickerLayout = InlineStickerDisplaySettings.layoutPreferences(context)
    val resolvedColor = if (color != Color.Unspecified) color else Color.White.copy(alpha = 0.86f)
    val textColorArgb = resolvedColor.toArgb()
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
    val stickerSizePx = remember(density, stickerLayout.sizeDp) {
        with(density) { stickerLayout.sizeDp.dp.toPx() }.roundToInt().coerceAtLeast(1)
    }
    val stickerVerticalOffsetPx = remember(density, stickerLayout.verticalOffsetDp) {
        with(density) { stickerLayout.verticalOffsetDp.dp.toPx() }.roundToInt()
    }
    val stickerHorizontalGapPx = remember(density, stickerLayout.horizontalGapDp) {
        with(density) { stickerLayout.horizontalGapDp.dp.toPx() }.roundToInt().coerceAtLeast(0)
    }
    val stickerLineExtraPx = remember(density, stickerLayout.lineExtraDp) {
        with(density) { stickerLayout.lineExtraDp.dp.toPx() }.roundToInt().coerceAtLeast(0)
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
        OptimizedStickerRenderKey(
            text = text,
            textColor = textColorArgb,
            textSizeBits = textSizePx.toBits(),
            lineHeightBits = lineHeightPx.toBits(),
            baseFontWeight = baseFontWeight,
            stickerSizePx = stickerSizePx,
            stickerVerticalOffsetPx = stickerVerticalOffsetPx,
            stickerHorizontalGapPx = stickerHorizontalGapPx,
            stickerLineExtraPx = stickerLineExtraPx,
            densityBits = density.density.toBits()
        )
    }

    val richText = remember(context, renderKey) {
        OptimizedStickerTextCache.getOrPut(renderKey) {
            buildOptimizedStickerSpannable(
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
            OptimizedStickerTextView(viewContext).apply {
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

private class OptimizedStickerTextView(context: Context) : TextView(context) {
    private var boundKey: OptimizedStickerRenderKey? = null
    private val stickerRequests = mutableMapOf<String, InlineStickerLoadHandle>()

    fun bind(
        key: OptimizedStickerRenderKey,
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

private fun buildOptimizedStickerSpannable(
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
    val normalized = sanitizeOptimizedStickerSource(raw)
    val (tokenized, formulaTokens) = extractOptimizedStickerFormulaTokens(normalized)
    val builder = SpannableStringBuilder()

    tokenized.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            appendOptimizedStickerBlankLine(builder)
            return@forEach
        }

        val formulaToken = formulaTokens[trimmed]
        if (formulaToken != null) {
            appendOptimizedStickerDisplayFormula(builder, context, formulaToken, textColor, textSizePx)
            return@forEach
        }

        when {
            optimizedStickerHorizontalRuleRegex.matches(trimmed) -> {
                appendOptimizedStickerSeparator(builder)
                appendOptimizedStickerStyled(builder, "────────", RelativeSizeSpan(0.96f), StyleSpan(Typeface.NORMAL))
            }
            optimizedStickerSampleLabelRegex.matches(trimmed) -> {
                appendOptimizedStickerSeparator(builder)
                appendOptimizedStickerStyled(builder, trimmed, RelativeSizeSpan(0.92f), OptimizedStickerWeightSpan(Typeface.BOLD))
            }
            optimizedStickerQuoteRegex.matches(trimmed) -> {
                val content = optimizedStickerQuoteRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                appendOptimizedStickerSeparator(builder)
                appendOptimizedStickerInline(builder, content, context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
            optimizedStickerHeadingRegex.matches(trimmed) -> {
                val match = optimizedStickerHeadingRegex.matchEntire(trimmed)!!
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val headingText = match.groupValues[2].trim()
                val size = when (level) {
                    1 -> 1.04f
                    2 -> 1.03f
                    3 -> 1.01f
                    else -> 1.00f
                }
                appendOptimizedStickerSeparator(builder)
                val start = builder.length
                appendOptimizedStickerInline(builder, headingText, context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
                if (builder.length > start) {
                    builder.setSpan(RelativeSizeSpan(size), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(OptimizedStickerWeightSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            optimizedStickerBulletRegex.matches(trimmed) -> {
                val content = optimizedStickerBulletRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                appendOptimizedStickerSeparator(builder)
                builder.append("• ")
                appendOptimizedStickerInline(builder, content, context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
            optimizedStickerTableDividerRegex.matches(trimmed) -> Unit
            optimizedStickerTableRowRegex.matches(trimmed) -> {
                val cells = optimizedStickerTableRowRegex.matchEntire(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    appendOptimizedStickerSeparator(builder)
                    appendOptimizedStickerInline(builder, cells.joinToString("  ·  "), context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
                }
            }
            else -> {
                appendOptimizedStickerSeparator(builder)
                appendOptimizedStickerInline(builder, line.trim(), context, formulaTokens, textColor, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
        }
    }

    trimOptimizedStickerTrailingNewlines(builder)
    if (baseFontWeight == Typeface.BOLD && builder.isNotEmpty()) {
        builder.setSpan(OptimizedStickerWeightSpan(Typeface.NORMAL), 0, builder.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }
    return builder
}

private fun sanitizeOptimizedStickerSource(source: String): String {
    var text = source.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace("\\\\(", "\\(")
        .replace("\\\\)", "\\)")
        .replace("\\\\[", "\\[")
        .replace("\\\\]", "\\]")
    text = text.replace(Regex("""\\\\([A-Za-z])""")) { match ->
        "\\${match.groupValues[1]}"
    }
    text = text.replace(Regex("""\n{3,}"""), "\n\n")
    return text.trim()
}

private fun extractOptimizedStickerFormulaTokens(source: String): Pair<String, Map<String, OptimizedStickerFormulaToken>> {
    val tokens = linkedMapOf<String, OptimizedStickerFormulaToken>()
    var counter = 0
    fun nextKey(): String = "@@FORMULA_${counter++}@@"

    var working = source
    working = optimizedStickerDisplayBracketFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedStickerFormulaToken(key, match.groupValues[1].trim(), true)
        "\n$key\n"
    }
    working = optimizedStickerDisplayDollarFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedStickerFormulaToken(key, match.groupValues[1].trim(), true)
        "\n$key\n"
    }
    working = optimizedStickerInlineDollarFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedStickerFormulaToken(key, match.groupValues[1].trim(), false)
        key
    }
    working = optimizedStickerInlineFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = OptimizedStickerFormulaToken(key, match.groupValues[1].trim(), false)
        key
    }
    working = optimizedStickerLeadingFormulaGapRegex.replace(working, "\n$1")
    working = optimizedStickerTrailingFormulaGapRegex.replace(working, "$1\n")
    return working to tokens
}

private fun appendOptimizedStickerInline(
    builder: SpannableStringBuilder,
    source: String,
    context: Context,
    formulaTokens: Map<String, OptimizedStickerFormulaToken>,
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

    working = optimizedStickerCodeRegex.replace(working) { match ->
        val key = "@@CODE_${codeIndex++}@@"
        codeTokens[key] = match.groupValues[1]
        key
    }
    working = optimizedStickerBoldRegex.replace(working) { match ->
        val key = "@@BOLD_${boldIndex++}@@"
        boldTokens[key] = match.groupValues[1]
        key
    }

    fun appendObjects(value: String) {
        appendOptimizedStickerTextObjects(
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
    optimizedStickerTokenRegex.findAll(working).forEach { match ->
        if (match.range.first > cursor) appendObjects(working.substring(cursor, match.range.first))
        when (val token = match.value) {
            in formulaTokens.keys -> {
                formulaTokens[token]?.let { formula ->
                    appendOptimizedStickerFormula(builder, context, formula.latex, formula.display, textColor, textSizePx)
                }
            }
            else -> when {
                token.startsWith("@@CODE_") -> appendOptimizedStickerStyledInline(builder, codeTokens[token].orEmpty(), OptimizedStickerTypefaceSpan(Typeface.MONOSPACE), textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
                token.startsWith("@@BOLD_") -> appendOptimizedStickerStyledInline(builder, boldTokens[token].orEmpty(), OptimizedStickerWeightSpan(Typeface.BOLD), textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < working.length) appendObjects(working.substring(cursor))
}

private fun appendOptimizedStickerTextObjects(
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

    val objects = mutableListOf<OptimizedStickerInlineObject>()
    InlineStickerAssets.findProtocolMarkers(source).forEach { marker ->
        objects += OptimizedStickerInlineObject.Sticker(marker.start, marker.endExclusive, marker.assetKey)
    }
    optimizedStickerCitationRegex.findAll(source).forEach { match ->
        objects += OptimizedStickerInlineObject.Citation(match.range.first, match.range.last + 1, match.groupValues[1])
    }

    val ordered = objects
        .sortedWith(compareBy<OptimizedStickerInlineObject> { it.start }.thenByDescending { it.endExclusive })
        .fold(mutableListOf<OptimizedStickerInlineObject>()) { accepted, item ->
            if (accepted.none { item.start < it.endExclusive && item.endExclusive > it.start }) accepted += item
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
            is OptimizedStickerInlineObject.Sticker -> {
                val key = item.assetKey
                if (key != null) {
                    val start = builder.length
                    builder.append('\uFFFC')
                    builder.setSpan(
                        InlineStickerSpan(
                            assetKey = key,
                            sizePx = stickerSizePx,
                            verticalOffsetPx = stickerVerticalOffsetPx,
                            horizontalGapPx = stickerHorizontalGapPx,
                            lineExtraPx = stickerLineExtraPx,
                            initialBitmap = InlineStickerAssets.cachedBitmap(key)
                        ),
                        start,
                        builder.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            is OptimizedStickerInlineObject.Citation -> {
                val start = builder.length
                builder.append('\uFFFC')
                builder.setSpan(InlineCitationSpan(item.number, textSizePx, density), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        cursor = max(cursor, item.endExclusive)
    }
    if (cursor < source.length) builder.append(source.substring(cursor))
}

private fun appendOptimizedStickerStyledInline(
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
    appendOptimizedStickerTextObjects(builder, text, textSizePx, stickerSizePx, stickerVerticalOffsetPx, stickerHorizontalGapPx, stickerLineExtraPx, density)
    if (builder.length > start) builder.setSpan(span, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun appendOptimizedStickerDisplayFormula(
    builder: SpannableStringBuilder,
    context: Context,
    token: OptimizedStickerFormulaToken,
    textColor: Int,
    textSizePx: Float
) {
    trimOptimizedStickerExtraBlankBeforeBlock(builder)
    appendOptimizedStickerSeparator(builder)
    appendOptimizedStickerFormula(builder, context, token.latex, true, textColor, textSizePx)
    builder.append('\n')
}

private fun appendOptimizedStickerFormula(
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
        builder.setSpan(OptimizedStickerFormulaSpan(drawable, display, textSizePx), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    } catch (_: Throwable) {
        builder.append(cleanLatex)
    }
}

private fun appendOptimizedStickerStyled(
    builder: SpannableStringBuilder,
    text: String,
    vararg spans: Any
) {
    if (text.isEmpty()) return
    val start = builder.length
    builder.append(text)
    spans.forEach { span -> builder.setSpan(span, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
}

private fun appendOptimizedStickerSeparator(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (builder.last() != '\n') builder.append('\n')
}

private fun appendOptimizedStickerBlankLine(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (!builder.endsWithOptimizedSticker("\n\n")) {
        if (!builder.endsWithOptimizedSticker("\n")) builder.append('\n')
        builder.append('\n')
    }
}

private fun trimOptimizedStickerExtraBlankBeforeBlock(builder: SpannableStringBuilder) {
    while (builder.endsWithOptimizedSticker("\n\n")) {
        builder.delete(builder.length - 1, builder.length)
    }
}

private fun trimOptimizedStickerTrailingNewlines(builder: SpannableStringBuilder) {
    while (builder.isNotEmpty() && builder.last() == '\n') {
        builder.delete(builder.length - 1, builder.length)
    }
}

private fun CharSequence.endsWithOptimizedSticker(value: String): Boolean {
    if (length < value.length) return false
    return substring(length - value.length, length) == value
}

private class OptimizedStickerWeightSpan(private val typefaceStyle: Int) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = Typeface.create(textPaint.typeface, typefaceStyle)
    }
}

private class OptimizedStickerTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
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
        destination.set(left, stickerTop, left + sizePx, stickerTop + sizePx)
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

private class OptimizedStickerFormulaSpan(
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
