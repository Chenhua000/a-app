package com.example.myfirstapp.ui.schedule

import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.domain.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 回归测试：课表「编辑这门课 → 保存」不允许把课程弄丢。
 *
 * 曾经的实现用展示用的 PlacedOccurrence 反推 CourseEntity，
 * dayOfWeek 被写死成 1、slotId/reminderMinutes/enabled 全丢，
 * 于是保存后课程被 @Update 覆盖成「周一」，从原来的星期消失。
 */
class CourseEditRegressionTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    // 2026-10-26 是周一
    private val monday = LocalDate.of(2026, 10, 26)

    private fun dayOfWeekOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(zone).dayOfWeek.value

    @Test
    fun draftingFromRealEntityKeepsEveryField() {
        val entity = CourseEntity(
            id = 7L,
            name = "线性代数",
            dayOfWeek = 3, // 周三
            startMinute = 10 * 60,
            endMinute = 11 * 60,
            location = "B202",
            teacher = "王老师",
            reminderMinutes = 20,
            enabled = true,
            slotId = 42L,
            colorIndex = 5
        )

        val draft = entity.toCourseDraft()

        assertEquals(7L, draft.id)
        assertEquals(3, draft.dayOfWeek)        // 不能被写死成 1
        assertEquals(42L, draft.slotId)         // 节次绑定不能丢
        assertEquals("20", draft.reminderText)  // 提醒设置不能丢
        assertEquals(5, draft.colorIndex)
        assertEquals(10 * 60, draft.startMinute)
        assertEquals(11 * 60, draft.endMinute)
        assertEquals("B202", draft.location)
        assertEquals("王老师", draft.teacher)
        assertTrue(draft.enabled)
    }

    @Test
    fun disabledCourseKeepsDisabledFlag() {
        val entity = CourseEntity(
            id = 9L,
            name = "停用的课",
            dayOfWeek = 5,
            startMinute = 8 * 60,
            endMinute = 9 * 60,
            enabled = false
        )
        // 编辑草稿必须保留 enabled=false，否则保存会把停用的课意外打开
        assertTrue(!entity.toCourseDraft().enabled)
    }

    @Test
    fun savedCourseStillAppearsOnItsOriginalDay() {
        val entity = CourseEntity(
            id = 1L,
            name = "大学英语",
            dayOfWeek = 3, // 周三
            startMinute = 8 * 60,
            endMinute = 9 * 60,
            slotId = 42L,
            reminderMinutes = 15
        )

        // 模拟「编辑 → 保存」：草稿回写成实体时不丢字段
        val draft = entity.toCourseDraft().copy(name = "大学英语（改）")
        val saved = CourseEntity(
            id = draft.id ?: 0L,
            name = draft.name,
            dayOfWeek = draft.dayOfWeek,
            startMinute = draft.startMinute,
            endMinute = draft.endMinute,
            location = draft.location.takeIf { it.isNotEmpty() },
            teacher = draft.teacher.takeIf { it.isNotEmpty() },
            reminderMinutes = draft.reminderText.toIntOrNull(),
            enabled = draft.enabled,
            slotId = draft.slotId,
            colorIndex = draft.colorIndex
        )

        val occurrences = ScheduleResolver.resolveRange(
            monday, monday.plusDays(6), listOf(saved), emptyList(), zone
        )
        assertEquals(1, occurrences.size)
        // 关键：课程仍在周三(3)，而不是被改到周一(1) 后从原列消失
        assertEquals(3, dayOfWeekOf(occurrences.first().startAtMillis))
        assertEquals("大学英语（改）", occurrences.first().name)
    }

    @Test
    fun oldLossyRebuildWouldMoveCourseToMonday() {
        // 复现旧 bug 的后果，作为对照：dayOfWeek 被写死成 1
        val broken = CourseEntity(
            id = 1L,
            name = "大学英语",
            dayOfWeek = 1, // 旧代码写死
            startMinute = 8 * 60,
            endMinute = 9 * 60
        )
        val occurrences = ScheduleResolver.resolveRange(
            monday, monday.plusDays(6), listOf(broken), emptyList(), zone
        )
        // 只剩周一，周三那节课对用户来说就是「消失」了
        assertTrue(occurrences.all { dayOfWeekOf(it.startAtMillis) == 1 })
    }
}
