package com.yuchen.ailedger.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.SystemActionRouter
import com.yuchen.ailedger.data.AssistantPreferencesStore
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.service.AgentOverlayService
import com.yuchen.ailedger.service.AgentRuntimeController
import com.yuchen.ailedger.service.ChatNotificationManager
import com.yuchen.ailedger.service.InstalledAppIndex
import com.yuchen.ailedger.service.MobileCommandParser
import com.yuchen.ailedger.ui.gl.OpenGLGlassProbeLayer

private const val COMPACT_DP_SCALE = 0.90f
private const val COMPACT_FONT_SCALE = 0.92f
private const val ENABLE_OPENGL_GLASS_PROBE = false
private const val SHOW_PERFORMANCE_DIAGNOSTICS_PANEL = false

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiAssistantNativeApp(viewModel: AssistantViewModel = viewModel()) {
    val state = viewModel.uiState
    val rootView = LocalView.current
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val preferencesStore = remember(context) { AssistantPreferencesStore(context.applicationContext) }
    val installedAppIndex = remember(context) { InstalledAppIndex(context.applicationContext) }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeOpenThresholdPx = with(density) { 48.dp.toPx() }.toInt()
    var previousImeBottomPx by remember { mutableStateOf(imeBottomPx) }
    var dockCollapsedByIme by remember { mutableStateOf(imeBottomPx >= imeOpenThresholdPx) }
    var diagnostics by remember { mutableStateOf(PerformanceDiagnosticsState()) }
    val effectiveMotionIntensity = if (diagnostics.continuousAnimationsOff) 0f else state.motionIntensity
    val imeHidden = imeBottomPx == 0
    val imeIsRetreating = imeBottomPx > 0 && imeBottomPx < previousImeBottomPx
    val nextDockCollapsedByIme = when {
        imeHidden || imeIsRetreating -> false
        imeBottomPx >= imeOpenThresholdPx -> true
        else -> dockCollapsedByIme
    }
    LaunchedEffect(imeBottomPx, nextDockCollapsedByIme) {
        dockCollapsedByIme = nextDockCollapsedByIme
        previousImeBottomPx = imeBottomPx
    }
    val bottomDockVisible = !nextDockCollapsedByIme
    val bottomDockClickable = imeHidden
    val assistantBottomPadding = if (bottomDockVisible) 68.dp else 8.dp
    val bottomBarOffsetY = if (bottomDockVisible) 0.dp else 24.dp
    val bottomBarAlpha = if (bottomDockVisible) 1f else 0f
    val compactDensity = remember(density.density, density.fontScale) {
        Density(density = density.density * COMPACT_DP_SCALE, fontScale = density.fontScale * COMPACT_FONT_SCALE)
    }
    val systemActionRouter = remember(context) { (context as? Activity)?.let { SystemActionRouter(it) } }
    var pendingMobileAction by remember { mutableStateOf<PendingMobileAction?>(null) }
    val currentMessages by rememberUpdatedState(state.messages)
    val commandSnapshot by rememberUpdatedState(
        MobileCommandSnapshot(
            composerText = state.composerText,
            isSending = state.isSending,
            navigationHomeAddress = state.navigationHomeAddress,
            navigationSchoolAddress = state.navigationSchoolAddress,
            navigationCompanyAddress = state.navigationCompanyAddress,
            navigationDormAddress = state.navigationDormAddress,
        )
    )
    val messageSideEffectKey = remember(state.messages) { ChatMessagesSideEffectKey.from(state.messages) }
    val visibleComposerText = remember(state.composerText) { visibleComposerTextForAssistant(state.composerText) }
    val assistantScreenState = rememberAssistantScreenState(state, effectiveMotionIntensity, visibleComposerText)
    val stockAndSettingsState = rememberMotionState(state, effectiveMotionIntensity)
    val glassBackdropSpec = remember(state.quality, effectiveMotionIntensity, state.backgroundTheme, state.backdropParams, state.glassBorderStyle) {
        GlassBackdropSpec(state.quality, effectiveMotionIntensity, state.backgroundTheme, state.backdropParams, state.glassBorderStyle)
    }
    val imageOnlyRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ChatNotificationManager.showPersistentChatEntry(context, currentMessages, force = true)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) ChatNotificationManager.showPersistentChatEntry(context, currentMessages, force = true)
            else notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ChatNotificationManager.showPersistentChatEntry(context, currentMessages, force = true)
        }
    }

    LaunchedEffect(messageSideEffectKey) {
        val messages = currentMessages
        ChatNotificationManager.showPersistentChatEntry(context, messages)
        syncAgentOverlayProgressFromMessages(context.applicationContext, messages)
        if (pendingMobileAction == null) parsePendingMobileActionFromLatestMessage(messages)?.let { pendingMobileAction = it }
        val update = parseCloudNavigationPreferenceUpdate(messages) ?: return@LaunchedEffect
        if (!isNavigationPreferenceAlreadySaved(commandSnapshot, update)) preferencesStore.setNavigationAddress(update.slot, update.address)
    }

    val runPendingMobileAction = remember(systemActionRouter, viewModel) {
        { quickReply: String ->
            val snapshot = commandSnapshot
            val pending = pendingMobileAction
            if (!snapshot.isSending && pending != null) {
                when (quickReply) {
                    "确认" -> {
                        val result = executeMobileCommand(systemActionRouter, pending.command)
                        pendingMobileAction = null
                        viewModel.acceptExecutedMobileCommand(quickReply, pending.command, result.first, result.second)
                    }
                    "取消" -> {
                        pendingMobileAction = null
                        viewModel.cancelMobileCommand(quickReply, pending.command)
                    }
                }
            }
        }
    }
    val submitOrRunLocalMobileCommand = remember(systemActionRouter, installedAppIndex, viewModel) {
        {
            val snapshot = commandSnapshot
            val text = snapshot.composerText.trim()
            val pending = pendingMobileAction
            when {
                text.isNotBlank() && !snapshot.isSending && pending != null && isConfirmMobileActionText(text) -> {
                    val result = executeMobileCommand(systemActionRouter, pending.command)
                    pendingMobileAction = null
                    viewModel.acceptExecutedMobileCommand(text, pending.command, result.first, result.second)
                }
                text.isNotBlank() && !snapshot.isSending && pending != null && isCancelMobileActionText(text) -> {
                    pendingMobileAction = null
                    viewModel.cancelMobileCommand(text, pending.command)
                }
                text.isNotBlank() && !snapshot.isSending && !text.startsWith(VISUAL_ATTACHMENT_STATUS_PREFIX) -> {
                    val command = parseInstalledAppOpenCommand(text, installedAppIndex)
                        ?: MobileCommandParser.parse(text)?.resolveNavigationAddress(snapshot)
                    if (command != null) {
                        pendingMobileAction = PendingMobileAction(originalText = text, command = command)
                        viewModel.previewMobileCommand(text, command)
                    } else {
                        viewModel.submitComposer()
                    }
                }
                else -> viewModel.submitComposer()
            }
        }
    }
    val backdropOrigin = remember { BackdropCoordinateSource() }
    val backdropTicker = remember { BackdropFrameTicker() }
    val glassRegistry = remember { GlassItemRegistry() }
    val activeRegistry = remember(diagnostics.openGlGlassOff, glassRegistry) {
        if (diagnostics.openGlGlassOff) null else glassRegistry
    }
    val backdropInvalidator = remember(backdropTicker) { BackdropFrameInvalidator(backdropTicker) }
    val glassScrollInvalidation = remember(backdropInvalidator) {
        object : NestedScrollConnection {
            private fun requestCoalescedFrame(force: Boolean = false) {
                backdropInvalidator.request(force = force)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available != Offset.Zero) requestCoalescedFrame()
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed != Offset.Zero || available != Offset.Zero) requestCoalescedFrame()
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available != Velocity.Zero) requestCoalescedFrame(force = true)
                return Velocity.Zero
            }
        }
    }
    DisposableEffect(backdropInvalidator) {
        onDispose { backdropInvalidator.dispose() }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importCustomBackground(uri)
    }
    val assistantImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePickedForAssistant(uri)
    }
    val onPickBackground = remember(backgroundPicker, imageOnlyRequest) {
        { backgroundPicker.launch(imageOnlyRequest) }
    }
    val onPickAssistantImage = remember(assistantImagePicker, imageOnlyRequest) {
        { assistantImagePicker.launch(imageOnlyRequest) }
    }
    val onOpenTools = remember(viewModel) { { viewModel.selectTab(AppTab.Tools) } }
    val onOpenSettings = remember(viewModel) { { viewModel.selectTab(AppTab.Settings) } }
    val onOpenAssistant = remember(viewModel) { { viewModel.selectTab(AppTab.Assistant) } }
    val onBottomTabChange = remember(bottomDockClickable, viewModel) {
        { tab: AppTab -> if (bottomDockClickable) viewModel.selectTab(tab) }
    }
    val onCopyMessage = remember(clipboardManager, context) {
        { text: String ->
            if (text.isNotBlank()) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("AI 回复", text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val blurredBackdrop = rememberStableBlurredBackdropBitmap(
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

    val rootBackdropScrollModifier = if (state.currentTab == AppTab.Assistant) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxSize().nestedScroll(glassScrollInvalidation)
    }

    MaterialTheme {
        Surface(color = Color(0xFF07132D), modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalPerformanceDiagnostics provides diagnostics,
                LocalOverscrollConfiguration provides null,
                LocalGlassBackdrop provides glassBackdropSpec,
                LocalBlurredBackdrop provides blurredBackdrop,
                LocalBackdropOrigin provides backdropOrigin,
                LocalBackdropFrameTicker provides backdropTicker,
                LocalGlassItemRegistry provides activeRegistry,
                LocalRainbowPrismStyle provides state.rainbowPrismStyle,
                LocalMobileCommandQuickReply provides runPendingMobileAction
            ) {
                Box(rootBackdropScrollModifier) {
                    WeatherNightBackground(
                        quality = state.quality,
                        motionIntensity = effectiveMotionIntensity,
                        theme = state.backgroundTheme,
                        params = state.backdropParams,
                        customBackgroundPath = state.customBackgroundPath,
                        modifier = Modifier.fillMaxSize().onPlaced { backdropOrigin.coordinates = it }
                    )
                    if (!ENABLE_OPENGL_GLASS_PROBE && !diagnostics.openGlGlassOff) UnifiedGlassBackdropLayer(Modifier.fillMaxSize())
                    OpenGLGlassProbeLayer(enabled = ENABLE_OPENGL_GLASS_PROBE && !diagnostics.openGlGlassOff, modifier = Modifier.fillMaxSize())
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
                            CachedAppTabHost(currentTab = state.currentTab, modifier = Modifier.fillMaxSize()) { tab ->
                                when (tab) {
                                    AppTab.Assistant -> {
                                        Box(Modifier.fillMaxSize()) {
                                            val assistantHeavyEffectsEnabled = LocalPageHeavyEffectsEnabled.current
                                            val stagedAssistantScreenState = remember(
                                                assistantScreenState,
                                                assistantHeavyEffectsEnabled,
                                                effectiveMotionIntensity
                                            ) {
                                                assistantScreenState.copy(
                                                    motionIntensity = if (assistantHeavyEffectsEnabled) effectiveMotionIntensity else 0f
                                                )
                                            }
                                            AssistantScreenV2(
                                                state = stagedAssistantScreenState,
                                                bottomPadding = assistantBottomPadding,
                                                onComposerChange = viewModel::updateComposer,
                                                onSend = submitOrRunLocalMobileCommand,
                                                onStopGenerating = viewModel::stopGenerating,
                                                onDraftCommand = viewModel::insertCommandDraft,
                                                onModelSelected = viewModel::selectModel,
                                                onPickImage = onPickAssistantImage,
                                                onOpenTools = onOpenTools,
                                                onOpenSettings = onOpenSettings,
                                                onToggleOnline = viewModel::toggleOnline,
                                                onCopyMessage = onCopyMessage,
                                                onRetryMessage = viewModel::retryMessage
                                            )
                                            AgentChatHeaderOverlay(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(start = 68.dp, top = 222.dp)
                                                    .zIndex(1600f)
                                            )
                                        }
                                    }
                                    AppTab.Tools -> {
                                        if (state.selectedToolTitle == STOCK_MARKET_TOOL_TITLE) {
                                            AStockMarketScreenV2(
                                                state = stockAndSettingsState,
                                                onBack = viewModel::closeTool,
                                                onOpenAssistant = onOpenAssistant
                                            )
                                        } else {
                                            StockFirstToolsHomeScreen(
                                                state = stockAndSettingsState,
                                                onOpenTool = viewModel::openTool
                                            )
                                        }
                                    }
                                    AppTab.Settings -> SettingsPolishedScreen(
                                        state = stockAndSettingsState,
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
                                        onUploadBackgroundClick = onPickBackground,
                                        onClearCustomBackgroundClick = viewModel::clearCustomBackground
                                    )
                                }
                            }
                            if (SHOW_PERFORMANCE_DIAGNOSTICS_PANEL) {
                                PerformanceDiagnosticsPanel(
                                    state = diagnostics,
                                    onStateChange = { diagnostics = it },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 76.dp, start = 4.dp)
                                        .zIndex(2000f)
                                )
                            }
                        }
                        PrismaticCapsuleBottomBar(
                            currentTab = state.currentTab,
                            quality = state.quality,
                            glassIntensity = state.glassIntensity,
                            motionIntensity = effectiveMotionIntensity,
                            onTabChange = onBottomTabChange,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .graphicsLayer {
                                    alpha = bottomBarAlpha
                                    translationY = bottomBarOffsetY.toPx()
                                }
                                .zIndex(1000f)
                        )
                    }
                }
            }
        }
    }
}

private fun syncAgentOverlayProgressFromMessages(context: Context, messages: List<ChatMessage>) {
    val latestAgent = messages.lastOrNull { it.source == "local_agent" } ?: return
    val latestUserText = messages.lastOrNull { it.role == MessageRole.User }?.text.orEmpty().trim()
    when (latestAgent.status) {
        MessageStatus.Sending -> {
            AgentRuntimeController.startTask(latestUserText.ifBlank { "手机智能体任务" })
            if (AgentOverlayService.canDrawOverlays(context)) AgentOverlayService.ensureStarted(context)
        }
        MessageStatus.Failed -> {
            AgentRuntimeController.failTask(latestAgent.errorText ?: latestAgent.text)
        }
        MessageStatus.Sent -> {
            if (latestAgent.text.startsWith("手机智能体任务执行")) {
                val completed = latestAgent.text.contains("状态：已完成")
                val result = latestAgent.text.lineSequence().firstOrNull { it.startsWith("结果：") }?.removePrefix("结果：")
                    ?: latestAgent.text.take(80)
                AgentRuntimeController.finishTask(result, completed)
            }
        }
    }
}

@Composable
private fun rememberAssistantScreenState(
    state: AssistantUiState,
    effectiveMotionIntensity: Float,
    visibleComposerText: String,
): AssistantHomeUiState {
    return remember(
        state.quality,
        state.glassIntensity,
        effectiveMotionIntensity,
        state.rainbowPrismStyle,
        state.modelCardGlassStyle,
        state.messages,
        visibleComposerText,
        state.composerAttachments,
        state.selectedModel,
        state.selectedModelLabel,
        state.onlineEnabled,
        state.isSending,
    ) {
        AssistantHomeUiState(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = effectiveMotionIntensity,
            rainbowPrismStyle = state.rainbowPrismStyle,
            modelCardGlassStyle = state.modelCardGlassStyle,
            messages = state.messages,
            composerText = visibleComposerText,
            composerAttachments = state.composerAttachments,
            selectedModel = state.selectedModel,
            selectedModelLabel = state.selectedModelLabel,
            onlineEnabled = state.onlineEnabled,
            isSending = state.isSending
        )
    }
}

@Composable
private fun rememberMotionState(state: AssistantUiState, effectiveMotionIntensity: Float): AssistantUiState {
    return remember(
        state.currentTab,
        state.quality,
        state.showPreviewConversation,
        state.glassPreset,
        state.backgroundTheme,
        state.customBackgroundPath,
        state.glassIntensity,
        effectiveMotionIntensity,
        state.rainbowPrismStyle,
        state.modelCardGlassStyle,
        state.backdropParams,
        state.glassBorderStyle,
        state.navigationHomeAddress,
        state.navigationSchoolAddress,
        state.navigationCompanyAddress,
        state.navigationDormAddress,
        state.stats,
        state.tools,
        state.selectedModel,
        state.selectedModelLabel,
        state.onlineEnabled,
        state.isSending,
        state.selectedToolTitle,
        state.ledgerRecords,
        state.ledgerBudgetText,
        state.ledgerDraftTitle,
        state.ledgerDraftAmount,
        state.ledgerDraftType,
        state.ledgerDraftCategory,
    ) {
        state.copy(motionIntensity = effectiveMotionIntensity)
    }
}
