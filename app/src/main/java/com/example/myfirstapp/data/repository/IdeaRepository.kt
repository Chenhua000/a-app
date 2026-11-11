package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.IdeaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IdeaRepository(
    private val database: AppDatabase,
    private val ioDispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.IO
) {
    fun observeIdeas(): Flow<List<IdeaEntity>> = database.ideaDao().observeAll()

    suspend fun getIdea(id: Long): IdeaEntity? = withContext(ioDispatcher) {
        database.ideaDao().get(id)
    }

    /**
     * 保存一条灵感。借鉴 Simplenote/Bear 的「边写边存」：调用方每次改动都直接落库，
     * 无需显式「保存」按钮。id == 0 时插入并返回新 id，否则更新。
     */
    suspend fun save(idea: IdeaEntity): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val toSave = if (idea.id == 0L) idea.copy(createdAtMillis = now) else idea
        val withStamp = toSave.copy(updatedAtMillis = now)
        if (withStamp.id == 0L) database.ideaDao().insert(withStamp)
        else {
            database.ideaDao().update(withStamp)
            withStamp.id
        }
    }

    suspend fun delete(idea: IdeaEntity) = withContext(ioDispatcher) {
        database.ideaDao().delete(idea)
    }

    suspend fun deleteById(id: Long) = withContext(ioDispatcher) {
        database.ideaDao().deleteById(id)
    }
}
