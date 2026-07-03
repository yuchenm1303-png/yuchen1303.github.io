package com.yuchen.ailedger.data

import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentAnalyticsTotals
import com.yuchen.ailedger.model.AgentDailyActivity
import java.time.LocalDate
import java.time.ZoneId

internal fun mergeAgentAnalyticsDaily(
    local: AgentAnalyticsSnapshot,
    otherDevicesDaily: List<AgentDailyActivity>,
): AgentAnalyticsSnapshot {
    if (!local.loaded || otherDevicesDaily.isEmpty()) return local
    val mergedByDate = LinkedHashMap<String, AgentDailyActivity>()
    local.dailyActivity.forEach { day -> mergedByDate[day.dateKey] = day }
    otherDevicesDaily.forEach { remote ->
        val existing = mergedByDate[remote.dateKey]
        mergedByDate[remote.dateKey] = if (existing == null) remote else existing.merge(remote)
    }
    val daily = mergedByDate.values.sortedBy(AgentDailyActivity::dateKey)
    val terminalTasks = daily.safeSum {
        safeAdd(
            safeAdd(it.completedTasks, it.failedTasks),
            safeAdd(safeAdd(it.pausedTasks, it.cancelledTasks), it.budgetExceededTasks),
        )
    }
    val completed = daily.safeSum(AgentDailyActivity::completedTasks)
    val autonomous = daily.safeSum(AgentDailyActivity::autonomousCompletedTasks)
    val assisted = daily.safeSum(AgentDailyActivity::assistedCompletedTasks)
    val streaks = calculateStreaks(daily)
    return local.copy(
        dailyActivity = daily,
        totals = AgentAnalyticsTotals(
            totalTokens = daily.safeSum(AgentDailyActivity::totalTokens),
            providerTokens = daily.safeSum(AgentDailyActivity::providerTokens),
            estimatedTokens = daily.safeSum(AgentDailyActivity::estimatedTokens),
            peakDailyTokens = daily.maxOfOrNull(AgentDailyActivity::totalTokens) ?: 0L,
            chatCalls = daily.safeSum(AgentDailyActivity::chatCalls),
            agentTasks = daily.safeSum(AgentDailyActivity::agentTasks),
            completedTasks = completed,
            autonomousCompletedTasks = autonomous,
            assistedCompletedTasks = assisted,
            taskSuccessRate = if (terminalTasks > 0L) completed.toFloat() / terminalTasks.toFloat() else 0f,
            autonomousCompletionRate = if (completed > 0L) autonomous.toFloat() / completed.toFloat() else 0f,
            executedActions = daily.safeSum(AgentDailyActivity::executedActions),
            agentModelTurns = daily.safeSum(AgentDailyActivity::agentModelTurns),
            modelCalls = daily.safeSum(AgentDailyActivity::modelCalls),
            totalTaskDurationMs = daily.safeSum(AgentDailyActivity::taskDurationMs),
            // 任务明细默认不上传，因此最长单任务仍只展示本机账号数据库中的真实值。
            longestTaskDurationMs = local.totals.longestTaskDurationMs,
            currentActiveStreakDays = streaks.first,
            longestActiveStreakDays = streaks.second,
        ),
    )
}

private fun AgentDailyActivity.merge(other: AgentDailyActivity): AgentDailyActivity = copy(
    firstActivityAtMillis = minPositive(firstActivityAtMillis, other.firstActivityAtMillis),
    lastActivityAtMillis = maxOf(lastActivityAtMillis, other.lastActivityAtMillis),
    chatCalls = safeAdd(chatCalls, other.chatCalls),
    chatFailures = safeAdd(chatFailures, other.chatFailures),
    agentTasks = safeAdd(agentTasks, other.agentTasks),
    completedTasks = safeAdd(completedTasks, other.completedTasks),
    autonomousCompletedTasks = safeAdd(autonomousCompletedTasks, other.autonomousCompletedTasks),
    assistedCompletedTasks = safeAdd(assistedCompletedTasks, other.assistedCompletedTasks),
    failedTasks = safeAdd(failedTasks, other.failedTasks),
    pausedTasks = safeAdd(pausedTasks, other.pausedTasks),
    cancelledTasks = safeAdd(cancelledTasks, other.cancelledTasks),
    budgetExceededTasks = safeAdd(budgetExceededTasks, other.budgetExceededTasks),
    modelCalls = safeAdd(modelCalls, other.modelCalls),
    modelFailures = safeAdd(modelFailures, other.modelFailures),
    agentModelTurns = safeAdd(agentModelTurns, other.agentModelTurns),
    inputTokens = safeAdd(inputTokens, other.inputTokens),
    outputTokens = safeAdd(outputTokens, other.outputTokens),
    reasoningTokens = safeAdd(reasoningTokens, other.reasoningTokens),
    cachedInputTokens = safeAdd(cachedInputTokens, other.cachedInputTokens),
    totalTokens = safeAdd(totalTokens, other.totalTokens),
    providerTokens = safeAdd(providerTokens, other.providerTokens),
    estimatedTokens = safeAdd(estimatedTokens, other.estimatedTokens),
    modelLatencyMs = safeAdd(modelLatencyMs, other.modelLatencyMs),
    requestBytes = safeAdd(requestBytes, other.requestBytes),
    responseBytes = safeAdd(responseBytes, other.responseBytes),
    taskDurationMs = safeAdd(taskDurationMs, other.taskDurationMs),
    executedActions = safeAdd(executedActions, other.executedActions),
    successfulActions = safeAdd(successfulActions, other.successfulActions),
    failedActions = safeAdd(failedActions, other.failedActions),
    observations = safeAdd(observations, other.observations),
    reobservations = safeAdd(reobservations, other.reobservations),
    rejectedPlans = safeAdd(rejectedPlans, other.rejectedPlans),
    executionFailures = safeAdd(executionFailures, other.executionFailures),
    confirmationRequests = safeAdd(confirmationRequests, other.confirmationRequests),
    confirmationsAccepted = safeAdd(confirmationsAccepted, other.confirmationsAccepted),
    userInputRequests = safeAdd(userInputRequests, other.userInputRequests),
    userInputsSubmitted = safeAdd(userInputsSubmitted, other.userInputsSubmitted),
    userTakeovers = safeAdd(userTakeovers, other.userTakeovers),
    takeoverResumes = safeAdd(takeoverResumes, other.takeoverResumes),
    webSearches = safeAdd(webSearches, other.webSearches),
    imageRequests = safeAdd(imageRequests, other.imageRequests),
)

private fun calculateStreaks(daily: List<AgentDailyActivity>): Pair<Int, Int> {
    val activeDates = daily.asSequence()
        .filter { it.totalTokens > 0L || it.chatCalls > 0L || it.agentTasks > 0L || it.executedActions > 0L }
        .mapNotNull { runCatching { LocalDate.parse(it.dateKey) }.getOrNull() }
        .distinct()
        .sorted()
        .toList()
    if (activeDates.isEmpty()) return 0 to 0
    var longest = 1
    var running = 1
    for (index in 1 until activeDates.size) {
        running = if (activeDates[index - 1].plusDays(1L) == activeDates[index]) running + 1 else 1
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

private fun minPositive(left: Long, right: Long): Long = when {
    left <= 0L -> right.coerceAtLeast(0L)
    right <= 0L -> left.coerceAtLeast(0L)
    else -> minOf(left, right)
}

private fun safeAdd(left: Long, right: Long): Long {
    val safeLeft = left.coerceAtLeast(0L)
    val safeRight = right.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - safeLeft < safeRight) Long.MAX_VALUE else safeLeft + safeRight
}
