package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState

@Suppress("UNCHECKED_CAST")
private fun Any.asClickAction(): () -> Unit = this as? (() -> Unit) ?: {}

@Composable
internal fun ModelChip(state: AssistantUiState, modifier: Modifier, onClick: Any) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(44.dp),
        role = GlassRole.Chip,
        onClick = onClick.asClickAction()
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(state.selectedModelLabel, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.onlineEnabled) "联网已开" else "纯文本模式", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            AnimatedVisibility(
                visible = state.isSending,
                enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 3 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 }
            ) {
                CompatThinkingDots(size = 4, color = Color.White.copy(alpha = 0.62f))
            }
            if (!state.isSending) Text("切换", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun StatusChip(
    label: String,
    value: String,
    accent: Color,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: Any
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.96f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(44.dp),
        role = GlassRole.Chip,
        onClick = onClick.asClickAction()
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompatPulseDot(active = state.onlineEnabled || state.isSending, color = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CompatPulseDot(active: Boolean, color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer {
                alpha = if (active) 0.95f else 0.72f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
}

@Composable
private fun CompatThinkingDots(size: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        alpha = 0.42f + index * 0.20f
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
}
