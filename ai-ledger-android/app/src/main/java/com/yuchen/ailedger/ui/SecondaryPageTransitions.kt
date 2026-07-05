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
 * 路由切换只做透明换场和小位移，不在整页上做 scale，避免应用列表这类卡片密集页面
 * 在交接时出现背板闪烁；入口动画仍保留弹性 scale，保证二级页打开手感不变。
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
                val enterOffsetRatio = 0.034f + 0.024f * motion
                val exitOffsetRatio = 0.014f + 0.008f * motion
                val enter = fadeIn(tween(durationMillis = 126, delayMillis = 18)) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { height ->
                        (height * enterOffsetRatio).roundToInt().coerceIn(14, 30)
                    }
                val exit = fadeOut(tween(durationMillis = 92)) +
                    slideOutVertically(tween(durationMillis = 108)) { height ->
                        -(height * exitOffsetRatio).roundToInt().coerceIn(6, 14)
                    }
                enter togetherWith exit
            }
        },
        label = "secondary-page-transition",
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = true },
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
