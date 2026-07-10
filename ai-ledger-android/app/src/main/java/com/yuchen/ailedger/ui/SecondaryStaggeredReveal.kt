package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * 二级页面首屏内容的分批入场。
 *
 * 每组只做透明度和纵向位移，不缩放玻璃、不绘制 glint，也不会触发普通玻璃自己的
 * 按压光效。最多错开前六组，避免长列表等待过久。
 */
@Composable
internal fun SecondaryStaggeredReveal(
    index: Int,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    val progress = remember { Animatable(if (motion <= 0.05f) 1f else 0f) }
    val density = LocalDensity.current
    val offsetPx = with(density) { 10.dp.toPx() }

    LaunchedEffect(motion) {
        if (motion <= 0.05f) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            delay(min(index, 5) * 42L)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            val raw = progress.value.coerceIn(0f, 1f)
            val p = secondaryMotionSmoothStep(raw)
            alpha = (raw * 1.72f).coerceIn(0f, 1f)
            translationY = (1f - p) * offsetPx
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) {
        content()
    }
}
