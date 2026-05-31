package com.yuchen.ailedger.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ImageSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathDrawable

private val richMessageTokenRegex = Regex(
    pattern = """(\*\*.+?\*\*)|(\\\\\(.+?\\\\\))|(\\\\\[.+?\\\\\])|(\$\$.+?\$\$)|(?m)^\s{0,3}#{1,6}\s+|(?m)^\s*---+\s*$|(?m)^\s*[-*]\s+|(?m)^\s*\|.+\|\s*$""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
)

private val headingRegex = Regex("""^\s*(#{1,6})\s*(.+?)\s*$""")
private val bulletRegex = Regex("""^\s*[-*•]\s+(.+?)\s*$""")
private val tableRowRegex = Regex("""^\s*\|(.+)\|\s*$""")
private val tableDividerRegex = Regex("""^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$""")
private val displayBracketFormulaRegex = Regex("""(?s)\\\[(.+?)\\\]""")
private val displayDollarFormulaRegex = Regex("""(?s)\$\$(.+?)\$\$""")
private val inlineFormulaRegex = Regex("""(?s)\\\((.+?)\\\)""")
private val boldRegex = Regex("""\*\*(.+?)\*\*""")
private val codeRegex = Regex("""`([^`]+)`""")
private val tokenRegex = Regex("""(@@FORMULA_\d+@@)|(@@CODE_\d+@@)|(@@BOLD_\d+@@)""")

private data class FormulaToken(
    val key: String,
    val latex: String,
    val display: Boolean
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
            textSizePx * 1.34f
        }
    }

    if (!richMessageTokenRegex.containsMatchIn(text)) {
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

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                includeFontPadding = false
                setTextColor(resolvedColor.toArgb())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            textView.setTextColor(resolvedColor.toArgb())
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
            textView.setLineSpacing((lineHeightPx - textSizePx).coerceAtLeast(0f), 1f)
            textView.text = buildRichMessageSpannable(
                context = textView.context,
                raw = text,
                textColor = resolvedColor.toArgb(),
                textSizePx = textSizePx,
                baseFontWeight = if (fontWeight == FontWeight.Bold || fontWeight == FontWeight.ExtraBold || fontWeight == FontWeight.Black) Typeface.BOLD else Typeface.NORMAL
            )
        }
    )
}

private fun buildRichMessageSpannable(
    context: Context,
    raw: String,
    textColor: Int,
    textSizePx: Float,
    baseFontWeight: Int
): CharSequence {
    val normalized = sanitizeRichTextSource(raw)
    val (tokenized, formulaTokens) = extractFormulaTokens(normalized)
    val builder = SpannableStringBuilder()
    val lines = tokenized.lines()

    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            appendCompactBlankLine(builder)
            return@forEach
        }

        val formulaToken = formulaTokens[trimmed]
        if (formulaToken != null) {
            appendDisplayFormula(builder, context, formulaToken, textColor, textSizePx)
            return@forEach
        }

        when {
            trimmed.matches(Regex("""---+""")) -> {
                appendCompactSeparator(builder)
                appendStyled(builder, "────────", RelativeSizeSpan(0.96f), StyleSpan(Typeface.NORMAL))
            }
            headingRegex.matches(trimmed) -> {
                val match = headingRegex.matchEntire(trimmed)!!
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val headingText = match.groupValues[2].trim()
                val size = when (level) {
                    1 -> 1.04f
                    2 -> 1.03f
                    3 -> 1.01f
                    else -> 1.00f
                }
                appendCompactSeparator(builder)
                appendInline(builder, headingText, context, formulaTokens, textColor, textSizePx)
                builder.setSpan(RelativeSizeSpan(size), findLineStart(builder), builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(WeightSpan(Typeface.BOLD), findLineStart(builder), builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            bulletRegex.matches(trimmed) -> {
                val content = bulletRegex.matchEntire(trimmed)!!.groupValues[1]
                appendCompactSeparator(builder)
                builder.append("• ")
                appendInline(builder, content, context, formulaTokens, textColor, textSizePx)
            }
            tableDividerRegex.matches(trimmed) -> {
            }
            tableRowRegex.matches(trimmed) -> {
                val cells = tableRowRegex.matchEntire(trimmed)!!.groupValues[1]
                    .split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    appendCompactSeparator(builder)
                    appendInline(builder, cells.joinToString("    "), context, formulaTokens, textColor, textSizePx)
                }
            }
            else -> {
                appendCompactSeparator(builder)
                appendInline(builder, line.trim(), context, formulaTokens, textColor, textSizePx)
            }
        }
    }

    trimTrailingNewlines(builder)
    if (baseFontWeight == Typeface.BOLD && builder.isNotEmpty()) {
        builder.setSpan(WeightSpan(Typeface.NORMAL), 0, builder.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }
    return builder
}

private fun sanitizeRichTextSource(source: String): String {
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

private fun extractFormulaTokens(source: String): Pair<String, Map<String, FormulaToken>> {
    val tokens = linkedMapOf<String, FormulaToken>()
    var counter = 0
    fun nextKey(): String = "@@FORMULA_${counter++}@@"

    var working = source
    working = displayBracketFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = FormulaToken(key, match.groupValues[1].trim(), true)
        "\n$key\n"
    }
    working = displayDollarFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = FormulaToken(key, match.groupValues[1].trim(), true)
        "\n$key\n"
    }
    working = inlineFormulaRegex.replace(working) { match ->
        val key = nextKey()
        tokens[key] = FormulaToken(key, match.groupValues[1].trim(), false)
        key
    }
    return working to tokens
}

private fun appendInline(
    builder: SpannableStringBuilder,
    source: String,
    formulaTokens: Map<String, FormulaToken>,
    textColor: Int,
    textSizePx: Float,
    context: Context
) {
    appendInline(builder, source, context, formulaTokens, textColor, textSizePx)
}

private fun appendInline(
    builder: SpannableStringBuilder,
    source: String,
    context: Context,
    formulaTokens: Map<String, FormulaToken>,
    textColor: Int,
    textSizePx: Float
) {
    val codeTokens = linkedMapOf<String, String>()
    val boldTokens = linkedMapOf<String, String>()
    var codeIndex = 0
    var boldIndex = 0
    var working = source

    working = codeRegex.replace(working) { match ->
        val key = "@@CODE_${codeIndex++}@@"
        codeTokens[key] = match.groupValues[1]
        key
    }
    working = boldRegex.replace(working) { match ->
        val key = "@@BOLD_${boldIndex++}@@"
        boldTokens[key] = match.groupValues[1]
        key
    }

    var cursor = 0
    tokenRegex.findAll(working).forEach { match ->
        if (match.range.first > cursor) {
            builder.append(working.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("@@FORMULA_") -> {
                val formula = formulaTokens[token]
                if (formula != null) {
                    appendFormula(builder, context, formula.latex, false, textColor, textSizePx)
                } else {
                    builder.append(token)
                }
            }
            token.startsWith("@@CODE_") -> {
                appendStyled(builder, codeTokens[token].orEmpty(), TypefaceSpanCompat(Typeface.MONOSPACE))
            }
            token.startsWith("@@BOLD_") -> {
                appendStyled(builder, boldTokens[token].orEmpty(), WeightSpan(Typeface.BOLD))
            }
            else -> builder.append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < working.length) {
        builder.append(working.substring(cursor))
    }
}

private fun appendDisplayFormula(
    builder: SpannableStringBuilder,
    context: Context,
    token: FormulaToken,
    textColor: Int,
    textSizePx: Float
) {
    appendCompactSeparator(builder)
    appendFormula(builder, context, token.latex, true, textColor, textSizePx)
    builder.append('\n')
}

private fun appendFormula(
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
            .textSize(if (display) textSizePx * 0.90f else textSizePx * 0.88f)
            .color(textColor)
            .align(if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
            .padding(0)
            .build()
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val start = builder.length
        builder.append('\uFFFC')
        builder.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    } catch (_: Throwable) {
        builder.append(cleanLatex)
    }
}

private fun appendStyled(
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

private fun appendCompactSeparator(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (builder.last() != '\n') {
        builder.append('\n')
    }
}

private fun appendCompactBlankLine(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (!builder.endsWith("\n")) {
        builder.append('\n')
    }
}

private fun trimTrailingNewlines(builder: SpannableStringBuilder) {
    while (builder.isNotEmpty() && builder.last() == '\n') {
        builder.delete(builder.length - 1, builder.length)
    }
}

private fun findLineStart(builder: SpannableStringBuilder): Int {
    for (i in builder.length - 1 downTo 0) {
        if (builder[i] == '\n') return i + 1
    }
    return 0
}

private fun CharSequence.endsWith(value: String): Boolean {
    if (length < value.length) return false
    return substring(length - value.length, length) == value
}

private class WeightSpan(private val typefaceStyle: Int) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = Typeface.create(textPaint.typeface, typefaceStyle)
    }
}

private class TypefaceSpanCompat(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) = apply(textPaint)
    override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
    private fun apply(textPaint: TextPaint) {
        textPaint.typeface = typeface
    }
}
