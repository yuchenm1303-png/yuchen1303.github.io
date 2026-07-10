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
    private var frame = SecondaryMotionFrame(
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
        if (target == current) return frame

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
        frame = SecondaryMotionFrame(
            value = target,
            direction = resolvedDirection,
            type = type,
            sequence = sequence,
            animate = true,
        )
        return frame
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
 * Capsule 沿中心轴轻抬升；Push/Pop 只保留水平方向；Replace 与 Modal 保持居中纵向。
 * 旧页立即退出，避免半透明玻璃双层叠绘。动画结束后移除整页 graphicsLayer。
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
        history.resolve(targetState, direction, motionType)
    }

    AnimatedContent(
        targetState = frame,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (motion <= 0.05f) {
                fadeIn(tween(60)) togetherWith fadeOut(tween(1))
            } else {
                fadeIn(tween(1)) togetherWith fadeOut(tween(1))
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
            SecondaryMotionType.Push -> 24.dp.toPx()
            else -> 0.dp.toPx()
        }
    }
    val verticalTravelPx = with(density) {
        when (motionType) {
            SecondaryMotionType.Capsule -> 6.dp.toPx()
            SecondaryMotionType.Replace -> 4.dp.toPx()
            SecondaryMotionType.Modal -> 14.dp.toPx()
            SecondaryMotionType.Push -> 0.dp.toPx()
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
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow,
                )
                SecondaryMotionType.Push -> spring(
                    dampingRatio = 0.84f,
                    stiffness = Spring.StiffnessMedium,
                )
                SecondaryMotionType.Replace -> spring(
                    dampingRatio = 0.90f,
                    stiffness = Spring.StiffnessMedium,
                )
                SecondaryMotionType.Modal -> spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow,
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
                horizontalTravelPx = horizontalTravelPx * motion,
                verticalTravelPx = verticalTravelPx * motion,
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
