package com.yuchen.ailedger.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.yuchen.ailedger.data.PlanTaskStore
import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

data class PlanScheduleResult(
    val scheduled: Boolean,
    val exact: Boolean,
    val message: String? = null,
)

object PlanScheduleCalculator {
    fun firstOccurrence(draft: PlanDraft, nowMillis: Long = System.currentTimeMillis()): Long? {
        return when (draft.repeatMode) {
            PlanRepeatMode.Once -> draft.scheduledAtMillis.takeIf { it > nowMillis + MINIMUM_LEAD_MILLIS }
            else -> nextOccurrence(
                repeatMode = draft.repeatMode,
                scheduledAtMillis = draft.scheduledAtMillis,
                afterMillis = nowMillis,
            )
        }
    }

    fun nextOccurrence(task: PlanTask, afterMillis: Long = System.currentTimeMillis()): Long? {
        if (task.repeatMode == PlanRepeatMode.Once) {
            return task.nextRunAtMillis?.takeIf { it > afterMillis }
        }
        return nextOccurrence(task.repeatMode, task.scheduledAtMillis, afterMillis)
    }

    private fun nextOccurrence(
        repeatMode: PlanRepeatMode,
        scheduledAtMillis: Long,
        afterMillis: Long,
    ): Long? {
        val zone = ZoneId.systemDefault()
        val anchor = Instant.ofEpochMilli(scheduledAtMillis).atZone(zone)
            .withSecond(0)
            .withNano(0)
        val after = Instant.ofEpochMilli(afterMillis).atZone(zone)
        val time = anchor.toLocalTime()

        val candidate = when (repeatMode) {
            PlanRepeatMode.Once -> return scheduledAtMillis.takeIf { it > afterMillis }
            PlanRepeatMode.Daily -> nextDaily(after, time)
            PlanRepeatMode.Weekdays -> nextWeekday(after, time)
            PlanRepeatMode.Weekly -> nextWeekly(after, time, anchor.dayOfWeek)
            PlanRepeatMode.Monthly -> nextMonthly(after, time, anchor.dayOfMonth)
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun nextDaily(after: ZonedDateTime, time: LocalTime): ZonedDateTime {
        var candidate = atLocal(after.toLocalDate(), time, after.zone)
        if (!candidate.isAfter(after)) candidate = candidate.plusDays(1)
        return candidate
    }

    private fun nextWeekday(after: ZonedDateTime, time: LocalTime): ZonedDateTime {
        var date = after.toLocalDate()
        repeat(8) {
            val candidate = atLocal(date, time, after.zone)
            val weekday = date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            if (weekday && candidate.isAfter(after)) return candidate
            date = date.plusDays(1)
        }
        return atLocal(date, time, after.zone)
    }

    private fun nextWeekly(
        after: ZonedDateTime,
        time: LocalTime,
        dayOfWeek: DayOfWeek,
    ): ZonedDateTime {
        var date = after.toLocalDate().with(TemporalAdjusters.nextOrSame(dayOfWeek))
        var candidate = atLocal(date, time, after.zone)
        if (!candidate.isAfter(after)) {
            date = date.plusWeeks(1)
            candidate = atLocal(date, time, after.zone)
        }
        return candidate
    }

    private fun nextMonthly(
        after: ZonedDateTime,
        time: LocalTime,
        anchorDay: Int,
    ): ZonedDateTime {
        var month = YearMonth.from(after)
        var date = month.atDay(anchorDay.coerceAtMost(month.lengthOfMonth()))
        var candidate = atLocal(date, time, after.zone)
        if (!candidate.isAfter(after)) {
            month = month.plusMonths(1)
            date = month.atDay(anchorDay.coerceAtMost(month.lengthOfMonth()))
            candidate = atLocal(date, time, after.zone)
        }
        return candidate
    }

    private fun atLocal(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime {
        return LocalDateTime.of(date, time).atZone(zone)
    }

    private const val MINIMUM_LEAD_MILLIS = 5_000L
}

class PlanScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun exactAlarmReady(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun schedule(task: PlanTask): PlanScheduleResult {
        val triggerAt = task.nextRunAtMillis
            ?: return PlanScheduleResult(false, exactAlarmReady(), "计划没有下一次运行时间。")
        if (!task.enabled) {
            cancel(task.id)
            return PlanScheduleResult(false, exactAlarmReady(), "计划当前已暂停。")
        }
        if (triggerAt <= System.currentTimeMillis()) {
            return PlanScheduleResult(false, exactAlarmReady(), "计划时间已经过去。")
        }

        val operation = pendingIntent(task.id)
        val exactReady = exactAlarmReady()
        val scheduledExactly = runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactReady -> {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                    false
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                    true
                }
                else -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                    true
                }
            }
        }.recoverCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            false
        }.getOrElse {
            return PlanScheduleResult(false, exactReady, it.message ?: "系统拒绝了计划调度。")
        }

        return PlanScheduleResult(
            scheduled = true,
            exact = scheduledExactly,
            message = if (scheduledExactly) null else "当前未授权精确闹钟，系统可能稍晚提醒。",
        )
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(pendingIntent(taskId))
    }

    fun restoreEnabledTasks(): List<PlanTask> {
        val store = PlanTaskStore(appContext)
        val now = System.currentTimeMillis()
        var changed = false
        val restored = store.loadTasks().map { task ->
            if (!task.enabled) {
                cancel(task.id)
                task
            } else {
                val next = when {
                    task.nextRunAtMillis != null && task.nextRunAtMillis > now -> task.nextRunAtMillis
                    task.repeatMode == PlanRepeatMode.Once -> null
                    else -> PlanScheduleCalculator.nextOccurrence(task, now)
                }
                val normalized = if (next == null) {
                    changed = changed || task.enabled || task.nextRunAtMillis != null
                    task.copy(enabled = false, nextRunAtMillis = null)
                } else if (next != task.nextRunAtMillis) {
                    changed = true
                    task.copy(nextRunAtMillis = next)
                } else {
                    task
                }
                if (normalized.enabled) schedule(normalized) else cancel(normalized.id)
                normalized
            }
        }
        if (changed) store.saveTasks(restored)
        return restored
    }

    private fun pendingIntent(taskId: String): PendingIntent {
        val intent = Intent(appContext, PlanAlarmReceiver::class.java).apply {
            action = "$ACTION_PLAN_TRIGGER.$taskId"
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode() and Int.MAX_VALUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "plan_task_id"
        private const val ACTION_PLAN_TRIGGER = "com.yuchen.ailedger.action.PLAN_TRIGGER"
    }
}
