package com.example.myfirstapp.ui.novel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.bookstore.BookstoreCatalog
import com.example.myfirstapp.data.bookstore.BookstoreData
import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.domain.BookSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import com.example.myfirstapp.domain.ShelfFilter
import com.example.myfirstapp.domain.ShelfSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class NovelSearchState {
    data object Idle : NovelSearchState()
    data object Loading : NovelSearchState()
    data class Success(val results: List<BookSearchResult>) : NovelSearchState()
    data class Error(val message: String) : NovelSearchState()
}

/** 「立即阅读」的状态：不依赖用户是否启用书源，自动从可用分组匹配后直接进阅读器 */
sealed interface QuickReadState {
    data object Idle : QuickReadState
    data object Loading : QuickReadState
    data class Ready(val shelfId: Long) : QuickReadState
    data class Error(val message: String) : QuickReadState
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _shelfQuery = MutableStateFlow("")
    val shelfQuery: StateFlow<String> = _shelfQuery
    private val _shelfFilter = MutableStateFlow(ShelfFilter.ALL)
    val shelfFilter: StateFlow<ShelfFilter> = _shelfFilter
    private val _shelfSort = MutableStateFlow(ShelfSort.RECENT)
    val shelfSort: StateFlow<ShelfSort> = _shelfSort
    fun setShelfQuery(value: String) { _shelfQuery.value = value }
    fun setShelfFilter(value: ShelfFilter) { _shelfFilter.value = value }
    fun setShelfSort(value: ShelfSort) { _shelfSort.value = value }

    /** 书库首页的分类卡 / 榜单 / 热搜词，来自 assets/bookstore_data.json */
    private val _bookstore = MutableStateFlow<BookstoreData?>(null)
    val bookstore: StateFlow<BookstoreData?> = _bookstore
    private val _channel = MutableStateFlow("male")
    val channel: StateFlow<String> = _channel
    fun setChannel(value: String) { _channel.value = value }

    init {
        viewModelScope.launch {
            _bookstore.value = withContext(Dispatchers.IO) {
                BookstoreCatalog.load(getApplication())
            }
        }
    }

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
        _query.value = text
    }

    /** 书库页点书名 / 榜单 / 热词：带关键词搜索；书库自带封面时回填到搜索结果 */
    fun searchWith(keyword: String, coverUrl: String? = null) {
        _query.value = keyword.trim()
        search(fallbackCover = coverUrl)
    }

    fun search(fallbackCover: String? = null) {
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
            val sourcesCount = repository.observeSources().first().count { it.enabled }.coerceAtMost(20)
            if (sourcesCount == 0) {
                _searchState.value = NovelSearchState.Error("没有启用任何书源，先去「书源管理」导入")
                return@launch
            }
            val results = runCatching { repository.search(keyword) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    _searchState.value = NovelSearchState.Error("搜索失败：${error.localizedMessage}")
                    return@launch
                }
            currentCoroutineContext().ensureActive()
            // 书库点进来的条目自带封面；书源规则没给封面时补上，避免结果页 / 书架 / 目录页缺图
            val patched = if (fallbackCover.isNullOrBlank()) results else results.map { result ->
                if (result.coverUrl.isBlank()) result.copy(coverUrl = fallbackCover) else result
            }
            _searchState.value = if (patched.isEmpty()) {
                NovelSearchState.Error(
                    "在 $sourcesCount 个启用书源里没找到完全匹配的。" +
                    "书名不用太精确——输入部分书名（如「斗破」「红楼」）也能搜到相关书；" +
                    "也可以到「书源」里多启用几个已验证可用的源再试。"
                )
            } else {
                NovelSearchState.Success(patched)
            }
        }
    }

    private val _quickReadState = MutableStateFlow<QuickReadState>(QuickReadState.Idle)
    val quickReadState: StateFlow<QuickReadState> = _quickReadState

    /**
     * 「立即阅读」：自动从可用书源里匹配这本书并直接打开阅读器，无需用户先去启用书源。
     * 失败时把错误透传到搜索结果页（而不是静默回到详情页），让用户知道原因。
     */
    fun quickRead(title: String, coverUrl: String? = null) {
        val keyword = title.trim()
        if (keyword.isEmpty()) { _message.value = "先输入书名"; return }
        job?.cancel()
        job = viewModelScope.launch {
            _quickReadState.value = QuickReadState.Loading
            settingsRepository.recordSearchHistory(keyword)
            val results = runCatching { repository.quickSearch(keyword) }.getOrElse { e ->
                if (e is CancellationException) throw e
                val msg = "匹配失败：${e.localizedMessage}"
                _searchState.value = NovelSearchState.Error(msg)
                _quickReadState.value = QuickReadState.Error(msg)
                return@launch
            }
            if (results.isEmpty()) {
                val msg = "暂时没在可用书源里找到《${keyword}》。可以到书源管理多启用几个源再试，或换更精确的书名搜索。"
                _searchState.value = NovelSearchState.Error(msg)
                _quickReadState.value = QuickReadState.Error(msg)
                return@launch
            }
            val first = results.first()
            val patched = if (coverUrl.isNullOrBlank()) first else first.copy(
                coverUrl = if (first.coverUrl.isBlank()) coverUrl else first.coverUrl
            )
            val id = repository.addToShelf(patched)
            if (id > 0) _quickReadState.value = QuickReadState.Ready(id)
            else {
                val msg = "打开失败，稍后再试"
                _searchState.value = NovelSearchState.Error(msg)
                _quickReadState.value = QuickReadState.Error(msg)
            }
        }
    }

    fun consumeQuickRead() { _quickReadState.value = QuickReadState.Idle }

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
