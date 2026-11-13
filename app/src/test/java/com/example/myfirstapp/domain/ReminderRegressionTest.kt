package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderRegressionTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 11, 12)
    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun task(repeat: Int = TASK_REPEAT_ONCE) = TaskEntity(id = 7, title = "任务", anchorAtEpochMillis = at(9), repeatType = repeat, reminderOn = true)

    @Test fun firedCourseAndTodoAreNotFilteredAsFutureEvents() {
        val course = CourseEntity(id = 1, name = "数学", dayOfWeek = 4, startMinute = 9 * 60, endMinute = 10 * 60)
        val todo = TodoEntity(id = 2, title = "作业", note = null, dueAtEpochMillis = at(9))
        val due = ReminderPlanner.firedReminders(at(8, 45), zone, listOf(course), emptyList(), listOf(todo), emptyList(), emptyList(), 15)
        assertEquals(setOf(ReminderStream.COURSE, ReminderStream.TODO), due.map { it.stream }.toSet())
    }

    @Test fun firingDailyTaskKeepsCurrentOccurrenceAndDoesNotSendTomorrowEarly() {
        val due = ReminderPlanner.firedReminders(at(9), zone, emptyList(), emptyList(), emptyList(), listOf(task(TASK_REPEAT_DAILY)), emptyList(), 0)
        assertEquals(1, due.size)
        assertEquals(at(9), due.single().triggerAtMillis)
        assertTrue(due.single().key.endsWith(day.toEpochDay().toString()))
    }

    @Test fun completedAndUndatedTasksDoNotNotify() {
        val completed = TaskCompletionEntity(7, day.toEpochDay(), at(8))
        assertTrue(ReminderPlanner.firedReminders(at(9), zone, emptyList(), emptyList(), emptyList(), listOf(task()), listOf(completed), 0).isEmpty())
        assertTrue(ReminderPlanner.upcomingTaskReminders(at(8), zone, listOf(task().copy(hasDate = false))).isEmpty())
    }

    @Test fun nextDailyReminderSkipsCompletedDay() {
        val completed = TaskCompletionEntity(7, day.toEpochDay(), at(8))
        val next = ReminderPlanner.upcomingTaskReminders(at(8), zone, listOf(task(TASK_REPEAT_DAILY)), listOf(completed)).single()
        assertEquals(at(9) + 24 * 3600_000L, next.triggerAtMillis)
    }

    @Test fun reminderLessThanAMinuteAwayIsStillScheduled() {
        assertEquals(at(9), ReminderPlanner.upcomingTaskReminders(at(9) - 30_000, zone, listOf(task())).single().triggerAtMillis)
    }

    @Test fun repeatedTasksDoNotStartBeforeTheirAnchorDate() {
        val future = task(TASK_REPEAT_DAILY).copy(anchorAtEpochMillis = at(9) + 7 * 24 * 3600_000L)
        assertEquals(future.anchorAtEpochMillis, ReminderPlanner.upcomingTaskReminders(at(8), zone, listOf(future)).single().triggerAtMillis)
    }

    @Test fun overdueTaskCarriesToTodayUntilCompleted() {
        val today = LocalDate.now(zone)
        val past = today.minusDays(2).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val item = task().copy(anchorAtEpochMillis = past)
        assertTrue(item.visibleOn(today, zone, emptyMap()))
        assertFalse(item.visibleOn(today, zone, mapOf(7L to setOf(today.minusDays(2).toEpochDay()))))
    }
}
