package com.yuchen.ailedger.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.zIndex
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
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
    var assistantEntranceKey by remember { mutableStateOf(0) }
    LaunchedEffect(state.currentTab) {
        if (state.currentTab != AppTab.Assistant) assistantEntranceKey += 1
    }
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
                        Box(
                            modifier = Modifier
                                .zIndex(0f)
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 12.dp)
                        ) {
                            when (state.currentTab) {
                                AppTab.Assistant -> AssistantScreenV2(
                                    state = state,
                                    entranceKey = assistantEntranceKey,
                                    onComposerChange = viewModel::updateComposer,
                                    onSend = viewModel::submitComposer,
                                    onStopGenerating = viewModel::stopGenerating,
                                    onDraftCommand = viewModel::insertCommandDraft,
                                    onModelSelected = viewModel::selectModel,
                                    onPickImage = { assistantImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onOpenTools = { viewModel.selectTab(AppTab.Tools) },
                                    onOpenSettings = { viewModel.selectTab(AppTab.Settings) },
                                    onToggleOnline = viewModel::toggleOnline,
                                    onCopyMessage = { text ->
                                        if (text.isNotBlank()) {
                                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("AI 回复", text))
                                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onRetryMessage = viewModel::retryMessage
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
                        if (!isKeyboardOpen) {
                            PrismaticCapsuleBottomBar(
                                currentTab = state.currentTab,
                                quality = state.quality,
                                glassIntensity = state.glassIntensity,
                                motionIntensity = state.motionIntensity,
                                onTabChange = viewModel::selectTab,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .zIndex(1000f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomDockSeparationMist(quality: RenderQuality, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(0.dp))
    quality.hashCode()
}
