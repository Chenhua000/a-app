package com.example.myfirstapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startMinute")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourse(id: Long): CourseEntity?

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("SELECT * FROM course_exceptions")
    fun observeExceptions(): Flow<List<CourseExceptionEntity>>

    @Query("SELECT * FROM course_exceptions WHERE courseId = :courseId")
    suspend fun getExceptions(courseId: Long): List<CourseExceptionEntity>

    @Query(
        "SELECT * FROM course_exceptions WHERE courseId = :courseId " +
            "AND originalDateEpochDay = :epochDay LIMIT 1"
    )
    suspend fun getException(courseId: Long, epochDay: Long): CourseExceptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertException(exception: CourseExceptionEntity)

    @Delete
    suspend fun deleteException(exception: CourseExceptionEntity)
}

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM time_slots ORDER BY sortIndex")
    fun observeSlots(): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slots ORDER BY sortIndex")
    suspend fun listSlots(): List<TimeSlotEntity>

    @Query("SELECT * FROM time_slots WHERE id = :id")
    suspend fun getSlot(id: Long): TimeSlotEntity?

    @Insert
    suspend fun insert(slot: TimeSlotEntity): Long

    @Update
    suspend fun update(slot: TimeSlotEntity)

    @Delete
    suspend fun delete(slot: TimeSlotEntity)

    @Query("SELECT COUNT(*) FROM time_slots")
    suspend fun count(): Int
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY dueAtEpochMillis")
    fun observeTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getTodo(id: Long): TodoEntity?

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("UPDATE todos SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM ledger_transactions ORDER BY occurredAtEpochMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM ledger_transactions " +
            "WHERE occurredAtEpochMillis >= :fromInclusive AND occurredAtEpochMillis < :toExclusive " +
            "ORDER BY occurredAtEpochMillis DESC"
    )
    fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM ledger_transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM ledger_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NotifiedReminderDao {
    @Query("SELECT * FROM notified_reminders WHERE key = :key")
    suspend fun get(key: String): NotifiedReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NotifiedReminderEntity)

    @Query("DELETE FROM notified_reminders WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM notified_reminders WHERE sentAtEpochMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}

@Dao
interface BookSourceDao {
    @Query("SELECT * FROM book_sources ORDER BY sortOrder, name")
    fun observeSources(): Flow<List<BookSourceEntity>>

    @Query("SELECT * FROM book_sources ORDER BY sortOrder, name")
    suspend fun listSources(): List<BookSourceEntity>

    @Query("SELECT * FROM book_sources WHERE id = :id")
    suspend fun getSource(id: String): BookSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: BookSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sources: List<BookSourceEntity>)

    @Update
    suspend fun update(source: BookSourceEntity)

    @Delete
    suspend fun delete(source: BookSourceEntity)

    @Query("DELETE FROM book_sources WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 批量启用 / 停用（书源管理器里的「全选 → 启用」） */
    @Query("UPDATE book_sources SET enabled = :enabled WHERE id IN (:ids)")
    suspend fun setEnabled(ids: List<String>, enabled: Boolean)

    /** 批量删除 */
    @Query("DELETE FROM book_sources WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** 写入自检结果（书源自检：能搜到书 / 搜不到 / 连不上） */
    @Query("UPDATE book_sources SET lastStatus = :status, lastTestedAt = :testedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String?, testedAt: Long)

    @Query("SELECT COUNT(*) FROM book_sources")
    suspend fun count(): Int
}

@Dao
interface ShelfBookDao {
    @Query("SELECT * FROM shelf_books ORDER BY lastReadAtMillis DESC")
    fun observeBooks(): Flow<List<ShelfBookEntity>>

    @Query("SELECT * FROM shelf_books WHERE id = :id")
    suspend fun getBook(id: Long): ShelfBookEntity?

    @Query("SELECT * FROM shelf_books WHERE bookUrl = :url LIMIT 1")
    suspend fun getByUrl(url: String): ShelfBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: ShelfBookEntity): Long

    @Delete
    suspend fun delete(book: ShelfBookEntity)

    @Query("SELECT COUNT(*) FROM shelf_books WHERE bookUrl = :url")
    suspend fun exists(url: String): Int

    @Query(
        "UPDATE shelf_books SET lastChapterIndex = :index, lastChapterTitle = :title, " +
            "lastReadAtMillis = :at WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, index: Int, title: String?, at: Long)
}

@Dao
interface ChapterIndexDao {
    @Query("SELECT * FROM chapter_index WHERE bookUrl = :bookUrl ORDER BY position")
    fun observeChapters(bookUrl: String): Flow<List<ChapterIndexEntity>>

    @Query("SELECT * FROM chapter_index WHERE bookUrl = :bookUrl ORDER BY position")
    suspend fun listChapters(bookUrl: String): List<ChapterIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChapterIndexEntity>)

    @Query("DELETE FROM chapter_index WHERE bookUrl = :bookUrl")
    suspend fun clear(bookUrl: String)

    /** 内置书源规则升级后，旧目录一律作废，让它们重新抓一次 */
    @Query("DELETE FROM chapter_index")
    suspend fun clearAll()
}

@Dao
interface ChapterCacheDao {
    @Query("SELECT * FROM chapter_cache WHERE url = :url")
    suspend fun get(url: String): ChapterCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChapterCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChapterCacheEntity>)

    /** 本地导入的书（local://）不是缓存，永远不能被清理掉 */
    @Query("DELETE FROM chapter_cache WHERE savedAtMillis < :beforeMillis AND url NOT LIKE 'local://%'")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM chapter_cache WHERE bookUrl = :bookUrl")
    suspend fun clearBook(bookUrl: String)
}

@Dao
interface ScheduledAlarmDao {
    @Query("SELECT * FROM scheduled_alarms")
    suspend fun list(): List<ScheduledAlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduledAlarmEntity)

    @Query("DELETE FROM scheduled_alarms WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM scheduled_alarms")
    suspend fun clear()
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeAll(): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): IdeaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(idea: IdeaEntity): Long

    @Update
    suspend fun update(idea: IdeaEntity)

    @Delete
    suspend fun delete(idea: IdeaEntity)

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM planner_tasks ORDER BY createdAtMillis DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM planner_tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM planner_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM task_completions")
    fun observeCompletions(): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun getCompletion(taskId: Long, epochDay: Long): TaskCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: TaskCompletionEntity)

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun deleteCompletion(taskId: Long, epochDay: Long)

    /** 清理 60 天前的完成记录，避免表无限增长 */
    @Query("DELETE FROM task_completions WHERE epochDay < :beforeEpochDay")
    suspend fun deleteOldCompletions(beforeEpochDay: Long)
}
