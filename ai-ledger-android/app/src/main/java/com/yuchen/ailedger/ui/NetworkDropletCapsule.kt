package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

private val NetworkCapsuleSpec = LightweightPrismCapsuleDefaults.LabMax.copy(
    surfaceAlpha = 0.058f,
    rimAlpha = 0.74f,
    rimWidth = 0.58f,
    topHighlight = 0.086f,
    innerRimAlpha = 0.42f,
    bottomDepth = 0.020f,
    cornerCatchlight = 0.36f,
    pressGlow = 0.48f,
    pressEdgeBoost = 0.60f,
    rainbowRimAlpha = 0.62f,
    rainbowSweepAlpha = 0.66f,
    rainbowCornerAlpha = 0.30f
)

@Composable
fun NetworkDropletCapsule(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: Any
) {
    val clickAction = remember(onClick) { (onClick as? () -> Unit) ?: {} }
    val active = state.onlineEnabled
    val contentAlpha = if (enabled) 1f else 0.46f
    val dotColor = if (active) Color(0xFF8DF9EA) else Color.White

    LightweightPrismCapsule(
        modifier = modifier,
        spec = NetworkCapsuleSpec,
        enabled = enabled,
        onClick = clickAction
    ) { press ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size((7.5f + press * 1.5f).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(dotColor.copy(alpha = if (active) 0.82f else 0.40f))
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = if (active) "在线" else "联网",
                        color = Color.White.copy(alpha = 0.92f * contentAlpha),
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = if (active) "已开启" else "点击开启",
                        color = Color.White.copy(alpha = 0.46f * contentAlpha),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
