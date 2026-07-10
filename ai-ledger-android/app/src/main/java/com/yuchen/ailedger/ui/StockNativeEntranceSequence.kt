package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

private class StockNativeEntranceSequenceState(
    val direction: SecondaryMotionDirection,
) {
    private var nextMajorPanelIndex: Int = 0

    fun claimMajorPanel(): Int {
        val claimed = nextMajorPanelIndex
        nextMajorPanelIndex += 1
        return claimed
    }
}

private val LocalStockNativeEntranceSequence =
    compositionLocalOf<StockNativeEntranceSequenceState?> { null }

/**
 * 每个股票路由拥有独立的首屏入场序列。序列只覆盖大玻璃和页面头部，内部指标小卡不
 * 逐个动画，避免一张市场大卡里的几十个玻璃同时建立动画层。
 */
@Composable
internal fun StockNativeEntranceSequenceHost(
    routeKey: Any?,
    direction: SecondaryMotionDirection = SecondaryMotionDirection.Forward,
    content: @Composable () -> Unit,
) {
    val sequence = remember(routeKey, direction) {
        StockNativeEntranceSequenceState(direction = direction)
    }
    CompositionLocalProvider(LocalStockNativeEntranceSequence provides sequence) {
        content()
    }
}

@Composable
internal fun StockNativeMajorPanelEntrance(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = LocalStockNativeGlassState.current
    val sequence = LocalStockNativeEntranceSequence.current
    val index = remember(sequence) { sequence?.claimMajorPanel() ?: -1 }

    if (state == null || sequence == null || index < 0 || index >= 3) {
        Box(modifier = modifier, content = { content() })
        return
    }

    val role = when (index) {
        0 -> SecondaryStageRole.Capsule
        1 -> SecondaryStageRole.Primary
        else -> SecondaryStageRole.Supporting
    }
    SecondaryStageReveal(
        role = role,
        index = (index - 1).coerceAtLeast(0),
        motionIntensity = state.motionIntensity,
        modifier = modifier,
        direction = sequence.direction,
        content = content,
    )
}

@Composable
internal fun StockNativeHeaderEntrance(
    content: @Composable () -> Unit,
) {
    val state = LocalStockNativeGlassState.current
    val sequence = LocalStockNativeEntranceSequence.current
    if (state == null || sequence == null) {
        content()
        return
    }
    SecondaryStageReveal(
        role = SecondaryStageRole.Header,
        motionIntensity = state.motionIntensity,
        modifier = Modifier.fillMaxWidth(),
        direction = sequence.direction,
        content = content,
    )
}
