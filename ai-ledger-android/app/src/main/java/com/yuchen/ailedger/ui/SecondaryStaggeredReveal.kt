package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal enum class SecondaryStageRole {
    Header,
    Capsule,
    Primary,
    Supporting,
    List,
}

/**
 * 一张二级页面只持有一个时间轴。页面外壳和内部阶段共同读取它，避免每张玻璃各自创建
 * Animatable、协程和弹簧计算。
 */
internal class SecondaryMotionTimelineState(initialProgress: Float) {
    val progress = Animatable(initialProgress)
    var settled by mutableStateOf(initialProgress >= 1f)
}

internal val LocalSecondaryMotionTimeline =
    staticCompositionLocalOf<SecondaryMotionTimelineState?> { null }

private data class SecondaryStageSpec(
    val startFraction: Float,
    val durationFraction: Float,
    val offsetY: Float,
    val overshoot: Float,
)

/**
 * 二级页面内部的共享时间轴分阶段入场。
 *
 * 不再对玻璃卡片缩放，也不再为每块卡片启动独立动画。标题从上方轻落，核心胶囊与主卡
 * 沿中心轴抬升，并通过低幅度 Back-Out 在落位后产生极小回弹。首屏之外的 Supporting 与
 * List 自动静态显示，避免重页面在同一帧建立过多合成层。
 */
@Composable
internal fun SecondaryStageReveal(
    role: SecondaryStageRole,
    index: Int = 0,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") transitionKey: Any? = Unit,
    @Suppress("UNUSED_PARAMETER") direction: SecondaryMotionDirection = SecondaryMotionDirection.Forward,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val timeline = LocalSecondaryMotionTimeline.current
    val motion = motionIntensity.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val stageAllowed = when (role) {
        SecondaryStageRole.Header,
        SecondaryStageRole.Capsule,
        SecondaryStageRole.Primary -> true

        SecondaryStageRole.Supporting -> index < 2
        SecondaryStageRole.List -> index == 0
    }
    val canAnimate = animate && stageAllowed && motion > 0.05f

    if (!canAnimate || timeline == null || timeline.settled) {
        Box(modifier = modifier) { content() }
        return
    }
    val activeTimeline = timeline

    val spec = remember(role, index, density.density) {
        val cappedIndex = min(index, 3)
        SecondaryStageSpec(
            startFraction = when (role) {
                SecondaryStageRole.Header -> cappedIndex * 0.025f
                SecondaryStageRole.Capsule -> 0.075f + cappedIndex * 0.035f
                SecondaryStageRole.Primary -> 0.165f + cappedIndex * 0.040f
                SecondaryStageRole.Supporting -> 0.255f + cappedIndex * 0.050f
                SecondaryStageRole.List -> 0.355f + cappedIndex * 0.040f
            },
            durationFraction = when (role) {
                SecondaryStageRole.Header -> 0.46f
                SecondaryStageRole.Capsule -> 0.62f
                SecondaryStageRole.Primary -> 0.62f
                SecondaryStageRole.Supporting -> 0.60f
                SecondaryStageRole.List -> 0.54f
            },
            offsetY = with(density) {
                when (role) {
                    SecondaryStageRole.Header -> (-7).dp.toPx()
                    SecondaryStageRole.Capsule -> 13.dp.toPx()
                    SecondaryStageRole.Primary -> 10.dp.toPx()
                    SecondaryStageRole.Supporting -> 8.dp.toPx()
                    SecondaryStageRole.List -> 6.dp.toPx()
                }
            },
            overshoot = when (role) {
                SecondaryStageRole.Header -> 0.24f
                SecondaryStageRole.Capsule -> 0.72f
                SecondaryStageRole.Primary -> 0.50f
                SecondaryStageRole.Supporting -> 0.32f
                SecondaryStageRole.List -> 0.16f
            },
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val global = activeTimeline.progress.value.coerceIn(0f, 1f)
            val local = ((global - spec.startFraction) / spec.durationFraction).coerceIn(0f, 1f)
            val eased = secondaryMotionBackOut(local, spec.overshoot)
            val alphaSpeed = when (role) {
                SecondaryStageRole.Header,
                SecondaryStageRole.List -> 2.35f

                else -> 3.10f
            }

            alpha = secondaryMotionSmoothStep((local * alphaSpeed).coerceIn(0f, 1f))
            translationX = 0f
            translationY = spec.offsetY * motion * (1f - eased)
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) {
        content()
    }
}

/**
 * 兼容原有按索引调用。新页面优先使用 [SecondaryStageReveal] 明确内容层级。
 * [tone] 仅为兼容旧调用保留，不参与任何绘制。
 */
@Composable
internal fun SecondaryStaggeredReveal(
    index: Int,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") tone: Color = Color.White,
    transitionKey: Any? = Unit,
    content: @Composable () -> Unit,
) {
    SecondaryStageReveal(
        role = SecondaryStageRole.Supporting,
        index = index,
        motionIntensity = motionIntensity,
        modifier = modifier,
        transitionKey = transitionKey,
        content = content,
    )
}
