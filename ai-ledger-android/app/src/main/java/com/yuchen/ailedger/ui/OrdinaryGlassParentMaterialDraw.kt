package com.yuchen.ailedger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawOrdinaryParentBaseMaterial(
    node: OrdinaryGlassRenderNode,
    rect: Rect
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return
    withOrdinaryParentTransform(node, rect) {
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
        }
    }
}

internal fun DrawScope.drawOrdinaryParentStaticOverlay(
    node: OrdinaryGlassRenderNode,
    rect: Rect
) {
    if (node.role == GlassRole.Shell || rect.width <= 1f || rect.height <= 1f) return
    withOrdinaryParentTransform(node, rect) {
        val cache = ensureOrdinaryParentGeometry(node, rect)
        ensureOrdinaryParentMaterialBrushes(node, rect, cache)
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
