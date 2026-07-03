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
 * 页面模式只读取页面唯一状态并登记卡片，不创建本地状态、坐标源或 TextureView。
 * 没有页面宿主的预览场景才创建局部 Host，两个所有权分支完全分离。
 */
@Composable
internal fun OpenGlShellBatchHost(
    modifier: Modifier = Modifier,
    acceptedShortEdgeDp: ClosedFloatingPointRange<Float>? = null,
    acceptedRadiusDp: IntRange? = null,
    preserveStandaloneFrame: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = remember(acceptedShortEdgeDp, acceptedRadiusDp, preserveStandaloneFrame) {
        OpenGlShellBatchPolicy(
            acceptedShortEdgeDp = acceptedShortEdgeDp,
            acceptedRadiusDp = acceptedRadiusDp,
            preserveStandaloneFrame = preserveStandaloneFrame,
        )
    }
    val pageState = LocalPageOpenGLShellBatchState.current

    if (pageState != null) {
        Box(modifier = modifier) {
            CompositionLocalProvider(
                LocalOpenGLShellBatchState provides pageState,
                LocalOpenGlShellBatchPolicy provides policy,
            ) {
                content()
            }
        }
        return
    }

    LocalOpenGlShellBatchFallbackHost(
        modifier = modifier,
        policy = policy,
        content = content,
    )
}

/** 独立预览和未提供页面 Host 的兼容回退，生产设置页不会进入这里。 */
@Composable
private fun LocalOpenGlShellBatchFallbackHost(
    modifier: Modifier,
    policy: OpenGlShellBatchPolicy,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberOpenGLShellBatchState()
    val parentCoordinates = remember { GlassCoordinateSource() }

    DisposableEffect(state, parentCoordinates) {
        state.bindParent(parentCoordinates)
        onDispose {
            parentCoordinates.coordinates = null
            state.clear()
        }
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
