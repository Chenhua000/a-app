package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.CourseExceptionEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ScheduleResolver {
    fun resolveRange(
        startDate: LocalDate,
        endDate: LocalDate,
        courses: List<CourseEntity>,
        exceptions: List<CourseExceptionEntity>,
        zone: ZoneId
    ): List<CourseOccurrence> {
        val result = ArrayList<CourseOccurrence>()
        val usedKeys = HashSet<String>()
        val exceptionMap = exceptions.associateBy { "${it.courseId}:${it.originalDateEpochDay}" }
        val courseMap = courses.associateBy { it.id }
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate)
        var cursor = startDate
        repeat((totalDays + 1).toInt()) {
            for (course in courses) {
                if (!course.enabled) continue
                if (course.dayOfWeek != cursor.dayOfWeek.value) continue
                val originalDay = cursor.toEpochDay()
                val key = "${course.id}:$originalDay"
                val exception = exceptionMap[key]
                if (exception?.type == ExceptionType.CANCELLED) continue
                val planned = buildOccurrence(course, originalDay, exception, zone)
                result.add(planned)
                usedKeys.add(key)
            }
            cursor = cursor.plusDays(1)
        }

        for (exception in exceptions) {
            if (exception.type != ExceptionType.RESCHEDULED) continue
            val newStart = exception.newStartAtEpochMillis ?: continue
            val newEnd = exception.newEndAtEpochMillis ?: continue
            val key = "${exception.courseId}:${exception.originalDateEpochDay}"
            if (usedKeys.contains(key)) continue
            val newDate = java.time.Instant.ofEpochMilli(newStart).atZone(zone).toLocalDate()
            if (newDate.isBefore(startDate) || newDate.isAfter(endDate)) continue
            val course = courseMap[exception.courseId] ?: continue
            if (!course.enabled) continue
            result.add(
                CourseOccurrence(
                    courseId = course.id,
                    originalDateEpochDay = exception.originalDateEpochDay,
                    name = course.name,
                    startAtMillis = newStart,
                    endAtMillis = newEnd,
                    location = course.location,
                    teacher = course.teacher,
                    isRescheduled = true
                )
            )
            usedKeys.add(key)
        }

        return result.sortedBy { it.startAtMillis }
    }

    fun buildOccurrence(
        course: CourseEntity,
        originalDateEpochDay: Long,
        exception: CourseExceptionEntity?,
        zone: ZoneId
    ): CourseOccurrence {
        if (exception?.type == ExceptionType.RESCHEDULED &&
            exception.newStartAtEpochMillis != null &&
            exception.newEndAtEpochMillis != null
        ) {
            return CourseOccurrence(
                courseId = course.id,
                originalDateEpochDay = originalDateEpochDay,
                name = course.name,
                startAtMillis = exception.newStartAtEpochMillis!!,
                endAtMillis = exception.newEndAtEpochMillis!!,
                location = course.location,
                teacher = course.teacher,
                isRescheduled = true
            )
        }
        val date = LocalDate.ofEpochDay(originalDateEpochDay)
        val start = date.atTime(LocalTime.of(course.startMinute / 60, course.startMinute % 60))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val end = date.atTime(LocalTime.of(course.endMinute / 60, course.endMinute % 60))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return CourseOccurrence(
            courseId = course.id,
            originalDateEpochDay = originalDateEpochDay,
            name = course.name,
            startAtMillis = start,
            endAtMillis = end,
            location = course.location,
            teacher = course.teacher,
            isRescheduled = false
        )
    }
}
