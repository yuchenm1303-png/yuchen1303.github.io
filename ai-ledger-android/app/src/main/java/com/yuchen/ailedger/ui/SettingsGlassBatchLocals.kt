package com.yuchen.ailedger.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 仅保留旧实验卡的编译兼容值；生产设置页不提供动画时钟，也不会启动逐帧循环。
 */
internal class SettingsFrostMotionClock(
    val frameNanos: Long = 0L
)

internal val LocalSettingsFrostMotionClock =
    staticCompositionLocalOf<SettingsFrostMotionClock?> { null }

/**
 * 设置页 Frost/Inset 静态批绘制就绪门。
 */
internal val LocalSettingsStaticBatchReady = staticCompositionLocalOf { true }
