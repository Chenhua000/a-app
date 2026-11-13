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
        applicationScope.launch {
            container.bookRepository.ensureDefaultSources()
            importBundledLegadoSourcesOnce()
        }
        // 周期校对走 AlarmManager（轻量），不再在启动时初始化 WorkManager
        container.schedulePeriodicRecovery()
    }

    /**
     * 把 assets/legado_sources.json 的 1744 条笔趣阁书源写进 Room，并 **全部默认启用**。
     *
     * **自愈导入**：首次启动导入 1744 条要几秒，期间 App 若被系统杀掉就会只导入一部分；
     * 旧逻辑「导入过就打标记」会导致永久缺源（用户看到的「书源不全」）。
     * 现在每次启动都用 `countBundledLegadoSources()` 核对条数，**不足就自动重导补齐**，
     * 补齐后才算完成。条数达标时只做一次廉价 COUNT 查询，不重复读 1.5MB assets。
     */
    private suspend fun importBundledLegadoSourcesOnce() {
        val prefs = getSharedPreferences("novel_import", MODE_PRIVATE)
        val repo = container.bookRepository

        // 期望条数：首次从 assets 读出后缓存，避免每次启动都解析 1.5MB
        var expected = prefs.getInt("legado_expected", 0)
        if (expected <= 0) {
            val text = readLegadoAsset() ?: return
            expected = runCatching { org.json.JSONArray(text).length() }.getOrDefault(0)
            if (expected <= 0) return
            prefs.edit().putInt("legado_expected", expected).apply()
        }

        // 自愈：库里条数不足就（重）导入，直到补齐
        val current = runCatching { repo.countBundledLegadoSources() }.getOrDefault(0)
        if (current < expected) {
            val text = readLegadoAsset()
            if (text != null) {
                runCatching { repo.importSources(text, builtIn = true) }
            }
        } else if (!prefs.getBoolean("legado_v1", false)) {
            prefs.edit().putBoolean("legado_v1", true).apply()
        }

        // 规则/搜索地址修正后的强制刷新：v3.55 起修复了原转换里的 {{{key}}} 三花括号、
        // 相对地址没拼 baseUrl、|char=gbk 选项没剥离等问题（这些导致「一本都搜不到」）。
        // 条数没变（仍 1744），单靠上面的 count 自愈不会触发重导，所以用数据版本号强制 upsert 一次。
        // v3.59：按原始 `enabled` 标志重导——只启用笔趣阁原本开启的 654 条（其余 1090 条保持关闭）。
        // 以前把 1744 条全启用、又固定搜「可用」组按名字前 60 个，结果搜的全是坏源（60 个里仅 9 个好源），
        // 所以「源一堆却一本搜不到」。现在只启用精选 654 条，跟笔趣阁一致。
        val dataVer = 5
        if (prefs.getInt("legado_data_ver", 0) < dataVer) {
            val text = readLegadoAsset()
            if (text != null) {
                // v3.59：用户要求去掉 56 书库，先从库里删掉这 3 条，再导入新版内置包（1741 条）
                runCatching { repo.deleteSources(listOf("legado_0376", "legado_0691", "legado_1378")) }
                val n = runCatching { repo.importSources(text, builtIn = true) }.getOrDefault(0)
                if (n > 0) {
                    val expectedNow = runCatching { org.json.JSONArray(text).length() }.getOrDefault(1741)
                    prefs.edit()
                        .putInt("legado_expected", expectedNow)
                        .putInt("legado_data_ver", dataVer)
                        .apply()
                }
            }
        }
        // 注意：故意**不再**调用 enableAllBundledLegadoSources（那会把 1090 条坏源也打开）。
    }

    private fun readLegadoAsset(): String? = runCatching {
        assets.open("legado_sources.json").bufferedReader().use { it.readText() }
    }.getOrNull()
}

class AppContainer(private val context: android.content.Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "campus_assistant.db"
    )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
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
