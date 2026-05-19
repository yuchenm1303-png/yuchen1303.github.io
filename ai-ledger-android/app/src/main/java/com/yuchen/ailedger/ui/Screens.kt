package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.ToolEntry
import kotlin.math.roundToInt

@Composable
fun AssistantScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "AI ASSISTANT",
                title = "AI 助手",
                subtitle = "把对话放在中央，常用动作收进轻量入口。"
            )
        }
        item { AssistantStatusRow(state) }
        item { AssistantConversationCard(state) }
        item { QuickCommandPanel(state) }
    }
}

@Composable
fun ToolsScreen(state: AssistantUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "TOOLS",
                title = "功能",
                subtitle = "手机任务、账本工具和快捷指令集中放在这里。"
            )
        }
        item { ToolsHeroCard(state) }
        items(toolEntries(state), key = { it.title }) { tool ->
            ToolListCard(tool = tool, state = state)
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "SETTINGS",
                title = "设置",
                subtitle = "外观、性能、偏好和服务状态。"
            )
        }
        item {
            SettingsGlassCard(
                state = state,
                onQualityChange = onQualityChange,
                onGlassPresetChange = onGlassPresetChange,
                onBackgroundThemeChange = onBackgroundThemeChange,
                onGlassIntensityChange = onGlassIntensityChange,
                onMotionIntensityChange = onMotionIntensityChange
            )
        }
        item {
            ToggleSettingCard(
                title = "聊天预览",
                subtitle = "保留首页里的示例对话和快捷建议。",
                checked = state.showPreviewConversation,
                onCheckedChange = onPreviewConversationChange,
                state = state
            )
        }
        item { ServiceStatusCard(aiEndpoint = aiEndpoint, state = state) }
        item { SettingsShortcutList(state) }
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = eyebrow,
            color = Color(0xFF8DF9EA).copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AssistantStatusRow(state: AssistantUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        StatusPill("在线", "Gemini 2.5", state, Modifier.weight(1.12f), accent = Color(0xFF8DF9EA))
        StatusPill("识图", "可用", state, Modifier.weight(0.88f), accent = Color(0xFF9EB7FF))
        StatusPill("本地", "待命", state, Modifier.weight(0.88f), accent = Color(0xFFFFD166))
    }
}

@Composable
private fun StatusPill(label: String, value: String, state: AssistantUiState, modifier: Modifier, accent: Color) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.96f,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = modifier.height(48.dp),
        role = GlassRole.Chip
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = Color.White.copy(alpha = 0.93f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AssistantConversationCard(state: AssistantUiState) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 34,
        modifier = Modifier.fillMaxWidth(),
        role = GlassRole.Shell
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModelSelectorRow(state)
            ConversationPreview(state)
            SuggestionRow(state)
            ComposerBar(state)
        }
    }
}

@Composable
private fun ModelSelectorRow(state: AssistantUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = 999,
            modifier = Modifier.weight(1f).height(44.dp),
            role = GlassRole.Chip
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("Gemini 2.5 Flash", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text("切换", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        PressableGlass(
            quality = state.quality,
            glassIntensity = state.glassIntensity,
            motionIntensity = state.motionIntensity,
            radius = 999,
            modifier = Modifier.width(76.dp).height(44.dp),
            role = GlassRole.Floating
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("识图", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ConversationPreview(state: AssistantUiState) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 0.92f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier.fillMaxWidth(),
        role = GlassRole.Card
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            val visibleMessages = if (state.showPreviewConversation) previewMessages(state) else emptyList()
            if (visibleMessages.isEmpty()) {
                EmptyConversationState(state)
            } else {
                visibleMessages.forEach { message ->
                    MessageBubble(
                        text = message.text,
                        fromUser = message.role == MessageRole.User,
                        state = state
                    )
                }
            }
        }
    }
}

private data class PreviewMessage(val text: String, val role: MessageRole)

private fun previewMessages(state: AssistantUiState): List<PreviewMessage> {
    val assistantText = state.messages.firstOrNull { it.role == MessageRole.Assistant }?.text
        ?: "我可以帮你记账、识别图片文字、设置提醒，也能把复杂任务拆成一步步执行。"
    return listOf(
        PreviewMessage("帮我整理一下今天的支出", MessageRole.User),
        PreviewMessage(assistantText, MessageRole.Assistant),
        PreviewMessage("顺便提醒我晚上复盘", MessageRole.User)
    )
}

@Composable
private fun EmptyConversationState(state: AssistantUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("对话预览已隐藏", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("输入框会保留，首页更接近真实空白状态。", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp)
    }
}

@Composable
private fun MessageBubble(text: String, fromUser: Boolean, state: AssistantUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        val width = if (fromUser) 0.74f else 0.92f
        val role = if (fromUser) GlassRole.Floating else GlassRole.Card
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity * if (fromUser) 1.08f else 0.98f,
            motionIntensity = state.motionIntensity,
            radius = 24,
            modifier = Modifier.fillMaxWidth(width),
            role = role
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (fromUser) 0.96f else 0.86f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun SuggestionRow(state: AssistantUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("记一笔", "设提醒", "识别图片").forEach { action ->
            SmallGlassButton(text = action, state = state, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ComposerBar(state: AssistantUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CircleGlassButton("+", state, size = 54)
        ComposerInputGlass(state = state, modifier = Modifier.weight(1f))
        CircleGlassButton("↑", state, size = 54)
    }
}

@Composable
private fun ComposerInputGlass(state: AssistantUiState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "composer-sheen")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "composer-sheen-value"
    )
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, modifier.height(54.dp), GlassRole.Card) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.020f + 0.024f * sweep),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "和我说点什么...",
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 17.dp)
            )
        }
    }
}

@Composable
private fun QuickCommandPanel(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            SectionHeader("快捷指令", "把高频动作放在对话框外侧")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickActionButton("账单", "查明细", state, Modifier.weight(1f))
                QuickActionButton("导航", "回家", state, Modifier.weight(1f))
                QuickActionButton("闹钟", "提醒", state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 24,
        modifier = modifier.height(76.dp),
        role = GlassRole.Chip
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ToolsHeroCard(state: AssistantUiState) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.04f,
        motionIntensity = state.motionIntensity,
        radius = 32,
        modifier = Modifier.fillMaxWidth(),
        role = GlassRole.Shell
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("功能中心", color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("保留入口，逐步接入旧版功能", color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                }
                Text("整理", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "先把能力分区放稳：账本、提醒、应用控制和快捷任务都可以从这里进入。",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetricGlass("今日支出", "¥47", state, Modifier.weight(1f))
                MiniMetricGlass("待接入", "6", state, Modifier.weight(1f))
                MiniMetricGlass("状态", "预览", state, Modifier.weight(1f))
            }
        }
    }
}

private fun toolEntries(state: AssistantUiState): List<ToolEntry> {
    if (state.tools.isNotEmpty()) {
        return state.tools
    }
    return listOf(
        ToolEntry("账单中心", "查看和管理收入支出"),
        ToolEntry("数据统计", "按周、月、年查看趋势"),
        ToolEntry("提醒闹钟", "创建提醒和闹钟"),
        ToolEntry("应用控制", "打开微信、支付宝等应用"),
        ToolEntry("快捷指令", "保存常用任务"),
        ToolEntry("任务记录", "查看助手执行历史")
    )
}

@Composable
private fun ToolListCard(tool: ToolEntry, state: AssistantUiState) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier.fillMaxWidth().height(88.dp),
        role = GlassRole.Card
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 18, Modifier.size(50.dp), GlassRole.Chip) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(toolGlyph(tool.title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayToolTitle(tool.title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(displayToolSubtitle(tool), color = Color.White.copy(alpha = 0.56f), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color.White.copy(alpha = 0.70f), fontSize = 34.sp, fontWeight = FontWeight.Light)
        }
    }
}

private fun displayToolTitle(title: String): String {
    return when {
        title.contains("账单") -> title
        title.contains("数据") -> title
        title.contains("提醒") || title.contains("闹钟") -> title
        title.contains("应用") -> title
        title.contains("快捷") -> title
        title.contains("任务") -> title
        title.contains("璐") || title.contains("鍗") -> "账单中心"
        title.contains("鏁") || title.contains("缁") -> "数据统计"
        title.contains("鎻") || title.contains("闂") -> "提醒闹钟"
        title.contains("搴") || title.contains("鎺") -> "应用控制"
        title.contains("蹇") || title.contains("鎸") -> "快捷指令"
        title.contains("浠") || title.contains("璁") -> "任务记录"
        else -> title.ifBlank { "功能入口" }
    }
}

private fun displayToolSubtitle(tool: ToolEntry): String {
    val title = displayToolTitle(tool.title)
    return when (title) {
        "账单中心" -> "查看和管理收入支出"
        "数据统计" -> "按周、月、年查看趋势"
        "提醒闹钟" -> "创建提醒和闹钟"
        "应用控制" -> "打开微信、支付宝等应用"
        "快捷指令" -> "保存常用任务"
        "任务记录" -> "查看助手执行历史"
        else -> tool.subtitle
    }
}

private fun toolGlyph(title: String): String = when (displayToolTitle(title)) {
    "账单中心" -> "账"
    "数据统计" -> "图"
    "提醒闹钟" -> "铃"
    "应用控制" -> "启"
    "快捷指令" -> "令"
    else -> "记"
}

@Composable
private fun SettingsGlassCard(
    state: AssistantUiState,
    onQualityChange: (RenderQuality) -> Unit,
    onGlassPresetChange: (GlassPreset) -> Unit,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onMotionIntensityChange: (Float) -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader("玻璃与性能", "真机调试时优先使用均衡档")
            SegmentedQuality(state, onQualityChange)
            GlassPresetSelector(state, onGlassPresetChange)
            ThemeSelector(state, onBackgroundThemeChange)
            SliderLine("玻璃强度", state.glassIntensity, onGlassIntensityChange, 0.6f..1.4f)
            SliderLine("动态强度", state.motionIntensity, onMotionIntensityChange, 0f..1.4f)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SegmentedQuality(state: AssistantUiState, onQualityChange: (RenderQuality) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RenderQuality.entries.forEach { quality ->
            val selected = state.quality == quality
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 20,
                modifier = Modifier.weight(1f).height(52.dp),
                role = if (selected) GlassRole.Floating else GlassRole.Chip,
                onClick = { onQualityChange(quality) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(qualityLabel(quality), color = Color.White.copy(alpha = if (selected) 1f else 0.68f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GlassPresetSelector(state: AssistantUiState, onGlassPresetChange: (GlassPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassPreset.entries.forEach { preset ->
            val selected = state.glassPreset == preset
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 20,
                modifier = Modifier.weight(1f).height(50.dp),
                role = if (selected) GlassRole.Floating else GlassRole.Chip,
                onClick = { onGlassPresetChange(preset) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(preset.label, color = Color.White.copy(alpha = if (selected) 1f else 0.68f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ThemeSelector(state: AssistantUiState, onBackgroundThemeChange: (BackgroundTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BackgroundTheme.entries.forEach { theme ->
            val selected = state.backgroundTheme == theme
            PressableGlass(
                quality = state.quality,
                glassIntensity = state.glassIntensity,
                motionIntensity = state.motionIntensity,
                radius = 20,
                modifier = Modifier.weight(1f).height(46.dp),
                role = if (selected) GlassRole.Floating else GlassRole.Chip,
                onClick = { onBackgroundThemeChange(theme) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(themeLabel(theme), color = Color.White.copy(alpha = if (selected) 1f else 0.66f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SliderLine(label: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${value.format2x()}x", color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun ToggleSettingCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    state: AssistantUiState
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp, lineHeight = 20.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ServiceStatusCard(aiEndpoint: String, state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("服务状态", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("当前是 Compose 迁移预览版，后续会接入云同步和 AI 解析服务。", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp, lineHeight = 20.sp)
            Text(aiEndpoint, color = Color.White.copy(alpha = 0.36f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsShortcutList(state: AssistantUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsListCard("账号与同步", "登录状态、云端同步和本地备份。", state)
        SettingsListCard("手机偏好", "家庭地址、默认地图、提醒和闹钟。", state)
        SettingsListCard("数据与预算", "预算、账单、导出和清空记录。", state)
    }
}

@Composable
private fun SettingsListCard(title: String, subtitle: String, state: AssistantUiState) {
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 26,
        modifier = Modifier.fillMaxWidth().height(78.dp),
        role = GlassRole.Card
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color.White.copy(alpha = 0.62f), fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun MiniMetricGlass(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 22,
        modifier = modifier.height(74.dp),
        role = GlassRole.Card
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.96f), fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun SmallGlassButton(text: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 22, modifier.height(46.dp), GlassRole.Chip) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun CircleGlassButton(text: String, state: AssistantUiState, size: Int) {
    val transition = rememberInfiniteTransition(label = "send-btn-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = if (state.motionIntensity > 0f) 1.018f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "send-btn-pulse-value"
    )
    PressableGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity,
        motionIntensity = state.motionIntensity,
        radius = 999,
        modifier = Modifier.size(size.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
        role = GlassRole.Floating
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = if (text == "+") 28.sp else 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun LiquidBottomBar(
    currentTab: AppTab,
    quality: RenderQuality,
    glassIntensity: Float,
    motionIntensity: Float,
    onTabChange: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(quality, glassIntensity, motionIntensity, 30, modifier.fillMaxWidth().height(62.dp), GlassRole.Nav) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            val tabCount = AppTab.entries.size
            val slot = maxWidth / tabCount
            val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0)
            val indicatorX by animateDpAsState(slot * target.toFloat(), animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-x")
            val indicatorW by animateDpAsState(slot - 8.dp, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-w")

            GlassPanel(
                quality = quality,
                glassIntensity = glassIntensity * 1.08f,
                motionIntensity = motionIntensity,
                radius = 24,
                modifier = Modifier.offset(x = indicatorX + 4.dp, y = 1.dp).width(indicatorW).height(48.dp),
                role = GlassRole.Floating
            ) {}

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(interactionSource = interaction, indication = null) { onTabChange(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(navIcon(tab), color = Color.White.copy(alpha = if (selected) 0.98f else 0.52f), fontSize = 16.sp, maxLines = 1)
                        Text(tabLabel(tab), color = Color.White.copy(alpha = if (selected) 0.96f else 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

fun tabLabel(tab: AppTab): String = when (tab) {
    AppTab.Assistant -> "AI 助手"
    AppTab.Tools -> "功能"
    AppTab.Settings -> "设置"
}

fun navIcon(tab: AppTab): String = when (tab) {
    AppTab.Assistant -> "✦"
    AppTab.Tools -> "▦"
    AppTab.Settings -> "⚙"
}

private fun qualityLabel(quality: RenderQuality): String = when (quality) {
    RenderQuality.Smooth -> "流畅"
    RenderQuality.Balanced -> "均衡"
    RenderQuality.Experimental -> "高画质"
}

private fun themeLabel(theme: BackgroundTheme): String = when (theme) {
    BackgroundTheme.Aurora -> "极光"
    BackgroundTheme.Jade -> "翡翠"
    BackgroundTheme.Sunset -> "暮色"
    BackgroundTheme.Dawn -> "晨雾"
}

private fun Float.format2x(): String {
    return (this * 100).roundToInt().div(100f).toString()
}
