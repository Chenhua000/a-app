package com.example.myfirstapp.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.domain.ReminderStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟触发 / 周期校对入口。
 *
 * 原先这里转一道 WorkManager（ReminderWorker / RescheduleWorker）。但在低端设备上，
 * WorkManager 初始化与调度会把 SystemJobService.onStartJob 拖到 10 秒超时，
 * 系统直接弹「尘剑 isn't responding」（Reason: No response to onStartJob）。
 * 现在改成 goAsync + 协程直接调用 coordinator，逻辑与原来的 Worker 完全一致，
 * 只是去掉了 WorkManager 这层调度。
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MyFirstApp ?: return
        val coordinator = app.container.reminderCoordinator
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == ReminderIntents.ACTION_REMINDER) {
                    val stream = ReminderStream.from(
                        intent.getStringExtra(ReminderIntents.EXTRA_STREAM)
                    )
                    if (stream != null) {
                        coordinator.handleFired(
                            stream,
                            intent.getStringExtra(ReminderIntents.EXTRA_KEY)
                        )
                    }
                } else {
                    // 周期校对：重新排一遍未过期的提醒
                    coordinator.refresh()
                }
            } catch (t: Throwable) {
                android.util.Log.w("ReminderReceiver", "处理提醒失败：${t.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
