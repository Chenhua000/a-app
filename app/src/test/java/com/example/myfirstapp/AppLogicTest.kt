package com.example.myfirstapp

import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.CourseExceptionEntity
import com.example.myfirstapp.data.local.TodoEntity
import com.example.myfirstapp.domain.ExceptionType
import com.example.myfirstapp.domain.ReminderPlanner
import com.example.myfirstapp.domain.ScheduleResolver
import com.example.myfirstapp.domain.TransactionType
import com.example.myfirstapp.domain.MoneyParser
import com.example.myfirstapp.domain.MoneyFormatter
import com.example.myfirstapp.domain.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AppLogicTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 10, 26)

    private fun course(id: Long = 1, reminderMinutes: Int? = null): CourseEntity =
        CourseEntity(
            id = id,
            name = "高等数学",
            dayOfWeek = monday.dayOfWeek.value,
            startMinute = 8 * 60,
            endMinute = 9 * 60 + 40,
            location = "A101",
            teacher = "张老师",
            reminderMinutes = reminderMinutes
        )

    @Test
    fun weeklyCourseIsResolvedOnceInThatWeek() {
        val startOfWeek = TimeText.startOfWeek(monday)
        val result = ScheduleResolver.resolveRange(
            startDate = startOfWeek,
            endDate = startOfWeek.plusDays(6),
            courses = listOf(course()),
            exceptions = emptyList(),
            zone = zone
        )
        assertEquals(1, result.size)
        assertEquals("高等数学", result.first().name)
        assertEquals(
            monday.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli(),
            result.first().startAtMillis
        )
    }

    @Test
    fun cancelledCourseIsNotResolved() {
        val exception = CourseExceptionEntity(
            courseId = 1,
            originalDateEpochDay = monday.toEpochDay(),
            type = ExceptionType.CANCELLED,
            newStartAtEpochMillis = null,
            newEndAtEpochMillis = null
        )
        val result = ScheduleResolver.resolveRange(
            startDate = monday,
            endDate = monday,
            courses = listOf(course()),
            exceptions = listOf(exception),
            zone = zone
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun rescheduledCourseUsesNewTime() {
        val newDate = LocalDate.of(2026, 10, 27)
        val newStart = newDate.atTime(LocalTime.of(14, 0)).atZone(zone).toInstant().toEpochMilli()
        val newEnd = newDate.atTime(LocalTime.of(15, 40)).atZone(zone).toInstant().toEpochMilli()
        val exception = CourseExceptionEntity(
            courseId = 1,
            originalDateEpochDay = monday.toEpochDay(),
            type = ExceptionType.RESCHEDULED,
            newStartAtEpochMillis = newStart,
            newEndAtEpochMillis = newEnd
        )
        val result = ScheduleResolver.resolveRange(
            startDate = monday,
            endDate = monday.plusDays(6),
            courses = listOf(course()),
            exceptions = listOf(exception),
            zone = zone
        )
        assertEquals(1, result.size)
        assertEquals(newStart, result.first().startAtMillis)
        assertTrue(result.first().isRescheduled)
    }

    @Test
    fun courseReminderUsesDefaultAndCustomMinutes() {
        val now = monday.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant().toEpochMilli()
        val defaultTrigger = monday.atTime(LocalTime.of(7, 45)).atZone(zone).toInstant().toEpochMilli()
        val customTrigger = monday.atTime(LocalTime.of(7, 30)).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            defaultTrigger,
            ReminderPlanner.nextTriggerForCourse(now, zone, listOf(course()), emptyList(), 15)
        )
        assertEquals(
            customTrigger,
            ReminderPlanner.nextTriggerForCourse(now, zone, listOf(course(reminderMinutes = 30)), emptyList(), 15)
        )
    }

    @Test
    fun completedTodoIsNotReminded() {
        val now = monday.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli()
        val due = monday.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        val pending = TodoEntity(id = 1, title = "交作业", note = null, dueAtEpochMillis = due)
        val done = TodoEntity(id = 2, title = "复习", note = null, dueAtEpochMillis = due, isCompleted = true)

        assertEquals(
            due - 30 * 60_000L,
            ReminderPlanner.nextTriggerForTodo(now, listOf(pending), 30)
        )
        assertNull(ReminderPlanner.nextTriggerForTodo(now, listOf(done), 30))
    }

    @Test
    fun dueRemindersIncludeItemsInWindow() {
        val due = monday.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        val now = due - 15 * 60_000L
        val todos = listOf(TodoEntity(id = 1, title = "交作业", note = null, dueAtEpochMillis = due))
        val reminders = ReminderPlanner.dueReminders(now, zone, emptyList(), emptyList(), todos, 15)
        assertTrue(reminders.any { it.stream == com.example.myfirstapp.domain.ReminderStream.TODO })
    }

    @Test
    fun moneyParsingAndFormattingAreStable() {
        assertEquals(1850L, MoneyParser.parseYuanToCents("18.50"))
        assertEquals(5L, MoneyParser.parseYuanToCents("0.05"))
        assertEquals(1200L, MoneyParser.parseYuanToCents("12"))
        assertNull(MoneyParser.parseYuanToCents("12.345"))
        assertNull(MoneyParser.parseYuanToCents("abc"))
        assertEquals("12.30", MoneyFormatter.formatCents(1230L))
        assertEquals("-8.05", MoneyFormatter.formatCents(-805L))
    }

    @Test
    fun transactionTypeKeepsAmountPositive() {
        val income = com.example.myfirstapp.data.local.TransactionEntity(
            type = TransactionType.INCOME,
            amountCents = 5000L,
            note = "生活费",
            occurredAtEpochMillis = monday.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli()
        )
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals(5000L, income.amountCents)
    }
}
