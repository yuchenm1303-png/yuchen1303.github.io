package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController

@Composable
internal fun AgentChatHeaderOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val agentEnabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    var overlayVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(overlayVisible, progress.updatedAt) {
        if (overlayVisible) {
            if (AgentOverlayService.canDrawOverlays(context)) {
                AgentOverlayService.ensureStarted(context.applicationContext)
            } else {
                overlayVisible = false
            }
        }
    }

    Row(
        modifier = modifier
            .offset(x = (-2).dp, y = (-50).dp)
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.075f),
                        Color(0xFF72FFF2).copy(alpha = 0.052f),
                        Color(0xFF7B8CFF).copy(alpha = 0.045f),
                        Color.White.copy(alpha = 0.036f)
                    )
                )
            )
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentHeaderSwitchPill(
            label = "Agent",
            enabled = agentEnabled,
            activeColors = listOf(Color(0xEE74FFF1), Color(0xCC4F83FF), Color(0xAA6C55FF)),
            onClick = { AgentRuntimeController.setEnabled(!agentEnabled) }
        )
        AgentHeaderSwitchPill(
            label = "浮窗",
            enabled = overlayVisible,
            activeColors = listOf(Color(0xEE8DFFF4), Color(0xCC9B73FF), Color(0xAA4FB6FF)),
            onClick = {
                val next = !overlayVisible
                if (next) {
                    val allowed = AgentOverlayService.requestPermissionIfNeeded(context.applicationContext)
                    if (allowed) {
                        overlayVisible = true
                        AgentOverlayService.ensureStarted(context.applicationContext)
                    } else {
                        Toast.makeText(context, "请开启悬浮窗权限，用来显示智能体执行进展", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    overlayVisible = false
                    AgentOverlayService.stop(context.applicationContext)
                }
            }
        )
    }
}

@Composable
private fun AgentHeaderSwitchPill(
    label: String,
    enabled: Boolean,
    activeColors: List<Color>,
    onClick: () -> Unit
) {
    val active by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "$label-header-switch-active"
    )
    val knobOffset by animateFloatAsState(
        targetValue = if (enabled) 10f else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "$label-header-switch-knob"
    )

    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) {
                        activeColors
                    } else {
                        listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.030f))
                    }
                )
            )
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.58f + active * 0.38f),
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(23.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.16f - active * 0.035f)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (2f + knobOffset).dp)
                    .size(9.dp)
                    .graphicsLayer {
                        scaleX = 0.92f + active * 0.12f
                        scaleY = 0.92f + active * 0.12f
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (enabled) {
                            Color(0xFFF3FFFC)
                        } else {
                            Color(0xFF9EA8C5)
                        }
                    )
            )
        }
        Text(
            text = if (enabled) "开" else "关",
            color = Color.White.copy(alpha = 0.50f + active * 0.36f),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
