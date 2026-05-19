package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import kotlin.math.PI
import kotlin.math.sin

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
    val transition = rememberInfiniteTransition(label = "compat-status-dot")
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "compat-status-dot-pulse"
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer {
                val s = if (active) pulse else 1f
                scaleX = s
                scaleY = s
                alpha = if (active) 0.95f else 0.72f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
}

@Composable
private fun CompatThinkingDots(size: Int, color: Color) {
    val transition = rememberInfiniteTransition(label = "compat-thinking-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(960), repeatMode = RepeatMode.Restart),
        label = "compat-thinking-phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val wave = ((sin(phase * 2f * PI.toFloat() + index * 1.35f) + 1f) / 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        translationY = -5f * wave
                        alpha = 0.35f + 0.65f * wave
                        scaleX = 0.78f + 0.22f * wave
                        scaleY = 0.78f + 0.22f * wave
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
}
