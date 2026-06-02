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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.SystemActionRouter
import com.yuchen.ailedger.data.AssistantPreferencesStore
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.service.InstalledAppIndex
import com.yuchen.ailedger.service.MobileCommand
import com.yuchen.ailedger.service.MobileCommandParser
import com.yuchen.ailedger.ui.gl.OpenGLGlassProbeLayer

private const val COMPACT_DP_SCALE = 0.90f
private const val COMPACT_FONT_SCALE = 0.92f
private const val ENABLE_OPENGL_GLASS_PROBE = false

private data class PendingMobileAction(
    val originalText: String,
    val command: MobileCommand,
)

private data class NavigationPreferenceUpdate(
    val slot: String,
    val label: String,
    val address: String,
)

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
    val compactDensity = remember(density.density, density.fontScale) {
        Density(density = density.density * COMPACT_DP_SCALE, fontScale = density.fontScale * COMPACT_FONT_SCALE)
    }
    val systemActionRouter = remember(context) { (context as? Activity)?.let { SystemActionRouter(it) } }
    var pendingMobileAction by remember { mutableStateOf<PendingMobileAction?>(null) }
    val latestMessageId = state.messages.lastOrNull()?.id
    val bottomBarAlpha = if (bottomDockVisible) 1f else 0f

    LaunchedEffect(latestMessageId) {
        if (pendingMobileAction == null) {
            parsePendingMobileActionFromLatestMessage(state.messages)?.let { pendingMobileAction = it }
        }
        val update = parseCloudNavigationPreferenceUpdate(state.messages) ?: return@LaunchedEffect
        if (!isNavigationPreferenceAlreadySaved(state, update)) {
            preferencesStore.setNavigationAddress(update.slot, update.address)
        }
    }

    val runPendingMobileAction = remember(state.isSending, systemActionRouter, pendingMobileAction, installedAppIndex) {
        { quickReply: String ->
            val pending = pendingMobileAction
            if (!state.isSending && pending != null) {
                when (quickReply) {
                    "确认" -> {
                        val result = executeMobileCommand(systemActionRouter, pending.command, installedAppIndex)
                        pendingMobileAction = null
                        viewModel.acceptExecutedMobileCommand(
                            userText = quickReply,
                            command = pending.command,
                            ok = result.first,
                            resultMessage = result.second
                        )
                    }
                    "取消" -> {
                        pendingMobileAction = null
                        viewModel.cancelMobileCommand(quickReply, pending.command)
                    }
                }
            }
        }
    }
    val submitOrRunLocalMobileCommand = remember(state, systemActionRouter, pendingMobileAction, installedAppIndex) {
        {
            val text = state.composerText.trim()
            val pending = pendingMobileAction
            when {
                text.isNotBlank() && !state.isSending && pending != null && isConfirmMobileActionText(text) -> {
                    val result = executeMobileCommand(systemActionRouter, pending.command, installedAppIndex)
                    pendingMobileAction = null
                    viewModel.acceptExecutedMobileCommand(
                        userText = text,
                        command = pending.command,
                        ok = result.first,
                        resultMessage = result.second
                    )
                }
                text.isNotBlank() && !state.isSending && pending != null && isCancelMobileActionText(text) -> {
                    pendingMobileAction = null
                    viewModel.cancelMobileCommand(text, pending.command)
                }
                text.isNotBlank() && !state.isSending -> {
                    val command = parseInstalledAppOpenCommand(text, installedAppIndex)
                        ?: MobileCommandParser.parse(text)?.resolveNavigationAddress(state)
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
                LocalPerformanceDiagnostics provides diagnostics,
                LocalOverscrollConfiguration provides null,
                LocalGlassBackdrop provides GlassBackdropSpec(state.quality, effectiveMotionIntensity, state.backgroundTheme, state.backdropParams, state.glassBorderStyle),
                LocalBlurredBackdrop provides blurredBackdrop,
                LocalBackdropOrigin provides backdropOrigin,
                LocalBackdropFrameTicker provides backdropTicker,
                LocalGlassItemRegistry provides if (diagnostics.openGlGlassOff) null else glassRegistry,
                LocalRainbowPrismStyle provides state.rainbowPrismStyle,
                LocalMobileCommandQuickReply provides runPendingMobileAction
            ) {
                Box(Modifier.fillMaxSize().nestedScroll(glassScrollInvalidation)) {
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
                            CachedAppTabHost(
                                currentTab = state.currentTab,
                                modifier = Modifier.fillMaxSize()
                            ) { tab ->
                                when (tab) {
                                    AppTab.Assistant -> AssistantScreenV2(
                                        state = state.copy(motionIntensity = effectiveMotionIntensity),
                                        bottomPadding = assistantBottomPadding,
                                        onComposerChange = viewModel::updateComposer,
                                        onSend = submitOrRunLocalMobileCommand,
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
                                    AppTab.Tools -> {
                                        if (state.selectedToolTitle == STOCK_MARKET_TOOL_TITLE) {
                                            AStockMarketScreenV2(
                                                state = state.copy(motionIntensity = effectiveMotionIntensity),
                                                onBack = viewModel::closeTool,
                                                onOpenAssistant = { viewModel.selectTab(AppTab.Assistant) }
                                            )
                                        } else {
                                            StockFirstToolsHomeScreen(
                                                state = state.copy(motionIntensity = effectiveMotionIntensity),
                                                onOpenTool = viewModel::openTool
                                            )
                                        }
                                    }
                                    AppTab.Settings -> SettingsPolishedScreen(
                                        state = state.copy(motionIntensity = effectiveMotionIntensity),
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
                            PerformanceDiagnosticsPanel(
                                state = diagnostics,
                                onStateChange = { diagnostics = it },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 76.dp, start = 4.dp)
                                    .zIndex(2000f)
                            )
                        }
                        PrismaticCapsuleBottomBar(
                            currentTab = state.currentTab,
                            quality = state.quality,
                            glassIntensity = state.glassIntensity,
                            motionIntensity = effectiveMotionIntensity,
                            onTabChange = { tab -> if (bottomDockClickable) viewModel.selectTab(tab) },
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

private fun parseInstalledAppOpenCommand(text: String, installedAppIndex: InstalledAppIndex): MobileCommand? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    val prefixes = listOf("打开", "启动", "开启")
    val prefix = prefixes.firstOrNull { trimmed.startsWith(it) } ?: return null
    val appName = trimmed.removePrefix(prefix).trim()
    if (appName.isBlank()) return null
    val match = installedAppIndex.findBestMatch(appName) ?: return null
    return MobileCommand.OpenApp(label = match.label, packageName = match.packageName)
}

private fun parsePendingMobileActionFromLatestMessage(messages: List<ChatMessage>): PendingMobileAction? {
    val latest = messages.lastOrNull { it.role == MessageRole.Assistant } ?: return null
    val text = latest.text
    val marker = "[mobile_command:"
    val start = text.indexOf(marker)
    if (start < 0) return null
    val end = text.indexOf("]", startIndex = start)
    if (end <= start) return null
    val body = text.substring(start + marker.length, end)
    val parts = body.split("|", limit = 3)
    return when (parts.firstOrNull()) {
        "open_app" -> {
            val packageName = parts.getOrNull(1).orEmpty()
            val label = parts.getOrNull(2).orEmpty().ifBlank { packageName }
            if (packageName.isBlank()) null else PendingMobileAction(text, MobileCommand.OpenApp(label, packageName))
        }
        "open_url" -> {
            val url = parts.getOrNull(1).orEmpty()
            if (url.isBlank()) null else PendingMobileAction(text, MobileCommand.OpenUrl(url))
        }
        else -> null
    }
}

private fun parseCloudNavigationPreferenceUpdate(messages: List<ChatMessage>): NavigationPreferenceUpdate? {
    val latest = messages.lastOrNull { it.role == MessageRole.Assistant } ?: return null
    val marker = "[navigation_pref:"
    val start = latest.text.indexOf(marker)
    if (start < 0) return null
    val end = latest.text.indexOf("]", startIndex = start)
    if (end <= start) return null
    val body = latest.text.substring(start + marker.length, end)
    val parts = body.split("|", limit = 3)
    val slot = parts.getOrNull(0).orEmpty()
    val label = parts.getOrNull(1).orEmpty()
    val address = parts.getOrNull(2).orEmpty()
    if (slot !in setOf("home", "school", "company") || address.isBlank()) return null
    return NavigationPreferenceUpdate(slot, label, address)
}

private fun isNavigationPreferenceAlreadySaved(state: AssistantUiState, update: NavigationPreferenceUpdate): Boolean {
    return when (update.slot) {
        "home" -> state.navigationHomeAddress == update.address
        "school" -> state.navigationSchoolAddress == update.address
        "company" -> state.navigationCompanyAddress == update.address
        else -> false
    }
}

private fun MobileCommand.resolveNavigationAddress(state: AssistantUiState): MobileCommand {
    if (this !is MobileCommand.OpenUrl) return this
    val url = url
    if (!url.startsWith("app://navigate/")) return this
    val slot = url.removePrefix("app://navigate/")
    val address = when (slot) {
        "home" -> state.navigationHomeAddress
        "school" -> state.navigationSchoolAddress
        "company" -> state.navigationCompanyAddress
        else -> ""
    }
    if (address.isBlank()) return this
    return MobileCommand.OpenUrl("geo:0,0?q=${android.net.Uri.encode(address)}")
}

private fun isConfirmMobileActionText(text: String): Boolean = text.trim() in setOf("确认", "好的", "打开", "执行", "确定")
private fun isCancelMobileActionText(text: String): Boolean = text.trim() in setOf("取消", "不用", "算了")

private fun executeMobileCommand(router: SystemActionRouter?, command: MobileCommand, installedAppIndex: InstalledAppIndex): Pair<Boolean, String> {
    if (router == null) return false to "当前环境无法执行手机动作。"
    return when (command) {
        is MobileCommand.OpenApp -> router.openApp(command.packageName).let { ok ->
            ok to if (ok) "已尝试打开 ${command.label}。" else "没有找到 ${command.label}，请确认是否已安装。"
        }
        is MobileCommand.OpenUrl -> router.openUrl(command.url).let { ok ->
            ok to if (ok) "已尝试打开链接。" else "无法打开这个链接。"
        }
    }
}
