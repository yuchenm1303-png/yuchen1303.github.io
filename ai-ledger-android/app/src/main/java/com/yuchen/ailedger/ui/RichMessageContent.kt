package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private data class CitationInlineToken(
    val id: String,
    val number: String
)

private data class CitationInlineRender(
    val annotated: AnnotatedString,
    val tokens: List<CitationInlineToken>
)

@Composable
fun RichMessageContent(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    CitationInlineRichText(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

@Composable
fun CitationInlineRichText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val render = remember(text) { buildCitationInlineRender(text) }

    if (render.tokens.isEmpty()) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }

    val inlineContent = remember(render.tokens) {
        render.tokens.associate { token ->
            val chipWidth = if (token.number.length >= 2) 25.sp else 19.sp
            token.id to InlineTextContent(
                Placeholder(
                    width = chipWidth,
                    height = 16.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                CitationBubble(number = token.number)
            }
        }
    }

    Text(
        text = render.annotated,
        inlineContent = inlineContent,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

private fun buildCitationInlineRender(text: String): CitationInlineRender {
    val citationRegex = Regex("""\[(\d{1,2})]""")
    val tokens = mutableListOf<CitationInlineToken>()
    var lastIndex = 0
    var tokenIndex = 0

    val annotated = buildAnnotatedString {
        citationRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            val number = match.groupValues[1]
            val id = "citation_${tokenIndex}_${number}"
            tokens += CitationInlineToken(id = id, number = number)
            appendInlineContent(id, "[$number]")

            lastIndex = end
            tokenIndex += 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    return CitationInlineRender(annotated = annotated, tokens = tokens)
}

@Composable
private fun CitationBubble(number: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF8DF9EA).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            color = Color(0xFF8DF9EA).copy(alpha = 0.92f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}
