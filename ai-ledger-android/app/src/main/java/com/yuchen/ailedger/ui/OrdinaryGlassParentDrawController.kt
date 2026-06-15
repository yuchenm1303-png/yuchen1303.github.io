package com.yuchen.ailedger.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 普通 Compose 玻璃父级绘制的受控验证开关。
 *
 * 当前只允许 SettingsDebugInnerScroll 进入 ParentDraw；其他场景固定 Shadow。
 * 默认关闭，App 重启后恢复关闭，避免验证状态意外固化到生产路径。
 */
@Stable
object OrdinaryGlassParentDrawController {
    var settingsDebugEnabled by mutableStateOf(false)

    fun renderModeFor(group: GlassSceneGroup): OrdinaryGlassRenderMode =
        if (group == GlassSceneGroup.SettingsDebugInnerScroll && settingsDebugEnabled) {
            OrdinaryGlassRenderMode.ParentDraw
        } else {
            OrdinaryGlassRenderMode.Shadow
        }
}
