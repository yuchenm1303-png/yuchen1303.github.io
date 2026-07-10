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

@Composable
internal fun SecondaryStaggeredReveal(
    index: Int,
    motionIntensity: Float,
    modifier: Modifier = Modifier,
    tone: Color = Color.White,
    content: @Composable () -> Unit,
) {
    val motion = motionIntensity.coerceIn(0f, 1f)
    val progress = remember { Animatable(if (motion <= 0.05f) 1f else 0f) }
    val density = LocalDensity.current
    val offsetPx = with(density) { 12.dp.toPx() }

    LaunchedEffect(motion) {
        if (motion <= 0.05f) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            delay(min(index, 6) * 34L)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val raw = progress.value
                val p = secondaryMotionSmoothStep(raw.coerceIn(0f, 1f))
                val pulse = secondaryMotionArc(p)
                alpha = (raw.coerceIn(0f, 1f) * 1.80f).coerceIn(0f, 1f)
                translationY = (1f - p) * offsetPx - pulse * offsetPx * 0.08f
                scaleX = 0.986f + p * 0.014f + pulse * 0.004f
                scaleY = 0.970f + p * 0.030f - pulse * 0.003f
                transformOrigin = TransformOrigin(0.50f, 0.58f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .secondaryItemGlint(
                progress = { progress.value },
                motionIntensity = motion,
                tone = tone,
            ),
    ) {
        content()
    }
}
