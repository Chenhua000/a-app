package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 任务时长类型 */
const val TASK_DURATION_SHORT = 0
const val TASK_DURATION_LONG = 1

/** 任务重复方式 */
const val TASK_REPEAT_ONCE = 0
const val TASK_REPEAT_DAILY = 1
const val TASK_REPEAT_WEEKLY = 2

/** 这条任务在给定的日期是否「出现」（即需要被安排/勾选） */
fun TaskEntity.occursOn(date: LocalDate, zone: ZoneId): Boolean {
    val anchorDate = Instant.ofEpochMilli(anchorAtEpochMillis).atZone(zone).toLocalDate()
    // 重复任务只在开始日期之后出现，不追溯创建之前的日期（否则本周视图会把任务显示在过去的几天上）
    if (date.isBefore(anchorDate)) return false
    return when (repeatType) {
        TASK_REPEAT_ONCE -> anchorDate == date
        TASK_REPEAT_DAILY -> true
        TASK_REPEAT_WEEKLY -> anchorDate.dayOfWeek == date.dayOfWeek
        else -> false
    }
}

/** 这条任务的「当天时间」（分钟），用于排序与展示 */
fun TaskEntity.timeMinute(zone: ZoneId): Int {
    val t = Instant.ofEpochMilli(anchorAtEpochMillis).atZone(zone).toLocalTime()
    return t.hour * 60 + t.minute
}

/** 重复方式的简短中文标签 */
fun repeatLabel(repeatType: Int): String = when (repeatType) {
    TASK_REPEAT_DAILY -> "每天"
    TASK_REPEAT_WEEKLY -> "每周"
    else -> "一次性"
}

/** 时长类型的中文标签 */
fun durationLabel(durationType: Int): String =
    if (durationType == TASK_DURATION_LONG) "长任务" else "短任务"
