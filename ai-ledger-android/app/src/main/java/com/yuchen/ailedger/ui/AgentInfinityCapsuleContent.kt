package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun AgentInfinityCapsuleContent(
    enabled: Boolean,
    state: AgentInfinityWebState,
    active: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize().padding(start = 7.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "Agent",
            color = Color.White.copy(alpha = 0.58f + active * 0.38f),
            fontSize = 9.2.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        AndroidView(
            factory = { AgentInfinityCanvasView(it) },
            update = { it.setState(enabled, state) },
            modifier = Modifier.width(42.dp).height(18.dp)
        )
    }
}
