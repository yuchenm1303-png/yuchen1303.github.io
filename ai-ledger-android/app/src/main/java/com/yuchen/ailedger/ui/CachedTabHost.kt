package com.yuchen.ailedger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab
import kotlinx.coroutines.delay

val LocalPageActive = compositionLocalOf { true }
val LocalPageVisible = compositionLocalOf { true }
val LocalPageLeaving = compositionLocalOf { false }
val LocalPageActivationTick = compositionLocalOf { 0 }
val LocalPageHeavyEffectsEnabled = compositionLocalOf { true }

private val DefaultPrewarmTabs: Set<AppTab> = AppTab.entries.toSet()
private const val DEFAULT_PREWARM_DELAY_MS = 5200L
private const val DEFAULT_PREWARM_STEP_DELAY_MS = 720L
private const val PAGE_ENTER_FADE_MS = 230
private const val PAGE_EXIT_FADE_MS = 160
private const val HEAVY_EFFECTS_REVEAL_MS = 180L

@Composable
fun CachedAppTabHost(
    currentTab: AppTab,
    modifier: Modifier = Modifier,
    prewarmTabs: Set<AppTab> = DefaultPrewarmTabs,
    prewarmDelayMs: Long = DEFAULT_PREWARM_DELAY_MS,
    prewarmStepDelayMs: Long = DEFAULT_PREWARM_STEP_DELAY_MS,
    content: @Composable (AppTab) -> Unit
) {
    val diagnostics = LocalPerformanceDiagnostics.current
    val effectivePrewarmTabs = if (diagnostics.pagePrewarmOff) emptySet() else prewarmTabs
    var renderedTabs by remember { mutableStateOf(setOf(currentTab)) }
    val currentActivationTick = remember(currentTab) { (System.nanoTime() and Int.MAX_VALUE.toLong()).toInt() }
    var heavyEffectsReadyTick by remember { mutableStateOf(currentActivationTick) }
    val orderedPrewarmTabs = remember(effectivePrewarmTabs, currentTab) {
        AppTab.entries.filter { tab -> tab in effectivePrewarmTabs && tab != currentTab }
    }
    val parentGlassBackdrop = LocalGlassBackdrop.current
    val parentBlurredBackdrop = LocalBlurredBackdrop.current
    val parentBackdropTicker = LocalBackdropFrameTicker.current
    val parentGlassRegistry = LocalGlassItemRegistry.current

    LaunchedEffect(currentTab, currentActivationTick) {
        renderedTabs = renderedTabs + currentTab
        heavyEffectsReadyTick = 0
        if (HEAVY_EFFECTS_REVEAL_MS > 0L) delay(HEAVY_EFFECTS_REVEAL_MS)
        heavyEffectsReadyTick = currentActivationTick
    }

    LaunchedEffect(orderedPrewarmTabs, prewarmDelayMs, prewarmStepDelayMs, diagnostics.pagePrewarmOff) {
        if (diagnostics.pagePrewarmOff) {
            StartupMetrics.setWarmupState("页面预热已禁用")
            return@LaunchedEffect
        }
        if (orderedPrewarmTabs.isEmpty()) {
            StartupMetrics.setWarmupState("按需页面加载")
            return@LaunchedEffect
        }
        StartupMetrics.setWarmupState("首页稳定中")
        if (prewarmDelayMs > 0L) delay(prewarmDelayMs)
        orderedPrewarmTabs.forEachIndexed { index, tab ->
            StartupMetrics.setWarmupState("轻量预热 ${tab.name} ${index + 1}/${orderedPrewarmTabs.size}")
            renderedTabs = renderedTabs + tab
            if (prewarmStepDelayMs > 0L) delay(prewarmStepDelayMs)
        }
        StartupMetrics.setWarmupState("页面已轻量预热")
    }

    Box(modifier) {
        AppTab.entries.forEach { tab ->
            if (tab in renderedTabs) {
                val active = tab == currentTab
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(durationMillis = if (active) PAGE_ENTER_FADE_MS else PAGE_EXIT_FADE_MS),
                    label = "tabAlpha-${tab.name}"
                )
                val visibleDuringTransition = active || alpha > 0.001f
                val leaving = !active && visibleDuringTransition
                val activationKey = if (active) currentActivationTick else 0
                val heavyEffectsReady = active && heavyEffectsReadyTick == activationKey
                val visualEffectsEnabled = visibleDuringTransition && !diagnostics.openGlGlassOff
                val liveRegistryEnabled = heavyEffectsReady && !diagnostics.openGlGlassOff

                OrdinaryGlassSceneHost(
                    group = tab.defaultGlassSceneGroup(),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (active) 1f else -1f)
                        .graphicsLayer { this.alpha = alpha }
                ) {
                    CompositionLocalProvider(
                        LocalPageActive provides active,
                        LocalPageVisible provides visibleDuringTransition,
                        LocalPageLeaving provides leaving,
                        LocalPageActivationTick provides activationKey,
                        LocalPageHeavyEffectsEnabled provides (visualEffectsEnabled && heavyEffectsReady),
                        // Keep this false for Shell cards. A true viewport flag means an external
                        // OpenGL viewport owns the Shell, which skips the single-card Shell layer.
                        LocalOpenGLGlassViewportActive provides false,
                        LocalGlassBackdrop provides (if (visibleDuringTransition) parentGlassBackdrop else null),
                        LocalBlurredBackdrop provides (if (visibleDuringTransition) parentBlurredBackdrop else null),
                        LocalBackdropFrameTicker provides (if (visualEffectsEnabled) parentBackdropTicker else null),
                        LocalGlassItemRegistry provides (if (liveRegistryEnabled) parentGlassRegistry else null)
                    ) {
                        key(tab) {
                            content(tab)
                        }
                    }
                }
            }
        }
    }
}
