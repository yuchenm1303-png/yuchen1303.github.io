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
