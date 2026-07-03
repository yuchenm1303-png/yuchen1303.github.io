package com.yuchen.ailedger.model

import androidx.compose.runtime.Immutable

/** Token 数量的来源。Provider 表示云端返回的真实 usage，Estimated 表示本地保守估算。 */
enum class AgentTokenAccuracy {
    Provider,
    Estimated,
}

@Immutable
data class AgentTokenUsage(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val accuracy: AgentTokenAccuracy = AgentTokenAccuracy.Estimated,
) {
    val normalizedTotal: Long
        get() = totalTokens.coerceAtLeast(
            inputTokens.coerceAtLeast(0L) +
                outputTokens.coerceAtLeast(0L) +
                reasoningTokens.coerceAtLeast(0L),
        )

    val hasUsage: Boolean get() = normalizedTotal > 0L
}

@Immutable
data class AgentDailyActivity(
    val dateKey: String,
    val firstActivityAtMillis: Long,
    val lastActivityAtMillis: Long,
    val chatCalls: Long,
    val chatFailures: Long,
    val agentTasks: Long,
    val completedTasks: Long,
    val failedTasks: Long,
    val pausedTasks: Long,
    val cancelledTasks: Long,
    val budgetExceededTasks: Long,
    val modelCalls: Long,
    val modelFailures: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cachedInputTokens: Long,
    val totalTokens: Long,
    val providerTokens: Long,
    val estimatedTokens: Long,
    val modelLatencyMs: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val taskDurationMs: Long,
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
    val webSearches: Long,
    val imageRequests: Long,
)

@Immutable
data class AgentTaskAnalytics(
    val taskId: Long,
    val goal: String,
    val status: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val durationMs: Long,
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
    val appUsage: Map<String, Long>,
    val actionUsage: Map<String, Long>,
)

@Immutable
data class AgentModelAnalytics(
    val modelId: String,
    val displayName: String,
    val calls: Long,
    val failures: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val cachedInputTokens: Long,
    val totalTokens: Long,
    val providerTokens: Long,
    val estimatedTokens: Long,
    val latencyMs: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val firstUsedAtMillis: Long,
    val lastUsedAtMillis: Long,
)

@Immutable
data class AgentCapabilityAnalytics(
    val kind: String,
    val key: String,
    val displayName: String,
    val uses: Long,
    val successes: Long,
    val failures: Long,
    val firstUsedAtMillis: Long,
    val lastUsedAtMillis: Long,
)

@Immutable
data class AgentAnalyticsTotals(
    val totalTokens: Long = 0L,
    val providerTokens: Long = 0L,
    val estimatedTokens: Long = 0L,
    val peakDailyTokens: Long = 0L,
    val chatCalls: Long = 0L,
    val agentTasks: Long = 0L,
    val completedTasks: Long = 0L,
    val taskSuccessRate: Float = 0f,
    val executedActions: Long = 0L,
    val modelCalls: Long = 0L,
    val totalTaskDurationMs: Long = 0L,
    val longestTaskDurationMs: Long = 0L,
    val currentActiveStreakDays: Int = 0,
    val longestActiveStreakDays: Int = 0,
)

@Immutable
data class AgentAnalyticsSnapshot(
    val dailyActivity: List<AgentDailyActivity> = emptyList(),
    val recentTasks: List<AgentTaskAnalytics> = emptyList(),
    val modelUsage: List<AgentModelAnalytics> = emptyList(),
    val capabilityUsage: List<AgentCapabilityAnalytics> = emptyList(),
    val totals: AgentAnalyticsTotals = AgentAnalyticsTotals(),
    val loaded: Boolean = false,
)
