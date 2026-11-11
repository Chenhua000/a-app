package com.example.myfirstapp.ui.novel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.domain.BookSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class NovelSearchState {
    data object Idle : NovelSearchState()
    data object Loading : NovelSearchState()
    data class Success(val results: List<BookSearchResult>) : NovelSearchState()
    data class Error(val message: String) : NovelSearchState()
}

class NovelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.bookRepository
    private val settingsRepository = (application as MyFirstApp).container.settingsRepository

    val sources: StateFlow<List<BookSourceEntity>> = repository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shelf: StateFlow<List<ShelfBookEntity>> = repository.observeShelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchHistory: StateFlow<List<String>> = settingsRepository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query: StateFlow<String> = MutableStateFlow("")

    private val _searchState = MutableStateFlow<NovelSearchState>(NovelSearchState.Idle)
    val searchState: StateFlow<NovelSearchState> = _searchState

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var job: Job? = null

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    /** 导入本地 TXT / EPUB：解析 → 写入书架和目录，书架列表会自动刷新 */
    fun importLocal(uri: android.net.Uri) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            val result = runCatching {
                val parsed = com.example.myfirstapp.data.repository.LocalBookImporter
                    .parse(getApplication(), uri)
                repository.importLocalBook(parsed)
                parsed.title to parsed.chapters.size
            }
            _importing.value = false
            result.onSuccess { (title, count) ->
                _message.value = "已导入《$title》，共 $count 章，点开就能读"
            }.onFailure { error ->
                _message.value = "导入失败：${error.message ?: "无法解析这个文件"}"
            }
        }
    }

    fun onQueryChange(text: String) {
        (query as MutableStateFlow).value = text
    }

    fun search() {
        val keyword = query.value.trim()
        if (keyword.isEmpty()) {
            _message.value = "先输入书名"
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _searchState.value = NovelSearchState.Loading
            // 记录进搜索历史（去重、最新在前）
            settingsRepository.recordSearchHistory(keyword)
            val sourcesCount = sources.value.count { it.enabled }
            if (sourcesCount == 0) {
                _searchState.value = NovelSearchState.Error("没有启用任何书源，先去「书源管理」导入")
                return@launch
            }
            val results = runCatching { repository.search(keyword) }
                .getOrElse { error ->
                    _searchState.value = NovelSearchState.Error("搜索失败：${error.localizedMessage}")
                    return@launch
                }
            _searchState.value = if (results.isEmpty()) {
                NovelSearchState.Error(
                    "在 $sourcesCount 个启用书源里没找到完全匹配的。" +
                    "书名不用太精确——输入部分书名（如「斗破」「红楼」）也能搜到相关书；" +
                    "也可以到「书源」里多启用几个已验证可用的源再试。"
                )
            } else {
                NovelSearchState.Success(results)
            }
        }
    }

    fun addToShelf(result: BookSearchResult) {
        viewModelScope.launch {
            val id = repository.addToShelf(result)
            _message.value = if (id > 0) "《${result.title}》已加入书架" else "书架里已有这本书"
        }
    }

    /** 点击搜索结果直接阅读：先存进书架（已存在则复用），再回调书架条目 id 打开目录 */
    fun openToRead(result: BookSearchResult, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.addToShelf(result)
            if (id > 0) onReady(id) else _message.value = "打开失败，稍后再试"
        }
    }

    fun removeFromShelf(book: ShelfBookEntity) {
        viewModelScope.launch { repository.removeFromShelf(book) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { settingsRepository.clearSearchHistory() }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
