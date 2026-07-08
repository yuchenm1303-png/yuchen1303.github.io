package com.yuchen.ailedger.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 普通 Compose 玻璃父级绘制的全 App 验证开关。
 *
 * 这个开关只能用于验证父级批量绘制，不作为默认动效架构：
 * ParentDraw 会把玻璃本体、内容 graphicsLayer 与光效层拆到不同绘制链，
 * 容易造成按压时光效和玻璃本体脱离。
 *
 * 普通 Compose 按压动效的默认路径必须保持子级单卡绘制：
 * PressableGlass 自己持有 material / lens / sweep / afterglow 动画，
 * 同一个组件内完成玻璃本体形变、静态材质和光效覆盖。
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
