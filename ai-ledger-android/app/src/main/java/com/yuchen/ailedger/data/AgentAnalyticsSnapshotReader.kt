package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentAnalyticsTotals
import com.yuchen.ailedger.model.AgentDailyActivity
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计详情页的单次安全快照读取器。
 *
 * 不订阅 Room Flow，也不创建页面级常驻数据库协程。页面每次打开只执行五个有索引或小表查询，
 * 读取完成后立即返回不可变快照，避免后台 stateIn 的异常越过页面层。
 */
internal object AgentAnalyticsSnapshotReader {
    private const val RECENT_TASK_LIMIT = 100

    suspend fun load(context: Context, owner: AgentAnalyticsOwner): AgentAnalyticsSnapshot {
        val appContext = context.applicationContext
        AgentAnalyticsDatabase.validate(appContext, owner.databaseName)
        val dao = AgentAnalyticsDatabase.get(appContext, owner.databaseName).analyticsDao()

        val daily = dao.getAllDailyActivity().map(AgentDailyActivityEntity::toModel)
        val tasks = dao.getRecentTasks(RECENT_TASK_LIMIT).map(AgentTaskAnalyticsEntity::toModel)
        val models = dao.getAllModelUsage().map(AgentModelUsageEntity::toModel)
        val capabilities = dao.getAllCapabilityUsage().map(AgentCapabilityUsageEntity::toModel)
        val longestTaskDurationMs = dao.getLongestTaskDurationMs().coerceAtLeast(0L)

        val completed = daily.safeSum(AgentDailyActivity::completedTasks)
        val autonomous = daily.safeSum(AgentDailyActivity::autonomousCompletedTasks)
        val assisted = daily.safeSum(AgentDailyActivity::assistedCompletedTasks)
        val terminalTasks = daily.safeSum { day ->
            safeAdd(
                safeAdd(day.completedTasks, day.failedTasks),
                safeAdd(
                    safeAdd(day.pausedTasks, day.cancelledTasks),
                    day.budgetExceededTasks,
                ),
            )
        }
        val streaks = calculateStreaks(daily)

        return AgentAnalyticsSnapshot(
            dailyActivity = daily,
            recentTasks = tasks,
            modelUsage = models,
            capabilityUsage = capabilities,
            totals = AgentAnalyticsTotals(
                totalTokens = daily.safeSum(AgentDailyActivity::totalTokens),
                providerTokens = daily.safeSum(AgentDailyActivity::providerTokens),
                estimatedTokens = daily.safeSum(AgentDailyActivity::estimatedTokens),
                peakDailyTokens = daily.maxOfOrNull(AgentDailyActivity::totalTokens)?.coerceAtLeast(0L) ?: 0L,
                chatCalls = daily.safeSum(AgentDailyActivity::chatCalls),
                agentTasks = daily.safeSum(AgentDailyActivity::agentTasks),
                completedTasks = completed,
                autonomousCompletedTasks = autonomous,
                assistedCompletedTasks = assisted,
                taskSuccessRate = if (terminalTasks > 0L) {
                    completed.toFloat() / terminalTasks.toFloat()
                } else {
                    0f
                },
                autonomousCompletionRate = if (completed > 0L) {
                    autonomous.toFloat() / completed.toFloat()
                } else {
                    0f
                },
                executedActions = daily.safeSum(AgentDailyActivity::executedActions),
                agentModelTurns = daily.safeSum(AgentDailyActivity::agentModelTurns),
                modelCalls = daily.safeSum(AgentDailyActivity::modelCalls),
                totalTaskDurationMs = daily.safeSum(AgentDailyActivity::taskDurationMs),
                longestTaskDurationMs = longestTaskDurationMs,
                currentActiveStreakDays = streaks.first,
                longestActiveStreakDays = streaks.second,
            ),
            loaded = true,
        )
    }

    private fun calculateStreaks(daily: List<AgentDailyActivity>): Pair<Int, Int> {
        val activeDates = daily.asSequence()
            .filter {
                it.totalTokens > 0L ||
                    it.chatCalls > 0L ||
                    it.agentTasks > 0L ||
                    it.executedActions > 0L
            }
            .mapNotNull { runCatching { LocalDate.parse(it.dateKey) }.getOrNull() }
            .distinct()
            .sorted()
            .toList()
        if (activeDates.isEmpty()) return 0 to 0

        var longest = 1
        var running = 1
        for (index in 1 until activeDates.size) {
            running = if (activeDates[index - 1].plusDays(1L) == activeDates[index]) {
                running + 1
            } else {
                1
            }
            longest = maxOf(longest, running)
        }

        val today = LocalDate.now(ZoneId.systemDefault())
        val latest = activeDates.last()
        if (latest != today && latest != today.minusDays(1L)) return 0 to longest

        var current = 1
        for (index in activeDates.lastIndex downTo 1) {
            if (activeDates[index - 1].plusDays(1L) != activeDates[index]) break
            current += 1
        }
        return current to longest
    }

    private inline fun <T> List<T>.safeSum(selector: (T) -> Long): Long {
        var total = 0L
        forEach { total = safeAdd(total, selector(it)) }
        return total
    }

    private fun safeAdd(left: Long, right: Long): Long {
        val safeLeft = left.coerceAtLeast(0L)
        val safeRight = right.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
    }
}
