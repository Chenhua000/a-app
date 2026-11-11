package com.example.myfirstapp.ui.repo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.repo.RepoCategory
import com.example.myfirstapp.data.repo.RepoCatalog
import com.example.myfirstapp.data.repo.RepoRepository
import com.example.myfirstapp.data.repo.RepoSite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class RepoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyFirstApp
    private val repository: RepoRepository = app.container.repoRepository

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val custom: StateFlow<List<RepoSite>> = repository.customSites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sites: StateFlow<List<RepoSite>> = custom
        .map { RepoCatalog.sites + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RepoCatalog.sites)

    private val _category = MutableStateFlow(RepoCategory.RECENT)
    val category: StateFlow<RepoCategory> = _category

    val recentIds: StateFlow<List<String>> = repository.recentIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<List<String>> = repository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _apks = MutableStateFlow<List<File>>(emptyList())
    val apks: StateFlow<List<File>> = _apks

    init {
        refreshApks()
    }

    fun selectCategory(c: RepoCategory) {
        _category.value = c
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** 打开一个网址时记录到「最近使用」 */
    fun recordOpen(id: String) {
        viewModelScope.launch { repository.recordOpen(id) }
    }

    /** 切换收藏状态：返回后通过消息提示是否成功（收藏满 5 个会提示） */
    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            if (favoriteIds.value.contains(id)) {
                repository.removeFavorite(id)
                _message.value = "已取消收藏"
            } else {
                val ok = repository.addFavorite(id)
                _message.value = if (ok) "已加入收藏（最多 5 个）" else "最多只能收藏 5 个"
            }
        }
    }

    fun refreshApks() {
        _apks.value = repository.localApks()
    }

    fun importApk(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            val file = repository.importApk(uri, displayName)
            if (file == null) {
                _message.value = "导入失败，请确认选择的是 APK 文件"
            } else {
                refreshApks()
                _message.value = "已存入仓库：${file.name}"
            }
        }
    }

    fun deleteApk(file: File) {
        viewModelScope.launch {
            repository.deleteApk(file)
            refreshApks()
            _message.value = "已删除"
        }
    }

    fun addCustomSite(name: String, url: String, desc: String, category: RepoCategory): Boolean {
        val n = name.trim()
        val u = url.trim()
        if (n.isEmpty()) {
            _message.value = "请填写名称"
            return false
        }
        val normalized = if (u.startsWith("http://", true) || u.startsWith("https://", true)) u
        else "https://$u"
        if (!normalized.matches(Regex("^https?://[^\\s]+\\.[^\\s]+"))) {
            _message.value = "网址格式不正确"
            return false
        }
        viewModelScope.launch {
            repository.addCustom(
                RepoSite(
                    id = "custom_${System.currentTimeMillis()}",
                    name = n,
                    url = normalized,
                    desc = desc.trim(),
                    category = category,
                    tags = listOf("自定义"),
                    builtIn = false
                )
            )
            _category.value = category
            _message.value = "已添加"
        }
        return true
    }

    fun removeCustomSite(id: String) {
        viewModelScope.launch {
            repository.removeCustom(id)
            _message.value = "已删除"
        }
    }

    fun notify(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }
}
