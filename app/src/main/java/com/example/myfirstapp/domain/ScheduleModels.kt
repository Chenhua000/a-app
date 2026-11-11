package com.example.myfirstapp.domain

enum class ReminderStream(val storageName: String) {
    COURSE("course"),
    TODO("todo"),
    TASK("task");

    companion object {
        fun from(value: String?): ReminderStream? =
            entries.firstOrNull { it.storageName == value }
    }
}

enum class TransactionType { INCOME, EXPENSE }

enum class ExceptionType { CANCELLED, RESCHEDULED }

data class CourseOccurrence(
    val courseId: Long,
    val originalDateEpochDay: Long,
    val name: String,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val location: String?,
    val teacher: String?,
    val isRescheduled: Boolean
)

data class DueReminder(
    val stream: ReminderStream,
    val title: String,
    val text: String,
    val key: String
)

data class ScheduleSnapshot(
    val courses: List<com.example.myfirstapp.data.local.CourseEntity>,
    val exceptions: List<com.example.myfirstapp.data.local.CourseExceptionEntity>
)

/** 一条已经安排好的未来提醒 */
data class PlannedReminder(
    val stream: ReminderStream,
    val key: String,
    val triggerAtMillis: Long,
    val title: String,
    val text: String
)
