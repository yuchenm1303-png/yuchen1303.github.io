package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuchen.ailedger.AgentAnalyticsSyncPhase
import com.yuchen.ailedger.AgentAnalyticsSyncUiState
import com.yuchen.ailedger.AgentAnalyticsViewModel
import com.yuchen.ailedger.data.AgentAnalyticsOwner
import com.yuchen.ailedger.data.SupabaseAccountState
import com.yuchen.ailedger.data.UserProfileState
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentSkillInventory
import com.yuchen.ailedger.model.AgentTaskAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val WideStatsBlue = Color(0xFF8FB2FF)
private val WideStatsViolet = Color(0xFFB49BFF)
private val WideStatsMint = Color(0xFF7BE8D2)
private val WideStatsWarm = Color(0xFFFFC58A)
private val WideStatsDanger = Color(0xFFFF9EAF)
private val WideStatsCard = Color(0xFF11152F).copy(alpha = 0.78f)
private val WidePagePadding = 9.dp

private enum class WideStatsTab(val label: String) {
    Overview("总览"), Tokens("Token"), Tasks("任务"), Capabilities("能力")
}

private enum class WideHeatMode(val label: String) {
    Daily("每日"), Weekly("每周"), Cumulative("累计")
}

private data class WideHeatCell(val column: Int, val row: Int, val tokens: Long, val future: Boolean)
private data class WideHeatmap(val weeks: Int, val cells: List<WideHeatCell>, val maxTokens: Long, val activeDays: Int)
private data class WideUsageMetric(val label: String, val value: String, val raw: Long, val tone: Color)

@Composable
internal fun AgentAnalyticsProfileWideScreen(
    viewModel: AgentAnalyticsViewModel,
    onBack: () -> Unit,
) {
    val snapshot by viewModel.state.collectAsState()
    val skills by viewModel.skillInventory.collectAsState()
    val owner by viewModel.owner.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var tabName by rememberSaveable { mutableStateOf(WideStatsTab.Overview.name) }
    val selectedTab = remember(tabName) { WideStatsTab.entries.firstOrNull { it.name == tabName } ?: WideStatsTab.Overview }

    LaunchedEffect(selectedTab) {
        if (selectedTab == WideStatsTab.Capabilities) viewModel.ensureSkillInventoryLoaded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("wide_top") { WideTopBar(onBack) }
        item("wide_identity") { WideIdentity(snapshot, owner, accountState, profileState, syncState) }
        item("wide_sync") { WideSyncCard(owner, accountState, syncState, viewModel::retryCloudSync) }
        item("wide_tabs") { WideTabs(selectedTab) { tabName = it.name } }

        if (!snapshot.loaded) {
            item("wide_loading") {
                WideCard {
                    Text("正在读取统计", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(5.dp))
                    Text("本页低频读取本地快照，不持续观察数据库。", color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        } else {
            when (selectedTab) {
                WideStatsTab.Overview -> {
                    item("wide_heatmap") { WideHeatmapCard(snapshot.dailyActivity) }
                    item("wide_runtime") { WideRuntimeCard(snapshot) }
                    item("wide_recent_title") { WideSectionTitle("最近任务", "本机任务摘要") }
                    if (snapshot.recentTasks.isEmpty()) item("wide_recent_empty") { WideEmpty("还没有智能体任务记录") }
                    else items(snapshot.recentTasks.take(5), key = { "wide_recent_${it.taskId}" }) { WideTaskRow(it) }
                }
                WideStatsTab.Tokens -> {
                    item("wide_token_summary") { WideTokenCard(snapshot) }
                    item("wide_token_heatmap") { WideHeatmapCard(snapshot.dailyActivity) }
                    item("wide_models_title") { WideSectionTitle("模型使用", "按累计 Token 排序") }
                    if (snapshot.modelUsage.isEmpty()) item("wide_models_empty") { WideEmpty("暂无模型使用数据") }
                    else items(snapshot.modelUsage.take(12), key = { "wide_model_${it.modelId}" }) { WideModelRow(it) }
                }
                WideStatsTab.Tasks -> {
                    item("wide_task_summary") { WideTaskSummary(snapshot) }
                    item("wide_task_title") { WideSectionTitle("任务记录", "最多显示最近 100 条") }
                    if (snapshot.recentTasks.isEmpty()) item("wide_task_empty") { WideEmpty("暂无任务记录") }
                    else items(snapshot.recentTasks, key = { "wide_task_${it.taskId}" }) { WideTaskRow(it) }
                }
                WideStatsTab.Capabilities -> {
                    item("wide_skill") { WideSkillCard(skills) }
                    item("wide_cap_title") { WideSectionTitle("能力使用", "工具、功能、动作与应用") }
                    if (snapshot.capabilityUsage.isEmpty()) item("wide_cap_empty") { WideEmpty("暂无能力使用数据") }
                    else items(snapshot.capabilityUsage.take(40), key = { "wide_cap_${it.kind}_${it.key}" }) { WideCapabilityRow(it) }
                }
            }
        }
    }
}

@Composable
private fun WideTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WidePagePadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(19.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹ 返回", color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("智能体统计", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WideIdentity(
    snapshot: AgentAnalyticsSnapshot,
    owner: AgentAnalyticsOwner,
    accountState: SupabaseAccountState,
    profileState: UserProfileState,
    syncState: AgentAnalyticsSyncUiState,
) {
    val loggedIn = accountState.isLoggedIn && !owner.isGuest
    val email = accountState.email.orEmpty()
    val displayName = if (loggedIn) {
        profileState.profile?.displayName?.takeIf(String::isNotBlank)
            ?: email.substringBefore('@').replace(Regex("[._-]+"), " ").trim().take(24).ifBlank { "AI Ledger 用户" }
    } else {
        "本地用户"
    }
    val fallback = displayName.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "AI" }
    val handle = if (loggedIn) "@" + email.substringBefore('@').trim().take(22).ifBlank { "account" } else "@local"
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WidePagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        UserProfileAvatar(
            localAvatarPath = profileState.localAvatarPath,
            avatarVersion = profileState.profile?.avatarVersion ?: 0L,
            fallbackText = fallback,
            size = 92.dp,
            loggedIn = loggedIn,
        )
        Text(displayName, color = Color.White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(handle, color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(wideSyncTone(syncState.phase).copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text(if (loggedIn) "账号空间" else "仅本机", color = wideSyncTone(syncState.phase), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(6.dp))
        WideMetricStrip(
            listOf(
                Triple("峰值 Token", wideNumber(snapshot.totals.peakDailyTokens), "单日最高"),
                Triple("最长任务", wideDuration(snapshot.totals.longestTaskDurationMs), "本机单次"),
                Triple("当前连续", "${snapshot.totals.currentActiveStreakDays} 天", "活跃天数"),
                Triple("最长连续", "${snapshot.totals.longestActiveStreakDays} 天", "历史纪录"),
            )
        )
    }
}

@Composable
private fun WideMetricStrip(metrics: List<Triple<String, String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.035f)).border(1.dp, Color.White.copy(alpha = 0.065f), RoundedCornerShape(24.dp)).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        metrics.forEachIndexed { index, metric ->
            if (index > 0) Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.07f)))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(metric.second, color = Color.White.copy(alpha = 0.94f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(metric.first, color = Color.White.copy(alpha = 0.44f), fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(metric.third, color = Color.White.copy(alpha = 0.23f), fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WideSyncCard(owner: AgentAnalyticsOwner, accountState: SupabaseAccountState, syncState: AgentAnalyticsSyncUiState, onSync: () -> Unit) {
    val loggedIn = accountState.isLoggedIn && !owner.isGuest
    val phase = if (accountState.loading) AgentAnalyticsSyncPhase.Checking else syncState.phase
    val syncing = phase == AgentAnalyticsSyncPhase.Syncing
    WideCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(wideSyncLabel(phase), color = wideSyncTone(phase), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    when {
                        accountState.loading -> "正在确认账号状态，不会阻塞本地统计。"
                        loggedIn -> syncState.message
                        else -> "访客数据只保存在当前设备，登录后会切换到独立账号空间。"
                    },
                    color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, lineHeight = 15.sp,
                )
                if (loggedIn && syncState.lastSyncedAtMillis > 0L) Text("上次同步 ${wideSyncTime(syncState.lastSyncedAtMillis)}", color = Color.White.copy(alpha = 0.29f), fontSize = 8.5.sp)
            }
            if (loggedIn) {
                Box(
                    modifier = Modifier.width(76.dp).height(36.dp).clip(RoundedCornerShape(14.dp)).background(if (syncing) Color.White.copy(alpha = 0.045f) else WideStatsBlue.copy(alpha = 0.13f)).border(1.dp, WideStatsBlue.copy(alpha = if (syncing) 0.08f else 0.20f), RoundedCornerShape(14.dp)).clickable(enabled = !syncing, onClick = onSync),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (syncing) "同步中" else "立即同步", color = if (syncing) Color.White.copy(alpha = 0.35f) else WideStatsBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun WideTabs(selected: WideStatsTab, onSelected: (WideStatsTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WidePagePadding).height(58.dp).clip(RoundedCornerShape(23.dp)).background(Color.White.copy(alpha = 0.045f)).border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(23.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WideStatsTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(if (active) WideStatsViolet.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.025f)).border(1.dp, if (active) WideStatsViolet.copy(alpha = 0.28f) else Color.Transparent, RoundedCornerShape(18.dp)).clickable(enabled = !active) { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label, color = Color.White.copy(alpha = if (active) 0.94f else 0.50f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WideHeatmapCard(daily: List<AgentDailyActivity>) {
    var modeName by rememberSaveable { mutableStateOf(WideHeatMode.Daily.name) }
    val mode = remember(modeName) { WideHeatMode.entries.firstOrNull { it.name == modeName } ?: WideHeatMode.Daily }
    val weeks = 52
    val heatmap = remember(daily, mode) { wideBuildHeatmap(daily, weeks, mode) }
    val rows = 7
    val gap = 4.dp
    val cellHeight = 10.dp
    val chartHeight = cellHeight * rows + gap * (rows - 1)
    val monthLabels = remember(weeks) { wideMonthLabels(weeks) }
    WideCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Token 活动", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("近一年 · ${heatmap.activeDays} 个活跃日 · ${mode.label}视图", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
            }
            WideHeatModeTabs(mode) { modeName = it.name }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.width(18.dp).height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start,
            ) {
                listOf("一", "三", "五", "日").forEach { label ->
                    Text(label, color = Color.White.copy(alpha = 0.28f), fontSize = 7.5.sp, lineHeight = 9.sp)
                }
            }
            Canvas(Modifier.weight(1f).height(chartHeight)) {
                val columns = heatmap.weeks.coerceAtLeast(1)
                val gapPx = gap.toPx()
                val cellWidth = ((size.width - gapPx * (columns - 1)) / columns).coerceAtLeast(1f)
                val cellHeightPx = cellHeight.toPx()
                val max = heatmap.maxTokens.coerceAtLeast(1L).toFloat()
                heatmap.cells.forEach { entry ->
                    val ratio = (entry.tokens.toFloat() / max).coerceIn(0f, 1f)
                    val color = when {
                        entry.future -> Color.White.copy(alpha = 0.018f)
                        entry.tokens <= 0L -> Color.White.copy(alpha = 0.050f)
                        ratio < 0.20f -> WideStatsBlue.copy(alpha = 0.30f)
                        ratio < 0.45f -> WideStatsBlue.copy(alpha = 0.54f)
                        ratio < 0.72f -> WideStatsViolet.copy(alpha = 0.72f)
                        else -> WideStatsMint.copy(alpha = 0.94f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(entry.column * (cellWidth + gapPx), entry.row * (cellHeightPx + gapPx)),
                        size = Size(cellWidth, cellHeightPx),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            monthLabels.forEach { label ->
                Text(label, color = Color.White.copy(alpha = 0.34f), fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WideHeatModeTabs(selected: WideHeatMode, onSelected: (WideHeatMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        WideHeatMode.entries.forEach { mode ->
            Text(
                text = mode.label,
                color = Color.White.copy(alpha = if (mode == selected) 0.92f else 0.42f),
                fontSize = 10.sp,
                fontWeight = if (mode == selected) FontWeight.Black else FontWeight.Bold,
                modifier = Modifier.clickable(enabled = mode != selected) { onSelected(mode) },
            )
        }
    }
}

@Composable
private fun WideRuntimeCard(snapshot: AgentAnalyticsSnapshot) {
    val rows = listOf(
        WideUsageMetric("累计 Token", wideNumber(snapshot.totals.totalTokens), snapshot.totals.totalTokens, WideStatsBlue),
        WideUsageMetric("模型调用", wideNumber(snapshot.totals.modelCalls), snapshot.totals.modelCalls, WideStatsViolet),
        WideUsageMetric("规划轮次", wideNumber(snapshot.totals.agentModelTurns), snapshot.totals.agentModelTurns, WideStatsMint),
        WideUsageMetric("执行动作", wideNumber(snapshot.totals.executedActions), snapshot.totals.executedActions, WideStatsBlue),
        WideUsageMetric("完成任务", snapshot.totals.completedTasks.toString(), snapshot.totals.completedTasks, WideStatsWarm),
        WideUsageMetric("累计任务时长", wideDuration(snapshot.totals.totalTaskDurationMs), snapshot.totals.totalTaskDurationMs / 1_000L, WideStatsViolet),
    )
    val maxValue = rows.maxOfOrNull { it.raw }?.coerceAtLeast(1L) ?: 1L
    WideCard {
        Text("运行概览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text("本机明细与账号每日聚合", color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
        Spacer(Modifier.height(11.dp))
        rows.forEach { metric ->
            WideUsageBar(metric, maxValue)
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun WideUsageBar(metric: WideUsageMetric, maxValue: Long) {
    val progress = if (maxValue > 0L) (metric.raw.toFloat() / maxValue.toFloat()).coerceIn(0.02f, 1f) else 0.02f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(metric.label, color = Color.White.copy(alpha = 0.48f), fontSize = 10.5.sp, modifier = Modifier.weight(1f))
            Text(metric.value, color = metric.tone, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.055f))) {
            Box(Modifier.fillMaxWidth(progress).height(7.dp).clip(RoundedCornerShape(999.dp)).background(metric.tone.copy(alpha = 0.70f)))
        }
    }
}

@Composable
private fun WideTokenCard(snapshot: AgentAnalyticsSnapshot) {
    WideRows("Token 构成", "真实 usage 与本地估算分开显示", listOf(
        Triple("Provider 真实", wideNumber(snapshot.totals.providerTokens), WideStatsMint),
        Triple("Estimated 估算", wideNumber(snapshot.totals.estimatedTokens), WideStatsViolet),
        Triple("峰值日", wideNumber(snapshot.totals.peakDailyTokens), WideStatsWarm),
        Triple("累计总量", wideNumber(snapshot.totals.totalTokens), WideStatsBlue),
    ))
}

@Composable
private fun WideTaskSummary(snapshot: AgentAnalyticsSnapshot) {
    WideRows("任务效率", "成功率、自主性与耗时", listOf(
        Triple("任务成功率", widePercent(snapshot.totals.taskSuccessRate), WideStatsMint),
        Triple("自主完成率", widePercent(snapshot.totals.autonomousCompletionRate), WideStatsBlue),
        Triple("完成任务", snapshot.totals.completedTasks.toString(), WideStatsViolet),
        Triple("介入后完成", snapshot.totals.assistedCompletedTasks.toString(), WideStatsWarm),
        Triple("累计耗时", wideDuration(snapshot.totals.totalTaskDurationMs), WideStatsBlue),
    ))
}

@Composable
private fun WideSkillCard(skills: AgentSkillInventory) {
    WideRows("Skill 资产", "操作学习形成的本机可复用能力", listOf(
        Triple("全部 Skill", skills.totalSkills.toString(), WideStatsBlue),
        Triple("可用", skills.usableSkills.toString(), WideStatsMint),
        Triple("待审核", skills.reviewSkills.toString(), WideStatsWarm),
        Triple("覆盖应用", skills.scopedApps.toString(), WideStatsViolet),
        Triple("运行次数", skills.totalRuns.toString(), WideStatsBlue),
        Triple("成功运行", skills.successfulRuns.toString(), WideStatsMint),
    ))
}

@Composable
private fun WideRows(title: String, subtitle: String, rows: List<Triple<String, String, Color>>) {
    WideCard {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
        Spacer(Modifier.height(9.dp))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(row.first, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp)
                Text(row.second, color = row.third, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun WideTaskRow(task: AgentTaskAnalytics) {
    WideCard {
        Text(task.goal.ifBlank { "未命名智能体任务" }, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        WideInline("状态", wideTaskStatus(task.status), wideTaskTone(task.status))
        WideInline("耗时", wideDuration(task.durationMs), WideStatsBlue)
        WideInline("动作", task.executedActions.toString(), WideStatsMint)
        WideInline("Token", wideNumber(task.totalTokens), WideStatsViolet)
    }
}

@Composable
private fun WideModelRow(model: AgentModelAnalytics) {
    val successRate = if (model.calls > 0L) (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls.toFloat() else 0f
    WideCard {
        Text(model.displayName, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        WideInline("调用次数", model.calls.toString(), WideStatsBlue)
        WideInline("累计 Token", wideNumber(model.totalTokens), WideStatsViolet)
        WideInline("调用成功率", widePercent(successRate), WideStatsMint)
    }
}

@Composable
private fun WideCapabilityRow(capability: AgentCapabilityAnalytics) {
    val resultCount = wideSafeAdd(capability.successes, capability.failures)
    val rate = if (resultCount > 0L) capability.successes.toFloat() / resultCount.toFloat() else 0f
    WideCard {
        Text(capability.displayName, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        WideInline("类型", wideCapabilityKind(capability.kind), WideStatsViolet)
        WideInline("使用次数", capability.uses.toString(), WideStatsBlue)
        WideInline("成功率", if (resultCount > 0L) widePercent(rate) else "累计", WideStatsMint)
    }
}

@Composable
private fun WideInline(label: String, value: String, tone: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.43f), fontSize = 10.sp)
        Text(value, color = tone, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WideCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WidePagePadding).clip(shape).background(WideStatsCard).border(1.dp, Color.White.copy(alpha = 0.065f), shape).padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) { content() }
}

@Composable
private fun WideSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = WidePagePadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 10.5.sp)
    }
}

@Composable
private fun WideEmpty(message: String) {
    WideCard { Text(message, color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp) }
}

private fun wideBuildHeatmap(daily: List<AgentDailyActivity>, requestedWeeks: Int, mode: WideHeatMode): WideHeatmap {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks * 7L - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val raw = LongArray(weeks * 7)
    val future = BooleanArray(weeks * 7)
    repeat(weeks * 7) { index ->
        val date = startMonday.plusDays(index.toLong())
        future[index] = date.isAfter(today)
        raw[index] = if (future[index]) 0L else values[date.toString()] ?: 0L
    }
    val weekly = LongArray(weeks)
    raw.forEachIndexed { index, value -> weekly[index / 7] += value }
    val cells = ArrayList<WideHeatCell>(weeks * 7)
    var maxTokens = 0L
    var activeDays = 0
    var cumulative = 0L
    repeat(weeks * 7) { index ->
        if (!future[index]) {
            cumulative += raw[index]
            if (raw[index] > 0L) activeDays += 1
        }
        val displayValue = when (mode) {
            WideHeatMode.Daily -> raw[index]
            WideHeatMode.Weekly -> weekly[index / 7]
            WideHeatMode.Cumulative -> if (future[index]) 0L else cumulative
        }
        if (!future[index]) maxTokens = maxOf(maxTokens, displayValue)
        cells += WideHeatCell(index / 7, index % 7, displayValue, future[index])
    }
    return WideHeatmap(weeks, cells, maxTokens, activeDays)
}

private fun wideMonthLabels(weeks: Int): List<String> {
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks.coerceIn(1, 52) * 7L - 1L)
    val labels = ArrayList<String>()
    var cursor = LocalDate.of(startMonday.year, startMonday.monthValue, 1)
    val endMonth = LocalDate.of(today.year, today.monthValue, 1)
    while (!cursor.isAfter(endMonth) && labels.size < 13) {
        labels += "${cursor.monthValue}月"
        cursor = cursor.plusMonths(1)
    }
    return labels.ifEmpty { listOf("${today.monthValue}月") }
}

private fun wideSyncLabel(phase: AgentAnalyticsSyncPhase): String = when (phase) {
    AgentAnalyticsSyncPhase.Checking -> "正在检查账号"
    AgentAnalyticsSyncPhase.Guest -> "本机访客统计"
    AgentAnalyticsSyncPhase.Syncing -> "正在同步账号统计"
    AgentAnalyticsSyncPhase.Synced -> "账号统计已同步"
    AgentAnalyticsSyncPhase.Cached -> "已复用同步缓存"
    AgentAnalyticsSyncPhase.LocalOnly -> "当前仅显示本机数据"
    AgentAnalyticsSyncPhase.Failed -> "云端同步暂时失败"
}

private fun wideSyncTone(phase: AgentAnalyticsSyncPhase): Color = when (phase) {
    AgentAnalyticsSyncPhase.Synced -> WideStatsMint
    AgentAnalyticsSyncPhase.Cached -> WideStatsBlue
    AgentAnalyticsSyncPhase.Syncing, AgentAnalyticsSyncPhase.Checking -> WideStatsViolet
    AgentAnalyticsSyncPhase.Guest, AgentAnalyticsSyncPhase.LocalOnly -> WideStatsWarm
    AgentAnalyticsSyncPhase.Failed -> WideStatsDanger
}

private fun wideSyncTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "尚未成功同步"
    val time = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
    return "%d月%d日 %02d:%02d".format(time.monthValue, time.dayOfMonth, time.hour, time.minute)
}

private fun wideTaskStatus(status: String): String = when (status.lowercase()) {
    "completed" -> "已完成"
    "failed" -> "失败"
    "paused" -> "已暂停"
    "cancelled", "canceled" -> "已停止"
    "budget_exceeded" -> "达到上限"
    "interrupted" -> "意外中断"
    "running" -> "执行中"
    else -> status.ifBlank { "未知" }
}

private fun wideTaskTone(status: String): Color = when (status.lowercase()) {
    "completed" -> WideStatsMint
    "running" -> WideStatsBlue
    "paused", "interrupted" -> WideStatsWarm
    else -> WideStatsDanger
}

private fun wideCapabilityKind(kind: String): String = when (kind.lowercase()) {
    "feature" -> "功能"
    "tool" -> "工具"
    "action" -> "动作"
    "app" -> "应用"
    else -> "能力"
}

private fun wideNumber(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    return when {
        safe >= 100_000_000L -> wideDecimal(safe / 100_000_000.0) + " 亿"
        safe >= 10_000L -> wideDecimal(safe / 10_000.0) + " 万"
        else -> safe.toString()
    }
}

private fun wideDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun widePercent(value: Float): String {
    val safe = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return "${(safe * 100f).roundToInt()}%"
}

private fun wideDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return when {
        seconds >= 3_600L -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
        seconds >= 60L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds}s"
    }
}

private fun wideSafeAdd(left: Long, right: Long): Long {
    val safeLeft = left.coerceAtLeast(0L)
    val safeRight = right.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
}
