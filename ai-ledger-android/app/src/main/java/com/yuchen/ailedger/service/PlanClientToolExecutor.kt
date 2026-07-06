package com.yuchen.ailedger.service

import android.content.Context
import com.yuchen.ailedger.data.PlanTaskStore
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import com.yuchen.ailedger.model.PlanTaskType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes final-model plan_* client tools against the Android local Plan Center.
 *
 * The cloud model owns intent parsing and tool selection. Android owns the durable plan state,
 * AlarmManager scheduling, exact-alarm permission state, and the structured receipt returned to
 * the final model. This executor deliberately does not call backend APIs and does not infer intent
 * from natural language.
 */
internal class PlanClientToolExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val store = PlanTaskStore(appContext)
    private val scheduler = PlanScheduler(appContext)
    private val zone: ZoneId = ZoneId.systemDefault()

    fun execute(call: CloudClientToolCall, fallbackGoal: String = ""): JSONObject {
        val goal = call.originalUserGoal
            ?.takeIf(String::isNotBlank)
            ?: fallbackGoal.takeIf(String::isNotBlank)
            ?: call.arguments.optString("sourceText").takeIf(String::isNotBlank)
            ?: call.name
        val receipt = baseReceipt(call, goal)
        return runCatching {
            when (call.name) {
                "plan_create_task" -> executeCreate(call, receipt)
                "plan_list_tasks" -> executeList(call, receipt)
                "plan_get_task" -> executeGet(call, receipt)
                "plan_update_task" -> executeUpdate(call, receipt)
                "plan_delete_task" -> executeDelete(call, receipt)
                "plan_toggle_task" -> executeToggle(call, receipt)
                else -> receipt.fail("unsupported", "Android 当前不支持计划工具：${call.name}。")
            }
        }.getOrElse { error ->
            receipt.fail(
                status = "failed",
                summary = "计划工具执行异常：${error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName}",
                technical = "plan_tool_exception:${error::class.java.simpleName}",
            )
        }
    }

    private fun executeCreate(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val title = args.optString("title").trim().take(80)
        if (title.isBlank()) return receipt.fail("invalid_arguments", "创建计划失败：缺少标题。")
        val type = parseTaskType(args.optString("taskType"))
            ?: return receipt.fail("invalid_arguments", "创建计划失败：taskType 只能是 reminder 或 alarm。")
        val repeatMode = parseRepeatMode(args.optString("repeatMode", "once"))
            ?: return receipt.fail("invalid_arguments", "创建计划失败：repeatMode 不在支持范围内。")
        val scheduledAtMillis = parseScheduledAtMillis(args)
            ?: return receipt.fail("invalid_arguments", "创建计划失败：scheduledAtLocal 必须是 YYYY-MM-DDTHH:mm。")
        val draft = PlanDraft(
            title = title,
            note = args.optString("note").trim().take(240),
            type = type,
            repeatMode = repeatMode,
            scheduledAtMillis = scheduledAtMillis,
        )
        val nextRun = PlanScheduleCalculator.firstOccurrence(draft)
            ?: return receipt.fail("invalid_time", "创建计划失败：这个时间已经过去，且不是可继续计算的周期计划。")
        val task = PlanTask(
            id = "plan_${UUID.randomUUID().toString().replace("-", "").take(24)}",
            title = draft.title,
            note = draft.note,
            type = draft.type,
            repeatMode = draft.repeatMode,
            scheduledAtMillis = draft.scheduledAtMillis,
            nextRunAtMillis = nextRun,
            enabled = true,
        )
        val scheduleResult = scheduler.schedule(task)
        if (!scheduleResult.scheduled) {
            return receipt.fail("schedule_failed", scheduleResult.message ?: "系统拒绝了计划调度。")
        }
        val tasks = sortedTasks(store.loadTasks().filterNot { it.id == task.id } + task)
        store.saveTasks(tasks)
        return receipt.success(
            status = "created",
            summary = createdSummary(task, scheduleResult),
            task = task,
        ).put("scheduled", true)
            .put("exact", scheduleResult.exact)
            .put("warning", scheduleResult.message ?: "")
    }

    private fun executeList(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val filter = normalizeFilter(args.optString("filter", "all"))
        val range = normalizeRange(args.optString("range", "all"))
        val limit = args.optInt("limit", 20).coerceIn(1, 50)
        val tasks = sortedTasks(store.loadTasks())
            .filter { task -> task.matchesFilter(filter) && task.matchesRange(range) }
            .take(limit)
        val array = JSONArray().apply { tasks.forEach { put(it.toJsonObject()) } }
        return receipt.success(
            status = "listed",
            summary = if (tasks.isEmpty()) "没有找到符合条件的计划。" else "找到 ${tasks.size} 个符合条件的计划。",
        ).put("tasks", array)
            .put("count", tasks.size)
            .put("filter", filter)
            .put("range", range)
    }

    private fun executeGet(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val taskId = call.arguments.optString("taskId").trim()
        val task = store.loadTasks().firstOrNull { it.id == taskId }
            ?: return receipt.fail("not_found", "没有找到这个计划。")
        return receipt.success(
            status = "found",
            summary = "已读取计划：${task.title}。",
            task = task,
        )
    }

    private fun executeUpdate(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val taskId = args.optString("taskId").trim()
        if (taskId.isBlank()) return receipt.fail("invalid_arguments", "修改计划失败：缺少 taskId。")
        val tasks = store.loadTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return receipt.fail("not_found", "没有找到要修改的计划。")
        val current = tasks[index]
        val title = if (args.has("title")) args.optString("title").trim().take(80) else current.title
        if (title.isBlank()) return receipt.fail("invalid_arguments", "修改计划失败：标题不能为空。")
        val note = if (args.has("note")) args.optString("note").trim().take(240) else current.note
        val type = if (args.has("taskType")) {
            parseTaskType(args.optString("taskType"))
                ?: return receipt.fail("invalid_arguments", "修改计划失败：taskType 只能是 reminder 或 alarm。")
        } else {
            current.type.takeIf { it == PlanTaskType.Reminder || it == PlanTaskType.Alarm }
                ?: PlanTaskType.Reminder
        }
        val repeatMode = if (args.has("repeatMode")) {
            parseRepeatMode(args.optString("repeatMode"))
                ?: return receipt.fail("invalid_arguments", "修改计划失败：repeatMode 不在支持范围内。")
        } else {
            current.repeatMode
        }
        val scheduledAtMillis = if (args.has("scheduledAtLocal")) {
            parseScheduledAtMillis(args)
                ?: return receipt.fail("invalid_arguments", "修改计划失败：scheduledAtLocal 必须是 YYYY-MM-DDTHH:mm。")
        } else {
            current.scheduledAtMillis
        }
        val draft = PlanDraft(
            title = title,
            note = note,
            type = type,
            repeatMode = repeatMode,
            scheduledAtMillis = scheduledAtMillis,
        )
        val nextRun = if (current.enabled) {
            PlanScheduleCalculator.firstOccurrence(draft)
                ?: return receipt.fail("invalid_time", "修改计划失败：新时间已经过去，且不是可继续计算的周期计划。")
        } else {
            PlanScheduleCalculator.firstOccurrence(draft)
        }
        val updated = current.copy(
            title = draft.title,
            note = draft.note,
            type = draft.type,
            repeatMode = draft.repeatMode,
            scheduledAtMillis = draft.scheduledAtMillis,
            nextRunAtMillis = nextRun,
        )
        scheduler.cancel(current.id)
        if (updated.enabled) {
            val scheduleResult = scheduler.schedule(updated)
            if (!scheduleResult.scheduled) {
                scheduler.schedule(current)
                return receipt.fail("schedule_failed", scheduleResult.message ?: "系统拒绝了计划调度。")
            }
            receipt.put("scheduled", true)
                .put("exact", scheduleResult.exact)
                .put("warning", scheduleResult.message ?: "")
        }
        tasks[index] = updated
        store.saveTasks(sortedTasks(tasks))
        return receipt.success(
            status = "updated",
            summary = "已修改计划：${updated.title}。",
            task = updated,
        )
    }

    private fun executeDelete(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val taskId = call.arguments.optString("taskId").trim()
        if (taskId.isBlank()) return receipt.fail("invalid_arguments", "删除计划失败：缺少 taskId。")
        val tasks = store.loadTasks().toMutableList()
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return receipt.fail("not_found", "没有找到要删除的计划。")
        scheduler.cancel(task.id)
        store.saveTasks(sortedTasks(tasks.filterNot { it.id == taskId }))
        return receipt.success(
            status = "deleted",
            summary = "已删除计划：${task.title}。",
            task = task,
        )
    }

    private fun executeToggle(call: CloudClientToolCall, receipt: JSONObject): JSONObject {
        val args = call.arguments
        val taskId = args.optString("taskId").trim()
        if (taskId.isBlank()) return receipt.fail("invalid_arguments", "切换计划失败：缺少 taskId。")
        if (!args.has("enabled")) return receipt.fail("invalid_arguments", "切换计划失败：缺少 enabled。")
        val enabled = args.optBoolean("enabled")
        val tasks = store.loadTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return receipt.fail("not_found", "没有找到要切换的计划。")
        val current = tasks[index]
        val updated = if (enabled) {
            val nextRun = PlanScheduleCalculator.nextOccurrence(current)
                ?: PlanScheduleCalculator.firstOccurrence(
                    PlanDraft(
                        title = current.title,
                        note = current.note,
                        type = current.type,
                        repeatMode = current.repeatMode,
                        scheduledAtMillis = current.scheduledAtMillis,
                    ),
                )
                ?: return receipt.fail("invalid_time", "这个计划没有可用的下一次运行时间，无法启用。")
            current.copy(enabled = true, nextRunAtMillis = nextRun)
        } else {
            scheduler.cancel(current.id)
            current.copy(enabled = false)
        }
        if (updated.enabled) {
            val scheduleResult = scheduler.schedule(updated)
            if (!scheduleResult.scheduled) return receipt.fail("schedule_failed", scheduleResult.message ?: "系统拒绝了计划调度。")
            receipt.put("scheduled", true)
                .put("exact", scheduleResult.exact)
                .put("warning", scheduleResult.message ?: "")
        }
        tasks[index] = updated
        store.saveTasks(sortedTasks(tasks))
        return receipt.success(
            status = if (enabled) "enabled" else "disabled",
            summary = if (enabled) "已启用计划：${updated.title}。" else "已暂停计划：${updated.title}。",
            task = updated,
        )
    }

    private fun baseReceipt(call: CloudClientToolCall, goal: String): JSONObject = JSONObject().apply {
        put("protocol", call.resultProtocol)
        put("schema", "ai_ledger_plan_tool_result_v1")
        put("toolCallId", call.id)
        put("toolName", call.name)
        put("toolArguments", JSONObject(call.arguments.toString()))
        put("finalModel", call.finalModel ?: "")
        put("goal", goal.trim().take(300))
        put("stoppedForConfirmation", false)
        put("handled", true)
        put("completed", false)
        put("exactAlarmReady", scheduler.exactAlarmReady())
    }

    private fun JSONObject.success(
        status: String,
        summary: String,
        task: PlanTask? = null,
    ): JSONObject {
        put("ok", true)
        put("status", status)
        put("completed", true)
        put("handled", true)
        put("resultSummary", summary.take(1_800))
        task?.let { put("task", it.toJsonObject()) }
        put("actions", JSONArray().apply {
            put(actionReceipt(tool = optString("toolName"), status = status, ok = true, detail = summary))
        })
        return this
    }

    private fun JSONObject.fail(
        status: String,
        summary: String,
        technical: String = status,
    ): JSONObject {
        put("ok", false)
        put("status", status)
        put("completed", false)
        put("handled", true)
        put("resultSummary", summary.take(1_800))
        put("actions", JSONArray().apply {
            put(actionReceipt(tool = optString("toolName"), status = status, ok = false, detail = technical))
        })
        return this
    }

    private fun actionReceipt(tool: String, status: String, ok: Boolean, detail: String): JSONObject = JSONObject().apply {
        put("tool", tool)
        put("toolLabel", PLAN_TOOL_LABELS[tool] ?: tool)
        put("riskLevel", if (tool == "plan_delete_task") "medium" else "low")
        put("requiresConfirmation", false)
        put("status", status)
        put("ok", ok)
        put("verified", ok)
        put("shouldContinue", false)
        put("technicalDetail", detail.take(1_800))
        put("undoAvailable", false)
    }

    private fun parseScheduledAtMillis(args: JSONObject): Long? {
        val raw = args.optString("scheduledAtLocal")
            .ifBlank { args.optString("scheduled_at_local") }
            .ifBlank { args.optString("datetime") }
            .ifBlank { args.optString("dateTime") }
            .ifBlank { args.optString("time") }
            .trim()
        if (raw.isBlank()) return null
        val normalized = raw.replace(' ', 'T').removeSuffix("Z")
        val local = runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
            .getOrNull()
            ?: runCatching { LocalDateTime.parse(normalized, INPUT_DATE_TIME_FORMATTER) }.getOrNull()
            ?: return null
        val targetZone = args.optString("timeZone")
            .ifBlank { args.optString("timezone") }
            .ifBlank { args.optString("tz") }
            .takeIf { it.isNotBlank() && it != "client_local" }
            ?.let { rawZone -> runCatching { ZoneId.of(rawZone) }.getOrNull() }
            ?: zone
        return local.atZone(targetZone).toInstant().toEpochMilli()
    }

    private fun parseTaskType(value: String): PlanTaskType? = when (value.lowercase(Locale.US).trim().replace('-', '_')) {
        "reminder", "remind", "notification", "notice" -> PlanTaskType.Reminder
        "alarm", "clock", "wake_alarm" -> PlanTaskType.Alarm
        else -> null
    }

    private fun parseRepeatMode(value: String): PlanRepeatMode? = when (value.lowercase(Locale.US).trim().replace('-', '_')) {
        "", "none", "no_repeat", "one_time", "once" -> PlanRepeatMode.Once
        "daily", "every_day", "day" -> PlanRepeatMode.Daily
        "weekdays", "workdays", "weekday", "working_days" -> PlanRepeatMode.Weekdays
        "weekly", "every_week", "week" -> PlanRepeatMode.Weekly
        "monthly", "every_month", "month" -> PlanRepeatMode.Monthly
        else -> null
    }

    private fun normalizeFilter(value: String): String = when (value.lowercase(Locale.US).trim().replace('-', '_')) {
        "active" -> "active"
        "inactive", "paused", "disabled" -> "inactive"
        "completed", "finished", "done" -> "completed"
        else -> "all"
    }

    private fun normalizeRange(value: String): String = when (value.lowercase(Locale.US).trim().replace('-', '_')) {
        "today" -> "today"
        "tomorrow" -> "tomorrow"
        "next_7_days", "next7days", "week" -> "next_7_days"
        else -> "all"
    }

    private fun PlanTask.matchesFilter(filter: String): Boolean = when (filter) {
        "active" -> enabled && !isFinished
        "inactive" -> !enabled && !isFinished
        "completed" -> isFinished
        else -> true
    }

    private fun PlanTask.matchesRange(range: String): Boolean {
        if (range == "all") return true
        val next = nextRunAtMillis ?: return range == "completed" && isFinished
        val taskDate = Instant.ofEpochMilli(next).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (range) {
            "today" -> taskDate == today
            "tomorrow" -> taskDate == today.plusDays(1)
            "next_7_days" -> !taskDate.isBefore(today) && taskDate.isBefore(today.plusDays(8))
            else -> true
        }
    }

    private fun PlanTask.toJsonObject(): JSONObject = JSONObject().apply {
        put("taskId", id)
        put("id", id)
        put("title", title)
        put("note", note)
        put("taskType", when (type) {
            PlanTaskType.Alarm -> "alarm"
            else -> "reminder"
        })
        put("taskTypeLabel", type.label)
        put("repeatMode", repeatMode.toWire())
        put("repeatLabel", repeatMode.label)
        put("scheduledAtMillis", scheduledAtMillis)
        put("scheduledAtLocal", formatLocal(scheduledAtMillis))
        put("nextRunAtMillis", nextRunAtMillis ?: JSONObject.NULL)
        put("nextRunAtLocal", nextRunAtMillis?.let(::formatLocal) ?: JSONObject.NULL)
        put("enabled", enabled)
        put("finished", isFinished)
        put("createdAtMillis", createdAtMillis)
        put("lastRunAtMillis", lastRunAtMillis ?: JSONObject.NULL)
        put("lastResult", lastResult ?: JSONObject.NULL)
    }

    private fun PlanRepeatMode.toWire(): String = when (this) {
        PlanRepeatMode.Once -> "once"
        PlanRepeatMode.Daily -> "daily"
        PlanRepeatMode.Weekdays -> "weekdays"
        PlanRepeatMode.Weekly -> "weekly"
        PlanRepeatMode.Monthly -> "monthly"
    }

    private fun formatLocal(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(zone)
        .format(OUTPUT_DATE_TIME_FORMATTER)

    private fun createdSummary(task: PlanTask, result: PlanScheduleResult): String {
        val typeLabel = if (task.type == PlanTaskType.Alarm) "闹钟" else "提醒"
        val time = task.nextRunAtMillis?.let(::formatReadableLocal) ?: "下一次时间未知"
        val warning = result.message?.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        return "已创建${task.repeatMode.label}${typeLabel}：${task.title}，下一次在 $time。$warning".trim()
    }

    private fun formatReadableLocal(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(zone)
        .format(READABLE_DATE_TIME_FORMATTER)

    private fun sortedTasks(tasks: List<PlanTask>): List<PlanTask> = tasks.sortedWith(
        compareByDescending<PlanTask> { it.enabled && !it.isFinished }
            .thenBy { it.nextRunAtMillis ?: Long.MAX_VALUE }
            .thenByDescending { it.createdAtMillis },
    )

    companion object {
        private val PLAN_TOOL_NAMES = setOf(
            "plan_create_task",
            "plan_list_tasks",
            "plan_get_task",
            "plan_update_task",
            "plan_delete_task",
            "plan_toggle_task",
        )
        private val PLAN_TOOL_LABELS = mapOf(
            "plan_create_task" to "创建计划",
            "plan_list_tasks" to "查询计划列表",
            "plan_get_task" to "读取计划详情",
            "plan_update_task" to "修改计划",
            "plan_delete_task" to "删除计划",
            "plan_toggle_task" to "暂停/启用计划",
        )
        private val INPUT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]", Locale.US)
        private val OUTPUT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.US)
        private val READABLE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

        fun isPlanTool(name: String): Boolean = name.lowercase(Locale.US).trim().replace('-', '_') in PLAN_TOOL_NAMES
    }
}
