package com.example.myfirstapp.ui.novel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.bookstore.BookstoreCatalog
import com.example.myfirstapp.data.bookstore.BookstoreData
import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.domain.BookResultFilter
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
import kotlinx.coroutines.withTimeoutOrNull

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
            val enabledCount = repository.observeSources().first().count { it.enabled }
            if (enabledCount == 0) {
                _searchState.value = NovelSearchState.Error("没有启用任何书源，先去「书源管理」导入")
                return@launch
            }
            // 书库点进来的条目自带封面；书源规则没给封面时补上，避免结果页 / 书架 / 目录页缺图
            fun patch(list: List<BookSearchResult>): List<BookSearchResult> =
                if (fallbackCover.isNullOrBlank()) list else list.map { result ->
                    if (result.coverUrl.isBlank()) result.copy(coverUrl = fallbackCover) else result
                }
            // 边搜边出：每收到一批结果就先显示，不再等所有源都超时。
            // 列表按「书名+作者」合并（同一本书只占一条，多源留给详情页换源）。
            val results = runCatching {
                repository.search(keyword) { partial ->
                    val p = patch(partial)
                    if (p.isNotEmpty()) {
                        allResults = p
                        _searchState.value = NovelSearchState.Success(mergeByBook(p))
                    }
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                _searchState.value = NovelSearchState.Error("搜索失败：${error.localizedMessage}")
                return@launch
            }
            currentCoroutineContext().ensureActive()
            val patched = patch(results)
            allResults = patched
            _searchState.value = if (patched.isEmpty()) {
                NovelSearchState.Error(
                    "暂时没搜到《$keyword》（已检索全部 $enabledCount 个已启用书源）。" +
                    "多半是这些源确实没这本、或页面被反爬；换个书名/作者再试，或到「书源」多启用一些源。"
                )
            } else {
                NovelSearchState.Success(mergeByBook(patched))
            }
            // 列表已经出来了；缺简介的条目后台再补，补完刷新一次，不拖慢首屏
            if (patched.isNotEmpty() && patched.any { it.summary.isBlank() }) {
                viewModelScope.launch {
                    val enriched = runCatching { repository.enrichSummaries(patched) }.getOrDefault(patched)
                    if (enriched != patched) {
                        allResults = enriched
                        _searchState.value = NovelSearchState.Success(mergeByBook(enriched))
                    }
                }
            }
        }
    }

    private val _quickReadState = MutableStateFlow<QuickReadState>(QuickReadState.Idle)
    val quickReadState: StateFlow<QuickReadState> = _quickReadState

    /** 立即阅读失败时就地提示（留在详情页，不再跳搜索结果页） */
    private val _quickError = MutableStateFlow<String?>(null)
    val quickError: StateFlow<String?> = _quickError
    fun clearQuickError() { _quickError.value = null }

    /**
     * 「立即阅读」：自动从可用书源里匹配这本书并直接打开阅读器，无需用户先去启用书源。
     * **跟笔趣阁一致：点阅读就直接读**。失败也不跳搜索页，只在详情页原地提示，用户可重试或手动搜索。
     */
    fun quickRead(title: String, coverUrl: String? = null) {
        val keyword = title.trim()
        if (keyword.isEmpty()) { _message.value = "先输入书名"; return }
        job?.cancel()
        job = viewModelScope.launch {
            _quickError.value = null
            _quickReadState.value = QuickReadState.Loading
            settingsRepository.recordSearchHistory(keyword)
            val results = runCatching { repository.quickSearch(keyword) }.getOrElse { e ->
                if (e is CancellationException) throw e
                _quickError.value = "匹配失败：${e.localizedMessage}"
                _quickReadState.value = QuickReadState.Idle
                return@launch
            }
            if (results.isEmpty()) {
                _quickError.value = "暂时没在可用书源里搜到《${keyword}》，可点「手动搜索」换个书名试试。"
                _quickReadState.value = QuickReadState.Idle
                return@launch
            }
            val first = results.first()
            val patched = if (coverUrl.isNullOrBlank()) first else first.copy(
                coverUrl = if (first.coverUrl.isBlank()) coverUrl else first.coverUrl
            )
            val id = repository.addToShelf(patched)
            if (id > 0) _quickReadState.value = QuickReadState.Ready(id)
            else {
                _quickError.value = "打开失败，稍后再试"
                _quickReadState.value = QuickReadState.Idle
            }
        }
    }

    fun consumeQuickRead() { _quickReadState.value = QuickReadState.Idle }

    // ─────── 书籍详情页（仿笔趣阁）：解析到真实书源、切换源、开始阅读 / 查看目录 / 收藏 ───────
    data class DetailState(
        val title: String = "",
        val cover: String? = null,
        val candidates: List<BookSearchResult> = emptyList(),
        val index: Int = 0,
        val loading: Boolean = false,
        val latestChapter: String? = null,
        val favorited: Boolean = false
    ) {
        val current: BookSearchResult? get() = candidates.getOrNull(index)
    }

    sealed interface DetailNav {
        data class Reader(val bookId: Long) : DetailNav
        data class Toc(val bookId: Long) : DetailNav
    }

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail
    private val _detailNav = MutableStateFlow<DetailNav?>(null)
    val detailNav: StateFlow<DetailNav?> = _detailNav
    private var detailJob: Job? = null

    /** 搜索的**完整**多源结果（不合并）。列表只展示每本书一条，其余源留给详情页「切换」。 */
    private var allResults: List<BookSearchResult> = emptyList()

    /**
     * 列表展示用：按「书名+作者」合并成一条，避免同一本书几十个源重复刷屏。
     * 合并时优先挑「有简介 / 有封面」的那条，并把同书其它源缺的简介/封面补上。
     */
    private fun mergeByBook(list: List<BookSearchResult>): List<BookSearchResult> =
        BookResultFilter.mergeByBook(list)

    /** 搜索结果点进「书籍详情」：候选源直接取本次搜索里同一本书的所有源，不用再搜一遍 */
    fun openDetailFor(result: BookSearchResult) {
        // 按「书名」而不是「书名+作者」找候选：很多源搜出来 author 是空的，
        // 严格匹配会导致「切换」里只剩一个源、按钮变灰。
        val norm = { s: String -> s.replace(Regex("[\\s《》]"), "") }
        val target = norm(result.title)
        val cands = allResults
            .filter { norm(it.title) == target }
            .distinctBy { it.sourceId }
            // 默认优先「能在尘剑里直接读」的源，云端（QQ 介绍页）只作兜底候选
            .sortedBy { if (it.sourceId == "cloud_qq") 1 else 0 }
            .ifEmpty { listOf(result) }
        detailJob?.cancel()
        _detail.value = DetailState(
            title = result.title,
            cover = result.coverUrl.takeIf { it.isNotBlank() },
            candidates = cands,
            index = 0,
            loading = false
        )
        loadLatestFor(cands.firstOrNull())
        viewModelScope.launch {
            val fav = runCatching { repository.isFavorited(cands.first().bookUrl) }.getOrDefault(false)
            if (_detail.value.current?.bookUrl == cands.first().bookUrl) {
                _detail.value = _detail.value.copy(favorited = fav)
            }
        }
    }

    fun consumeDetailNav() { _detailNav.value = null }

    /** 把一批搜索结果过滤成「同一本书的各源候选」（书名归一化匹配 + 源去重 + 非云端优先） */
    private fun candidatesOf(list: List<BookSearchResult>, kw: String): List<BookSearchResult> {
        val norm = { s: String -> s.replace(Regex("[\\s《》]"), "") }
        val nkw = norm(kw)
        val matched = list.filter {
            val t = norm(it.title)
            t.isNotEmpty() && (t.contains(nkw) || nkw.contains(t))
        }.ifEmpty { list }
        return matched.distinctBy { it.sourceId }
            .sortedBy { if (it.sourceId == "cloud_qq") 1 else 0 }
    }

    /**
     * 打开详情（书库/榜单点书走这里）：**两段式**——
     * 先跑一波 6 秒的快速搜索（含云端兜底），有结果就立刻让用户能读；
     * 再在后台跑全量搜索，把更多源补进「切换」列表。
     * （之前等完整 14 秒才出候选，按钮一直灰着，看着像「打不开」）
     */
    fun openDetail(title: String, cover: String?, author: String? = null) {
        val kw = title.trim()
        if (kw.isEmpty()) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detail.value = DetailState(title = kw, cover = cover, loading = true)

            val quick = runCatching { repository.search(kw, sourceLimit = 60, windowMs = 6_000) }
                .getOrDefault(emptyList())
            val first = candidatesOf(quick, kw)
            _detail.value = _detail.value.copy(candidates = first, index = 0, loading = false)
            loadLatestFor(first.firstOrNull())
            first.firstOrNull()?.let { c ->
                val fav = runCatching { repository.isFavorited(c.bookUrl) }.getOrDefault(false)
                if (_detail.value.current?.bookUrl == c.bookUrl) {
                    _detail.value = _detail.value.copy(favorited = fav)
                }
            }

            // 第二段：全量补源（后台，不阻塞已经可用的界面）
            var all = candidatesOf(runCatching { repository.search(kw) }.getOrDefault(emptyList()), kw)
            // 冷门书只用书名搜不到时，用「书名 + 作者」再试一次（可显著提高命中）
            val authorKey = author?.trim().orEmpty()
            if (all.isEmpty() && authorKey.isNotEmpty()) {
                all = candidatesOf(
                    runCatching { repository.search("$kw $authorKey", sourceLimit = 120, windowMs = 10_000) }
                        .getOrDefault(emptyList()),
                    kw
                )
            }
            if (all.size > _detail.value.candidates.size && _detail.value.title == kw) {
                _detail.value = _detail.value.copy(candidates = all, loading = false)
            } else if (_detail.value.title == kw) {
                _detail.value = _detail.value.copy(loading = false)
            }
        }
    }

    private fun loadLatestFor(c: BookSearchResult?) {
        if (c == null) return
        viewModelScope.launch {
            val chs = runCatching {
                withTimeoutOrNull(10_000) { repository.chapters(c.sourceId, c.bookUrl) } ?: emptyList()
            }.getOrDefault(emptyList())
            if (_detail.value.current?.bookUrl == c.bookUrl) {
                _detail.value = _detail.value.copy(latestChapter = chs.lastOrNull()?.title)
            }
        }
    }

    /** 切换到下一个书源（同一本书换源） */
    fun switchDetailSource() {
        val s = _detail.value
        if (s.candidates.size <= 1) return
        val next = (s.index + 1) % s.candidates.size
        _detail.value = s.copy(index = next, latestChapter = null, favorited = false)
        loadLatestFor(s.candidates[next])
        val c = s.candidates[next]
        viewModelScope.launch {
            val fav = runCatching { repository.isFavorited(c.bookUrl) }.getOrDefault(false)
            if (_detail.value.current?.bookUrl == c.bookUrl) {
                _detail.value = _detail.value.copy(favorited = fav)
            }
        }
    }

    /** 详情页「切换」菜单：直接选第 index 个候选源 */
    fun selectDetailSource(index: Int) {
        val s = _detail.value
        val c = s.candidates.getOrNull(index) ?: return
        if (index == s.index) return
        _detail.value = s.copy(index = index, latestChapter = null, favorited = false)
        loadLatestFor(c)
        viewModelScope.launch {
            val fav = runCatching { repository.isFavorited(c.bookUrl) }.getOrDefault(false)
            if (_detail.value.current?.bookUrl == c.bookUrl) {
                _detail.value = _detail.value.copy(favorited = fav)
            }
        }
    }

    /** 开始阅读：建临时记录（不进书架）后直接进阅读器 */
    fun startRead() {
        val s = _detail.value
        val c = s.candidates.getOrNull(s.index)
        if (c == null) {
            _message.value = if (s.loading) "正在匹配书源，稍等一下…" else "没找到可用书源，换个书名再搜试试"
            return
        }
        viewModelScope.launch {
            val id = runCatching { repository.ensureReadingBook(c) }.getOrDefault(0)
            if (id > 0) _detailNav.value = DetailNav.Reader(id)
            else _message.value = "打开失败，稍后再试"
        }
    }

    /** 查看目录：同样用临时记录，进目录页 */
    fun viewDetailToc() {
        val s = _detail.value
        val c = s.candidates.getOrNull(s.index)
        if (c == null) {
            _message.value = if (s.loading) "正在匹配书源，稍等一下…" else "没找到可用书源，换个书名再搜试试"
            return
        }
        viewModelScope.launch {
            val id = runCatching { repository.ensureReadingBook(c) }.getOrDefault(0)
            if (id > 0) _detailNav.value = DetailNav.Toc(id)
            else _message.value = "打开失败，稍后再试"
        }
    }

    /** 收藏书籍：真正加入书架 */
    fun favoriteCurrent() {
        val c = _detail.value.current ?: return
        viewModelScope.launch {
            val id = runCatching { repository.addToShelf(c, favorited = true) }.getOrDefault(0)
            if (id > 0) {
                _detail.value = _detail.value.copy(favorited = true)
                _message.value = "《${c.title}》已加入书架"
            }
        }
    }

    fun closeDetail() { _detail.value = DetailState() }

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
