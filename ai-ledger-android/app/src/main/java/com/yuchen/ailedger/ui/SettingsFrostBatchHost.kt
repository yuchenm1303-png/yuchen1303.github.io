package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier

@Stable
internal class SettingsFrostMotionClock {
    private var originNanos = 0L

    var frameNanos by mutableLongStateOf(0L)
        private set

    fun update(frameTimeNanos: Long) {
        if (originNanos == 0L) originNanos = frameTimeNanos
        frameNanos = (frameTimeNanos - originNanos).coerceAtLeast(0L)
    }
}

internal val LocalSettingsFrostMotionClock =
    staticCompositionLocalOf<SettingsFrostMotionClock?> { null }

/**
 * 设置页 Frost 专用单 Host：
 * 1. 静止卡片共享一个父级背景 Canvas；
 * 2. 所有卡片共享一个页面级动画时钟；
 * 3. 与普通玻璃 registry、OpenGL registry 和 geometry sync 完全隔离。
 */
@Composable
internal fun SettingsFrostBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val layerState = rememberSettingsFrostParentLayerState()
    val motionClock = remember { SettingsFrostMotionClock() }
    val pageVisible = LocalPageVisible.current

    LaunchedEffect(pageVisible, motionClock) {
        if (!pageVisible) return@LaunchedEffect
        while (true) {
            withFrameNanos(motionClock::update)
        }
    }

    CompositionLocalProvider(LocalSettingsFrostMotionClock provides motionClock) {
        SettingsFrostParentScope(layerState) {
            Box(modifier = modifier) {
                SettingsFrostParentLayer(
                    layerState = layerState,
                    modifier = Modifier.matchParentSize()
                )
                content()
            }
        }
    }
}
