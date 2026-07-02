package com.yuchen.ailedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 无自定义宽度场景的设备优化主操作按钮，避免调用处重复传入默认 Modifier。
 */
@Composable
internal fun OptimizePrimaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(0xFF8DF9EA)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = if (enabled) 0.13f else 0.04f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.28f else 0.08f)),
    ) {
        Text(
            text,
            color = accent.copy(alpha = if (enabled) 0.92f else 0.34f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(11.dp),
        )
    }
}
