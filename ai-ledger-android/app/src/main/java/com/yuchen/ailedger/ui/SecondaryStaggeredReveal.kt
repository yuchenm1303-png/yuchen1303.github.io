package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.delay

internal enum class SecondaryStageRole {
    Header,
    Capsule,
    Primary,
    Supporting,
    List,
}

private data class SecondaryStageSpec(
    val delayMillis: Long,
    val offsetY: Float,
    val initialScale: Float,
    val dampingRatio: Float,
    val stiffness: Float,
    val transformOrigin: TransformOrigin,
)

/**
 * 二级页面内部的中心对称分阶段入场。
 *
 * 顶部内容从正上方轻落，胶囊与卡片沿中心轴从正下方抬升；不再包含任何横向偏移，避免
 * 与整页转场叠加后形成右下角斜飞。动画直接读取弹簧原始进度，让落位后的极轻反向位移
 * 和统一缩放自然产生灵动感。静止后移除 graphicsLayer，不保留额外合成层。
 */
@Composable
internal fun SecondaryStageReveal(
    role: SecondaryStageRole,
    index: Int = 0,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    transitionKey: Any? = Unit,
    @Suppress("UNUSED_PARAMETER") direction: SecondaryMotionDirection = SecondaryMotionDirection.Forward,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val spec = remember(role, index, density.density) {
        SecondaryStageSpec(
            delayMillis = when (role) {
                SecondaryStageRole.Header -> min(index, 2) * 12L
                SecondaryStageRole.Capsule -> 30L + min(index, 3) * 18L
                SecondaryStageRole.Primary -> 62L + min(index, 3) * 22L
                SecondaryStageRole.Supporting -> 92L + min(index, 4) * 24L
                SecondaryStageRole.List -> 112L + min(index, 2) * 20L
            },
            offsetY = with(density) {
                when (role) {
                    SecondaryStageRole.Header -> (-9).dp.toPx()
                    SecondaryStageRole.Capsule -> 14.dp.toPx()
                    SecondaryStageRole.Primary -> 12.dp.toPx()
                    SecondaryStageRole.Supporting -> 9.dp.toPx()
                    SecondaryStageRole.List -> 7.dp.toPx()
                }
            },
            initialScale = when (role) {
                SecondaryStageRole.Header -> 1f
                SecondaryStageRole.Capsule -> 0.972f
                SecondaryStageRole.Primary -> 0.981f
                SecondaryStageRole.Supporting -> 0.988f
                SecondaryStageRole.List -> 0.994f
            },
            dampingRatio = when (role) {
                SecondaryStageRole.Header -> 0.86f
                SecondaryStageRole.Capsule -> 0.74f
                SecondaryStageRole.Primary -> 0.78f
                SecondaryStageRole.Supporting -> 0.84f
                SecondaryStageRole.List -> 0.90f
            },
            stiffness = when (role) {
                SecondaryStageRole.Capsule,
                SecondaryStageRole.Primary -> Spring.StiffnessMediumLow

                else -> Spring.StiffnessMedium
            },
            transformOrigin = TransformOrigin(0.50f, 0.50f),
        )
    }
    val shouldAnimate = animate && motion > 0.05f
    val progress = remember(transitionKey, role, index) {
        Animatable(if (shouldAnimate) 0f else 1f)
    }
    var settled by remember(transitionKey, role, index) {
        mutableStateOf(!shouldAnimate)
    }

    LaunchedEffect(transitionKey, role, index, shouldAnimate, motion) {
        if (!shouldAnimate) {
            progress.snapTo(1f)
            settled = true
            return@LaunchedEffect
        }
        settled = false
        progress.snapTo(0f)
        delay(spec.delayMillis)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = spec.dampingRatio,
                stiffness = spec.stiffness,
            ),
        )
        settled = true
    }

    val entranceModifier = if (settled) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val raw = progress.value.coerceIn(0f, 1.10f)
            val clamped = raw.coerceIn(0f, 1f)
            val initialScale = 1f - (1f - spec.initialScale) * motion

            alpha = secondaryMotionSmoothStep((clamped * 1.46f).coerceIn(0f, 1f))
            translationX = 0f
            translationY = spec.offsetY * motion * (1f - raw)
            scaleX = initialScale + (1f - initialScale) * raw
            scaleY = initialScale + (1f - initialScale) * raw
            transformOrigin = spec.transformOrigin
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    }

    Box(modifier = modifier.then(entranceModifier)) {
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
