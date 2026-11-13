package com.example.myfirstapp.ui.novel

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.domain.BookChapter
import com.example.myfirstapp.ui.components.BookCover
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.minTouchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BookTocViewModel(
    application: Application,
    private val bookId: Long
) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.bookRepository

    private val _book = MutableStateFlow<ShelfBookEntity?>(null)
    val book: StateFlow<ShelfBookEntity?> = _book

    private val _chapters = MutableStateFlow<List<BookChapter>?>(null)
    val chapters: StateFlow<List<BookChapter>?> = _chapters

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        reload(force = false)
    }

    fun reload(force: Boolean) {
        viewModelScope.launch {
            val entity = repository.book(bookId)
            if (entity == null) {
                // 书已经被移出书架：连书名一起清掉，别让页面继续显示上一本书的信息
                _book.value = null
                _chapters.value = null
                _error.value = "这本书已经不在书架里（可能被长按移出了），回书架重新打开即可"
                return@launch
            }
            _book.value = entity
            _chapters.value = null
            _error.value = null
            val result = runCatching { repository.chapters(entity.sourceId, entity.bookUrl, force) }
                .getOrElse { error ->
                    _error.value = "目录加载失败（书源：${entity.sourceName}）：${error.localizedMessage}。可点「重新加载」，或在小说→书源里换一个源。"
                    return@launch
                }
            if (result.isEmpty()) {
                _error.value = "没解析到目录，可能「${entity.sourceName}」规则已失效或站点打不开。去「小说→书源」换一个源或导入新源即可。"
            } else {
                _chapters.value = result
            }
        }
    }

    /**
     * 只刷新「这本书读到哪儿了」，不重新抓目录。
     *
     * 从阅读器跳章后返回目录时，ViewModel 是复用的，里面的 book 还是旧对象，
     * 章节列表会停在原来的位置；这里把最新进度拉一次，列表才定位得到当前章。
     */
    fun refreshBook() {
        viewModelScope.launch {
            repository.book(bookId)?.let { _book.value = it }
        }
    }

    companion object {
        fun factory(application: Application, bookId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BookTocViewModel(application, bookId) as T
            }
    }
}

@Composable
fun BookTocScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenReader: (Int) -> Unit
) {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    // 必须按 bookId 区分 ViewModel：目录页在 App 里是同一个调用位置，
    // 不加 key 的话「看完 A 书目录、再点 B 书目录」会复用同一个 ViewModel，
    // 页面就会显示上一本书的书名/来源（甚至显示已删除的书）。
    val viewModel: BookTocViewModel = viewModel(
        key = "toc:$bookId",
        factory = BookTocViewModel.factory(application, bookId)
    )
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // 每次进目录都拉一次最新进度：从阅读器跳到第 255 章后回来，列表要停在 255 章
    LaunchedEffect(bookId) { viewModel.refreshBook() }

    // 列表定位：目录一出来就滚到当前章，并把它高亮出来，而不是永远从第一章开始
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = book?.lastChapterIndex?.coerceAtLeast(0) ?: 0
    LaunchedEffect(chapters, currentIndex) {
        val size = chapters?.size ?: return@LaunchedEffect
        if (size == 0) return@LaunchedEffect
        listState.scrollToItem(currentIndex.coerceAtMost(size - 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        book?.let { current ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    BookCover(
                        title = current.title,
                        coverUrl = current.coverUrl,
                        modifier = Modifier.width(64.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "来源：${current.sourceName} · 共 ${chapters?.size ?: 0} 章" +
                                if (current.lastChapterIndex > 0) " · 读到第 ${current.lastChapterIndex + 1} 章" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenReader(current.lastChapterIndex) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "继续阅读")
                                Text(if (current.lastChapterIndex > 0) "继续阅读" else "开始阅读")
                            }
                            OutlinedButton(onClick = { viewModel.reload(force = true) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "重新抓取目录")
                            }
                        }
                    }
                }
            }
        }

        when {
            error != null -> {
                EmptyState(
                    icon = Icons.Default.Refresh,
                    title = "目录没能加载",
                    subtitle = error.orEmpty(),
                    action = { Button(onClick = { viewModel.reload(force = true) }) { Text("重新加载") } }
                )
            }
            chapters == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "正在读取目录…",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chapters!!, key = { it.position }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            current = chapter.position == currentIndex,
                            onClick = { onOpenReader(chapter.position) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: BookChapter, current: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .minTouchTarget()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = chapter.title,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = chapter.title },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
