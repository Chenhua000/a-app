package com.example.myfirstapp.data.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "browser")

class BrowserRepository(private val context: Context) {
    private val customKey = stringPreferencesKey("custom_engines")
    private val selectedKey = stringPreferencesKey("selected_engine")
    private val historyKey = stringPreferencesKey("browser_history")
    private val bookmarksKey = stringPreferencesKey("browser_bookmarks")

    val customEngines: Flow<List<SearchEngine>> = context.browserDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { SearchEngineCodec.decode(it[customKey]) }

    val selectedEngineId: Flow<String> = context.browserDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[selectedKey] ?: BuiltInEngines.defaultId }

    suspend fun addCustom(engine: SearchEngine) {
        context.browserDataStore.edit { prefs ->
            val current = SearchEngineCodec.decode(prefs[customKey]).toMutableList()
            current.removeAll { it.id == engine.id }
            current.add(engine)
            prefs[customKey] = SearchEngineCodec.encode(current)
        }
    }

    suspend fun removeCustom(id: String) {
        context.browserDataStore.edit { prefs ->
            val current = SearchEngineCodec.decode(prefs[customKey]).filterNot { it.id == id }
            prefs[customKey] = SearchEngineCodec.encode(current)
        }
    }

    suspend fun setSelected(id: String) {
        context.browserDataStore.edit { it[selectedKey] = id }
    }

    val history: Flow<List<BrowserVisit>> = context.browserDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { BrowserExtrasCodec.decodeHistory(it[historyKey]) }

    val bookmarks: Flow<List<BrowserBookmark>> = context.browserDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { BrowserExtrasCodec.decodeBookmarks(it[bookmarksKey]) }

    /** 记录一次访问：同网址只保留最新一条，最多 30 条 */
    suspend fun recordVisit(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        context.browserDataStore.edit { prefs ->
            val updated = BrowserExtrasCodec.decodeHistory(prefs[historyKey])
                .filterNot { it.url == url }
                .toMutableList()
            updated.add(0, BrowserVisit(url, title.ifBlank { url }))
            prefs[historyKey] = BrowserExtrasCodec.encodeHistory(updated.take(30))
        }
    }

    suspend fun clearHistory() {
        context.browserDataStore.edit { it.remove(historyKey) }
    }

    suspend fun addBookmark(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        context.browserDataStore.edit { prefs ->
            val updated = BrowserExtrasCodec.decodeBookmarks(prefs[bookmarksKey])
                .filterNot { it.url == url }
                .toMutableList()
            updated.add(0, BrowserBookmark(url, title.ifBlank { url }))
            prefs[bookmarksKey] = BrowserExtrasCodec.encodeBookmarks(updated.take(60))
        }
    }

    suspend fun removeBookmark(url: String) {
        context.browserDataStore.edit { prefs ->
            val updated = BrowserExtrasCodec.decodeBookmarks(prefs[bookmarksKey])
                .filterNot { it.url == url }
            prefs[bookmarksKey] = BrowserExtrasCodec.encodeBookmarks(updated)
        }
    }
}
