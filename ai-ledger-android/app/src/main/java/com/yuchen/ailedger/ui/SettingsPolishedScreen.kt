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
import androidx.compose.foundation.border
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.roundToInt

private enum class SettingsPanel { Appearance, Glass, Assistant, Data, Service, Advanced, Debug }
private val SettingsChipRole = GlassRole.Chip
private val SettingsFloatingRole = GlassRole.Floating

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
    var selectedPanel by rememberSaveable { mutableStateOf(SettingsPanel.Appearance) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { SettingsEntrance(0, -8, 0.985f) { SettingsHeader() } }
        item { SettingsEntrance(42, 18, 0.965f) { SettingsOverviewCard(state, aiEndpoint) } }
        item { SettingsEntrance(78, 18, 0.97f) { SettingsSectionTitle("常用设置", "选中的入口会持续呼吸，方便快速定位当前面板。") } }
        item { SettingsEntrance(104, 20, 0.965f) { SettingsDashboardGrid(state, aiEndpoint, selectedPanel) { selectedPanel = it } } }
        item {
            SettingsEntrance(140, 22, 0.965f) {
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
        item { SettingsEntrance(176, 24, 0.96f) { SettingsLabEntry(state, selectedPanel == SettingsPanel.Debug) { selectedPanel = SettingsPanel.Debug } } }
    }
}

@Composable
private fun SettingsEntrance(delayMs: Long, initialOffsetY: Int = 24, initialScale: Float = 0.96f, content: @Composable () -> Unit) {
    val pageActive = LocalPageActive.current
    val pageLeaving = LocalPageLeaving.current
    val activationTick = LocalPageActivationTick.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(pageActive, pageLeaving, activationTick, delayMs) {
        if (pageActive) {
            visible = false
            yield()
            if (delayMs > 0L) delay(delayMs)
            visible = true
        } else {
            if (pageLeaving && delayMs > 0L) delay((delayMs / 12L).coerceAtMost(24L))
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)) { initialOffsetY } +
            scaleIn(initialScale = initialScale, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(92)) +
            slideOutVertically(tween(104)) { (-initialOffsetY / 3).coerceIn(-10, 10) } +
            scaleOut(targetScale = 0.986f, animationSpec = tween(112))
    ) { content() }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text("设置", color = Color.White, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("常用项直接展示，复杂参数收进详情面板。", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsGlassFrame(
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
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clip(shape)
            .background(Color(0xFF151A4F).copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
    ) {
        FrostInfoGlassPanel(
            radius = 17.44f,
            backdropAlpha = 1f,
            frostAlpha = 0.088f,
            dimAlpha = 0f,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    SettingsGlassFrame(modifier = Modifier.height(176.dp), radius = 30) {
        Column(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前状态", color = Color.White, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("服务、画质与关键外观集中展示。", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (aiEndpoint.isBlank()) "本地优先" else "云端已配置", color = Color.White.copy(alpha = 0.66f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            SettingsHairline(alpha = 0.12f)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingsFrostMetric("服务", if (aiEndpoint.isBlank()) "本地" else "已连接", Modifier.weight(1f))
                    SettingsDivider()
                    SettingsFrostMetric("画质", qualityLabel(state.quality), Modifier.weight(1f).padding(start = 10.dp))
                    SettingsDivider()
                    SettingsFrostMetric("背景", themeLabel(state.backgroundTheme), Modifier.weight(1f).padding(start = 10.dp))
                }
                SettingsHairline(alpha = 0.08f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SettingsFrostMetric("玻璃", glassPresetLabel(state.glassPreset), Modifier.weight(1f))
                    SettingsDivider()
                    SettingsFrostMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f).padding(start = 10.dp))
                    SettingsDivider()
                    SettingsFrostMetric("OpenGL", "隔离", Modifier.weight(1f).padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsFrostMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.height(40.dp), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(value, color = Color.White.copy(alpha = 0.94f), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 3.dp, start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.88f), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsDashboardGrid(state: AssistantUiState, aiEndpoint: String, selectedPanel: SettingsPanel, onSelected: (SettingsPanel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SettingsTile("景", "外观", "背景与主题", themeLabel(state.backgroundTheme), selectedPanel == SettingsPanel.Appearance, Modifier.weight(1f)) { onSelected(SettingsPanel.Appearance) }
            SettingsTile("璃", "玻璃", "质感与流畅度", "${qualityLabel(state.quality)} · ${glassPresetLabel(state.glassPreset)}", selectedPanel == SettingsPanel.Glass, Modifier.weight(1f)) { onSelected(SettingsPanel.Glass) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SettingsTile("助", "助手", "模型与首页", state.selectedModelLabel, selectedPanel == SettingsPanel.Assistant, Modifier.weight(1f)) { onSelected(SettingsPanel.Assistant) }
            SettingsTile("账", "数据", "预算与账单", "${state.ledgerRecords.size} 笔", selectedPanel == SettingsPanel.Data, Modifier.weight(1f)) { onSelected(SettingsPanel.Data) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SettingsTile("云", "服务", "AI Worker", if (aiEndpoint.isBlank()) "未配置" else "已连接", selectedPanel == SettingsPanel.Service, Modifier.weight(1f)) { onSelected(SettingsPanel.Service) }
            SettingsTile("GL", "高级", "渲染边界", "OpenGL 隔离", selectedPanel == SettingsPanel.Advanced, Modifier.weight(1f)) { onSelected(SettingsPanel.Advanced) }
        }
    }
}

@Composable
private fun SettingsTile(icon: String, title: String, subtitle: String, value: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val clickSource = remember { MutableInteractionSource() }
    val pressed by clickSource.collectIsPressedAsState()
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-tile-selected-$title"
    )
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-tile-press-$title"
    )
    val surfaceEnergy = (selectedProgress + pressProgress * 0.72f).coerceIn(0f, 1.18f)

    FrostInfoGlassPanel(
        radius = 17.44f,
        backdropAlpha = 1f,
        frostAlpha = 0.082f + selectedProgress * 0.028f + pressProgress * 0.010f,
        dimAlpha = 0f,
        modifier = modifier
            .height(116.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.50f, 0.54f)
                scaleX = 1f + selectedProgress * 0.010f + pressProgress * 0.018f
                scaleY = 1f - pressProgress * 0.020f + selectedProgress * 0.004f
                translationY = -selectedProgress * 1.4f + pressProgress * 2.1f
                shadowElevation = selectedProgress * 0.42f + pressProgress * 0.22f
            }
            .settingsTileSwitchOptics(selectedProgress, pressProgress)
            .clickable(interactionSource = clickSource, indication = null, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIconBadge(icon, selected, selectedProgress, pressProgress)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White.copy(alpha = 0.90f + 0.06f * surfaceEnergy), fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Color.White.copy(alpha = 0.48f + 0.07f * surfaceEnergy), fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SettingsHairline(alpha = 0.10f + 0.13f * surfaceEnergy)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("当前", color = Color.White.copy(alpha = 0.32f + 0.08f * surfaceEnergy), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(value, color = Color.White.copy(alpha = 0.58f + 0.26f * selectedProgress + 0.08f * pressProgress), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            }
        }
    }
}

private fun Modifier.settingsTileSwitchOptics(selectedProgress: Float, pressProgress: Float): Modifier = drawWithContent {
    drawContent()
    val selected = selectedProgress.coerceIn(0f, 1f)
    val pressed = pressProgress.coerceIn(0f, 1f)
    val energy = (selected + pressed * 0.72f).coerceIn(0f, 1.20f)
    if (energy <= 0.001f) return@drawWithContent

    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val radius = 17.44.dp.toPx()
    val corner = CornerRadius(radius, radius)
    val glowCenter = Offset(w * (0.62f - 0.10f * pressed), h * (0.18f + 0.18f * selected))
    val rimInset = 0.72.dp.toPx()
    val rimSize = Size((w - rimInset * 2f).coerceAtLeast(1f), (h - rimInset * 2f).coerceAtLeast(1f))

    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.072f * energy),
                Color(0xFF8DFFF3).copy(alpha = 0.050f * energy),
                Color(0xFF8EA2FF).copy(alpha = 0.034f * energy),
                Color.Transparent
            ),
            center = glowCenter,
            radius = w * (0.54f + 0.10f * selected)
        ),
        size = Size(w, h),
        cornerRadius = corner,
        blendMode = BlendMode.Screen
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFFF7AD9).copy(alpha = 0.050f * selected),
                Color.White.copy(alpha = 0.108f * energy),
                Color(0xFF7CFFEA).copy(alpha = 0.070f * energy),
                Color.Transparent
            ),
            start = Offset(-w * 0.18f + w * 0.24f * pressed, 0f),
            end = Offset(w * (0.82f + 0.10f * selected), h)
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = corner,
        style = Stroke(0.54.dp.toPx() + 0.34.dp.toPx() * energy),
        blendMode = BlendMode.Plus
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.052f * energy),
                Color.Transparent,
                Color(0xFF03091B).copy(alpha = 0.030f * pressed)
            ),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset(rimInset, rimInset),
        size = rimSize,
        cornerRadius = corner,
        style = Stroke(0.42.dp.toPx()),
        blendMode = BlendMode.Screen
    )
}

@Composable
private fun SettingsIconBadge(text: String, active: Boolean, activeProgress: Float = if (active) 1f else 0f, pressProgress: Float = 0f) {
    val energy = (activeProgress + pressProgress * 0.62f).coerceIn(0f, 1.15f)
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = 1f + 0.030f * energy
                scaleY = 1f - 0.016f * pressProgress + 0.020f * activeProgress
            }
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.055f + 0.058f * energy)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.66f + 0.28f * energy), fontSize = if (text.length > 1) 13.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsDetailPanel(panel: SettingsPanel, state: AssistantUiState, aiEndpoint: String, onQualityChange: (RenderQuality) -> Unit, onPreviewConversationChange: (Boolean) -> Unit, onGlassPresetChange: (GlassPreset) -> Unit, onBackgroundThemeChange: (BackgroundTheme) -> Unit, onGlassIntensityChange: (Float) -> Unit, onMotionIntensityChange: (Float) -> Unit, onRainbowPrismChange: (RainbowPrismStyle) -> Unit, onBackdropChange: (BackdropDebugParams) -> Unit, onBorderChange: (GlassBorderStyle) -> Unit, onUploadBackgroundClick: () -> Unit, onClearCustomBackgroundClick: () -> Unit) {
    SettingsGlassFrame(radius = 28) {
        AnimatedContent(
            targetState = panel,
            transitionSpec = {
                val direction = if (targetState.settingsOrder() >= initialState.settingsOrder()) 1 else -1
                fadeIn(animationSpec = tween(150, delayMillis = 36, easing = FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { 26 * direction } +
                    scaleIn(initialScale = 0.972f, animationSpec = tween(280, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(112, easing = FastOutSlowInEasing)) +
                    slideOutVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)) { -14 * direction } +
                    scaleOut(targetScale = 0.988f, animationSpec = tween(150, easing = FastOutSlowInEasing))
            },
            label = "settings-detail-panel-switch"
        ) { activePanel ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .settingsDetailSwitchOptics(activePanel)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailHeader(panelTitle(activePanel), panelSubtitle(activePanel))
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    when (activePanel) {
                        SettingsPanel.Appearance -> AppearanceContent(state, onBackgroundThemeChange, onUploadBackgroundClick, onClearCustomBackgroundClick)
                        SettingsPanel.Glass -> GlassContent(state, onQualityChange, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange, onRainbowPrismChange)
                        SettingsPanel.Assistant -> AssistantContent(state, onPreviewConversationChange)
                        SettingsPanel.Data -> DataContent(state)
                        SettingsPanel.Service -> ServiceContent(state, aiEndpoint)
                        SettingsPanel.Advanced -> AdvancedContent(state)
                        SettingsPanel.Debug -> GlassDebugFloatingPanel(state, onBackdropChange, onBorderChange, onUploadBackgroundClick, onClearCustomBackgroundClick, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun Modifier.settingsDetailSwitchOptics(panel: SettingsPanel): Modifier = drawWithContent {
    val tone = panel.settingsOrder() / 6f
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.022f),
                Color(0xFF8DFFF3).copy(alpha = 0.012f + 0.008f * tone),
                Color(0xFFFF8FE7).copy(alpha = 0.006f * (1f - tone)),
                Color.Transparent
            ),
            center = Offset(size.width * (0.18f + 0.62f * tone), size.height * 0.10f),
            radius = size.width * 0.78f
        ),
        size = size,
        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
        blendMode = BlendMode.Screen
    )
    drawContent()
}

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppearanceContent(state: AssistantUiState, onBackgroundThemeChange: (BackgroundTheme) -> Unit, onUploadBackgroundClick: () -> Unit, onClearCustomBackgroundClick: () -> Unit) {
    SettingChipGrid(BackgroundTheme.entries, state.backgroundTheme, { themeLabel(it) }, state, onBackgroundThemeChange)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        SettingActionButton("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", state, Modifier.weight(1f), onUploadBackgroundClick)
        SettingActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
    }
}

@Composable
private fun GlassContent(state: AssistantUiState, onQualityChange: (RenderQuality) -> Unit, onGlassPresetChange: (GlassPreset) -> Unit, onGlassIntensityChange: (Float) -> Unit, onMotionIntensityChange: (Float) -> Unit, onRainbowPrismChange: (RainbowPrismStyle) -> Unit) {
    val prism = state.rainbowPrismStyle
    SettingChipGrid(RenderQuality.entries, state.quality, { qualityLabel(it) }, state, onQualityChange)
    SettingChipGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabel(it) }, state, onGlassPresetChange)
    SliderSettingRow("玻璃强度", state.glassIntensity, 0.6f..1.4f, onGlassIntensityChange)
    SliderSettingRow("动态强度", state.motionIntensity, 0f..1.4f, onMotionIntensityChange)
    SectionTitleInline("首页聊天大玻璃彩虹")
    SliderSettingRow("整体彩虹强度", prism.overall, 0f..2f) { onRainbowPrismChange(prism.copy(overall = it)) }
    SliderSettingRow("棱彩边缘高光", prism.edgeHighlight, 0f..2f) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
    SectionTitleInline("随机渐变扫光区间")
    SliderSettingRow("扫光强度下限", prism.sweepMin, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMin = it)) }
    SliderSettingRow("扫光强度上限", prism.sweepMax, 0f..2f) { onRainbowPrismChange(prism.copy(sweepMax = it)) }
    SliderSettingRow("粉金青蓝彩虹光晕", prism.rainbowHalo, 0f..2f) { onRainbowPrismChange(prism.copy(rainbowHalo = it)) }
}

@Composable
private fun AssistantContent(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("聊天预览", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("打开后首页会保留示例对话和建议词。", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
    }
    SettingInfoRow("默认模型", state.selectedModelLabel)
    SettingInfoRow("首页消息", "${state.messages.size} 条")
    SettingInfoRow("联网模式", if (state.onlineEnabled) "已开启" else "已关闭")
}

@Composable
private fun DataContent(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MiniSettingMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
        MiniSettingMetric("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", Modifier.weight(1f))
        MiniSettingMetric("同步", "本地", Modifier.weight(1f))
    }
    SettingInfoRow("数据保存", "当前为内存预览，重启后恢复示例数据")
    SettingInfoRow("家", state.navigationHomeAddress.ifBlank { "未设置" })
    SettingInfoRow("学校", state.navigationSchoolAddress.ifBlank { "未设置" })
    SettingInfoRow("公司", state.navigationCompanyAddress.ifBlank { "未设置" })
    SettingInfoRow("宿舍", state.navigationDormAddress.ifBlank { "未设置" })
}

@Composable
private fun ServiceContent(state: AssistantUiState, aiEndpoint: String) {
    SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint)
    SettingInfoRow("执行模式", "云端理解，本地确认后执行")
    SettingInfoRow("云端协议", "mobileAction / preferenceUpdate")
}

@Composable
private fun AdvancedContent(state: AssistantUiState) {
    SettingInfoRow("玻璃渲染", "首页大玻璃使用 OpenGL，普通设置控件隔离")
    SettingInfoRow("普通控件", "Card / Chip / Nav / Floating / Flex 完全隔离")
    SettingInfoRow("调试入口", "底部玻璃实验室可调整底层参数")
}

@Composable
private fun SettingsLabEntry(state: AssistantUiState, selected: Boolean, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * if (selected) 0.92f else 0.76f, state.motionIntensity, 26, Modifier.fillMaxWidth().height(62.dp), SettingsChipRole, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsIconBadge("⚗", selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("玻璃实验室", color = Color.White.copy(alpha = 0.90f), fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("高级调试与实验功能", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(if (selected) "已打开" else "进入", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitleInline(title: String) {
    Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun <T> SettingChipGrid(items: List<T>, selected: T, label: (T) -> String, state: AssistantUiState, onSelected: (T) -> Unit) {
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                val active = item == selected
                PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.weight(1f).height(42.dp), if (active) SettingsFloatingRole else SettingsChipRole, onClick = { onSelected(item) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label(item), color = Color.White.copy(alpha = if (active) 0.96f else 0.62f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SliderSettingRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.74f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${value.formatSettingValue()}x", color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun SettingActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 23, modifier.height(58.dp), SettingsChipRole, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

@Composable
private fun MiniSettingMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.50f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsHairline(alpha: Float) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = alpha)))
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.size(1.dp, 38.dp).background(Color.White.copy(alpha = 0.10f)))
}

private fun SettingsPanel.settingsOrder(): Int = when (this) {
    SettingsPanel.Appearance -> 0
    SettingsPanel.Glass -> 1
    SettingsPanel.Assistant -> 2
    SettingsPanel.Data -> 3
    SettingsPanel.Service -> 4
    SettingsPanel.Advanced -> 5
    SettingsPanel.Debug -> 6
}

private fun panelTitle(panel: SettingsPanel): String = when (panel) {
    SettingsPanel.Appearance -> "外观"
    SettingsPanel.Glass -> "玻璃"
    SettingsPanel.Assistant -> "助手"
    SettingsPanel.Data -> "数据"
    SettingsPanel.Service -> "服务"
    SettingsPanel.Advanced -> "高级"
    SettingsPanel.Debug -> "玻璃实验室"
}

private fun panelSubtitle(panel: SettingsPanel): String = when (panel) {
    SettingsPanel.Appearance -> "背景、主题和自定义图片。"
    SettingsPanel.Glass -> "画质、玻璃质感和聊天大玻璃彩虹。"
    SettingsPanel.Assistant -> "模型、联网和首页展示。"
    SettingsPanel.Data -> "账单状态、预算、本地数据和常用导航地址。"
    SettingsPanel.Service -> "AI Worker、云端接口和执行模式。"
    SettingsPanel.Advanced -> "渲染边界和 OpenGL 隔离状态。"
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

private fun Float.formatSettingValue(): String = (this * 100).roundToInt().div(100f).toString()
