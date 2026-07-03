package com.yuchen.ailedger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.yuchen.ailedger.ui.gl.OpenGLShellBatchState
import com.yuchen.ailedger.ui.gl.rememberOpenGLShellBatchState

@Immutable
internal data class OpenGlShellBatchPolicy(
    val acceptedShortEdgeDp: ClosedFloatingPointRange<Float>? = null,
    val acceptedRadiusDp: IntRange? = null,
    val preserveStandaloneFrame: Boolean = false,
)

internal val LocalOpenGlShellBatchPolicy = staticCompositionLocalOf {
    OpenGlShellBatchPolicy()
}

/**
 * 页面固定批宿主的状态通道。
 *
 * 它只由页面视口提供，普通 [OpenGlShellGlass] 不会直接读取；只有显式的
 * [OpenGlShellBatchHost] 才会把组内 Shell 登记到页面 Host，避免把独立 Hero Shell
 * 误并入同一批次。
 */
internal val LocalPageOpenGLShellBatchState =
    staticCompositionLocalOf<OpenGLShellBatchState?> { null }

/**
 * OpenGL Shell 批分组入口。
 *
 * 页面提供固定批宿主时，本层只负责限定登记范围，不再创建跟随滚动内容移动的
 * TextureView；没有页面宿主的预览或独立场景仍保留原来的局部 Host 作为兼容回退。
 */
@Composable
internal fun OpenGlShellBatchHost(
    modifier: Modifier = Modifier,
    acceptedShortEdgeDp: ClosedFloatingPointRange<Float>? = null,
    acceptedRadiusDp: IntRange? = null,
    preserveStandaloneFrame: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val pageState = LocalPageOpenGLShellBatchState.current
    val localState = rememberOpenGLShellBatchState()
    val state = pageState ?: localState
    val ownsLocalHost = pageState == null
    val localParentCoordinates = remember { GlassCoordinateSource() }
    val policy = remember(acceptedShortEdgeDp, acceptedRadiusDp, preserveStandaloneFrame) {
        OpenGlShellBatchPolicy(
            acceptedShortEdgeDp = acceptedShortEdgeDp,
            acceptedRadiusDp = acceptedRadiusDp,
            preserveStandaloneFrame = preserveStandaloneFrame,
        )
    }

    DisposableEffect(state, localParentCoordinates, ownsLocalHost) {
        if (ownsLocalHost) state.bindParent(localParentCoordinates)
        onDispose {
            if (ownsLocalHost) {
                localParentCoordinates.coordinates = null
                state.clear()
            }
        }
    }

    val hostModifier = if (ownsLocalHost) {
        modifier.onPlaced { localParentCoordinates.coordinates = it }
    } else {
        modifier
    }

    Box(modifier = hostModifier) {
        if (ownsLocalHost) {
            NewOpenGLGlassBatchLayer(
                state = state,
                parentCoordinates = localParentCoordinates,
                modifier = Modifier.matchParentSize(),
            )
        }
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
