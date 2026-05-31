package com.yuchen.ailedger.ui

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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.yuchen.ailedger.model.AppTab
import kotlinx.coroutines.delay

val LocalPageActive = compositionLocalOf { true }
val LocalPageActivationTick = compositionLocalOf { 0 }

private val DefaultPrewarmTabs: Set<AppTab> = AppTab.entries.toSet()
private const val DEFAULT_PREWARM_DELAY_MS = 360L
private const val DEFAULT_PREWARM_STEP_DELAY_MS = 96L

@Composable
fun CachedAppTabHost(
    currentTab: AppTab,
    modifier: Modifier = Modifier,
    prewarmTabs: Set<AppTab> = DefaultPrewarmTabs,
    prewarmDelayMs: Long = DEFAULT_PREWARM_DELAY_MS,
    prewarmStepDelayMs: Long = DEFAULT_PREWARM_STEP_DELAY_MS,
    content: @Composable (AppTab) -> Unit
) {
    var visitedTabs by remember { mutableStateOf(setOf(currentTab)) }
    val activationTicks = remember {
        mutableStateMapOf<AppTab, Int>().apply {
            AppTab.entries.forEach { put(it, 0) }
        }
    }
    val orderedPrewarmTabs = remember(prewarmTabs) {
        AppTab.entries.filter { tab -> tab in prewarmTabs }
    }

    LaunchedEffect(currentTab) {
        if (currentTab !in visitedTabs) {
            visitedTabs = visitedTabs + currentTab
        }
        activationTicks[currentTab] = (activationTicks[currentTab] ?: 0) + 1
    }

    LaunchedEffect(orderedPrewarmTabs, prewarmDelayMs, prewarmStepDelayMs) {
        if (prewarmDelayMs > 0L) delay(prewarmDelayMs)
        orderedPrewarmTabs.forEach { tab ->
            visitedTabs = visitedTabs + tab
            if (prewarmStepDelayMs > 0L) delay(prewarmStepDelayMs)
        }
    }

    Box(modifier) {
        AppTab.entries.forEach { tab ->
            if (tab in visitedTabs) {
                val active = tab == currentTab
                val density = LocalDensity.current
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (active) 180 else 90,
                        easing = FastOutSlowInEasing
                    ),
                    label = "tabAlpha-${tab.name}"
                )
                val offsetDp by animateFloatAsState(
                    targetValue = if (active) 0f else 8f,
                    animationSpec = tween(
                        durationMillis = if (active) 220 else 90,
                        easing = FastOutSlowInEasing
                    ),
                    label = "tabOffset-${tab.name}"
                )

                CompositionLocalProvider(
                    LocalPageActive provides active,
                    LocalPageActivationTick provides (activationTicks[tab] ?: 0),
                    LocalGlassItemRegistry provides if (active) LocalGlassItemRegistry.current else null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (active) 1f else -1f)
                            .graphicsLayer {
                                this.alpha = alpha
                                translationY = with(density) { offsetDp.toDp().toPx() }
                            }
                    ) {
                        content(tab)
                    }
                }
            }
        }
    }
}
