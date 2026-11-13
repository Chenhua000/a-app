package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.CourseExceptionEntity
import com.example.myfirstapp.data.local.TodoEntity
import com.example.myfirstapp.domain.TASK_REPEAT_DAILY
import com.example.myfirstapp.domain.TASK_REPEAT_ONCE
import com.example.myfirstapp.domain.TASK_REPEAT_WEEKLY
import java.time.LocalDate
import java.time.ZoneId

object ReminderPlanner {
    private const val LOOK_AHEAD_DAYS = 400L
    private const val FUTURE_TOLERANCE_MILLIS = 60_000L
    private const val PAST_TOLERANCE_MILLIS = 6 * 60 * 60 * 1000L
    /** 每条流最多同时向系统注册多少个未来闹钟 */
    const val MAX_ALARMS_PER_STREAM = 15

    fun nextTriggerForCourse(
        nowMillis: Long,
        zone: ZoneId,
        courses: List<CourseEntity>,
        exceptions: List<CourseExceptionEntity>,
        defaultReminderMinutes: Int
    ): Long? = upcomingCourseReminders(nowMillis, zone, courses, exceptions, defaultReminderMinutes)
        .firstOrNull()?.triggerAtMillis

    fun nextTriggerForTodo(
        nowMillis: Long,
        todos: List<TodoEntity>,
        defaultReminderMinutes: Int
    ): Long? = upcomingTodoReminders(nowMillis, todos, defaultReminderMinutes)
        .firstOrNull()?.triggerAtMillis

    fun upcomingCourseReminders(
        nowMillis: Long,
        zone: ZoneId,
        courses: List<CourseEntity>,
        exceptions: List<CourseExceptionEntity>,
        defaultReminderMinutes: Int
    ): List<PlannedReminder> {
        val cutoff = nowMillis
        return courseReminders(nowMillis, zone, courses, exceptions, defaultReminderMinutes)
            .asSequence()
            .filter { it.triggerAtMillis > cutoff }
            .map { it.toPlanned() }
            .take(MAX_ALARMS_PER_STREAM)
            .toList()
    }

    fun upcomingTodoReminders(
        nowMillis: Long,
        todos: List<TodoEntity>,
        defaultReminderMinutes: Int
    ): List<PlannedReminder> {
        val cutoff = nowMillis
        return todoReminders(nowMillis, todos, defaultReminderMinutes)
            .asSequence()
            .filter { it.triggerAtMillis > cutoff }
            .map { it.toPlanned() }
            .take(MAX_ALARMS_PER_STREAM)
            .toList()
    }

    fun upcomingTaskReminders(
        nowMillis: Long,
        zone: ZoneId,
        tasks: List<com.example.myfirstapp.data.local.TaskEntity>,
        completions: List<com.example.myfirstapp.data.local.TaskCompletionEntity> = emptyList()
    ): List<PlannedReminder> {
        val cutoff = nowMillis
        return taskReminders(nowMillis, zone, tasks, completions)
            .asSequence()
            .filter { it.triggerAtMillis > cutoff }
            .map { it.toPlanned() }
            .take(MAX_ALARMS_PER_STREAM)
            .toList()
    }

    fun firedReminders(
        nowMillis: Long,
        zone: ZoneId,
        courses: List<CourseEntity>,
        exceptions: List<CourseExceptionEntity>,
        todos: List<TodoEntity>,
        tasks: List<com.example.myfirstapp.data.local.TaskEntity>,
        completions: List<com.example.myfirstapp.data.local.TaskCompletionEntity>,
        defaultReminderMinutes: Int
    ): List<PlannedReminder> {
        val lower = nowMillis - PAST_TOLERANCE_MILLIS
        return (courseReminders(nowMillis, zone, courses, exceptions, defaultReminderMinutes) +
            todoReminders(nowMillis, todos, defaultReminderMinutes) +
            taskReminders(lower - 1, zone, tasks, completions))
            .filter { it.triggerAtMillis in lower..nowMillis }.map { it.toPlanned() }
    }

    fun dueReminders(
        nowMillis: Long,
        zone: ZoneId,
        courses: List<CourseEntity>,
        exceptions: List<CourseExceptionEntity>,
        todos: List<TodoEntity>,
        defaultReminderMinutes: Int
    ): List<DueReminder> {
        val lower = nowMillis - PAST_TOLERANCE_MILLIS
        val upper = nowMillis + FUTURE_TOLERANCE_MILLIS
        val courseItems = courseReminders(nowMillis, zone, courses, exceptions, defaultReminderMinutes)
            .filter { it.triggerAtMillis in lower..upper }
            .map { it.toDueReminder() }
        val todoItems = todoReminders(nowMillis, todos, defaultReminderMinutes)
            .filter { it.triggerAtMillis in lower..upper }
            .map { it.toDueReminder() }
        return courseItems + todoItems
    }

    private fun courseReminders(
        nowMillis: Long,
        zone: ZoneId,
        courses: List<CourseEntity>,
        exceptions: List<CourseExceptionEntity>,
        defaultReminderMinutes: Int
    ): List<ReminderItem> {
        if (courses.isEmpty()) return emptyList()
        // 用入参里的「现在」而不是系统时钟，这样同一份入参永远算出同一批提醒（也才测得准）
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val occurrences = ScheduleResolver.resolveRange(
            startDate = today.minusDays(1),
            endDate = today.plusDays(LOOK_AHEAD_DAYS),
            courses = courses,
            exceptions = exceptions,
            zone = zone
        )
        val reminders = occurrences.mapNotNull { occurrence ->
            val course = courses.firstOrNull { it.id == occurrence.courseId } ?: return@mapNotNull null
            val minutes = parseReminderMinutes(course.reminderMinutes, defaultReminderMinutes)
            val trigger = occurrence.startAtMillis - minutes * 60_000L
            ReminderItem(
                stream = ReminderStream.COURSE,
                triggerAtMillis = trigger,
                title = "准备上课：${occurrence.name}",
                text = buildString {
                    append(formattedClock(occurrence.startAtMillis, zone))
                    append(" 开始")
                    occurrence.location?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    occurrence.teacher?.takeIf { it.isNotBlank() }?.let { append(" · ${it}老师") }
                    if (occurrence.isRescheduled) append(" · 调课后的时间")
                },
                key = "course:${occurrence.courseId}:${occurrence.originalDateEpochDay}"
            )
        }
        return reminders.sortedBy { it.triggerAtMillis }
    }

    private fun todoReminders(
        nowMillis: Long,
        todos: List<TodoEntity>,
        defaultReminderMinutes: Int
    ): List<ReminderItem> = todos
        .filter { !it.isCompleted }
        .map { todo ->
            val minutes = parseReminderMinutes(todo.reminderMinutes, defaultReminderMinutes)
            val zone = ZoneId.systemDefault()
            ReminderItem(
                stream = ReminderStream.TODO,
                triggerAtMillis = todo.dueAtEpochMillis - minutes * 60_000L,
                title = "待办提醒：${todo.title}",
                text = buildString {
                    append("截止时间 ")
                    append(formattedClock(todo.dueAtEpochMillis, zone))
                    todo.note?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                key = "todo:${todo.id}"
            )
        }
        .sortedBy { it.triggerAtMillis }

    private fun taskReminders(
        nowMillis: Long,
        zone: ZoneId,
        tasks: List<com.example.myfirstapp.data.local.TaskEntity>,
        completions: List<com.example.myfirstapp.data.local.TaskCompletionEntity>
    ): List<ReminderItem> {
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        return tasks
            .filter { it.reminderOn && it.hasDate }
            .mapNotNull { task ->
                var candidate = nextTriggerMillis(now, zone, task) ?: return@mapNotNull null
                var attempts = 0
                fun completed(at: Long): Boolean = completions.any {
                    it.taskId == task.id && it.epochDay == java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
                }
                while (completed(candidate) && attempts++ < 400) {
                    candidate = nextTriggerMillis(java.time.Instant.ofEpochMilli(candidate).atZone(zone), zone, task)
                        ?: return@mapNotNull null
                }
                if (completed(candidate)) return@mapNotNull null
                val trigger = candidate
                ReminderItem(
                    stream = ReminderStream.TASK,
                    triggerAtMillis = trigger,
                    title = "任务提醒：${task.title}",
                    text = buildString {
                        append("时间 ")
                        append(formattedClock(trigger, zone))
                        task.note?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    key = "task:${task.id}:${java.time.Instant.ofEpochMilli(trigger).atZone(zone).toLocalDate().toEpochDay()}"
                )
            }
            .sortedBy { it.triggerAtMillis }
    }

    private fun nextTriggerMillis(
        now: java.time.ZonedDateTime,
        zone: ZoneId,
        task: com.example.myfirstapp.data.local.TaskEntity
    ): Long? {
        val nowMs = now.toInstant().toEpochMilli()
        val anchor = java.time.Instant.ofEpochMilli(task.anchorAtEpochMillis).atZone(zone)
        return when (task.repeatType) {
            TASK_REPEAT_ONCE -> {
                val m = anchor.toInstant().toEpochMilli()
                if (m > nowMs) m else null
            }
            TASK_REPEAT_DAILY -> {
                val time = anchor.toLocalTime()
                var candidate = maxOf(now.toLocalDate(), anchor.toLocalDate()).atTime(time).atZone(zone)
                var guard = 0
                while (candidate.toInstant().toEpochMilli() <= nowMs && guard < 5) {
                    candidate = candidate.plusDays(1)
                    guard++
                }
                if (candidate.toInstant().toEpochMilli() > nowMs) candidate.toInstant().toEpochMilli() else null
            }
            TASK_REPEAT_WEEKLY -> {
                val time = anchor.toLocalTime()
                val dow = anchor.dayOfWeek
                var candidate = maxOf(now.toLocalDate(), anchor.toLocalDate()).atTime(time).atZone(zone)
                var guard = 0
                while ((candidate.dayOfWeek != dow || candidate.toInstant().toEpochMilli() <= nowMs) && guard < 8) {
                    candidate = candidate.plusDays(1)
                    guard++
                }
                if (candidate.dayOfWeek == dow && candidate.toInstant().toEpochMilli() > nowMs) {
                    candidate.toInstant().toEpochMilli()
                } else null
            }
            else -> null
        }
    }

    fun formattedClock(millis: Long, zone: ZoneId): String {
        val time = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return TimeText.formatMinute(time.hour * 60 + time.minute)
    }

    private fun parseReminderMinutes(custom: Int?, defaultReminderMinutes: Int): Int =
        (custom ?: defaultReminderMinutes).coerceAtLeast(0)

    private data class ReminderItem(
        val stream: ReminderStream,
        val triggerAtMillis: Long,
        val title: String,
        val text: String,
        val key: String
    ) {
        fun toDueReminder(): DueReminder =
            DueReminder(stream = stream, title = title, text = text, key = key)

        fun toPlanned(): PlannedReminder =
            PlannedReminder(
                stream = stream,
                key = key,
                triggerAtMillis = triggerAtMillis,
                title = title,
                text = text
            )
    }
}
