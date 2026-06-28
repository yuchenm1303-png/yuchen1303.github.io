package com.yuchen.ailedger.ui

import com.yuchen.ailedger.service.AgentOverlayProgress

internal fun resolveAgentInfinityState(
    enabled: Boolean,
    progress: AgentOverlayProgress
): AgentInfinityWebState = when {
    !enabled -> AgentInfinityWebState.Off
    progress.userTakeoverPaused -> AgentInfinityWebState.Paused
    progress.running -> AgentInfinityWebState.Running
    progress.status.contains("失败") ||
        progress.status.contains("错误") ||
        progress.status.contains("异常") -> AgentInfinityWebState.Error
    else -> AgentInfinityWebState.Standby
}
