package com.yuchen.ailedger.data

import android.content.Context
import com.yuchen.ailedger.model.AgentAnalyticsSnapshot
import com.yuchen.ailedger.model.AgentAnalyticsTotals
import com.yuchen.ailedger.model.AgentCapabilityAnalytics
import com.yuchen.ailedger.model.AgentDailyActivity
import com.yuchen.ailedger.model.AgentModelAnalytics
import com.yuchen.ailedger.model.AgentTaskAnalytics
import com.yuchen.ailedger.model.AgentTokenAccuracy
import com.yuchen.ailedger.model.AgentTokenUsage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class AgentModelCallWrite(
    val taskId: Long? = null,
    val source: String,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val modelId: String,
    val modelLabel: String,
    val success: Boolean,
    val usage: AgentTokenUsage,
    val latencyMs: Long,
    val requestBytes: Long,
    val responseBytes: Long,
)

internal data class AgentChatCallWrite(
    val modelCall: AgentModelCallWrite,
    val webSearchUsed: Boolean,
    val imageRequest: Boolean,
    val toolKeys: List<Pair<String, String>> = emptyList(),
)

internal data class AgentUsageCounter(
    val displayName: String,
    val uses: Long,
    val successes: Long,
    val failures: Long,
)

internal data class AgentTaskWrite(
    val taskId: Long,
    val goal: String,
    val status: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val latestResult: String,
    val modelCalls: Long,
    val modelFailures: Long,
    val modelTurns: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cachedInputTokens: Long,
    val totalTokens: Long,
    val providerTokens: Long,
    val estimatedTokens: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val modelLatencyMs: Long,
    val executedActions: Long,
    val successfulActions: Long,
    val failedActions: Long,
    val observations: Long,
    val reobservations: Long,
    val rejectedPlans: Long,
    val executionFailures: Long,
    val confirmationRequests: Long,
    val confirmationsAccepted: Long,
    val userInputRequests: Long,
    val userInputsSubmitted: Long,
    val userTakeovers: Long,
    val takeoverResumes: Long,
    val appUsage: Map<String, AgentUsageCounter>,
    val actionUsage: Map<String, AgentUsageCounter>,
)

internal data class AgentTaskMetricsPatch(
    val modelTurns: Long,
    val executedActions: Long,
    val observations: Long,
    val reobservations: Long,
    val rejectedPlans: Long,
    val executionFailures: Long,
)

class AgentAnalyticsRepository private constructor(context: Context) {
    private val dao = AgentAnalyticsDatabase.get(context).analyticsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val mutableState = MutableStateFlow(AgentAnalyticsSnapshot())
    val state: StateFlow<AgentAnalyticsSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching {
                writeMutex.withLock {
                    val now = System.currentTimeMillis()
                    dao.markInterruptedTasks(now)
                    dao.deleteOldTokenEvents(now - TOKEN_EVENT_RETENTION_MS)
                    dao.pruneTasks(MAX_TASK_HISTORY)
                    dao.pruneDailyActivity(dateKey(now - DAILY_RETENTION_MS))
                }
            }
        }
        scope.launch {
            combine(
                dao.observeDailyActivity(),
                dao.observeRecentTasks(RECENT_TASK_STATE_LIMIT),
                dao.observeModelUsage(),
                dao.observeCapabilityUsage(),
            ) { daily, tasks, models, capabilities ->
                buildSnapshot(daily, tasks, models, capabilities)
            }.collect { snapshot ->
                mutableState.value = snapshot
            }
        }
    }

    internal fun beginTask(taskId: Long, goal: String, startedAtMillis: Long) {
        if (taskId <= 0L) return
        // 先在调用线程立即取得写锁，再转入 IO 挂起；终态写入不能越过任务开始写入。
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeMutex.withLock {
                if (dao.getTask(taskId) != null) return@withLock
                dao.upsertTask(
                    AgentTaskAnalyticsEntity(
                        taskId = taskId,
                        goal = goal.trim().take(MAX_GOAL_CHARS),
                        status = "running",
                        startedAtMillis = startedAtMillis,
                    ),
                )
                updateDayLocked(startedAtMillis) { day ->
                    day.copy(agentTasks = day.agentTasks + 1L)
                }
            }
        }
    }

    internal fun recordChatCall(write: AgentChatCallWrite) {
        scope.launch {
            writeMutex.withLock {
                recordModelCallLocked(write.modelCall)
                val at = write.modelCall.occurredAtMillis
                updateDayLocked(at) { day ->
                    day.copy(
                        // 成功请求代表一轮实际对话；失败的备用端点尝试只进入失败与模型调用统计。
                        chatCalls = day.chatCalls + if (write.modelCall.success) 1L else 0L,
                        chatFailures = day.chatFailures + if (write.modelCall.success) 0L else 1L,
                        webSearches = day.webSearches + if (write.webSearchUsed) 1L else 0L,
                        imageRequests = day.imageRequests + if (write.imageRequest) 1L else 0L,
                    )
                }
                if (write.webSearchUsed) {
                    updateCapabilityLocked("feature", "web_search", "联网搜索", write.modelCall.success, at)
                }
                if (write.imageRequest) {
                    updateCapabilityLocked("feature", "image_understanding", "视觉理解", write.modelCall.success, at)
                }
                write.toolKeys.distinct().forEach { (key, label) ->
                    updateCapabilityLocked("tool", key, label, write.modelCall.success, at)
                }
            }
        }
    }

    internal fun recordModelCall(write: AgentModelCallWrite) {
        scope.launch {
            writeMutex.withLock { recordModelCallLocked(write) }
        }
    }

    internal fun finishTask(write: AgentTaskWrite) {
        if (write.taskId <= 0L) return
        scope.launch {
            writeMutex.withLock {
                val existing = dao.getTask(write.taskId)
                    ?: AgentTaskAnalyticsEntity(
                        taskId = write.taskId,
                        goal = write.goal.take(MAX_GOAL_CHARS),
                        status = "running",
                        startedAtMillis = write.startedAtMillis,
                    )
                val merged = existing.merge(write)
                val firstTerminalWrite = existing.endedAtMillis == null
                dao.upsertTask(merged)
                updateDayLocked(write.endedAtMillis) { day ->
                    val outcome = if (firstTerminalWrite) outcomeDelta(write.status) else OutcomeDelta()
                    day.copy(
                        completedTasks = day.completedTasks + outcome.completed,
                        failedTasks = day.failedTasks + outcome.failed,
                        pausedTasks = day.pausedTasks + outcome.paused,
                        cancelledTasks = day.cancelledTasks + outcome.cancelled,
                        budgetExceededTasks = day.budgetExceededTasks + outcome.budgetExceeded,
                        taskDurationMs = day.taskDurationMs + positiveDelta(merged.durationMs, existing.durationMs),
                        executedActions = day.executedActions + positiveDelta(merged.executedActions, existing.executedActions),
                        successfulActions = day.successfulActions + positiveDelta(merged.successfulActions, existing.successfulActions),
                        failedActions = day.failedActions + positiveDelta(merged.failedActions, existing.failedActions),
                        observations = day.observations + positiveDelta(merged.observations, existing.observations),
                        reobservations = day.reobservations + positiveDelta(merged.reobservations, existing.reobservations),
                        rejectedPlans = day.rejectedPlans + positiveDelta(merged.rejectedPlans, existing.rejectedPlans),
                        executionFailures = day.executionFailures + positiveDelta(merged.executionFailures, existing.executionFailures),
                        confirmationRequests = day.confirmationRequests + positiveDelta(merged.confirmationRequests, existing.confirmationRequests),
                        confirmationsAccepted = day.confirmationsAccepted + positiveDelta(merged.confirmationsAccepted, existing.confirmationsAccepted),
                        userInputRequests = day.userInputRequests + positiveDelta(merged.userInputRequests, existing.userInputRequests),
                        userInputsSubmitted = day.userInputsSubmitted + positiveDelta(merged.userInputsSubmitted, existing.userInputsSubmitted),
                        userTakeovers = day.userTakeovers + positiveDelta(merged.userTakeovers, existing.userTakeovers),
                        takeoverResumes = day.takeoverResumes + positiveDelta(merged.takeoverResumes, existing.takeoverResumes),
                    )
                }
                if (firstTerminalWrite) {
                    write.actionUsage.forEach { (key, counter) ->
                        updateCapabilityLocked(
                            kind = "action",
                            key = key,
                            displayName = counter.displayName,
                            uses = counter.uses,
                            successes = counter.successes,
                            failures = counter.failures,
                            atMillis = write.endedAtMillis,
                        )
                    }
                    write.appUsage.forEach { (key, counter) ->
                        updateCapabilityLocked(
                            kind = "app",
                            key = key,
                            displayName = counter.displayName,
                            uses = counter.uses,
                            successes = counter.successes,
                            failures = counter.failures,
                            atMillis = write.endedAtMillis,
                        )
                    }
                }
                dao.pruneTasks(MAX_TASK_HISTORY)
            }
        }
    }

    internal fun patchTaskMetrics(taskId: Long, patch: AgentTaskMetricsPatch) {
        if (taskId <= 0L) return
        scope.launch {
            writeMutex.withLock {
                val existing = dao.getTask(taskId) ?: return@withLock
                val updated = existing.copy(
                    modelTurns = maxOf(existing.modelTurns, patch.modelTurns),
                    executedActions = maxOf(existing.executedActions, patch.executedActions),
                    observations = maxOf(existing.observations, patch.observations),
                    reobservations = maxOf(existing.reobservations, patch.reobservations),
                    rejectedPlans = maxOf(existing.rejectedPlans, patch.rejectedPlans),
                    executionFailures = maxOf(existing.executionFailures, patch.executionFailures),
                )
                if (updated == existing) return@withLock
                dao.upsertTask(updated)
                val at = updated.endedAtMillis ?: System.currentTimeMillis()
                updateDayLocked(at) { day ->
                    day.copy(
                        executedActions = day.executedActions + positiveDelta(updated.executedActions, existing.executedActions),
                        observations = day.observations + positiveDelta(updated.observations, existing.observations),
                        reobservations = day.reobservations + positiveDelta(updated.reobservations, existing.reobservations),
                        rejectedPlans = day.rejectedPlans + positiveDelta(updated.rejectedPlans, existing.rejectedPlans),
                        executionFailures = day.executionFailures + positiveDelta(updated.executionFailures, existing.executionFailures),
                    )
                }
            }
        }
    }

    private suspend fun recordModelCallLocked(write: AgentModelCallWrite) {
        val normalizedModelId = write.modelId.trim().ifBlank { "unknown" }.take(MAX_KEY_CHARS)
        val normalizedLabel = write.modelLabel.trim().ifBlank { normalizedModelId }.take(MAX_LABEL_CHARS)
        val usage = write.usage
        val total = usage.normalizedTotal.coerceAtLeast(0L)
        val provider = if (usage.accuracy == AgentTokenAccuracy.Provider) total else 0L
        val estimated = if (usage.accuracy == AgentTokenAccuracy.Estimated) total else 0L
        dao.insertTokenEvent(
            AgentTokenEventEntity(
                taskId = write.taskId?.takeIf { it > 0L },
                source = write.source.take(MAX_KEY_CHARS),
                occurredAtMillis = write.occurredAtMillis,
                modelId = normalizedModelId,
                modelLabel = normalizedLabel,
                success = write.success,
                inputTokens = usage.inputTokens.coerceAtLeast(0L),
                outputTokens = usage.outputTokens.coerceAtLeast(0L),
                reasoningTokens = usage.reasoningTokens.coerceAtLeast(0L),
                cachedInputTokens = usage.cachedInputTokens.coerceAtLeast(0L),
                totalTokens = total,
                accuracy = usage.accuracy.name,
                latencyMs = write.latencyMs.coerceAtLeast(0L),
                requestBytes = write.requestBytes.coerceAtLeast(0L),
                responseBytes = write.responseBytes.coerceAtLeast(0L),
            ),
        )
        updateDayLocked(write.occurredAtMillis) { day ->
            day.copy(
                modelCalls = day.modelCalls + 1L,
                modelFailures = day.modelFailures + if (write.success) 0L else 1L,
                inputTokens = day.inputTokens + usage.inputTokens.coerceAtLeast(0L),
                outputTokens = day.outputTokens + usage.outputTokens.coerceAtLeast(0L),
                reasoningTokens = day.reasoningTokens + usage.reasoningTokens.coerceAtLeast(0L),
                cachedInputTokens = day.cachedInputTokens + usage.cachedInputTokens.coerceAtLeast(0L),
                totalTokens = day.totalTokens + total,
                providerTokens = day.providerTokens + provider,
                estimatedTokens = day.estimatedTokens + estimated,
                modelLatencyMs = day.modelLatencyMs + write.latencyMs.coerceAtLeast(0L),
                requestBytes = day.requestBytes + write.requestBytes.coerceAtLeast(0L),
                responseBytes = day.responseBytes + write.responseBytes.coerceAtLeast(0L),
            )
        }
        val oldModel = dao.getModelUsage(normalizedModelId)
        val firstUsed = oldModel?.firstUsedAtMillis?.takeIf { it > 0L } ?: write.occurredAtMillis
        dao.upsertModelUsage(
            (oldModel ?: AgentModelUsageEntity(normalizedModelId, normalizedLabel)).copy(
                displayName = normalizedLabel,
                calls = (oldModel?.calls ?: 0L) + 1L,
                failures = (oldModel?.failures ?: 0L) + if (write.success) 0L else 1L,
                inputTokens = (oldModel?.inputTokens ?: 0L) + usage.inputTokens.coerceAtLeast(0L),
                outputTokens = (oldModel?.outputTokens ?: 0L) + usage.outputTokens.coerceAtLeast(0L),
                reasoningTokens = (oldModel?.reasoningTokens ?: 0L) + usage.reasoningTokens.coerceAtLeast(0L),
                cachedInputTokens = (oldModel?.cachedInputTokens ?: 0L) + usage.cachedInputTokens.coerceAtLeast(0L),
                totalTokens = (oldModel?.totalTokens ?: 0L) + total,
                providerTokens = (oldModel?.providerTokens ?: 0L) + provider,
                estimatedTokens = (oldModel?.estimatedTokens ?: 0L) + estimated,
                latencyMs = (oldModel?.latencyMs ?: 0L) + write.latencyMs.coerceAtLeast(0L),
                requestBytes = (oldModel?.requestBytes ?: 0L) + write.requestBytes.coerceAtLeast(0L),
                responseBytes = (oldModel?.responseBytes ?: 0L) + write.responseBytes.coerceAtLeast(0L),
                firstUsedAtMillis = firstUsed,
                lastUsedAtMillis = maxOf(oldModel?.lastUsedAtMillis ?: 0L, write.occurredAtMillis),
            ),
        )
    }

    private suspend fun updateDayLocked(
        atMillis: Long,
        transform: (AgentDailyActivityEntity) -> AgentDailyActivityEntity,
    ) {
        val key = dateKey(atMillis)
        val old = dao.getDailyActivity(key) ?: AgentDailyActivityEntity(dateKey = key)
        val transformed = transform(old)
        dao.upsertDailyActivity(
            transformed.copy(
                firstActivityAtMillis = old.firstActivityAtMillis.takeIf { it > 0L }
                    ?.let { minOf(it, atMillis) }
                    ?: atMillis,
                lastActivityAtMillis = maxOf(old.lastActivityAtMillis, atMillis),
            ),
        )
    }

    private suspend fun updateCapabilityLocked(
        kind: String,
        key: String,
        displayName: String,
        success: Boolean,
        atMillis: Long,
    ) {
        updateCapabilityLocked(
            kind = kind,
            key = key,
            displayName = displayName,
            uses = 1L,
            successes = if (success) 1L else 0L,
            failures = if (success) 0L else 1L,
            atMillis = atMillis,
        )
    }

    private suspend fun updateCapabilityLocked(
        kind: String,
        key: String,
        displayName: String,
        uses: Long,
        successes: Long,
        failures: Long,
        atMillis: Long,
    ) {
        if (uses <= 0L) return
        val normalizedKind = kind.trim().ifBlank { "feature" }.take(MAX_KEY_CHARS)
        val normalizedKey = key.trim().ifBlank { "unknown" }.take(MAX_KEY_CHARS)
        val normalizedLabel = displayName.trim().ifBlank { normalizedKey }.take(MAX_LABEL_CHARS)
        val old = dao.getCapabilityUsage(normalizedKind, normalizedKey)
        dao.upsertCapabilityUsage(
            (old ?: AgentCapabilityUsageEntity(normalizedKind, normalizedKey, normalizedLabel)).copy(
                displayName = normalizedLabel,
                uses = (old?.uses ?: 0L) + uses.coerceAtLeast(0L),
                successes = (old?.successes ?: 0L) + successes.coerceAtLeast(0L),
                failures = (old?.failures ?: 0L) + failures.coerceAtLeast(0L),
                firstUsedAtMillis = old?.firstUsedAtMillis?.takeIf { it > 0L } ?: atMillis,
                lastUsedAtMillis = maxOf(old?.lastUsedAtMillis ?: 0L, atMillis),
            ),
        )
    }

    private fun buildSnapshot(
        dailyEntities: List<AgentDailyActivityEntity>,
        taskEntities: List<AgentTaskAnalyticsEntity>,
        modelEntities: List<AgentModelUsageEntity>,
        capabilityEntities: List<AgentCapabilityUsageEntity>,
    ): AgentAnalyticsSnapshot {
        val daily = dailyEntities.map { it.toModel() }
        val tasks = taskEntities.map { it.toModel() }
        val models = modelEntities.map { it.toModel() }
        val capabilities = capabilityEntities.map { it.toModel() }
        val terminalTasks = daily.sumOf {
            it.completedTasks + it.failedTasks + it.pausedTasks + it.cancelledTasks + it.budgetExceededTasks
        }
        val completed = daily.sumOf(AgentDailyActivity::completedTasks)
        val streaks = calculateStreaks(daily)
        return AgentAnalyticsSnapshot(
            dailyActivity = daily,
            recentTasks = tasks,
            modelUsage = models,
            capabilityUsage = capabilities,
            totals = AgentAnalyticsTotals(
                totalTokens = daily.sumOf(AgentDailyActivity::totalTokens),
                providerTokens = daily.sumOf(AgentDailyActivity::providerTokens),
                estimatedTokens = daily.sumOf(AgentDailyActivity::estimatedTokens),
                peakDailyTokens = daily.maxOfOrNull(AgentDailyActivity::totalTokens) ?: 0L,
                chatCalls = daily.sumOf(AgentDailyActivity::chatCalls),
                agentTasks = daily.sumOf(AgentDailyActivity::agentTasks),
                completedTasks = completed,
                taskSuccessRate = if (terminalTasks > 0L) completed.toFloat() / terminalTasks.toFloat() else 0f,
                executedActions = daily.sumOf(AgentDailyActivity::executedActions),
                modelCalls = daily.sumOf(AgentDailyActivity::modelCalls),
                totalTaskDurationMs = daily.sumOf(AgentDailyActivity::taskDurationMs),
                longestTaskDurationMs = tasks.maxOfOrNull(AgentTaskAnalytics::durationMs) ?: 0L,
                currentActiveStreakDays = streaks.first,
                longestActiveStreakDays = streaks.second,
            ),
            loaded = true,
        )
    }

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

    private fun AgentTaskAnalyticsEntity.merge(write: AgentTaskWrite): AgentTaskAnalyticsEntity {
        val end = maxOf(endedAtMillis ?: 0L, write.endedAtMillis).takeIf { it > 0L }
        return copy(
            goal = goal.ifBlank { write.goal.take(MAX_GOAL_CHARS) },
            status = write.status,
            startedAtMillis = startedAtMillis.takeIf { it > 0L } ?: write.startedAtMillis,
            endedAtMillis = end,
            durationMs = end?.let {
                (it - (startedAtMillis.takeIf { value -> value > 0L } ?: write.startedAtMillis)).coerceAtLeast(0L)
            } ?: durationMs,
            latestResult = write.latestResult.take(MAX_RESULT_CHARS),
            modelCalls = maxOf(modelCalls, write.modelCalls),
            modelFailures = maxOf(modelFailures, write.modelFailures),
            modelTurns = maxOf(modelTurns, write.modelTurns),
            inputTokens = maxOf(inputTokens, write.inputTokens),
            outputTokens = maxOf(outputTokens, write.outputTokens),
            reasoningTokens = maxOf(reasoningTokens, write.reasoningTokens),
            cachedInputTokens = maxOf(cachedInputTokens, write.cachedInputTokens),
            totalTokens = maxOf(totalTokens, write.totalTokens),
            providerTokens = maxOf(providerTokens, write.providerTokens),
            estimatedTokens = maxOf(estimatedTokens, write.estimatedTokens),
            requestBytes = maxOf(requestBytes, write.requestBytes),
            responseBytes = maxOf(responseBytes, write.responseBytes),
            modelLatencyMs = maxOf(modelLatencyMs, write.modelLatencyMs),
            executedActions = maxOf(executedActions, write.executedActions),
            successfulActions = maxOf(successfulActions, write.successfulActions),
            failedActions = maxOf(failedActions, write.failedActions),
            observations = maxOf(observations, write.observations),
            reobservations = maxOf(reobservations, write.reobservations),
            rejectedPlans = maxOf(rejectedPlans, write.rejectedPlans),
            executionFailures = maxOf(executionFailures, write.executionFailures),
            confirmationRequests = maxOf(confirmationRequests, write.confirmationRequests),
            confirmationsAccepted = maxOf(confirmationsAccepted, write.confirmationsAccepted),
            userInputRequests = maxOf(userInputRequests, write.userInputRequests),
            userInputsSubmitted = maxOf(userInputsSubmitted, write.userInputsSubmitted),
            userTakeovers = maxOf(userTakeovers, write.userTakeovers),
            takeoverResumes = maxOf(takeoverResumes, write.takeoverResumes),
            appUsageJson = write.appUsage.toJson(),
            actionUsageJson = write.actionUsage.toJson(),
        )
    }

    private data class OutcomeDelta(
        val completed: Long = 0L,
        val failed: Long = 0L,
        val paused: Long = 0L,
        val cancelled: Long = 0L,
        val budgetExceeded: Long = 0L,
    )

    private fun outcomeDelta(status: String): OutcomeDelta = when (status.lowercase()) {
        "completed", "已完成" -> OutcomeDelta(completed = 1L)
        "cancelled", "canceled", "已手动停止" -> OutcomeDelta(cancelled = 1L)
        "paused", "interrupted", "已暂停", "等待确认" -> OutcomeDelta(paused = 1L)
        "budget_exceeded", "已达上限" -> OutcomeDelta(budgetExceeded = 1L)
        else -> OutcomeDelta(failed = 1L)
    }

    private fun Map<String, AgentUsageCounter>.toJson(): String = JSONObject().apply {
        this@toJson.forEach { (key, counter) ->
            put(key, JSONObject().apply {
                put("label", counter.displayName)
                put("uses", counter.uses)
                put("successes", counter.successes)
                put("failures", counter.failures)
            })
        }
    }.toString()

    private fun String.toUsageMap(): Map<String, Long> {
        val json = runCatching { JSONObject(this) }.getOrNull() ?: return emptyMap()
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optJSONObject(key)?.optLong("uses") ?: 0L)
            }
        }
    }

    private fun AgentDailyActivityEntity.toModel() = AgentDailyActivity(
        dateKey, firstActivityAtMillis, lastActivityAtMillis, chatCalls, chatFailures, agentTasks,
        completedTasks, failedTasks, pausedTasks, cancelledTasks, budgetExceededTasks, modelCalls,
        modelFailures, inputTokens, outputTokens, reasoningTokens, cachedInputTokens, totalTokens,
        providerTokens, estimatedTokens, modelLatencyMs, requestBytes, responseBytes, taskDurationMs,
        executedActions, successfulActions, failedActions, observations, reobservations, rejectedPlans,
        executionFailures, confirmationRequests, confirmationsAccepted, userInputRequests,
        userInputsSubmitted, userTakeovers, takeoverResumes, webSearches, imageRequests,
    )

    private fun AgentTaskAnalyticsEntity.toModel() = AgentTaskAnalytics(
        taskId, goal, status, startedAtMillis, endedAtMillis, durationMs, latestResult, modelCalls,
        modelFailures, modelTurns, inputTokens, outputTokens, reasoningTokens, cachedInputTokens,
        totalTokens, providerTokens, estimatedTokens, requestBytes, responseBytes, modelLatencyMs,
        executedActions, successfulActions, failedActions, observations, reobservations, rejectedPlans,
        executionFailures, confirmationRequests, confirmationsAccepted, userInputRequests,
        userInputsSubmitted, userTakeovers, takeoverResumes, appUsageJson.toUsageMap(),
        actionUsageJson.toUsageMap(),
    )

    private fun AgentModelUsageEntity.toModel() = AgentModelAnalytics(
        modelId, displayName, calls, failures, inputTokens, outputTokens, reasoningTokens,
        cachedInputTokens, totalTokens, providerTokens, estimatedTokens, latencyMs, requestBytes,
        responseBytes, firstUsedAtMillis, lastUsedAtMillis,
    )

    private fun AgentCapabilityUsageEntity.toModel() = AgentCapabilityAnalytics(
        kind, capabilityKey, displayName, uses, successes, failures, firstUsedAtMillis, lastUsedAtMillis,
    )

    companion object {
        private const val MAX_GOAL_CHARS = 240
        private const val MAX_RESULT_CHARS = 320
        private const val MAX_KEY_CHARS = 120
        private const val MAX_LABEL_CHARS = 80
        private const val MAX_TASK_HISTORY = 1_000
        private const val RECENT_TASK_STATE_LIMIT = 500
        private const val TOKEN_EVENT_RETENTION_MS = 730L * 24L * 60L * 60L * 1_000L
        private const val DAILY_RETENTION_MS = 1_825L * 24L * 60L * 60L * 1_000L

        @Volatile
        private var instance: AgentAnalyticsRepository? = null

        fun get(context: Context): AgentAnalyticsRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentAnalyticsRepository(context.applicationContext).also { instance = it }
            }
        }

        private fun dateKey(timestampMillis: Long): String = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        private fun positiveDelta(next: Long, previous: Long): Long = (next - previous).coerceAtLeast(0L)
    }
}
