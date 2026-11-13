package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.TaskCompletionEntity
import com.example.myfirstapp.data.local.TaskEntity
import androidx.room.withTransaction
import com.example.myfirstapp.domain.TASK_REPEAT_ONCE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TaskRepository(
    private val database: AppDatabase,
    private val ioDispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.IO
) {
    fun observeTasks(): Flow<List<TaskEntity>> = database.taskDao().observeTasks()

    fun observeCompletions(): Flow<List<TaskCompletionEntity>> =
        database.taskDao().observeCompletions()

    suspend fun save(task: TaskEntity) = withContext(ioDispatcher) {
        if (task.id == 0L) database.taskDao().insert(task)
        else database.taskDao().update(task)
    }

    suspend fun delete(task: TaskEntity) = withContext(ioDispatcher) {
        database.withTransaction {
            database.taskDao().delete(task)
            database.taskDao().deleteCompletions(task.id)
        }
    }

    /** 切换某条任务在某一天是否完成 */
    suspend fun setCompletion(taskId: Long, epochDay: Long, done: Boolean) =
        withContext(ioDispatcher) {
            database.withTransaction {
            val dao = database.taskDao()
            val task = dao.get(taskId) ?: return@withTransaction
            // One-shot completion belongs to the task, even after moving its date.
            if (task.repeatType == TASK_REPEAT_ONCE) dao.deleteCompletions(taskId)
            if (done) {
                dao.upsertCompletion(
                    TaskCompletionEntity(
                        taskId = taskId,
                        epochDay = epochDay,
                        completedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            } else {
                dao.deleteCompletion(taskId, epochDay)
            }
            }
        }
}
