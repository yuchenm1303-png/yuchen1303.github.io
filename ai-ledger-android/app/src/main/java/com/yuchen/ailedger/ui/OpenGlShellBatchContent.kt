package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.ui.gl.LocalNewOpenGlGlassStyleOverride
import com.yuchen.ailedger.ui.gl.OpenGLGlassDynamicState

/** 与独立新版 OpenGL Shell 使用完全相同的强度解析顺序。 */
@Composable
internal fun resolvedFilteredBatchRendererIntensity(fallback: Float): Float {
    val baseBorder = LocalGlassBackdrop.current?.borderStyle ?: GlassBorderStyle()
    val styleOverride = LocalNewOpenGlGlassStyleOverride.current
    val border = remember(baseBorder, styleOverride) {
        styleOverride?.invoke(baseBorder) ?: baseBorder
    }
    return border.newOpenGlGlassIntensity
        .takeIf { it > 0f }
        ?.coerceIn(0.35f, 1.35f)
        ?: fallback.coerceIn(0.35f, 1.35f)
}

/**
 * 只在动态外框强度变化时重组这一小块内容。OpenGL 注册、几何监听、手势协程和点击状态
 * 均停留在上层稳定组合组中，避免按压动画扩大重组范围。
 */
@Composable
internal fun OpenGlBatchFramedContent(
    preserveStandaloneFrame: Boolean,
    effectiveRadius: Int,
    frameGlassIntensity: Float,
    backdropReady: Boolean,
    dynamicState: OpenGLGlassDynamicState,
    shellPressEnabled: Boolean,
    prismEdgeHighlight: Float,
    content: @Composable () -> Unit,
) {
    val framedIntensity = if (preserveStandaloneFrame) {
        frameGlassIntensity * dynamicState.snapshotState.value.glassIntensityScale
    } else {
        frameGlassIntensity
    }
    val frameModifier = if (preserveStandaloneFrame) {
        Modifier.openGlBatchStandaloneShellFrame(
            radius = effectiveRadius,
            glassIntensity = framedIntensity,
        )
    } else {
        Modifier.clip(RoundedCornerShape(effectiveRadius.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(frameModifier),
    ) {
        if (!backdropReady) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFF17345B).copy(
                            alpha = (0.34f * frameGlassIntensity)
                                .coerceIn(0.18f, 0.48f),
                        )
                    )
            )
        }
        OpenGlBatchStaticContent(content)
        if (shellPressEnabled) {
            Box(
                Modifier
                    .fillMaxSize()
                    .openGlBatchShellPressSurfaceOptics(
                        dynamicState = dynamicState,
                        radius = effectiveRadius,
                        prismEdgeHighlight = prismEdgeHighlight,
                    )
            )
        }
    }
}

@Composable
private fun OpenGlBatchStaticContent(content: @Composable () -> Unit) {
    content()
}
