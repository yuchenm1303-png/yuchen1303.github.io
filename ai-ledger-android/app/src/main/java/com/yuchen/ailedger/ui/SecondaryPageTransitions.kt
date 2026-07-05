package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import kotlinx.coroutines.yield

/**
 * 二级页面统一轻量转场。
 *
 * 只驱动 alpha / translation / scale，不改变真实布局尺寸，不接入 OpenGL，
 * 用于功能页、设置页内部二级页面和列表详情页的硬切替换。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T> SecondaryPageTransition(
    targetState: T,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (motion <= 0.05f) {
                fadeIn(tween(durationMillis = 70)) togetherWith fadeOut(tween(durationMillis = 54))
            } else {
                val enterOffsetRatio = 0.045f + 0.030f * motion
                val exitOffsetRatio = 0.018f + 0.010f * motion
                val enter = fadeIn(tween(durationMillis = 126, delayMillis = 18)) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { height ->
                        (height * enterOffsetRatio).roundToInt().coerceAtLeast(18)
                    } +
                    scaleIn(
                        initialScale = 0.965f,
                        transformOrigin = TransformOrigin(0.50f, 0.58f),
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                val exit = fadeOut(tween(durationMillis = 96)) +
                    slideOutVertically(tween(durationMillis = 118)) { height ->
                        -(height * exitOffsetRatio).roundToInt().coerceAtMost(16)
                    } +
                    scaleOut(
                        targetScale = 0.982f,
                        transformOrigin = TransformOrigin(0.50f, 0.54f),
                        animationSpec = tween(durationMillis = 118),
                    )
                enter togetherWith exit
            }
        },
        label = "secondary-page-transition",
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false },
        ) {
            content(page)
        }
    }
}

@Composable
internal fun SecondaryRouteEntrance(
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    var visible by remember { mutableStateOf(motion <= 0.05f) }
    LaunchedEffect(Unit) {
        if (!visible) {
            yield()
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion <= 0.05f) {
            fadeIn(tween(durationMillis = 70))
        } else {
            fadeIn(tween(durationMillis = 132, delayMillis = 18)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { height -> height.coerceAtMost(44) } +
                scaleIn(
                    initialScale = 0.968f,
                    transformOrigin = TransformOrigin(0.50f, 0.58f),
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
        },
        exit = fadeOut(tween(durationMillis = 96)) +
            slideOutVertically(tween(durationMillis = 118)) { height -> -height.coerceAtMost(18) } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(durationMillis = 118)),
    ) {
        content()
    }
}

internal fun secondaryPanelScale(progress: Float): Float = 0.982f + 0.018f * progress
