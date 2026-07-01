package com.yuchen.ailedger.model

import androidx.compose.runtime.Immutable

@Immutable
enum class PlanTaskType(val label: String, val shortLabel: String) {
    Reminder("提醒", "提"),
    Alarm("闹钟", "闹"),
    AiTask("AI 任务", "AI"),
    ConditionWatch("条件监控", "监"),
}

@Immutable
enum class PlanRepeatMode(val label: String, val compactLabel: String) {
    Once("仅一次", "一次"),
    Daily("每天", "每天"),
    Weekdays("工作日", "工作日"),
    Weekly("每周", "每周"),
    Monthly("每月", "每月"),
}

@Immutable
enum class PlanTaskFilter(val label: String) {
    All("全部"),
    Active("活动"),
    Paused("已暂停"),
}

@Immutable
data class PlanTask(
    val id: String,
    val title: String,
    val note: String = "",
    val type: PlanTaskType = PlanTaskType.Reminder,
    val repeatMode: PlanRepeatMode = PlanRepeatMode.Once,
    val scheduledAtMillis: Long,
    val nextRunAtMillis: Long? = scheduledAtMillis,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastRunAtMillis: Long? = null,
    val lastResult: String? = null,
) {
    val isFinished: Boolean
        get() = repeatMode == PlanRepeatMode.Once && nextRunAtMillis == null
}

@Immutable
data class PlanDraft(
    val title: String,
    val note: String = "",
    val type: PlanTaskType = PlanTaskType.Reminder,
    val repeatMode: PlanRepeatMode = PlanRepeatMode.Once,
    val scheduledAtMillis: Long,
)

@Immutable
data class PlanCenterUiState(
    val tasks: List<PlanTask> = emptyList(),
    val filter: PlanTaskFilter = PlanTaskFilter.All,
    val exactAlarmReady: Boolean = true,
    val lastError: String? = null,
) {
    val visibleTasks: List<PlanTask>
        get() = when (filter) {
            PlanTaskFilter.All -> tasks
            PlanTaskFilter.Active -> tasks.filter { it.enabled && !it.isFinished }
            PlanTaskFilter.Paused -> tasks.filter { !it.enabled || it.isFinished }
        }

    val activeCount: Int
        get() = tasks.count { it.enabled && !it.isFinished }
}
