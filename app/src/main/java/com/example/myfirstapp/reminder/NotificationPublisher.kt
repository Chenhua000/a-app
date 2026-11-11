package com.example.myfirstapp.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myfirstapp.MainActivity
import com.example.myfirstapp.R

class NotificationPublisher(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    fun areNotificationsEnabled(): Boolean = notificationManager.areNotificationsEnabled()

    fun show(key: String, title: String, text: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_TARGET, key)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(context, key.hashCode(), intent, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val safeId = key.hashCode() and Int.MAX_VALUE
        notificationManager.notify(safeId, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = CHANNEL_DESCRIPTION }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "campus_reminders"
        const val CHANNEL_NAME = "课程与待办提醒"
        const val CHANNEL_DESCRIPTION = "上课提醒、待办提醒和记账以外的计划通知"
    }
}
