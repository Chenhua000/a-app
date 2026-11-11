package com.example.myfirstapp.data.shortcuts

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

private val Context.shortcutDataStore: DataStore<Preferences> by preferencesDataStore(name = "shortcuts")

class ShortcutRepository(private val context: Context) {
    private val listKey = stringPreferencesKey("shortcut_list")

    val shortcuts: Flow<List<AppShortcut>> = context.shortcutDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ShortcutCodec.decode(it[listKey]) }

    suspend fun replaceAll(list: List<AppShortcut>) {
        context.shortcutDataStore.edit { it[listKey] = ShortcutCodec.encode(list) }
    }
}
