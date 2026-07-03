package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentSkillInventory
import com.yuchen.ailedger.model.AgentTaskAnalytics
import com.yuchen.ailedger.model.AssistantUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val AnalyticsMint = Color(0xFF7BE8D2)
private val AnalyticsBlue = Color(0xFF8FB2FF)
private val AnalyticsViolet = Color(0xFFB49BFF)
private val AnalyticsWarm = Color(0xFFFFC58A)
private val AnalyticsDanger = Color(0xFFFF9EAF)

private enum class AgentAnalyticsTab(val label: String) {
    Overview("总览"),
    Tokens("Token"),
    Tasks("任务"),
    Capabilities("能力"),
}

private enum class AgentAnalyticsRange(
    val label: String,
    val days: Long?,
    val heatmapWeeks: Int,
) {
    Weeks12("12 周", 84L, 12),
    HalfYear("半年", 183L, 26),
    Year("一年", 365L, 52),
    All("全部", null, 52),
}

@Immutable
private data class AgentAnalyticsPeriod(
    val daily: List<AgentDailyActivity>,
    val tasks: List<AgentTaskAnalytics>,
    val totalTokens: Long,
    val providerTokens: Long,
    val estimatedTokens: Long,
    val peakDailyTokens: Long,
    val activeDays: Int,
    val chatCalls: Long,
    val modelCalls: Long,
    val modelFailures: Long,
    val agentTasks: Long,
    val completedTasks: Long,
    val autonomousCompletedTasks: Long,
    val assistedCompletedTasks: Long,
    val failedTasks: Long,
    val pausedTasks: Long,
    val cancelledTasks: Long,
    val budgetExceededTasks: Long,
    val agentModelTurns: Long,
    val executedActions: Long,
    val successfulActions: Long,
    val failedActions: Long,
    val observations: Long,
    val reobservations: Long,
    val rejectedPlans: Long,
    val executionFailures: Long,
    val confirmationRequests: Long,
    val userInputRequests: Long,
    val userTakeovers: Long,
    val taskDurationMs: Long,
) {
    val terminalTasks: Long
        get() = completedTasks + failedTasks + pausedTasks + cancelledTasks + budgetExceededTasks

    val taskSuccessRate: Float
        get() = if (terminalTasks > 0L) completedTasks.toFloat() / terminalTasks else 0f

    val autonomousRate: Float
        get() = if (completedTasks > 0L) autonomousCompletedTasks.toFloat() / completedTasks else 0f

    val modelSuccessRate: Float
        get() = if (modelCalls > 0L) (modelCalls - modelFailures).coerceAtLeast(0L).toFloat() / modelCalls else 0f

    val actionSuccessRate: Float
        get() {
            val total = successfulActions + failedActions
            return if (total > 0L) successfulActions.toFloat() / total else 0f
        }
}

@Immutable
private data class HeatmapCell(
    val column: Int,
    val row: Int,
    val tokens: Long,
    val future: Boolean,
)

@Immutable
private data class HeatmapData(
    val weeks: Int,
    val cells: List<HeatmapCell>,
    val maxTokens: Long,
    val activeDays: Int,
)

@Composable
fun AgentAnalyticsScreen(
    appState: AssistantUiState,
    viewModel: AgentAnalyticsViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val skillInventory by viewModel.skillInventory.collectAsState()
    var selectedTabName by rememberSaveable { mutableStateOf(AgentAnalyticsTab.Overview.name) }
    var selectedRangeName by rememberSaveable { mutableStateOf(AgentAnalyticsRange.Weeks12.name) }
    val selectedTab = remember(selectedTabName) {
        AgentAnalyticsTab.entries.firstOrNull { it.name == selectedTabName } ?: AgentAnalyticsTab.Overview
    }
    val selectedRange = remember(selectedRangeName) {
        AgentAnalyticsRange.entries.firstOrNull { it.name == selectedRangeName } ?: AgentAnalyticsRange.Weeks12
    }
    val period = remember(snapshot, selectedRange) {
        buildPeriod(snapshot, selectedRange)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AgentAnalyticsTab.Capabilities) {
            viewModel.ensureSkillInventoryLoaded()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "analytics_header") {
            AgentAnalyticsHeader(appState, onBack)
        }
        item(key = "analytics_range") {
            AgentSegmentedControl(
                labels = AgentAnalyticsRange.entries.map { it.name to it.label },
                selectedKey = selectedRange.name,
                onSelected = { selectedRangeName = it },
            )
        }
        item(key = "analytics_hero") {
            AgentAnalyticsHero(
                period = period,
                allTimeStreak = snapshot.totals.currentActiveStreakDays,
                rangeLabel = selectedRange.label,
            )
        }
        item(key = "analytics_tabs") {
            AgentSegmentedControl(
                labels = AgentAnalyticsTab.entries.map { it.name to it.label },
                selectedKey = selectedTab.name,
                onSelected = { selectedTabName = it },
            )
        }

        if (!snapshot.loaded) {
            item(key = "analytics_loading") {
                AgentMessageCard(
                    title = "正在准备统计",
                    message = "只在进入本页后读取本地聚合数据，不扫描聊天记录，也不会额外截图。",
                )
            }
        } else {
            when (selectedTab) {
                AgentAnalyticsTab.Overview -> {
                    item(key = "overview_heatmap") {
                        TokenHeatmapCard(
                            daily = snapshot.dailyActivity,
                            weeks = selectedRange.heatmapWeeks,
                        )
                    }
                    item(key = "overview_pulse") {
                        AgentOverviewPulseCard(period)
                    }
                    item(key = "overview_insights") {
                        AgentInsightsCard(period, snapshot)
                    }
                    item(key = "overview_recent_title") {
                        AgentSectionTitle("最近任务", "最近收口的智能体执行记录")
                    }
                    if (period.tasks.isEmpty()) {
                        item(key = "overview_recent_empty") {
                            AgentMessageCard("还没有任务记录", "完成一次 GUI Plus 任务后，这里会显示耗时、动作和介入情况。")
                        }
                    } else {
                        items(period.tasks.take(3), key = { "overview_${it.taskId}" }) { task ->
                            AgentTaskRow(task)
                        }
                    }
                }

                AgentAnalyticsTab.Tokens -> {
                    item(key = "tokens_heatmap") {
                        TokenHeatmapCard(
                            daily = snapshot.dailyActivity,
                            weeks = selectedRange.heatmapWeeks,
                        )
                    }
                    item(key = "tokens_breakdown") {
                        TokenBreakdownCard(period)
                    }
                    item(key = "tokens_model_title") {
                        AgentSectionTitle("模型使用", "累计数据 · 按 Token 总量排序")
                    }
                    if (snapshot.modelUsage.isEmpty()) {
                        item(key = "tokens_model_empty") {
                            AgentMessageCard("暂无模型数据", "下一次模型请求完成后会开始记录。")
                        }
                    } else {
                        items(snapshot.modelUsage.take(6), key = { "model_${it.modelId}" }) { model ->
                            AgentModelRow(model)
                        }
                    }
                }

                AgentAnalyticsTab.Tasks -> {
                    item(key = "tasks_performance") {
                        AgentTaskPerformanceCard(period, snapshot.totals.longestTaskDurationMs)
                    }
                    item(key = "tasks_recent_title") {
                        AgentSectionTitle("任务记录", "按开始时间倒序，仅加载最近 100 条摘要")
                    }
                    if (period.tasks.isEmpty()) {
                        item(key = "tasks_empty") {
                            AgentMessageCard("当前范围没有任务", "切换时间范围，或完成一次智能体任务后再查看。")
                        }
                    } else {
                        items(period.tasks.take(20), key = { "task_${it.taskId}" }) { task ->
                            AgentTaskRow(task)
                        }
                    }
                }

                AgentAnalyticsTab.Capabilities -> {
                    item(key = "skills_inventory") {
                        AgentSkillInventoryCard(skillInventory)
                    }
                    item(key = "capability_title") {
                        AgentSectionTitle("能力使用", "累计工具、功能、动作与应用调用")
                    }
                    if (snapshot.capabilityUsage.isEmpty()) {
                        item(key = "capability_empty") {
                            AgentMessageCard("暂无能力数据", "联网、图片理解、设备动作和已验证应用会在使用后出现。")
                        }
                    } else {
                        items(snapshot.capabilityUsage.take(20), key = { "cap_${it.kind}_${it.key}" }) { capability ->
                            AgentCapabilityRow(capability)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentAnalyticsHeader(
    appState: AssistantUiState,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(
            quality = appState.quality,
            glassIntensity = appState.glassIntensity,
            motionIntensity = appState.motionIntensity,
            radius = 999,
            modifier = Modifier.width(92.dp).height(40.dp),
            role = GlassRole.Chip,
            onClick = onBack,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "‹ 返回",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "AGENT INSIGHTS",
                color = AnalyticsMint.copy(alpha = 0.76f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "智能体统计",
                color = Color.White,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Token 活动、任务效率、自主性与能力成长",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AgentSegmentedControl(
    labels: List<Pair<String, String>>,
    selectedKey: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101640).copy(alpha = 0.34f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEach { (key, label) ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(
                                    AnalyticsBlue.copy(alpha = 0.24f),
                                    AnalyticsViolet.copy(alpha = 0.20f),
                                ),
                            )
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                    )
                    .clickable { if (!selected) onSelected(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = Color.White.copy(alpha = if (selected) 0.94f else 0.48f),
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AgentAnalyticsHero(
    period: AgentAnalyticsPeriod,
    allTimeStreak: Int,
    rangeLabel: String,
) {
    FrostInfoGlassPanel(
        radius = 20f,
        backdropAlpha = 1f,
        frostAlpha = 0.09f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(224.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF202D70).copy(alpha = 0.52f),
                            Color(0xFF211B58).copy(alpha = 0.40f),
                            Color(0xFF0E3851).copy(alpha = 0.32f),
                        ),
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = AnalyticsViolet.copy(alpha = 0.09f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                )
                drawCircle(
                    color = AnalyticsMint.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.08f, size.height * 0.94f),
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 19.dp, vertical = 17.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$rangeLabel 活动",
                        color = AnalyticsMint.copy(alpha = 0.76f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        formatCompactNumber(period.totalTokens),
                        color = Color.White,
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "累计 Token",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroMetric("活跃天数", period.activeDays.toString(), Modifier.weight(1f))
                    HeroMetric("完成任务", period.completedTasks.toString(), Modifier.weight(1f))
                    HeroMetric("自主完成", formatPercent(period.autonomousRate), Modifier.weight(1f))
                    HeroMetric("连续活跃", "${allTimeStreak} 天", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 8.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TokenHeatmapCard(
    daily: List<AgentDailyActivity>,
    weeks: Int,
) {
    val heatmap = remember(daily, weeks) { buildHeatmap(daily, weeks) }
    val horizontalState = rememberScrollState()
    val cellSize = 11.dp
    val gap = 3.dp
    val chartWidth = (cellSize + gap) * weeks - gap
    val chartHeight = (cellSize + gap) * 7 - gap

    LaunchedEffect(horizontalState.maxValue, weeks) {
        if (horizontalState.maxValue > 0) {
            horizontalState.scrollTo(horizontalState.maxValue)
        }
    }

    AgentFrostCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Token 活动热力图",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${heatmap.weeks} 周 · ${heatmap.activeDays} 个活跃日 · 每格一天",
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            HeatmapLegend()
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.width(18.dp).height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                    Text(
                        label,
                        color = Color.White.copy(alpha = 0.34f),
                        fontSize = 8.sp,
                        lineHeight = 11.sp,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalState),
            ) {
                Canvas(Modifier.width(chartWidth).height(chartHeight)) {
                    val cellPx = cellSize.toPx()
                    val gapPx = gap.toPx()
                    val max = heatmap.maxTokens.coerceAtLeast(1L).toFloat()
                    heatmap.cells.forEach { cell ->
                        val ratio = (cell.tokens / max).coerceIn(0f, 1f)
                        val color = when {
                            cell.future -> Color.White.copy(alpha = 0.025f)
                            cell.tokens <= 0L -> Color.White.copy(alpha = 0.065f)
                            ratio < 0.2f -> AnalyticsBlue.copy(alpha = 0.28f)
                            ratio < 0.45f -> AnalyticsBlue.copy(alpha = 0.52f)
                            ratio < 0.72f -> AnalyticsViolet.copy(alpha = 0.72f)
                            else -> AnalyticsMint.copy(alpha = 0.94f)
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(
                                x = cell.column * (cellPx + gapPx),
                                y = cell.row * (cellPx + gapPx),
                            ),
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }
            }
        }
        if (heatmap.maxTokens <= 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                "从下一次模型调用开始积累，旧版本历史不会被推测补写。",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("少", color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp)
        listOf(
            Color.White.copy(alpha = 0.07f),
            AnalyticsBlue.copy(alpha = 0.28f),
            AnalyticsBlue.copy(alpha = 0.52f),
            AnalyticsViolet.copy(alpha = 0.72f),
            AnalyticsMint.copy(alpha = 0.94f),
        ).forEach { color ->
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color),
            )
        }
        Text("多", color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp)
    }
}

@Composable
private fun AgentOverviewPulseCard(period: AgentAnalyticsPeriod) {
    AgentFrostCard {
        AgentCardTitle("运行概览", "选定时间范围内的真实聚合")
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactMetric("对话", formatCompactNumber(period.chatCalls), AnalyticsBlue, Modifier.weight(1f))
            CompactMetric("模型调用", formatCompactNumber(period.modelCalls), AnalyticsViolet, Modifier.weight(1f))
            CompactMetric("规划轮次", formatCompactNumber(period.agentModelTurns), AnalyticsMint, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactMetric("执行动作", formatCompactNumber(period.executedActions), AnalyticsWarm, Modifier.weight(1f))
            CompactMetric("重新观察", formatCompactNumber(period.reobservations), AnalyticsBlue, Modifier.weight(1f))
            CompactMetric("用户介入", formatCompactNumber(period.confirmationRequests + period.userInputRequests + period.userTakeovers), AnalyticsViolet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AgentInsightsCard(
    period: AgentAnalyticsPeriod,
    snapshot: AgentAnalyticsSnapshot,
) {
    val insights = remember(period, snapshot.totals.longestTaskDurationMs) {
        buildInsights(period, snapshot.totals.longestTaskDurationMs)
    }
    AgentFrostCard {
        AgentCardTitle("活动洞察", "只根据已记录事实生成，不做能力猜测")
        Spacer(Modifier.height(12.dp))
        insights.forEachIndexed { index, text ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(listOf(AnalyticsMint, AnalyticsBlue, AnalyticsViolet)[index % 3]),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TokenBreakdownCard(period: AgentAnalyticsPeriod) {
    val total = period.totalTokens.coerceAtLeast(1L)
    val providerRatio = (period.providerTokens.toFloat() / total).coerceIn(0f, 1f)
    val estimatedRatio = (period.estimatedTokens.toFloat() / total).coerceIn(0f, 1f)

    AgentFrostCard {
        AgentCardTitle("Token 构成", "真实 usage 与本地保守估算分开显示")
        Spacer(Modifier.height(14.dp))
        TokenBreakdownLine("Provider 真实 Token", period.providerTokens, providerRatio, AnalyticsMint)
        Spacer(Modifier.height(12.dp))
        TokenBreakdownLine("Estimated 估算 Token", period.estimatedTokens, estimatedRatio, AnalyticsViolet)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactMetric("峰值日", formatCompactNumber(period.peakDailyTokens), AnalyticsWarm, Modifier.weight(1f))
            CompactMetric("活跃日", period.activeDays.toString(), AnalyticsBlue, Modifier.weight(1f))
            CompactMetric("调用成功", formatPercent(period.modelSuccessRate), AnalyticsMint, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TokenBreakdownLine(
    label: String,
    value: Long,
    progress: Float,
    tone: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatCompactNumber(value),
                color = tone.copy(alpha = 0.94f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.07f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tone.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun AgentTaskPerformanceCard(
    period: AgentAnalyticsPeriod,
    longestTaskDurationMs: Long,
) {
    AgentFrostCard {
        AgentCardTitle("任务效率", "成功、自主性与恢复情况")
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactMetric("成功率", formatPercent(period.taskSuccessRate), AnalyticsMint, Modifier.weight(1f))
            CompactMetric("自主完成", formatPercent(period.autonomousRate), AnalyticsBlue, Modifier.weight(1f))
            CompactMetric("动作成功", formatPercent(period.actionSuccessRate), AnalyticsViolet, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MetricPair("完成任务", period.completedTasks.toString(), "介入后完成", period.assistedCompletedTasks.toString())
        MetricPair("执行失败", period.executionFailures.toString(), "计划拒绝", period.rejectedPlans.toString())
        MetricPair("累计耗时", formatDuration(period.taskDurationMs), "最长任务", formatDuration(longestTaskDurationMs))
    }
}

@Composable
private fun AgentSkillInventoryCard(inventory: AgentSkillInventory) {
    AgentFrostCard {
        AgentCardTitle("Skill 资产", "操作学习形成的长期可复用能力")
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactMetric("全部 Skill", inventory.totalSkills.toString(), AnalyticsBlue, Modifier.weight(1f))
            CompactMetric("可用", inventory.usableSkills.toString(), AnalyticsMint, Modifier.weight(1f))
            CompactMetric("待审核", inventory.reviewSkills.toString(), AnalyticsWarm, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MetricPair("已验证", inventory.verifiedSkills.toString(), "已批准", inventory.approvedSkills.toString())
        MetricPair("演示次数", inventory.demonstrations.toString(), "覆盖应用", inventory.scopedApps.toString())
        MetricPair("运行次数", inventory.totalRuns.toString(), "成功运行", inventory.successfulRuns.toString())
    }
}

@Composable
private fun AgentModelRow(model: AgentModelAnalytics) {
    AgentFrostCard(contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AnalyticsViolet.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    model.displayName.take(1).uppercase(),
                    color = AnalyticsViolet,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    model.displayName,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${model.calls} 次调用 · ${formatCompactNumber(model.totalTokens)} Token",
                    color = Color.White.copy(alpha = 0.44f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Text(
                formatPercent(if (model.calls > 0L) (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls else 0f),
                color = AnalyticsMint.copy(alpha = 0.88f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AgentCapabilityRow(capability: AgentCapabilityAnalytics) {
    val total = capability.successes + capability.failures
    val successRate = if (total > 0L) capability.successes.toFloat() / total else 0f
    AgentFrostCard(contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(39.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(capabilityTone(capability.kind).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    capabilityKindSymbol(capability.kind),
                    color = capabilityTone(capability.kind),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    capability.displayName,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${capabilityKindLabel(capability.kind)} · ${capability.uses} 次使用",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Text(
                if (total > 0L) formatPercent(successRate) else "累计",
                color = capabilityTone(capability.kind).copy(alpha = 0.88f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AgentTaskRow(task: AgentTaskAnalytics) {
    val tone = taskStatusTone(task.status)
    AgentFrostCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        task.goal.ifBlank { "未命名智能体任务" },
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatTaskTime(task.startedAtMillis),
                        color = Color.White.copy(alpha = 0.36f),
                        fontSize = 9.5.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(tone.copy(alpha = 0.13f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        taskStatusLabel(task.status),
                        color = tone.copy(alpha = 0.94f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyMetric("耗时", formatDuration(task.durationMs), Modifier.weight(1f))
                TinyMetric("动作", task.executedActions.toString(), Modifier.weight(1f))
                TinyMetric("Token", formatCompactNumber(task.totalTokens), Modifier.weight(1f))
                TinyMetric("介入", task.interventionCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AgentFrostCard(
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable Column.() -> Unit,
) {
    FrostInfoGlassPanel(
        radius = 18f,
        backdropAlpha = 1f,
        frostAlpha = 0.082f,
        dimAlpha = 0f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF111642).copy(alpha = 0.24f))
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun AgentCardTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.43f),
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AgentSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.5.sp,
        )
    }
}

@Composable
private fun AgentMessageCard(title: String, message: String) {
    AgentFrostCard {
        Text(
            title,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            message,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(tone.copy(alpha = 0.085f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            color = tone.copy(alpha = 0.94f),
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 8.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun MetricPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            leftLabel,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            leftValue,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(18.dp))
        Text(
            rightLabel,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 10.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            rightValue,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TinyMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 7.5.sp,
            maxLines = 1,
        )
    }
}

private fun buildPeriod(
    snapshot: AgentAnalyticsSnapshot,
    range: AgentAnalyticsRange,
): AgentAnalyticsPeriod {
    val today = LocalDate.now(ZoneId.systemDefault())
    val cutoffDate = range.days?.let { today.minusDays((it - 1L).coerceAtLeast(0L)) }
    val cutoffKey = cutoffDate?.toString()
    val daily = if (cutoffKey == null) {
        snapshot.dailyActivity
    } else {
        snapshot.dailyActivity.filter { it.dateKey >= cutoffKey }
    }
    val cutoffMillis = cutoffDate
        ?.atStartOfDay(ZoneId.systemDefault())
        ?.toInstant()
        ?.toEpochMilli()
    val tasks = if (cutoffMillis == null) {
        snapshot.recentTasks
    } else {
        snapshot.recentTasks.filter { it.startedAtMillis >= cutoffMillis }
    }

    return AgentAnalyticsPeriod(
        daily = daily,
        tasks = tasks,
        totalTokens = daily.safeSumOf { it.totalTokens },
        providerTokens = daily.safeSumOf { it.providerTokens },
        estimatedTokens = daily.safeSumOf { it.estimatedTokens },
        peakDailyTokens = daily.maxOfOrNull { it.totalTokens } ?: 0L,
        activeDays = daily.count { it.totalTokens > 0L || it.chatCalls > 0L || it.agentTasks > 0L || it.executedActions > 0L },
        chatCalls = daily.safeSumOf { it.chatCalls },
        modelCalls = daily.safeSumOf { it.modelCalls },
        modelFailures = daily.safeSumOf { it.modelFailures },
        agentTasks = daily.safeSumOf { it.agentTasks },
        completedTasks = daily.safeSumOf { it.completedTasks },
        autonomousCompletedTasks = daily.safeSumOf { it.autonomousCompletedTasks },
        assistedCompletedTasks = daily.safeSumOf { it.assistedCompletedTasks },
        failedTasks = daily.safeSumOf { it.failedTasks },
        pausedTasks = daily.safeSumOf { it.pausedTasks },
        cancelledTasks = daily.safeSumOf { it.cancelledTasks },
        budgetExceededTasks = daily.safeSumOf { it.budgetExceededTasks },
        agentModelTurns = daily.safeSumOf { it.agentModelTurns },
        executedActions = daily.safeSumOf { it.executedActions },
        successfulActions = daily.safeSumOf { it.successfulActions },
        failedActions = daily.safeSumOf { it.failedActions },
        observations = daily.safeSumOf { it.observations },
        reobservations = daily.safeSumOf { it.reobservations },
        rejectedPlans = daily.safeSumOf { it.rejectedPlans },
        executionFailures = daily.safeSumOf { it.executionFailures },
        confirmationRequests = daily.safeSumOf { it.confirmationRequests },
        userInputRequests = daily.safeSumOf { it.userInputRequests },
        userTakeovers = daily.safeSumOf { it.userTakeovers },
        taskDurationMs = daily.safeSumOf { it.taskDurationMs },
    )
}

private fun buildHeatmap(
    daily: List<AgentDailyActivity>,
    requestedWeeks: Int,
): HeatmapData {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays((weeks * 7L) - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val cells = ArrayList<HeatmapCell>(weeks * 7)
    var maxTokens = 0L
    var activeDays = 0

    repeat(weeks * 7) { index ->
        val date = startMonday.plusDays(index.toLong())
        val value = values[date.toString()] ?: 0L
        if (!date.isAfter(today)) {
            maxTokens = maxOf(maxTokens, value)
            if (value > 0L) activeDays += 1
        }
        cells += HeatmapCell(
            column = index / 7,
            row = index % 7,
            tokens = value,
            future = date.isAfter(today),
        )
    }
    return HeatmapData(
        weeks = weeks,
        cells = cells,
        maxTokens = maxTokens,
        activeDays = activeDays,
    )
}

private fun buildInsights(
    period: AgentAnalyticsPeriod,
    longestTaskDurationMs: Long,
): List<String> {
    if (period.totalTokens <= 0L && period.agentTasks <= 0L) {
        return listOf(
            "当前范围还没有智能体活动，统计会从下一次真实模型调用开始积累。",
            "Token 热力图不会根据旧聊天内容倒推，因此不会出现伪造的历史活跃记录。",
            "统计写入采用低频聚合，不会持续扫描页面、节点或聊天记录。",
        )
    }
    val providerShare = if (period.totalTokens > 0L) {
        period.providerTokens.toFloat() / period.totalTokens
    } else {
        0f
    }
    return listOf(
        "${period.activeDays} 个活跃日累计 ${formatCompactNumber(period.totalTokens)} Token，其中 ${formatPercent(providerShare)} 来自供应商真实 usage。",
        if (period.completedTasks > 0L) {
            "完成 ${period.completedTasks} 个任务，自主完成率 ${formatPercent(period.autonomousRate)}，介入后完成 ${period.assistedCompletedTasks} 个。"
        } else {
            "当前范围还没有完成任务，模型调用和 Token 活动仍会正常记录。"
        },
        "动作成功率 ${formatPercent(period.actionSuccessRate)}，累计重新观察 ${period.reobservations} 次；历史最长任务 ${formatDuration(longestTaskDurationMs)}。",
    )
}

private inline fun List<AgentDailyActivity>.safeSumOf(selector: (AgentDailyActivity) -> Long): Long {
    var total = 0L
    forEach { item ->
        val value = selector(item).coerceAtLeast(0L)
        total = if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
    }
    return total
}

private fun formatCompactNumber(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 100_000_000L -> formatOneDecimal(safe / 100_000_000.0) + " 亿"
        safe >= 10_000L -> formatOneDecimal(safe / 10_000.0) + " 万"
        else -> safe.toString()
    }
}

private fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun formatPercent(value: Float): String = "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    return when {
        seconds >= 3_600L -> {
            val hours = seconds / 3_600L
            val minutes = (seconds % 3_600L) / 60L
            if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
        }
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }
}

private fun formatTaskTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "时间未知"
    val dateTime = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now(ZoneId.systemDefault())
    val date = dateTime.toLocalDate()
    val prefix = when {
        date == today -> "今天"
        date == today.minusDays(1L) -> "昨天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
    return "%s %02d:%02d".format(prefix, dateTime.hour, dateTime.minute)
}

private fun taskStatusLabel(status: String): String = when (status.lowercase()) {
    "completed" -> "已完成"
    "failed" -> "失败"
    "paused" -> "已暂停"
    "cancelled", "canceled" -> "已停止"
    "budget_exceeded" -> "达到上限"
    "interrupted" -> "意外中断"
    "running" -> "执行中"
    else -> status.ifBlank { "未知" }
}

private fun taskStatusTone(status: String): Color = when (status.lowercase()) {
    "completed" -> AnalyticsMint
    "running" -> AnalyticsBlue
    "paused", "interrupted" -> AnalyticsWarm
    else -> AnalyticsDanger
}

private fun capabilityKindLabel(kind: String): String = when (kind.lowercase()) {
    "feature" -> "功能"
    "tool" -> "工具"
    "action" -> "动作"
    "app" -> "应用"
    else -> "能力"
}

private fun capabilityKindSymbol(kind: String): String = when (kind.lowercase()) {
    "feature" -> "F"
    "tool" -> "T"
    "action" -> "A"
    "app" -> "APP"
    else -> "AI"
}

private fun capabilityTone(kind: String): Color = when (kind.lowercase()) {
    "feature" -> AnalyticsMint
    "tool" -> AnalyticsBlue
    "action" -> AnalyticsViolet
    "app" -> AnalyticsWarm
    else -> Color.White
}
