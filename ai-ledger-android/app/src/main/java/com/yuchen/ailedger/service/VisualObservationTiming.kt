package com.yuchen.ailedger.service

data class VisualObservationTiming(
    val fullVisualSettleMs: Long = 110L,
    val nonVisualSettleMs: Long = 160L,
    val packageProbeSettleMs: Long = 160L,
    val openAppInitialSettleMs: Long = 260L,
    val openAppVerifyPollMs: Long = 140L,
    val openAppVerifyTimeoutMs: Long = 4200L,
    val requiredStableSamples: Int = 2,
)
