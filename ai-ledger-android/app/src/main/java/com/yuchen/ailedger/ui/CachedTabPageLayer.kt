package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab

private const val PAGE_ENTER_FADE_MS = 210
private const val PAGE_EXIT_FADE_MS = 150
private const val PAGE_ENTER_OFFSET_DP = 8f
private const val PAGE_EXIT_OFFSET_DP = -4f
private const val PAGE_MIN_SCALE = 0.992f
private const val PAGE_HIDDEN_ALPHA_EPSILON = 0.001f
private const val PAGE_HORIZONTAL_UNBOUNDED_CLIP_PX = 1_000_000f

/**
 * 底部导航栏的可视高度为 56 dp，外层底边距为 6 dp。
 * 再向上保留 8 dp 安全间隔，使普通页面的卡片、雾面玻璃和点击区域
 * 在进入导航栏假折射区域之前就被真实布局边界截断。
 */
private val BOTTOM_DOCK_CONTENT_BOUNDARY_INSET = 70.dp
private val BOTTOM_DOCK_IME_OPEN_THRESHOLD = 48.dp

@Composable
internal fun CachedTabPageLayer(
    tab: AppTab,
    currentTab: AppTab,
    initialTab: AppTab,
    activationKey: Int,
    heavyEffectsReady: Boolean,
    diagnostics: PerformanceDiagnosticsState,
    saveableStateHolder: SaveableStateHolder,
    onHidden: () -> Unit,
    content: @Composable (AppTab) -> Unit,
) {
    val parentGlassBackdrop = LocalGlassBackdrop.current
    val parentBlurredBackdrop = LocalBlurredBackdrop.current
    val parentBackdropTicker = LocalBackdropFrameTicker.current
    val hostView = LocalView.current
    val active = tab == currentTab
    val initialAlpha = if (tab == initialTab && activationKey == 1) 1f else 0f
    val alphaState = remember(tab) { Animatable(initialAlpha) }

    // 所有缓存页面共用 Activity 根视图的 PreDraw。引用计数只负责确保最终帧提交器
    // 在页面切换期间始终绑定到真实 Android traversal，不介入任何 Compose 布局尺寸。
    DisposableEffect(hostView) {
        val release = OpenGLFrameFinalizer.bindHostView(hostView)
        onDispose(release)
    }

    LaunchedEffect(active) {
        alphaState.animateTo(
            targetValue = if (active) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (active) PAGE_ENTER_FADE_MS else PAGE_EXIT_FADE_MS,
                easing = FastOutSlowInEasing,
            ),
        )
        if (!active && alphaState.value <= PAGE_HIDDEN_ALPHA_EPSILON) onHidden()
    }

    val alpha = alphaState.value
    val visibleDuringTransition = active || alpha > PAGE_HIDDEN_ALPHA_EPSILON
    val leaving = !active && visibleDuringTransition
    val pageHeavyEffectsReady = active && heavyEffectsReady
    val visualEffectsEnabled = visibleDuringTransition &&
        pageHeavyEffectsReady &&
        !diagnostics.openGlGlassOff
    val openGlFrameSyncEnabled = visibleDuringTransition && !diagnostics.openGlGlassOff
    val sceneGroup = tab.defaultGlassSceneGroup()
    val ordinaryRenderMode = if (visibleDuringTransition) {
        OrdinaryGlassParentDrawController.renderModeFor(sceneGroup)
    } else {
        OrdinaryGlassRenderMode.Shadow
    }
    val pageOffsetDp = if (active) PAGE_ENTER_OFFSET_DP else PAGE_EXIT_OFFSET_DP
    val pageScale = PAGE_MIN_SCALE + (1f - PAGE_MIN_SCALE) * alpha

    // graphicsLayer 动画不会保证触发 onPlaced。每次 alpha/translation/scale 推进时直接标记
    // 当前 traversal，在同帧 PreDraw 读取最终 localToRoot，避免 OpenGL 本体追后一帧。
    LaunchedEffect(visibleDuringTransition, parentBackdropTicker, alphaState) {
        if (!visibleDuringTransition || parentBackdropTicker == null) return@LaunchedEffect
        snapshotFlow { alphaState.value }.collect {
            OpenGLFrameFinalizer.requestActiveTickerFrame()
        }
    }

    OrdinaryGlassSceneHost(
        group = sceneGroup,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (active) 1f else -1f)
            .graphicsLayer {
                this.alpha = alpha
                translationY = pageOffsetDp.dp.toPx() * (1f - alpha)
                scaleX = pageScale
                scaleY = pageScale
            },
        renderMode = ordinaryRenderMode,
    ) {
        saveableStateHolder.SaveableStateProvider(tab.name) {
            CompositionLocalProvider(
                LocalPageActive provides visibleDuringTransition,
                LocalPageVisible provides visibleDuringTransition,
                LocalPageLeaving provides leaving,
                LocalPageActivationTick provides activationKey,
                LocalPageHeavyEffectsEnabled provides visualEffectsEnabled,
                LocalOpenGLGlassViewportActive provides false,
                LocalGlassBackdrop provides if (visibleDuringTransition) parentGlassBackdrop else null,
                LocalBlurredBackdrop provides if (visibleDuringTransition) parentBlurredBackdrop else null,
                // 离场页面仍在 graphicsLayer 中移动，必须保留 ticker 直到 alpha 真正归零。
                // heavyEffectsReady 只控制动态视觉，不再切断坐标同步链。
                LocalBackdropFrameTicker provides if (openGlFrameSyncEnabled) parentBackdropTicker else null,
            ) {
                key(tab) {
                    BottomDockBoundedPageViewport(tab = tab) {
                        NonOpenGLGlassBatchHost(
                            modifier = Modifier.fillMaxSize(),
                            includeAdaptiveSettingsFrost = tab == AppTab.Settings,
                        ) {
                            content(tab)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 只约束功能页和设置页。聊天页保持原始满高尺寸，避免介入
 * FixedHeightOverflowSlot / OpenGL anchor / viewportTopInset 稳定链。
 */
@Composable
private fun BottomDockBoundedPageViewport(
    tab: AppTab,
    content: @Composable () -> Unit,
) {
    if (tab == AppTab.Assistant) {
        content()
        return
    }

    val boundaryVisible = rememberBottomDockBoundaryVisible()
    val bottomInset = if (boundaryVisible) BOTTOM_DOCK_CONTENT_BOUNDARY_INSET else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomInset),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipPageGlassVertically(),
        ) {
            content()
        }
    }
}

/**
 * 页面层只保留顶部和底部的真实可见区域限制。
 * 左右使用远大于任何设备宽度的有限裁剪范围，允许玻璃边缘光、阴影和折射越过内容边距，
 * 最终仍由 Activity 根视图和物理屏幕边界负责裁剪。
 */
private fun Modifier.clipPageGlassVertically(): Modifier = drawWithContent {
    clipRect(
        left = -PAGE_HORIZONTAL_UNBOUNDED_CLIP_PX,
        top = 0f,
        right = PAGE_HORIZONTAL_UNBOUNDED_CLIP_PX,
        bottom = size.height,
    ) {
        this@drawWithContent.drawContent()
    }
}

/**
 * 与 App.kt 的导航栏收起逻辑保持一致：键盘展开时撤销边界，
 * 键盘开始回落时立即恢复边界，避免导航栏先出现而内容仍穿到其下方。
 */
@Composable
private fun rememberBottomDockBoundaryVisible(): Boolean {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeOpenThresholdPx = with(density) {
        BOTTOM_DOCK_IME_OPEN_THRESHOLD.toPx()
    }.toInt()
    var boundaryVisible by remember(imeInsets, density, imeOpenThresholdPx) {
        mutableStateOf(imeInsets.getBottom(density) < imeOpenThresholdPx)
    }

    LaunchedEffect(imeInsets, density, imeOpenThresholdPx) {
        var previousImeBottomPx = imeInsets.getBottom(density)
        var dockCollapsed = previousImeBottomPx >= imeOpenThresholdPx
        boundaryVisible = !dockCollapsed

        snapshotFlow { imeInsets.getBottom(density) }.collect { imeBottomPx ->
            val retreating = imeBottomPx > 0 && imeBottomPx < previousImeBottomPx
            dockCollapsed = when {
                imeBottomPx == 0 || retreating -> false
                imeBottomPx >= imeOpenThresholdPx -> true
                else -> dockCollapsed
            }
            val nextBoundaryVisible = !dockCollapsed
            if (boundaryVisible != nextBoundaryVisible) {
                boundaryVisible = nextBoundaryVisible
            }
            previousImeBottomPx = imeBottomPx
        }
    }

    return boundaryVisible
}
