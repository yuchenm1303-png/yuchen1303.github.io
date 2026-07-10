package com.yuchen.ailedger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 市场宽度专用的共享环境光相位。
 * 一组卡片只维护一条动画时钟，所有卡片只重绘光层，不触发内容重组。
 */
@Composable
internal fun rememberStockAmbientGlassPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "stock-breadth-glow")
    return transition.animateFloat(
        initialValue = -0.55f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stock-breadth-glow-phase"
    )
}

/**
 * 单层、非交互的 Compose 环境光玻璃。
 *
 * 底层只有一个普通 Floating 玻璃，额外的 Canvas 只绘制低强度扫光，不承载材质、
 * 点击或几何注册，因此不会出现固定玻璃与动态玻璃重叠，也不会制造虚假的按钮语义。
 * 不调用 OpenGLGlassCardLayer，不进入 OpenGL registry，不触发 geometry sync。
 */
@Composable
internal fun StockNativeAmbientGlass(
    phase: State<Float>,
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    frostAlpha: Float = 0.075f,
    contentPadding: Dp = 0.dp,
    phaseOffset: Float = 0f,
    glowStrength: Float = 1f,
    content: @Composable () -> Unit
) {
    val state = LocalStockNativeGlassState.current
    val radiusValue = radius.value.roundToInt().coerceAtLeast(1)
    val shape = RoundedCornerShape(radius)
    val motion = (state?.motionIntensity ?: 1f).coerceIn(0f, 1f)
    val resolvedGlow = (0.075f * glowStrength * motion).coerceIn(0f, 0.12f)

    Box(modifier = modifier) {
        if (state != null) {
            val intensity = state.glassIntensity * 1.02f
            GlassPanel(
                quality = state.quality,
                glassIntensity = intensity,
                motionIntensity = state.motionIntensity,
                radius = radiusValue,
                modifier = Modifier.fillMaxSize(),
                role = GlassRole.Floating,
                intensity = intensity
            ) {}
        } else {
            FrostInfoGlassPanel(
                radius = radius.value,
                backdropAlpha = 1f,
                frostAlpha = frostAlpha,
                dimAlpha = 0f,
                modifier = Modifier.fillMaxSize()
            ) {}
        }

        if (resolvedGlow > 0.001f) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
            ) {
                val centerX = size.width * (phase.value + phaseOffset)
                val sweepWidth = size.width * 0.52f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = resolvedGlow * 0.42f),
                            StockAqua.copy(alpha = resolvedGlow),
                            Color.White.copy(alpha = resolvedGlow * 0.30f),
                            Color.Transparent
                        ),
                        start = Offset(centerX - sweepWidth, 0f),
                        end = Offset(centerX + sweepWidth, size.height)
                    ),
                    blendMode = BlendMode.Screen
                )
            }
        }

        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            content()
        }
    }
}

@Composable
internal fun StockNativeAmbientMetricTile(
    label: String,
    value: String,
    tone: Color,
    phase: State<Float>,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    phaseOffset: Float = 0f
) {
    StockNativeAmbientGlass(
        phase = phase,
        modifier = modifier.height(if (prominent) 70.dp else 60.dp),
        radius = 18.dp,
        frostAlpha = if (prominent) 0.092f else 0.074f,
        phaseOffset = phaseOffset,
        glowStrength = if (prominent) 1.04f else 0.92f
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = StockMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                value.ifBlank { "--" },
                color = tone,
                fontSize = if (prominent) 17.sp else 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
