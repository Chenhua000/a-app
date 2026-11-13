package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

/** Builds an edit without losing identity, pin state or creation time. */
fun buildTaskEdit(
    original: TaskEntity?, title: String, note: String, date: LocalDate?, time: String,
    durationType: Int, repeatType: Int, reminderOn: Boolean, zone: ZoneId,
    now: Long = System.currentTimeMillis()
): Pair<TaskEntity?, String?> {
    if (title.isBlank()) return null to "请填写任务标题"
    val minute = if (date == null) 0 else TimeText.parseTimeMinute(time)
        ?: return null to "时间格式应为 HH:mm，例如 18:30"
    val anchor = date?.let { TimeText.parseDateTimeToMillis(it, minute, zone) }
        ?: original?.anchorAtEpochMillis ?: now
    val repeat = if (date == null) TASK_REPEAT_ONCE else repeatType
    val remind = reminderOn && date != null
    val changedReminder = original == null || !original.reminderOn || original.anchorAtEpochMillis != anchor
    if (remind && repeat == TASK_REPEAT_ONCE && anchor <= now && changedReminder) {
        return null to "提醒时间已过去，请选择未来时间，或关闭提醒"
    }
    return (original ?: TaskEntity(title = title, createdAtMillis = now)).copy(
        title = title.trim(), note = note.trim().takeIf { it.isNotEmpty() },
        durationType = durationType, repeatType = repeat, anchorAtEpochMillis = anchor,
        reminderOn = remind, hasDate = date != null
    ) to null
}
