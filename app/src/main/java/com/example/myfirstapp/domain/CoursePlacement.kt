package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.TimeSlotEntity
import java.time.Instant
import java.time.ZoneId

/**
 * 网格课表的纯计算逻辑：把「一周内的课程安排」放到「自定义节次」组成的行上。
 * 不依赖 Android，可直接单元测试。
 */
data class PlacedOccurrence(
    val occurrence: CourseOccurrence,
    /** 起始行（节次顺序） */
    val rowIndex: Int,
    /** 跨多少行 */
    val rowSpan: Int,
    val colorIndex: Int,
    /** 列：1=周一 ... 7=周日 */
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int
)

object CoursePlacement {

    fun minuteOfDay(millis: Long, zone: ZoneId): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    /**
     * 找到某课程应该落在的行。优先使用绑定的节次，其次按时间包含或最近匹配。
     */
    fun resolveRowIndex(
        course: CourseEntity?,
        startMinute: Int,
        slots: List<TimeSlotEntity>
    ): Int {
        if (slots.isEmpty()) return 0
        val bound = course?.slotId?.let { slotId -> slots.indexOfFirst { it.id == slotId } }
        if (bound != null && bound >= 0) return bound

        val containing = slots.indexOfFirst { startMinute >= it.startMinute && startMinute < it.endMinute }
        if (containing >= 0) return containing

        val before = slots.indexOfLast { it.endMinute <= startMinute }
        if (before >= 0 && before < slots.lastIndex) return before + 1
        return if (startMinute < slots.first().startMinute) 0 else slots.lastIndex
    }

    /**
     * 计算课程跨多少行：后续节次的开始时间早于课程结束时间即视为覆盖。
     */
    fun resolveRowSpan(slots: List<TimeSlotEntity>, rowIndex: Int, endMinute: Int): Int {
        if (slots.isEmpty()) return 1
        var span = 1
        var cursor = rowIndex
        while (cursor + 1 < slots.size && slots[cursor + 1].startMinute < endMinute) {
            span += 1
            cursor += 1
        }
        return span
    }

    fun place(
        occurrences: List<CourseOccurrence>,
        coursesById: Map<Long, CourseEntity>,
        slots: List<TimeSlotEntity>,
        zone: ZoneId
    ): List<PlacedOccurrence> {
        if (slots.isEmpty()) return emptyList()
        return occurrences.map { occurrence ->
            val course = coursesById[occurrence.courseId]
            val startMinute = minuteOfDay(occurrence.startAtMillis, zone)
            val endMinute = minuteOfDay(occurrence.endAtMillis, zone)
            val rowIndex = resolveRowIndex(course, startMinute, slots).coerceIn(0, slots.lastIndex)
            val rowSpan = resolveRowSpan(slots, rowIndex, endMinute)
                .coerceAtMost(slots.size - rowIndex)
                .coerceAtLeast(1)
            val dayOfWeek = Instant.ofEpochMilli(occurrence.startAtMillis).atZone(zone).dayOfWeek.value
            PlacedOccurrence(
                occurrence = occurrence,
                rowIndex = rowIndex,
                rowSpan = rowSpan,
                colorIndex = course?.colorIndex ?: 0,
                dayOfWeek = dayOfWeek,
                startMinute = startMinute,
                endMinute = endMinute
            )
        }
    }
}
