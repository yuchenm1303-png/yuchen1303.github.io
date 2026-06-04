package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController

@Composable
internal fun AgentChatHeaderOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    val knobOffset by animateFloatAsState(if (enabled) 18f else 0f, label = "agent-switch-knob")

    LaunchedEffect(enabled, progress.running, progress.updatedAt) {
        if (enabled && progress.running && AgentOverlayService.canDrawOverlays(context)) {
            AgentOverlayService.ensureStarted(context.applicationContext)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled) {
                        listOf(Color(0xAA31F6E2), Color(0x883B7BFF), Color(0x66543CFF))
                    } else {
                        listOf(Color(0x44FFFFFF), Color(0x22FFFFFF))
                    }
                )
            )
            .clickable {
                val next = !enabled
                AgentRuntimeController.setEnabled(next)
                if (next) {
                    val allowed = AgentOverlayService.requestPermissionIfNeeded(context.applicationContext)
                    if (allowed) AgentOverlayService.ensureStarted(context.applicationContext)
                    else Toast.makeText(context, "请开启悬浮窗权限，用来显示智能体执行进展", Toast.LENGTH_SHORT).show()
                } else {
                    AgentOverlayService.stop(context.applicationContext)
                }
            }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .widthIn(min = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Agent",
            color = Color.White.copy(alpha = if (enabled) 0.98f else 0.62f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = if (enabled) 0.24f else 0.13f)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFFE9FFFB) else Color(0xFFB8C0D4))
            )
        }
        Spacer(Modifier.width(1.dp))
        Text(
            text = if (enabled) "开" else "关",
            color = Color.White.copy(alpha = if (enabled) 0.94f else 0.58f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
