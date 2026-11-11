package com.example.myfirstapp

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.repository.BookRepository
import com.example.myfirstapp.data.repository.CourseRepository
import com.example.myfirstapp.data.repository.IdeaRepository
import com.example.myfirstapp.data.repository.LedgerRepository
import com.example.myfirstapp.data.repository.SlotRepository
import com.example.myfirstapp.data.repository.TodoRepository
import com.example.myfirstapp.data.repository.TaskRepository
import com.example.myfirstapp.data.settings.SettingsRepository
import com.example.myfirstapp.data.shortcuts.ShortcutRepository
import com.example.myfirstapp.data.browser.BrowserRepository
import com.example.myfirstapp.data.repo.RepoRepository
import com.example.myfirstapp.reminder.AndroidAlarmScheduler
import com.example.myfirstapp.reminder.NotificationPublisher
import com.example.myfirstapp.reminder.ReminderCoordinator
import com.example.myfirstapp.reminder.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirstApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderCoordinator.let { coordinator ->
            applicationScope.launch { coordinator.refresh() }
        }
        applicationScope.launch { container.slotRepository.ensureDefaults() }
        applicationScope.launch { container.bookRepository.ensureDefaultSources() }
        // 周期校对走 AlarmManager（轻量），不再在启动时初始化 WorkManager
        container.schedulePeriodicRecovery()
    }
}

class AppContainer(private val context: android.content.Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "campus_assistant.db"
    )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    AppDatabase.insertDefaultSlots(db)
                }
            }
        )
        .build()

    val settingsRepository: SettingsRepository = SettingsRepository(context)
    val shortcutRepository: ShortcutRepository = ShortcutRepository(context)
    val browserRepository: BrowserRepository = BrowserRepository(context)
    val repoRepository: RepoRepository = RepoRepository(context)
    private val notificationPublisher: NotificationPublisher = NotificationPublisher(context)
    private val alarmScheduler = AndroidAlarmScheduler(context, database.scheduledAlarmDao())

    val reminderCoordinator: ReminderCoordinator = ReminderCoordinator(
        database = database,
        settingsRepository = settingsRepository,
        alarmScheduler = alarmScheduler,
        notificationPublisher = notificationPublisher
    )

    val courseRepository: CourseRepository = CourseRepository(database, reminderCoordinator)
    val todoRepository: TodoRepository = TodoRepository(database, reminderCoordinator)
    val ledgerRepository: LedgerRepository = LedgerRepository(database)
    val slotRepository: SlotRepository = SlotRepository(database)
    val bookRepository: BookRepository = BookRepository(database)
    val ideaRepository: IdeaRepository = IdeaRepository(database)
    val taskRepository: TaskRepository = TaskRepository(database)

    val canScheduleExact: Boolean
        get() = alarmScheduler.canScheduleExact()

    /**
     * 每 12 小时校对一次提醒。
     *
     * 原来用 WorkManager 的 PeriodicWorkRequest，但它在低端设备上会把
     * SystemJobService.onStartJob 拖到 10 秒超时并弹 ANR；改用 AlarmManager 的
     * 非精确重复闹钟，触发同一个 ReminderReceiver（内部直接调 coordinator）。
     */
    fun schedulePeriodicRecovery() {
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
            ?: return
        val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_PERIODIC_RECOVERY
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setInexactRepeating(
                android.app.AlarmManager.RTC,
                System.currentTimeMillis() + PERIODIC_RECOVERY_INTERVAL_MS,
                PERIODIC_RECOVERY_INTERVAL_MS,
                pending
            )
        }
    }

    fun publishTestNotification(title: String, text: String) {
        notificationPublisher.show("test:${System.currentTimeMillis()}", title, text)
    }

    companion object {
        const val ACTION_PERIODIC_RECOVERY = "com.example.myfirstapp.PERIODIC_RECOVERY"
        private const val PERIODIC_RECOVERY_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
