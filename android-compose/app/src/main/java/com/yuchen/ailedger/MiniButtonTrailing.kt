package com.yuchen.ailedger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MiniButton(
    text: String,
    horizontal: Dp = 15.dp,
    vertical: Dp = 9.dp,
    fontSize: Int = 13,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = if (pressed) 0.96f else 1f
                scaleY = if (pressed) 0.96f else 1f
            }
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .background(Color.White.copy(alpha = 0.060f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(999.dp))
            .padding(horizontal = horizontal, vertical = vertical),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xF5F8FAFF),
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black
        )
    }
}
