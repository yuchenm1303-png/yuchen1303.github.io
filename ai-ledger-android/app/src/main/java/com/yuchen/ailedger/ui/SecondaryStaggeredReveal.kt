package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    val offsetX: Float,
    val offsetY: Float,
    val initialScaleX: Float,
    val initialScaleY: Float,
    val pulseScaleX: Float,
    val pulseScaleY: Float,
    val dampingRatio: Float,
    val stiffness: Float,
    val transformOrigin: TransformOrigin,
)

/**
 * 二级页面内部的分阶段胶囊入场。
 *
 * 页面只需要标记 Header / Capsule / Primary / Supporting / List，不在业务页面里重复写
 * 延迟和弹簧参数。每个阶段独立进入，避免全部玻璃同帧启动。这里不绘制光效，也不
 * 触发 OpenGL 或 geometry sync；胶囊感只来自局部容器的横纵异步收束与一次克制回弹。
 */
@Composable
internal fun SecondaryStageReveal(
    role: SecondaryStageRole,
    index: Int = 0,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    transitionKey: Any? = Unit,
    direction: SecondaryMotionDirection = SecondaryMotionDirection.Forward,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val sign = if (direction == SecondaryMotionDirection.Forward) 1f else -1f
    val spec = remember(role, index, direction, density.density) {
        val delay = when (role) {
            SecondaryStageRole.Header -> 0L + min(index, 2) * 18L
            SecondaryStageRole.Capsule -> 48L + min(index, 3) * 26L
            SecondaryStageRole.Primary -> 92L + min(index, 3) * 30L
            SecondaryStageRole.Supporting -> 136L + min(index, 4) * 34L
            SecondaryStageRole.List -> 176L + min(index, 2) * 30L
        }
        val originX = when {
            role != SecondaryStageRole.Capsule -> 0.50f
            direction == SecondaryMotionDirection.Forward -> 0.22f
            else -> 0.78f
        }
        SecondaryStageSpec(
            delayMillis = delay,
            offsetX = with(density) {
                when (role) {
                    SecondaryStageRole.Header -> 0.dp.toPx()
                    SecondaryStageRole.Capsule -> 8.dp.toPx() * sign
                    SecondaryStageRole.Primary -> 6.dp.toPx() * sign
                    SecondaryStageRole.Supporting -> 3.dp.toPx() * sign
                    SecondaryStageRole.List -> 0.dp.toPx()
                }
            },
            offsetY = with(density) {
                when (role) {
                    SecondaryStageRole.Header -> 7.dp.toPx()
                    SecondaryStageRole.Capsule -> 12.dp.toPx()
                    SecondaryStageRole.Primary -> 10.dp.toPx()
                    SecondaryStageRole.Supporting -> 8.dp.toPx()
                    SecondaryStageRole.List -> 7.dp.toPx()
                }
            },
            initialScaleX = when (role) {
                SecondaryStageRole.Header, SecondaryStageRole.List -> 1f
                SecondaryStageRole.Capsule -> 0.986f
                SecondaryStageRole.Primary -> 0.992f
                SecondaryStageRole.Supporting -> 0.996f
            },
            initialScaleY = when (role) {
                SecondaryStageRole.Header, SecondaryStageRole.List -> 1f
                SecondaryStageRole.Capsule -> 0.968f
                SecondaryStageRole.Primary -> 0.980f
                SecondaryStageRole.Supporting -> 0.988f
            },
            pulseScaleX = when (role) {
                SecondaryStageRole.Capsule -> 0.0038f
                SecondaryStageRole.Primary -> 0.0020f
                SecondaryStageRole.Supporting -> 0.0010f
                else -> 0f
            },
            pulseScaleY = when (role) {
                SecondaryStageRole.Capsule -> -0.0028f
                SecondaryStageRole.Primary -> -0.0014f
                SecondaryStageRole.Supporting -> -0.0007f
                else -> 0f
            },
            dampingRatio = when (role) {
                SecondaryStageRole.Header -> 0.94f
                SecondaryStageRole.Capsule -> 0.82f
                SecondaryStageRole.Primary -> 0.86f
                SecondaryStageRole.Supporting -> 0.91f
                SecondaryStageRole.List -> 0.94f
            },
            stiffness = when (role) {
                SecondaryStageRole.Capsule -> Spring.StiffnessMediumLow
                SecondaryStageRole.Primary -> Spring.StiffnessMediumLow
                else -> Spring.StiffnessMedium
            },
            transformOrigin = TransformOrigin(originX, if (role == SecondaryStageRole.Capsule) 0.52f else 0.58f),
        )
    }
    val shouldAnimate = animate && motion > 0.05f
    val progress = remember(transitionKey, role, index) {
        Animatable(if (shouldAnimate) 0f else 1f)
    }

    LaunchedEffect(transitionKey, role, index, shouldAnimate, motion) {
        if (!shouldAnimate) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        delay(spec.delayMillis)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = spec.dampingRatio,
                stiffness = spec.stiffness,
            ),
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val raw = progress.value
            val clamped = raw.coerceIn(0f, 1f)
            val p = secondaryMotionSmoothStep(clamped)
            val pulse = secondaryMotionArc(p)
            val overshoot = (raw - 1f).coerceIn(0f, 0.08f)

            alpha = (clamped * if (role == SecondaryStageRole.Header) 1.92f else 1.72f)
                .coerceIn(0f, 1f)
            translationX = spec.offsetX * (1f - p) - spec.offsetX * pulse * 0.045f
            translationY = spec.offsetY * (1f - p) - spec.offsetY * pulse * 0.060f
            scaleX = spec.initialScaleX + (1f - spec.initialScaleX) * p +
                spec.pulseScaleX * pulse - overshoot * 0.012f
            scaleY = spec.initialScaleY + (1f - spec.initialScaleY) * p +
                spec.pulseScaleY * pulse + overshoot * 0.009f
            transformOrigin = spec.transformOrigin
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
