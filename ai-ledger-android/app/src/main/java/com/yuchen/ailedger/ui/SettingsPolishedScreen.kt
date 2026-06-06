package com.yuchen.ailedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

enum class SettingsPanel { Appearance, Glass, Assistant, Data, Service, Advanced, Debug }
enum class AccountAuthMode { Login, Register }
enum class AccountMessageTone { Normal, Success, Error }

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
        item { SettingsPanelGrid(state, aiEndpoint, selectedPanel) { selectedPanel = it } }
        item {
            when (selectedPanel) {
                SettingsPanel.Appearance -> AppearanceSettingsPanel(state, onBackgroundThemeChange, onUploadBackgroundClick, onClearCustomBackgroundClick)
                SettingsPanel.Glass -> GlassSettingsPanel(state, onQualityChange, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange, onRainbowPrismChange)
                SettingsPanel.Assistant -> AssistantSettingsPanel(state, onPreviewConversationChange)
                SettingsPanel.Data -> DataSettingsPanel(state)
                SettingsPanel.Service -> ServiceSettingsPanel(state, aiEndpoint)
                SettingsPanel.Advanced -> AdvancedSettingsPanel(state, onBackdropChange, onBorderChange)
                SettingsPanel.Debug -> DebugSettingsPanel(state, aiEndpoint)
            }
        }
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
private fun SettingsOverviewCard(state: AssistantUiState, aiEndpoint: String) {
    SettingsCard {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前状态", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusMetric("服务", if (aiEndpoint.isBlank()) "本地" else "云端", Modifier.weight(1f))
                StatusMetric("画质", qualityLabel(state.quality), Modifier.weight(1f))
                StatusMetric("背景", themeLabel(state.backgroundTheme), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusMetric("玻璃", glassPresetLabel(state.glassPreset), Modifier.weight(1f))
                StatusMetric("账单", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
                StatusMetric("OpenGL", "隔离", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsPanelGrid(
    state: AssistantUiState,
    aiEndpoint: String,
    selectedPanel: SettingsPanel,
    onSelected: (SettingsPanel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PanelChip("外观", themeLabel(state.backgroundTheme), selectedPanel == SettingsPanel.Appearance, Modifier.weight(1f)) { onSelected(SettingsPanel.Appearance) }
            PanelChip("玻璃", "${qualityLabel(state.quality)} · ${glassPresetLabel(state.glassPreset)}", selectedPanel == SettingsPanel.Glass, Modifier.weight(1f)) { onSelected(SettingsPanel.Glass) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PanelChip("助手", state.selectedModelLabel, selectedPanel == SettingsPanel.Assistant, Modifier.weight(1f)) { onSelected(SettingsPanel.Assistant) }
            PanelChip("数据", "${state.ledgerRecords.size} 笔账单", selectedPanel == SettingsPanel.Data, Modifier.weight(1f)) { onSelected(SettingsPanel.Data) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PanelChip("服务", if (aiEndpoint.isBlank()) "账号 / 本地" else "账号 / 云端", selectedPanel == SettingsPanel.Service, Modifier.weight(1f)) { onSelected(SettingsPanel.Service) }
            PanelChip("高级", "渲染边界", selectedPanel == SettingsPanel.Advanced, Modifier.weight(1f)) { onSelected(SettingsPanel.Advanced) }
        }
        PanelChip("调试", "参数检查与构建排查", selectedPanel == SettingsPanel.Debug, Modifier.fillMaxWidth()) { onSelected(SettingsPanel.Debug) }
    }
}

@Composable
private fun AppearanceSettingsPanel(
    state: AssistantUiState,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onUploadBackgroundClick: () -> Unit,
    onClearCustomBackgroundClick: () -> Unit
) {
    SettingsCard(title = "外观与背景", subtitle = "切换内置主题或使用自定义背景。") {
        ThemeOptions(state.backgroundTheme, onBackgroundThemeChange)
        SettingButton("上传背景", "选择本机图片作为背景", onUploadBackgroundClick)
        SettingButton("清除自定义背景", "恢复内置主题背景", onClearCustomBackgroundClick)
    }
}

@Composable
private fun GlassSettingsPanel(
    state: AssistantUiState,
    onQualityChange: (RenderQuality) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit,
    onRainbowPrismChange: (RainbowPrismStyle) -> Unit
) {
    SettingsCard(title = "玻璃与动效", subtitle = "只调整 Compose 表层参数，不触碰 OpenGL 聊天框稳定链路。") {
        Text("画质", color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        OptionRow(RenderQuality.entries.toList(), state.quality, { it.title }, onQualityChange)
        Text("玻璃预设", color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        OptionRow(GlassPreset.entries.toList(), state.glassPreset, { it.label }, onGlassPresetChange)
        SettingSlider("玻璃强度", state.glassIntensity, 0.55f..1.45f, onGlassIntensityChange)
        SettingSlider("动效强度", state.motionIntensity, 0f..1.35f, onMotionIntensityChange)
        SettingSlider("彩虹折射", state.rainbowPrismStyle.overall, 0f..1.6f) {
            onRainbowPrismChange(state.rainbowPrismStyle.copy(overall = it))
        }
        SettingSlider("边缘高光", state.rainbowPrismStyle.edgeHighlight, 0f..1.6f) {
            onRainbowPrismChange(state.rainbowPrismStyle.copy(edgeHighlight = it))
        }
    }
}

@Composable
private fun AssistantSettingsPanel(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    SettingsCard(title = "助手", subtitle = "模型选择在聊天页顶部完成，这里只保留全局显示开关。") {
        SettingInfoRow("当前模型", state.selectedModelLabel)
        SettingSwitchRow("首页预览对话", "显示欢迎页内置示例消息", state.showPreviewConversation, onPreviewConversationChange)
        SettingInfoRow("联网状态", if (state.onlineEnabled) "已开启" else "关闭")
        SettingInfoRow("智能体状态", if (state.agentEnabled) "已开启" else "关闭")
    }
}

@Composable
private fun DataSettingsPanel(state: AssistantUiState) {
    SettingsCard(title = "数据", subtitle = "本地账单与预算概览。") {
        SettingInfoRow("预算", state.ledgerBudgetText)
        SettingInfoRow("账单数量", "${state.ledgerRecords.size} 笔")
        SettingInfoRow("默认分类", state.ledgerDraftCategory)
    }
}

@Composable
private fun ServiceSettingsPanel(state: AssistantUiState, aiEndpoint: String) {
    SettingsCard(title = "服务", subtitle = "AI Worker 与 Supabase 账号。") {
        SettingInfoRow("AI 接口", if (aiEndpoint.isBlank()) "未配置，使用本地占位回复" else aiEndpoint)
        SettingInfoRow("执行模式", "云端理解，本地确认后执行")
        AccountSettingsCard(state)
    }
}

@Composable
private fun AdvancedSettingsPanel(
    state: AssistantUiState,
    onBackdropChange: (BackdropDebugParams) -> Unit,
    onBorderChange: (GlassBorderStyle) -> Unit
) {
    SettingsCard(title = "高级", subtitle = "只暴露安全的外观参数，不改变 OpenGL Host 尺寸链。") {
        SettingSlider("背景亮度", state.backdropParams.brightness, 0.7f..1.45f) {
            onBackdropChange(state.backdropParams.copy(brightness = it))
        }
        SettingSlider("背景对比", state.backdropParams.contrast, 0.7f..1.35f) {
            onBackdropChange(state.backdropParams.copy(contrast = it))
        }
        SettingSlider("顶部高光", state.glassBorderStyle.topHighlightAlpha, 0f..1.6f) {
            onBorderChange(state.glassBorderStyle.copy(topHighlightAlpha = it))
        }
        SettingSlider("底部阴影", state.glassBorderStyle.bottomShadowAlpha, 0f..1.2f) {
            onBorderChange(state.glassBorderStyle.copy(bottomShadowAlpha = it))
        }
    }
}

@Composable
private fun DebugSettingsPanel(state: AssistantUiState, aiEndpoint: String) {
    SettingsCard(title = "调试", subtitle = "用于快速确认关键状态。") {
        SettingInfoRow("质量", state.quality.name)
        SettingInfoRow("玻璃预设", state.glassPreset.name)
        SettingInfoRow("背景", state.backgroundTheme.name)
        SettingInfoRow("AI Endpoint", if (aiEndpoint.isBlank()) "empty" else aiEndpoint)
    }
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
                    current?.accessToken?.takeIf { it.isNotBlank() }?.let { token -> runCatching { authClient.signOut(token) } }
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("账号与同步", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(session?.email ?: "未登录 · 本地模式", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AccountStatusPill(session?.isUsable == true)
        }

        if (session?.isUsable != true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthModeChip("登录", authMode == AccountAuthMode.Login, state, Modifier.weight(1f)) { authMode = AccountAuthMode.Login }
                AuthModeChip("注册", authMode == AccountAuthMode.Register, state, Modifier.weight(1f)) { authMode = AccountAuthMode.Register }
            }
            SettingTextField(emailInput, { emailInput = it.take(80) }, "邮箱 name@example.com", KeyboardType.Email, VisualTransformation.None, !loading)
            SettingTextField(passwordInput, { passwordInput = it.take(72) }, "密码至少 6 位", KeyboardType.Password, PasswordVisualTransformation(), !loading)
            SettingButton(
                title = if (loading) "处理中…" else if (authMode == AccountAuthMode.Register) "注册" else "登录",
                subtitle = if (authMode == AccountAuthMode.Register) "Supabase 邮箱注册" else "邮箱密码登录",
                onClick = { submitAuth() }
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingButton("刷新会话", if (loading) "处理中" else "更新 token", { refreshLogin() }, Modifier.weight(1f))
                SettingButton("退出登录", "回到本地模式", { logout() }, Modifier.weight(1f))
            }
        }

        Text(
            message,
            color = when (tone) {
                AccountMessageTone.Success -> Color(0xFF8DF9EA).copy(alpha = 0.88f)
                AccountMessageTone.Error -> Color(0xFFFFB4B4).copy(alpha = 0.92f)
                AccountMessageTone.Normal -> Color.White.copy(alpha = 0.56f)
            },
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsCard(
    title: String? = null,
    subtitle: String? = null,
    content: @Composable Column.() -> Unit
) {
    GlassPanel(
        quality = RenderQuality.Balanced,
        glassIntensity = 0.92f,
        motionIntensity = 0.55f,
        radius = 26,
        modifier = Modifier.fillMaxWidth(),
        role = GlassRole.Card
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF151A4F).copy(alpha = 0.30f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            if (title != null) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold) }
            }
            content()
        }
    }
}

@Composable
private fun StatusMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PanelChip(title: String, value: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.075f)
    Column(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color.White.copy(alpha = if (selected) 0.96f else 0.72f), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = Color.White.copy(alpha = if (selected) 0.70f else 0.46f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingButton(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.90f), fontSize = 14.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
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
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(Color.White.copy(alpha = 0.90f)),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        visualTransformation = visualTransformation,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text(placeholder, color = Color.White.copy(alpha = 0.34f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                inner()
            }
        }
    )
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
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = if (selected) 0.96f else 0.62f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ThemeOptions(selected: BackgroundTheme, onSelected: (BackgroundTheme) -> Unit) {
    BackgroundTheme.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { theme ->
                PanelChip(themeLabel(theme), theme.storageValue, selected == theme, Modifier.weight(1f)) { onSelected(theme) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun <T> OptionRow(items: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> PanelChip(label(item), if (selected == item) "当前" else "可选", selected == item, Modifier.weight(1f)) { onSelected(item) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun qualityLabel(value: RenderQuality): String = when (value) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun glassPresetLabel(value: GlassPreset): String = when (value) {
    GlassPreset.Basic -> "Basic"
    GlassPreset.Blur -> "Blur"
    GlassPreset.Liquid -> "Liquid"
    GlassPreset.Safe -> "Safe"
}

private fun themeLabel(value: BackgroundTheme): String = when (value) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠海雾"
    BackgroundTheme.Sunset -> "暮色流光"
    BackgroundTheme.Dawn -> "晨曦珍珠"
}

private fun Throwable.friendlyAuthMessage(): String {
    val text = message.orEmpty().trim()
    return when {
        text.isBlank() -> "账号服务暂时不可用，请稍后重试。"
        text.contains("timeout", ignoreCase = true) -> "账号服务请求超时，请检查网络。"
        else -> text
    }
}
