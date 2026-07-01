package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.AssistantMemoryRepository
import com.yuchen.ailedger.data.LedgerSnapshot
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.data.StockWatchlistRepository
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.AgentOverlayProgress
import com.yuchen.ailedger.service.AgentRuntimeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

@Composable
internal fun SettingsPolishedScreenOptimized(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    SyncGlassBackdropToScroll(listState)
    var selectedPanel by rememberSaveable { mutableStateOf(SettingsDetailSection.Service) }
    val entranceSessions = remember { mutableStateMapOf<String, Int>() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "settings-header") {
            SettingsOptimizedEntrance("settings-header", entranceSessions, 0, -8, 0.985f) {
                SettingsOptimizedHeader()
            }
        }
        item(key = "settings-overview") {
            SettingsOptimizedEntrance("settings-overview", entranceSessions, 90, 18, 0.965f) {
                SettingsPersonalSpaceCard(state)
            }
        }
        item(key = "settings-section-title") {
            SettingsOptimizedEntrance("settings-section-title", entranceSessions, 170, 18, 0.97f) {
                SettingsOptimizedSectionTitle(
                    "常用设置",
                    "选中的入口会保持静态高亮，方便快速定位当前面板。",
                )
            }
        }
        item(key = "settings-dashboard") {
            SettingsOptimizedEntrance("settings-dashboard", entranceSessions, 260, 20, 0.965f) {
                SettingsDashboardGridFullMotion(
                    state = state,
                    aiEndpoint = aiEndpoint,
                    selectedPanel = selectedPanel,
                    onSelected = { selectedPanel = it },
                )
            }
        }
        item(key = "settings-detail") {
            SettingsOptimizedEntrance("settings-detail", entranceSessions, 370, 22, 0.965f) {
                SettingsDetailPanel(
                    panel = selectedPanel,
                    state = state,
                    aiEndpoint = aiEndpoint,
                    onQualityChange = onQualityChange,
                    onPreviewConversationChange = onPreviewConversationChange,
                    onGlassPresetChange = onGlassPresetChange,
                    onBackgroundThemeChange = onBackgroundThemeChange,
                    onGlassIntensityChange = onGlassIntensityChange,
                    onMotionIntensityChange = onMotionIntensityChange,
                    onRainbowPrismChange = onRainbowPrismChange,
                    onBackdropChange = onBackdropChange,
                    onBorderChange = onBorderChange,
                    onUploadBackgroundClick = onUploadBackgroundClick,
                    onClearCustomBackgroundClick = onClearCustomBackgroundClick,
                )
            }
        }
        item(key = "settings-lab-entry") {
            SettingsOptimizedEntrance("settings-lab-entry", entranceSessions, 470, 24, 0.96f) {
                SettingsLabEntry(state, selectedPanel == SettingsDetailSection.Debug) {
                    selectedPanel = SettingsDetailSection.Debug
                }
            }
        }
    }
}

@Composable
private fun SettingsOptimizedEntrance(
    entranceKey: String,
    playedSessions: MutableMap<String, Int>,
    delayMs: Long,
    initialOffsetY: Int = 24,
    initialScale: Float = 0.96f,
    content: @Composable () -> Unit,
) {
    val pageActive = LocalPageActive.current
    val pageLeaving = LocalPageLeaving.current
    val activationTick = LocalPageActivationTick.current
    val alreadyPlayedForSession = pageActive && playedSessions[entranceKey] == activationTick
    var visible by remember(entranceKey, activationTick) {
        mutableStateOf(alreadyPlayedForSession)
    }

    LaunchedEffect(pageActive, pageLeaving, activationTick, delayMs, entranceKey) {
        if (pageActive) {
            if (playedSessions[entranceKey] == activationTick) {
                visible = true
                return@LaunchedEffect
            }
            visible = false
            yield()
            if (delayMs > 0L) delay(delayMs)
            visible = true
            playedSessions[entranceKey] = activationTick
        } else {
            if (pageLeaving && delayMs > 0L) {
                delay((delayMs / 18L).coerceAtMost(34L))
            }
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(
                spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow,
                )
            ) { initialOffsetY } +
            scaleIn(
                initialScale = initialScale,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        exit = fadeOut(tween(108)) +
            slideOutVertically(tween(126)) {
                (-initialOffsetY / 3).coerceIn(-10, 10)
            } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(132)),
    ) {
        content()
    }
}

@Composable
private fun SettingsOptimizedHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "SETTINGS",
            color = Color(0xFF8DF9EA).copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            "设置",
            color = Color.White,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Text(
            "账号、资产与智能能力概览。",
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsPersonalSpaceCard(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val watchlistRepository = remember(context) { StockWatchlistRepository.get(context) }
    val ledgerStore = remember(context) { LedgerStore(context) }
    val ledgerSnapshots = remember(ledgerStore) { ledgerStore.observeSnapshots() }

    val accountState by authRepository.state.collectAsState()
    val memoryState by memoryRepository.state.collectAsState()
    val watchlistState by watchlistRepository.state.collectAsState()
    val ledgerSnapshot by ledgerSnapshots.collectAsState(
        initial = LedgerSnapshot(
            records = ledgerStore.loadRecords(),
            budgetText = ledgerStore.loadBudget(),
        )
    )
    val agentProgress by AgentRuntimeController.progress.collectAsState()

    val loggedIn = accountState.isLoggedIn
    val session = accountState.session
    val email = session?.email.orEmpty()
    val displayName = remember(email, loggedIn) { profileDisplayName(email, loggedIn) }
    val maskedEmail = remember(email, loggedIn) { profileMaskedEmail(email, loggedIn) }
    val avatarText = remember(displayName, loggedIn) {
        if (loggedIn) {
            displayName.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "AI" }
        } else {
            "AI"
        }
    }
    val ledgerId = remember(session?.userId, loggedIn) {
        profileLedgerId(session?.userId.orEmpty(), loggedIn)
    }
    val accountStatus = profileAccountStatus(
        loading = accountState.loading,
        loggedIn = loggedIn,
        memoryEnabled = memoryState.cloudReady && memoryState.memoryEnabled,
    )
    val syncStatus = profileSyncStatus(
        accountLoading = accountState.loading,
        loggedIn = loggedIn,
        memoryLoading = memoryState.loading,
        memoryReady = memoryState.cloudReady,
        watchlistLoading = watchlistState.loading,
        watchlistReady = watchlistState.cloudReady,
    )
    val syncHealthy = loggedIn && memoryState.cloudReady && watchlistState.cloudReady
    val visualStatus = remember(agentProgress) { visualAgentSummary(agentProgress) }

    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier
            .fillMaxWidth()
            .height(252.dp),
        role = GlassRole.Shell,
        intensity = (state.glassIntensity * 1.08f).coerceIn(0.78f, 1.30f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = "个人空间",
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsProfileAvatar(
                    text = avatarText,
                    loggedIn = loggedIn,
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = displayName,
                        color = Color.White.copy(alpha = 0.96f),
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = maskedEmail,
                        color = Color.White.copy(alpha = 0.52f),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SettingsAccountStatusPill(
                        text = accountStatus,
                        active = loggedIn,
                    )
                }
                Spacer(Modifier.width(10.dp))
                SettingsIdentityBadge(
                    ledgerId = ledgerId,
                    syncStatus = syncStatus,
                    syncHealthy = syncHealthy,
                    loggedIn = loggedIn,
                )
            }

            SettingsPersonalHairline(alpha = 0.13f)

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPersonalMetric(
                    label = "自选",
                    value = "${watchlistState.items.size} 只",
                    detail = "股票关注",
                    modifier = Modifier.weight(1f),
                )
                SettingsPersonalDivider()
                SettingsPersonalMetric(
                    label = "账单",
                    value = "${ledgerSnapshot.records.size} 笔",
                    detail = "累计记录",
                    modifier = Modifier.weight(1f),
                )
                SettingsPersonalDivider()
                SettingsPersonalMetric(
                    label = "记忆",
                    value = "${memoryState.enabledItemCount} 条",
                    detail = "启用条目",
                    modifier = Modifier.weight(1f),
                )
                SettingsPersonalDivider()
                SettingsPersonalMetric(
                    label = "视觉智能",
                    value = visualStatus.value,
                    detail = visualStatus.detail,
                    modifier = Modifier.weight(1.12f),
                    valueFontSize = 20f,
                )
            }
        }
    }
}

@Composable
private fun SettingsProfileAvatar(
    text: String,
    loggedIn: Boolean,
) {
    val shape = CircleShape
    Box(
        Modifier
            .size(72.dp)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = if (loggedIn) {
                        listOf(
                            Color(0xFF86E8FF).copy(alpha = 0.62f),
                            Color(0xFF335FD7).copy(alpha = 0.74f),
                            Color(0xFF141A55).copy(alpha = 0.96f),
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color(0xFF263269).copy(alpha = 0.72f),
                            Color(0xFF11173F).copy(alpha = 0.96f),
                        )
                    }
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (loggedIn) 0.48f else 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF07132D).copy(alpha = 0.26f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.96f),
                fontSize = if (text.length > 1) 19.sp else 29.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsAccountStatusPill(
    text: String,
    active: Boolean,
) {
    Row(
        Modifier
            .height(27.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) Color(0xFF8DF9EA).copy(alpha = 0.105f)
                else Color.White.copy(alpha = 0.060f)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color(0xFF8DF9EA).copy(alpha = 0.90f)
                    else Color.White.copy(alpha = 0.34f)
                )
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsIdentityBadge(
    ledgerId: String,
    syncStatus: String,
    syncHealthy: Boolean,
    loggedIn: Boolean,
) {
    Column(
        Modifier
            .width(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.075f),
                        Color(0xFF5B63CE).copy(alpha = 0.055f),
                        Color.Black.copy(alpha = 0.060f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "AI LEDGER ID",
            color = Color(0xFF9CCBFF).copy(alpha = 0.66f),
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Text(
            text = ledgerId,
            color = Color.White.copy(alpha = if (loggedIn) 0.92f else 0.55f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsPersonalHairline(alpha = 0.08f)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            syncHealthy -> Color(0xFF8DF9EA).copy(alpha = 0.90f)
                            loggedIn -> Color(0xFFFFD38A).copy(alpha = 0.84f)
                            else -> Color.White.copy(alpha = 0.30f)
                        }
                    )
            )
            Text(
                text = syncStatus,
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 8.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsPersonalMetric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueFontSize: Float = 23f,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.96f),
            fontSize = valueFontSize.sp,
            lineHeight = (valueFontSize + 4f).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            color = Color.White.copy(alpha = 0.31f),
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsPersonalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(52.dp)
            .background(Color.White.copy(alpha = 0.085f))
    )
}

@Composable
private fun SettingsPersonalHairline(alpha: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = alpha))
    )
}

private data class VisualAgentCardSummary(
    val value: String,
    val detail: String,
)

private fun visualAgentSummary(progress: AgentOverlayProgress): VisualAgentCardSummary {
    val value = when {
        !progress.enabled -> "已关闭"
        progress.pendingConfirmation != null -> "待确认"
        progress.pendingUserInput != null -> "待输入"
        progress.userTakeoverPaused -> "接管中"
        progress.running -> progress.status.trim().take(6).ifBlank { "执行中" }
        else -> "待命"
    }
    val detail = when {
        !progress.enabled -> "尚未开启"
        progress.pendingConfirmation != null -> progress.pendingConfirmation.actionText
        progress.pendingUserInput != null -> progress.pendingUserInput.actionText
        progress.userTakeoverPaused -> "等待恢复"
        progress.running -> progress.currentAction
        else -> "等待任务"
    }.trim().replace('\n', ' ').take(12)
    return VisualAgentCardSummary(value, detail)
}

private fun profileDisplayName(email: String, loggedIn: Boolean): String {
    if (!loggedIn) return "本地用户"
    val localPart = email.substringBefore('@').trim()
    if (localPart.isBlank()) return "AI Ledger 用户"
    return localPart
        .replace(Regex("[._-]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { part ->
            part.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        }
        .take(24)
        .ifBlank { "AI Ledger 用户" }
}

private fun profileMaskedEmail(email: String, loggedIn: Boolean): String {
    if (!loggedIn) return "数据仅保存在当前设备"
    val localPart = email.substringBefore('@').trim()
    val domain = email.substringAfter('@', missingDelimiterValue = "").trim()
    if (localPart.isBlank() || domain.isBlank()) return email.take(36)
    return "${localPart.take(1)}***@$domain"
}

private fun profileLedgerId(userId: String, loggedIn: Boolean): String {
    if (!loggedIn || userId.isBlank()) return "LOCAL"
    val hash = userId.fold(0x45D9F3B) { accumulator, character ->
        accumulator * 31 + character.code
    }
    val hex = (hash.toLong() and 0xFFFFFFFFL)
        .toString(16)
        .uppercase()
        .padStart(8, '0')
        .takeLast(8)
    return "ALD-${hex.take(4)}-${hex.takeLast(4)}"
}

private fun profileAccountStatus(
    loading: Boolean,
    loggedIn: Boolean,
    memoryEnabled: Boolean,
): String {
    return when {
        loading -> "正在恢复账号"
        !loggedIn -> "本地模式 · 登录后开启长期记忆"
        memoryEnabled -> "已登录 · 长期记忆已开启"
        else -> "已登录 · 长期记忆未开启"
    }
}

private fun profileSyncStatus(
    accountLoading: Boolean,
    loggedIn: Boolean,
    memoryLoading: Boolean,
    memoryReady: Boolean,
    watchlistLoading: Boolean,
    watchlistReady: Boolean,
): String {
    return when {
        accountLoading || memoryLoading || watchlistLoading -> "正在同步"
        !loggedIn -> "本地数据正常"
        memoryReady && watchlistReady -> "云端同步正常"
        memoryReady || watchlistReady -> "部分同步正常"
        else -> "云端待恢复"
    }
}

@Composable
private fun SettingsOptimizedSectionTitle(title: String, subtitle: String) {
    Column(
        Modifier.padding(top = 3.dp, start = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
