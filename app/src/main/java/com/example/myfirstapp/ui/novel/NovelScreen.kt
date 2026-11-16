package com.example.myfirstapp.ui.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.domain.BookSearchResult
import com.example.myfirstapp.domain.ShelfFilter
import com.example.myfirstapp.domain.ShelfSort
import com.example.myfirstapp.domain.filterShelfBooks
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.AppLoading
import com.example.myfirstapp.ui.components.BookCover
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.minTouchTarget
import kotlinx.coroutines.delay

/** 小说页面的视图状态（提升到 AppRoot 持有，避免打开书后被移出组合树导致状态丢失） */
enum class NovelPage { BOOKSTORE, SHELF, SEARCH_HISTORY, SEARCH_RESULT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelScreen(
    onOpenSources: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenToc: (Long) -> Unit = {},
    page: NovelPage,
    onPageChange: (NovelPage) -> Unit,
    viewModel: NovelViewModel = viewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val shelfQuery by viewModel.shelfQuery.collectAsStateWithLifecycle()
    val shelfFilter by viewModel.shelfFilter.collectAsStateWithLifecycle()
    val shelfSort by viewModel.shelfSort.collectAsStateWithLifecycle()
    val bookstore by viewModel.bookstore.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val quickReadState by viewModel.quickReadState.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val detailNav by viewModel.detailNav.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importLocal(uri)
    }
    val openFilePicker = {
        importLauncher.launch(
            arrayOf("text/plain", "application/epub+zip", "application/octet-stream")
        )
    }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(2200)
        viewModel.consumeMessage()
    }

    // 进入搜索历史页时自动聚焦输入框
    LaunchedEffect(page) {
        if (page == NovelPage.SEARCH_HISTORY) {
            delay(120)
            focusRequester.requestFocus()
        }
    }

    // 「立即阅读」命中后直接进阅读器（AppRoot 的 onOpenBook 本身就是进阅读器，从最近进度开始）。
    LaunchedEffect(quickReadState) {
        val s = quickReadState
        if (s is QuickReadState.Ready) {
            onOpenBook(s.shelfId)
            viewModel.consumeQuickRead()
        }
    }

    // 系统返回键：搜索结果页 / 搜索历史页要逐级回退，否则会直接退出 App（用户反馈过）
    BackHandler(enabled = page == NovelPage.SEARCH_RESULT || page == NovelPage.SEARCH_HISTORY) {
        when {
            detail.title.isNotBlank() -> viewModel.closeDetail()
            page == NovelPage.SEARCH_RESULT -> onPageChange(NovelPage.SEARCH_HISTORY)
            else -> onPageChange(NovelPage.SHELF)
        }
    }

    // 详情页「开始阅读 / 查看目录」：分别跳到阅读器 / 目录页
    LaunchedEffect(detailNav) {
        when (val nav = detailNav) {
            is NovelViewModel.DetailNav.Reader -> {
                onOpenBook(nav.bookId)
                viewModel.consumeDetailNav()
            }
            is NovelViewModel.DetailNav.Toc -> {
                onOpenToc(nav.bookId)
                viewModel.consumeDetailNav()
            }
            null -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (page == NovelPage.BOOKSTORE || page == NovelPage.SHELF) {
            TabRow(selectedTabIndex = if (page == NovelPage.BOOKSTORE) 0 else 1) {
                Tab(
                    selected = page == NovelPage.BOOKSTORE,
                    onClick = { onPageChange(NovelPage.BOOKSTORE) },
                    text = { Text("书库") }
                )
                Tab(
                    selected = page == NovelPage.SHELF,
                    onClick = { onPageChange(NovelPage.SHELF) },
                    text = { Text("书架") }
                )
            }
        }
        when (page) {
            NovelPage.BOOKSTORE -> BookstorePage(
                data = bookstore,
                channel = channel,
                onChannelChange = viewModel::setChannel,
                onSearchHistory = { onPageChange(NovelPage.SEARCH_HISTORY) },
                onSearch = { title, cover ->
                    viewModel.searchWith(title, cover)
                    onPageChange(NovelPage.SEARCH_RESULT)
                },
                onOpenSources = onOpenSources,
                detail = detail,
                onOpenDetail = { title, cover, author -> viewModel.openDetail(title, cover, author) },
                onStartRead = viewModel::startRead,
                onViewToc = viewModel::viewDetailToc,
                onFavorite = viewModel::favoriteCurrent,
                onSwitchSource = viewModel::switchDetailSource,
                onSelectSource = viewModel::selectDetailSource,
                onCloseDetail = viewModel::closeDetail,
                onManualSearch = { title ->
                    viewModel.closeDetail()
                    viewModel.onQueryChange(title)
                    onPageChange(NovelPage.SEARCH_RESULT)
                    viewModel.search()
                }
            )
            NovelPage.SHELF -> ShelfPage(
                books = shelf,
                shelfQuery = shelfQuery, filter = shelfFilter, sort = shelfSort,
                onQueryChange = viewModel::setShelfQuery,
                onFilterChange = viewModel::setShelfFilter,
                onSortChange = viewModel::setShelfSort,
                importing = importing,
                message = message,
                onSearchClick = { onPageChange(NovelPage.SEARCH_HISTORY) },
                onImport = openFilePicker,
                onOpenBook = onOpenBook,
                onRemove = viewModel::removeFromShelf
            )
            NovelPage.SEARCH_HISTORY -> SearchHistoryPage(
                query = query,
                history = searchHistory,
                focusRequester = focusRequester,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    if (query.isNotBlank()) {
                        onPageChange(NovelPage.SEARCH_RESULT)
                        viewModel.search()
                    }
                },
                onBack = { onPageChange(NovelPage.SHELF) },
                onClearHistory = viewModel::clearSearchHistory
            )
            NovelPage.SEARCH_RESULT -> {
                if (detail.title.isNotBlank()) {
                    // 点了某本书 → 书籍详情页（简介 + 来源切换），跟笔趣阁一致
                    BookDetailPage(
                        book = com.example.myfirstapp.data.bookstore.StoreBook(
                            title = detail.title,
                            author = detail.current?.author.orEmpty(),
                            cover = detail.cover.orEmpty(),
                            intro = ""
                        ),
                        detail = detail,
                        onBack = { viewModel.closeDetail() },
                        onStartRead = viewModel::startRead,
                        onViewToc = viewModel::viewDetailToc,
                        onFavorite = viewModel::favoriteCurrent,
                        onSwitchSource = viewModel::switchDetailSource,
                        onSelectSource = viewModel::selectDetailSource,
                        onManualSearch = {
                            val t = detail.title
                            viewModel.closeDetail()
                            viewModel.onQueryChange(t)
                            onPageChange(NovelPage.SEARCH_RESULT)
                            viewModel.search()
                        }
                    )
                } else {
                    SearchResultPage(
                        query = query,
                        searchState = searchState,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::search,
                        onBack = { onPageChange(NovelPage.SEARCH_HISTORY) },
                        onOpenSources = onOpenSources,
                        onAddToShelf = viewModel::addToShelf,
                        onOpenResult = { result -> viewModel.openDetailFor(result) }
                    )
                }
            }
        }
    }
}

// ───────────────────────── 书架页（默认） ─────────────────────────

@Composable
private fun ShelfPage(
    books: List<ShelfBookEntity>,
    shelfQuery: String, filter: ShelfFilter, sort: ShelfSort,
    onQueryChange: (String) -> Unit, onFilterChange: (ShelfFilter) -> Unit, onSortChange: (ShelfSort) -> Unit,
    importing: Boolean,
    message: String?,
    onSearchClick: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onRemove: (ShelfBookEntity) -> Unit
) {
    var pendingRemove by remember { mutableStateOf<ShelfBookEntity?>(null) }
    val shown = remember(books, shelfQuery, filter, sort) { filterShelfBooks(books, shelfQuery, filter, sort) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部通栏搜索框：点击进入搜索历史页
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .minTouchTarget()
                .semantics { contentDescription = "搜索小说" }
                .clickable(onClick = onSearchClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "在线搜索书名或作者",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 本地导入入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "书架 · ${shown.size}/${books.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier
                    .minTouchTarget()
                    .clickable(enabled = !importing, onClick = onImport)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = "导入",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (importing) "导入中…" else "导入本地书",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (books.isNotEmpty()) {
            OutlinedTextField(value = shelfQuery, onValueChange = onQueryChange,
                placeholder = { Text("在书架中查找书名或作者") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                trailingIcon = { if (shelfQuery.isNotEmpty()) TextButton(onClick = { onQueryChange("") }) { Text("清空") } })
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ShelfFilter.entries) { option ->
                    androidx.compose.material3.FilterChip(selected = filter == option,
                        onClick = { onFilterChange(option) }, label = { Text(option.label) })
                }
                item { TextButton(onClick = { onSortChange(if (sort == ShelfSort.RECENT) ShelfSort.TITLE else ShelfSort.RECENT) }) { Text(sort.label) } }
            }
        }
        message?.let { AppInlineMessage(text = it) }

        if (books.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "书架还是空的",
                subtitle = "点上方搜索框找书加入书架，或点「导入本地书」读取手机里的 TXT / EPUB 小说。本地书不需要联网，导入后直接就能读",
                action = {
                    FilledTonalButton(onClick = onSearchClick) { Text("去搜书") }
                }
            )
        } else if (shown.isEmpty()) {
            EmptyState(icon = Icons.Default.Search, title = "没有匹配的书籍", subtitle = "试试其他关键词，或切回全部书籍",
                action = { TextButton(onClick = { onQueryChange(""); onFilterChange(ShelfFilter.ALL) }) { Text("清除筛选") } })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(shown, key = { it.id }) { book ->
                    ShelfBookRow(
                        book = book,
                        onOpen = { onOpenBook(book.id) },
                        onLongPress = { pendingRemove = book }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    // 长按一本书 → 二次确认后移出书架
    pendingRemove?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移出书架") },
            text = {
                Text("${book.title}\n" + if (book.bookUrl.startsWith("local://"))
                    "本地正文和阅读记录会一起删除，再次阅读需要重新导入原文件。"
                    else "移出后阅读记录会一起清掉，之后可以重新搜索加入。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(book)
                        pendingRemove = null
                    }
                ) { Text("移出") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 书架里的一本书：封面在左，书名 + 作者/进度在右。
 *
 * 为了「一屏能看到 6 本」，这里刻意压扁了行高：封面 46dp 宽（3:4 约 61dp 高），
 * 文字两行。右边不再放删除按钮，改成**长按整行**移出书架。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookRow(
    book: ShelfBookEntity,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val progress = book.lastChapterTitle?.let { "读到：$it" } ?: "还没开始读"
    val subtitle = if (book.author.isNullOrBlank()) progress else "${book.author} · $progress"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier.width(46.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ───────────────────────── 搜索历史页 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryPage(
    query: String,
    history: List<String>,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 返回 + 搜索框（自动聚焦，回车即搜索）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索书名、作者") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索历史",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier
                        .minTouchTarget()
                        .semantics { contentDescription = "清除搜索历史" }
                ) {
                    Text(
                        text = "清除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Search,
                title = "还没有搜索记录",
                subtitle = "搜过的书名会出现在这里，点一下就能再次搜索"
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                history.forEach { keyword ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .minTouchTarget()
                            .clickable {
                                onQueryChange(keyword)
                                onSearch()
                            }
                    ) {
                        Text(
                            text = keyword,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── 搜索结果页 ─────────────────────────

@Composable
private fun SearchResultPage(
    query: String,
    searchState: NovelSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onAddToShelf: (BookSearchResult) -> Unit,
    onOpenResult: (BookSearchResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 返回 + 搜索框 + 右上角三点（书源管理）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索书名、作者") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.weight(1f)
            )
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("书源管理") },
                        leadingIcon = {
                            Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenSources()
                        }
                    )
                }
            }
        }

        when (searchState) {
            NovelSearchState.Idle -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "搜索想看的书",
                    subtitle = "输入书名或作者后回车，会同时在所有启用书源里查找并合并结果"
                )
            }
            NovelSearchState.Loading -> {
                AppLoading(label = "正在搜索…")
            }
            is NovelSearchState.Error -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "没有找到",
                    subtitle = (searchState as NovelSearchState.Error).message +
                        "\n也可以点右上角「⋮」进书源管理，多启用几个可用书源再试。",
                    action = { Button(onClick = onOpenSources) { Text("去管理书源") } }
                )
            }
            is NovelSearchState.Success -> {
                val results = (searchState as NovelSearchState.Success).results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 上游已按 源+URL 去重，这里再带上 index 作为最后一道保险：
                    // 任何脏数据都不会再触发 LazyColumn "Key was already used" 闪退。
                    itemsIndexed(
                        results,
                        key = { index, result -> "${result.sourceId}|${result.bookUrl}|$index" }
                    ) { _, result ->
                        SearchResultRow(
                            result = result,
                            onAddToShelf = onAddToShelf,
                            onClick = { onOpenResult(result) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/** 搜索结果行：封面 + 书名，右侧加号加入书架 */
@Composable
private fun SearchResultRow(
    result: BookSearchResult,
    onAddToShelf: (BookSearchResult) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(
            title = result.title,
            coverUrl = result.coverUrl,
            modifier = Modifier.width(56.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (result.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 同一本书会列出多个源，来源必须显示，方便挑能读的那个
            if (result.sourceName.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "来源：${result.sourceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = { onAddToShelf(result) }) {
            Icon(Icons.Default.Add, contentDescription = "加入书架", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
