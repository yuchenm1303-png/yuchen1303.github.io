package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val SETTINGS_COMPOSE_BATCH_REVEAL_MS = 820L

/**
 * 设置页生产态 Compose 玻璃单宿主。
 * 页面进退场期间保留原本地 Frost/Inset 绘制；几何稳定后再切入父级批绘制。
 * 不创建逐帧时钟，不接入任何 OpenGL registry 或 geometry sync。
 */
@Composable
internal fun SettingsComposeGlassBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val frostLayerState = rememberSettingsFrostParentLayerState()
    val pageActive = LocalPageActive.current
    val activationTick = LocalPageActivationTick.current
    var staticBatchReady by remember { mutableStateOf(false) }

    LaunchedEffect(pageActive, activationTick) {
        staticBatchReady = false
        if (!pageActive) return@LaunchedEffect
        delay(SETTINGS_COMPOSE_BATCH_REVEAL_MS)
        staticBatchReady = true
    }

    CompositionLocalProvider(
        LocalSettingsStaticBatchReady provides staticBatchReady,
        LocalSettingsFrostParentLayer provides frostLayerState.takeIf { staticBatchReady }
    ) {
        Box(modifier = modifier) {
            if (staticBatchReady) {
                SettingsFrostParentLayer(
                    layerState = frostLayerState,
                    modifier = Modifier.matchParentSize()
                )
            }
            content()
        }
    }
}
