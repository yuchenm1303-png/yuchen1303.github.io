package com.yuchen.ailedger.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AppTab
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.BackgroundTheme
import com.yuchen.ailedger.model.ChatMessage
import com.yuchen.ailedger.model.GlassPreset
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.MessageRole
import com.yuchen.ailedger.model.MessageStatus
import com.yuchen.ailedger.model.RenderQuality
import com.yuchen.ailedger.model.ToolEntry
import kotlin.math.roundToInt

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onDraftCommand: (String) -> Unit,
    onModelClick: () -> Unit,
    onPickImage: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateHome: () -> Unit,
    onSetAlarm: () -> Unit,
    onToggleOnline: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 14.dp, bottom = 78.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AssistantTopBar(
            state = state,
            onModelClick = onModelClick,
            onToggleOnline = onToggleOnline,
            onOpenTools = onOpenTools,
            onOpenSettings = onOpenSettings
        )
        ChatGlassPanel(state, Modifier.weight(1f), onDraftCommand, onPickImage)
        AssistantQuickActions(state, onQuickCommand, onNavigateHome, onSetAlarm, onPickImage)
        ComposerBar(state, onComposerChange, onSend, onPickImage)
    }
}

@Composable
private fun AssistantTopBar(
    state: AssistantUiState,
    onModelClick: () -> Unit,
    onToggleOnline: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val allowSwitch = !state.isSending
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("AI ASSISTANT", color = Color(0xFF8DF9EA).copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("AI 助手", color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                Text("直接说需求，我来帮你拆成动作。", color = Color.White.copy(alpha = 0.58f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyRoundAction("▦", state, onOpenTools)
                TinyRoundAction("⚙", state, onOpenSettings)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModelChip(state, Modifier.weight(1f), if (allowSwitch) onModelClick else {})
            StatusChip(
                label = "联网",
                value = if (state.onlineEnabled) "已开启" else "已关闭",
                accent = if (state.onlineEnabled) Color(0xFF8DF9EA) else Color(0xFF9EB7FF),
                state = state,
                modifier = Modifier.weight(0.72f),
                onClick = if (allowSwitch) onToggleOnline else {}
            )
        }
    }
}

@Composable
private fun ModelChip(state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(44.dp), GlassRole.Chip, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(state.selectedModelLabel, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.onlineEnabled) "联网已开" else "纯文本模式", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(if (state.isSending) "发送中" else "切换", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String, accent: Color, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 999, modifier.height(44.dp), GlassRole.Chip, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TinyRoundAction(text: String, state: AssistantUiState, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(42.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ChatGlassPanel(state: AssistantUiState, modifier: Modifier, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 34, modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("对话", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(if (state.isSending) "云端生成中" else "可上下滑动", color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.messages, key = { it.id }) { message -> MessageBubble(message = message, state = state) }
                item { StarterSuggestions(state, onDraftCommand, onPickImage) }
            }
        }
    }
}

@Composable
private fun StarterSuggestions(state: AssistantUiState, onDraftCommand: (String) -> Unit, onPickImage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
        Text("可以这样说", color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallGlassButton("记一笔", state, Modifier.weight(1f)) { onDraftCommand("记一笔 午饭 18 元") }
            SmallGlassButton("设提醒", state, Modifier.weight(1f)) { onDraftCommand("今晚 9 点半提醒我复盘") }
            SmallGlassButton("识图", state, Modifier.weight(1f), onClick = onPickImage)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, state: AssistantUiState) {
    val fromUser = message.role == MessageRole.User
    val statusColor = when (message.status) {
        MessageStatus.Failed -> Color(0xFFFFB4B4)
        MessageStatus.Sending -> Color.White.copy(alpha = 0.72f)
        MessageStatus.Sent -> Color.White.copy(alpha = if (fromUser) 0.97f else 0.86f)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        GlassPanel(
            quality = state.quality,
            glassIntensity = state.glassIntensity * if (fromUser) 1.08f else 0.96f,
            motionIntensity = state.motionIntensity,
            radius = 24,
            modifier = Modifier.fillMaxWidth(if (fromUser) 0.78f else 0.92f),
            role = if (fromUser) GlassRole.Floating else GlassRole.Card
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = displayMessageText(message),
                    color = statusColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (fromUser) FontWeight.Bold else FontWeight.Medium
                )
                if (!fromUser) SourceBadgeRow(message)
            }
        }
    }
}

@Composable
private fun SourceBadgeRow(message: ChatMessage) {
    val badge = messageBadgeText(message) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(badgeColor(message).copy(alpha = 0.86f))
        )
        Text(
            text = badge,
            color = badgeColor(message).copy(alpha = 0.76f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun displayMessageText(message: ChatMessage): String = when (message.status) {
    MessageStatus.Sending -> message.text.ifBlank { "正在思考…" }
    MessageStatus.Failed -> message.errorText ?: message.text.ifBlank { "云端请求失败，请稍后再试。" }
    MessageStatus.Sent -> message.text
}

private fun messageBadgeText(message: ChatMessage): String? {
    val status = when (message.status) {
        MessageStatus.Sending -> "生成中"
        MessageStatus.Failed -> "请求失败"
        MessageStatus.Sent -> null
    }
    val main = message.modelLabel?.takeIf { it.isNotBlank() }
        ?: sourceReadableLabel(message.source)
        ?: status
    val source = sourceReadableLabel(message.source)
    val version = message.version?.takeIf { it.isNotBlank() }?.let { shortVersion(it) }
    return listOfNotNull(status, main, source, version)
        .distinct()
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

private fun sourceReadableLabel(source: String?): String? = when (source) {
    null, "" -> null
    "cloud_ai" -> "云端 AI"
    "workers_ai", "workers_ai_text_fallback" -> "Workers AI"
    "gemini_ai", "gemini_chat", "gemini_text_fallback" -> "Gemini"
    "kimi", "nvidia_chat" -> "Kimi / NIM"
    "mistral" -> "Mistral"
    "web_search_tool", "tavily_web_search", "tavily_ai_summary" -> "联网搜索"
    "cloud_fetch_failed" -> "云端连接失败"
    "cloud_error_normalized" -> "云端错误"
    "local" -> "本地"
    "local_ledger" -> "本地记账"
    "local_mobile" -> "手机动作"
    else -> source.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun badgeColor(message: ChatMessage): Color = when (message.status) {
    MessageStatus.Failed -> Color(0xFFFFB4B4)
    MessageStatus.Sending -> Color(0xFF8DF9EA)
    MessageStatus.Sent -> when (message.source) {
        "web_search_tool", "tavily_web_search", "tavily_ai_summary" -> Color(0xFF8DF9EA)
        "cloud_fetch_failed", "cloud_error_normalized" -> Color(0xFFFFB4B4)
        else -> Color.White
    }
}

private fun shortVersion(version: String): String {
    return version.removePrefix("2026-").removePrefix("android-").take(18)
}

@Composable
private fun AssistantQuickActions(state: AssistantUiState, onQuickCommand: (String) -> Unit, onNavigateHome: () -> Unit, onSetAlarm: () -> Unit, onPickImage: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickActionButton("记账", "写入草稿", state, Modifier.weight(1f)) { onQuickCommand("记一笔 咖啡 12 元") }
        QuickActionButton("回家", "打开地图", state, Modifier.weight(1f), onClick = onNavigateHome)
        QuickActionButton("提醒", "开闹钟", state, Modifier.weight(1f), onClick = onSetAlarm)
        QuickActionButton("图片", "选择识图", state, Modifier.weight(1f), onClick = onPickImage)
    }
}

@Composable
private fun QuickActionButton(title: String, subtitle: String, state: AssistantUiState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 24, modifier.height(64.dp), GlassRole.Chip, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ComposerBar(state: AssistantUiState, onComposerChange: (String) -> Unit, onSend: () -> Unit, onPickImage: () -> Unit) {
    val sendAction = if (state.isSending) ({}) else onSend
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        CircleGlassButton("+", state, size = 52, onClick = onPickImage)
        ComposerInputGlass(
            state = state,
            text = state.composerText,
            onTextChange = onComposerChange,
            onSend = sendAction,
            modifier = Modifier.weight(1f),
            placeholder = if (state.isSending) "正在等待云端回复..." else "和我说点什么..."
        )
        CircleGlassButton(if (state.isSending) "…" else "↑", state, size = 52, onClick = sendAction)
    }
}

@Composable
private fun ComposerInputGlass(
    state: AssistantUiState,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入内容...",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, modifier.height(52.dp), GlassRole.Card) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Color.White.copy(alpha = 0.85f)),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
            if (text.isBlank()) Text(placeholder, color = Color.White.copy(alpha = 0.46f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CircleGlassButton(text: String, state: AssistantUiState, size: Int, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.size(size.dp), GlassRole.Floating, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White, fontSize = if (text == "+") 28.sp else 22.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
fun ToolsScreen(
    state: AssistantUiState,
    onOpenTool: (String) -> Unit,
    onBack: () -> Unit,
    onLedgerTitleChange: (String) -> Unit,
    onLedgerAmountChange: (String) -> Unit,
    onLedgerTypeChange: (LedgerRecordType) -> Unit,
    onLedgerCategoryChange: (String) -> Unit,
    onLedgerBudgetChange: (String) -> Unit,
    onAddLedgerRecord: () -> Unit,
    onDeleteLedgerRecord: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    val selected = state.selectedToolTitle
    if (selected == "账单中心") {
        LedgerCenterScreen(state, onBack, onLedgerTitleChange, onLedgerAmountChange, onLedgerTypeChange, onLedgerCategoryChange, onLedgerBudgetChange, onAddLedgerRecord, onDeleteLedgerRecord, onOpenAssistant)
    } else if (selected != null) {
        ToolDetailPlaceholder(state, selected, onBack, onOpenAssistant)
    } else {
        ToolsHomeScreen(state, onOpenTool)
    }
}

@Composable
private fun ToolsHomeScreen(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 116.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("TOOLS", "功能", "把手机能力变成助手可以执行的动作。") }
        item { ToolsHeroCard(state) }
        item { ToolGrid(state, onOpenTool) }
        items(toolEntries(state), key = { it.title }) { tool -> ToolListCard(tool = tool, state = state, onClick = { onOpenTool(displayToolTitle(tool.title)) }) }
    }
}

@Composable
private fun ToolGrid(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("账单中心", "提醒闹钟", "应用控制").forEach { title ->
            QuickActionButton(title.take(2), when (title) { "账单中心" -> "明细/预算"; "提醒闹钟" -> "系统入口"; else -> "打开应用" }, state, Modifier.weight(1f)) { onOpenTool(title) }
        }
    }
}

@Composable
private fun LedgerCenterScreen(
    state: AssistantUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (LedgerRecordType) -> Unit,
    onCategoryChange: (String) -> Unit,
    onBudgetChange: (String) -> Unit,
    onAddRecord: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    onOpenAssistant: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 116.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DetailHeader("账单中心", "手动记账、预算、分类和最近明细", state, onBack) }
        item { LedgerSummaryCard(state) }
        item { LedgerEditorCard(state, onTitleChange, onAmountChange, onTypeChange, onCategoryChange, onAddRecord) }
        item { BudgetCard(state, onBudgetChange) }
        item { LedgerAssistantHintCard(state, onOpenAssistant) }
        item { SectionHeader("最近账单", "点击删除可移除一条记录") }
        if (state.ledgerRecords.isEmpty()) {
            item { EmptyToolCard("还没有账单", "先在上方添加一笔，或者回到 AI 助手直接说“记一笔”。", state) }
        } else {
            items(state.ledgerRecords, key = { it.id }) { record -> LedgerRecordRow(record, state) { onDeleteRecord(record.id) } }
        }
    }
}

@Composable
private fun LedgerSummaryCard(state: AssistantUiState) {
    val todayExpense = state.ledgerRecords.filter { it.dateLabel == "今天" && it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }.toFloat()
    val monthExpense = state.ledgerRecords.filter { it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }.toFloat()
    val monthIncome = state.ledgerRecords.filter { it.type == LedgerRecordType.Income }.sumOf { it.amount.toDouble() }.toFloat()
    val budget = state.ledgerBudgetText.toFloatOrNull() ?: 0f
    val remain = budget - monthExpense
    GlassPanel(state.quality, state.glassIntensity * 1.04f, state.motionIntensity, 32, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("本月概览", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(formatMoney(monthExpense), color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                }
                Text(if (remain >= 0f) "剩余 ${formatMoney(remain)}" else "超支 ${formatMoney(-remain)}", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetricGlass("今日支出", formatMoney(todayExpense), state, Modifier.weight(1f))
                MiniMetricGlass("本月收入", formatMoney(monthIncome), state, Modifier.weight(1f))
                MiniMetricGlass("记录", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LedgerEditorCard(
    state: AssistantUiState,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (LedgerRecordType) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddRecord: () -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 30, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("快速记一笔", "对应旧版的手动添加账单")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LedgerTypeChip("支出", state.ledgerDraftType == LedgerRecordType.Expense, state, Modifier.weight(1f)) { onTypeChange(LedgerRecordType.Expense) }
                LedgerTypeChip("收入", state.ledgerDraftType == LedgerRecordType.Income, state, Modifier.weight(1f)) { onTypeChange(LedgerRecordType.Income) }
            }
            ComposerInputGlass(state, state.ledgerDraftTitle, onTitleChange, {}, Modifier.fillMaxWidth(), "标题，比如 午饭 / 工资")
            ComposerInputGlass(state, state.ledgerDraftAmount, onAmountChange, onAddRecord, Modifier.fillMaxWidth(), "金额，比如 18", KeyboardType.Decimal)
            CategorySelector(state, onCategoryChange)
            PressableGlass(state.quality, state.glassIntensity * 1.08f, state.motionIntensity, 999, Modifier.fillMaxWidth().height(50.dp), GlassRole.Floating, onClick = onAddRecord) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("保存账单", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun LedgerTypeChip(text: String, selected: Boolean, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, modifier.height(42.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White.copy(alpha = if (selected) 0.96f else 0.62f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun CategorySelector(state: AssistantUiState, onCategoryChange: (String) -> Unit) {
    val categories = listOf("餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("分类", color = Color.White.copy(alpha = 0.60f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category -> LedgerTypeChip(category, state.ledgerDraftCategory == category, state, Modifier.weight(1f)) { onCategoryChange(category) } }
            }
        }
    }
}

@Composable
private fun BudgetCard(state: AssistantUiState, onBudgetChange: (String) -> Unit) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("预算", "先做本地月预算输入，后续可持久化")
            ComposerInputGlass(state, state.ledgerBudgetText, onBudgetChange, {}, Modifier.fillMaxWidth(), "本月预算", KeyboardType.Decimal)
        }
    }
}

@Composable
private fun LedgerAssistantHintCard(state: AssistantUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth().height(74.dp), GlassRole.Card, onClick = onOpenAssistant) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("也可以直接对 AI 说", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("例如：记一笔午饭 18 元。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp)
            }
            Text("去说 ›", color = Color.White.copy(alpha = 0.74f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LedgerRecordRow(record: LedgerRecord, state: AssistantUiState, onDelete: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth().height(76.dp), GlassRole.Card, onClick = onDelete) {
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${record.dateLabel} · ${record.category} · ${record.type.label}", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, maxLines = 1)
            }
            Text((if (record.type == LedgerRecordType.Income) "+" else "-") + formatMoney(record.amount), color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ToolDetailPlaceholder(state: AssistantUiState, title: String, onBack: () -> Unit, onOpenAssistant: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 116.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DetailHeader(title, "先搭好详情页骨架，后续逐个接系统能力", state, onBack) }
        item { EmptyToolCard(title, "这个入口已经可以点开。下一步可以像账单中心一样继续填具体表单、权限和执行结果。", state) }
        item { LedgerAssistantHintCard(state, onOpenAssistant) }
    }
}

@Composable
private fun DetailHeader(title: String, subtitle: String, state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 999, Modifier.width(92.dp).height(40.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("‹ 返回", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        }
        PageHeader("TOOL DETAIL", title, subtitle)
    }
}

@Composable
private fun EmptyToolCard(title: String, subtitle: String, state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, lineHeight = 20.sp)
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
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 124.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("SETTINGS", "设置", "外观、性能、偏好和服务状态。") }
        item { SettingsGlassCard(state, onQualityChange, onGlassPresetChange, onBackgroundThemeChange, onGlassIntensityChange, onMotionIntensityChange) }
        item { ToggleSettingCard("聊天预览", "保留首页里的示例对话和快捷建议。", state.showPreviewConversation, onPreviewConversationChange, state) }
        item { ServiceStatusCard(aiEndpoint = aiEndpoint, state = state) }
        item { SettingsShortcutList(state) }
    }
}

@Composable
private fun PageHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow, color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolsHeroCard(state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 1.04f, state.motionIntensity, 32, Modifier.fillMaxWidth(), GlassRole.Shell) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("功能中心", color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("从列表变成真正可进入的功能页", color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
            Text("先把账单中心做完整，其它入口也能点开，后续逐步接系统能力。", color = Color.White.copy(alpha = 0.68f), fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniMetricGlass("今日支出", todayExpenseText(state), state, Modifier.weight(1f))
                MiniMetricGlass("账单", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
                MiniMetricGlass("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", state, Modifier.weight(1f))
            }
        }
    }
}

private fun toolEntries(state: AssistantUiState): List<ToolEntry> = state.tools.ifEmpty {
    listOf(
        ToolEntry("账单中心", "查看和管理收入支出"),
        ToolEntry("数据统计", "按周、月、年查看趋势"),
        ToolEntry("提醒闹钟", "创建提醒和闹钟"),
        ToolEntry("应用控制", "打开微信、支付宝等应用"),
        ToolEntry("快捷指令", "保存常用任务"),
        ToolEntry("任务记录", "查看助手执行历史")
    )
}

@Composable
private fun ToolListCard(tool: ToolEntry, state: AssistantUiState, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 28, Modifier.fillMaxWidth().height(88.dp), GlassRole.Card, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 18, Modifier.size(50.dp), GlassRole.Chip) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(toolGlyph(tool.title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayToolTitle(tool.title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(displayToolSubtitle(tool), color = Color.White.copy(alpha = 0.56f), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.68f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun displayToolTitle(title: String): String = when {
    title.contains("账单") -> "账单中心"
    title.contains("数据") -> "数据统计"
    title.contains("提醒") || title.contains("闹钟") -> "提醒闹钟"
    title.contains("应用") -> "应用控制"
    title.contains("快捷") -> "快捷指令"
    title.contains("任务") -> "任务记录"
    else -> title.ifBlank { "功能入口" }
}

private fun displayToolSubtitle(tool: ToolEntry): String = when (displayToolTitle(tool.title)) {
    "账单中心" -> "手动记账、预算、分类和最近明细"
    "数据统计" -> "按周、月、年查看趋势"
    "提醒闹钟" -> "创建提醒和闹钟"
    "应用控制" -> "打开微信、支付宝等应用"
    "快捷指令" -> "保存常用任务"
    "任务记录" -> "查看助手执行历史"
    else -> tool.subtitle
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
private fun SettingsGlassCard(state: AssistantUiState, onQualityChange: (RenderQuality) -> Unit, onGlassPresetChange: (GlassPreset) -> Unit, onBackgroundThemeChange: (BackgroundTheme) -> Unit, onGlassIntensityChange: (Float) -> Unit, onMotionIntensityChange: (Float) -> Unit) {
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
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, Modifier.weight(1f).height(52.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onQualityChange(quality) }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(qualityLabel(quality), color = Color.White.copy(alpha = if (selected) 1f else 0.68f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun GlassPresetSelector(state: AssistantUiState, onGlassPresetChange: (GlassPreset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassPreset.entries.forEach { preset ->
            val selected = state.glassPreset == preset
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, Modifier.weight(1f).height(50.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onGlassPresetChange(preset) }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(preset.label, color = Color.White.copy(alpha = if (selected) 1f else 0.68f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ThemeSelector(state: AssistantUiState, onBackgroundThemeChange: (BackgroundTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BackgroundTheme.entries.forEach { theme ->
            val selected = state.backgroundTheme == theme
            PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 20, Modifier.weight(1f).height(46.dp), if (selected) GlassRole.Floating else GlassRole.Chip, onClick = { onBackgroundThemeChange(theme) }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(themeLabel(theme), color = Color.White.copy(alpha = if (selected) 1f else 0.66f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
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
private fun ToggleSettingCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, state: AssistantUiState) {
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
            Text("Compose 版已接入第一阶段纯文本 AI 请求链路。", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp, lineHeight = 20.sp)
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
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth().height(78.dp), GlassRole.Card) {
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
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 22, modifier.height(74.dp), GlassRole.Card) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.96f), fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun SmallGlassButton(text: String, state: AssistantUiState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity, state.motionIntensity, 22, modifier.height(42.dp), GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
    }
}

@Composable
fun LiquidBottomBar(currentTab: AppTab, quality: RenderQuality, glassIntensity: Float, motionIntensity: Float, onTabChange: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(quality, glassIntensity, motionIntensity, 30, modifier.fillMaxWidth().height(62.dp), GlassRole.Nav) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            val tabCount = AppTab.entries.size
            val slot = maxWidth / tabCount
            val target = AppTab.entries.indexOf(currentTab).coerceAtLeast(0)
            val indicatorX by animateDpAsState(slot * target.toFloat(), animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-x")
            val indicatorW by animateDpAsState(slot - 8.dp, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "nav-indicator-w")
            GlassPanel(quality, glassIntensity * 1.08f, motionIntensity, 24, Modifier.offset(x = indicatorX + 4.dp, y = 1.dp).width(indicatorW).height(48.dp), GlassRole.Floating) {}
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tab-press")
                    Column(modifier = Modifier.weight(1f).height(50.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(24.dp)).clickable(interactionSource = interaction, indication = null) { onTabChange(tab) }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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

private fun todayExpenseText(state: AssistantUiState): String = formatMoney(state.ledgerRecords.filter { it.dateLabel == "今天" && it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }.toFloat())

private fun formatMoney(value: Float): String = "¥${String.format("%.2f", value)}"

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

private fun Float.format2x(): String = (this * 100).roundToInt().div(100f).toString()
