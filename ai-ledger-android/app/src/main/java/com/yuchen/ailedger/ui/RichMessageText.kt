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
import kotlin.math.roundToInt
import ru.noties.jlatexmath.JLatexMathDrawable

private val richMessageTokenRegex = Regex(
    pattern = """(\*\*.+?\*\*)|(\\\\\(.+?\\\\\))|(\\\\\[.+?\\\\\])|(\$\$.+?\$\$)|(?m)^\s{0,3}#{1,3}\s+|(?m)^\s*---+\s*$|(?m)^\s*[-*]\s+""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
)

private val inlineTokenRegex = Regex(
    pattern = """(@@FORMULA_\d+@@)|(\\\[(.+?)\\\])|(\\\((.+?)\\\))|(\*\*(.+?)\*\*)|(`([^`]+)`)""",
    options = setOf(RegexOption.DOT_MATCHES_ALL)
)

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
            textSizePx * 1.42f
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
                setLineSpacing(lineHeightPx - textSizePx, 1f)
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            textView.setTextColor(resolvedColor.toArgb())
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSizePx)
            textView.setLineSpacing(lineHeightPx - textSizePx, 1f)
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
    val (preprocessed, tokenMap) = preprocessDisplayMathBlocks(raw)
    val builder = SpannableStringBuilder()
    val lines = preprocessed
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .lines()

    lines.forEachIndexed { index, sourceLine ->
        val line = sourceLine.trimEnd()
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> {
                if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
                    builder.append("\n\n")
                }
            }
            trimmed.matches(Regex("""---+""")) -> {
                appendStyledSegment(builder, "────────", RelativeSizeSpan(0.96f), StyleSpan(Typeface.NORMAL))
                if (index != lines.lastIndex) builder.append('\n')
            }
            trimmed.startsWith("### ") -> {
                appendStyledSegment(builder, trimmed.removePrefix("### ").trim(), RelativeSizeSpan(1.01f), StyleSpan(Typeface.BOLD))
                if (index != lines.lastIndex) builder.append('\n')
            }
            trimmed.startsWith("## ") -> {
                appendStyledSegment(builder, trimmed.removePrefix("## ").trim(), RelativeSizeSpan(1.03f), StyleSpan(Typeface.BOLD))
                if (index != lines.lastIndex) builder.append('\n')
            }
            trimmed.startsWith("# ") -> {
                appendStyledSegment(builder, trimmed.removePrefix("# ").trim(), RelativeSizeSpan(1.05f), StyleSpan(Typeface.BOLD))
                if (index != lines.lastIndex) builder.append('\n')
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                builder.append("• ")
                appendInlineMarkdown(builder, trimmed.drop(2).trim(), context, tokenMap, textColor, textSizePx)
                if (index != lines.lastIndex) builder.append('\n')
            }
            else -> {
                appendInlineMarkdown(builder, line, context, tokenMap, textColor, textSizePx)
                if (index != lines.lastIndex) builder.append('\n')
            }
        }
    }

    if (baseFontWeight == Typeface.BOLD && builder.isNotEmpty()) {
        builder.setSpan(WeightSpan(Typeface.NORMAL), 0, builder.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }
    return builder
}

private fun appendInlineMarkdown(
    builder: SpannableStringBuilder,
    source: String,
    context: Context,
    tokenMap: Map<String, FormulaToken>,
    textColor: Int,
    textSizePx: Float
) {
    var cursor = 0
    inlineTokenRegex.findAll(source).forEach { match ->
        if (match.range.first > cursor) {
            builder.append(source.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("@@FORMULA_") -> {
                val formulaToken = tokenMap[token]
                if (formulaToken != null) appendFormula(builder, context, formulaToken.latex, formulaToken.display, textColor, textSizePx) else builder.append(token)
            }
            token.startsWith("\\[") && token.endsWith("\\]") -> {
                appendFormula(builder, context, token.removePrefix("\\[").removeSuffix("\\]").trim(), true, textColor, textSizePx)
            }
            token.startsWith("\\(") && token.endsWith("\\)") -> {
                appendFormula(builder, context, token.removePrefix("\\(").removeSuffix("\\)").trim(), false, textColor, textSizePx)
            }
            token.startsWith("**") && token.endsWith("**") -> {
                appendStyledSegment(builder, token.removePrefix("**").removeSuffix("**"), WeightSpan(Typeface.BOLD))
            }
            token.startsWith("`") && token.endsWith("`") -> {
                appendStyledSegment(builder, token.removePrefix("`").removeSuffix("`"), TypefaceSpanCompat(Typeface.MONOSPACE))
            }
            else -> builder.append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < source.length) {
        builder.append(source.substring(cursor))
    }
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
            .textSize(if (display) textSizePx * 1.06f else textSizePx * 0.98f)
            .color(textColor)
            .align(if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
            .padding(if (display) 2 else 0)
            .build()
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        if (display && builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
        val start = builder.length
        builder.append('\uFFFC')
        builder.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (display) builder.append('\n')
    } catch (_: Throwable) {
        builder.append(if (display) "[$cleanLatex]" else cleanLatex)
    }
}

private fun appendStyledSegment(
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

private fun preprocessDisplayMathBlocks(source: String): Pair<String, Map<String, FormulaToken>> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
    val output = mutableListOf<String>()
    val tokens = linkedMapOf<String, FormulaToken>()
    var index = 0
    var tokenIndex = 0

    while (index < lines.size) {
        val trimmed = lines[index].trim()
        when (trimmed) {
            "\\[" -> {
                index += 1
                val body = StringBuilder()
                while (index < lines.size && lines[index].trim() != "\\]") {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[index].trim())
                    index += 1
                }
                val key = "@@FORMULA_${tokenIndex++}@@"
                tokens[key] = FormulaToken(key, body.toString(), true)
                output += key
                if (index < lines.size && lines[index].trim() == "\\]") index += 1
            }
            "$$" -> {
                index += 1
                val body = StringBuilder()
                while (index < lines.size && lines[index].trim() != "$$") {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[index].trim())
                    index += 1
                }
                val key = "@@FORMULA_${tokenIndex++}@@"
                tokens[key] = FormulaToken(key, body.toString(), true)
                output += key
                if (index < lines.size && lines[index].trim() == "$$") index += 1
            }
            else -> {
                output += lines[index]
                index += 1
            }
        }
    }

    return output.joinToString("\n") to tokens
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
