package com.yuchen.ailedger.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 普通 Compose 玻璃父级绘制的全 App 开关。
 *
 * ParentDraw 是当前普通 Compose 玻璃的目标架构：
 * - Card / Chip / Floating / Nav / Flex 统一上报到页面 Host；
 * - Shell / OpenGL / 聊天气泡 / Frost / Inset 仍由各自入口硬排除；
 * - Fallback 场景保留 Shadow，避免无场景边界时误接管。
 *
 * 之前默认 false 会让尺寸归一化、父级余辉和单卡光效只在局部调试开关打开时生效，
 * 造成实验室参数看起来“完全没变化”。现在默认启用目标架构。
 */
@Stable
object OrdinaryGlassParentDrawController {
    var globalEnabled by mutableStateOf(true)

    fun renderModeFor(group: GlassSceneGroup): OrdinaryGlassRenderMode =
        if (globalEnabled && group.owner != GlassSceneOwner.Fallback) {
            OrdinaryGlassRenderMode.ParentDraw
        } else {
            OrdinaryGlassRenderMode.Shadow
        }
}
