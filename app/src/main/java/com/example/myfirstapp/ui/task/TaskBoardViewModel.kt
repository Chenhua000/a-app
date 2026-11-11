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

class TaskBoardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TaskRepository =
        (application as MyFirstApp).container.taskRepository
    private val reminderCoordinator = (application as MyFirstApp).container.reminderCoordinator

    val tasks: StateFlow<List<TaskEntity>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completions: StateFlow<List<com.example.myfirstapp.data.local.TaskCompletionEntity>> =
        repository.observeCompletions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(task: TaskEntity) {
        viewModelScope.launch {
            repository.save(task)
            if (task.reminderOn) reminderCoordinator.refresh()
        }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { repository.delete(task) }
    }

    fun setDone(taskId: Long, epochDay: Long, done: Boolean) {
        viewModelScope.launch { repository.setCompletion(taskId, epochDay, done) }
    }

    /** 置顶/取消置顶：改字段后整体保存 */
    fun setPinned(task: TaskEntity, pinned: Boolean) {
        if (task.pinned == pinned) return
        viewModelScope.launch { repository.save(task.copy(pinned = pinned)) }
    }

    /** 开/关定时提醒：改字段后保存，并立即重排系统闹钟 */
    fun setReminderOn(task: TaskEntity, on: Boolean) {
        if (task.reminderOn == on) return
        viewModelScope.launch {
            repository.save(task.copy(reminderOn = on))
            reminderCoordinator.refresh()
        }
    }
}
