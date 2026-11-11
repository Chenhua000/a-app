package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.ReminderRefresher
import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.CourseEntity
import com.example.myfirstapp.data.local.CourseExceptionEntity
import com.example.myfirstapp.domain.ExceptionType
import com.example.myfirstapp.domain.ScheduleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class CourseRepository(
    private val database: AppDatabase,
    private val reminderRefresher: ReminderRefresher,
    private val ioDispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.IO
) {
    fun observeSnapshot(): Flow<ScheduleSnapshot> = combine(
        database.courseDao().observeCourses(),
        database.courseDao().observeExceptions()
    ) { courses, exceptions -> ScheduleSnapshot(courses, exceptions) }

    suspend fun saveCourse(entity: CourseEntity) = withContext(ioDispatcher) {
        if (entity.id == 0L) database.courseDao().insert(entity) else database.courseDao().update(entity)
        reminderRefresher.refresh()
    }

    suspend fun deleteCourse(entity: CourseEntity) = withContext(ioDispatcher) {
        database.courseDao().delete(entity)
        reminderRefresher.refresh()
    }

    suspend fun saveException(
        courseId: Long,
        epochDay: Long,
        type: ExceptionType,
        newStartAt: Long?,
        newEndAt: Long?
    ) = withContext(ioDispatcher) {
        val existing = database.courseDao().getException(courseId, epochDay)
        val entity = CourseExceptionEntity(
            id = existing?.id ?: 0L,
            courseId = courseId,
            originalDateEpochDay = epochDay,
            type = type,
            newStartAtEpochMillis = newStartAt,
            newEndAtEpochMillis = newEndAt
        )
        database.courseDao().upsertException(entity)
        reminderRefresher.refresh()
    }

    suspend fun clearException(exception: CourseExceptionEntity) = withContext(ioDispatcher) {
        database.courseDao().deleteException(exception)
        reminderRefresher.refresh()
    }

    suspend fun getException(courseId: Long, epochDay: Long): CourseExceptionEntity? =
        withContext(ioDispatcher) { database.courseDao().getException(courseId, epochDay) }
}
