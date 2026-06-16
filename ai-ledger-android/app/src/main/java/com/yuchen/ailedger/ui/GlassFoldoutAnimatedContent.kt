package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/**
 * 折叠动画期间，凹槽批绘制暂时回退到本地绘制，
 * 避免正在变化的父级几何被旧槽位坐标继续使用。
 */
internal val LocalGlassFoldoutAnimationRunning =
    staticCompositionLocalOf { false }

/**
 * 仅保存玻璃实验室中“普通 Compose 父级绘制”开关的用户状态。
 * 折叠动画绝不再修改全局绘制开关，避免展开首帧重建整个 registry。
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
 * 保留原有 fade + expand/shrink 动画，并建立严格的 Compose 裁剪边界。
 * 动画期间只让凹槽批绘制局部回退，不触碰全局普通玻璃绘制模式。
 */
@Composable
internal fun GlassFoldoutAnimatedContent(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val visibleState = remember { MutableTransitionState(expanded) }
    visibleState.targetState = expanded
    val animationRunning = visibleState.currentState != visibleState.targetState
    var geometrySettling by remember { mutableStateOf(false) }

    LaunchedEffect(animationRunning) {
        if (animationRunning) {
            geometrySettling = true
        } else if (geometrySettling) {
            // 动画完成后再等待两帧，让最终测量和槽位坐标稳定。
            withFrameNanos { }
            withFrameNanos { }
            geometrySettling = false
        }
    }

    val localDrawingGuard = animationRunning || geometrySettling

    CompositionLocalProvider(
        LocalGlassFoldoutAnimationRunning provides localDrawingGuard
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = modifier.clipToBounds(),
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
