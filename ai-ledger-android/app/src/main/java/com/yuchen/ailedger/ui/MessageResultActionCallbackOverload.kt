package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Supports direct function-reference calls such as ResultAction("代码", onOpenFiles).
 *
 * The existing result action keeps its trailing-lambda API with optional enabled/emphasis flags.
 * Keeping this overload explicit prevents a function reference from being interpreted as the
 * Boolean `enabled` argument while preserving the exact same compact result-button appearance.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ResultAction(
    label: String,
    callback: () -> Unit,
    functionReferenceMarker: Unit = Unit,
) {
    Text(
        text = label,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = callback)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}
