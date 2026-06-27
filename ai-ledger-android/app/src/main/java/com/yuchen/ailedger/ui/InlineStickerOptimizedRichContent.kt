package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.jvm.JvmName

private const val INLINE_STICKER_MARKER_PREFIX = "[[AI_LEDGER_INLINE_STICKER:"

@Composable
@JvmName("OptimizedRichMessageContentWithInlineSticker")
fun OptimizedRichMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight
) {
    val hasInlineStickerMarker = remember(text) {
        text.contains(INLINE_STICKER_MARKER_PREFIX, ignoreCase = true)
    }

    if (hasInlineStickerMarker) {
        CitationInlineRichText(
            text = text,
            color = if (color != Color.Unspecified) color else Color.White.copy(alpha = 0.86f),
            fontSize = if (fontSize != TextUnit.Unspecified) fontSize else 14.sp,
            lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else 20.sp,
            fontWeight = fontWeight,
            modifier = modifier
        )
        return
    }

    OptimizedRichMessageContent(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight as FontWeight?
    )
}
