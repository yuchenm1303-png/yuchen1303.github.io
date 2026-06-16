package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * 对齐 Glass.kt 中独立 ordinaryPressSurfaceOptics 空 Box 的真实顺序：
 * 六层按压光学都位于业务 content 上方，只在按压活跃期间执行。
 * 所有动态值直接保存在当前绘制栈中，避免活跃帧创建临时参数对象。
 */
internal fun DrawScope.drawOrdinaryParentPressOptics(
    item: VisibleOrdinaryGlassItem
) {
    val node = item.node
    val rect = item.rect
    if (!node.pressable || node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return

    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val rebound = ordinaryParentSmoothStep((-node.pressProgress / 0.18f).coerceIn(0f, 1f))
    val safePress = maxOf(
        positivePress,
        node.lensProgress * 0.86f,
        rebound * 0.28f
    ).coerceIn(0f, 1.28f)
    if (safePress < 0.001f) return

    withOrdinaryParentTransform(item) {
        val cache = ensureOrdinaryParentGeometry(node, rect)
        val w = rect.width.coerceAtLeast(1f)
        val h = rect.height.coerceAtLeast(1f)
        val elasticity = node.elasticity.coerceIn(0.08f, 1f)
        val center = Offset(
            node.pressCenter.x.coerceIn(0f, 1f) * w,
            node.pressCenter.y.coerceIn(0f, 1f) * h
        )
        val p = ordinaryParentSmoothStep((safePress / 0.92f).coerceIn(0f, 1f)) * elasticity
        val rimFlow = ordinaryParentSmoothStep(
            node.sweepProgress.coerceIn(0f, 1.18f) / 1.18f
        )
        val chroma = when (node.role) {
            GlassRole.Chip -> 1.00f
            GlassRole.Floating -> 0.95f
            GlassRole.Flex -> 0.82f
            GlassRole.Card -> 0.66f
            GlassRole.Nav -> 0.58f
            GlassRole.Shell -> 0f
        }
        val optical = (0.10f + p * 1.10f).coerceIn(0f, 1.18f)
        val rainbowAlpha = (0.040f + p * 0.170f).coerceIn(0f, 0.24f) * chroma
        val maxSide = maxOf(w, h)
        val sweepX = -0.36f + rimFlow * 1.66f
        val localSize = cache.localSize
        val rimSize = cache.rimSize
        val cornerRadius = cache.cornerRadius
        val rimInset = 0.62.dp.toPx()

        clipPath(cache.shapePath) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.080f * optical),
                        Color(0xFF8DFFF3).copy(alpha = 0.044f * optical * chroma),
                        Color(0xFFFF8FE7).copy(alpha = 0.026f * optical * chroma),
                        Color.Transparent
                    ),
                    center,
                    maxSide * (0.48f + 0.28f * p)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFFF7AD9).copy(alpha = rainbowAlpha * 0.70f),
                        Color(0xFFFFD166).copy(alpha = rainbowAlpha * 0.52f),
                        Color(0xFF7CFFEA).copy(alpha = rainbowAlpha * 0.80f),
                        Color(0xFF8EA2FF).copy(alpha = rainbowAlpha * 0.66f),
                        Color.Transparent
                    ),
                    Offset(localSize.width * (sweepX - 0.46f), localSize.height * -0.10f),
                    Offset(localSize.width * (sweepX + 0.58f), localSize.height * 1.08f)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF04112A).copy(alpha = 0.018f * optical),
                        Color(0xFF00030A).copy(alpha = 0.072f * p)
                    ),
                    center,
                    maxSide * (0.76f + 0.20f * p)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.160f * p),
                        Color(0xFF9DFFF1).copy(alpha = 0.080f * p * chroma),
                        Color(0xFFFF8FE7).copy(alpha = 0.052f * p * chroma),
                        Color.Transparent
                    ),
                    center,
                    maxSide * (0.32f + 0.12f * p)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFFFF72D2).copy(alpha = 0.32f * p * chroma),
                        Color(0xFFFFF0A8).copy(alpha = 0.30f * p * chroma),
                        Color(0xFF76FFF1).copy(alpha = 0.34f * p * chroma),
                        Color(0xFF9AA8FF).copy(alpha = 0.26f * p * chroma),
                        Color.Transparent
                    ),
                    Offset(localSize.width * (sweepX - 0.24f), 0f),
                    Offset(localSize.width * (sweepX + 0.30f), localSize.height * 0.98f)
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(0.64.dp.toPx() + 1.16.dp.toPx() * p),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.105f + 0.090f * p),
                        Color(0xFFE9FFFF).copy(alpha = 0.020f + 0.056f * p),
                        Color.Transparent,
                        Color(0xFF000819).copy(alpha = 0.030f + 0.070f * p)
                    ),
                    0f,
                    localSize.height
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(0.48.dp.toPx() + 0.72.dp.toPx() * p),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFFFF7FF).copy(alpha = 0.100f * p),
                        Color(0xFFFF8BD9).copy(alpha = 0.038f * p * chroma),
                        Color.Transparent
                    ),
                    Offset(localSize.width * 0.14f, localSize.height * 0.08f),
                    maxSide * 0.32f
                ),
                topLeft = Offset(1.15.dp.toPx(), 1.15.dp.toPx()),
                size = Size(
                    (localSize.width - 2.30.dp.toPx()).coerceAtLeast(1f),
                    (localSize.height - 2.30.dp.toPx()).coerceAtLeast(1f)
                ),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun ordinaryParentSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
