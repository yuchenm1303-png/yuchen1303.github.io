package com.yuchen.ailedger.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.LedgerScreenState
import com.yuchen.ailedger.LedgerSyncPhase
import com.yuchen.ailedger.LedgerViewModel
import com.yuchen.ailedger.data.LedgerStore
import com.yuchen.ailedger.model.AssistantUiState
import com.yuchen.ailedger.model.LedgerRecord
import com.yuchen.ailedger.model.LedgerRecordType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun NativeLedgerCenterScreen(
    appState: AssistantUiState,
    ledgerViewModel: LedgerViewModel,
    statisticsOnly: Boolean,
    onBack: () -> Unit,
    onOpenAssistant: () -> Unit
) {
    val ledgerState = ledgerViewModel.state
    val context = LocalContext.current
    var searchText by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("all") }
    var categoryFilter by rememberSaveable { mutableStateOf("全部") }
    var monthFilter by rememberSaveable { mutableStateOf(LedgerStore.currentMonthPrefix()) }

    LaunchedEffect(Unit) {
        ledgerViewModel.onScreenOpened()
    }

    val filteredRecords = remember(ledgerState.records, searchText, typeFilter, categoryFilter, monthFilter) {
        ledgerState.records.filter { record ->
            val matchSearch = searchText.isBlank() || record.title.contains(searchText, ignoreCase = true) || record.category.contains(searchText, ignoreCase = true)
            val matchType = when (typeFilter) {
                "expense" -> record.type == LedgerRecordType.Expense
                "income" -> record.type == LedgerRecordType.Income
                else -> true
            }
            val matchCategory = categoryFilter == "全部" || record.category == categoryFilter
            val matchMonth = monthFilter.isBlank() || LedgerStore.normalizeDate(record.dateLabel).startsWith(monthFilter)
            matchSearch && matchType && matchCategory && matchMonth
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LedgerHeader(
                title = if (statisticsOnly) "数据统计" else "账单中心",
                subtitle = if (statisticsOnly) "按月份和分类查看收支结构" else "记账、预算、筛选、本地保存与云同步",
                appState = appState,
                onBack = onBack
            )
        }
        item { LedgerSummaryCard(appState, ledgerState, monthFilter) }
        item { LedgerSyncCard(appState, ledgerState, ledgerViewModel::syncNow) }

        if (!statisticsOnly) {
            item { SmartLedgerCard(appState, ledgerState, ledgerViewModel::updateSmartInput, ledgerViewModel::addSmartRecords) }
            item {
                LedgerEditorCard(
                    appState = appState,
                    ledgerState = ledgerState,
                    onTitleChange = ledgerViewModel::updateTitle,
                    onAmountChange = ledgerViewModel::updateAmount,
                    onDateChange = ledgerViewModel::updateDate,
                    onTypeChange = ledgerViewModel::updateType,
                    onCategoryChange = ledgerViewModel::updateCategory,
                    onSave = ledgerViewModel::saveRecord,
                    onCancelEdit = ledgerViewModel::cancelEdit
                )
            }
            item { LedgerBudgetCard(appState, ledgerState, ledgerViewModel::updateBudget) }
        }

        item { LedgerCategoryStatsCard(appState, ledgerState.records, monthFilter) }
        item {
            LedgerFilterCard(
                appState = appState,
                searchText = searchText,
                onSearchChange = { searchText = it.take(40) },
                typeFilter = typeFilter,
                onTypeFilterChange = { typeFilter = it },
                categoryFilter = categoryFilter,
                onCategoryFilterChange = { categoryFilter = it },
                monthFilter = monthFilter,
                onMonthFilterChange = { monthFilter = sanitizeMonth(it) }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账单明细", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("共 ${filteredRecords.size} 笔符合条件", color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                LedgerSmallButton("导出 JSON", appState) {
                    shareLedgerJson(context, ledgerViewModel.exportJson())
                }
            }
        }
        if (filteredRecords.isEmpty()) {
            item { LedgerEmptyCard(appState, if (ledgerState.records.isEmpty()) "还没有账单，先添加一笔吧。" else "当前筛选条件下没有账单。") }
        } else {
            items(filteredRecords, key = { it.id }) { record ->
                LedgerRecordCard(
                    appState = appState,
                    record = record,
                    onEdit = { ledgerViewModel.beginEdit(record) },
                    onDelete = { ledgerViewModel.deleteRecord(record.id) }
                )
            }
        }
        if (!statisticsOnly) {
            item {
                LedgerAssistantEntry(appState, onOpenAssistant)
            }
        }
    }
}

@Composable
private fun LedgerHeader(title: String, subtitle: String, appState: AssistantUiState, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(
            quality = appState.quality,
            glassIntensity = appState.glassIntensity,
            motionIntensity = appState.motionIntensity,
            radius = 999,
            modifier = Modifier.width(92.dp).height(40.dp),
            role = GlassRole.Chip,
            onClick = onBack
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ 返回", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LEDGER", color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(title, color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LedgerSummaryCard(appState: AssistantUiState, state: LedgerScreenState, month: String) {
    val monthRecords = state.records.filter { LedgerStore.normalizeDate(it.dateLabel).startsWith(month) }
    val today = LedgerStore.todayIso()
    val todayExpense = monthRecords.filter { it.type == LedgerRecordType.Expense && LedgerStore.normalizeDate(it.dateLabel) == today }.sumOf { it.amount.toDouble() }
    val expense = monthRecords.filter { it.type == LedgerRecordType.Expense }.sumOf { it.amount.toDouble() }
    val income = monthRecords.filter { it.type == LedgerRecordType.Income }.sumOf { it.amount.toDouble() }
    val budget = state.budgetText.toDoubleOrNull() ?: 0.0
    val remaining = budget - expense

    LedgerCard(appState) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${month.ifBlank { "全部" }} 概览", color = Color.White.copy(alpha = 0.54f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(formatMoney(expense), color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                Text("本月支出", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (remaining >= 0.0) "剩余 ${formatMoney(remaining)}" else "超支 ${formatMoney(abs(remaining))}", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("结余 ${formatSignedMoney(income - expense)}", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        val progress = if (budget > 0.0) (expense / budget).toFloat().coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(progress).height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.48f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerMetric("今日支出", formatMoney(todayExpense), Modifier.weight(1f))
            LedgerMetric("本月收入", formatMoney(income), Modifier.weight(1f))
            LedgerMetric("记录", "${monthRecords.size} 笔", Modifier.weight(1f))
        }
    }
}

@Composable
private fun LedgerSyncCard(appState: AssistantUiState, state: LedgerScreenState, onSync: () -> Unit) {
    LedgerCard(appState) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("账号与云同步", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(state.accountEmail ?: "未登录 · 本地模式", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SyncPill(state.syncPhase)
        }
        Text(state.syncMessage, color = if (state.syncPhase == LedgerSyncPhase.Error) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.58f), fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
        state.lastSyncedAt?.let {
            Text("最近同步：${formatSyncTime(it)}", color = Color.White.copy(alpha = 0.38f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        LedgerWideButton(if (state.isSyncing) "同步中…" else "立即同步", "合并本地与 Supabase 数据", appState, state.isSyncing.not(), onSync)
    }
}

@Composable
private fun SmartLedgerCard(
    appState: AssistantUiState,
    state: LedgerScreenState,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    LedgerCard(appState) {
        Text("智能快速记账", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text("支持一次输入多笔，例如：午饭18元，地铁4元。", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        LedgerTextField(state.smartInput, onValueChange, "输入自然语言账单", KeyboardType.Text)
        LedgerWideButton("识别并保存", "本地解析，不需要联网", appState, true, onAdd)
        Text(state.smartMessage, color = Color(0xFF8DF9EA).copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LedgerEditorCard(
    appState: AssistantUiState,
    ledgerState: LedgerScreenState,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTypeChange: (LedgerRecordType) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit
) {
    LedgerCard(appState) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ledgerState.editingRecordId == null) "手动记一笔" else "编辑账单", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            if (ledgerState.editingRecordId != null) LedgerSmallButton("取消编辑", appState, onCancelEdit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerChoiceChip("支出", ledgerState.draftType == LedgerRecordType.Expense, appState, Modifier.weight(1f)) { onTypeChange(LedgerRecordType.Expense) }
            LedgerChoiceChip("收入", ledgerState.draftType == LedgerRecordType.Income, appState, Modifier.weight(1f)) { onTypeChange(LedgerRecordType.Income) }
        }
        LedgerTextField(ledgerState.draftTitle, onTitleChange, "标题，例如午饭 / 工资", KeyboardType.Text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerTextField(ledgerState.draftAmount, onAmountChange, "金额", KeyboardType.Decimal, Modifier.weight(1f))
            LedgerTextField(ledgerState.draftDate, onDateChange, "YYYY-MM-DD", KeyboardType.Number, Modifier.weight(1f))
        }
        Text("分类", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LedgerStore.LEDGER_CATEGORIES.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { category ->
                    LedgerChoiceChip(category, ledgerState.draftCategory == category, appState, Modifier.weight(1f)) { onCategoryChange(category) }
                }
            }
        }
        LedgerWideButton(if (ledgerState.editingRecordId == null) "保存账单" else "保存修改", "自动保存到本机并尝试云同步", appState, true, onSave)
    }
}

@Composable
private fun LedgerBudgetCard(appState: AssistantUiState, state: LedgerScreenState, onBudgetChange: (String) -> Unit) {
    LedgerCard(appState) {
        Text("本月预算", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        LedgerTextField(state.budgetText, onBudgetChange, "预算金额", KeyboardType.Decimal)
        Text("预算会实时保存；登录后同步到 user_settings 表。", color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LedgerCategoryStatsCard(appState: AssistantUiState, records: List<LedgerRecord>, month: String) {
    val expenses = records.filter { it.type == LedgerRecordType.Expense && (month.isBlank() || LedgerStore.normalizeDate(it.dateLabel).startsWith(month)) }
    val totals = expenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount.toDouble() } }.toList().sortedByDescending { it.second }
    val maxValue = totals.maxOfOrNull { it.second } ?: 0.0
    LedgerCard(appState) {
        Text("分类支出", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        if (totals.isEmpty()) {
            Text("当前月份还没有支出数据。", color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp)
        } else {
            totals.take(8).forEach { (category, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(category, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(formatMoney(value), color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    val fraction = if (maxValue > 0.0) (value / maxValue).toFloat().coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.07f))) {
                        Box(Modifier.fillMaxWidth(fraction).height(5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.36f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerFilterCard(
    appState: AssistantUiState,
    searchText: String,
    onSearchChange: (String) -> Unit,
    typeFilter: String,
    onTypeFilterChange: (String) -> Unit,
    categoryFilter: String,
    onCategoryFilterChange: (String) -> Unit,
    monthFilter: String,
    onMonthFilterChange: (String) -> Unit
) {
    LedgerCard(appState) {
        Text("筛选与搜索", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerTextField(searchText, onSearchChange, "搜索标题或分类", KeyboardType.Text, Modifier.weight(1.35f))
            LedgerTextField(monthFilter, onMonthFilterChange, "YYYY-MM", KeyboardType.Number, Modifier.weight(0.85f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LedgerChoiceChip("全部", typeFilter == "all", appState, Modifier.weight(1f)) { onTypeFilterChange("all") }
            LedgerChoiceChip("支出", typeFilter == "expense", appState, Modifier.weight(1f)) { onTypeFilterChange("expense") }
            LedgerChoiceChip("收入", typeFilter == "income", appState, Modifier.weight(1f)) { onTypeFilterChange("income") }
        }
        val categories = listOf("全部") + LedgerStore.LEDGER_CATEGORIES
        categories.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { category ->
                    LedgerChoiceChip(category, categoryFilter == category, appState, Modifier.weight(1f)) { onCategoryFilterChange(category) }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LedgerRecordCard(appState: AssistantUiState, record: LedgerRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    LedgerCard(appState) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${LedgerStore.displayDate(record.dateLabel)} · ${record.category} · ${record.type.label}", color = Color.White.copy(alpha = 0.46f), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(
                text = (if (record.type == LedgerRecordType.Income) "+" else "-") + formatMoney(record.amount.toDouble()),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerSmallButton("编辑", appState, onEdit, Modifier.weight(1f))
            LedgerSmallButton("删除", appState, onDelete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LedgerAssistantEntry(appState: AssistantUiState, onClick: () -> Unit) {
    PressableGlass(appState.quality, appState.glassIntensity, appState.motionIntensity, 24, Modifier.fillMaxWidth().height(70.dp), GlassRole.Card, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("回到 AI 助手", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("可以继续咨询账单分析和消费建议。", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text("进入 ›", color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LedgerEmptyCard(appState: AssistantUiState, message: String) {
    LedgerCard(appState) {
        Text(message, color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun LedgerCard(appState: AssistantUiState, content: @Composable () -> Unit) {
    FrostInfoGlassPanel(
        radius = 17.44f,
        backdropAlpha = 1f,
        frostAlpha = 0.090f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF151A4F).copy(alpha = 0.28f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun LedgerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.065f)).padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = Color.White.copy(alpha = 0.90f), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(Color(0xFF8DF9EA)),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.070f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text(placeholder, color = Color.White.copy(alpha = 0.34f), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        }
    )
}

@Composable
private fun LedgerChoiceChip(text: String, selected: Boolean, appState: AssistantUiState, modifier: Modifier, onClick: () -> Unit) {
    PressableGlass(
        quality = appState.quality,
        glassIntensity = appState.glassIntensity,
        motionIntensity = appState.motionIntensity,
        radius = 999,
        modifier = modifier.height(38.dp),
        role = if (selected) GlassRole.Floating else GlassRole.Chip,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = if (selected) 0.96f else 0.58f), fontSize = if (text.length > 2) 10.sp else 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun LedgerWideButton(title: String, subtitle: String, appState: AssistantUiState, enabled: Boolean, onClick: () -> Unit) {
    PressableGlass(
        quality = appState.quality,
        glassIntensity = appState.glassIntensity,
        motionIntensity = appState.motionIntensity,
        radius = 22,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        role = GlassRole.Chip,
        onClick = if (enabled) onClick else {}
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White.copy(alpha = if (enabled) 0.94f else 0.50f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = if (enabled) 0.44f else 0.28f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = Color.White.copy(alpha = if (enabled) 0.58f else 0.26f), fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LedgerSmallButton(text: String, appState: AssistantUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableGlass(appState.quality, appState.glassIntensity, appState.motionIntensity, 999, modifier.height(36.dp), GlassRole.Chip, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White.copy(alpha = 0.74f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun SyncPill(phase: LedgerSyncPhase) {
    val text = when (phase) {
        LedgerSyncPhase.LocalOnly -> "本地"
        LedgerSyncPhase.Ready -> "待同步"
        LedgerSyncPhase.Syncing -> "同步中"
        LedgerSyncPhase.Synced -> "已同步"
        LedgerSyncPhase.Error -> "失败"
    }
    Box(Modifier.height(30.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = if (phase == LedgerSyncPhase.Error) 0.12f else 0.08f)).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (phase == LedgerSyncPhase.Error) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun shareLedgerJson(context: Context, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "AI Ledger 账单导出")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "导出账单"))
}

private fun sanitizeMonth(value: String): String {
    return value.filter { it.isDigit() || it == '-' }.take(7)
}

private fun formatMoney(value: Double): String = "¥${String.format(Locale.CHINA, "%.2f", value)}"

private fun formatSignedMoney(value: Double): String = (if (value >= 0.0) "+" else "-") + formatMoney(abs(value))

private fun formatSyncTime(timestamp: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
