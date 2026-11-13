package com.example.myfirstapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myfirstapp.domain.ExceptionType
import com.example.myfirstapp.domain.TransactionType

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val location: String? = null,
    val teacher: String? = null,
    val reminderMinutes: Int? = null,
    val enabled: Boolean = true,
    /** 所属节次（行）。为空时按开始时间自动匹配最合适的节次。 */
    val slotId: Long? = null,
    /** 课表格子配色序号，0-7 循环。 */
    val colorIndex: Int = 0
)

/**
 * 课表的"行"。用户可自行增删、修改标签与起止时间。
 */
@Entity(
    tableName = "time_slots",
    indices = [Index("sortIndex")]
)
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sortIndex: Int,
    val label: String,
    val startMinute: Int,
    val endMinute: Int
)

@Entity(
    tableName = "course_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "originalDateEpochDay"], unique = true)
    ]
)
data class CourseExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val originalDateEpochDay: Long,
    val type: ExceptionType,
    val newStartAtEpochMillis: Long?,
    val newEndAtEpochMillis: Long?
)

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("courseId"), Index("dueAtEpochMillis")]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val dueAtEpochMillis: Long,
    val reminderMinutes: Int? = null,
    val isCompleted: Boolean = false,
    val courseId: Long? = null
)

@Entity(
    tableName = "ledger_transactions",
    indices = [Index("occurredAtEpochMillis")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val note: String? = null,
    val occurredAtEpochMillis: Long,
    @androidx.room.ColumnInfo(defaultValue = "'其他'") val category: String = "其他"
)

/**
 * 已发送提醒的记录，用于避免重复弹窗，并支持迟到补发。
 */
@Entity(tableName = "notified_reminders")
data class NotifiedReminderEntity(
    @PrimaryKey val key: String,
    val sentAtEpochMillis: Long
)

/**
 * 书源：描述一个网站怎么搜索、怎么拿目录、怎么取正文。
 * rules 是 JSON，字段见 domain/BookRules.kt
 */
@Entity(tableName = "book_sources")
data class BookSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val searchUrl: String,
    val charset: String = "utf-8",
    val rules: String = "{}",
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val sortOrder: Int = 0,
    /** 分组名，来自导入的书源配置；用于一次导入几百个时按组筛选 */
    val groupName: String = "",
    /** 自检结果：null=未测, "ok"=能搜到书, "fail"=可达但搜不到/规则失效, "timeout"=连不上 */
    val lastStatus: String? = null,
    val lastTestedAt: Long = 0
)

/** 书架里的一本书 */
@Entity(
    tableName = "shelf_books",
    indices = [Index("bookUrl")]
)
data class ShelfBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val summary: String? = null,
    val sourceId: String,
    val sourceName: String,
    val bookUrl: String,
    val lastChapterIndex: Int = 0,
    val lastChapterTitle: String? = null,
    val lastReadAtMillis: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "0") val lastChapterOffset: Int = 0,
    // 是否「收藏」进书架。临时阅读（开始阅读/查看目录）建的条目 favorited=false，书架列表不显示；
    // 只有点「收藏书籍」才置 true。老数据默认 1（视为已收藏）。
    @androidx.room.ColumnInfo(defaultValue = "1") val favorited: Boolean = true
)

/** 目录缓存：进去一次就不再重复抓网页 */
@Entity(
    tableName = "chapter_index",
    primaryKeys = ["bookUrl", "position"]
)
data class ChapterIndexEntity(
    val bookUrl: String,
    val position: Int,
    val title: String,
    val url: String
)

/** 正文缓存：抓过一次即可离线重复阅读 */
@Entity(tableName = "chapter_cache")
data class ChapterCacheEntity(
    @PrimaryKey val url: String,
    val bookUrl: String,
    val title: String,
    val content: String,
    val savedAtMillis: Long
)

/**
 * 已经交给系统的闹钟清单，用于精确取消与重排。
 */
@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarmEntity(
    @PrimaryKey val key: String,
    val streamName: String,
    val triggerAtMillis: Long,
    val requestCode: Int
)

/**
 * 灵感便签：随手记录的想法。
 * 设计借鉴 Google Keep（彩色卡片 + 极速速记）与 Simplenote（边写边存、无需保存按钮）。
 * colorIndex：0-5 对应一组便签配色；pinned 为置顶。
 */
@Entity(tableName = "ideas")
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * 任务栏里的一条任务（与「待办」相互独立）。
 * durationType：0=短任务, 1=长任务（看板按此分类）。
 * repeatType：0=一次性, 1=每天, 2=每周。
 * anchorAtEpochMillis：基准时间（含日期+时间）。
 *   - 一次性：就是到期时间；
 *   - 每天：取其中的「时间」；
 *   - 每周：取其中的「星期几 + 时间」（按星期几每周重复）。
 * 完成状态不写死在任务上，而是按「天」记录到 task_completions，重复任务每天可独立勾掉。
 */
@Entity(tableName = "planner_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val durationType: Int = 0,
    val repeatType: Int = 0,
    val anchorAtEpochMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** 置顶：在当天未完成/已完成分组内排到最前 */
    val pinned: Boolean = false,
    /** 是否开启定时提醒（按 anchor 时间触发系统闹钟） */
    val reminderOn: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "1") val hasDate: Boolean = true
)

/**
 * 任务在某一天是否完成。主键 (taskId, epochDay) 保证每条任务每一天只有一条记录。
 * epochDay 用 LocalDate.toEpochDay()（与 TimeText 的换算一致）。
 */
@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "epochDay"],
    indices = [Index("epochDay")]
)
data class TaskCompletionEntity(
    val taskId: Long,
    val epochDay: Long,
    val completedAtEpochMillis: Long
)
