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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.noties.jlatexmath.JLatexMathDrawable

private val richMessageTokenRegex = Regex(
    pattern = """(\*\*.+?\*\*)|(\\\(.+?\\\))|(\\\[.+?\\\])|(\$\$.+?\$\$)|(?m)^\s{0,3}#{1,6}\s+|(?m)^\s*---+\s*$|(?m)^\s*[-*]\s+|(?m)^\s*\|.+\|\s*$|(?m)^\s*【样本\d+】\s*$|(?m)^\s*>\s+""",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
)

private val headingRegex = Regex("""^\s*(#{1,6})\s*(.+?)\s*$""")
private val bulletRegex = Regex("""^\s*[-*•]\s+(.+?)\s*$""")
private val quoteRegex = Regex("""^\s*>\s*(.+?)\s*$""")
private val sampleLabelRegex = Regex("""^\s*【样本\d+】\s*$""")
private val tableRowRegex = Regex("""^\s*\|(.+)\|\s*$""")
private val tableDividerRegex = Regex("""^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$""")
private val displayBracketFormulaRegex = Regex("""(?s)\\\[(.+?)\\\]""")
private val displayDollarFormulaRegex = Regex("""(?s)\$\$(.+?)\$\$""")
private val inlineFormulaRegex = Regex("""(?s)\\\((.+?)\\\)""")
private val boldRegex = Regex("""\*\*(.+?)\*\*""")
private val codeRegex = Regex("""`([^`]+)`""")
private val tokenRegex = Regex("""(@@FORMULA_\d+@@)|(@@CODE_\d+@@)|(@@BOLD_\d+@@)""")
private val leadingFormulaGapRegex = Regex("""\n{2,}(@@FORMULA_\d+@@)""")
private val trailingFormulaGapRegex = Regex("""(@@FORMULA_\d+@@)\n{2,}""")

private data class FormulaToken(
    val key: String,
    val latex: String,
    val display: Boolean
)

private data class MobileCommandPanelData(
    val intro: String,
    val title: String,
    val detail: String,
    val status: String,
    val result: String?,
    val pending: Boolean
)

private fun hasRichMessageTokens(source: String): Boolean {
    if (richMessageTokenRegex.containsMatchIn(source)) return true
    val normalized = sanitizeRichTextSource(source)
    return normalized != source && richMessageTokenRegex.containsMatchIn(normalized)
}

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
        hasRichMessageTokens(text)

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
            textSizePx * 1.28f
        }
    }

    val mobileCommandPanel = remember(text) { parseMobileCommandPanel(text) }
    if (mobileCommandPanel != null) {
        MobileCommandInlineCard(
            data = mobileCommandPanel,
            modifier = modifier,
            color = resolvedColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        )
        return
    }

    if (!hasRichMessageTokens(text)) {
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

@Composable
private fun MobileCommandInlineCard(
    data: MobileCommandPanelData,
    modifier: Modifier,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?
) {
    val quickReply = LocalMobileCommandQuickReply.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (data.intro.isNotBlank()) {
            MaterialText(
                text = data.intro,
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = fontWeight ?: FontWeight.Medium
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.078f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (data.pending) Color(0xFF8DF9EA).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.58f))
                )
                MaterialText(
                    text = data.title,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                MaterialText(
                    text = data.status,
                    color = if (data.pending) Color(0xFF8DF9EA).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.58f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            MaterialText(
                text = data.detail,
                color = Color.White.copy(alpha = 0.74f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (data.pending) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    MobileCommandChip("确认执行", onClick = { quickReply?.invoke("确认") })
                    MobileCommandChip("取消", dim = true, onClick = { quickReply?.invoke("取消") })
                }
            } else if (!data.result.isNullOrBlank()) {
                MaterialText(
                    text = data.result,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MobileCommandChip(text: String, dim: Boolean = false, onClick: (() -> Unit)? = null) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (dim) 0.055f else 0.105f))
            .then(clickableModifier)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        MaterialText(
            text = text,
            color = Color.White.copy(alpha = if (dim) 0.50f else 0.78f),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

private fun parseMobileCommandPanel(text: String): MobileCommandPanelData? {
    val clean = text.trim()
    if (clean.isBlank()) return null
    if (clean.contains("动作：") && clean.contains("详情：")) {
        val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
        val intro = lines.firstOrNull().orEmpty()
        val title = lines.firstOrNull { it.startsWith("动作：") }?.removePrefix("动作：")?.trim().orEmpty()
        val detail = lines.firstOrNull { it.startsWith("详情：") }?.removePrefix("详情：")?.trim().orEmpty()
        if (title.isBlank() || detail.isBlank()) return null
        return MobileCommandPanelData(
            intro = intro,
            title = title,
            detail = detail,
            status = "待确认",
            result = "回复“确认”执行，或回复“取消”。",
            pending = true
        )
    }
    if (clean.startsWith("已取消这个手机动作：")) {
        val payload = clean.removePrefix("已取消这个手机动作：").removeSuffix("。")
        val title = payload.substringBefore("·").trim().ifBlank { "手机动作" }
        val detail = payload.substringAfter("·", "已取消").trim()
        return MobileCommandPanelData(
            intro = "已取消这个手机动作。",
            title = title,
            detail = detail,
            status = "已取消",
            result = null,
            pending = false
        )
    }
    val resultLabel = when {
        clean.contains("执行结果：") -> "执行结果："
        clean.contains("执行失败：") -> "执行失败："
        else -> null
    } ?: return null
    val intro = clean.substringBefore(resultLabel).trim().lineSequence().firstOrNull().orEmpty()
    val result = clean.substringAfter(resultLabel).trim()
    return MobileCommandPanelData(
        intro = intro,
        title = inferMobileCommandTitle(clean),
        detail = inferMobileCommandDetail(clean),
        status = if (resultLabel.contains("失败")) "未完成" else "已执行",
        result = result,
        pending = false
    )
}

private fun inferMobileCommandTitle(text: String): String = when {
    text.contains("设置闹钟") -> "设置系统闹钟"
    text.contains("打开“") -> "打开手机应用"
    text.contains("导航到") -> "地图导航"
    else -> "手机动作"
}

private fun inferMobileCommandDetail(text: String): String {
    val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
    return firstLine.removePrefix("我理解为要").removeSuffix("。").ifBlank { "已处理手机动作" }
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
            sampleLabelRegex.matches(trimmed) -> {
                appendCompactSeparator(builder)
                appendStyled(builder, trimmed, RelativeSizeSpan(0.92f), WeightSpan(Typeface.BOLD))
            }
            quoteRegex.matches(trimmed) -> {
                val content = quoteRegex.matchEntire(trimmed)!!.groupValues[1]
                appendCompactSeparator(builder)
                appendInline(builder, content, context, formulaTokens, textColor, textSizePx)
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
                    appendInline(builder, cells.joinToString("  ·  "), context, formulaTokens, textColor, textSizePx)
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
    working = leadingFormulaGapRegex.replace(working, "\n$1")
    working = trailingFormulaGapRegex.replace(working, "$1\n")
    return working to tokens
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
        when (val token = match.value) {
            in formulaTokens.keys -> {
                val formula = formulaTokens[token]
                if (formula != null) {
                    appendFormula(builder, context, formula.latex, formula.display, textColor, textSizePx)
                }
            }
            else -> when {
                token.startsWith("@@CODE_") -> appendStyled(builder, codeTokens[token].orEmpty(), TypefaceSpanCompat(Typeface.MONOSPACE))
                token.startsWith("@@BOLD_") -> appendStyled(builder, boldTokens[token].orEmpty(), WeightSpan(Typeface.BOLD))
            }
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
    trimExtraBlankBeforeBlock(builder)
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
            .textSize(if (display) textSizePx * 0.82f else textSizePx * 0.84f)
            .color(textColor)
            .align(if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
            .padding(0)
            .build()
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val start = builder.length
        builder.append('\uFFFC')
        builder.setSpan(FormulaDrawableSpan(drawable, display, textSizePx), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
    if (builder.last() != '\n') builder.append('\n')
}

private fun appendCompactBlankLine(builder: SpannableStringBuilder) {
    if (builder.isEmpty()) return
    if (!builder.endsWith("\n\n")) {
        if (!builder.endsWith("\n")) builder.append('\n')
        builder.append('\n')
    }
}

private fun trimExtraBlankBeforeBlock(builder: SpannableStringBuilder) {
    while (builder.endsWith("\n\n")) {
        builder.delete(builder.length - 1, builder.length)
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

private class FormulaDrawableSpan(
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
