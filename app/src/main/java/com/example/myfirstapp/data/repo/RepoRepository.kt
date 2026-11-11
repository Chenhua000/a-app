package com.example.myfirstapp.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

private val Context.repoDataStore: DataStore<Preferences> by preferencesDataStore(name = "repo")

class RepoRepository(private val context: Context) {
    private val customKey = stringPreferencesKey("custom_sites")
    private val recentKey = stringPreferencesKey("repo_recent_ids")
    private val favoriteKey = stringPreferencesKey("repo_favorite_ids")

    private val MAX_RECENT = 5
    private val MAX_FAVORITE = 5

    val customSites: Flow<List<RepoSite>> = context.repoDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { RepoSiteCodec.decode(it[customKey]) }

    /** 最近使用过的网址 id（最新在前，最多 5 个） */
    val recentIds: Flow<List<String>> = context.repoDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[recentKey]?.split(',')?.filter { s -> s.isNotBlank() } ?: emptyList() }

    /** 收藏的网址 id（最多 5 个） */
    val favoriteIds: Flow<List<String>> = context.repoDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[favoriteKey]?.split(',')?.filter { s -> s.isNotBlank() } ?: emptyList() }

    private suspend fun readList(key: Preferences.Key<String>): List<String> =
        context.repoDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[key]?.split(',')?.filter { s -> s.isNotBlank() } ?: emptyList() }
            .first()

    suspend fun addCustom(site: RepoSite) {
        context.repoDataStore.edit { prefs ->
            val current = RepoSiteCodec.decode(prefs[customKey]).toMutableList()
            current.removeAll { it.id == site.id }
            current.add(site)
            prefs[customKey] = RepoSiteCodec.encode(current)
        }
    }

    suspend fun removeCustom(id: String) {
        context.repoDataStore.edit { prefs ->
            val current = RepoSiteCodec.decode(prefs[customKey]).filterNot { it.id == id }
            prefs[customKey] = RepoSiteCodec.encode(current)
        }
    }

    /** 记录一次打开：把 id 顶到最前、去重、最多保留 5 个 */
    suspend fun recordOpen(id: String) {
        val cur = readList(recentKey).toMutableList()
        cur.remove(id)
        cur.add(0, id)
        while (cur.size > MAX_RECENT) cur.removeAt(cur.lastIndex)
        context.repoDataStore.edit { it[recentKey] = cur.joinToString(",") }
    }

    /** 加入收藏：已满 5 个时返回 false（调用方负责提示） */
    suspend fun addFavorite(id: String): Boolean {
        val cur = readList(favoriteKey).toMutableList()
        if (id in cur) return true
        if (cur.size >= MAX_FAVORITE) return false
        cur.add(id)
        context.repoDataStore.edit { it[favoriteKey] = cur.joinToString(",") }
        return true
    }

    suspend fun removeFavorite(id: String) {
        val cur = readList(favoriteKey).toMutableList()
        cur.remove(id)
        context.repoDataStore.edit { it[favoriteKey] = cur.joinToString(",") }
    }

    /** 本地 APK 存放目录（应用私有 filesDir 下） */
    fun apkDir(): File = File(context.filesDir, "repo_apks").apply { mkdirs() }

    /** 列出已存放的 APK，按修改时间倒序 */
    fun localApks(): List<File> = apkDir().listFiles()
        ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    /** 把用户选中的 APK 复制进私有目录 */
    fun importApk(source: android.net.Uri, displayName: String?): File? = runCatching {
        val rawName = displayName?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')
            ?: "app_${System.currentTimeMillis()}.apk"
        val safe = rawName.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
        val target = File(apkDir(), safe)
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    fun deleteApk(file: File) {
        runCatching { if (file.exists() && file.parentFile == apkDir()) file.delete() }
    }
}
