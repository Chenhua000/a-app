package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.ReminderRefresher
import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.TodoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TodoRepository(
    private val database: AppDatabase,
    private val reminderRefresher: ReminderRefresher,
    private val ioDispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.IO
) {
    fun observeTodos(): Flow<List<TodoEntity>> = database.todoDao().observeTodos()

    suspend fun saveTodo(entity: TodoEntity) = withContext(ioDispatcher) {
        if (entity.id == 0L) database.todoDao().insert(entity) else database.todoDao().update(entity)
        reminderRefresher.refresh()
    }

    suspend fun setCompleted(id: Long, completed: Boolean) = withContext(ioDispatcher) {
        database.todoDao().setCompleted(id, completed)
        reminderRefresher.refresh()
    }

    suspend fun deleteTodo(entity: TodoEntity) = withContext(ioDispatcher) {
        database.todoDao().delete(entity)
        reminderRefresher.refresh()
    }
}
