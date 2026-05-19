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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.SystemActionRouter
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.RenderQuality

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiAssistantNativeApp(viewModel: AssistantViewModel = viewModel()) {
    val state = viewModel.uiState
    val rootView = LocalView.current
    val context = LocalContext.current
    val actionRouter = remember(context) { (context as? Activity)?.let { SystemActionRouter(it) } }
    val backdropOrigin = remember { BackdropCoordinateSource() }
    val backdropTicker = remember { BackdropFrameTicker() }
    val glassRegistry = remember { GlassItemRegistry() }
    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importCustomBackground(uri)
    }
    val assistantImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onImagePickedForAssistant(uri) }
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

    LaunchedEffect(Unit) {
        while (true) {
            val frameTime = withFrameNanos { it }
            backdropTicker.frameNanos = frameTime
        }
    }

    MaterialTheme {
        Surface(color = Color(0xFF07132D), modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
                LocalGlassBackdrop provides GlassBackdropSpec(
                    quality = state.quality,
                    motionIntensity = state.motionIntensity,
                    theme = state.backgroundTheme,
                    params = state.backdropParams,
                    borderStyle = state.glassBorderStyle
                ),
                LocalBlurredBackdrop provides blurredBackdrop,
                LocalBackdropOrigin provides backdropOrigin,
                LocalBackdropFrameTicker provides backdropTicker,
                LocalGlassItemRegistry provides glassRegistry
            ) {
                Box(Modifier.fillMaxSize()) {
                    WeatherNightBackground(
                        quality = state.quality,
                        motionIntensity = state.motionIntensity,
                        theme = state.backgroundTheme,
                        params = state.backdropParams,
                        customBackgroundPath = state.customBackgroundPath,
                        modifier = Modifier
                            .fillMaxSize()
                            .onPlaced { backdropOrigin.coordinates = it }
                    )

                    UnifiedGlassBackdropLayer(Modifier.fillMaxSize())

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp)
                    ) {
                        when (state.currentTab) {
                            AppTab.Assistant -> AssistantScreen(
                                state = state,
                                onComposerChange = viewModel::updateComposer,
                                onSend = viewModel::submitComposer,
                                onQuickCommand = viewModel::sendUserCommand,
                                onDraftCommand = viewModel::insertCommandDraft,
                                onModelClick = viewModel::cycleModel,
                                onPickImage = {
                                    assistantImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onOpenTools = { viewModel.selectTab(AppTab.Tools) },
                                onOpenSettings = { viewModel.selectTab(AppTab.Settings) },
                                onNavigateHome = {
                                    val ok = actionRouter?.startNavigation("家") == true
                                    viewModel.appendAssistantNotice(if (ok) "已打开系统地图，开始导航到家。" else "没有可用的地图应用。")
                                },
                                onSetAlarm = {
                                    val ok = actionRouter?.setAlarm(21, 30, "AI 助手提醒：晚上复盘") == true
                                    viewModel.appendAssistantNotice(if (ok) "已打开系统闹钟，准备创建晚上复盘提醒。" else "无法打开系统闹钟。")
                                },
                                onToggleOnline = viewModel::toggleOnline
                            )
                            AppTab.Tools -> ToolsScreen(
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
                                onBackdropChange = viewModel::setBackdropDebugParams,
                                onBorderChange = viewModel::setGlassBorderStyle,
                                onUploadBackgroundClick = {
                                    backgroundPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearCustomBackgroundClick = viewModel::clearCustomBackground
                            )
                        }
                    }

                    BottomDockSeparationMist(
                        quality = state.quality,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )

                    CompactLiquidBottomBar(
                        currentTab = state.currentTab,
                        quality = state.quality,
                        glassIntensity = state.glassIntensity,
                        motionIntensity = state.motionIntensity,
                        onTabChange = viewModel::selectTab,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomDockSeparationMist(quality: RenderQuality, modifier: Modifier = Modifier) {
    val blur = if (quality.enableMotion) 14.dp else 0.dp
    val height = if (quality.enableMotion) 112.dp else 76.dp
    val bottomAlpha = if (quality.enableMotion) 0x88 else 0x5C
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .blur(blur)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x1A08142C),
                        Color(0x5208142C),
                        Color(red = 0x03, green = 0x08, blue = 0x17, alpha = bottomAlpha)
                    )
                )
            )
    )
}
