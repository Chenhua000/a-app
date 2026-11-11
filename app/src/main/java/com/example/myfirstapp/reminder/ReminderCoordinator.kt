package com.example.myfirstapp.reminder

import com.example.myfirstapp.data.ReminderRefresher
import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.NotifiedReminderEntity
import com.example.myfirstapp.data.settings.SettingsRepository
import com.example.myfirstapp.domain.PlannedReminder
import com.example.myfirstapp.domain.ReminderPlanner
import com.example.myfirstapp.domain.ReminderStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

class ReminderCoordinator(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notificationPublisher: NotificationPublisher,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : ReminderRefresher {

    private val mutex = Mutex()
    private val notifiedDao get() = database.notifiedReminderDao()

    override suspend fun refresh() {
        mutex.withLock { rescheduleAll() }
    }

    /**
     * 系统闹钟触发、应用重建或周期性校对都会走到这里。
     * key 不为空时只处理这一条提醒。
     */
    suspend fun handleFired(stream: ReminderStream, key: String?) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!settingsRepository.currentNotificationsEnabled()) {
                alarmScheduler.cancelAll()
                return
            }
            val planned = loadPlanned(now)
            val targets = when {
                key != null -> planned.filter { it.key == key }
                else -> planned.filter {
                    it.stream == stream && it.triggerAtMillis <= now + FUTURE_TOLERANCE_MILLIS
                }
            }
            targets.forEach { item ->
                if (!recentlyNotified(item.key, now)) {
                    notificationPublisher.show(item.key, item.title, item.text)
                    notifiedDao.upsert(NotifiedReminderEntity(key = item.key, sentAtEpochMillis = now))
                }
            }
            notifiedDao.deleteOlderThan(now - CLEANUP_WINDOW_MILLIS)
            rescheduleAll(now)
        }
    }

    suspend fun testNotification(title: String, text: String) {
        if (!settingsRepository.currentNotificationsEnabled()) return
        notificationPublisher.show("test:${System.currentTimeMillis()}", title, text)
    }

    private suspend fun recentlyNotified(key: String, now: Long): Boolean {
        val last = notifiedDao.get(key) ?: return false
        return now - last.sentAtEpochMillis < DUPLICATE_WINDOW_MILLIS
    }

    private suspend fun rescheduleAll(now: Long = System.currentTimeMillis()) {
        if (!settingsRepository.currentNotificationsEnabled()) {
            alarmScheduler.cancelAll()
            return
        }
        val planned = loadPlanned(now)
        alarmScheduler.reschedule(planned)
    }

    private suspend fun loadPlanned(now: Long): List<PlannedReminder> {
        val defaultMinutes = settingsRepository.currentDefaultReminderMinutes()
        val zone = zoneProvider()
        val courses = database.courseDao().observeCourses().first()
        val exceptions = database.courseDao().observeExceptions().first()
        val todos = database.todoDao().observeTodos().first()
        val tasks = database.taskDao().observeTasks().first()
        return ReminderPlanner.upcomingCourseReminders(now, zone, courses, exceptions, defaultMinutes) +
            ReminderPlanner.upcomingTodoReminders(now, todos, defaultMinutes) +
            ReminderPlanner.upcomingTaskReminders(now, zone, tasks)
    }

    companion object {
        private const val DUPLICATE_WINDOW_MILLIS = 12 * 60 * 60 * 1000L
        private const val CLEANUP_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val FUTURE_TOLERANCE_MILLIS = 60_000L
    }
}
