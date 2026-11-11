package com.example.myfirstapp.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.myfirstapp.data.local.ScheduledAlarmDao
import com.example.myfirstapp.data.local.ScheduledAlarmEntity
import com.example.myfirstapp.domain.PlannedReminder
import com.example.myfirstapp.domain.ReminderStream

interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    /** 用给定的提醒清单整体替换系统闹钟（按 key 精确增删）。 */
    suspend fun reschedule(planned: List<PlannedReminder>)
    suspend fun cancelAll()
}

object ReminderIntents {
    const val ACTION_REMINDER = "com.example.myfirstapp.ACTION_REMINDER"
    const val EXTRA_STREAM = "extra_stream"
    const val EXTRA_KEY = "extra_key"

    fun createIntent(context: Context, stream: ReminderStream, key: String? = null): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_STREAM, stream.storageName)
            if (key != null) putExtra(EXTRA_KEY, key)
        }
}

class AndroidAlarmScheduler(
    private val context: Context,
    private val scheduledAlarmDao: ScheduledAlarmDao
) : AlarmScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    override suspend fun reschedule(planned: List<PlannedReminder>) {
        cancelLegacyStreamAlarms()

        val plannedByKey = planned.associateBy { it.key }
        val existing = scheduledAlarmDao.list()

        existing.forEach { old ->
            val target = plannedByKey[old.key]
            if (target == null || target.triggerAtMillis != old.triggerAtMillis) {
                val stream = ReminderStream.from(old.streamName) ?: ReminderStream.COURSE
                cancelPending(old.requestCode, stream, old.key)
                scheduledAlarmDao.delete(old.key)
            }
        }

        planned.forEach { item ->
            val alreadyScheduled = existing.any {
                it.key == item.key && it.triggerAtMillis == item.triggerAtMillis
            }
            if (alreadyScheduled) return@forEach
            val requestCode = requestCodeFor(item.key)
            if (scheduleAlarm(item, requestCode)) {
                scheduledAlarmDao.upsert(
                    ScheduledAlarmEntity(
                        key = item.key,
                        streamName = item.stream.storageName,
                        triggerAtMillis = item.triggerAtMillis,
                        requestCode = requestCode
                    )
                )
            }
        }
    }

    override suspend fun cancelAll() {
        scheduledAlarmDao.list().forEach { old ->
            val stream = ReminderStream.from(old.streamName) ?: ReminderStream.COURSE
            cancelPending(old.requestCode, stream, old.key)
        }
        scheduledAlarmDao.clear()
        cancelLegacyStreamAlarms()
    }

    private fun cancelLegacyStreamAlarms() {
        ReminderStream.entries.forEach { stream ->
            cancelPending(LEGACY_BASE + stream.ordinal, stream, null)
        }
    }

    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun noCreateFlags() = PendingIntent.FLAG_NO_CREATE or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun requestCodeFor(key: String): Int =
        REQUEST_CODE_BASE + (key.hashCode() and Int.MAX_VALUE) % REQUEST_CODE_RANGE

    private fun scheduleAlarm(item: PlannedReminder, requestCode: Int): Boolean {
        val intent = ReminderIntents.createIntent(context, item.stream, item.key)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingFlags())
        return try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerAtMillis,
                    pendingIntent
                )
            }
            true
        } catch (security: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerAtMillis,
                    pendingIntent
                )
                true
            } catch (ignored: SecurityException) {
                false
            }
        } catch (illegal: IllegalArgumentException) {
            false
        }
    }

    private fun cancelPending(requestCode: Int, stream: ReminderStream, key: String?) {
        val intent = ReminderIntents.createIntent(context, stream, key)
        val existing = PendingIntent.getBroadcast(context, requestCode, intent, noCreateFlags())
        existing?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        private const val LEGACY_BASE = 1000
        private const val REQUEST_CODE_BASE = 2_000_000
        private const val REQUEST_CODE_RANGE = 800_000
    }
}
