package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private data class SecondaryMotionFrame<T>(
    val value: T,
    val direction: SecondaryMotionDirection,
    val type: SecondaryMotionType,
    val sequence: Int,
    val animate: Boolean,
)

private class SecondaryRouteHistory<T>(
    initial: T,
    initialDirection: SecondaryMotionDirection,
    initialType: SecondaryMotionType,
    animateInitial: Boolean,
) {
    private val stack = mutableListOf(initial)
    private var current = initial
    private var sequence = 0
    private var currentFrame = SecondaryMotionFrame(
        value = initial,
        direction = initialDirection,
        type = initialType,
        sequence = sequence,
        animate = animateInitial,
    )

    fun resolve(
        target: T,
        explicitDirection: SecondaryMotionDirection?,
        type: SecondaryMotionType,
    ): SecondaryMotionFrame<T> {
        if (target == current) return currentFrame

        val knownIndex = stack.indexOfLast { it == target }
        val resolvedDirection = explicitDirection ?: when {
            type == SecondaryMotionType.Replace -> SecondaryMotionDirection.Forward
            knownIndex >= 0 -> SecondaryMotionDirection.Backward
            else -> SecondaryMotionDirection.Forward
        }

        when {
            type == SecondaryMotionType.Replace -> {
                if (stack.isEmpty()) stack.add(target) else stack[stack.lastIndex] = target
            }

            resolvedDirection == SecondaryMotionDirection.Backward && knownIndex >= 0 -> {
                while (stack.lastIndex > knownIndex) stack.removeAt(stack.lastIndex)
            }

            resolvedDirection == SecondaryMotionDirection.Backward -> {
                stack.clear()
                stack.add(target)
            }

            else -> stack.add(target)
        }

        current = target
        sequence += 1
        currentFrame = SecondaryMotionFrame(
            value = target,
            direction = resolvedDirection,
            type = type,
            sequence = sequence,
            animate = true,
        )
        return currentFrame
    }
}

private class SecondaryKeySequence(initial: Any?) {
    private var current = initial
    private var sequence = 0

    fun resolve(target: Any?): Int {
        if (target == current) return sequence
        current = target
        sequence += 1
        return sequence
    }
}

/**
 * 二级页面统一轻量转场。
 *
 * 旧页面立即退出，避免半透明玻璃双层叠绘。新页面只做方向位移和透明度，不缩放整棵
 * 玻璃树，不绘制转场光效，也不触发 OpenGL、geometry registry 或 geometry sync。
 * 动画结束后主动移除整页 graphicsLayer，静止状态不保留额外合成层。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T> SecondaryPageTransition(
    targetState: T,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    motionType: SecondaryMotionType = SecondaryMotionType.Capsule,
    direction: SecondaryMotionDirection? = null,
    animateInitial: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    val history = remember {
        SecondaryRouteHistory(
            initial = targetState,
            initialDirection = direction ?: SecondaryMotionDirection.Forward,
            initialType = motionType,
            animateInitial = animateInitial,
        )
    }
    val frame = remember(targetState, direction, motionType) {
        history.resolve(
            target = targetState,
            explicitDirection = direction,
            type = motionType,
        )
    }

    AnimatedContent(
        targetState = frame,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (motion <= 0.05f) {
                fadeIn(tween(durationMillis = 60)) togetherWith fadeOut(tween(durationMillis = 1))
            } else {
                fadeIn(tween(durationMillis = 1)) togetherWith fadeOut(tween(durationMillis = 1))
            }
        },
        label = "secondary-page-transition",
    ) { resolved ->
        SecondaryMotionLayer(
            sequence = resolved.sequence,
            animate = resolved.animate,
            motionIntensity = motion,
            motionType = resolved.type,
            direction = resolved.direction,
            modifier = Modifier.fillMaxSize(),
        ) {
            content(resolved.value)
        }
    }
}

/**
 * 独立二级路由首次进入。只播放新页面，不保留来源页面的玻璃绘制。
 */
@Composable
internal fun SecondaryRouteEntrance(
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    motionType: SecondaryMotionType = SecondaryMotionType.Capsule,
    direction: SecondaryMotionDirection = SecondaryMotionDirection.Forward,
    content: @Composable () -> Unit,
) {
    SecondaryMotionLayer(
        sequence = 0,
        animate = true,
        motionIntensity = motionIntensity,
        motionType = motionType,
        direction = direction,
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/**
 * 已由外部持有页面实例的场景使用，例如预热后的计划编辑器。
 * key 改变时旧内容由调用方立即替换，本容器只负责新内容的轻量入场。
 */
@Composable
internal fun SecondaryMotionContainer(
    transitionKey: Any?,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    motionType: SecondaryMotionType = SecondaryMotionType.Capsule,
    direction: SecondaryMotionDirection = SecondaryMotionDirection.Forward,
    animateInitial: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tracker = remember { SecondaryKeySequence(transitionKey) }
    val sequence = remember(transitionKey) { tracker.resolve(transitionKey) }

    SecondaryMotionLayer(
        sequence = sequence,
        animate = animateInitial || sequence > 0,
        motionIntensity = motionIntensity,
        motionType = motionType,
        direction = direction,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun SecondaryMotionLayer(
    sequence: Int,
    animate: Boolean,
    motionIntensity: Float,
    motionType: SecondaryMotionType,
    direction: SecondaryMotionDirection,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    val shouldAnimate = animate && motion > 0.05f
    val progress = remember(sequence) { Animatable(if (shouldAnimate) 0f else 1f) }
    var settled by remember(sequence) { mutableStateOf(!shouldAnimate) }
    val density = LocalDensity.current
    val horizontalTravelPx = with(density) {
        when (motionType) {
            SecondaryMotionType.Capsule -> 24.dp.toPx()
            SecondaryMotionType.Push -> 20.dp.toPx()
            SecondaryMotionType.Replace -> 10.dp.toPx()
            SecondaryMotionType.Modal -> 0.dp.toPx()
        }
    }
    val verticalTravelPx = with(density) {
        when (motionType) {
            SecondaryMotionType.Capsule -> 9.dp.toPx()
            SecondaryMotionType.Push -> 4.dp.toPx()
            SecondaryMotionType.Replace -> 1.dp.toPx()
            SecondaryMotionType.Modal -> 14.dp.toPx()
        }
    }

    LaunchedEffect(progress, shouldAnimate, motionType, motion) {
        if (!shouldAnimate) {
            progress.snapTo(1f)
            settled = true
            return@LaunchedEffect
        }

        settled = false
        withFrameNanos { }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = when (motionType) {
                SecondaryMotionType.Capsule -> spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMedium,
                )

                SecondaryMotionType.Push -> spring(
                    dampingRatio = 0.90f,
                    stiffness = Spring.StiffnessMedium,
                )

                SecondaryMotionType.Replace -> spring(
                    dampingRatio = 0.94f,
                    stiffness = Spring.StiffnessMedium,
                )

                SecondaryMotionType.Modal -> spring(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMedium,
                )
            },
        )
        settled = true
    }

    val motionModifier = if (settled) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val visual = secondaryMotionVisual(
                rawProgress = progress.value,
                type = motionType,
                direction = direction,
                horizontalTravelPx = horizontalTravelPx,
                verticalTravelPx = verticalTravelPx,
            )
            alpha = visual.alpha
            translationX = visual.translationX
            translationY = visual.translationY
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    }

    Box(
        modifier = modifier
            .clipSecondaryPageVertically()
            .then(motionModifier),
    ) {
        content()
    }
}
