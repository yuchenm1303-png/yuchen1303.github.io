package com.yuchen.ailedger.service

data class VisualTargetPackageVerification(
    val verified: Boolean,
    val stableSamples: Int,
    val lastSnapshot: AgentScreenSnapshot?,
    val lastObservation: ScreenObservation?,
)
