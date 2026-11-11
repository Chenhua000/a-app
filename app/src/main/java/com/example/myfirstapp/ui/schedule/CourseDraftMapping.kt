package com.example.myfirstapp.ui.schedule

import com.example.myfirstapp.data.local.CourseEntity

/**
 * 把数据库里的「真实课程实体」完整转成编辑草稿。
 *
 * 编辑课程时不要用展示用的 PlacedOccurrence 去反推 CourseEntity：
 * occurrence 里没有 dayOfWeek / slotId / reminderMinutes / enabled 等定义字段，
 * 反推会丢字段（历史上曾把 dayOfWeek 写死成 1），导致保存后课程从原来的星期「消失」。
 */
fun CourseEntity.toCourseDraft(): CourseDraft = CourseDraft(
    id = id,
    name = name,
    dayOfWeek = dayOfWeek,
    slotId = slotId,
    startMinute = startMinute,
    endMinute = endMinute,
    location = location.orEmpty(),
    teacher = teacher.orEmpty(),
    reminderText = reminderMinutes?.toString().orEmpty(),
    colorIndex = colorIndex,
    enabled = enabled
)
