package com.yuchen.ailedger.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.service.AgentOverlayLaunchPolicy
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController

@Composable
internal fun AgentChatHeaderOverlay(modifier: Modifier = Modifier) = Unit

@Composable
internal fun AgentChatGlassTitleControls(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val agentEnabled by AgentRuntimeController.enabled.collectAsState()
    val progress by AgentRuntimeController.progress.collectAsState()
    val overlayVisible by AgentOverlayLaunchPolicy.manualEnabled.collectAsState()

    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AgentInfinityWebCapsule(
            enabled = agentEnabled,
            progress = progress,
            onClick = { AgentRuntimeController.setEnabled(!agentEnabled) }
        )
        AgentHeaderSwitchPill(
            label = "浮窗",
            enabled = overlayVisible,
            activeColors = listOf(Color(0xEE8DFFF4), Color(0xCC9B73FF), Color(0xAA4FB6FF)),
            onClick = {
                if (!overlayVisible) {
                    val allowed = AgentOverlayService.requestPermissionIfNeeded(context.applicationContext)
                    if (allowed) {
                        AgentOverlayService.ensureStarted(context.applicationContext)
                    } else {
                        Toast.makeText(context, "请开启悬浮窗权限，用来显示智能体执行进展", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    AgentOverlayService.stop(context.applicationContext)
                }
            }
        )
    }
}
