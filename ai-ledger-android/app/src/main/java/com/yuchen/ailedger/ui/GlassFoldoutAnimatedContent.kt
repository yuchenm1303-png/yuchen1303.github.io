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
import androidx.compose.runtime.DisposableEffect
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
 * 折叠动画期间，外部父级绘制不能继续使用正在变化的旧几何。
 * 该标记让凹槽等组件暂时回退到本地绘制，动画结束后再恢复批绘制。
 */
internal val LocalGlassFoldoutAnimationRunning =
    staticCompositionLocalOf { false }

/**
 * 普通 Compose 玻璃动画保护门。
 * 只临时改变实际绘制模式，displayedEnabled 始终保存用户选择，
 * 因此调试开关不会在折叠动画期间视觉跳变。
 */
internal object GlassFoldoutParentDrawGate {
    private var holderCount = 0
    private var restoreEnabled = false

    var displayedEnabled by mutableStateOf(OrdinaryGlassParentDrawController.globalEnabled)
        private set

    @Synchronized
    fun setUserEnabled(enabled: Boolean) {
        displayedEnabled = enabled
        restoreEnabled = enabled
        if (holderCount == 0) {
            OrdinaryGlassParentDrawController.globalEnabled = enabled
        }
    }

    @Synchronized
    fun acquire() {
        if (holderCount == 0) {
            restoreEnabled = OrdinaryGlassParentDrawController.globalEnabled
            displayedEnabled = restoreEnabled
            if (restoreEnabled) {
                OrdinaryGlassParentDrawController.globalEnabled = false
            }
        }
        holderCount += 1
    }

    @Synchronized
    fun release() {
        if (holderCount <= 0) return
        holderCount -= 1
        if (holderCount == 0) {
            OrdinaryGlassParentDrawController.globalEnabled = restoreEnabled
            displayedEnabled = restoreEnabled
        }
    }
}

/**
 * 保留原有 fade + expand/shrink 动画，同时建立严格裁剪边界。
 * 动画期间普通玻璃和凹槽使用本地绘制，避免父级 registry 的旧坐标
 * 在首帧或退出帧穿过折叠标题；动画稳定后自动恢复批绘制。
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
            // 动画结束后保留两帧本地绘制，让父级 registry 收到最终几何。
            withFrameNanos { }
            withFrameNanos { }
            geometrySettling = false
        }
    }

    val guardedDrawing = animationRunning || geometrySettling
    DisposableEffect(guardedDrawing) {
        if (guardedDrawing) GlassFoldoutParentDrawGate.acquire()
        onDispose {
            if (guardedDrawing) GlassFoldoutParentDrawGate.release()
        }
    }

    CompositionLocalProvider(
        LocalGlassFoldoutAnimationRunning provides guardedDrawing
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
