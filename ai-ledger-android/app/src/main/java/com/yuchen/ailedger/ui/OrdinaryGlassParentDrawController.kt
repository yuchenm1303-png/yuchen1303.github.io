package com.yuchen.ailedger.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 普通 Compose 玻璃父级绘制的全 App 验证开关。
 *
 * 仅覆盖已建立普通 Compose 场景边界的 Page / ScrollSubScene / Persistent；
 * Unassigned / Fallback 始终保持 Shadow。Shell、OpenGL、聊天气泡、Frost、Inset
 * 仍由各自入口硬排除，不会因为全局开关进入普通 Compose 父级系统。
 *
 * 默认关闭，App 重启后恢复关闭，便于随时回退到原子级绘制。
 */
@Stable
object OrdinaryGlassParentDrawController {
    var globalEnabled by mutableStateOf(false)

    fun renderModeFor(group: GlassSceneGroup): OrdinaryGlassRenderMode =
        if (globalEnabled && group.owner != GlassSceneOwner.Fallback) {
            OrdinaryGlassRenderMode.ParentDraw
        } else {
            OrdinaryGlassRenderMode.Shadow
        }
}
