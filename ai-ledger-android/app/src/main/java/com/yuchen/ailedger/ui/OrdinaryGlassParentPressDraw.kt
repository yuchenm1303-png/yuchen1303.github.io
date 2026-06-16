package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

private data class OrdinaryParentPressValues(
    val p: Float,
    val chroma: Float,
    val optical: Float,
    val rainbowAlpha: Float,
    val center: Offset,
    val maxSide: Float,
    val sweepX: Float,
    val cornerRadius: CornerRadius,
    val localSize: Size,
    val rimSize: Size,
    val shapePath: Path
)

private fun DrawScope.resolveOrdinaryParentPressValues(
    item: VisibleOrdinaryGlassItem
): OrdinaryParentPressValues? {
    val node = item.node
    val rect = item.rect
    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val rebound = ordinaryParentSmoothStep((-node.pressProgress / 0.18f).coerceIn(0f, 1f))
    val safePress = maxOf(
        positivePress,
        node.lensProgress * 0.86f,
        rebound * 0.28f
    ).coerceIn(0f, 1.28f)
    if (safePress < 0.001f) return null

    val cache = ensureOrdinaryParentGeometry(node, rect)
    val w = rect.width.coerceAtLeast(1f)
    val h = rect.height.coerceAtLeast(1f)
    val elasticity = node.elasticity.coerceIn(0.08f, 1f)
    val centerNorm = Offset(
        node.pressCenter.x.coerceIn(0f, 1f),
        node.pressCenter.y.coerceIn(0f, 1f)
    )
    val center = Offset(centerNorm.x * w, centerNorm.y * h)
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
    return OrdinaryParentPressValues(
        p = p,
        chroma = chroma,
        optical = (0.10f + p * 1.10f).coerceIn(0f, 1.18f),
        rainbowAlpha = (0.040f + p * 0.170f).coerceIn(0f, 0.24f) * chroma,
        center = center,
        maxSide = maxOf(w, h),
        sweepX = -0.36f + rimFlow * 1.66f,
        cornerRadius = cache.cornerRadius,
        localSize = cache.localSize,
        rimSize = cache.rimSize,
        shapePath = cache.shapePath
    )
}

/**
 * 对齐 Glass.kt 中独立 ordinaryPressSurfaceOptics 空 Box 的真实顺序：
 * 六层按压光学都位于业务 content 上方，只在按压活跃期间执行。
 */
internal fun DrawScope.drawOrdinaryParentPressOptics(
    item: VisibleOrdinaryGlassItem
) {
    val node = item.node
    val rect = item.rect
    if (!node.pressable || node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return
    withOrdinaryParentTransform(item) {
        val values = resolveOrdinaryParentPressValues(item)
            ?: return@withOrdinaryParentTransform
        val rimInset = 0.62.dp.toPx()
        clipPath(values.shapePath) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.080f * values.optical),
                        Color(0xFF8DFFF3).copy(alpha = 0.044f * values.optical * values.chroma),
                        Color(0xFFFF8FE7).copy(alpha = 0.026f * values.optical * values.chroma),
                        Color.Transparent
                    ),
                    values.center,
                    values.maxSide * (0.48f + 0.28f * values.p)
                ),
                size = values.localSize,
                cornerRadius = values.cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFFF7AD9).copy(alpha = values.rainbowAlpha * 0.70f),
                        Color(0xFFFFD166).copy(alpha = values.rainbowAlpha * 0.52f),
                        Color(0xFF7CFFEA).copy(alpha = values.rainbowAlpha * 0.80f),
                        Color(0xFF8EA2FF).copy(alpha = values.rainbowAlpha * 0.66f),
                        Color.Transparent
                    ),
                    Offset(values.localSize.width * (values.sweepX - 0.46f), values.localSize.height * -0.10f),
                    Offset(values.localSize.width * (values.sweepX + 0.58f), values.localSize.height * 1.08f)
                ),
                size = values.localSize,
                cornerRadius = values.cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF04112A).copy(alpha = 0.018f * values.optical),
                        Color(0xFF00030A).copy(alpha = 0.072f * values.p)
                    ),
                    values.center,
                    values.maxSide * (0.76f + 0.20f * values.p)
                ),
                size = values.localSize,
                cornerRadius = values.cornerRadius,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.160f * values.p),
                        Color(0xFF9DFFF1).copy(alpha = 0.080f * values.p * values.chroma),
                        Color(0xFFFF8FE7).copy(alpha = 0.052f * values.p * values.chroma),
                        Color.Transparent
                    ),
                    values.center,
                    values.maxSide * (0.32f + 0.12f * values.p)
                ),
                size = values.localSize,
                cornerRadius = values.cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFFFF72D2).copy(alpha = 0.32f * values.p * values.chroma),
                        Color(0xFFFFF0A8).copy(alpha = 0.30f * values.p * values.chroma),
                        Color(0xFF76FFF1).copy(alpha = 0.34f * values.p * values.chroma),
                        Color(0xFF9AA8FF).copy(alpha = 0.26f * values.p * values.chroma),
                        Color.Transparent
                    ),
                    Offset(values.localSize.width * (values.sweepX - 0.24f), 0f),
                    Offset(values.localSize.width * (values.sweepX + 0.30f), values.localSize.height * 0.98f)
                ),
                topLeft = Offset(rimInset, rimInset),
                size = values.rimSize,
                cornerRadius = values.cornerRadius,
                style = Stroke(0.64.dp.toPx() + 1.16.dp.toPx() * values.p),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.105f + 0.090f * values.p),
                        Color(0xFFE9FFFF).copy(alpha = 0.020f + 0.056f * values.p),
                        Color.Transparent,
                        Color(0xFF000819).copy(alpha = 0.030f + 0.070f * values.p)
                    ),
                    0f,
                    values.localSize.height
                ),
                topLeft = Offset(rimInset, rimInset),
                size = values.rimSize,
                cornerRadius = values.cornerRadius,
                style = Stroke(0.48.dp.toPx() + 0.72.dp.toPx() * values.p),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFFFF7FF).copy(alpha = 0.100f * values.p),
                        Color(0xFFFF8BD9).copy(alpha = 0.038f * values.p * values.chroma),
                        Color.Transparent
                    ),
                    Offset(values.localSize.width * 0.14f, values.localSize.height * 0.08f),
                    values.maxSide * 0.32f
                ),
                topLeft = Offset(1.15.dp.toPx(), 1.15.dp.toPx()),
                size = Size(
                    (values.localSize.width - 2.30.dp.toPx()).coerceAtLeast(1f),
                    (values.localSize.height - 2.30.dp.toPx()).coerceAtLeast(1f)
                ),
                cornerRadius = values.cornerRadius,
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun ordinaryParentSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
