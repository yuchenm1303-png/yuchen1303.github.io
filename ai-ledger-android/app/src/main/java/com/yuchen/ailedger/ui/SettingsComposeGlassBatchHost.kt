package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned

private val ToolsUnifiedShellShortEdgeRange = 0f..10_000f

/**
 * 每个 Tab 独立持有的玻璃父级宿主。
 *
 * 普通雾面卡、设置页自适应雾面卡、凹槽 Slider 与动态进度轨仍在 Compose 父层绘制。
 * 功能 Tab 的 Shell 玻璃在同一页面级 OpenGL 宿主中合批，卡片本身仍保持独立光学参数。
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
    val batchToolsShells =
        !includeAdaptiveSettingsFrost && LocalGlassSceneGroup == GlassSceneGroup.ToolsHomePage

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
        Box(
            modifier = modifier.onGloballyPositioned(pageFrostLayerState::updateRoot)
        ) {
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
                    if (batchToolsShells) {
                        OpenGlShellBatchHost(
                            modifier = Modifier.matchParentSize(),
                            acceptedShortEdgeDp = ToolsUnifiedShellShortEdgeRange,
                            preserveStandaloneFrame = true,
                        ) {
                            content()
                        }
                    } else {
                        content()
                    }
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
