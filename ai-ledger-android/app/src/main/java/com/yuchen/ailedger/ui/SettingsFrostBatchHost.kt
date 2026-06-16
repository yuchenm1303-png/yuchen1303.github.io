package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val SETTINGS_STATIC_BATCH_REVEAL_MS = 820L

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

internal val LocalSettingsStaticBatchReady = staticCompositionLocalOf { true }

@Composable
internal fun SettingsFrostBatchHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val layerState = rememberSettingsFrostParentLayerState()
    val motionClock = remember { SettingsFrostMotionClock() }
    val pageVisible = LocalPageVisible.current
    val pageActive = LocalPageActive.current
    val activationTick = LocalPageActivationTick.current
    var staticBatchReady by remember { mutableStateOf(false) }

    LaunchedEffect(pageVisible, motionClock) {
        if (!pageVisible) return@LaunchedEffect
        while (true) {
            withFrameNanos(motionClock::update)
        }
    }

    LaunchedEffect(pageActive, activationTick) {
        staticBatchReady = false
        if (!pageActive) return@LaunchedEffect
        delay(SETTINGS_STATIC_BATCH_REVEAL_MS)
        staticBatchReady = true
    }

    CompositionLocalProvider(
        LocalSettingsFrostMotionClock provides motionClock,
        LocalSettingsStaticBatchReady provides staticBatchReady,
        LocalSettingsFrostParentLayer provides layerState.takeIf { staticBatchReady }
    ) {
        Box(modifier = modifier) {
            if (staticBatchReady) {
                SettingsFrostParentLayer(
                    layerState = layerState,
                    modifier = Modifier.matchParentSize()
                )
            }
            content()
        }
    }
}
