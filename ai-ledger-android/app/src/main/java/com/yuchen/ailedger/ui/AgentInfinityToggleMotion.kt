package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class AgentInfinityCapsuleMotion(
    val active: Float,
    val scaleX: Float,
    val scaleY: Float,
    val sweep: Float
)

@Composable
internal fun rememberAgentInfinityCapsuleMotion(enabled: Boolean): AgentInfinityCapsuleMotion {
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val sweep = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                scaleX.snapTo(1f)
                scaleX.animateTo(
                    1f,
                    if (enabled) {
                        keyframes {
                            durationMillis = 560
                            0.974f at 78
                            1.036f at 235
                            0.992f at 358
                            1.010f at 459
                            1f at 560
                        }
                    } else {
                        keyframes {
                            durationMillis = 420
                            1.015f at 76
                            0.981f at 202
                            1.006f at 302
                            1f at 420
                        }
                    }
                )
            }
            launch {
                scaleY.snapTo(1f)
                scaleY.animateTo(
                    1f,
                    if (enabled) {
                        keyframes {
                            durationMillis = 560
                            1.026f at 78
                            0.984f at 235
                            1.010f at 358
                            0.996f at 459
                            1f at 560
                        }
                    } else {
                        keyframes {
                            durationMillis = 420
                            0.988f at 76
                            1.017f at 202
                            0.996f at 302
                            1f at 420
                        }
                    }
                )
            }
            launch {
                sweep.snapTo(0f)
                sweep.animateTo(
                    1f,
                    tween(
                        durationMillis = if (enabled) 520 else 360,
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    val active by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (enabled) 560 else 420,
            easing = FastOutSlowInEasing
        ),
        label = "agent-web-active"
    )
    return AgentInfinityCapsuleMotion(
        active = active,
        scaleX = scaleX.value,
        scaleY = scaleY.value,
        sweep = sweep.value
    )
}
