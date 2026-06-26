package com.yuchen.ailedger.ui

import android.graphics.Color as NativeColor

internal object AgentInfinityWebSpec {
    const val family = 0.05f
    const val aspect = 0.71f
    const val overall = 1.65f
    const val waist = 0.97f
    const val crossAngle = 1.80f
    const val shoulder = 0.64f
    const val tipRound = 1.12f
    const val lobe = 0.60f
    const val vertical = 0.26f
    const val centerBias = 0f
    const val asym = 0.26f
    const val tilt = -10f
    const val pinch = 0.20f
    const val band = 1.58f
    const val crossingDepth = 1f
    const val glow = 1.70f
    const val dispersion = 0.85f
    const val speed = 1.69f
    const val trail = 0.56f
    const val spark = 3.20f
    const val sparkPass = 0f
    const val sparkH = 2.30f
    const val sparkV = 2.10f
    const val sparkCore = 1.90f
    const val sparkTwinkle = 0.79f
    const val sparkSoft = 1.15f
}

internal enum class AgentInfinityWebState { Off, Standby, Running, Paused, Error }
internal enum class AgentInfinityToggleDirection { On, Off }

internal data class AgentInfinityWebTheme(
    val a: Int,
    val b: Int,
    val c: Int,
    val alpha: Float,
    val speed: Float
)

internal data class AgentInfinityMotionFrame(
    val energy: Float,
    val trail: Float,
    val motion: Float,
    val flash: Float
)

internal val AgentInfinityOffTheme = AgentInfinityWebTheme(
    NativeColor.rgb(104, 117, 151), NativeColor.rgb(92, 102, 133), NativeColor.rgb(127, 139, 169), 0.34f, 0f
)
internal val AgentInfinityStandbyTheme = AgentInfinityWebTheme(
    NativeColor.rgb(102, 255, 240), NativeColor.rgb(93, 132, 255), NativeColor.rgb(164, 105, 255), 0.83f, 0.42f
)
internal val AgentInfinityRunningTheme = AgentInfinityWebTheme(
    NativeColor.rgb(105, 255, 241), NativeColor.rgb(87, 132, 255), NativeColor.rgb(186, 101, 255), 1f, 1f
)
internal val AgentInfinityPausedTheme = AgentInfinityWebTheme(
    NativeColor.rgb(255, 218, 137), NativeColor.rgb(161, 115, 255), NativeColor.rgb(111, 143, 255), 0.88f, 0f
)
internal val AgentInfinityErrorTheme = AgentInfinityWebTheme(
    NativeColor.rgb(255, 122, 151), NativeColor.rgb(209, 84, 255), NativeColor.rgb(113, 116, 255), 0.90f, 0.18f
)

internal fun AgentInfinityWebState.theme(): AgentInfinityWebTheme = when (this) {
    AgentInfinityWebState.Off -> AgentInfinityOffTheme
    AgentInfinityWebState.Standby -> AgentInfinityStandbyTheme
    AgentInfinityWebState.Running -> AgentInfinityRunningTheme
    AgentInfinityWebState.Paused -> AgentInfinityPausedTheme
    AgentInfinityWebState.Error -> AgentInfinityErrorTheme
}
