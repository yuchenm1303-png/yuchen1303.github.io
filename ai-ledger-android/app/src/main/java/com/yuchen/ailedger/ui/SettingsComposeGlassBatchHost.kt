package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

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
    // 在子树创建 TextureView 前开启计数，保证 Host、纹理、EGL 与首帧事件都被采集。
    val diagnosticsSession = remember { beginSettingsOpenGLDiagnostics() }
    val frostLayerState = rememberSettingsFrostParentLayerState()
    val foldoutClipRegistry = remember { GlassFoldoutClipRegistry() }

    DisposableEffect(foldoutClipRegistry, diagnosticsSession) {
        onDispose {
            foldoutClipRegistry.clear()
            endSettingsOpenGLDiagnostics(diagnosticsSession)
        }
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
            SettingsOpenGLDiagnosticsPanel(
                session = diagnosticsSession,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, bottom = 92.dp)
                    .zIndex(1000f),
            )
        }
    }
}
