package com.example.myfirstapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myfirstapp.domain.ExceptionType
import com.example.myfirstapp.domain.TransactionType

class Converters {
    @androidx.room.TypeConverter
    fun fromExceptionType(value: ExceptionType?): String? = value?.name

    @androidx.room.TypeConverter
    fun toExceptionType(value: String?): ExceptionType? =
        value?.let { runCatching { ExceptionType.valueOf(it) }.getOrNull() }

    @androidx.room.TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @androidx.room.TypeConverter
    fun toTransactionType(value: String?): TransactionType? =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
}

@Database(
    entities = [
        CourseEntity::class,
        CourseExceptionEntity::class,
        TodoEntity::class,
        TimeSlotEntity::class,
        TransactionEntity::class,
        NotifiedReminderEntity::class,
        ScheduledAlarmEntity::class,
        BookSourceEntity::class,
        ShelfBookEntity::class,
        ChapterIndexEntity::class,
        ChapterCacheEntity::class,
        IdeaEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun todoDao(): TodoDao
    abstract fun transactionDao(): TransactionDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun notifiedReminderDao(): NotifiedReminderDao
    abstract fun scheduledAlarmDao(): ScheduledAlarmDao
    abstract fun bookSourceDao(): BookSourceDao
    abstract fun shelfBookDao(): ShelfBookDao
    abstract fun chapterIndexDao(): ChapterIndexDao
    abstract fun chapterCacheDao(): ChapterCacheDao
    abstract fun ideaDao(): IdeaDao
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ledger_transactions ADD COLUMN category TEXT NOT NULL DEFAULT '其他'")
                db.execSQL("ALTER TABLE planner_tasks ADD COLUMN hasDate INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE shelf_books ADD COLUMN lastChapterOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 默认 12 节次，用户可在「节次设置」里自由增删修改。 */
        fun defaultSlots(): List<TimeSlotEntity> = listOf(
            TimeSlotEntity(sortIndex = 0, label = "第1节", startMinute = 8 * 60, endMinute = 8 * 60 + 45),
            TimeSlotEntity(sortIndex = 1, label = "第2节", startMinute = 8 * 60 + 55, endMinute = 9 * 60 + 40),
            TimeSlotEntity(sortIndex = 2, label = "第3节", startMinute = 10 * 60, endMinute = 10 * 60 + 45),
            TimeSlotEntity(sortIndex = 3, label = "第4节", startMinute = 10 * 60 + 55, endMinute = 11 * 60 + 40),
            TimeSlotEntity(sortIndex = 4, label = "第5节", startMinute = 14 * 60, endMinute = 14 * 60 + 45),
            TimeSlotEntity(sortIndex = 5, label = "第6节", startMinute = 14 * 60 + 55, endMinute = 15 * 60 + 40),
            TimeSlotEntity(sortIndex = 6, label = "第7节", startMinute = 16 * 60, endMinute = 16 * 60 + 45),
            TimeSlotEntity(sortIndex = 7, label = "第8节", startMinute = 16 * 60 + 55, endMinute = 17 * 60 + 40),
            TimeSlotEntity(sortIndex = 8, label = "第9节", startMinute = 18 * 60 + 30, endMinute = 19 * 60 + 15),
            TimeSlotEntity(sortIndex = 9, label = "第10节", startMinute = 19 * 60 + 25, endMinute = 20 * 60 + 10),
            TimeSlotEntity(sortIndex = 10, label = "第11节", startMinute = 20 * 60 + 20, endMinute = 21 * 60 + 5),
            TimeSlotEntity(sortIndex = 11, label = "第12节", startMinute = 21 * 60 + 15, endMinute = 22 * 60)
        )

        fun insertDefaultSlots(db: SupportSQLiteDatabase) {
            defaultSlots().forEach { slot ->
                db.execSQL(
                    "INSERT OR IGNORE INTO time_slots (sortIndex, label, startMinute, endMinute) " +
                        "VALUES (${slot.sortIndex}, '${slot.label}', ${slot.startMinute}, ${slot.endMinute})"
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `time_slots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sortIndex` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`startMinute` INTEGER NOT NULL, " +
                        "`endMinute` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slots_sortIndex` ON `time_slots` (`sortIndex`)")

                db.execSQL("ALTER TABLE `courses` ADD COLUMN `slotId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `courses` ADD COLUMN `colorIndex` INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notified_reminders` (" +
                        "`key` TEXT NOT NULL, " +
                        "`sentAtEpochMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_alarms` (" +
                        "`key` TEXT NOT NULL, " +
                        "`streamName` TEXT NOT NULL, " +
                        "`triggerAtMillis` INTEGER NOT NULL, " +
                        "`requestCode` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`))"
                )

                // 去掉账目分类：minSdk 24 不能 DROP COLUMN，改为建新表 -> 拷数据 -> 删旧表 -> 改名
                db.execSQL(
                    "CREATE TABLE `ledger_transactions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`occurredAtEpochMillis` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `ledger_transactions_new` (`id`, `type`, `amountCents`, `note`, `occurredAtEpochMillis`) " +
                        "SELECT `id`, `type`, `amountCents`, `note`, `occurredAtEpochMillis` FROM `ledger_transactions`"
                )
                db.execSQL("DROP TABLE `ledger_transactions`")
                db.execSQL("ALTER TABLE `ledger_transactions_new` RENAME TO `ledger_transactions`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_occurredAtEpochMillis` " +
                        "ON `ledger_transactions` (`occurredAtEpochMillis`)"
                )

                insertDefaultSlots(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_sources` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                        "`searchUrl` TEXT NOT NULL, `charset` TEXT NOT NULL DEFAULT 'utf-8', " +
                        "`rules` TEXT NOT NULL DEFAULT '{}', `enabled` INTEGER NOT NULL DEFAULT 1, " +
                        "`builtIn` INTEGER NOT NULL DEFAULT 0, `sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shelf_books` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`author` TEXT, `coverUrl` TEXT, `summary` TEXT, `sourceId` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, `bookUrl` TEXT NOT NULL, " +
                        "`lastChapterIndex` INTEGER NOT NULL DEFAULT 0, `lastChapterTitle` TEXT, " +
                        "`lastReadAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shelf_books_bookUrl` ON `shelf_books` (`bookUrl`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_index` (" +
                        "`bookUrl` TEXT NOT NULL, `position` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, PRIMARY KEY(`bookUrl`, `position`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_cache` (" +
                        "`url` TEXT NOT NULL, `bookUrl` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `savedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`url`))"
                )
            }
        }

        /** 书源分组：一次导入几百个书源后，靠分组才能快速找到想要的那一批 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `book_sources` ADD COLUMN `groupName` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** 书源自检：记录每个源的最近一次测试结果，方便在手机上筛掉连不上的死源 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `book_sources` ADD COLUMN `lastStatus` TEXT DEFAULT NULL")
                db.execSQL(
                    "ALTER TABLE `book_sources` ADD COLUMN `lastTestedAt` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ideas` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL DEFAULT '', " +
                        "`content` TEXT NOT NULL DEFAULT '', " +
                        "`colorIndex` INTEGER NOT NULL DEFAULT 0, " +
                        "`pinned` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAtMillis` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        /** 任务栏：planner_tasks（任务）+ task_completions（按天完成记录） */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `planner_tasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`note` TEXT, " +
                        "`durationType` INTEGER NOT NULL DEFAULT 0, " +
                        "`repeatType` INTEGER NOT NULL DEFAULT 0, " +
                        "`anchorAtEpochMillis` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_completions` (" +
                        "`taskId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`completedAtEpochMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`taskId`, `epochDay`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_completions_epochDay` ON `task_completions` (`epochDay`)")
            }
        }

        /** 任务栏：新增「置顶」与「定时提醒」开关 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `planner_tasks` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `planner_tasks` ADD COLUMN `reminderOn` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
