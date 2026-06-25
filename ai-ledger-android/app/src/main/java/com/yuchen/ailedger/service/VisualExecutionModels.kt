package com.yuchen.ailedger.service

enum class VisualSurfaceState(val wireValue: String) {
    Planning("planning"),
    Launching("launching"),
    WorkSurface("work_surface"),
    Replanning("replanning"),
}

data class VisualAgentRuntimeContext(
    val surfaceState: VisualSurfaceState = VisualSurfaceState.Planning,
    val selectedTargetPackage: String = "",
    val verifiedTargetPackage: String = "",
    val currentPackage: String = "",
    val observationId: String = "",
    val routeEpoch: Long = 0L,
    val surfaceEpoch: Long = 0L,
    val guiPlusEligible: Boolean = false,
)
