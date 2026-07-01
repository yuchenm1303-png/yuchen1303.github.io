package com.yuchen.ailedger.service

import com.yuchen.ailedger.model.PlanDraft
import com.yuchen.ailedger.model.PlanRepeatMode
import com.yuchen.ailedger.model.PlanTask
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlanScheduleCalculatorTest {
    private lateinit var originalTimeZone: TimeZone
    private val zone: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun onceTaskRejectsPastTime() {
        val now = epoch(2026, 7, 1, 10, 0)
        val draft = PlanDraft(
            title = "过期提醒",
            repeatMode = PlanRepeatMode.Once,
            scheduledAtMillis = epoch(2026, 7, 1, 9, 0),
        )

        assertNull(PlanScheduleCalculator.firstOccurrence(draft, now))
    }

    @Test
    fun dailyTaskMovesToNextDayAfterScheduledTime() {
        val task = task(
            repeatMode = PlanRepeatMode.Daily,
            anchor = epoch(2026, 7, 1, 8, 30),
        )

        val next = PlanScheduleCalculator.nextOccurrence(
            task,
            epoch(2026, 7, 1, 9, 0),
        )

        assertEquals(epoch(2026, 7, 2, 8, 30), next)
    }

    @Test
    fun weekdayTaskSkipsWeekend() {
        val task = task(
            repeatMode = PlanRepeatMode.Weekdays,
            anchor = epoch(2026, 7, 3, 8, 0),
        )

        val next = PlanScheduleCalculator.nextOccurrence(
            task,
            epoch(2026, 7, 3, 9, 0),
        )

        assertEquals(epoch(2026, 7, 6, 8, 0), next)
    }

    @Test
    fun weeklyTaskPreservesAnchorWeekday() {
        val task = task(
            repeatMode = PlanRepeatMode.Weekly,
            anchor = epoch(2026, 7, 1, 20, 0),
        )

        val next = PlanScheduleCalculator.nextOccurrence(
            task,
            epoch(2026, 7, 2, 8, 0),
        )

        assertEquals(epoch(2026, 7, 8, 20, 0), next)
    }

    @Test
    fun monthlyTaskClampsToLastDayOfShortMonth() {
        val task = task(
            repeatMode = PlanRepeatMode.Monthly,
            anchor = epoch(2026, 1, 31, 18, 0),
        )

        val next = PlanScheduleCalculator.nextOccurrence(
            task,
            epoch(2026, 2, 1, 8, 0),
        )

        assertEquals(epoch(2026, 2, 28, 18, 0), next)
    }

    private fun task(repeatMode: PlanRepeatMode, anchor: Long): PlanTask = PlanTask(
        id = "test",
        title = "测试计划",
        repeatMode = repeatMode,
        scheduledAtMillis = anchor,
        nextRunAtMillis = anchor,
    )

    private fun epoch(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
