package com.yuchen.ailedger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 股票页面专用的可交互 Compose 玻璃。
 *
 * 只有明确传入点击行为的入口才使用本组件；纯行情、状态和统计卡继续使用
 * StockNativeFrostCard 的静态普通玻璃。该组件复用搜索按钮的 PressableGlass
 * 光效与形变链，不调用 OpenGLGlassCardLayer，不进入 OpenGL registry，也不触发
 * OpenGL geometry sync。
 */
@Composable
internal fun StockNativeActionGlass(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    frostAlpha: Float = 0.075f,
    contentPadding: Dp = 0.dp,
    intensityScale: Float = 1.04f,
    role: GlassRole = GlassRole.Floating,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = LocalStockNativeGlassState.current
    if (state != null) {
        val resolvedIntensity = state.glassIntensity * intensityScale
        PressableGlass(
            quality = state.quality,
            glassIntensity = resolvedIntensity,
            motionIntensity = state.motionIntensity,
            radius = radius.value.roundToInt().coerceAtLeast(1),
            modifier = modifier,
            role = role,
            onClick = onClick,
            intensity = resolvedIntensity
        ) {
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                content()
            }
        }
    } else {
        StockNativeFrostCard(
            modifier = modifier.clickable(onClick = onClick),
            radius = radius,
            frostAlpha = frostAlpha,
            contentPadding = contentPadding,
            content = content
        )
    }
}
