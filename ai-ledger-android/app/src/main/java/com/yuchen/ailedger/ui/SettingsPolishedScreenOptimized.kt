package com.yuchen.ailedger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.yuchen.ailedger.data.UserProfileRepository
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
import kotlinx.coroutines.launch
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
    onRequestLogin: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val profileRepository = remember(context) { UserProfileRepository.get(context) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val entranceSessions = remember { mutableStateMapOf<String, Int>() }
    var selectedPanel by rememberSaveable { mutableStateOf(SettingsDetailSection.Service) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(profileRepository::updateAvatar)
    }

    SyncGlassBackdropToScroll(listState)

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
                SettingsPersonalSpaceCard(
                    state = state,
                    onLoginClick = onRequestLogin,
                    onAvatarEditClick = { avatarPicker.launch("image/*") },
                    onNicknameEditClick = {
                        selectedPanel = SettingsDetailSection.Service
                        coroutineScope.launch {
                            delay(70)
                            listState.animateScrollToItem(4)
                        }
                    },
                )
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
private fun SettingsPersonalSpaceCard(
    state: AssistantUiState,
    onLoginClick: () -> Unit,
    onAvatarEditClick: () -> Unit,
    onNicknameEditClick: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val profileRepository = remember(context) { UserProfileRepository.get(context) }
    val memoryRepository = remember(context) { AssistantMemoryRepository.get(context) }
    val watchlistRepository = remember(context) { StockWatchlistRepository.get(context) }
    val ledgerStore = remember(context) { LedgerStore(context) }
    val ledgerSnapshots = remember(ledgerStore) { ledgerStore.observeSnapshots() }

    val accountState by authRepository.state.collectAsState()
    val profileState by profileRepository.state.collectAsState()
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
    val displayName = if (loggedIn) {
        profileState.profile?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: profileDisplayName(email, true)
    } else {
        "本地用户"
    }
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
        profileLoading = profileState.loading || profileState.saving || profileState.uploadingAvatar,
        profileReady = profileState.cloudReady,
        memoryLoading = memoryState.loading,
        memoryReady = memoryState.cloudReady,
        watchlistLoading = watchlistState.loading,
        watchlistReady = watchlistState.cloudReady,
    )
    val syncHealthy = loggedIn &&
        profileState.cloudReady &&
        memoryState.cloudReady &&
        watchlistState.cloudReady
    val visualStatus = remember(agentProgress) { visualAgentSummary(agentProgress) }

    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier
            .fillMaxWidth()
            .height(228.dp),
        role = GlassRole.Shell,
        intensity = (state.glassIntensity * 1.08f).coerceIn(0.78f, 1.30f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "个人空间",
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    UserProfileAvatar(
                        localAvatarPath = profileState.localAvatarPath,
                        avatarVersion = profileState.profile?.avatarVersion ?: 0L,
                        fallbackText = avatarText,
                        size = 62.dp,
                        loggedIn = loggedIn,
                    )
                    if (loggedIn) {
                        SettingsAvatarEditBadge(
                            state = state,
                            onClick = onAvatarEditClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 3.dp, y = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = displayName,
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 19.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (loggedIn) {
                            Spacer(Modifier.width(5.dp))
                            SettingsNicknameEditButton(
                                state = state,
                                onClick = onNicknameEditClick,
                            )
                        }
                    }
                    Text(
                        text = maskedEmail,
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (loggedIn) {
                        SettingsAccountStatusPill(
                            text = accountStatus,
                            active = true,
                        )
                    } else {
                        Text(
                            text = "登录后同步昵称、头像与长期记忆",
                            color = Color.White.copy(alpha = 0.36f),
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                if (loggedIn) {
                    SettingsIdentityBadge(
                        ledgerId = ledgerId,
                        syncStatus = syncStatus,
                        syncHealthy = syncHealthy,
                        loggedIn = true,
                    )
                } else {
                    SettingsPersonalLoginButton(
                        state = state,
                        onClick = onLoginClick,
                    )
                }
            }

            SettingsPersonalHairline(alpha = 0.11f)

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
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
                    valueFontSize = 16.5f,
                )
            }
        }
    }
}

@Composable
private fun SettingsPersonalLoginButton(
    state: AssistantUiState,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier
            .width(78.dp)
            .height(42.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "登录  →",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun SettingsAvatarEditBadge(
    state: AssistantUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.size(25.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(5.5.dp)
        ) {
            val stroke = size.minDimension * 0.115f
            val bodyTop = size.height * 0.28f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.84f),
                topLeft = Offset(size.width * 0.08f, bodyTop),
                size = Size(size.width * 0.84f, size.height * 0.62f),
                cornerRadius = CornerRadius(size.minDimension * 0.12f),
                style = Stroke(width = stroke),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.84f),
                topLeft = Offset(size.width * 0.31f, size.height * 0.12f),
                size = Size(size.width * 0.38f, size.height * 0.22f),
                cornerRadius = CornerRadius(size.minDimension * 0.08f),
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.84f),
                radius = size.minDimension * 0.18f,
                center = Offset(size.width * 0.50f, size.height * 0.59f),
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun SettingsNicknameEditButton(
    state: AssistantUiState,
    onClick: () -> Unit,
) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier
            .width(27.dp)
            .height(25.dp),
        role = GlassRole.Chip,
        onClick = onClick,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val stroke = size.minDimension * 0.14f
            val color = Color.White.copy(alpha = 0.78f)
            drawLine(
                color = color,
                start = Offset(size.width * 0.23f, size.height * 0.76f),
                end = Offset(size.width * 0.73f, size.height * 0.26f),
                strokeWidth = stroke,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.67f, size.height * 0.20f),
                end = Offset(size.width * 0.80f, size.height * 0.33f),
                strokeWidth = stroke,
            )
            drawLine(
                color = color.copy(alpha = 0.54f),
                start = Offset(size.width * 0.17f, size.height * 0.84f),
                end = Offset(size.width * 0.44f, size.height * 0.78f),
                strokeWidth = stroke * 0.72f,
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
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) Color(0xFF8DF9EA).copy(alpha = 0.090f)
                else Color.White.copy(alpha = 0.052f)
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color(0xFF8DF9EA).copy(alpha = 0.84f)
                    else Color.White.copy(alpha = 0.30f)
                )
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
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
            .width(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.066f),
                        Color(0xFF5B63CE).copy(alpha = 0.050f),
                        Color.Black.copy(alpha = 0.050f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.085f), RoundedCornerShape(16.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "AI LEDGER ID",
            color = Color(0xFF9CCBFF).copy(alpha = 0.58f),
            fontSize = 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        Text(
            text = ledgerId,
            color = Color.White.copy(alpha = if (loggedIn) 0.86f else 0.50f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SettingsPersonalHairline(alpha = 0.07f)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            syncHealthy -> Color(0xFF8DF9EA).copy(alpha = 0.84f)
                            loggedIn -> Color(0xFFFFD38A).copy(alpha = 0.78f)
                            else -> Color.White.copy(alpha = 0.26f)
                        }
                    )
            )
            Text(
                text = syncStatus,
                color = Color.White.copy(alpha = 0.47f),
                fontSize = 7.5.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
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
    valueFontSize: Float = 18f,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.43f),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = valueFontSize.sp,
            lineHeight = (valueFontSize + 3f).sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            color = Color.White.copy(alpha = 0.26f),
            fontSize = 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.SemiBold,
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
            .height(42.dp)
            .background(Color.White.copy(alpha = 0.070f))
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
    profileLoading: Boolean,
    profileReady: Boolean,
    memoryLoading: Boolean,
    memoryReady: Boolean,
    watchlistLoading: Boolean,
    watchlistReady: Boolean,
): String {
    return when {
        accountLoading || profileLoading || memoryLoading || watchlistLoading -> "正在同步"
        !loggedIn -> "本地数据正常"
        profileReady && memoryReady && watchlistReady -> "云端同步正常"
        profileReady || memoryReady || watchlistReady -> "部分同步正常"
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
