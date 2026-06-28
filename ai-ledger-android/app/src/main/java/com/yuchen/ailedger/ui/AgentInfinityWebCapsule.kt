package com.yuchen.ailedger.ui

import androidx.compose.runtime.Composable
import com.yuchen.ailedger.service.AgentOverlayProgress

@Composable
internal fun AgentInfinityWebCapsule(
    enabled: Boolean,
    progress: AgentOverlayProgress,
    onClick: () -> Unit
) {
    val state = resolveAgentInfinityState(enabled, progress)
    val motion = rememberAgentInfinityCapsuleMotion(enabled)
    AgentInfinityCapsuleBody(
        enabled = enabled,
        state = state,
        motion = motion,
        onClick = onClick
    )
}
