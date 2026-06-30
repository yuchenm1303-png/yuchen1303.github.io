package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 每个 Tab 独立持有的非 OpenGL 玻璃父级宿主。
 *
 * 普通雾面卡、设置页自适应雾面卡、凹槽 Slider 与动态进度轨都在各自页面父层绘制。
 * 本宿主不调用 OpenGL，不注册到 OpenGL registry，也不触发 geometry sync。
 */
@Composable
internal fun NonOpenGLGlassBatchHost(
    modifier: Modifier = Modifier,
    includeAdaptiveSettingsFrost: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val pageFrostLayerState = rememberPageFrostParentLayerState()
    val rememberedSettingsFrostLayerState = rememberSettingsFrostParentLayerState()
    val settingsFrostLayerState = rememberedSettingsFrostLayerState
        .takeIf { includeAdaptiveSettingsFrost }
    val foldoutClipRegistry = remember { GlassFoldoutClipRegistry() }

    DisposableEffect(pageFrostLayerState, foldoutClipRegistry) {
        onDispose {
            pageFrostLayerState.clear()
            foldoutClipRegistry.clear()
        }
    }

    CompositionLocalProvider(
        LocalGlassFoldoutClipRegistry provides foldoutClipRegistry,
        LocalSettingsStaticBatchReady provides true,
        LocalPageFrostParentLayer provides pageFrostLayerState,
        LocalSettingsFrostParentLayer provides settingsFrostLayerState,
    ) {
        Box(modifier = modifier) {
            PageFrostParentLayer(
                layerState = pageFrostLayerState,
                modifier = Modifier.matchParentSize(),
            )
            if (settingsFrostLayerState != null) {
                SettingsFrostParentLayer(
                    layerState = settingsFrostLayerState,
                    modifier = Modifier.matchParentSize(),
                )
            }
            InsetGlassSliderBatchGroup(Modifier.matchParentSize()) {
                InsetGlassSliderProgressBatchGroup(Modifier.matchParentSize()) {
                    content()
                }
            }
        }
    }
}

/** 保留旧入口，设置页仍启用原有的高模糊自适应材质。 */
@Composable
internal fun SettingsComposeGlassBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    NonOpenGLGlassBatchHost(
        modifier = modifier,
        includeAdaptiveSettingsFrost = true,
        content = content,
    )
}
