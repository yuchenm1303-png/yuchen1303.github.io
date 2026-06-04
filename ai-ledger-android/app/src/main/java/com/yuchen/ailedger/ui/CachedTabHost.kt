package com.yuchen.ailedger.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab
import kotlinx.coroutines.delay

val LocalPageActive = compositionLocalOf { true }
val LocalPageVisible = compositionLocalOf { true }
val LocalPageActivationTick = compositionLocalOf { 0 }
val LocalPageHeavyEffectsEnabled = compositionLocalOf { true }

private val DefaultPrewarmTabs: Set<AppTab> = AppTab.entries.toSet()
private const val DEFAULT_PREWARM_DELAY_MS = 5200L
private const val DEFAULT_PREWARM_STEP_DELAY_MS = 720L
private val PageEnterEasing = CubicBezierEasing(0.16f, 0.00f, 0.12f, 1.00f)
private val PageExitEasing = CubicBezierEasing(0.24f, 0.00f, 0.20f, 1.00f)

private data class PageMotionProfile(
    val enterMillis: Int,
    val exitMillis: Int,
    val enterOffsetDp: Float,
    val enterScale: Float,
    val heavyEffectsAlphaGate: Float,
    val transformOriginY: Float
)

private fun motionProfileFor(tab: AppTab): PageMotionProfile = when (tab) {
    AppTab.Assistant -> PageMotionProfile(
        enterMillis = 260,
        exitMillis = 110,
        enterOffsetDp = 10f,
        enterScale = 0.990f,
        heavyEffectsAlphaGate = 0.72f,
        transformOriginY = 0.58f
    )
    AppTab.Tools -> PageMotionProfile(
        enterMillis = 320,
        exitMillis = 120,
        enterOffsetDp = 16f,
        enterScale = 0.982f,
        heavyEffectsAlphaGate = 0.80f,
        transformOriginY = 0.46f
    )
    AppTab.Settings -> PageMotionProfile(
        enterMillis = 230,
        exitMillis = 100,
        enterOffsetDp = 8f,
        enterScale = 0.994f,
        heavyEffectsAlphaGate = 0.74f,
        transformOriginY = 0.40f
    )
}

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
    val orderedPrewarmTabs = remember(effectivePrewarmTabs, currentTab) {
        AppTab.entries.filter { tab -> tab in effectivePrewarmTabs && tab != currentTab }
    }
    val parentGlassBackdrop = LocalGlassBackdrop.current
    val parentBlurredBackdrop = LocalBlurredBackdrop.current
    val parentBackdropTicker = LocalBackdropFrameTicker.current
    val parentGlassRegistry = LocalGlassItemRegistry.current

    LaunchedEffect(currentTab) {
        renderedTabs = renderedTabs + currentTab
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
                val density = LocalDensity.current
                val profile = remember(tab) { motionProfileFor(tab) }
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (active) profile.enterMillis else profile.exitMillis,
                        easing = if (active) PageEnterEasing else PageExitEasing
                    ),
                    label = "tabAlpha-${tab.name}"
                )
                val offsetDp by animateFloatAsState(
                    targetValue = if (active) 0f else profile.enterOffsetDp,
                    animationSpec = tween(
                        durationMillis = if (active) profile.enterMillis + 40 else profile.exitMillis,
                        easing = if (active) PageEnterEasing else FastOutSlowInEasing
                    ),
                    label = "tabOffset-${tab.name}"
                )
                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else profile.enterScale,
                    animationSpec = tween(
                        durationMillis = if (active) profile.enterMillis + 60 else profile.exitMillis,
                        easing = if (active) PageEnterEasing else FastOutSlowInEasing
                    ),
                    label = "tabScale-${tab.name}"
                )
                val visibleDuringTransition = active || alpha > 0.001f
                val heavyEffectsEnabled = active && alpha >= profile.heavyEffectsAlphaGate && !diagnostics.openGlGlassOff

                CompositionLocalProvider(
                    LocalPageActive provides active,
                    LocalPageVisible provides visibleDuringTransition,
                    LocalPageActivationTick provides if (active) currentActivationTick else 0,
                    LocalPageHeavyEffectsEnabled provides heavyEffectsEnabled,
                    // Keep this false for Shell cards. In GlassPanel, a true viewport flag means
                    // an external OpenGL viewport owns the Shell, which intentionally skips the
                    // card-bound OpenGLGlassCardLayer. Our app uses single-card Shell OpenGL.
                    LocalOpenGLGlassViewportActive provides false,
                    LocalGlassBackdrop provides if (visibleDuringTransition) parentGlassBackdrop else null,
                    LocalBlurredBackdrop provides if (visibleDuringTransition) parentBlurredBackdrop else null,
                    LocalBackdropFrameTicker provides if (heavyEffectsEnabled) parentBackdropTicker else null,
                    LocalGlassItemRegistry provides if (heavyEffectsEnabled) parentGlassRegistry else null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (active) 1f else -1f)
                            .graphicsLayer {
                                this.alpha = alpha
                                translationY = with(density) { offsetDp.dp.toPx() }
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0.50f, profile.transformOriginY)
                            }
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
