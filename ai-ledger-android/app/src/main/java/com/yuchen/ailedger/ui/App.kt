package com.yuchen.ailedger.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuchen.ailedger.AssistantViewModel
import com.yuchen.ailedger.SystemActionRouter
import com.yuchen.ailedger.data.AssistantPreferencesStore
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.ComposerAttachment
import com.yuchen.ailedger.model.ComposerAttachmentStatus
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.ChatNotificationManager
import com.yuchen.ailedger.service.InstalledAppIndex
import com.yuchen.ailedger.service.MobileCommand
import com.yuchen.ailedger.service.MobileCommandParser
import com.yuchen.ailedger.ui.gl.OpenGLGlassProbeLayer
import kotlin.math.max

private const val COMPACT_DP_SCALE = 0.90f
private const val COMPACT_FONT_SCALE = 0.92f
private const val ENABLE_OPENGL_GLASS_PROBE = false
private const val VISUAL_ATTACHMENT_STATUS_PREFIX = "视觉附件 · "
private const val GLASS_SCROLL_INVALIDATION_INTERVAL_MS = 12L

private data class PendingMobileAction(
    val originalText: String,
    val command: MobileCommand,
)

private data class NavigationPreferenceUpdate(
    val slot: String,
    val label: String,
    val address: String,
)

private data class MobileCommandSnapshot(
    val composerText: String,
    val isSending: Boolean,
    val navigationHomeAddress: String,
    val navigationSchoolAddress: String,
    val navigationCompanyAddress: String,
    val navigationDormAddress: String,
)

private data class ChatMessagesSideEffectKey(
    val messageCount: Int,
    val notificationSignature: String,
    val latestAssistantSignature: String,
) {
    companion object {
        fun from(messages: List<ChatMessage>): ChatMessagesSideEffectKey {
            val visibleMessages = messages
                .asSequence()
                .filter { it.text.isNotBlank() }
                .filterNot { it.status == MessageStatus.Sending }
                .takeLastCompat(6)
            val notificationSignature = visibleMessages.joinToString("|") { message ->
                "${message.id}:${message.role.name}:${message.status.name}:${message.createdAt}:${message.text.stableShortHash()}"
            }
            val latestAssistant = messages.lastOrNull { it.role == MessageRole.Assistant }
            val latestAssistantSignature = when {
                latestAssistant == null -> "none"
                latestAssistant.status == MessageStatus.Sending -> "${latestAssistant.id}:sending"
                else -> "${latestAssistant.id}:${latestAssistant.status.name}:${latestAssistant.text.stableShortHash()}"
            }
            return ChatMessagesSideEffectKey(
                messageCount = messages.size,
                notificationSignature = notificationSignature,
                latestAssistantSignature = latestAssistantSignature,
            )
        }
    }
}

private data class VisualAttachmentOverlayState(
    val attachment: ComposerAttachment,
    val quality: RenderQuality,
    val glassIntensity: Float,
    val motionIntensity: Float,
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
    val bottomBarAlpha = if (bottomDockVisible) 1f else 0f
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
    val glassScrollInvalidationClock = remember { LongArray(1) }
    val glassScrollInvalidation = remember(backdropTicker, glassScrollInvalidationClock) {
        object : NestedScrollConnection {
            private fun requestCoalescedFrame(force: Boolean = false) {
                val now = SystemClock.uptimeMillis()
                if (force || now - glassScrollInvalidationClock[0] >= GLASS_SCROLL_INVALIDATION_INTERVAL_MS) {
                    glassScrollInvalidationClock[0] = now
                    backdropTicker.requestFrame(force = force)
                }
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
                            CachedAppTabHost(currentTab = state.currentTab, modifier = Modifier.fillMaxSize()) { tab ->
                                when (tab) {
                                    AppTab.Assistant -> {
                                        Box(Modifier.fillMaxSize()) {
                                            AssistantScreenV2(
                                                state = assistantScreenState,
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
                                        }
                                    }
                                    AppTab.Tools -> {
                                        if (state.selectedToolTitle == STOCK_MARKET_TOOL_TITLE) {
                                            AStockMarketScreenV2(
                                                state = stockAndSettingsState,
                                                onBack = viewModel::closeTool,
                                                onOpenAssistant = { viewModel.selectTab(AppTab.Assistant) }
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

@Composable
private fun rememberAssistantScreenState(
    state: AssistantUiState,
    effectiveMotionIntensity: Float,
    visibleComposerText: String,
): AssistantUiState {
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
        state.copy(
            motionIntensity = effectiveMotionIntensity,
            composerText = visibleComposerText,
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

private fun visibleComposerTextForAssistant(text: String): String {
    return if (text.trim().startsWith(VISUAL_ATTACHMENT_STATUS_PREFIX)) "" else text
}

private fun parseInstalledAppOpenCommand(text: String, installedAppIndex: InstalledAppIndex): MobileCommand? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    val prefixes = listOf("打开", "启动", "开启")
    val prefix = prefixes.firstOrNull { trimmed.startsWith(it) } ?: return null
    val appName = trimmed.removePrefix(prefix).trim()
    if (appName.isBlank()) return null
    val match = installedAppIndex.findBestApp(appName) ?: return null
    return MobileCommand.OpenApp(appName = match.label, packageName = match.packageName)
}

private fun parsePendingMobileActionFromLatestMessage(messages: List<ChatMessage>): PendingMobileAction? {
    val latest = messages.lastOrNull { it.role == MessageRole.Assistant && it.status == MessageStatus.Sent } ?: return null
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
            if (packageName.isBlank()) null else PendingMobileAction(text, MobileCommand.OpenApp(appName = label, packageName = packageName))
        }
        else -> null
    }
}

private fun parseCloudNavigationPreferenceUpdate(messages: List<ChatMessage>): NavigationPreferenceUpdate? {
    val latest = messages.lastOrNull { it.role == MessageRole.Assistant && it.status == MessageStatus.Sent } ?: return null
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

private fun isNavigationPreferenceAlreadySaved(snapshot: MobileCommandSnapshot, update: NavigationPreferenceUpdate): Boolean {
    return when (update.slot) {
        "home" -> snapshot.navigationHomeAddress == update.address
        "school" -> snapshot.navigationSchoolAddress == update.address
        "company" -> snapshot.navigationCompanyAddress == update.address
        else -> false
    }
}

private fun MobileCommand.resolveNavigationAddress(snapshot: MobileCommandSnapshot): MobileCommand {
    if (this !is MobileCommand.Navigate) return this
    val resolvedDestination = when (destination) {
        "home" -> snapshot.navigationHomeAddress
        "school" -> snapshot.navigationSchoolAddress
        "company" -> snapshot.navigationCompanyAddress
        "dorm" -> snapshot.navigationDormAddress
        else -> destination
    }
    return if (resolvedDestination.isBlank()) this else copy(destination = resolvedDestination)
}

private fun isConfirmMobileActionText(text: String): Boolean = text.trim() in setOf("确认", "好的", "打开", "执行", "确定")
private fun isCancelMobileActionText(text: String): Boolean = text.trim() in setOf("取消", "不用", "算了")

private fun executeMobileCommand(router: SystemActionRouter?, command: MobileCommand): Pair<Boolean, String> {
    if (router == null) return false to "当前环境无法执行手机动作。"
    return when (command) {
        is MobileCommand.OpenApp -> {
            val opened = when {
                !command.launchUri.isNullOrBlank() -> router.openDeepLink(command.launchUri, command.packageName, command.appName)
                !command.packageName.isNullOrBlank() -> router.openApp(command.packageName, command.appName)
                else -> false
            }
            opened to if (opened) "已尝试打开 ${command.appName}。" else "没有找到 ${command.appName}，请确认是否已安装。"
        }
        is MobileCommand.Navigate -> router.startNavigation(command.destination).let { ok -> ok to if (ok) "已尝试打开导航。" else "没有可用的地图应用。" }
        is MobileCommand.SetAlarm -> router.setAlarm(command.hour, command.minute, command.label).let { ok -> ok to if (ok) "已尝试设置闹钟。" else "无法打开系统闹钟。" }
    }
}

private fun Sequence<ChatMessage>.takeLastCompat(count: Int): List<ChatMessage> {
    if (count <= 0) return emptyList()
    val buffer = ArrayDeque<ChatMessage>(count)
    forEach { message ->
        if (buffer.size == count) buffer.removeFirst()
        buffer.addLast(message)
    }
    return buffer.toList()
}

private fun String.stableShortHash(): Int {
    return replace('\n', ' ')
        .trim()
        .take(180)
        .hashCode()
}