package com.yuchen.ailedger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppPageActivity
import com.yuchen.ailedger.model.AppTab
import kotlinx.coroutines.delay

val LocalPageActive = compositionLocalOf { true }
val LocalPageVisible = compositionLocalOf { true }
val LocalPageLeaving = compositionLocalOf { false }
val LocalPageActivationTick = compositionLocalOf { 0 }
val LocalPageHeavyEffectsEnabled = compositionLocalOf { true }

private val DefaultPrewarmTabs: Set<AppTab> = emptySet()
private const val DEFAULT_PREWARM_DELAY_MS = 5200L
private const val DEFAULT_PREWARM_STEP_DELAY_MS = 720L
private const val PAGE_ENTER_FADE_MS = 210
private const val PAGE_EXIT_FADE_MS = 150
private const val PAGE_ENTER_OFFSET_DP = 8f
private const val PAGE_EXIT_OFFSET_DP = -4f
private const val PAGE_MIN_SCALE = 0.992f
private const val PAGE_HIDDEN_ALPHA_EPSILON = 0.001f

private fun AppTab.cacheBit(): Int = 1 shl ordinal

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
    val initialTab = remember { currentTab }
    var renderedTabMask by remember { mutableIntStateOf(currentTab.cacheBit()) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val activationCounter = remember { intArrayOf(0) }
    val activationTicks = remember { IntArray(AppTab.entries.size) }
    remember(currentTab) {
        val next = if (activationCounter[0] == Int.MAX_VALUE) 1 else activationCounter[0] + 1
        activationCounter[0] = next
        activationTicks[currentTab.ordinal] = next
        next
    }
    var heavyEffectsReady by remember { mutableStateOf(false) }
    val orderedPrewarmTabs = remember(effectivePrewarmTabs, currentTab) {
        if (effectivePrewarmTabs.isEmpty()) {
            emptyList()
        } else {
            AppTab.entries.filter { tab ->
                tab in effectivePrewarmTabs && tab != currentTab && tab != AppTab.Settings
            }
        }
    }
    val parentGlassBackdrop = LocalGlassBackdrop.current
    val parentBlurredBackdrop = LocalBlurredBackdrop.current
    val parentBackdropTicker = LocalBackdropFrameTicker.current
    val parentGlassRegistry = LocalGlassItemRegistry.current

    // 冷启动重效果门只允许打开一次。旧实现会在每次切页时先关闭再重新打开，
    // 导致设置页批绘制 registry 被移除后重新注册，看起来像整页自动刷新。
    LaunchedEffect(Unit) {
        StartupPerformanceGate.awaitPostBackdropStability()
        heavyEffectsReady = true
        StartupPerformanceGate.markFullEffectsReady()
    }

    LaunchedEffect(currentTab) {
        AppPageActivity.update(currentTab)
        val tabBit = currentTab.cacheBit()
        var nextMask = renderedTabMask or tabBit
        if (currentTab != AppTab.Settings) {
            nextMask = nextMask and AppTab.Settings.cacheBit().inv()
        }
        if (nextMask != renderedTabMask) renderedTabMask = nextMask
    }

    LaunchedEffect(orderedPrewarmTabs, prewarmDelayMs, prewarmStepDelayMs, diagnostics.pagePrewarmOff) {
        if (diagnostics.pagePrewarmOff) {
            StartupMetrics.setWarmupState("页面预热已禁用")
            return@LaunchedEffect
        }
        if (orderedPrewarmTabs.isEmpty()) {
            return@LaunchedEffect
        }
        if (prewarmDelayMs > 0L) delay(prewarmDelayMs)
        orderedPrewarmTabs.forEachIndexed { index, tab ->
            StartupMetrics.setWarmupState("轻量预热 ${tab.name} ${index + 1}/${orderedPrewarmTabs.size}")
            val tabBit = tab.cacheBit()
            if (renderedTabMask and tabBit == 0) renderedTabMask = renderedTabMask or tabBit
            if (prewarmStepDelayMs > 0L) delay(prewarmStepDelayMs)
        }
        StartupMetrics.setWarmupState("页面已轻量预热")
    }

    Box(modifier) {
        AppTab.entries.forEach { tab ->
            val usesPageHostTransition = tab != AppTab.Settings
            val keptForExit = usesPageHostTransition && renderedTabMask and tab.cacheBit() != 0

            // 设置页包含 TextureView OpenGL Shell：当前即挂载、离开即释放，禁止把它保留在
            // 整页 alpha/scale/translation RenderNode 中。其他页面继续保留原有淡入淡出。
            if (tab == currentTab || keptForExit) {
                val active = tab == currentTab
                val activationKey = activationTicks[tab.ordinal]
                val initialAlpha = if (
                    !usesPageHostTransition || (tab == initialTab && activationKey == 1)
                ) 1f else 0f
                val alphaState = remember(tab) { Animatable(initialAlpha) }

                LaunchedEffect(active, usesPageHostTransition) {
                    if (!usesPageHostTransition) {
                        alphaState.snapTo(1f)
                        return@LaunchedEffect
                    }
                    alphaState.animateTo(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (active) PAGE_ENTER_FADE_MS else PAGE_EXIT_FADE_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                    if (!active && alphaState.value <= PAGE_HIDDEN_ALPHA_EPSILON) {
                        renderedTabMask = renderedTabMask and tab.cacheBit().inv()
                    }
                }

                val alpha = if (usesPageHostTransition) alphaState.value else 1f
                val visibleDuringTransition = if (usesPageHostTransition) {
                    active || alpha > PAGE_HIDDEN_ALPHA_EPSILON
                } else {
                    active
                }
                val leaving = usesPageHostTransition && !active && visibleDuringTransition
                // 离场期间保持原激活序号，避免子页面把整套卡片入退场动画重新启动。
                val pageHeavyEffectsReady = active && heavyEffectsReady
                val visualEffectsEnabled = visibleDuringTransition && pageHeavyEffectsReady && !diagnostics.openGlGlassOff
                val liveRegistryEnabled = pageHeavyEffectsReady && !diagnostics.openGlGlassOff
                val sceneGroup = tab.defaultGlassSceneGroup()
                val ordinaryRenderMode = if (visibleDuringTransition) {
                    OrdinaryGlassParentDrawController.renderModeFor(sceneGroup)
                } else {
                    OrdinaryGlassRenderMode.Shadow
                }
                val basePageModifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (active) 1f else -1f)
                val pageModifier = if (usesPageHostTransition) {
                    val pageOffsetDp = if (active) PAGE_ENTER_OFFSET_DP else PAGE_EXIT_OFFSET_DP
                    val pageScale = PAGE_MIN_SCALE + (1f - PAGE_MIN_SCALE) * alpha
                    basePageModifier.graphicsLayer {
                        this.alpha = alpha
                        translationY = pageOffsetDp.dp.toPx() * (1f - alpha)
                        scaleX = pageScale
                        scaleY = pageScale
                    }
                } else {
                    basePageModifier
                }

                OrdinaryGlassSceneHost(
                    group = sceneGroup,
                    modifier = pageModifier,
                    renderMode = ordinaryRenderMode
                ) {
                    saveableStateHolder.SaveableStateProvider(tab.name) {
                        CompositionLocalProvider(
                            // 页面淡出交给最外层统一完成。子页面在完全不可见后才收到 inactive，
                            // 避免数十个 AnimatedVisibility 与整页淡出同时执行。
                            LocalPageActive provides visibleDuringTransition,
                            LocalPageVisible provides visibleDuringTransition,
                            LocalPageLeaving provides leaving,
                            LocalPageActivationTick provides activationKey,
                            LocalPageHeavyEffectsEnabled provides visualEffectsEnabled,
                            LocalOpenGLGlassViewportActive provides false,
                            LocalGlassBackdrop provides (if (visibleDuringTransition) parentGlassBackdrop else null),
                            LocalBlurredBackdrop provides (if (visibleDuringTransition) parentBlurredBackdrop else null),
                            LocalBackdropFrameTicker provides (if (visualEffectsEnabled) parentBackdropTicker else null),
                            LocalGlassItemRegistry provides (if (liveRegistryEnabled) parentGlassRegistry else null)
                        ) {
                            key(tab) {
                                if (tab == AppTab.Settings) {
                                    SettingsComposeGlassBatchHost(Modifier.fillMaxSize()) {
                                        InsetGlassSliderBatchGroup(Modifier.fillMaxSize()) {
                                            content(tab)
                                        }
                                    }
                                } else {
                                    content(tab)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
