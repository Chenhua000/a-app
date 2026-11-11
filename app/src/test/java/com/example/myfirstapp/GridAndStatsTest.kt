package com.example.myfirstapp

import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.TimeSlotEntity
import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.CoursePlacement
import com.example.myfirstapp.domain.PlacedOccurrence
import com.example.myfirstapp.domain.ScheduleResolver
import com.example.myfirstapp.domain.Statistics
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class GridAndStatsTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 10, 26)

    private fun slots(): List<TimeSlotEntity> = listOf(
        TimeSlotEntity(id = 1, sortIndex = 0, label = "第1节", startMinute = 8 * 60, endMinute = 8 * 60 + 45),
        TimeSlotEntity(id = 2, sortIndex = 1, label = "第2节", startMinute = 8 * 60 + 55, endMinute = 9 * 60 + 40),
        TimeSlotEntity(id = 3, sortIndex = 2, label = "第3节", startMinute = 10 * 60, endMinute = 10 * 60 + 45)
    )

    private fun course(id: Long = 1, day: Int = 1, start: Int = 8 * 60, end: Int = 9 * 60 + 40): CourseEntity =
        CourseEntity(
            id = id,
            name = "高等数学",
            dayOfWeek = day,
            startMinute = start,
            endMinute = end,
            location = "A101"
        )

    private fun occurrences(courses: List<CourseEntity>): List<com.example.myfirstapp.domain.CourseOccurrence> =
        ScheduleResolver.resolveRange(monday, monday.plusDays(6), courses, emptyList(), zone)

    @Test
    fun courseWithoutSlotFallsIntoMatchingRow() {
        val list = slots()
        val placed = CoursePlacement.place(
            occurrences = occurrences(listOf(course(start = 8 * 60 + 55, end = 9 * 60 + 40))),
            coursesById = mapOf(1L to course(start = 8 * 60 + 55, end = 9 * 60 + 40)),
            slots = list,
            zone = zone
        )
        assertEquals(1, placed.size)
        assertEquals(1, placed.first().rowIndex)
        assertEquals(1, placed.first().dayOfWeek)
    }

    @Test
    fun boundSlotWinsAndLongCourseSpansRows() {
        val list = slots()
        val bound = course(start = 8 * 60, end = 10 * 60 + 45).copy(slotId = 1L)
        val placed = CoursePlacement.place(
            occurrences = occurrences(listOf(bound)),
            coursesById = mapOf(bound.id to bound),
            slots = list,
            zone = zone
        )
        val item: PlacedOccurrence = placed.first()
        assertEquals(0, item.rowIndex)
        assertEquals(3, item.rowSpan)
    }

    @Test
    fun courseOutsideAllSlotsIsClamped() {
        val list = slots()
        val late = course(start = 22 * 60, end = 23 * 60)
        val placed = CoursePlacement.place(
            occurrences = occurrences(listOf(late)),
            coursesById = mapOf(late.id to late),
            slots = list,
            zone = zone
        )
        assertEquals(list.lastIndex, placed.first().rowIndex)
    }

    @Test
    fun moneySummaryCountsIncomeAndExpense() {
        val records = listOf(
            TransactionEntity(type = TransactionType.INCOME, amountCents = 100_00L, occurredAtEpochMillis = 0L),
            TransactionEntity(type = TransactionType.INCOME, amountCents = 50_00L, occurredAtEpochMillis = 1L),
            TransactionEntity(type = TransactionType.EXPENSE, amountCents = 30_00L, occurredAtEpochMillis = 2L)
        )
        val summary = Statistics.summarize(records)
        assertEquals(150_00L, summary.incomeCents)
        assertEquals(30_00L, summary.expenseCents)
        assertEquals(120_00L, summary.balanceCents)
    }

    @Test
    fun monthRangeUsesHalfOpenInterval() {
        val yearMonth = java.time.YearMonth.of(2026, 10)
        val from = TimeText.startOfDayMillis(yearMonth.atDay(1), zone)
        val to = TimeText.startOfDayMillis(yearMonth.plusMonths(1).atDay(1), zone)
        val lastDayEnd = monday.withDayOfMonth(31).atTime(LocalTime.of(23, 59))
            .atZone(zone).toInstant().toEpochMilli()
        assertTrue(lastDayEnd >= from && lastDayEnd < to)
        assertTrue(to > from)
        assertEquals(yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(), from)
    }

    @Test
    fun clockTextIsStable() {
        val millis = Instant.ofEpochMilli(
            monday.atTime(LocalTime.of(8, 5)).atZone(zone).toInstant().toEpochMilli()
        ).toEpochMilli()
        assertEquals("08:05", com.example.myfirstapp.domain.ReminderPlanner.formattedClock(millis, zone))
    }
}
