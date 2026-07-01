package com.yuchen.ailedger.ui

import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val planDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
internal val planTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val planNextFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

internal fun PlanTask.toPlanDraft(): PlanDraft = PlanDraft(
    title = title,
    note = note,
    type = type,
    repeatMode = repeatMode,
    scheduledAtMillis = scheduledAtMillis,
)

internal fun defaultPlanDraft(title: String): PlanDraft = PlanDraft(
    title = title.trim(),
    scheduledAtMillis = ZonedDateTime.now()
        .plusHours(1)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toInstant()
        .toEpochMilli(),
)

internal fun tomorrowAt(hour: Int, minute: Int): Long = ZonedDateTime.now()
    .plusDays(1)
    .withHour(hour)
    .withMinute(minute)
    .withSecond(0)
    .withNano(0)
    .toInstant()
    .toEpochMilli()

internal fun nextAt(hour: Int, minute: Int): Long {
    val now = ZonedDateTime.now()
    var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return target.toInstant().toEpochMilli()
}

internal fun replacePlanDate(source: Long, date: LocalDate): Long {
    val old = Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault())
    return LocalDateTime.of(date, old.toLocalTime())
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

internal fun replacePlanTime(source: Long, time: LocalTime): Long {
    val old = Instant.ofEpochMilli(source).atZone(ZoneId.systemDefault())
    return LocalDateTime.of(old.toLocalDate(), time)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

internal fun PlanTask.scheduleLabel(): String {
    val anchor = Instant.ofEpochMilli(scheduledAtMillis).atZone(ZoneId.systemDefault())
    val time = anchor.format(planTimeFormat)
    return when (repeatMode) {
        PlanRepeatMode.Once -> "${anchor.format(planDateFormat)} · $time"
        PlanRepeatMode.Daily -> "每天 $time"
        PlanRepeatMode.Weekdays -> "工作日 $time"
        PlanRepeatMode.Weekly ->
            "每周 ${anchor.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)} $time"
        PlanRepeatMode.Monthly -> "每月${anchor.dayOfMonth}日 $time"
    }
}

internal fun PlanTask.nextLabel(): String = when {
    isFinished -> lastRunAtMillis?.let { "已于 ${formatPlanTime(it)} 完成" } ?: "单次计划已完成"
    !enabled -> nextRunAtMillis?.let { "暂停中 · 原定 ${formatPlanTime(it)}" } ?: "计划已暂停"
    nextRunAtMillis != null -> "下次运行：${formatPlanTime(nextRunAtMillis)}"
    else -> "暂无下次运行时间"
}

private fun formatPlanTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(planNextFormat)
