package com.example.myfirstapp.ui.task

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.TaskEntity
import com.example.myfirstapp.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException

class TaskBoardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TaskRepository =
        (application as MyFirstApp).container.taskRepository
    private val reminderCoordinator = (application as MyFirstApp).container.reminderCoordinator

    val tasks: StateFlow<List<TaskEntity>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completions: StateFlow<List<com.example.myfirstapp.data.local.TaskCompletionEntity>> =
        repository.observeCompletions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }
    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _message.value = "操作未完成：${error.localizedMessage}" }
        }
    }
    private suspend fun refreshReminders() {
        try { reminderCoordinator.refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _message.value = "任务已保存，但提醒同步失败，请重新打开应用后检查" }
    }

    fun save(task: TaskEntity, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.save(task)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onResult("保存失败：${error.localizedMessage}"); return@launch }
            onResult(null)
            refreshReminders()
        }
    }

    fun delete(task: TaskEntity) {
        mutate { repository.delete(task); refreshReminders() }
    }

    fun setDone(taskId: Long, epochDay: Long, done: Boolean) {
        mutate {
            val task = tasks.value.firstOrNull { it.id == taskId }
            val completionDay = if (task != null && task.hasDate && task.repeatType == 0) {
                java.time.Instant.ofEpochMilli(task.anchorAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            } else epochDay
            repository.setCompletion(taskId, completionDay, done)
            refreshReminders()
        }
    }

    /** 置顶/取消置顶：改字段后整体保存 */
    fun setPinned(task: TaskEntity, pinned: Boolean) {
        if (task.pinned == pinned) return
        mutate { repository.save(task.copy(pinned = pinned)) }
    }

    /** 开/关定时提醒：改字段后保存，并立即重排系统闹钟 */
    fun setReminderOn(task: TaskEntity, on: Boolean) {
        if (task.reminderOn == on || !task.hasDate) return
        mutate {
            repository.save(task.copy(reminderOn = on))
            refreshReminders()
        }
    }
}
