package com.yuchen.ailedger.ui

import android.app.Activity
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.SystemActionRouter
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.ui.gl.OpenGLGlassProbeLayer

private const val COMPACT_DP_SCALE = 0.90f
private const val COMPACT_FONT_SCALE = 0.92f
private const val ENABLE_OPENGL_GLASS_PROBE = false

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiAssistantNativeApp(viewModel: AssistantViewModel = viewModel()) {
    val state = viewModel.uiState
    val rootView = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val compactDensity = remember(density.density, density.fontScale) {
        Density(density = density.density * COMPACT_DP_SCALE, fontScale = density.fontScale * COMPACT_FONT_SCALE)
    }
    remember(context) { (context as? Activity)?.let { SystemActionRouter(it) } }
    val backdropOrigin = remember { BackdropCoordinateSource() }
    val backdropTicker = remember { BackdropFrameTicker() }
    val glassRegistry = remember { GlassItemRegistry() }
    val glassScrollInvalidation = remember(backdropTicker) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available != Offset.Zero) backdropTicker.requestFrame()
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed != Offset.Zero || available != Offset.Zero) backdropTicker.requestFrame()
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available != Velocity.Zero) backdropTicker.requestFrame(force = true)
                return Velocity.Zero
            }
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importCustomBackground(uri)
    }
    val assistantImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePickedForAssistant(uri)
    }
    val blurredBackdrop = rememberBlurredBackdropBitmap(
        theme = state.backgroundTheme,
        quality = state.quality,
        params = state.backdropParams,
        customBackgroundPath = state.customBackgroundPath
    )

    DisposableEffect(rootView) {
        val oldOverscrollMode = rootView.overScrollMode
        rootView.overScrollMode = View.OVER_SCROLL_NEVER
        onDispose { rootView.overScrollMode = oldOverscrollMode }
    }

    MaterialTheme {
        Surface(color = Color(0xFF07132D), modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
                LocalGlassBackdrop provides GlassBackdropSpec(state.quality, state.motionIntensity, state.backgroundTheme, state.backdropParams, state.glassBorderStyle),
                LocalBlurredBackdrop provides blurredBackdrop,
                LocalBackdropOrigin provides backdropOrigin,
                LocalBackdropFrameTicker provides backdropTicker,
                LocalGlassItemRegistry provides glassRegistry,
                LocalRainbowPrismStyle provides state.rainbowPrismStyle
            ) {
                Box(Modifier.fillMaxSize().nestedScroll(glassScrollInvalidation)) {
                    WeatherNightBackground(
                        quality = state.quality,
                        motionIntensity = state.motionIntensity,
                        theme = state.backgroundTheme,
                        params = state.backdropParams,
                        customBackgroundPath = state.customBackgroundPath,
                        modifier = Modifier.fillMaxSize().onPlaced { backdropOrigin.coordinates = it }
                    )
                    if (!ENABLE_OPENGL_GLASS_PROBE) UnifiedGlassBackdropLayer(Modifier.fillMaxSize())
                    OpenGLGlassProbeLayer(enabled = ENABLE_OPENGL_GLASS_PROBE, modifier = Modifier.fillMaxSize())
                    CompositionLocalProvider(LocalDensity provides compactDensity) {
                        CachedAppTabHost(
                            currentTab = state.currentTab,
                            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)
                        ) { tab ->
                            when (tab) {
                                AppTab.Assistant -> AssistantScreenV2(
                                    state = state,
                                    onComposerChange = viewModel::updateComposer,
                                    onSend = viewModel::submitComposer,
                                    onDraftCommand = viewModel::insertCommandDraft,
                                    onModelSelected = viewModel::selectModel,
                                    onPickImage = { assistantImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onOpenTools = { viewModel.selectTab(AppTab.Tools) },
                                    onOpenSettings = { viewModel.selectTab(AppTab.Settings) },
                                    onToggleOnline = viewModel::toggleOnline
                                )
                                AppTab.Tools -> ToolsScreenV2(
                                    state = state,
                                    onOpenTool = viewModel::openTool,
                                    onBack = viewModel::closeTool,
                                    onLedgerTitleChange = viewModel::updateLedgerDraftTitle,
                                    onLedgerAmountChange = viewModel::updateLedgerDraftAmount,
                                    onLedgerTypeChange = viewModel::selectLedgerDraftType,
                                    onLedgerCategoryChange = viewModel::selectLedgerCategory,
                                    onLedgerBudgetChange = viewModel::updateLedgerBudget,
                                    onAddLedgerRecord = viewModel::addLedgerRecord,
                                    onDeleteLedgerRecord = viewModel::deleteLedgerRecord,
                                    onOpenAssistant = { viewModel.selectTab(AppTab.Assistant) }
                                )
                                AppTab.Settings -> SettingsPolishedScreen(
                                    state = state,
                                    aiEndpoint = viewModel.aiEndpoint,
                                    onQualityChange = viewModel::selectQuality,
                                    onPreviewConversationChange = viewModel::setShowPreviewConversation,
                                    onGlassPresetChange = viewModel::setGlassPreset,
                                    onBackgroundThemeChange = viewModel::setBackgroundTheme,
                                    onGlassIntensityChange = viewModel::setGlassIntensity,
                                    onMotionIntensityChange = viewModel::setMotionIntensity,
                                    onRainbowPrismChange = viewModel::setRainbowPrismStyle,
                                    onBackdropChange = viewModel::setBackdropDebugParams,
                                    onBorderChange = viewModel::setGlassBorderStyle,
                                    onUploadBackgroundClick = { backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onClearCustomBackgroundClick = viewModel::clearCustomBackground
                                )
                            }
                        }
                        BottomDockSeparationMist(state.quality, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                        PrismaticCapsuleBottomBar(
                            currentTab = state.currentTab,
                            quality = state.quality,
                            glassIntensity = state.glassIntensity,
                            motionIntensity = state.motionIntensity,
                            onTabChange = viewModel::selectTab,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomDockSeparationMist(quality: RenderQuality, modifier: Modifier = Modifier) {
    val blur = if (quality.enableMotion) 10.dp else 0.dp
    val height = if (quality.enableMotion) 86.dp else 64.dp
    val bottomAlpha = if (quality.enableMotion) 0x72 else 0x50
    Box(
        modifier = modifier.fillMaxWidth().height(height).blur(blur).background(
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color(0x1208142C),
                    Color(0x3E08142C),
                    Color(red = 0x03, green = 0x08, blue = 0x17, alpha = bottomAlpha)
                )
            )
        )
    )
}
