package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskEditingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 11, 13)
    private val now = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    @Test fun editingPreservesIdentityAndPin() {
        val original = TaskEntity(id = 18, title = "old", createdAtMillis = 1000, pinned = true)
        val (edited, error) = buildTaskEdit(original, " new ", " note ", day.plusDays(1), "10:30", TASK_DURATION_LONG, TASK_REPEAT_WEEKLY, true, zone, now)
        assertNull(error)
        assertEquals(18L, edited!!.id)
        assertEquals(1000L, edited.createdAtMillis)
        assertTrue(edited.pinned)
        assertEquals("new", edited.title)
        assertEquals("note", edited.note)
        assertEquals(day.plusDays(1).atTime(10, 30).atZone(zone).toInstant().toEpochMilli(), edited.anchorAtEpochMillis)
    }
    @Test fun undatedTaskIgnoresHiddenTimeAndDisablesReminderAndRepeat() {
        val (task, error) = buildTaskEdit(null, "task", "", null, "invalid", 0, TASK_REPEAT_DAILY, true, zone, now)
        assertNull(error)
        assertFalse(task!!.hasDate)
        assertFalse(task.reminderOn)
        assertEquals(TASK_REPEAT_ONCE, task.repeatType)
    }
    @Test fun datedTaskRejectsInvalidTime() {
        assertNull(buildTaskEdit(null,"t","",day,"25:91",0,0,false,zone,now).first)
    }
    @Test fun newOneShotReminderCannotBeInPast() {
        assertNull(buildTaskEdit(null,"t","",day,"10:00",0,0,true,zone,now).first)
    }
    @Test fun editingRecurringTaskRetainsItsPastStartDate() {
        val original = TaskEntity(id=1,title="t",repeatType=TASK_REPEAT_DAILY,reminderOn=true)
        assertNotNull(buildTaskEdit(original,"renamed","",day.minusDays(2),"10:00",0,TASK_REPEAT_DAILY,true,zone,now).first)
    }
}
