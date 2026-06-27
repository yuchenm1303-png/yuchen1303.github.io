package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.SupabaseAuthRepository
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

private enum class SettingsPanel { Appearance, Glass, Assistant, Data, Service, Advanced, Chat, Memory, Debug }
private val SettingsChipRole = GlassRole.Chip
private val SettingsFloatingRole = GlassRole.Floating
private val SettingsOverviewRole = GlassRole.Shell

@Composable
fun SettingsPolishedScreen(
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
    onClearCustomBackgroundClick: () -> Unit
) {
    val listState = rememberLazyListState()
    SyncGlassBackdropToScroll(listState)
    var selectedPanel by rememberSaveable { mutableStateOf(SettingsPanel.Service) }
    val entranceSessions = remember { mutableStateMapOf<String, Int>() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "settings-header") {
            SettingsEntrance("settings-header", entranceSessions, 0, -8, 0.985f) {
                SettingsHeader()
            }
        }
        item(key = "settings-overview") {
            SettingsEntrance("settings-overview", entranceSessions, 90, 18, 0.965f) {
                SettingsOverviewCard(state, aiEndpoint)
            }
        }
        item(key = "settings-section-title") {
            SettingsEntrance("settings-section-title", entranceSessions, 170, 18, 0.97f) {
                SettingsSectionTitle("常用设置", "选中的入口会持续呼吸，方便快速定位当前面板。")
            }
        }
        item(key = "settings-dashboard") {
            SettingsEntrance("settings-dashboard", entranceSessions, 260, 20, 0.965f) {
                SettingsDashboardGrid(state, aiEndpoint, selectedPanel) { selectedPanel = it }
            }
        }
        item(key = "settings-detail") {
            SettingsEntrance("settings-detail", entranceSessions, 370, 22, 0.965f) {
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
                    onClearCustomBackgroundClick = onClearCustomBackgroundClick
                )
            }
        }
        item(key = "settings-lab-entry") {
            SettingsEntrance("settings-lab-entry", entranceSessions, 470, 24, 0.96f) {
                SettingsLabEntry(state, selectedPanel == SettingsPanel.Debug) {
                    selectedPanel = SettingsPanel.Debug
                }
            }
        }
    }
}

@Composable
private fun SettingsEntrance(
    entranceKey: String,
    playedSessions: MutableMap<String, Int>,
    delayMs: Long,
    initialOffsetY: Int = 24,
    initialScale: Float = 0.96f,
    content: @Composable () -> Unit
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
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { initialOffsetY } +
            scaleIn(
                initialScale = initialScale,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        exit = fadeOut(tween(108)) +
            slideOutVertically(tween(126)) {
                (-initialOffsetY / 3).coerceIn(-10, 10)
            } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(132))
    ) {
        content()
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "SETTINGS",
            color = Color(0xFF8DF9EA).copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "设置",
            color = Color.White,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            "账号、服务、外观和玻璃参数集中管理。",
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsGlassFrame(
    state: AssistantUiState,
    modifier: Modifier = Modifier,
    radius: Int = 28,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clip(shape)
    ) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = radius,
            modifier = Modifier.matchParentSize(),
            role = GlassRole.Card
        ) {}
        Box(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
        role = SettingsOverviewRole,
        intensity = (state.glassIntensity * 1.08f).coerceIn(0.78f, 1.30f)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "当前状态",
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        "服务、账号、画质与关键外观集中展示。",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (aiEndpoint.isBlank()) "本地优先" else "云端已配置",
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            SettingsHairline(alpha = 0.12f)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingsFrostMetric(
                        "服务",
                        if (aiEndpoint.isBlank()) "本地" else "已连接",
                        Modifier.weight(1f)
                    )
                    SettingsDivider()
                    SettingsFrostMetric(
                        "画质",
                        qualityLabel(state.quality),
                        Modifier.weight(1f).padding(start = 10.dp)
                    )
                    SettingsDivider()
                    SettingsFrostMetric(
                        "背景",
                        themeLabel(state.backgroundTheme),
                        Modifier.weight(1f).padding(start = 10.dp)
                    )
                }
                SettingsHairline(alpha = 0.08f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingsFrostMetric(
                        "玻璃",
                        glassPresetLabel(state.glassPreset),
                        Modifier.weight(1f)
                    )
                    SettingsDivider()
                    SettingsFrostMetric(
                        "账单",
                        "${state.ledgerRecords.size} 笔",
                        Modifier.weight(1f).padding(start = 10.dp)
                    )
                    SettingsDivider()
                    SettingsFrostMetric(
                        "OpenGL",
                        "隔离",
                        Modifier.weight(1f).padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsFrostMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.height(40.dp), verticalArrangement = Arrangement.Center) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Text(
            value,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Column(
        Modifier.padding(top = 3.dp, start = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsDashboardGrid(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsPanel,
    onSelected: (SettingsPanel) -> Unit
) {
    val context = LocalContext.current
    val stickerSizeDp = InlineStickerDisplaySettings.sizeDp(context)
    val accountRepository = remember(context.applicationContext) {
        SupabaseAuthRepository.get(context.applicationContext)
    }
    val accountState by accountRepository.state.collectAsState()
    val serviceValue = when {
        accountState.loading -> "检查登录状态"
        accountState.isLoggedIn -> "已登录 · 云端"
        aiEndpoint.isBlank() -> "登录与本地"
        else -> "登录与云端"
    }
    val memoryValue = when {
        accountState.loading -> "检查登录状态"
        accountState.isLoggedIn -> "账号已登录"
        else -> "登录后使用"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsTile(
                "景",
                "外观",
                "背景与主题",
                themeLabel(state.backgroundTheme),
                selectedPanel == SettingsPanel.Appearance,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Appearance) }
            SettingsTile(
                "璃",
                "玻璃",
                "质感与流畅度",
                "${qualityLabel(state.quality)} · ${glassPresetLabel(state.glassPreset)}",
                selectedPanel == SettingsPanel.Glass,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Glass) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsTile(
                "视",
                "视觉智能",
                "边缘光与光标",
                "运行 HUD",
                selectedPanel == SettingsPanel.Assistant,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Assistant) }
            SettingsTile(
                "账",
                "数据",
                "预算与账单",
                "${state.ledgerRecords.size} 笔",
                selectedPanel == SettingsPanel.Data,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Data) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsTile(
                "云",
                "服务",
                "账号 / Worker",
                serviceValue,
                selectedPanel == SettingsPanel.Service,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Service) }
            SettingsTile(
                "GL",
                "高级",
                "渲染边界",
                "OpenGL 隔离",
                selectedPanel == SettingsPanel.Advanced,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Advanced) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsTile(
                "聊",
                "聊天页设置",
                "消息与表情",
                "${stickerSizeDp.roundToInt()} dp",
                selectedPanel == SettingsPanel.Chat,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Chat) }
            SettingsTile(
                "忆",
                "记忆",
                "长期上下文",
                memoryValue,
                selectedPanel == SettingsPanel.Memory,
                Modifier.weight(1f)
            ) { onSelected(SettingsPanel.Memory) }
        }
    }
}

@Composable
private fun SettingsTile(
    icon: String,
    title: String,
    subtitle: String,
    value: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val clickSource = remember { MutableInteractionSource() }
    val pressed by clickSource.collectIsPressedAsState()
    val selectedPulse by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "settings-tile-selected-$title"
    )
    val pressPulse by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "settings-tile-pressed-$title"
    )
    val glow = (selectedPulse + pressPulse * 0.55f).coerceIn(0f, 1f)
    val titleFontSize = if (title.length >= 5) 16.sp else 20.sp
    val titleLineHeight = if (title.length >= 5) 20.sp else 23.sp
    val radius = 17.44f
    val frostAlpha = 0.085f + glow * 0.034f
    val parentLayer = LocalSettingsFrostParentLayer.current
    val useParentFrost = parentLayer != null &&
        selectedPulse < 0.001f &&
        pressPulse < 0.001f
    val registeredLayer = parentLayer.takeIf { useParentFrost }
    val frostItemId = remember(title, icon) { "settings-dashboard-$title-$icon" }

    SettingsFrostParentRegistrationCleanup(registeredLayer, frostItemId)

    Box(
        modifier = modifier
            .height(116.dp)
            .registerSettingsFrostParentItem(
                id = frostItemId,
                layerState = registeredLayer,
                radiusDp = radius,
                backdropAlpha = 1f,
                frostAlpha = frostAlpha,
                dimAlpha = 0f
            )
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.50f, 0.54f)
                scaleX = 1f + selectedPulse * 0.012f + pressPulse * 0.018f
                scaleY = 1f + selectedPulse * 0.004f - pressPulse * 0.018f
                translationY = -selectedPulse * 2.2f + pressPulse * 1.8f
                shadowElevation = selectedPulse * 0.7f
            }
            .clickable(
                interactionSource = clickSource,
                indication = null,
                onClick = onClick
            )
            .clip(RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!useParentFrost) {
            FrostInfoGlassPanel(
                radius = radius,
                backdropAlpha = 1f,
                frostAlpha = frostAlpha,
                dimAlpha = 0f,
                modifier = Modifier.matchParentSize()
            ) {}
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsIconBadge(icon, selected, glow)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        color = Color.White.copy(alpha = 0.88f + glow * 0.10f),
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.48f + glow * 0.10f),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SettingsHairline(alpha = 0.10f + glow * 0.14f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前",
                    color = Color.White.copy(alpha = 0.34f + glow * 0.10f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    value,
                    color = Color.White.copy(alpha = 0.62f + glow * 0.28f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun SettingsIconBadge(
    text: String,
    active: Boolean,
    glow: Float = if (active) 1f else 0f
) {
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = 1f + glow * 0.030f
                scaleY = 1f + glow * 0.030f
            }
            .clip(RoundedCornerShape(15.dp))
            .background(
                Color.White.copy(
                    alpha = if (active) {
                        0.105f + glow * 0.025f
                    } else {
                        0.055f + glow * 0.040f
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(
                alpha = if (active) 0.94f else 0.66f + glow * 0.20f
            ),
            fontSize = if (text.length > 1) 13.sp else 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingsDetailPanel(
    panel: SettingsPanel,
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
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingsGlassFrame(state = state, radius = 28) {
        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                val direction = if (
                    targetState.settingsOrder() >= initialState.settingsOrder()
                ) 1 else -1
                fadeIn(
                    animationSpec = tween(
                        170,
                        delayMillis = 42,
                        easing = FastOutSlowInEasing
                    )
                ) +
                    slideInVertically(
                        animationSpec = tween(310, easing = FastOutSlowInEasing)
                    ) { 46 * direction } +
                    scaleIn(
                        initialScale = 0.955f,
                        animationSpec = tween(310, easing = FastOutSlowInEasing)
                    ) togetherWith
                    fadeOut(animationSpec = tween(135, easing = FastOutSlowInEasing)) +
                    slideOutVertically(
                        animationSpec = tween(170, easing = FastOutSlowInEasing)
                    ) { -30 * direction } +
                    scaleOut(
                        targetScale = 0.982f,
                        animationSpec = tween(170, easing = FastOutSlowInEasing)
                    )
            },
            label = "settings-detail-panel-switch"
        ) { activePanel ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailHeader(panelTitle(activePanel), panelSubtitle(activePanel))
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    when (activePanel) {
                        SettingsPanel.Appearance -> AppearanceContent(
                            state,
                            onBackgroundThemeChange,
                            onUploadBackgroundClick,
                            onClearCustomBackgroundClick
                        )
                        SettingsPanel.Glass -> GlassContent(
                            state,
                            onQualityChange,
                            onGlassPresetChange,
                            onGlassIntensityChange,
                            onMotionIntensityChange,
                            onRainbowPrismChange
                        )
                        SettingsPanel.Assistant -> VisualAgentHudSettingsContent(state)
                        SettingsPanel.Data -> DataContent(state)
                        SettingsPanel.Service -> ServiceContent(state, aiEndpoint)
                        SettingsPanel.Advanced -> AdvancedContent()
                        SettingsPanel.Chat -> ChatPageSettingsContent()
                        SettingsPanel.Memory -> MemorySettingsContent()
                        SettingsPanel.Debug -> GlassDebugFloatingPanel(
                            state,
                            onBackdropChange,
                            onBorderChange,
                            onUploadBackgroundClick,
                            onClearCustomBackgroundClick,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppearanceContent(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingChipGrid(
        BackgroundTheme.entries,
        state.backgroundTheme,
        { themeLabel(it) },
        state,
        onBackgroundThemeChange
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingActionButton(
            "上传背景",
            if (state.customBackgroundPath == null) "选择图片" else "已自定义",
            state,
            Modifier.weight(1f),
            onUploadBackgroundClick
        )
        SettingActionButton(
            "清除背景",
            "恢复主题",
            state,
            Modifier.weight(1f),
            onClearCustomBackgroundClick
        )
    }
}

@Composable
private fun GlassContent(
    state: AssistantUiState,
    onQualityChange: (RenderQuality) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit
) {
    val prism = state.rainbowPrismStyle
    SettingChipGrid(
        RenderQuality.entries,
        state.quality,
        { qualityLabel(it) },
        state,
        onQualityChange
    )
    SettingChipGrid(
        GlassPreset.entries,
        state.glassPreset,
        { glassPresetLabel(it) },
        state,
        onGlassPresetChange
    )
    SliderSettingRow(
        "玻璃强度",
        "控制通用玻璃的可见度、雾感和边缘能量。",
        state.glassIntensity,
        0.6f..1.4f,
        onGlassIntensityChange
    )
    SliderSettingRow(
        "动态强度",
        "控制呼吸、扫光和形变动画幅度，0 为静态。",
        state.motionIntensity,
        0f..1.4f,
        onMotionIntensityChange
    )
    SectionTitleInline("首页聊天大玻璃彩虹")
    SliderSettingRow(
        "整体彩虹强度",
        "统一调节聊天大玻璃彩虹镀膜的总能量。",
        prism.overall,
        0f..2f
    ) { onRainbowPrismChange(prism.copy(overall = it)) }
    SliderSettingRow(
        "棱彩边缘高光",
        "增强圆角和玻璃边缘对彩色入射光的捕获。",
        prism.edgeHighlight,
        0f..2f
    ) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
    SectionTitleInline("随机渐变扫光区间")
    SliderSettingRow(
        "扫光强度下限",
        "随机扫光每次出现时允许的最低亮度。",
        prism.sweepMin,
        0f..2f
    ) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
    SliderSettingRow(
        "扫光强度上限",
        "随机扫光每次出现时允许的最高亮度。",
        prism.sweepMax,
        0f..2f
    ) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
    SliderSettingRow(
        "粉金青蓝彩虹光晕",
        "调节粉、金、青、蓝在玻璃外缘形成的柔和光晕。",
        prism.rainbowHalo,
        0f..2f
    ) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
}

@Composable
private fun DataContent(state: AssistantUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
        MiniSettingMetric(
            "预算",
            "¥${state.ledgerBudgetText.ifBlank { "0" }}",
            Modifier.weight(1f)
        )
        MiniSettingMetric("同步", "自动", Modifier.weight(1f))
    }
    SettingInfoRow("数据保存", "LedgerStore 统一持久化，手动与 AI 记账共用数据源")
    SettingInfoRow("云同步", "登录后自动合并并同步；未登录时保存在本机")
    SettingInfoRow("家", state.navigationHomeAddress.ifBlank { "未设置" })
    SettingInfoRow("学校", state.navigationSchoolAddress.ifBlank { "未设置" })
    SettingInfoRow("公司", state.navigationCompanyAddress.ifBlank { "未设置" })
    SettingInfoRow("宿舍", state.navigationDormAddress.ifBlank { "未设置" })
}

@Composable
private fun ServiceContent(state: AssistantUiState, aiEndpoint: String) {
    NativeAccountSettingsCard(state)
    SettingInfoRow(
        "AI 接口",
        if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint
    )
    SettingInfoRow("执行模式", "云端理解，本地确认后执行")
    SettingInfoRow("云端协议", "mobileAction / preferenceUpdate")
}

@Composable
private fun AdvancedContent() {
    SettingInfoRow("玻璃渲染", "仅真正的大型 Shell 使用 OpenGL")
    SettingInfoRow("功能页栏目", "普通入口卡片固定使用 Compose 玻璃")
    SettingInfoRow("隔离范围", "Card / Chip / Floating / Nav / Flex")
    SettingInfoRow("几何同步", "普通控件不注册 registry，也不请求 geometry sync")
    SettingInfoRow("账号控件", "纯 Compose + REST API，不接入 OpenGL registry")
}

@Composable
private fun ChatPageSettingsContent() {
    val context = LocalContext.current
    val stickerSizeDp = InlineStickerDisplaySettings.sizeDp(context)
    InsetGlassParameterSlider(
        title = "表情包大小",
        description = "调节聊天消息中内联表情的显示与排版占位尺寸。",
        value = stickerSizeDp,
        valueRange = InlineStickerDisplaySettings.SizeRange,
        onValueChange = { InlineStickerDisplaySettings.updateSizeDp(context, it) },
        valueText = "${stickerSizeDp.roundToInt()} dp"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "示例消息",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
        OptimizedRichMessageContent(
            text = "这次终于调顺了[[AI_LEDGER_INLINE_STICKER:joy_burst]][[AI_LEDGER_INLINE_STICKER:sparkle_excited]]，句中的表情也会跟着当前尺寸实时变化。",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "拖动上方滑块，示例和聊天页中的表情会同步更新。",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MemorySettingsContent() {
    val context = LocalContext.current.applicationContext
    val accountRepository = remember(context) { SupabaseAuthRepository.get(context) }
    val accountState by accountRepository.state.collectAsState()

    if (!accountState.isLoggedIn) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MiniSettingMetric("长期记忆", "需要登录", Modifier.weight(1f))
            MiniSettingMetric("已保存", "0 条", Modifier.weight(1f))
            MiniSettingMetric("访问状态", "已锁定", Modifier.weight(1f))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.065f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.075f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "锁",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                if (accountState.loading) "正在检查登录状态" else "登录后使用长期记忆",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                if (accountState.loading) {
                    "正在恢复本机保存的 Supabase 会话。"
                } else {
                    "长期记忆会与账号绑定并按用户隔离。请先在上方“服务”卡片完成登录或注册。"
                },
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        SectionTitleInline("为什么需要登录")
        MemoryCapabilityRow(
            title = "账号隔离",
            description = "每个 Supabase 用户只读取自己的长期记忆，避免不同账号互相混用。",
            status = "强制"
        )
        MemoryCapabilityRow(
            title = "跨设备同步",
            description = "登录后才能在设备之间恢复同一份偏好和长期上下文。",
            status = "登录后"
        )
        MemoryCapabilityRow(
            title = "退出即锁定",
            description = "退出登录后停止读取记忆，并清空当前进程中的记忆快照。",
            status = "默认保护"
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MiniSettingMetric("长期记忆", "未接入", Modifier.weight(1f))
        MiniSettingMetric("已保存", "0 条", Modifier.weight(1f))
        MiniSettingMetric("账号状态", "已登录", Modifier.weight(1f))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.065f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "长期记忆",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    accountState.email.orEmpty(),
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = false,
                onCheckedChange = null,
                enabled = false
            )
        }
        SettingsHairline(alpha = 0.08f)
        Text(
            "登录门槛已经满足；长期存储、编辑和跨会话同步将在下一步接入。",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }

    SectionTitleInline("记忆方式")
    MemoryCapabilityRow(
        title = "自动整理",
        description = "从对话中提取长期有效的称呼、偏好和项目背景。",
        status = "待接入"
    )
    MemoryCapabilityRow(
        title = "跨模型共享",
        description = "切换 Qwen、DeepSeek 或识图时继续使用同一份记忆。",
        status = "待接入"
    )
    MemoryCapabilityRow(
        title = "敏感信息保护",
        description = "默认不主动记录密码、验证码、支付信息和一次性隐私内容。",
        status = "默认保护"
    )

    SectionTitleInline("已保存的记忆")
    MemoryEmptyState()

    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MemoryUnavailableAction(
            title = "添加记忆",
            subtitle = "功能接入后可用",
            modifier = Modifier.weight(1f)
        )
        MemoryUnavailableAction(
            title = "全部清除",
            subtitle = "当前没有数据",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MemoryCapabilityRow(
    title: String,
    description: String,
    status: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                description,
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            status,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MemoryEmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.065f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "忆",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            "还没有长期记忆",
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "接入存储后，已保存的偏好、背景和项目上下文会显示在这里，并可单独编辑或删除。",
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MemoryUnavailableAction(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.30f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsLabEntry(
    state: AssistantUiState,
    selected: Boolean,
    onClick: () -> Unit
) {
    PressableGlass(
        state.quality,
        state.glassIntensity * if (selected) 0.92f else 0.76f,
        state.motionIntensity,
        26,
        Modifier.fillMaxWidth().height(62.dp),
        SettingsChipRole,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsIconBadge("⚗", selected)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "玻璃实验室",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    "高级调试与实验功能",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                if (selected) "已打开" else "进入",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionTitleInline(title: String) {
    Text(
        title,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun <T> SettingChipGrid(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    state: AssistantUiState,
    onSelected: (T) -> Unit
) {
    items.chunked(2).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            row.forEach { item ->
                val active = item == selected
                PressableGlass(
                    state.quality,
                    state.glassIntensity,
                    state.motionIntensity,
                    999,
                    Modifier.weight(1f).height(42.dp),
                    if (active) SettingsFloatingRole else SettingsChipRole,
                    onClick = { onSelected(item) }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            label(item),
                            color = Color.White.copy(alpha = if (active) 0.96f else 0.62f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    InsetGlassParameterSlider(
        title = label,
        description = description,
        value = value,
        valueRange = range,
        onValueChange = onValueChange,
        valueText = "${value.formatSettingValue()}×"
    )
}

@Composable
private fun SettingActionButton(
    title: String,
    subtitle: String,
    state: AssistantUiState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressableGlass(
        state.quality,
        state.glassIntensity,
        state.motionIntensity,
        23,
        modifier.height(58.dp),
        SettingsChipRole,
        onClick = onClick
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.060f))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun MiniSettingMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsHairline(alpha: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = alpha))
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .size(1.dp, 38.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

private fun SettingsPanel.settingsOrder(): Int = when (this) {
    SettingsPanel.Appearance -> 0
    SettingsPanel.Glass -> 1
    SettingsPanel.Assistant -> 2
    SettingsPanel.Data -> 3
    SettingsPanel.Service -> 4
    SettingsPanel.Advanced -> 5
    SettingsPanel.Chat -> 6
    SettingsPanel.Memory -> 7
    SettingsPanel.Debug -> 8
}

private fun panelTitle(panel: SettingsPanel): String = when (panel) {
    SettingsPanel.Appearance -> "外观"
    SettingsPanel.Glass -> "玻璃"
    SettingsPanel.Assistant -> "视觉智能"
    SettingsPanel.Data -> "数据"
    SettingsPanel.Service -> "服务"
    SettingsPanel.Advanced -> "高级"
    SettingsPanel.Chat -> "聊天页设置"
    SettingsPanel.Memory -> "记忆"
    SettingsPanel.Debug -> "玻璃实验室"
}

private fun panelSubtitle(panel: SettingsPanel): String = when (panel) {
    SettingsPanel.Appearance -> "背景、主题和自定义图片。"
    SettingsPanel.Glass -> "画质、玻璃质感和聊天大玻璃彩虹。"
    SettingsPanel.Assistant -> "边缘光效、鼠标光标与运行 HUD 的全部参数。"
    SettingsPanel.Data -> "账单状态、预算、本地数据和常用导航地址。"
    SettingsPanel.Service -> "账号登录、AI Worker 和云端接口。"
    SettingsPanel.Advanced -> "渲染边界和 OpenGL 隔离状态。"
    SettingsPanel.Chat -> "聊天消息与内联表情显示参数。"
    SettingsPanel.Memory -> "登录后查看、整理并控制 AI 的长期记忆。"
    SettingsPanel.Debug -> "高级玻璃参数与实验入口。"
}

private fun qualityLabel(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun glassPresetLabel(preset: GlassPreset): String = when (preset) {
    GlassPreset.Basic -> "基础"
    GlassPreset.Blur -> "模糊"
    GlassPreset.Liquid -> "液态"
    GlassPreset.Safe -> "安全"
}

private fun themeLabel(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}

private fun Float.formatSettingValue(): String =
    (this * 100).roundToInt().div(100f).toString()
