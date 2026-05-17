package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.ToolEntry
import kotlin.math.roundToInt

private enum class SettingsDetail { Account, Display, Phone, Background, Data }

@Composable
fun AssistantScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 20.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { AssistantTopBar(state) }
        item { AssistantHomePanel(state) }
        item { RecentTaskStrip(state) }
    }
}

@Composable
private fun AssistantTopBar(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TopCommandPill("清空", state, Modifier.width(76.dp).height(34.dp))
        TopCommandPill("◎", "联网", state, Modifier.width(106.dp).height(34.dp))
        Spacer(Modifier.weight(1f))
        TopCommandPill("轻量", state, Modifier.width(72.dp).height(34.dp))
    }
}

@Composable
private fun AssistantHomePanel(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HeaderChip("AI", "自动", state, Modifier.weight(1f))
                HeaderChip("✦", "轻量待命", state, Modifier.weight(1.35f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricMiniCard("今日支出", "¥47.00", state, Modifier.weight(1f))
                MetricMiniCard("本月结余", "¥52.50", state, Modifier.weight(1f))
            }
            state.messages.firstOrNull()?.let { message -> CompactAssistantMessage(message, state) } ?: PreviewHiddenCard(state)
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.fillMaxWidth().height(42.dp), GlassRole.Floating) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0x9C5173FF), Color(0xA86E45DD)))).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                    Text("导航回家", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("提醒", "回家", "记账").forEach { action -> SmallGlassButton(action, state, Modifier.weight(1f)) }
            }
            ComposerBar(state)
            Text("本地动作优先识别，复杂问题再交给云端。", color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricMiniCard(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 20, modifier.height(72.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun CompactAssistantMessage(message: ChatMessage, state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = message.text,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.width(96.dp).height(30.dp), GlassRole.Chip) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.70f)))
                    Text("内置回复", color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecentTaskStrip(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("快捷动作", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("查账单", "开应用", "设闹钟", "记一笔").forEach { label ->
                    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 16, Modifier.weight(1f).height(42.dp), GlassRole.Chip) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = Color.White.copy(alpha = 0.84f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, state: AssistantUiState) {
    val isUser = message.role == MessageRole.User
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (isUser) {
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, Modifier.fillMaxWidth(0.70f), GlassRole.Floating) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xAA4C6CFF), Color(0xA8784CE6)))).padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(message.text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            CompactAssistantMessage(message, state)
        }
    }
}

@Composable
private fun TopCommandPill(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier, GlassRole.Chip) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
    }
}

@Composable
private fun TopCommandPill(icon: String, text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier, GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, color = Color(0xFF8DF9EA), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.42f)))
        }
    }
}

@Composable
private fun HeaderChip(icon: String, text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, modifier.height(42.dp), GlassRole.Chip) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(icon, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ComposerBar(state: AssistantUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(42.dp), GlassRole.Floating) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
        ComposerInputGlass(state = state, modifier = Modifier.weight(1f))
        CircleGlassButton("➤", state)
    }
}

@Composable
private fun ComposerInputGlass(state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 22, modifier.height(42.dp), GlassRole.Card) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { Text("和我说点什么", color = Color.White.copy(alpha = 0.48f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 13.dp)) }
    }
}

@Composable
fun ToolsScreen(state: AssistantUiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 190.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenHero(title = "功能", subtitle = "账单、提醒、应用控制和快捷任务") }
        item { ToolsOverviewCard(state) }
        items(state.tools.chunked(2), key = { row -> row.joinToString { it.title } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tool -> ToolTile(tool, state, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolsOverviewCard(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 24, Modifier.fillMaxWidth().height(82.dp), GlassRole.Shell) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 16, Modifier.size(42.dp), GlassRole.Floating) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("AI 手机动作", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1); Text("原 Web 工具迁到原生 Compose", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, lineHeight = 17.sp) }
            Text("›", color = Color.White.copy(alpha = 0.66f), fontSize = 27.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ToolTile(tool: ToolEntry, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, modifier.height(108.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 14, Modifier.size(34.dp), GlassRole.Chip) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tool.icon, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) } }
            Text(tool.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tool.subtitle, color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SettingsScreen(
    state: AssistantUiState,
    aiEndpoint: String,
    onQualityChange: (RenderQuality) -> Unit,
    onPreviewConversationChange: (Boolean) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit
) {
    var detail by remember { mutableStateOf<SettingsDetail?>(null) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 220.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ScreenHero(kicker = "设置中心", title = "设置", subtitle = "账号、显示、手机动作与数据") }
            item { SettingsSection("账号", state) { SettingsCompactRow("☁", "账号与同步", "登录、云端 AI、Worker 连接", state) { detail = SettingsDetail.Account } } }
            item { SettingsSection("体验", state) {
                SettingsCompactRow("Aa", "显示与语言", "语言、字体、玻璃和动效", state) { detail = SettingsDetail.Display }
                SettingsCompactRow("✦", "背景外观", "四套背景与玻璃预设", state) { detail = SettingsDetail.Background }
            } }
            item { SettingsSection("手机", state) { SettingsCompactRow("⌖", "手机偏好", "家庭地址、默认地图和任务偏好", state) { detail = SettingsDetail.Phone } } }
            item { CompactBackgroundThemeSelector(state, onBackgroundThemeChange) }
            item { SettingsSection("数据", state) {
                SettingsCompactRow("▤", "数据与预算", "预算、导出、清理记录", state) { detail = SettingsDetail.Data }
                PreviewSwitchCompactRow(state, onPreviewConversationChange)
            } }
            item { CompactGlassPerformanceCard(state, onGlassPresetChange, onGlassIntensityChange, onMotionIntensityChange) }
        }
        AnimatedVisibility(visible = detail != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            detail?.let { SettingsDetailOverlay(it, state, aiEndpoint, onDismiss = { detail = null }, onBackgroundThemeChange = onBackgroundThemeChange, onGlassPresetChange = onGlassPresetChange) }
        }
    }
}

@Composable
private fun ScreenHero(kicker: String? = null, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        kicker?.let { Text(it, color = Color(0xFF76F2FF), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
        Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SettingsSection(title: String, state: AssistantUiState, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 22, Modifier.fillMaxWidth(), GlassRole.Card) {
            Column(Modifier.padding(vertical = 5.dp), content = content)
        }
    }
}

@Composable
private fun SettingsCompactRow(icon: String, title: String, subtitle: String, state: AssistantUiState, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(18.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }.padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 14, Modifier.size(36.dp), GlassRole.Chip) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(icon, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) } }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = Color.White.copy(alpha = 0.60f), fontSize = 25.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun PreviewSwitchCompactRow(state: AssistantUiState, onPreviewConversationChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 14, Modifier.size(36.dp), GlassRole.Chip) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("◎", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("聊天预览", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("保留示例对话和快捷指令", color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp)
        }
        Switch(checked = state.showPreviewConversation, onCheckedChange = onPreviewConversationChange)
    }
}

@Composable
private fun CompactBackgroundThemeSelector(state: AssistantUiState, onBackgroundThemeChange: (BackgroundTheme) -> Unit) {
    SettingsSection("背景", state) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
            BackgroundTheme.entries.forEach { theme ->
                val selected = state.backgroundTheme == theme
                PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 15, Modifier.weight(1f).height(42.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onBackgroundThemeChange(theme) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(theme.label, color = Color.White.copy(alpha = if (selected) 1f else 0.68f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun CompactGlassPerformanceCard(state: AssistantUiState, onGlassPresetChange: (GlassPreset) -> Unit, onGlassIntensityChange: (Float) -> Unit, onMotionIntensityChange: (Float) -> Unit) {
    SettingsSection("性能", state) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                GlassPreset.entries.forEach { preset ->
                    val selected = state.glassPreset == preset
                    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 14, Modifier.weight(1f).height(36.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onGlassPresetChange(preset) }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(preset.label, color = Color.White.copy(alpha = if (selected) 1f else 0.68f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }
                }
            }
            Text("玻璃强度 ${state.glassIntensity.format2x()}x", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
            Slider(value = state.glassIntensity, onValueChange = onGlassIntensityChange, valueRange = 0.6f..1.4f)
            Text("动态强度 ${state.motionIntensity.format2x()}x", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
            Slider(value = state.motionIntensity, onValueChange = onMotionIntensityChange, valueRange = 0f..1.4f)
        }
    }
}

@Composable
private fun SettingsDetailOverlay(detail: SettingsDetail, state: AssistantUiState, aiEndpoint: String, onDismiss: () -> Unit, onBackgroundThemeChange: (BackgroundTheme) -> Unit, onGlassPresetChange: (GlassPreset) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99030A18)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }, contentAlignment = Alignment.BottomCenter) {
        GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth().padding(bottom = 92.dp), GlassRole.Shell) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(detailTitle(detail), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black); Text(detailSubtitle(detail), color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, lineHeight = 17.sp) }
                    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(36.dp), GlassRole.Chip, onClick = onDismiss) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("×", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) } }
                }
                when (detail) {
                    SettingsDetail.Account -> DetailRows(state, listOf("登录状态：本地预览", "云端 AI：Worker 待连接", "当前端点：$aiEndpoint", "同步策略：稍后接入"))
                    SettingsDetail.Display -> DetailRows(state, listOf("语言：简体中文", "字体大小：标准", "玻璃透明度：跟随当前强度", "动画效果：跟随性能模式"))
                    SettingsDetail.Phone -> DetailRows(state, listOf("家庭地址：未设置", "默认地图：系统默认", "打开应用：等待接入无障碍动作", "本地指令：优先识别"))
                    SettingsDetail.Data -> DetailRows(state, listOf("预算：稍后接入", "导出记录：稍后接入", "清空记录：稍后接入", "本地缓存：DataStore"))
                    SettingsDetail.Background -> { CompactBackgroundThemeSelector(state, onBackgroundThemeChange); Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { GlassPreset.entries.forEach { preset -> PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 14, Modifier.weight(1f).height(38.dp), if (state.glassPreset == preset) GlassRole.Floating else GlassRole.Chip, onClick = { onGlassPresetChange(preset) }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(preset.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } } } }
                }
            }
        }
    }
}

private fun detailTitle(detail: SettingsDetail) = when (detail) { SettingsDetail.Account -> "账号与同步"; SettingsDetail.Display -> "显示与语言"; SettingsDetail.Phone -> "手机偏好"; SettingsDetail.Background -> "背景外观"; SettingsDetail.Data -> "数据与预算" }
private fun detailSubtitle(detail: SettingsDetail) = when (detail) { SettingsDetail.Account -> "登录、云端 AI、Worker 连接和同步状态。"; SettingsDetail.Display -> "语言、字体、透明度和动效的原生入口。"; SettingsDetail.Phone -> "为导航、打开应用和本地动作提供默认参数。"; SettingsDetail.Background -> "切换 Web 版四套背景和玻璃预设。"; SettingsDetail.Data -> "预算、导出、清理记录等数据工具。" }

@Composable
private fun DetailRows(state: AssistantUiState, rows: List<String>) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { rows.forEach { text -> GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 16, Modifier.fillMaxWidth().height(38.dp), GlassRole.Card) { Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { Text(text, color = Color.White.copy(alpha = 0.80f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } } }

@Composable private fun PreviewHiddenCard(state: AssistantUiState) { GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 20, Modifier.fillMaxWidth(), GlassRole.Card) { Column(Modifier.padding(13.dp)) { Text("预览对话已隐藏", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text("现在展示的是更接近真实聊天入口的空白态。", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, lineHeight = 17.sp) } } }
@Composable private fun SmallGlassButton(text: String, state: AssistantUiState, modifier: Modifier = Modifier) { PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(34.dp), GlassRole.Chip) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) } } }
@Composable private fun CircleGlassButton(text: String, state: AssistantUiState) { PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(42.dp), GlassRole.Floating) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) } } }

@Composable
fun LiquidBottomBar(currentTab: AppTab, quality: RenderQuality, glassIntensity: Float, motionIntensity: Float, onTabChange: (AppTab) -> Unit, modifier: Modifier = Modifier) { GlassPanel(quality, glassIntensity, motionIntensity, 30, modifier.fillMaxWidth().height(72.dp), GlassRole.Nav) { BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(6.dp)) { val slot = maxWidth / AppTab.entries.size; val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0); val indicatorX by animateDpAsState(slot * target.toFloat(), animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-x"); val indicatorW by animateDpAsState(slot - 8.dp, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-w"); GlassPanel(quality, glassIntensity * 1.18f, motionIntensity, 22, Modifier.offset(x = indicatorX + 4.dp, y = 1.dp).width(indicatorW).height(58.dp), GlassRole.Floating) {}; Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { AppTab.entries.forEach { tab -> val selected = tab == currentTab; val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press"); Column(modifier = Modifier.weight(1f).height(60.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(24.dp)).clickable(interactionSource = interaction, indication = null) { onTabChange(tab) }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(navIcon(tab), color = Color.White.copy(alpha = if (selected) 0.98f else 0.55f), fontSize = 19.sp, maxLines = 1); Spacer(Modifier.height(1.dp)); Text(tab.title, color = Color.White.copy(alpha = if (selected) 0.96f else 0.54f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } } } }

private fun navIcon(tab: AppTab): String = when (tab) { AppTab.Assistant -> "✦"; AppTab.Tools -> "▦"; AppTab.Settings -> "⚙" }
private fun Float.format2x(): String = (this * 100).roundToInt().div(100f).toString()
