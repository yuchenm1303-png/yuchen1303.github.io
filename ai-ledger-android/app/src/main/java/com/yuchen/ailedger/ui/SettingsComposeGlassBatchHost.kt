package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 设置页生产态 Compose 玻璃单宿主。
 *
 * 批处理宿主从设置页第一次进入 Composition 起就保持同一棵稳定结构；几何数据由
 * onPlaced/onGloballyPositioned 自然补齐，禁止再通过延时切换整套 Composition 分支。
 * 这样不会在进入设置页约 820ms 后卸载并重新挂载 Slider、折叠区和本地交互状态。
 *
 * 折叠动画裁剪表始终存在，只记录真实 LayoutCoordinates，不创建逐帧扫描；
 * 同时不接入任何 OpenGL registry 或 geometry sync。
 */
@Composable
internal fun SettingsComposeGlassBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val frostLayerState = rememberSettingsFrostParentLayerState()
    val foldoutClipRegistry = remember { GlassFoldoutClipRegistry() }

    DisposableEffect(foldoutClipRegistry) {
        onDispose { foldoutClipRegistry.clear() }
    }

    CompositionLocalProvider(
        LocalGlassFoldoutClipRegistry provides foldoutClipRegistry,
        LocalSettingsStaticBatchReady provides true,
        LocalSettingsFrostParentLayer provides frostLayerState
    ) {
        Box(modifier = modifier) {
            SettingsFrostParentLayer(
                layerState = frostLayerState,
                modifier = Modifier.matchParentSize()
            )
            content()
        }
    }
}
