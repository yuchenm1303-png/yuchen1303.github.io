package com.yuchen.ailedger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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

private val WideProfileBlue = Color(0xFF8FB2FF)
private val WideProfileViolet = Color(0xFFB49BFF)
private val WideProfileMint = Color(0xFF7BE8D2)
private val WideProfileWarm = Color(0xFFFFC58A)
private val WideProfileDanger = Color(0xFFFF9EAF)
private val WideProfileCard = Color(0xFF11152F).copy(alpha = 0.78f)

private enum class WideProfileTab(val label: String) {
    Overview("总览"), Tokens("Token"), Tasks("任务"), Capabilities("能力")
}

private data class WideHeatCell(val column: Int, val row: Int, val tokens: Long, val future: Boolean)
private data class WideHeatmap(
    val weeks: Int,
    val cells: List<WideHeatCell>,
    val maxTokens: Long,
    val activeDays: Int,
)

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
    var tabName by rememberSaveable { mutableStateOf(WideProfileTab.Overview.name) }
    val selectedTab = remember(tabName) {
        WideProfileTab.entries.firstOrNull { it.name == tabName } ?: WideProfileTab.Overview
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == WideProfileTab.Capabilities) viewModel.ensureSkillInventoryLoaded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "wide-profile-top") {
            WideProfileTopBar(onBack = onBack)
        }
        item(key = "wide-profile-identity") {
            WideProfileIdentity(
                snapshot = snapshot,
                owner = owner,
                accountState = accountState,
                profileState = profileState,
                syncState = syncState,
            )
        }
        item(key = "wide-profile-sync") {
            WideProfileSyncCard(
                owner = owner,
                accountState = accountState,
                syncState = syncState,
                onSync = viewModel::retryCloudSync,
            )
        }
        item(key = "wide-profile-tabs") {
            WideProfileTabs(selected = selectedTab) { tabName = it.name }
        }

        if (!snapshot.loaded) {
            item(key = "wide-profile-loading") {
                WideProfileCard {
                    Text("正在读取统计", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "本页低频读取本地快照，不持续观察数据库。",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        } else {
            when (selectedTab) {
                WideProfileTab.Overview -> {
                    item(key = "wide-profile-heatmap") { WideProfileHeatmap(snapshot.dailyActivity) }
                    item(key = "wide-profile-runtime") { WideProfileRuntime(snapshot) }
                    item(key = "wide-profile-task-title") { WideSectionTitle("最近任务", "本机任务摘要") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "wide-profile-empty") { WideEmptyCard("还没有智能体任务记录") }
                    } else {
                        items(snapshot.recentTasks.take(5), key = { "wide-overview-${it.taskId}" }) { task ->
                            WideTaskRow(task)
                        }
                    }
                }
                WideProfileTab.Tokens -> {
                    item(key = "wide-profile-token-summary") { WideTokenRows(snapshot) }
                    item(key = "wide-profile-token-heatmap") { WideProfileHeatmap(snapshot.dailyActivity) }
                    item(key = "wide-profile-model-title") { WideSectionTitle("模型使用", "按累计 Token 排序") }
                    if (snapshot.modelUsage.isEmpty()) {
                        item(key = "wide-profile-model-empty") { WideEmptyCard("暂无模型使用数据") }
                    } else {
                        items(snapshot.modelUsage.take(12), key = { "wide-model-${it.modelId}" }) { model ->
                            WideModelRow(model)
                        }
                    }
                }
                WideProfileTab.Tasks -> {
                    item(key = "wide-profile-task-summary") { WideTaskRows(snapshot) }
                    item(key = "wide-profile-task-list-title") { WideSectionTitle("任务记录", "最多显示最近 100 条") }
                    if (snapshot.recentTasks.isEmpty()) {
                        item(key = "wide-profile-task-empty") { WideEmptyCard("暂无任务记录") }
                    } else {
                        items(snapshot.recentTasks, key = { "wide-task-${it.taskId}" }) { task ->
                            WideTaskRow(task)
                        }
                    }
                }
                WideProfileTab.Capabilities -> {
                    item(key = "wide-profile-skill") { WideSkillCard(skills) }
                    item(key = "wide-profile-cap-title") { WideSectionTitle("能力使用", "工具、功能、动作与应用") }
                    if (snapshot.capabilityUsage.isEmpty()) {
                        item(key = "wide-profile-cap-empty") { WideEmptyCard("暂无能力使用数据") }
                    } else {
                        items(snapshot.capabilityUsage.take(40), key = { "wide-cap-${it.kind}-${it.key}" }) { capability ->
                            WideCapabilityRow(capability)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WideProfileTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
private fun WideProfileIdentity(
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
    val handle = if (loggedIn) "@" + email.substringBefore('@').trim().take(22).ifBlank { "account" } else "@local"
    val badge = if (loggedIn) "账号空间" else "仅本机"
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            displayName,
            color = Color.White,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(handle, color = Color.White.copy(alpha = 0.46f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(wideSyncTone(syncState.phase).copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(badge, color = wideSyncTone(syncState.phase), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.065f), RoundedCornerShape(24.dp))
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        metrics.forEachIndexed { index, metric ->
            if (index > 0) Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.07f)))
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(metric.second, color = Color.White.copy(alpha = 0.94f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(metric.first, color = Color.White.copy(alpha = 0.44f), fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(metric.third, color = Color.White.copy(alpha = 0.23f), fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WideProfileSyncCard(
    owner: AgentAnalyticsOwner,
    accountState: SupabaseAccountState,
    syncState: AgentAnalyticsSyncUiState,
    onSync: () -> Unit,
) {
    val loggedIn = accountState.isLoggedIn && !owner.isGuest
    val phase = if (accountState.loading) AgentAnalyticsSyncPhase.Checking else syncState.phase
    val syncing = phase == AgentAnalyticsSyncPhase.Syncing
    WideProfileCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(wideSyncLabel(phase), color = wideSyncTone(phase), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    when {
                        accountState.loading -> "正在确认账号状态，不会阻塞本地统计。"
                        loggedIn -> syncState.message
                        else -> "访客数据只保存在当前设备，登录后会切换到独立账号空间。"
                    },
                    color = Color.White.copy(alpha = 0.48f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
                if (loggedIn && syncState.lastSyncedAtMillis > 0L) {
                    Text("上次同步 ${wideSyncTime(syncState.lastSyncedAtMillis)}", color = Color.White.copy(alpha = 0.29f), fontSize = 8.5.sp)
                }
            }
            if (loggedIn) {
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (syncing) Color.White.copy(alpha = 0.045f) else WideProfileBlue.copy(alpha = 0.13f))
                        .border(1.dp, WideProfileBlue.copy(alpha = if (syncing) 0.08f else 0.20f), RoundedCornerShape(14.dp))
                        .clickable(enabled = !syncing, onClick = onSync),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (syncing) "同步中" else "立即同步", color = if (syncing) Color.White.copy(alpha = 0.35f) else WideProfileBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun WideProfileTabs(selected: WideProfileTab, onSelected: (WideProfileTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(22.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WideProfileTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (active) WideProfileViolet.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.025f))
                    .border(1.dp, if (active) WideProfileViolet.copy(alpha = 0.28f) else Color.Transparent, RoundedCornerShape(17.dp))
                    .clickable(enabled = !active) { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    color = Color.White.copy(alpha = if (active) 0.94f else 0.50f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WideProfileHeatmap(daily: List<AgentDailyActivity>) {
    val heatmap = remember(daily) { wideBuildHeatmap(daily, 14) }
    WideProfileCard {
        Text("Token 活动", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "${heatmap.weeks} 周 · ${heatmap.activeDays} 个活跃日 · 每格一天",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(13.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = heatmap.weeks.coerceAtLeast(1)
            val rows = 7
            val gap = 5.dp
            val cellHeight = 12.dp
            val chartHeight = cellHeight * rows + gap * (rows - 1)
            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                val gapPx = gap.toPx()
                val cellWidth = ((size.width - gapPx * (columns - 1)) / columns).coerceAtLeast(1f)
                val cellHeightPx = cellHeight.toPx()
                val max = heatmap.maxTokens.coerceAtLeast(1L).toFloat()
                heatmap.cells.forEach { entry ->
                    val ratio = (entry.tokens.toFloat() / max).coerceIn(0f, 1f)
                    val color = when {
                        entry.future -> Color.White.copy(alpha = 0.02f)
                        entry.tokens <= 0L -> Color.White.copy(alpha = 0.055f)
                        ratio < 0.20f -> WideProfileBlue.copy(alpha = 0.28f)
                        ratio < 0.45f -> WideProfileBlue.copy(alpha = 0.52f)
                        ratio < 0.72f -> WideProfileViolet.copy(alpha = 0.72f)
                        else -> WideProfileMint.copy(alpha = 0.92f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(entry.column * (cellWidth + gapPx), entry.row * (cellHeightPx + gapPx)),
                        size = Size(cellWidth, cellHeightPx),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun WideProfileRuntime(snapshot: AgentAnalyticsSnapshot) {
    WideStatsRows(
        title = "运行概览",
        subtitle = "本机明细与账号每日聚合",
        rows = listOf(
            Triple("累计 Token", wideNumber(snapshot.totals.totalTokens), WideProfileBlue),
            Triple("模型调用", wideNumber(snapshot.totals.modelCalls), WideProfileViolet),
            Triple("规划轮次", wideNumber(snapshot.totals.agentModelTurns), WideProfileMint),
            Triple("完成任务", snapshot.totals.completedTasks.toString(), WideProfileWarm),
            Triple("执行动作", wideNumber(snapshot.totals.executedActions), WideProfileBlue),
            Triple("累计任务时长", wideDuration(snapshot.totals.totalTaskDurationMs), WideProfileViolet),
        ),
    )
}

@Composable
private fun WideTokenRows(snapshot: AgentAnalyticsSnapshot) {
    WideStatsRows(
        title = "Token 构成",
        subtitle = "真实 usage 与本地估算分开显示",
        rows = listOf(
            Triple("Provider 真实", wideNumber(snapshot.totals.providerTokens), WideProfileMint),
            Triple("Estimated 估算", wideNumber(snapshot.totals.estimatedTokens), WideProfileViolet),
            Triple("峰值日", wideNumber(snapshot.totals.peakDailyTokens), WideProfileWarm),
            Triple("累计总量", wideNumber(snapshot.totals.totalTokens), WideProfileBlue),
        ),
    )
}

@Composable
private fun WideTaskRows(snapshot: AgentAnalyticsSnapshot) {
    WideStatsRows(
        title = "任务效率",
        subtitle = "成功率、自主性与耗时",
        rows = listOf(
            Triple("任务成功率", widePercent(snapshot.totals.taskSuccessRate), WideProfileMint),
            Triple("自主完成率", widePercent(snapshot.totals.autonomousCompletionRate), WideProfileBlue),
            Triple("完成任务", snapshot.totals.completedTasks.toString(), WideProfileViolet),
            Triple("介入后完成", snapshot.totals.assistedCompletedTasks.toString(), WideProfileWarm),
            Triple("累计耗时", wideDuration(snapshot.totals.totalTaskDurationMs), WideProfileBlue),
        ),
    )
}

@Composable
private fun WideSkillCard(skills: AgentSkillInventory) {
    WideStatsRows(
        title = "Skill 资产",
        subtitle = "操作学习形成的本机可复用能力",
        rows = listOf(
            Triple("全部 Skill", skills.totalSkills.toString(), WideProfileBlue),
            Triple("可用", skills.usableSkills.toString(), WideProfileMint),
            Triple("待审核", skills.reviewSkills.toString(), WideProfileWarm),
            Triple("覆盖应用", skills.scopedApps.toString(), WideProfileViolet),
            Triple("运行次数", skills.totalRuns.toString(), WideProfileBlue),
            Triple("成功运行", skills.successfulRuns.toString(), WideProfileMint),
        ),
    )
}

@Composable
private fun WideStatsRows(title: String, subtitle: String, rows: List<Triple<String, String, Color>>) {
    WideProfileCard {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp)
        Spacer(Modifier.height(9.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.first, color = Color.White.copy(alpha = 0.46f), fontSize = 10.5.sp)
                Text(row.second, color = row.third, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun WideTaskRow(task: AgentTaskAnalytics) {
    WideProfileCard {
        Text(
            task.goal.ifBlank { "未命名智能体任务" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        WideInline("状态", wideTaskStatus(task.status), wideTaskTone(task.status))
        WideInline("耗时", wideDuration(task.durationMs), WideProfileBlue)
        WideInline("动作", task.executedActions.toString(), WideProfileMint)
        WideInline("Token", wideNumber(task.totalTokens), WideProfileViolet)
    }
}

@Composable
private fun WideModelRow(model: AgentModelAnalytics) {
    val successRate = if (model.calls > 0L) (model.calls - model.failures).coerceAtLeast(0L).toFloat() / model.calls.toFloat() else 0f
    WideProfileCard {
        Text(model.displayName, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        WideInline("调用次数", model.calls.toString(), WideProfileBlue)
        WideInline("累计 Token", wideNumber(model.totalTokens), WideProfileViolet)
        WideInline("调用成功率", widePercent(successRate), WideProfileMint)
    }
}

@Composable
private fun WideCapabilityRow(capability: AgentCapabilityAnalytics) {
    val resultCount = wideSafeAdd(capability.successes, capability.failures)
    val rate = if (resultCount > 0L) capability.successes.toFloat() / resultCount.toFloat() else 0f
    WideProfileCard {
        Text(capability.displayName, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        WideInline("类型", wideCapabilityKind(capability.kind), WideProfileViolet)
        WideInline("使用次数", capability.uses.toString(), WideProfileBlue)
        WideInline("成功率", if (resultCount > 0L) widePercent(rate) else "累计", WideProfileMint)
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
private fun WideProfileCard(content: @Composable Column.() -> Unit) {
    val shape = RoundedCornerShape(23.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(shape)
            .background(WideProfileCard)
            .border(1.dp, Color.White.copy(alpha = 0.065f), shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@Composable
private fun WideSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color.White.copy(alpha = 0.40f), fontSize = 10.5.sp)
    }
}

@Composable
private fun WideEmptyCard(message: String) {
    WideProfileCard { Text(message, color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp) }
}

private fun wideBuildHeatmap(daily: List<AgentDailyActivity>, requestedWeeks: Int): WideHeatmap {
    val weeks = requestedWeeks.coerceIn(1, 52)
    val today = LocalDate.now(ZoneId.systemDefault())
    val endSunday = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val startMonday = endSunday.minusDays(weeks * 7L - 1L)
    val values = daily.associate { it.dateKey to it.totalTokens.coerceAtLeast(0L) }
    val cells = ArrayList<WideHeatCell>(weeks * 7)
    var maxTokens = 0L
    var activeDays = 0
    repeat(weeks * 7) { index ->
        val date = startMonday.plusDays(index.toLong())
        val value = values[date.toString()] ?: 0L
        val future = date.isAfter(today)
        if (!future) {
            maxTokens = maxOf(maxTokens, value)
            if (value > 0L) activeDays += 1
        }
        cells += WideHeatCell(index / 7, index % 7, value, future)
    }
    return WideHeatmap(weeks, cells, maxTokens, activeDays)
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
    AgentAnalyticsSyncPhase.Synced -> WideProfileMint
    AgentAnalyticsSyncPhase.Cached -> WideProfileBlue
    AgentAnalyticsSyncPhase.Syncing,
    AgentAnalyticsSyncPhase.Checking -> WideProfileViolet
    AgentAnalyticsSyncPhase.Guest,
    AgentAnalyticsSyncPhase.LocalOnly -> WideProfileWarm
    AgentAnalyticsSyncPhase.Failed -> WideProfileDanger
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
    "completed" -> WideProfileMint
    "running" -> WideProfileBlue
    "paused", "interrupted" -> WideProfileWarm
    else -> WideProfileDanger
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
