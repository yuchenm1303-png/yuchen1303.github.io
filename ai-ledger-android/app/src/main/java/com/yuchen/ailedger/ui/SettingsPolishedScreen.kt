package com.yuchen.ailedger.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.data.SupabaseSessionStore
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.BackdropDebugParams
import com.yuchen.ailedger.model.GlassBorderStyle
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.RainbowPrismStyle
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.service.SupabaseAuthClient
import com.yuchen.ailedger.service.SupabaseUserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class SettingsPanel { Appearance, Glass, Assistant, Data, Service, Advanced, Debug }
private enum class AccountAuthMode { Login, Register }
private enum class AccountMessageTone { Normal, Success, Error }

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
    var selectedPanel by rememberSaveable { mutableStateOf(SettingsPanel.Service) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { SettingsHeader() }
        item { SettingsOverviewCard(state, aiEndpoint) }
        item { SettingsSectionTitle("常用设置", "账号登录已迁移到原生 Compose，服务面板可直接登录或注册。") }
        item { SettingsDashboardGrid(state, aiEndpoint, selectedPanel) { selectedPanel = it } }
        item {
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
        item { SettingsLabEntry(state, selectedPanel == SettingsPanel.Debug) { selectedPanel = SettingsPanel.Debug } }
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("SETTINGS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text("设置", color = Color.White, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("账号、服务、外观和玻璃参数集中管理。", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsGlassFrame(
    modifier: Modifier = Modifier,
    radius: Int = 28,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius.dp)
    GlassPanel(
        quality = RenderQuality.Balanced,
        glassIntensity = 0.92f,
        motionIntensity = 0.55f,
        radius = radius,
        modifier = modifier.fillMaxWidth(),
        role = GlassRole.Card
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
                .clip(shape)
                .background(Color(0xFF151A4F).copy(alpha = 0.30f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
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
                    Text("服务、账号、画质与关键外观集中展示。", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun SettingsDashboardGrid(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsPanel,
    onSelected: (SettingsPanel) -> Unit
) {
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
            SettingsTile("云", "服务", "账号 / Worker", if (aiEndpoint.isBlank()) "登录与本地" else "登录与云端", selectedPanel == SettingsPanel.Service, Modifier.weight(1f)) { onSelected(SettingsPanel.Service) }
            SettingsTile("GL", "高级", "渲染边界", "OpenGL 隔离", selectedPanel == SettingsPanel.Advanced, Modifier.weight(1f)) { onSelected(SettingsPanel.Advanced) }
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
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-tile-selected-$title"
    )
    val pressPulse by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "settings-tile-pressed-$title"
    )
    val glow = (selectedPulse + pressPulse * 0.55f).coerceIn(0f, 1f)

    FrostInfoGlassPanel(
        radius = 17.44f,
        backdropAlpha = 1f,
        frostAlpha = 0.085f + glow * 0.034f,
        dimAlpha = 0f,
        modifier = modifier
            .height(116.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.50f, 0.54f)
                scaleX = 1f + selectedPulse * 0.012f + pressPulse * 0.018f
                scaleY = 1f + selectedPulse * 0.004f - pressPulse * 0.018f
                translationY = -selectedPulse * 2.2f + pressPulse * 1.8f
                shadowElevation = selectedPulse * 0.7f
            }
            .clickable(interactionSource = clickSource, indication = null, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsIconBadge(icon, selected, glow)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White.copy(alpha = 0.88f + glow * 0.10f), fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Color.White.copy(alpha = 0.48f + glow * 0.10f), fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SettingsHairline(alpha = 0.10f + glow * 0.14f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("当前", color = Color.White.copy(alpha = 0.34f + glow * 0.10f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(value, color = Color.White.copy(alpha = 0.62f + glow * 0.28f), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun SettingsIconBadge(text: String, active: Boolean, glow: Float = if (active) 1f else 0f) {
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = 1f + glow * 0.030f
                scaleY = 1f + glow * 0.030f
            }
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = if (active) 0.105f + glow * 0.025f else 0.055f + glow * 0.040f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = if (active) 0.94f else 0.66f + glow * 0.20f), fontSize = if (text.length > 1) 13.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 1, textAlign = TextAlign.Center)
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
    SettingsGlassFrame(radius = 28) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailHeader(panelTitle(panel), panelSubtitle(panel))
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                when (panel) {
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

@Composable
private fun DetailHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppearanceContent(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingChipGrid(BackgroundTheme.entries, state.backgroundTheme, { themeLabel(it) }, state, onBackgroundThemeChange)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        SettingActionButton("上传背景", if (state.customBackgroundPath == null) "选择图片" else "已自定义", state, Modifier.weight(1f), onUploadBackgroundClick)
        SettingActionButton("清除背景", "恢复主题", state, Modifier.weight(1f), onClearCustomBackgroundClick)
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
    SettingChipGrid(RenderQuality.entries, state.quality, { qualityLabel(it) }, state, onQualityChange)
    SettingChipGrid(GlassPreset.entries, state.glassPreset, { glassPresetLabel(it) }, state, onGlassPresetChange)
    SliderSettingRow("玻璃强度", state.glassIntensity, 0.6f..1.4f, onGlassIntensityChange)
    SliderSettingRow("动态强度", state.motionIntensity, 0f..1.4f, onMotionIntensityChange)
    SectionTitleInline("首页聊天大玻璃彩虹")
    SliderSettingRow("整体彩虹强度", prism.overall, 0f..2f) { onRainbowPrismChange(prism.copy(overall = it)) }
    SliderSettingRow("棱彩边缘高光", prism.edgeHighlight, 0f..2f) { onRainbowPrismChange(prism.copy(edgeHighlight = it)) }
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
        MiniSettingMetric("同步", "待接入", Modifier.weight(1f))
    }
    SettingInfoRow("数据保存", "当前账单仍由原生本地状态管理")
    SettingInfoRow("云同步", "账号登录已完成，账单云同步下一步接入")
    SettingInfoRow("家", state.navigationHomeAddress.ifBlank { "未设置" })
    SettingInfoRow("学校", state.navigationSchoolAddress.ifBlank { "未设置" })
    SettingInfoRow("公司", state.navigationCompanyAddress.ifBlank { "未设置" })
    SettingInfoRow("宿舍", state.navigationDormAddress.ifBlank { "未设置" })
}

@Composable
private fun ServiceContent(state: AssistantUiState, aiEndpoint: String) {
    AccountSettingsCard(state)
    SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint)
    SettingInfoRow("执行模式", "云端理解，本地确认后执行")
    SettingInfoRow("云端协议", "mobileAction / preferenceUpdate")
}

@Composable
private fun AccountSettingsCard(state: AssistantUiState) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sessionStore = remember(context) { SupabaseSessionStore(context) }
    val authClient = remember { SupabaseAuthClient() }

    var session by remember { mutableStateOf<SupabaseUserSession?>(null) }
    var authMode by rememberSaveable { mutableStateOf(AccountAuthMode.Login) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("未登录时仍可继续本地使用；登录后会保留 Supabase 会话。") }
    var tone by rememberSaveable { mutableStateOf(AccountMessageTone.Normal) }

    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) { sessionStore.load() }
        session = stored
        if (stored != null) {
            emailInput = stored.email
            message = "账号已接通。会话已保存在本机。"
            tone = AccountMessageTone.Success
        }
    }

    fun submitAuth() {
        val email = emailInput.trim()
        val password = passwordInput
        if (loading) return
        if (email.isBlank() || password.isBlank()) {
            message = "邮箱和密码都要填写。"
            tone = AccountMessageTone.Error
            return
        }
        loading = true
        message = if (authMode == AccountAuthMode.Register) "正在注册…" else "正在登录…"
        tone = AccountMessageTone.Normal
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (authMode == AccountAuthMode.Register) authClient.signUp(email, password)
                    else authClient.signInWithPassword(email, password)
                }
                result.session?.let { nextSession ->
                    withContext(Dispatchers.IO) { sessionStore.save(nextSession) }
                    session = nextSession
                    emailInput = nextSession.email
                    passwordInput = ""
                }
                message = result.message
                tone = if (result.requiresEmailConfirmation) AccountMessageTone.Normal else AccountMessageTone.Success
            } catch (error: Throwable) {
                message = error.friendlyAuthMessage()
                tone = AccountMessageTone.Error
            } finally {
                loading = false
            }
        }
    }

    fun refreshLogin() {
        val current = session
        if (current?.refreshToken.isNullOrBlank() || loading) {
            message = "当前会话缺少刷新令牌，请重新登录。"
            tone = AccountMessageTone.Error
            return
        }
        loading = true
        message = "正在刷新登录状态…"
        tone = AccountMessageTone.Normal
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { authClient.refreshSession(current!!.refreshToken) }
                val nextSession = result.session ?: current
                withContext(Dispatchers.IO) { sessionStore.save(nextSession) }
                session = nextSession
                emailInput = nextSession.email
                message = result.message
                tone = AccountMessageTone.Success
            } catch (error: Throwable) {
                message = error.friendlyAuthMessage()
                tone = AccountMessageTone.Error
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        val current = session
        if (loading) return
        loading = true
        message = "正在退出…"
        tone = AccountMessageTone.Normal
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    current?.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                        runCatching { authClient.signOut(token) }
                    }
                    sessionStore.clear()
                }
                session = null
                passwordInput = ""
                message = "已退出登录，当前仅保存在本机。"
                tone = AccountMessageTone.Normal
            } catch (error: Throwable) {
                message = error.friendlyAuthMessage()
                tone = AccountMessageTone.Error
            } finally {
                loading = false
            }
        }
    }

    SettingsGlassFrame(radius = 26) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("账号与同步", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(if (session?.isUsable == true) session!!.email else "未登录 · 本地模式", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AccountStatusPill(session?.isUsable == true)
            }

            SettingInfoRow("当前账号", session?.email ?: "未登录")
            SettingInfoRow("同步状态", if (session?.isUsable == true) "登录体系已接通" else "登录后开启云端能力")

            if (session?.isUsable != true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AuthModeChip("登录", authMode == AccountAuthMode.Login, state, Modifier.weight(1f)) { authMode = AccountAuthMode.Login }
                    AuthModeChip("注册", authMode == AccountAuthMode.Register, state, Modifier.weight(1f)) { authMode = AccountAuthMode.Register }
                }
                SettingTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it.take(80) },
                    placeholder = "邮箱 name@example.com",
                    keyboardType = KeyboardType.Email,
                    enabled = !loading
                )
                SettingTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it.take(72) },
                    placeholder = "密码至少 6 位",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !loading
                )
                SettingActionButton(
                    title = if (loading) "处理中…" else if (authMode == AccountAuthMode.Register) "注册" else "登录",
                    subtitle = if (authMode == AccountAuthMode.Register) "Supabase 邮箱注册" else "邮箱密码登录",
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { submitAuth() }
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    SettingActionButton("刷新会话", if (loading) "处理中" else "更新 token", state, Modifier.weight(1f)) { refreshLogin() }
                    SettingActionButton("退出登录", "回到本地模式", state, Modifier.weight(1f)) { logout() }
                }
            }

            Text(
                text = message,
                color = when (tone) {
                    AccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
                    AccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
                    AccountMessageTone.Normal -> Color.White.copy(alpha = 0.52f)
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccountStatusPill(loggedIn: Boolean) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (loggedIn) Color(0xFF8DF9EA).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (loggedIn) "已登录" else "本地模式", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun AuthModeChip(text: String, selected: Boolean, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(40.dp), if (selected) SettingsFloatingRole else SettingsChipRole, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (selected) 0.96f else 0.62f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Color(0xFF8DF9EA).copy(alpha = 0.92f)),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isBlank()) {
            Text(placeholder, color = Color.White.copy(alpha = 0.38f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdvancedContent(state: AssistantUiState) {
    SettingInfoRow("玻璃渲染", "首页大玻璃使用 OpenGL，普通设置控件隔离")
    SettingInfoRow("普通控件", "Card / Chip / Nav / Floating / Flex 完全隔离")
    SettingInfoRow("账号控件", "纯 Compose + REST API，不接入 OpenGL registry")
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
    SettingsPanel.Service -> "账号登录、AI Worker 和云端接口。"
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

private fun Throwable.friendlyAuthMessage(): String = message?.takeIf { it.isNotBlank() } ?: "账号操作失败，请稍后再试。"
