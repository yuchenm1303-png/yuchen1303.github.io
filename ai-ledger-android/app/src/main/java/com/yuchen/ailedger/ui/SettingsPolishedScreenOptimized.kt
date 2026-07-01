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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
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
                SettingsOptimizedOverviewCard(state, aiEndpoint)
            }
        }
        item(key = "settings-section-title") {
            SettingsOptimizedEntrance("settings-section-title", entranceSessions, 170, 18, 0.97f) {
                SettingsOptimizedSectionTitle(
                    "常用设置",
                    "选中的入口会持续呼吸，方便快速定位当前面板。",
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
            "账号、服务、外观和玻璃参数集中管理。",
            color = Color.White.copy(alpha = 0.60f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsOptimizedOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 30,
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
        role = GlassRole.Shell,
        intensity = (state.glassIntensity * 1.08f).coerceIn(0.78f, 1.30f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "当前状态",
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        "服务、账号、画质与关键外观集中展示。",
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (aiEndpoint.isBlank()) "本地优先" else "云端已配置",
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            SettingsHairline(alpha = 0.12f)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingsOptimizedFrostMetric(
                        "服务",
                        if (aiEndpoint.isBlank()) "本地" else "已连接",
                        Modifier.weight(1f),
                    )
                    SettingsDivider()
                    SettingsOptimizedFrostMetric(
                        "画质",
                        qualityLabel(state.quality),
                        Modifier.weight(1f).padding(start = 10.dp),
                    )
                    SettingsDivider()
                    SettingsOptimizedFrostMetric(
                        "背景",
                        themeLabel(state.backgroundTheme),
                        Modifier.weight(1f).padding(start = 10.dp),
                    )
                }
                SettingsHairline(alpha = 0.08f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingsOptimizedFrostMetric(
                        "玻璃",
                        glassPresetLabel(state.glassPreset),
                        Modifier.weight(1f),
                    )
                    SettingsDivider()
                    SettingsOptimizedFrostMetric(
                        "账单",
                        "${state.ledgerRecords.size} 笔",
                        Modifier.weight(1f).padding(start = 10.dp),
                    )
                    SettingsDivider()
                    SettingsOptimizedFrostMetric(
                        "OpenGL",
                        "隔离",
                        Modifier.weight(1f).padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOptimizedFrostMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.height(40.dp), verticalArrangement = Arrangement.Center) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        Text(
            value,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
