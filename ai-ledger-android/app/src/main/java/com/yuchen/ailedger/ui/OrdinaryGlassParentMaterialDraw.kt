package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/**
 * 对齐 Glass.kt 中独立 glassSkin 空 Box 的真实绘制顺序：
 * 静态底材、边缘带和外沿全部位于业务 content 下方。
 */
internal fun DrawScope.drawOrdinaryParentMaterial(
    item: VisibleOrdinaryGlassItem
) {
    val node = item.node
    val rect = item.rect
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return
    withOrdinaryParentTransform(item) {
        val cache = ensureOrdinaryParentGeometry(node, rect)
        ensureOrdinaryParentMaterialBrushes(node, rect, cache)
        val glass = ComposeGlassLabState.style
        clipPath(cache.shapePath) {
            drawRect(requireNotNull(cache.baseField), size = cache.localSize)
            drawRect(requireNotNull(cache.quietField), size = cache.localSize)
            drawPath(
                path = cache.edgeBandPath,
                brush = requireNotNull(cache.edgeBandBrush),
                blendMode = BlendMode.Screen
            )
            if (glass.topVariation > 0.001f) {
                clipRect(
                    left = 0f,
                    top = 0f,
                    right = cache.localSize.width,
                    bottom = (cache.localSize.height * 0.52f).coerceAtLeast(1f)
                ) {
                    drawPath(
                        path = cache.edgeBandPath,
                        brush = requireNotNull(cache.flowBrush),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            if (glass.bottomMass > 0.001f) {
                clipRect(
                    left = 0f,
                    top = cache.localSize.height * 0.45f,
                    right = cache.localSize.width,
                    bottom = cache.localSize.height
                ) {
                    drawPath(
                        path = cache.bottomMassBandPath,
                        brush = requireNotNull(cache.bottomMassBrush),
                        blendMode = BlendMode.Multiply
                    )
                }
            }

            val inset = 0.75.dp.toPx()
            drawRoundRect(
                brush = requireNotNull(cache.rimField),
                topLeft = Offset(inset, inset),
                size = Size(
                    (cache.localSize.width - inset * 2f).coerceAtLeast(1f),
                    (cache.localSize.height - inset * 2f).coerceAtLeast(1f)
                ),
                cornerRadius = cache.cornerRadius,
                style = Stroke(cache.rimStrokePx),
                blendMode = BlendMode.Screen
            )
        }
    }
}
