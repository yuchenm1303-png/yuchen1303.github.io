package com.yuchen.ailedger.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 普通 Compose 玻璃父级绘制验证开关。
 *
 * 当前正式架构固定为单卡绘制：PressableGlass 自己持有 material / lens / sweep /
 * afterglow 动画，并在同一个组件绘制链内完成本体形变、静态材质和光效覆盖。
 *
 * ParentDraw 会把本体、内容 graphicsLayer 和光效层拆开，按压时容易出现光效和
 * 玻璃本体脱离；因此这里保留开关状态供面板显示，但 renderMode 不再让普通玻璃
 * 进入 ParentDraw 接管。
 */
@Stable
object OrdinaryGlassParentDrawController {
    var globalEnabled by mutableStateOf(false)

    fun renderModeFor(group: GlassSceneGroup): OrdinaryGlassRenderMode = OrdinaryGlassRenderMode.Shadow
}
