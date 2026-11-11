package com.example.myfirstapp.ui.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.TodoEntity
import com.example.myfirstapp.domain.TimeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TodoMessage(val text: String, val token: Long = System.currentTimeMillis())

sealed class TodoValidation {
    object Success : TodoValidation()
    data class Error(val message: String) : TodoValidation()
}

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyFirstApp
    private val repository = app.container.todoRepository

    val todos: StateFlow<List<TodoEntity>> = repository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<TodoMessage?>(null)
    val message: StateFlow<TodoMessage?> = _message

    fun saveTodo(
        id: Long?,
        title: String,
        note: String,
        dateText: String,
        timeText: String,
        reminderText: String
    ): TodoValidation {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return TodoValidation.Error("请填写待办标题")
        val date = TimeText.parseDate(dateText)
            ?: return TodoValidation.Error("日期格式应为 yyyy-MM-dd")
        val minute = TimeText.parseTimeMinute(timeText)
            ?: return TodoValidation.Error("时间格式应为 HH:mm")
        val reminderMinutes = run {
            val trimmed = reminderText.trim()
            if (trimmed.isEmpty()) {
                null // 留空 = 跟随默认
            } else {
                val v = trimmed.toIntOrNull()
                if (v == null || v !in 0..240) {
                    return TodoValidation.Error("提前提醒必须是 0 到 240 之间的整数，留空表示跟随默认")
                }
                v
            }
        }
        val zone = ZoneId.systemDefault()
        val dueAt = TimeText.parseDateTimeToMillis(date, minute, zone)
        val entity = TodoEntity(
            id = id ?: 0L,
            title = trimmedTitle,
            note = note.trim().takeIf { it.isNotEmpty() },
            dueAtEpochMillis = dueAt,
            reminderMinutes = reminderMinutes
        )
        viewModelScope.launch {
            runCatching { repository.saveTodo(entity) }
                .onFailure { _message.value = TodoMessage("保存失败：${it.localizedMessage}") }
                .onSuccess { _message.value = TodoMessage("待办已保存，提醒已更新") }
        }
        return TodoValidation.Success
    }

    fun setCompleted(todo: TodoEntity, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompleted(todo.id, completed) }
                .onFailure { _message.value = TodoMessage("更新失败：${it.localizedMessage}") }
                .onSuccess {
                    _message.value = TodoMessage(
                        if (completed) "已完成，将不再提醒" else "已恢复未完成状态"
                    )
                }
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteTodo(todo) }
                .onFailure { _message.value = TodoMessage("删除失败：${it.localizedMessage}") }
                .onSuccess { _message.value = TodoMessage("待办已删除") }
        }
    }

    fun defaultDateText(): String = TimeText.formatDate(LocalDate.now())
}
