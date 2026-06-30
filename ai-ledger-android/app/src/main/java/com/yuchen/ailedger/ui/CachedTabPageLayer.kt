package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab

private const val PAGE_ENTER_FADE_MS = 210
private const val PAGE_EXIT_FADE_MS = 150
private const val PAGE_ENTER_OFFSET_DP = 8f
private const val PAGE_EXIT_OFFSET_DP = -4f
private const val PAGE_MIN_SCALE = 0.992f
private const val PAGE_HIDDEN_ALPHA_EPSILON = 0.001f

@Composable
internal fun CachedTabPageLayer(
    tab: AppTab,
    currentTab: AppTab,
    initialTab: AppTab,
    activationKey: Int,
    heavyEffectsReady: Boolean,
    diagnostics: PerformanceDiagnosticsState,
    parentGlassBackdrop: GlassBackdropSpec?,
    parentBlurredBackdrop: BlurredBackdropBitmap?,
    parentBackdropTicker: BackdropFrameTicker?,
    saveableStateHolder: SaveableStateHolder,
    onHidden: () -> Unit,
    content: @Composable (AppTab) -> Unit,
) {
    val active = tab == currentTab
    val initialAlpha = if (tab == initialTab && activationKey == 1) 1f else 0f
    val alphaState = remember(tab) { Animatable(initialAlpha) }

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
    val sceneGroup = tab.defaultGlassSceneGroup()
    val ordinaryRenderMode = if (visibleDuringTransition) {
        OrdinaryGlassParentDrawController.renderModeFor(sceneGroup)
    } else {
        OrdinaryGlassRenderMode.Shadow
    }
    val pageOffsetDp = if (active) PAGE_ENTER_OFFSET_DP else PAGE_EXIT_OFFSET_DP
    val pageScale = PAGE_MIN_SCALE + (1f - PAGE_MIN_SCALE) * alpha

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
                LocalBackdropFrameTicker provides if (visualEffectsEnabled) parentBackdropTicker else null,
            ) {
                key(tab) {
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
