package com.yuchen.ailedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import com.yuchen.ailedger.model.ToolEntry
import kotlinx.coroutines.delay

@Composable
fun ToolsScreenV2(
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
    AnimatedVisibility(
        visible = selected == null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.72f)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.98f, animationSpec = tween(120))
    ) {
        ToolsHomeV2(state = state, onOpenTool = onOpenTool)
    }
    AnimatedVisibility(
        visible = selected != null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInHorizontally(spring(dampingRatio = 0.72f)) { it / 4 },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(140)) { it / 5 }
    ) {
        when (selected) {
            "账单中心" -> LedgerCenterV2(
                state = state,
                onBack = onBack,
                onTitleChange = onLedgerTitleChange,
                onAmountChange = onLedgerAmountChange,
                onTypeChange = onLedgerTypeChange,
                onCategoryChange = onLedgerCategoryChange,
                onBudgetChange = onLedgerBudgetChange,
                onAddRecord = onAddLedgerRecord,
                onDeleteRecord = onDeleteLedgerRecord,
                onOpenAssistant = onOpenAssistant
            )
            null -> Unit
            else -> ToolDetailV2(state = state, title = selected, onBack = onBack, onOpenAssistant = onOpenAssistant)
        }
    }
}

@Composable
private fun ToolsHomeV2(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { AnimatedAppear(delayMs = 0) { ToolsHeaderV2() } }
        item { AnimatedAppear(delayMs = 55) { ToolsHeroV2(state, onOpenTool) } }
        item { AnimatedAppear(delayMs = 100) { PrimaryToolRowV2(state, onOpenTool) } }
        itemsIndexed(toolEntriesV2(state), key = { _, item -> item.title }) { index, tool ->
            AnimatedAppear(delayMs = 145L + index * 42L) {
                ToolCardV2(tool = tool, state = state, onClick = { onOpenTool(displayToolTitleV2(tool.title)) })
            }
        }
    }
}

@Composable
private fun ToolsHeaderV2() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("TOOLS", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("功能", color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
        Text("把常用操作整理成可以执行的入口。", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolsHeroV2(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.03f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier
            .fillMaxWidth()
            .height(198.dp),
        mood = OpenGlShellMood.Hero,
        onClick = { onOpenTool("账单中心") }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("今日入口", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("账单中心", color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text("从记账、预算和最近明细开始", color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FrostInfoGlassPanel(
                radius = 20f,
                backdropAlpha = 1f,
                frostAlpha = 0f,
                dimAlpha = 0f,
                modifier = Modifier.fillMaxWidth().height(68.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroFrostMetric("记录", "${state.ledgerRecords.size} 笔", Modifier.weight(1f))
                    HeroFrostMetric("今日", todayExpenseTextV2(state), Modifier.weight(1f))
                    HeroFrostMetric("预算", "¥${state.ledgerBudgetText.ifBlank { "0" }}", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroFrostMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Text(label, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroToolMetric(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity * 0.86f, state.motionIntensity, 18, modifier.height(42.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PrimaryToolRowV2(state: AssistantUiState, onOpenTool: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickToolPillV2("账单", "明细", "账单中心", state, Modifier.weight(1f), onOpenTool)
        QuickToolPillV2("提醒", "待接入", "提醒闹钟", state, Modifier.weight(1f), onOpenTool)
        QuickToolPillV2("应用", "打开", "应用控制", state, Modifier.weight(1f), onOpenTool)
    }
}

@Composable
private fun QuickToolPillV2(title: String, subtitle: String, target: String, state: AssistantUiState, modifier: Modifier, onOpenTool: (String) -> Unit) {
    val active = target == "账单中心"
    val pop by animateFloatAsState(
        targetValue = if (active) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "quick-tool-pop"
    )
    PressableGlass(
        state.quality,
        state.glassIntensity * if (active) 1.03f else 0.92f,
        state.motionIntensity,
        22,
        modifier
            .height(62.dp)
            .graphicsLayer { scaleX = pop; scaleY = pop },
        if (active) GlassRole.Floating else GlassRole.Chip,
        onClick = { onOpenTool(target) }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ToolCardV2(tool: ToolEntry, state: AssistantUiState, onClick: () -> Unit) {
    val title = displayToolTitleV2(tool.title)
    val active = title == "账单中心"
    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * if (active) 1.02f else 0.92f,
        motionIntensity = state.motionIntensity,
        radius = 24,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        mood = OpenGlShellMood.List,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                ToolMinimalIconV2(
                    title = title,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = 0.94f), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(displayToolSubtitleV2(tool), color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("进入", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LedgerCenterV2(
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { AnimatedAppear(0) { DetailHeaderV2("账单中心", "预算、分类和最近明细", state, onBack) } }
        item { AnimatedAppear(55) { LedgerSummaryV2(state) } }
        item { AnimatedAppear(100) { LedgerEditorV2(state, onTitleChange, onAmountChange, onTypeChange, onCategoryChange, onAddRecord) } }
        item { AnimatedAppear(145) { BudgetEditorV2(state, onBudgetChange) } }
        item { AnimatedAppear(180) { AssistantHintV2(state, onOpenAssistant) } }
        item { AnimatedAppear(210) { SectionTitleV2("最近账单", "点一条记录可以删除") } }
        if (state.ledgerRecords.isEmpty()) {
            item { AnimatedAppear(240) { EmptyStateV2("还没有账单", "先添加一笔，或者回到 AI 助手直接说“记一笔”。", state) } }
        } else {
            itemsIndexed(state.ledgerRecords, key = { _, item -> item.id }) { index, record ->
                AnimatedAppear(240L + index * 34L) {
                    LedgerRecordCardV2(record, state) { onDeleteRecord(record.id) }
                }
            }
        }
    }
}

@Composable
private fun LedgerSummaryV2(state: AssistantUiState) {
    val monthExpense = state.ledgerRecords.filter { it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }.toFloat()
    val monthIncome = state.ledgerRecords.filter { it.type == LedgerRecordType.Income }.sumOf { it.amount.toDouble() }.toFloat()
    val todayExpense = state.ledgerRecords.filter { it.dateLabel == "今天" && it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }.toFloat()
    val budget = state.ledgerBudgetText.toFloatOrNull() ?: 0f
    val remain = budget - monthExpense
    OpenGlShellGlass(
        quality = state.quality,
        glassIntensity = state.glassIntensity * 1.04f,
        motionIntensity = state.motionIntensity,
        radius = 28,
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
        mood = OpenGlShellMood.Summary
    ) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("本月支出", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formatMoneyV2(monthExpense), color = Color.White, fontSize = 29.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                }
                Text(if (remain >= 0f) "剩余 ${formatMoneyV2(remain)}" else "超支 ${formatMoneyV2(-remain)}", color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniToolMetric("今日", formatMoneyV2(todayExpense), state, Modifier.weight(1f))
                MiniToolMetric("收入", formatMoneyV2(monthIncome), state, Modifier.weight(1f))
                MiniToolMetric("记录", "${state.ledgerRecords.size} 笔", state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LedgerEditorV2(
    state: AssistantUiState,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (LedgerRecordType) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddRecord: () -> Unit
) {
    GlassPanel(state.quality, state.glassIntensity, state.motionIntensity, 26, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitleV2("快速记一笔", "保存后会加入最近账单")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TypeChipV2("支出", state.ledgerDraftType == LedgerRecordType.Expense, state, Modifier.weight(1f)) { onTypeChange(LedgerRecordType.Expense) }
                TypeChipV2("收入", state.ledgerDraftType == LedgerRecordType.Income, state, Modifier.weight(1f)) { onTypeChange(LedgerRecordType.Income) }
            }
            ToolInputV2(state, state.ledgerDraftTitle, onTitleChange, {}, "标题，比如 午饭 / 工资")
            ToolInputV2(state, state.ledgerDraftAmount, onAmountChange, onAddRecord, "金额，比如 18", KeyboardType.Decimal)
            CategoryRowV2(state, onCategoryChange)
            PressableGlass(state.quality, state.glassIntensity * 1.08f, state.motionIntensity, 999, Modifier.fillMaxWidth().height(46.dp), GlassRole.Floating, onClick = onAddRecord) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("保存账单", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun BudgetEditorV2(state: AssistantUiState, onBudgetChange: (String) -> Unit) {
    GlassPanel(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitleV2("预算", "先做本地月预算输入")
            ToolInputV2(state, state.ledgerBudgetText, onBudgetChange, {}, "本月预算", KeyboardType.Decimal)
        }
    }
}

@Composable
private fun ToolDetailV2(state: AssistantUiState, title: String, onBack: () -> Unit, onOpenAssistant: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { AnimatedAppear(0) { DetailHeaderV2(title, "功能入口已接入，后续可以继续填执行能力。", state, onBack) } }
        item { AnimatedAppear(70) { EmptyStateV2(title, "这里先做成有动效的详情页骨架。下一步可以加权限说明、执行按钮和结果反馈。", state) } }
        item { AnimatedAppear(120) { AssistantHintV2(state, onOpenAssistant) } }
    }
}

@Composable
private fun DetailHeaderV2(title: String, subtitle: String, state: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        PressableGlass(state.quality, state.glassIntensity * 0.95f, state.motionIntensity, 999, Modifier.height(38.dp), GlassRole.Chip, onClick = onBack) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ 返回", color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TOOL DETAIL", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(title, color = Color.White, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AnimatedAppear(delayMs: Long, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
            scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(120))
    ) { content() }
}

@Composable
private fun IlluminatedGlyph(text: String, state: AssistantUiState, active: Boolean, accent: Color = Color(0xFF8DF9EA)) {
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0.18f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        label = "tool-glyph-glow"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .toolGlyphGlow(glow, accent),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(state.quality, state.glassIntensity * if (active) 1.04f else 0.90f, state.motionIntensity, 18, Modifier.fillMaxSize(), if (active) GlassRole.Floating else GlassRole.Chip) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text, color = Color.White.copy(alpha = if (active) 0.96f else 0.70f), fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MiniToolMetric(label: String, value: String, state: AssistantUiState, modifier: Modifier = Modifier) {
    GlassPanel(state.quality, state.glassIntensity * 0.90f, state.motionIntensity, 18, modifier.height(50.dp), GlassRole.Chip) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.46f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TypeChipV2(text: String, selected: Boolean, state: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMediumLow),
        label = "type-chip-pop"
    )
    PressableGlass(
        state.quality,
        state.glassIntensity * if (selected) 1.05f else 0.90f,
        state.motionIntensity,
        999,
        modifier.height(39.dp).graphicsLayer { scaleX = pop; scaleY = pop },
        if (selected) GlassRole.Floating else GlassRole.Chip,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (selected) 0.95f else 0.58f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ToolInputV2(
    state: AssistantUiState,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    GlassPanel(state.quality, state.glassIntensity * 0.92f, state.motionIntensity, 22, Modifier.fillMaxWidth().height(46.dp), GlassRole.Card) {
        Box(Modifier.fillMaxSize().padding(horizontal = 13.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Color.White.copy(alpha = 0.86f)),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isBlank()) {
                Text(placeholder, color = Color.White.copy(alpha = 0.42f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CategoryRowV2(state: AssistantUiState, onCategoryChange: (String) -> Unit) {
    val categories = listOf("餐饮", "交通", "购物", "居住", "饮品", "工资", "礼物", "其他")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("分类", color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    TypeChipV2(category, state.ledgerDraftCategory == category, state, Modifier.weight(1f)) { onCategoryChange(category) }
                }
            }
        }
    }
}

@Composable
private fun LedgerRecordCardV2(record: LedgerRecord, state: AssistantUiState, onDelete: () -> Unit) {
    val accent = if (record.type == LedgerRecordType.Income) Color(0xFF8DF9EA) else Color(0xFFFFC2D1)
    PressableGlass(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 23, Modifier.fillMaxWidth().height(66.dp), GlassRole.Card, onClick = onDelete) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = 0.86f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(record.title, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${record.dateLabel} · ${record.category} · ${record.type.label}", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, maxLines = 1)
            }
            Text((if (record.type == LedgerRecordType.Income) "+" else "-") + formatMoneyV2(record.amount), color = Color.White.copy(alpha = 0.90f), fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AssistantHintV2(state: AssistantUiState, onOpenAssistant: () -> Unit) {
    PressableGlass(state.quality, state.glassIntensity * 0.96f, state.motionIntensity, 24, Modifier.fillMaxWidth().height(66.dp), GlassRole.Card, onClick = onOpenAssistant) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("也可以直接对 AI 说", color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("例如：记一笔午饭 18 元。", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
            }
            Text("去说 ›", color = Color.White.copy(alpha = 0.68f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun EmptyStateV2(title: String, subtitle: String, state: AssistantUiState) {
    GlassPanel(state.quality, state.glassIntensity * 0.94f, state.motionIntensity, 24, Modifier.fillMaxWidth(), GlassRole.Card) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SectionTitleV2(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ToolMinimalIconV2(title: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color.White.copy(alpha = 0.90f)
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = size.minDimension * 0.085f, cap = StrokeCap.Round)
        when (displayToolTitleV2(title)) {
            "账单中心" -> drawLedgerIcon(color, stroke, w, h)
            "数据统计" -> drawStatsIcon(color, stroke, w, h)
            "提醒闹钟" -> drawBellIcon(color, stroke, w, h)
            "应用控制" -> drawAppControlIcon(color, stroke, w, h)
            "快捷指令" -> drawShortcutIcon(color, stroke, w, h)
            "任务记录" -> drawTaskRecordIcon(color, stroke, w, h)
            else -> drawLedgerIcon(color, stroke, w, h)
        }
    }
}

private fun DrawScope.drawLedgerIcon(color: Color, stroke: Stroke, w: Float, h: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.20f, h * 0.14f),
        size = Size(w * 0.60f, h * 0.72f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.32f, h * 0.36f), Offset(w * 0.68f, h * 0.36f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.32f, h * 0.52f), Offset(w * 0.68f, h * 0.52f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.32f, h * 0.68f), Offset(w * 0.56f, h * 0.68f), stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawStatsIcon(color: Color, stroke: Stroke, w: Float, h: Float) {
    drawLine(color, Offset(w * 0.20f, h * 0.78f), Offset(w * 0.80f, h * 0.78f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.28f, h * 0.78f), Offset(w * 0.28f, h * 0.56f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.48f, h * 0.78f), Offset(w * 0.48f, h * 0.40f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.68f, h * 0.78f), Offset(w * 0.68f, h * 0.24f), stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawBellIcon(color: Color, stroke: Stroke, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(w * 0.30f, h * 0.64f)
        quadraticBezierTo(w * 0.30f, h * 0.34f, w * 0.50f, h * 0.26f)
        quadraticBezierTo(w * 0.70f, h * 0.34f, w * 0.70f, h * 0.64f)
    }
    drawPath(path = path, color = color, style = stroke)
    drawLine(color, Offset(w * 0.26f, h * 0.66f), Offset(w * 0.74f, h * 0.66f), stroke.width, cap = StrokeCap.Round)
    drawCircle(color = color, radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.76f))
    drawLine(color, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.24f), stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawAppControlIcon(color: Color, stroke: Stroke, w: Float, h: Float) {
    val side = w * 0.17f
    val corner = CornerRadius(w * 0.035f, w * 0.035f)
    drawRoundRect(color, Offset(w * 0.21f, h * 0.23f), Size(side, side), corner, style = stroke)
    drawRoundRect(color, Offset(w * 0.56f, h * 0.23f), Size(side, side), corner, style = stroke)
    drawRoundRect(color, Offset(w * 0.21f, h * 0.57f), Size(side, side), corner, style = stroke)
    drawRoundRect(color, Offset(w * 0.56f, h * 0.57f), Size(side, side), corner, style = stroke)
}

private fun DrawScope.drawShortcutIcon(color: Color, stroke: Stroke, w: Float, h: Float) {
    drawLine(color, Offset(w * 0.28f, h * 0.70f), Offset(w * 0.72f, h * 0.28f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.50f, h * 0.28f), Offset(w * 0.72f, h * 0.28f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.72f, h * 0.50f), stroke.width, cap = StrokeCap.Round)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.22f, h * 0.42f),
        size = Size(w * 0.27f, h * 0.27f),
        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        style = stroke
    )
}

private fun DrawScope.drawTaskRecordIcon(color: Color, stroke: Stroke, w: Float, h: Float) {
    drawCircle(color = color, radius = w * 0.11f, center = Offset(w * 0.30f, h * 0.34f), style = stroke)
    drawLine(color, Offset(w * 0.30f, h * 0.34f), Offset(w * 0.30f, h * 0.28f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.30f, h * 0.34f), Offset(w * 0.36f, h * 0.39f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.48f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.24f, h * 0.56f), Offset(w * 0.78f, h * 0.56f), stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.24f, h * 0.72f), Offset(w * 0.64f, h * 0.72f), stroke.width, cap = StrokeCap.Round)
}

private fun Modifier.toolGlyphGlow(glow: Float, accent: Color): Modifier = drawWithCache {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.82f
    val brush = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = 0.28f * glow),
            accent.copy(alpha = 0.10f * glow),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    onDrawWithContent {
        if (glow > 0.01f) drawCircle(brush = brush, radius = radius, center = center, blendMode = BlendMode.Screen)
        drawContent()
    }
}

private fun toolEntriesV2(state: AssistantUiState): List<ToolEntry> = state.tools.ifEmpty {
    listOf(
        ToolEntry("账单中心", "手动记账、预算、分类和最近明细"),
        ToolEntry("数据统计", "按周、月、年查看趋势"),
        ToolEntry("提醒闹钟", "创建提醒和闹钟"),
        ToolEntry("应用控制", "打开微信、支付宝等应用"),
        ToolEntry("快捷指令", "保存常用任务"),
        ToolEntry("任务记录", "查看助手执行历史")
    )
}

private fun displayToolTitleV2(title: String): String = when {
    title.contains("账单") -> "账单中心"
    title.contains("数据") -> "数据统计"
    title.contains("提醒") || title.contains("闹钟") -> "提醒闹钟"
    title.contains("应用") -> "应用控制"
    title.contains("快捷") -> "快捷指令"
    title.contains("任务") -> "任务记录"
    else -> title.ifBlank { "功能入口" }
}

private fun displayToolSubtitleV2(tool: ToolEntry): String = when (displayToolTitleV2(tool.title)) {
    "账单中心" -> "手动记账、预算、分类和最近明细"
    "数据统计" -> "按周、月、年查看趋势"
    "提醒闹钟" -> "创建提醒和闹钟"
    "应用控制" -> "打开微信、支付宝等应用"
    "快捷指令" -> "保存常用任务"
    "任务记录" -> "查看助手执行历史"
    else -> tool.subtitle
}

private fun toolGlyphV2(title: String): String = when (displayToolTitleV2(title)) {
    "账单中心" -> "账"
    "数据统计" -> "图"
    "提醒闹钟" -> "铃"
    "应用控制" -> "启"
    "快捷指令" -> "令"
    else -> "记"
}

private fun toolAccentV2(title: String): Color = when (displayToolTitleV2(title)) {
    "账单中心" -> Color(0xFF8DF9EA)
    "数据统计" -> Color(0xFF9EB7FF)
    "提醒闹钟" -> Color(0xFFFFD166)
    "应用控制" -> Color(0xFFFFB4D2)
    "快捷指令" -> Color(0xFFC7A8FF)
    else -> Color.White
}

private fun todayExpenseTextV2(state: AssistantUiState): String = formatMoneyV2(
    state.ledgerRecords.filter { it.dateLabel == "今天" && it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }.toFloat()
)

private fun formatMoneyV2(value: Float): String = "¥${String.format("%.2f", value)}"