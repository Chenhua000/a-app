package com.example.myfirstapp.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.myfirstapp.ui.navigation.AppTab
import com.example.myfirstapp.ui.navigation.defaultModuleOrder
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** 阅读器排版偏好：字号、行距倍数、配色方案名、是否宋体、是否自动翻下一章 */
data class ReaderPrefs(
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.9f,
    val theme: String = "PAPER",
    val serif: Boolean = true,
    val autoNextChapter: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val defaultReminderMinutesKey = intPreferencesKey("default_reminder_minutes")
    private val showWeekendKey = booleanPreferencesKey("show_weekend")
    private val rowHeightKey = stringPreferencesKey("row_height_mode")
    private val readerFontSizeKey = intPreferencesKey("reader_font_size")
    private val readerLineSpacingKey = intPreferencesKey("reader_line_spacing_percent")
    private val readerThemeKey = stringPreferencesKey("reader_theme")
    private val readerSerifKey = booleanPreferencesKey("reader_serif")
    private val readerAutoNextKey = booleanPreferencesKey("reader_auto_next")
    private val searchHistoryKey = stringSetPreferencesKey("search_history")
    private val searchHistoryOrderKey = stringPreferencesKey("search_history_order")
    private val moduleOrderKey = stringPreferencesKey("module_order")
    private val hiddenModulesKey = stringSetPreferencesKey("hidden_modules")

    /**
     * 小说搜索历史：最近搜过的关键词，最新在前，最多 20 条。
     * 用两个 key 存：一个 stringSet 去重，一个用 \n 分隔的 order 串保序（DataStore 的 Set 无序）。
     */
    val searchHistory: Flow<List<String>> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val order = prefs[searchHistoryOrderKey].orEmpty()
            val set = prefs[searchHistoryKey] ?: emptySet()
            order.split("\n").filter { it.isNotBlank() && it in set }
        }

    suspend fun recordSearchHistory(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val set = (prefs[searchHistoryKey] ?: emptySet()).toMutableSet()
            set.add(k)
            val order = (prefs[searchHistoryOrderKey].orEmpty())
                .split("\n")
                .filter { it.isNotBlank() && it != k }
                .toMutableList()
            order.add(0, k)
            while (order.size > 20) order.removeAt(order.size - 1)
            prefs[searchHistoryKey] = set
            prefs[searchHistoryOrderKey] = order.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(searchHistoryKey)
            prefs.remove(searchHistoryOrderKey)
        }
    }

    /** 底部模块顺序（含被隐藏的模块，顺序即设置里展示的顺序） */
    val moduleOrder: Flow<List<AppTab>> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val stored = prefs[moduleOrderKey].orEmpty()
                .split(",")
                .mapNotNull { name -> AppTab.entries.firstOrNull { it.name == name.trim() } }
            val known = stored.distinct()
            // 追加还没出现在记录里的新模块，保证以后新增模块不会丢
            known + defaultModuleOrder.filter { it !in known }
        }

    /** 被隐藏的模块 */
    val hiddenModules: Flow<Set<AppTab>> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            (prefs[hiddenModulesKey] ?: emptySet())
                .mapNotNull { name -> AppTab.entries.firstOrNull { it.name == name } }
                .toSet()
        }

    suspend fun currentModuleOrder(): List<AppTab> = moduleOrder.first()

    suspend fun setModuleOrder(order: List<AppTab>) {
        context.settingsDataStore.edit {
            it[moduleOrderKey] = order.joinToString(",") { tab -> tab.name }
        }
    }

    suspend fun setModuleHidden(tab: AppTab, hidden: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val set = (prefs[hiddenModulesKey] ?: emptySet()).toMutableSet()
            if (hidden) set.add(tab.name) else set.remove(tab.name)
            prefs[hiddenModulesKey] = set
        }
    }

    /** 阅读器的排版偏好，重开 App 也记得住 */
    val readerPrefs: Flow<ReaderPrefs> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            ReaderPrefs(
                fontSize = it[readerFontSizeKey] ?: 18,
                lineSpacing = (it[readerLineSpacingKey] ?: 190) / 100f,
                theme = it[readerThemeKey] ?: "PAPER",
                serif = it[readerSerifKey] ?: true,
                autoNextChapter = it[readerAutoNextKey] ?: false
            )
        }

    suspend fun currentReaderPrefs(): ReaderPrefs = readerPrefs.first()

    suspend fun setReaderPrefs(prefs: ReaderPrefs) {
        context.settingsDataStore.edit {
            it[readerFontSizeKey] = prefs.fontSize.coerceIn(14, 30)
            it[readerLineSpacingKey] = (prefs.lineSpacing * 100).toInt().coerceIn(140, 240)
            it[readerThemeKey] = prefs.theme
            it[readerSerifKey] = prefs.serif
            it[readerAutoNextKey] = prefs.autoNextChapter
        }
    }

    val showWeekend: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[showWeekendKey] ?: true }

    val rowHeightMode: Flow<RowHeightMode> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { RowHeightMode.from(it[rowHeightKey]) }

    val notificationsEnabled: Flow<Boolean> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[notificationsEnabledKey] ?: true }

    val defaultReminderMinutes: Flow<Int> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[defaultReminderMinutesKey] ?: 15 }

    suspend fun currentNotificationsEnabled(): Boolean = notificationsEnabled.first()

    suspend fun currentDefaultReminderMinutes(): Int = defaultReminderMinutes.first()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[notificationsEnabledKey] = enabled }
    }

    suspend fun currentShowWeekend(): Boolean = showWeekend.first()

    suspend fun currentRowHeightMode(): RowHeightMode = rowHeightMode.first()

    suspend fun setDefaultReminderMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[defaultReminderMinutesKey] = minutes.coerceIn(0, 240) }
    }

    suspend fun setShowWeekend(enabled: Boolean) {
        context.settingsDataStore.edit { it[showWeekendKey] = enabled }
    }

    suspend fun setRowHeightMode(mode: RowHeightMode) {
        context.settingsDataStore.edit { it[rowHeightKey] = mode.storageName }
    }
}

enum class RowHeightMode(val storageName: String, val dp: Int, val label: String) {
    COMPACT("compact", 52, "紧凑"),
    STANDARD("standard", 64, "标准"),
    COZY("cozy", 80, "宽松");

    companion object {
        fun from(value: String?): RowHeightMode =
            entries.firstOrNull { it.storageName == value } ?: STANDARD
    }
}
