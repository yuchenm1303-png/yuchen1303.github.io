package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.LocalOpenGLShellBatchState
import com.yuchen.ailedger.ui.gl.NewOpenGLGlassBatchLayer
import com.yuchen.ailedger.ui.gl.rememberOpenGLShellBatchState

@Immutable
internal data class OpenGlShellBatchPolicy(
    val acceptedShortEdgeDp: ClosedFloatingPointRange<Float>? = null,
    val preserveStandaloneFrame: Boolean = false,
)

internal val LocalOpenGlShellBatchPolicy = staticCompositionLocalOf {
    OpenGlShellBatchPolicy()
}

/**
 * 页面级 OpenGL Shell 批宿主。
 *
 * 每张玻璃仍保留自己的矩形、圆角、背景采样原点、折射场和按压动态；宿主只共享
 * TextureView、EGL、纹理、shader program 与 VSync 提交，不改变任何卡片的视觉参数。
 */
@Composable
internal fun OpenGlShellBatchHost(
    modifier: Modifier = Modifier,
    acceptedShortEdgeDp: ClosedFloatingPointRange<Float>? = null,
    preserveStandaloneFrame: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberOpenGLShellBatchState()
    val parentCoordinates = remember { GlassCoordinateSource() }
    val policy = remember(acceptedShortEdgeDp, preserveStandaloneFrame) {
        OpenGlShellBatchPolicy(
            acceptedShortEdgeDp = acceptedShortEdgeDp,
            preserveStandaloneFrame = preserveStandaloneFrame,
        )
    }

    DisposableEffect(state, parentCoordinates) {
        state.bindParent(parentCoordinates)
        onDispose { state.clear() }
    }

    Box(
        modifier = modifier.onPlaced { parentCoordinates.coordinates = it },
    ) {
        NewOpenGLGlassBatchLayer(
            state = state,
            parentCoordinates = parentCoordinates,
            modifier = Modifier.matchParentSize(),
        )
        CompositionLocalProvider(
            LocalOpenGLShellBatchState provides state,
            LocalOpenGlShellBatchPolicy provides policy,
        ) {
            content()
        }
    }
}

/**
 * OpenGlShellGlass 在批宿主中的稳定入口。具体表面状态与光效分别拆分到独立源码，
 * 避免宿主、手势动画和绘制光效在同一组合函数中互相扩大重组范围。
 */
@Composable
internal fun OpenGlShellBatchItemSurface(
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    radius: Int,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    OpenGlShellBatchItemSurfaceImpl(
        quality = quality,
        glassIntensity = glassIntensity,
        motionIntensity = motionIntensity,
        radius = radius,
        modifier = modifier,
        onClick = onClick,
        content = content,
    )
}
