package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

internal data class OrdinaryGlassVisualTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationY: Float,
    val origin: Offset
)

internal fun ordinaryGlassVisualTransform(node: OrdinaryGlassRenderNode): OrdinaryGlassVisualTransform {
    if (!node.pressable || node.role == GlassRole.Shell) {
        return OrdinaryGlassVisualTransform(
            scaleX = 1f,
            scaleY = 1f,
            translationY = 0f,
            origin = Offset(0.5f, 0.5f)
        )
    }

    val elasticity = node.elasticity.coerceIn(0f, 1f)
    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val rebound = ordinaryGlassSmoothStep((-node.pressProgress / 0.18f).coerceIn(0f, 1f))
    val compression = ordinaryGlassSmoothStep((positivePress / 0.94f).coerceIn(0f, 1f))

    return OrdinaryGlassVisualTransform(
        scaleX = 1f + compression * (0.006f + 0.049f * elasticity) - rebound * 0.018f * elasticity,
        scaleY = 1f - compression * (0.010f + 0.064f * elasticity) + rebound * 0.030f * elasticity,
        translationY = compression * (0.70f + 3.90f * elasticity) - rebound * 1.55f * elasticity,
        origin = Offset(
            node.pressCenter.x.coerceIn(0f, 1f),
            node.pressCenter.y.coerceIn(0f, 1f)
        )
    )
}

internal fun ordinaryGlassTransformedBounds(
    node: OrdinaryGlassRenderNode,
    rect: Rect
): Rect {
    val transform = ordinaryGlassVisualTransform(node)
    val pivotX = rect.width * transform.origin.x
    val pivotY = rect.height * transform.origin.y
    val left = rect.left + pivotX * (1f - transform.scaleX)
    val top = rect.top + transform.translationY + pivotY * (1f - transform.scaleY)
    return Rect(
        left = left,
        top = top,
        right = left + rect.width * transform.scaleX,
        bottom = top + rect.height * transform.scaleY
    )
}

private fun DrawScope.withOrdinaryGlassVisualTransform(
    node: OrdinaryGlassRenderNode,
    rect: Rect,
    block: DrawScope.() -> Unit
) {
    val transform = ordinaryGlassVisualTransform(node)
    withTransform({
        translate(rect.left, rect.top + transform.translationY)
    }) {
        withTransform({
            scale(
                scaleX = transform.scaleX,
                scaleY = transform.scaleY,
                pivot = Offset(rect.width * transform.origin.x, rect.height * transform.origin.y)
            )
        }) {
            block()
        }
    }
}

/**
 * 普通 Compose GlassPanel / PressableGlass 的父级材质绘制实现。
 *
 * 这里严格复用当前 Glass.kt 中普通玻璃的数值公式；只有
 * OrdinaryGlassRenderMode.ParentDraw 才会真正调用，因此 Shadow 阶段不改变视觉。
 */
internal fun DrawScope.drawOrdinaryComposeGlassMaterial(
    node: OrdinaryGlassRenderNode,
    rect: Rect
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return

    withOrdinaryGlassVisualTransform(node, rect) {
        val w = rect.width.coerceAtLeast(1f)
        val h = rect.height.coerceAtLeast(1f)
        val radiusPx = node.radius.dp.toPx()
        val cornerRadius = CornerRadius(radiusPx, radiusPx)
        val shapePath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
        }
        val intensityScale = node.glassIntensity.coerceIn(0.25f, 1.45f)
        val pulse = 0.94f + node.breathe * 0.030f

        val glass = ComposeGlassLabState.style
        val frost = ComposeGlassRuntimeDefaults.frost * intensityScale
        val quiet = glass.quiet
        val topLight = glass.topLight * intensityScale
        val edgeWidth = glass.topWidthDp.dp.toPx().coerceAtLeast(0.05.dp.toPx())
        val pathFlow = glass.topVariation
        val bottomLight = glass.bottomLight * intensityScale
        val bottomWidth = glass.bottomWidthDp.dp.toPx().coerceAtLeast(0.05.dp.toPx())
        val outerRim = glass.outerRim * intensityScale
        val bottomMass = glass.bottomMass * intensityScale
        val sideCarry = glass.sideLight * intensityScale

        val baseField = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.026f * frost * pulse),
                Color.White.copy(alpha = 0f),
                Color.Black.copy(alpha = 0.080f * quiet)
            ),
            start = Offset.Zero,
            end = Offset(w, h)
        )
        val quietField = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.010f / quiet.coerceAtLeast(0.25f)),
                Color.Transparent,
                Color.Black.copy(alpha = 0.045f * quiet)
            ),
            center = Offset(w * 0.50f, h * 0.46f),
            radius = maxOf(w, h) * 0.72f
        )

        val opticalBandWidth = (
            1.0.dp.toPx() + edgeWidth * 2.20f + bottomWidth * 0.72f
        ).coerceIn(1.0.dp.toPx(), minOf(w, h) * 0.22f)
        val innerLeft = opticalBandWidth
        val innerTop = opticalBandWidth
        val innerRight = (w - opticalBandWidth).coerceAtLeast(innerLeft + 1f)
        val innerBottom = (h - opticalBandWidth).coerceAtLeast(innerTop + 1f)
        val innerRadius = (radiusPx - opticalBandWidth).coerceAtLeast(0f)
        val edgeBandPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
            addRoundRect(RoundRect(innerLeft, innerTop, innerRight, innerBottom, innerRadius, innerRadius))
        }

        val bottomMassWidth = (
            opticalBandWidth + bottomWidth * 2.10f
        ).coerceIn(opticalBandWidth, minOf(w, h) * 0.28f)
        val massInnerLeft = bottomMassWidth
        val massInnerTop = bottomMassWidth
        val massInnerRight = (w - bottomMassWidth).coerceAtLeast(massInnerLeft + 1f)
        val massInnerBottom = (h - bottomMassWidth).coerceAtLeast(massInnerTop + 1f)
        val massInnerRadius = (radiusPx - bottomMassWidth).coerceAtLeast(0f)
        val bottomMassBandPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
            addRoundRect(
                RoundRect(
                    massInnerLeft,
                    massInnerTop,
                    massInnerRight,
                    massInnerBottom,
                    massInnerRadius,
                    massInnerRadius
                )
            )
        }

        val topAlpha = (0.120f + 0.055f * pathFlow) * topLight
        val sideAlpha = 0.028f * sideCarry
        val bottomAlpha = 0.090f * bottomLight
        val edgeBandBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha),
                Color(0xFFEAF9FF).copy(alpha = topAlpha * 0.36f),
                Color.White.copy(alpha = sideAlpha),
                Color.Transparent,
                Color.White.copy(alpha = bottomAlpha * 0.26f),
                Color.White.copy(alpha = bottomAlpha)
            ),
            startY = 0f,
            endY = h
        )
        val flowBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = topAlpha * 0.18f * pathFlow),
                Color.White.copy(alpha = topAlpha * 0.04f),
                Color.Transparent,
                Color.White.copy(alpha = topAlpha * 0.08f * pathFlow),
                Color.White.copy(alpha = topAlpha * 0.025f)
            ),
            startX = 0f,
            endX = w
        )
        val bottomMassBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color(0xFF07132F).copy(alpha = 0.024f * bottomMass),
                Color(0xFF030714).copy(alpha = 0.082f * bottomMass),
                Color(0xFF00020A).copy(alpha = 0.180f * bottomMass)
            ),
            startY = 0f,
            endY = h
        )
        val rimField = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.120f * outerRim),
                Color.White.copy(alpha = 0.018f * outerRim),
                Color.Transparent,
                Color.Black.copy(alpha = 0.072f * bottomMass),
                Color.White.copy(alpha = 0.014f * outerRim)
            ),
            start = Offset.Zero,
            end = Offset(w, h)
        )
        val localSize = Size(w, h)

        clipPath(shapePath) {
            drawRect(brush = baseField, size = localSize)
            drawRect(brush = quietField, size = localSize)
            drawPath(path = edgeBandPath, brush = edgeBandBrush, blendMode = BlendMode.Screen)

            if (pathFlow > 0.001f) {
                clipRect(left = 0f, top = 0f, right = w, bottom = (h * 0.52f).coerceAtLeast(1f)) {
                    drawPath(path = edgeBandPath, brush = flowBrush, blendMode = BlendMode.Screen)
                }
            }
            if (bottomMass > 0.001f) {
                clipRect(left = 0f, top = h * 0.45f, right = w, bottom = h) {
                    drawPath(path = bottomMassBandPath, brush = bottomMassBrush, blendMode = BlendMode.Multiply)
                }
            }

            drawRoundRect(
                brush = rimField,
                topLeft = Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
                size = Size(
                    (w - 1.5.dp.toPx()).coerceAtLeast(1f),
                    (h - 1.5.dp.toPx()).coerceAtLeast(1f)
                ),
                cornerRadius = cornerRadius,
                style = Stroke(maxOf(0.34.dp.toPx(), 0.48.dp.toPx() * outerRim)),
                blendMode = BlendMode.Screen
            )
        }
    }
}

internal fun DrawScope.drawOrdinaryComposeGlassPressOptics(
    node: OrdinaryGlassRenderNode,
    rect: Rect
) {
    if (!node.pressable || node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return

    val positivePress = node.pressProgress.coerceAtLeast(0f)
    val rebound = ordinaryGlassSmoothStep((-node.pressProgress / 0.18f).coerceIn(0f, 1f))
    val safePress = maxOf(positivePress, node.lensProgress * 0.86f, rebound * 0.28f).coerceIn(0f, 1.28f)
    if (safePress < 0.001f) return

    withOrdinaryGlassVisualTransform(node, rect) {
        val w = rect.width.coerceAtLeast(1f)
        val h = rect.height.coerceAtLeast(1f)
        val maxSide = maxOf(w, h)
        val elasticity = node.elasticity.coerceIn(0.08f, 1f)
        val centerNorm = Offset(
            node.pressCenter.x.coerceIn(0f, 1f),
            node.pressCenter.y.coerceIn(0f, 1f)
        )
        val center = Offset(centerNorm.x * w, centerNorm.y * h)
        val p = ordinaryGlassSmoothStep((safePress / 0.92f).coerceIn(0f, 1f)) * elasticity
        val rimFlow = ordinaryGlassSmoothStep(node.sweepProgress.coerceIn(0f, 1.18f) / 1.18f)
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
        val rimInset = 0.62.dp.toPx()
        val radiusPx = node.radius.dp.toPx()
        val cornerRadius = CornerRadius(radiusPx, radiusPx)
        val shapePath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, radiusPx, radiusPx))
        }
        val rimSize = Size(
            (w - rimInset * 2f).coerceAtLeast(1f),
            (h - rimInset * 2f).coerceAtLeast(1f)
        )
        val sweepX = -0.36f + rimFlow * 1.66f
        val localSize = Size(w, h)

        clipPath(shapePath) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.080f * optical),
                        Color(0xFF8DFFF3).copy(alpha = 0.044f * optical * chroma),
                        Color(0xFFFF8FE7).copy(alpha = 0.026f * optical * chroma),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxSide * (0.48f + 0.28f * p)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF7AD9).copy(alpha = rainbowAlpha * 0.70f),
                        Color(0xFFFFD166).copy(alpha = rainbowAlpha * 0.52f),
                        Color(0xFF7CFFEA).copy(alpha = rainbowAlpha * 0.80f),
                        Color(0xFF8EA2FF).copy(alpha = rainbowAlpha * 0.66f),
                        Color.Transparent
                    ),
                    start = Offset(w * (sweepX - 0.46f), h * -0.10f),
                    end = Offset(w * (sweepX + 0.58f), h * 1.08f)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF04112A).copy(alpha = 0.018f * optical),
                        Color(0xFF00030A).copy(alpha = 0.072f * p)
                    ),
                    center = center,
                    radius = maxSide * (0.76f + 0.20f * p)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.160f * p),
                        Color(0xFF9DFFF1).copy(alpha = 0.080f * p * chroma),
                        Color(0xFFFF8FE7).copy(alpha = 0.052f * p * chroma),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxSide * (0.32f + 0.12f * p)
                ),
                size = localSize,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFF72D2).copy(alpha = 0.32f * p * chroma),
                        Color(0xFFFFF0A8).copy(alpha = 0.30f * p * chroma),
                        Color(0xFF76FFF1).copy(alpha = 0.34f * p * chroma),
                        Color(0xFF9AA8FF).copy(alpha = 0.26f * p * chroma),
                        Color.Transparent
                    ),
                    start = Offset(w * (sweepX - 0.24f), 0f),
                    end = Offset(w * (sweepX + 0.30f), h * 0.98f)
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(0.64.dp.toPx() + 1.16.dp.toPx() * p),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.105f + 0.090f * p),
                        Color(0xFFE9FFFF).copy(alpha = 0.020f + 0.056f * p),
                        Color.Transparent,
                        Color(0xFF000819).copy(alpha = 0.030f + 0.070f * p)
                    ),
                    startY = 0f,
                    endY = h
                ),
                topLeft = Offset(rimInset, rimInset),
                size = rimSize,
                cornerRadius = cornerRadius,
                style = Stroke(0.48.dp.toPx() + 0.72.dp.toPx() * p),
                blendMode = BlendMode.Screen
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF7FF).copy(alpha = 0.100f * p),
                        Color(0xFFFF8BD9).copy(alpha = 0.038f * p * chroma),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.14f, h * 0.08f),
                    radius = maxSide * 0.32f
                ),
                topLeft = Offset(1.15.dp.toPx(), 1.15.dp.toPx()),
                size = Size(
                    (w - 2.30.dp.toPx()).coerceAtLeast(1f),
                    (h - 2.30.dp.toPx()).coerceAtLeast(1f)
                ),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun ordinaryGlassSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
