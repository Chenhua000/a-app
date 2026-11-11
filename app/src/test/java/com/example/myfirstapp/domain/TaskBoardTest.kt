package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskBoardTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun task(
        date: LocalDate,
        minute: Int,
        repeatType: Int,
        durationType: Int = TASK_DURATION_SHORT
    ) = TaskEntity(
        title = "t",
        durationType = durationType,
        repeatType = repeatType,
        anchorAtEpochMillis = LocalDateTime.of(
            date,
            java.time.LocalTime.of(minute / 60, minute % 60)
        ).atZone(zone).toInstant().toEpochMilli()
    )

    @Test
    fun onceOnlyOccursOnItsOwnDate() {
        val t = task(LocalDate.of(2026, 11, 1), 9 * 60, TASK_REPEAT_ONCE)
        assertTrue(t.occursOn(LocalDate.of(2026, 11, 1), zone))
        assertFalse(t.occursOn(LocalDate.of(2026, 11, 2), zone))
        assertFalse(t.occursOn(LocalDate.of(2026, 10, 31), zone))
    }

    @Test
    fun dailyOccursFromStartDateOnwards() {
        val t = task(LocalDate.of(2026, 11, 1), 8 * 60, TASK_REPEAT_DAILY)
        for (offset in 0L..6L) {
            assertTrue(t.occursOn(LocalDate.of(2026, 11, 1).plusDays(offset), zone))
        }
        // 开始日期之前不出现（避免把任务显示在本周过去的几天）
        assertFalse(t.occursOn(LocalDate.of(2026, 10, 20), zone))
        assertFalse(t.occursOn(LocalDate.of(2026, 10, 31), zone))
    }

    @Test
    fun weeklyOccursOnSameWeekdayFromStartDate() {
        // 2026-11-01 是周日；每周任务应只在周日出现，且不早于开始日期
        val anchor = LocalDate.of(2026, 11, 1)
        assertTrue(anchor.dayOfWeek == java.time.DayOfWeek.SUNDAY)
        val t = task(anchor, 20 * 60, TASK_REPEAT_WEEKLY)

        assertTrue(t.occursOn(LocalDate.of(2026, 11, 1), zone))  // 本周日
        assertTrue(t.occursOn(LocalDate.of(2026, 11, 8), zone))  // 下周日
        // 开始日期之前的上周日不出现
        assertFalse(t.occursOn(LocalDate.of(2026, 10, 25), zone))
        // 其余六天都不出现
        assertFalse(t.occursOn(LocalDate.of(2026, 11, 2), zone)) // 周一
        assertFalse(t.occursOn(LocalDate.of(2026, 11, 5), zone)) // 周四
        assertFalse(t.occursOn(LocalDate.of(2026, 11, 7), zone)) // 周六
    }

    @Test
    fun timeMinuteExtractsClock() {
        val t = task(LocalDate.of(2026, 11, 1), 14 * 60 + 35, TASK_REPEAT_ONCE)
        assertEquals(14 * 60 + 35, t.timeMinute(zone))
        assertEquals("14:35", TimeText.formatMinute(t.timeMinute(zone)))
    }

    @Test
    fun labelsAreReadable() {
        assertEquals("一次性", repeatLabel(TASK_REPEAT_ONCE))
        assertEquals("每天", repeatLabel(TASK_REPEAT_DAILY))
        assertEquals("每周", repeatLabel(TASK_REPEAT_WEEKLY))
        assertEquals("短任务", durationLabel(TASK_DURATION_SHORT))
        assertEquals("长任务", durationLabel(TASK_DURATION_LONG))
    }
}
