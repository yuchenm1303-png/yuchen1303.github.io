package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 仅保存玻璃实验室中“普通 Compose 父级绘制”开关的用户状态。
 * 折叠动画绝不修改全局绘制开关，避免展开首帧重建整个 registry。
 */
internal object GlassFoldoutParentDrawGate {
    var displayedEnabled by mutableStateOf(OrdinaryGlassParentDrawController.globalEnabled)
        private set

    fun setUserEnabled(enabled: Boolean) {
        displayedEnabled = enabled
        OrdinaryGlassParentDrawController.globalEnabled = enabled
    }
}

/**
 * 保留原有 fade + expand/shrink 动画，并把当前真实可见矩形注册到页面级裁剪表。
 *
 * 外层 Box 是独立 Layout 节点：其高度随 AnimatedVisibility 每帧变化，同时也是内部
 * 玻璃 LayoutCoordinates 的真实祖先。页面级普通玻璃、Frost、Inset 与 OpenGL 因此
 * 能通过同一祖先链取得裁剪区域；首帧坐标未建立或高度为零时统一保持不可见。
 */
@Composable
internal fun GlassFoldoutAnimatedContent(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val visibleState = remember { MutableTransitionState(expanded) }
    visibleState.targetState = expanded

    val clipRegistry = LocalGlassFoldoutClipRegistry.current
    val clipKey = remember { Any() }

    DisposableEffect(clipRegistry, clipKey) {
        clipRegistry?.register(clipKey)
        onDispose { clipRegistry?.unregister(clipKey) }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                clipRegistry?.update(clipKey, coordinates)
            }
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                content = content
            )
        }
    }
}
