package com.example.myfirstapp.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.CourseExceptionEntity
import com.example.myfirstapp.data.local.TimeSlotEntity
import com.example.myfirstapp.data.settings.RowHeightMode
import com.example.myfirstapp.domain.CoursePlacement
import com.example.myfirstapp.domain.PlacedOccurrence
import com.example.myfirstapp.domain.ScheduleResolver
import com.example.myfirstapp.domain.ExceptionType
import com.example.myfirstapp.domain.TimeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class ScheduleMessage(val text: String, val token: Long = System.currentTimeMillis())

sealed class ValidationResult {
    data object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

data class CourseDraft(
    val id: Long? = null,
    val name: String = "",
    val dayOfWeek: Int = 1,
    val slotId: Long? = null,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 8 * 60 + 45,
    val location: String = "",
    val teacher: String = "",
    val reminderText: String = "",
    val colorIndex: Int = 0,
    /** 是否启用；编辑已有课程时要保留，别被默认值重置 */
    val enabled: Boolean = true
)

data class ScheduleUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val visibleDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val slots: List<TimeSlotEntity> = emptyList(),
    val placed: List<PlacedOccurrence> = emptyList(),
    /** 原始课程数据，编辑时按 id 取真实实体，避免从展示用的 occurrence 反推丢字段 */
    val courses: List<CourseEntity> = emptyList()
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyFirstApp
    private val repository = app.container.courseRepository
    private val slotRepository = app.container.slotRepository
    private val settingsRepository = app.container.settingsRepository
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _weekStart = MutableStateFlow(TimeText.startOfWeek(LocalDate.now(zone)))
    val weekStart: StateFlow<LocalDate> = _weekStart

    private val _message = MutableStateFlow<ScheduleMessage?>(null)
    val message: StateFlow<ScheduleMessage?> = _message

    val slots: StateFlow<List<TimeSlotEntity>> = slotRepository.observeSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rowHeightMode: StateFlow<RowHeightMode> = settingsRepository.rowHeightMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RowHeightMode.STANDARD)

    val showWeekend: StateFlow<Boolean> = settingsRepository.showWeekend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.observeSnapshot(),
        slots,
        _weekStart,
        showWeekend
    ) { snapshot, slotList, week, weekend ->
        val days = if (weekend) listOf(1, 2, 3, 4, 5, 6, 7) else listOf(1, 2, 3, 4, 5)
        val occurrences = if (slotList.isEmpty() || snapshot.courses.isEmpty()) {
            emptyList()
        } else {
            ScheduleResolver.resolveRange(
                startDate = week,
                endDate = week.plusDays(6),
                courses = snapshot.courses,
                exceptions = snapshot.exceptions,
                zone = zone
            )
        }
        val placed = if (occurrences.isEmpty()) {
            emptyList()
        } else {
            CoursePlacement.place(
                occurrences = occurrences,
                coursesById = snapshot.courses.associateBy { it.id },
                slots = slotList,
                zone = zone
            ).filter { it.dayOfWeek in days }
        }
        ScheduleUiState(
            weekStart = week,
            today = LocalDate.now(zone),
            visibleDays = days,
            slots = slotList,
            placed = placed,
            courses = snapshot.courses
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScheduleUiState()
    )

    fun moveWeek(weeks: Long) {
        _weekStart.value = _weekStart.value.plusWeeks(weeks)
    }

    fun goCurrentWeek() {
        _weekStart.value = TimeText.startOfWeek(LocalDate.now(zone))
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun saveCourse(draft: CourseDraft): ValidationResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return ValidationResult.Error("请填写课程名称")
        if (draft.dayOfWeek !in 1..7) return ValidationResult.Error("请选择星期")
        if (draft.endMinute <= draft.startMinute) return ValidationResult.Error("结束时间必须晚于开始时间")
        val reminder = when (val input = parseReminder(draft.reminderText)) {
            ReminderInput.Invalid -> return ValidationResult.Error(
                "提前提醒必须是 0 到 240 之间的整数，留空跟随默认"
            )
            is ReminderInput.Minutes -> input.value
            ReminderInput.FollowDefault -> null
        }
        val entity = CourseEntity(
            id = draft.id ?: 0L,
            name = name,
            dayOfWeek = draft.dayOfWeek,
            startMinute = draft.startMinute,
            endMinute = draft.endMinute,
            location = draft.location.trim().takeIf { it.isNotEmpty() },
            teacher = draft.teacher.trim().takeIf { it.isNotEmpty() },
            reminderMinutes = reminder,
            enabled = draft.enabled,
            slotId = draft.slotId,
            colorIndex = draft.colorIndex
        )
        viewModelScope.launch {
            runCatching { repository.saveCourse(entity) }
                .onFailure { _message.value = ScheduleMessage("保存失败：${it.localizedMessage}") }
                .onSuccess { _message.value = ScheduleMessage("课程已保存，提醒已同步更新") }
        }
        return ValidationResult.Success
    }

    fun deleteCourse(entity: CourseEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteCourse(entity) }
                .onFailure { _message.value = ScheduleMessage("删除失败：${it.localizedMessage}") }
                .onSuccess { _message.value = ScheduleMessage("课程已删除") }
        }
    }

    fun cancelOccurrence(courseId: Long, epochDay: Long) {
        viewModelScope.launch {
            runCatching { repository.saveException(courseId, epochDay, ExceptionType.CANCELLED, null, null) }
                .onFailure { _message.value = ScheduleMessage("停课失败：${it.localizedMessage}") }
                .onSuccess { _message.value = ScheduleMessage("已设置本次停课") }
        }
    }

    fun rescheduleOccurrence(
        courseId: Long,
        epochDay: Long,
        newDateText: String,
        startTimeText: String,
        endTimeText: String
    ): ValidationResult {
        val date = TimeText.parseDate(newDateText)
            ?: return ValidationResult.Error("日期格式应为 yyyy-MM-dd，例如 2026-10-27")
        val startMinute = TimeText.parseTimeMinute(startTimeText)
            ?: return ValidationResult.Error("新的开始时间格式应为 HH:mm")
        val endMinute = TimeText.parseTimeMinute(endTimeText)
            ?: return ValidationResult.Error("新的结束时间格式应为 HH:mm")
        if (endMinute <= startMinute) return ValidationResult.Error("结束时间必须晚于开始时间")
        val startMillis = TimeText.parseDateTimeToMillis(date, startMinute, zone)
        val endMillis = TimeText.parseDateTimeToMillis(date, endMinute, zone)
        viewModelScope.launch {
            runCatching {
                repository.saveException(courseId, epochDay, ExceptionType.RESCHEDULED, startMillis, endMillis)
            }
                .onFailure { _message.value = ScheduleMessage("调课失败：${it.localizedMessage}") }
                .onSuccess { _message.value = ScheduleMessage("已设置本次调课") }
        }
        return ValidationResult.Success
    }

    fun clearException(exception: CourseExceptionEntity) {
        viewModelScope.launch {
            runCatching { repository.clearException(exception) }
                .onFailure { _message.value = ScheduleMessage("取消失败：${it.localizedMessage}") }
                .onSuccess { _message.value = ScheduleMessage("已恢复原来的每周课程") }
        }
    }

    fun suggestedColorIndex(): Int = (0..7).random()

    /** 解析提前提醒输入：留空表示跟随默认 */
    private fun parseReminder(text: String): ReminderInput {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ReminderInput.FollowDefault
        val value = trimmed.toIntOrNull() ?: return ReminderInput.Invalid
        return if (value in 0..240) ReminderInput.Minutes(value) else ReminderInput.Invalid
    }

    private sealed interface ReminderInput {
        data object FollowDefault : ReminderInput
        data class Minutes(val value: Int) : ReminderInput
        data object Invalid : ReminderInput
    }
}
