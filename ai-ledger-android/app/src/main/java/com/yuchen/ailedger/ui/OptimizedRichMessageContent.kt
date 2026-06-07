package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.DynamicDrawableSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathDrawable

private val optimizedRichMessageTokenRegex = Regex(
    pattern = """(\*\*.+?\*\*)|(\\\(.+?\\\))|(\\\[.+?\\\])|(\$\$.+?\$\$)|(?m)^\s{0,3}#{1,6}\s+|(?m)^\s*---+\s*$|(?m)^\s*[-*]\s+|(?m)^\s*\|.+\|\s*$|(?m)^\s*【样本\d+】\s*$|(?m)^\s*>\s+""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
)

private val optimizedHeadingRegex = Regex("""^\s*(#{1,6})\s*(.+?)\s*$""")
private val optimizedBulletRegex = Regex("""^\s*[-*•]\s+(.+?)\s*$""")
private val optimizedQuoteRegex = Regex("""^\s*>\s*(.+?)\s*$""")
private val optimizedSampleLabelRegex = Regex("""^\s*【样本\d+】\s*$""")
private val optimizedTableRowRegex = Regex("""^\s*\|(.+)\|\s*$""")
private val optimizedTableDividerRegex = Regex("""^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$""")
private val optimizedHorizontalRuleRegex = Regex("""---+""")
private val optimizedDisplayBracketFormulaRegex = Regex("""(?s)\\\[(.+?)\\\]""")
private val optimizedDisplayDollarFormulaRegex = Regex("""(?s)\$\$(.+?)\$\$""")
private val optimizedInlineFormulaRegex = Regex("""(?s)\\\((.+?)\\\)""")
private val optimizedBoldRegex = Regex("""\*\*(.+?)\*\*""")
private val optimizedCodeRegex = Regex("""`([^`]+)`""")
private val optimizedTokenRegex = Regex("""(@@FORMULA_\d+@@)|(@@CODE_\d+@@)|(@@BOLD_\d+@@)""")
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
    val lineHeightBits: Int
)

private object OptimizedRichMessageTextCache {
    private const val MaxEntries = 64
    private val entries = object : LinkedHashMap<OptimizedRichTextRenderKey, CharSequence>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<OptimizedRichTextRenderKey, CharSequence>?): Boolean {
            return size > MaxEntries
        }
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

    val hasRichMarkup = remember(text) {
        optimizedMayContainRichMarkup(text) && optimizedRichMessageTokenRegex.containsMatchIn(text)
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

    val baseFontWeight = if (fontWeight == FontWeight.Bold || fontWeight == FontWeight.ExtraBold || fontWeight == FontWeight.Black) {
        Typeface.BOLD
    } else {
        Typeface.NORMAL
    }
    val textColorArgb = resolvedColor.toArgb()
    val renderKey = remember(text, textColorArgb, textSizePx, lineHeightPx, baseFontWeight) {
        OptimizedRichTextRenderKey(
            text = text,
            textColor = textColorArgb,
            textSizeBits = textSizePx.toBits(),
            baseFontWeight = baseFontWeight,
            lineHeightBits = lineHeightPx.toBits()
        )
    }
    val richText = remember(context, renderKey) {
        OptimizedRichMessageTextCache.getOrPut(renderKey) {
            buildOptimizedRichMessageSpannable(
                context = context,
                raw = text,
                textColor = textColorArgb,
                textSizePx = textSizePx,
                baseFontWeight = baseFontWeight
            )
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = false
                setTextColor(textColorArgb)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            val currentKey = textView.tag as? OptimizedRichTextRenderKey
            if (currentKey != renderKey) {
                textView.setTextColor(textColorArgb)
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
                textView.setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
                textView.text = richText
                textView.tag = renderKey
            }
        }
    )
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
            '*', '\\', '$', '#', '-', '|', '【', '>', '`' -> return true
        }
    }
    return false
}

private fun buildOptimizedRichMessageSpannable(
    context: Context,
    raw: String,
    textColor: Int,
    textSizePx: Float,
    baseFontWeight: Int
): CharSequence {
    val normalized = sanitizeOptimizedRichTextSource(raw)
    val (tokenized, formulaTokens) = extractOptimizedFormulaTokens(normalized)
    val builder = SpannableStringBuilder()
    val lines = tokenized.lines()

    lines.forEach { rawLine ->
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
                val content = optimizedQuoteRegex.matchEntire(trimmed)!!.groupValues[1]
                appendOptimizedCompactSeparator(builder)
                appendOptimizedInline(builder, content, context, formulaTokens, textColor, textSizePx)
            }
            optimizedHeadingRegex.matches(trimmed) -> {
                val match = optimizedHeadingRegex.matchEntire(trimmed)!!
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val headingText = match.groupValues[2].trim()
                val size = when (level) {
                    1 -> 1.04f
                    2 -> 1.03f
                    3 -> 1.01f
                    else -> 1.00f
                }
                appendOptimizedCompactSeparator(builder)
                appendOptimizedInline(builder, headingText, context, formulaTokens, textColor, textSizePx)
                builder.setSpan(RelativeSizeSpan(size), findOptimizedLineStart(builder), builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(OptimizedWeightSpan(Typeface.BOLD), findOptimizedLineStart(builder), builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            optimizedBulletRegex.matches(trimmed) -> {
                val content = optimizedBulletRegex.matchEntire(trimmed)!!.groupValues[1]
                appendOptimizedCompactSeparator(builder)
                builder.append("• ")
                appendOptimizedInline(builder, content, context, formulaTokens, textColor, textSizePx)
            }
            optimizedTableDividerRegex.matches(trimmed) -> {
            }
            optimizedTableRowRegex.matches(trimmed) -> {
                val cells = optimizedTableRowRegex.matchEntire(trimmed)!!.groupValues[1]
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    appendOptimizedCompactSeparator(builder)
                    appendOptimizedInline(builder, cells.joinToString("  ·  "), context, formulaTokens, textColor, textSizePx)
                }
            }
            else -> {
                appendOptimizedCompactSeparator(builder)
                appendOptimizedInline(builder, line.trim(), context, formulaTokens, textColor, textSizePx)
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
    text = text.replace(Regex("""\n{3,}"""), "\n\n")
    return text.trim()
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
    textSizePx: Float
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

    var cursor = 0
    optimizedTokenRegex.findAll(working).forEach { match ->
        if (match.range.first > cursor) {
            builder.append(working.substring(cursor, match.range.first))
        }
        when (val token = match.value) {
            in formulaTokens.keys -> {
                val formula = formulaTokens[token]
                if (formula != null) {
                    appendOptimizedFormula(builder, context, formula.latex, formula.display, textColor, textSizePx)
                }
            }
            else -> when {
                token.startsWith("@@CODE_") -> appendOptimizedStyled(builder, codeTokens[token].orEmpty(), OptimizedTypefaceSpanCompat(Typeface.MONOSPACE))
                token.startsWith("@@BOLD_") -> appendOptimizedStyled(builder, boldTokens[token].orEmpty(), OptimizedWeightSpan(Typeface.BOLD))
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < working.length) {
        builder.append(working.substring(cursor))
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
        builder.setSpan(OptimizedFormulaDrawableSpan(drawable, display, textSizePx), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

private fun findOptimizedLineStart(builder: SpannableStringBuilder): Int {
    for (i in builder.length - 1 downTo 0) {
        if (builder[i] == '\n') return i + 1
    }
    return 0
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
