package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * OptimizedRichMessageContent used to carry an experimental rich-text renderer.
 * The APK build failed when that experimental renderer drifted into a second
 * top-level RichMessageContent implementation with the same signature as the
 * canonical renderer in RichMessageText.kt.
 *
 * Keep a single source of truth for rich-message rendering: RichMessageText.kt.
 * This compatibility wrapper preserves existing call sites while avoiding any
 * duplicate RichMessageContent overload in this file.
 */
@Composable
fun OptimizedRichMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    RichMessageContent(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
    )
}
