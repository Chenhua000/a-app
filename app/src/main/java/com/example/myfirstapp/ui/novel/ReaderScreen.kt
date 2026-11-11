package com.example.myfirstapp.ui.novel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.ShelfBookEntity
import com.example.myfirstapp.data.settings.ReaderPrefs
import com.example.myfirstapp.domain.BookChapter
import com.example.myfirstapp.domain.ChapterContent
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class ReaderContentState {
    data object Loading : ReaderContentState()
    data class Success(val content: ChapterContent) : ReaderContentState()
    data class Error(val message: String) : ReaderContentState()
}

/**
 * 阅读器配色。参照 Moon+ Reader / ReadEra / 微信读书 的做法：
 * 纸白、米黄（护眼纸）、护眼绿、夜间、纯黑 五档，正文色与背景色一起配套，不是简单反色。
 */
enum class ReaderTheme(
    val label: String,
    val bg: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val dark: Boolean
) {
    PAPER("纸白", Color(0xFFF8F6F1), Color(0xFF23272E), Color(0xFF8C9199), Color(0xFF1565C0), false),
    SEPIA("米黄", Color(0xFFF4EAD3), Color(0xFF4A3A26), Color(0xFF9B896C), Color(0xFF8D6E3A), false),
    GREEN("护眼", Color(0xFFCFE2C9), Color(0xFF23302A), Color(0xFF6C8072), Color(0xFF2F6B45), false),
    NIGHT("夜间", Color(0xFF171A20), Color(0xFFAEB6C2), Color(0xFF6B7380), Color(0xFF7FB0FF), true),
    BLACK("纯黑", Color(0xFF000000), Color(0xFF8C9199), Color(0xFF565B63), Color(0xFF7FB0FF), true);

    /** 顶栏 / 底栏的底色：跟着阅读背景走，但稍微抬高一点，避免和正文糊在一起 */
    val barColor: Color get() = if (dark) Color(0xFF20242B) else Color(0xFFFDFBF6)
}

class ReaderViewModel(
    application: Application,
    private val bookId: Long,
    startIndex: Int
) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.bookRepository

    private val _book = MutableStateFlow<ShelfBookEntity?>(null)
    val book: StateFlow<ShelfBookEntity?> = _book

    private val _chapters = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters: StateFlow<List<BookChapter>> = _chapters

    private val _index = MutableStateFlow(startIndex)
    val index: StateFlow<Int> = _index

    private val _state = MutableStateFlow<ReaderContentState>(ReaderContentState.Loading)
    val state: StateFlow<ReaderContentState> = _state

    /**
     * startIndex < 0 表示「从书架进度继续」，否则直接跳到该章。
     *
     * silent = true（横滑换上一章 / 下一章）时不再闪「正在排版…」：下一章通常已被
     * [prefetchNext] 预取进缓存，这一步是瞬时的；即使稍慢，也宁可让读者多看一眼当前页，
     * 也不要先白屏转个圈再出现——那一下才是「卡顿感」的来源。
     */
    fun load(index: Int, force: Boolean, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent || _state.value !is ReaderContentState.Success) {
                _state.value = ReaderContentState.Loading
            }
            // 每次都从库里重新读一次这本书：ViewModel 是按 bookId 复用的，里面缓存的
            // entity 是**上一次打开时**的快照，lastChapterIndex 还是旧的。用它算「从哪儿继续」
            // 会得到过期的进度（读到第 20 章，退出再打开又回到第 1 章，根子就在这里）。
            val entity = repository.book(bookId)
            if (entity == null) {
                _state.value = ReaderContentState.Error("这本书已不在书架中，请回到书架重新添加")
                return@launch
            }
            _book.value = entity
            // 双保险：即便 chapters() 因为任何原因抛异常，也走「目录为空」的友好提示，不闪退
            var chapters = _chapters.value.takeIf { it.isNotEmpty() }
                ?: runCatching { repository.chapters(entity.sourceId, entity.bookUrl) }
                    .getOrDefault(emptyList())
            if (chapters.isEmpty()) {
                // 兜底：有些站点整本书内容就在书页本身（没有独立章节链接），
                // 目录解析不到时，直接把书页当唯一一章去抓正文，避免「读不了」。
                val single = BookChapter(0, entity.title, entity.bookUrl)
                val fallback = runCatching {
                    repository.chapterContent(entity.sourceId, entity.bookUrl, single, force)
                }.getOrNull()
                if (fallback != null && fallback.html.isNotBlank()) {
                    chapters = listOf(single)
                    _chapters.value = chapters
                    _index.value = 0
                    _state.value = ReaderContentState.Success(fallback)
                    // 目录没抓到时**绝不能写进度**：这里只有一章，一写就把
                    // 「读到第 20 章」cover 成 0，下次打开只能从第 1 章重来。
                    return@launch
                }
                _state.value = ReaderContentState.Error(
                    "目录抓取为空。多半是「${entity.sourceName}」这个书源站点打不开或规则已失效。" +
                        "可以去「书源」里切换 / 导入其它书源后再读。"
                )
                return@launch
            }
            _chapters.value = chapters
            val effective = if (index < 0) {
                entity.lastChapterIndex.coerceIn(0, chapters.lastIndex)
            } else {
                index.coerceIn(0, chapters.lastIndex)
            }
            val chapter = chapters[effective]
            val result = runCatching {
                repository.chapterContent(entity.sourceId, entity.bookUrl, chapter, force)
            }.getOrElse { error ->
                _state.value = ReaderContentState.Error(
                    "第 ${effective + 1} 章加载失败（书源：${entity.sourceName}）：${error.localizedMessage}。" +
                        "可点「重试」，或去「书源」换一个源。"
                )
                return@launch
            }
            // 章号和正文一起换：否则会出现「新章标题 + 旧章正文」的一瞬，
            // 而且标题变了会立刻触发一次没必要的重新分页
            _index.value = effective
            _state.value = ReaderContentState.Success(result)
            repository.updateProgress(entity.id, effective, chapter.title)
            prefetchNext(entity, chapters, effective)
        }
    }

    /**
     * 后台把下一章正文抓进缓存（已在缓存里就直接命中，不发请求）。
     * 这是「横滑换章不卡」的前提：换章时正文已经躺在库里，只剩一次数据库读 + 重新分页。
     * 失败无所谓，真换章时会再抓一次并把错误报给读者。
     */
    private fun prefetchNext(entity: ShelfBookEntity, chapters: List<BookChapter>, current: Int) {
        val next = chapters.getOrNull(current + 1) ?: return
        viewModelScope.launch {
            runCatching {
                repository.chapterContent(entity.sourceId, entity.bookUrl, next, forceRefresh = false)
            }
        }
    }

    /**
     * 取相邻章的（标题, 正文 html）。给「跨章翻页」用：把上一章最后一页、下一章第一页
     * 也摆进 pager，滑到章末才能像本章内翻页那样滑过去。优先命中 [prefetchNext] 的缓存。
     */
    suspend fun neighbourChapter(target: Int): Pair<String, String>? {
        val entity = _book.value ?: return null
        val chapter = _chapters.value.getOrNull(target) ?: return null
        val content = runCatching {
            repository.chapterContent(entity.sourceId, entity.bookUrl, chapter, forceRefresh = false)
        }.getOrNull() ?: return null
        return chapter.title to content.html
    }

    fun next() = load(_index.value + 1, force = false, silent = true)
    fun previous() = load(_index.value - 1, force = false, silent = true)
    fun refresh() = load(_index.value, force = true)

    /**
     * 把「现在读到第几章」写回书架。
     *
     * 翻章虽然也会保存，但那是异步的：读者翻到某一章后立刻退出（返回键 / 手势返回 /
     * 直接杀进程）时，最后一次写入可能还没落库，下次打开就退回上一章甚至第 1 章。
     * 离开阅读器前再补写一次，进度才稳。
     */
    fun persistProgress() {
        val entity = _book.value ?: return
        val current = _index.value
        val title = _chapters.value.getOrNull(current)?.title
        viewModelScope.launch {
            runCatching { repository.updateProgress(entity.id, current, title) }
        }
    }

    /** 目录里点某一章直接跳过去（已经在别的页面渲染，这里只做越界保护） */
    fun jumpTo(target: Int) {
        if (target == _index.value) return
        load(target, force = false)
    }

    companion object {
        fun factory(application: Application, bookId: Long, startIndex: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(application, bookId, startIndex) as T
            }
    }
}

@Composable
fun ReaderScreen(
    bookId: Long,
    startIndex: Int,
    onBack: () -> Unit,
    onOpenToc: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ReaderViewModel = viewModel(
        key = "reader:$bookId",
        factory = ReaderViewModel.factory(application, bookId, startIndex)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val index by viewModel.index.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()

    // 阅读位置锚点：当前页首字符在本章里的偏移。换字号 / 换行距 / 重排后靠它跳回原处
    var anchor by remember { mutableIntStateOf(0) }
    var pendingLastPage by remember { mutableStateOf(false) }
    // 仅当「正向」到达本章最后一页时才允许自动翻下一章；往回翻（上一章末页）不触发
    var reachedLastByForward by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(startIndex) {
        anchor = 0
        pendingLastPage = false
        viewModel.load(startIndex, force = false)
    }

    // 换章统一从这里走：保证翻页位置按「下一章第 1 页 / 上一章最后 1 页」重置
    val chapterNext: () -> Unit = {
        anchor = 0
        pendingLastPage = false
        reachedLastByForward = true
        viewModel.next()
    }
    val chapterPrev: () -> Unit = {
        pendingLastPage = true
        reachedLastByForward = false
        viewModel.previous()
    }
    val chapterJump: (Int) -> Unit = { target ->
        anchor = 0
        pendingLastPage = false
        reachedLastByForward = false
        viewModel.jumpTo(target)
    }

    // ---- 阅读偏好（本次会话内保留，切章不重置）----
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableIntStateOf(18) }
    var lineSpacing by rememberSaveable { mutableFloatStateOf(1.9f) }
    var serif by rememberSaveable { mutableStateOf(true) }
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // 自动翻下一章：读到本章最后一页后停顿一下自动进下一章（关掉则和以前一样手动翻）
    var autoNext by rememberSaveable { mutableStateOf(false) }
    val palette = ReaderTheme.entries[themeIndex.coerceIn(0, ReaderTheme.entries.lastIndex)]

    // 排版偏好持久化：下次打开还是你上次的字体、字号、配色
    val settingsRepository = remember(application) {
        (application as MyFirstApp).container.settingsRepository
    }
    var prefsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = settingsRepository.currentReaderPrefs()
        fontSize = prefs.fontSize
        lineSpacing = prefs.lineSpacing
        serif = prefs.serif
        autoNext = prefs.autoNextChapter
        themeIndex = ReaderTheme.entries
            .indexOfFirst { it.name == prefs.theme }
            .coerceIn(0, ReaderTheme.entries.lastIndex)
        prefsLoaded = true
    }
    LaunchedEffect(fontSize, lineSpacing, themeIndex, serif, autoNext, prefsLoaded) {
        if (prefsLoaded) {
            settingsRepository.setReaderPrefs(
                ReaderPrefs(
                    fontSize = fontSize,
                    lineSpacing = lineSpacing,
                    theme = ReaderTheme.entries[themeIndex.coerceIn(0, ReaderTheme.entries.lastIndex)].name,
                    serif = serif,
                    autoNextChapter = autoNext
                )
            )
        }
    }

    // 离开阅读器（返回 / 进目录 / 切到别的页）时补一次进度写入
    DisposableEffect(Unit) {
        onDispose { viewModel.persistProgress() }
    }

    // ---- 沉浸式：隐藏系统栏，退出时恢复；阅读时保持屏幕常亮 ----
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousKeepOn = view.keepScreenOn
        view.keepScreenOn = true
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = previousKeepOn
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val family = if (serif) FontFamily.Serif else FontFamily.Default
    val bodyStyle = remember(fontSize, lineSpacing, serif, palette) {
        TextStyle(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineSpacing).sp,
            fontFamily = family,
            color = palette.text,
            letterSpacing = 0.2.sp
        )
    }
    val titleStyle = remember(fontSize, serif, palette) {
        TextStyle(
            fontSize = (fontSize + 4).sp,
            lineHeight = ((fontSize + 4) * 1.4f).sp,
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            color = palette.text
        )
    }

    val success = state as? ReaderContentState.Success
    val rawHtml = success?.content?.html
    val chapterTitle = remember(success, index, chapters) {
        chapters.getOrNull(index)?.title?.takeIf { it.isNotBlank() }
            ?: success?.content?.title?.takeIf { it.isNotBlank() }
            ?: ""
    }
    // 有些书源（如古腾堡）整本书只解析出一章，标题会变成「Read online now」这种链接文字，
    // 这时直接用书名当页首标题，别把导航文案摆到正文顶上
    val singleChapter = chapters.size <= 1
    val pageTitle = remember(singleChapter, book, chapterTitle) {
        if (singleChapter) book?.title.orEmpty().ifBlank { chapterTitle } else chapterTitle
    }
    val plainText = remember(rawHtml, pageTitle) {
        val text = rawHtml
            ?.let { prettify(Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString()) }
            .orEmpty()
        stripLeadingHeading(text, pageTitle)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        when (val s = state) {
            ReaderContentState.Loading -> ReaderLoading(palette)

            is ReaderContentState.Error -> ReaderError(
                message = s.message,
                palette = palette,
                onRetry = { viewModel.refresh() }
            )

            is ReaderContentState.Success -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    val hPad = 26.dp
                    // 上下留白常驻：像纸质书的天头地脚；顶栏弹出时刚好落在留白里，不切正文
                    val topPad = 66.dp
                    val indicatorReserve = 30.dp
                    val widthPx = with(density) {
                        (maxWidth - hPad * 2).toPx().toInt().coerceAtLeast(120)
                    }
                    val heightPx = with(density) {
                        (maxHeight - topPad - indicatorReserve).toPx().toInt().coerceAtLeast(160)
                    }
                    val lineHeightPx = with(density) { (fontSize * lineSpacing).sp.toPx() }
                    val titleGapPx = with(density) { 14.dp.toPx().toInt() }

                    val pageKey = "$plainText|$pageTitle|$widthPx|$heightPx|$fontSize|$lineSpacing|$serif"
                    var paged by remember { mutableStateOf<PagedResult?>(null) }
                    LaunchedEffect(pageKey) {
                        val result = withContext(Dispatchers.Default) {
                            runCatching {
                                paginateChapter(
                                    text = plainText,
                                    title = pageTitle,
                                    measurer = measurer,
                                    bodyStyle = bodyStyle,
                                    titleStyle = titleStyle,
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    lineHeightPx = lineHeightPx,
                                    titleGapPx = titleGapPx
                                )
                            }.getOrElse { emptyList() }
                        }
                        paged = PagedResult(pageKey, result, pageTitle)
                    }

                    // 分页是异步的。换章 / 换字号时不要先闪一屏「正在排版…」：先沿用上一份
                    // 已排好的页（连同它自己的标题），新的算完再整体换掉，视觉上只是一次内容
                    // 替换。下面几个 LaunchedEffect 都以「这份排版是不是当前这份」为门槛，
                    // 避免拿旧页的起始偏移去给新章定位。
                    val pages = paged?.pages.orEmpty()
                    val layoutCurrent = paged?.key == pageKey
                    val curTitle = paged?.title ?: pageTitle

                    // ---- 跨章「端点页」----
                    // 把上一章最后一页、下一章第一页也当成 pager 的一页摆在两端。这样滑到章末
                    // 还能继续滑过去，用的是 pager 自己的翻页动画——和本章内翻页完全一样，
                    // 而不是松手之后突兀地换一张图。
                    suspend fun neighbourEdge(target: Int, wantLast: Boolean): DisplayPage? {
                        val raw = viewModel.neighbourChapter(target) ?: return null
                        val title = if (singleChapter) {
                            book?.title.orEmpty().ifBlank { raw.first }
                        } else {
                            raw.first
                        }
                        val edges = withContext(Dispatchers.Default) {
                            runCatching {
                                val body = prettify(
                                    Html.fromHtml(raw.second, Html.FROM_HTML_MODE_COMPACT).toString()
                                )
                                paginateChapter(
                                    text = stripLeadingHeading(body, title),
                                    title = title,
                                    measurer = measurer,
                                    bodyStyle = bodyStyle,
                                    titleStyle = titleStyle,
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    lineHeightPx = lineHeightPx,
                                    titleGapPx = titleGapPx
                                )
                            }.getOrElse { emptyList() }
                        }
                        val edge = (if (wantLast) edges.lastOrNull() else edges.firstOrNull())
                            ?: return null
                        return DisplayPage(edge.text, title, isFirst = !wantLast)
                    }

                    var prevGhost by remember { mutableStateOf<DisplayPage?>(null) }
                    var nextGhost by remember { mutableStateOf<DisplayPage?>(null) }
                    LaunchedEffect(index, chapters.size, widthPx, heightPx, fontSize, lineSpacing, serif) {
                        // 刻意不先清空：换章瞬间旧的虚拟页还在，pager 的页数不会突然缩水
                        prevGhost = if (index - 1 in chapters.indices) {
                            neighbourEdge(index - 1, wantLast = true)
                        } else {
                            null
                        }
                        nextGhost = if (index + 1 in chapters.indices) {
                            neighbourEdge(index + 1, wantLast = false)
                        } else {
                            null
                        }
                    }

                    // 物理页表：上一章末页? + 本章 + 下一章首页?
                    val base = if (prevGhost != null) 1 else 0
                    val display: List<DisplayPage> = remember(pages, prevGhost, nextGhost, curTitle) {
                        buildList {
                            prevGhost?.let { add(it) }
                            pages.forEachIndexed { i, p -> add(DisplayPage(p.text, curTitle, i == 0)) }
                            nextGhost?.let { add(it) }
                        }
                    }
                    val pagerState = rememberPagerState(pageCount = { display.size.coerceAtLeast(1) })
                    val chapterPage = (pagerState.currentPage - base)
                        .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    val pagePercent = if (pages.isEmpty()) 0
                    else ((chapterPage + 1) * 100f / pages.size).roundToInt()

                    var programmaticTarget by remember { mutableStateOf(-1) }

                    // 分页就绪（换章 / 换字号 / 换行距 / 两端多了虚拟页）后，定位到该去的那一页。
                    // 注意：加载中 plainText 还是空的，这时不能把 pendingLastPage 用掉，
                    // 否则「翻到上一章」会落到第 1 页而不是最后一页。
                    LaunchedEffect(paged, success, base) {
                        if (success == null || pages.isEmpty() || !layoutCurrent) return@LaunchedEffect
                        val goLastPage = pendingLastPage
                        val target = if (goLastPage) pages.lastIndex
                        else pages.indexOfLast { it.start <= anchor }.coerceAtLeast(0)
                        val safeTarget = target.coerceIn(0, pages.lastIndex)
                        programmaticTarget = safeTarget
                        // 物理页前面可能多了一页「上一章末页」，页码整体要跟着平移
                        pagerState.scrollToPage(
                            (safeTarget + base).coerceIn(0, (display.size - 1).coerceAtLeast(0))
                        )
                        anchor = pages[safeTarget].start
                        pendingLastPage = false
                        // 定位已经落地，解除「别记位置」的闩；否则记位置的 effect 会拿旧页码把
                        // anchor 改掉，再反过来把刚定位好的页又顶走（换章后落到第 4 页就是这么来的）
                        programmaticTarget = -1
                    }

                    // 记住读者自己翻到的那一页；重排引起的自动跳页不算，免得位置越改越往回跑。
                    // 停在虚拟页（上一章末页 / 下一章首页）时不算本章进度，交给下面的换章逻辑。
                    LaunchedEffect(pagerState.settledPage, pages, layoutCurrent, base, programmaticTarget) {
                        if (pages.isEmpty() || !layoutCurrent) return@LaunchedEffect
                        if (programmaticTarget >= 0) return@LaunchedEffect
                        val p = pagerState.settledPage - base
                        if (p in pages.indices) {
                            anchor = pages[p].start
                        }
                    }

                    // 停在两端的虚拟页 = 要翻到相邻章。等 pager 真正 settle 了再换，
                    // 这样滑过去的那一下动画是完整的，衔接也自然。
                    // 换章会改 index / pages，effect 会重跑一次，所以用 edgeCrossing 闩住，
                    // 免得一次滑动连着换两章（反向实测会直接跳过一章）。
                    var edgeCrossing by remember { mutableStateOf(false) }
                    LaunchedEffect(pagerState.settledPage, prevGhost, nextGhost, layoutCurrent, pages.size, base, index) {
                        if (!layoutCurrent || pages.isEmpty()) return@LaunchedEffect
                        val p = pagerState.settledPage
                        val onPrevEdge = p < base
                        val onNextEdge = p > base + pages.lastIndex
                        if (!onPrevEdge && !onNextEdge) {
                            // 回到本章的真实页了，解除闩锁，下一次到边界还能再换
                            edgeCrossing = false
                            return@LaunchedEffect
                        }
                        if (edgeCrossing) return@LaunchedEffect
                        if (onPrevEdge && index > 0) {
                            edgeCrossing = true
                            chapterPrev()
                        } else if (onNextEdge && index < chapters.lastIndex) {
                            edgeCrossing = true
                            chapterNext()
                        }
                    }

                    // 自动下一章：正向读到本章最后一页 + 开关打开时，停顿一下自动翻到下一章。
                    // 把「是否正向到达末页」和翻页状态都放进 key，用户往回翻会取消正在倒计时的自动跳转。
                    LaunchedEffect(autoNext, reachedLastByForward, pagerState.settledPage, pages.size, base, index, chapters.size) {
                        if (!autoNext || !reachedLastByForward) return@LaunchedEffect
                        if (pages.isEmpty()) return@LaunchedEffect
                        if (pagerState.settledPage < base + pages.lastIndex) return@LaunchedEffect
                        if (index >= chapters.lastIndex) return@LaunchedEffect
                        delay(1000)
                        if (reachedLastByForward && index < chapters.lastIndex) {
                            chapterNext()
                        }
                    }

                    fun goNext() {
                        val p = pagerState.currentPage
                        reachedLastByForward = true
                        if (p < display.lastIndex) {
                            // 本章末页之后还有一页「下一章首页」，点一下也能滑过去（带翻页动画）
                            scope.launch { pagerState.animateScrollToPage(p + 1) }
                        } else if (!autoNext && index < chapters.lastIndex) {
                            // 没开自动下一章时，末页再往右翻才手动进下一章；开了就交给上面的自动逻辑
                            chapterNext()
                        }
                    }

                    fun goPrev() {
                        val p = pagerState.currentPage
                        reachedLastByForward = false
                        if (p > 0) {
                            // 第 1 页之前可能还有一页「上一章末页」，同样是滑过去
                            scope.launch { pagerState.animateScrollToPage(p - 1) }
                        } else if (index > 0) {
                            chapterPrev()
                        }
                    }

                    // ---- 兜底：相邻章还没排好时，章末继续横滑也能换章 ----
                    // 正常情况两端都有虚拟页，滑过去就是 pager 自己的翻页动画；只有在下一章
                    // 正文还没抓下来（比如没网）时，才退回到「攒够位移、松手换章」这套。
                    val haptics = LocalHapticFeedback.current
                    val edgeTriggerPx = remember(density) { with(density) { 72.dp.toPx() } }
                    val edgeSwipe = remember(display.size, index, chapters.size, edgeTriggerPx, prevGhost, nextGhost) {
                        ChapterEdgeSwipeConnection(
                            triggerPx = edgeTriggerPx,
                            atLastPage = {
                                nextGhost == null && pagerState.currentPage >= display.lastIndex
                            },
                            atFirstPage = {
                                prevGhost == null && pagerState.currentPage <= 0
                            },
                            onArmed = {
                                // 够数了：轻轻震一下，告诉读者「松手就翻章」
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onNextChapter = { if (index < chapters.lastIndex) chapterNext() },
                            onPrevChapter = { if (index > 0) chapterPrev() }
                        )
                    }

                    if (display.isEmpty()) {
                        ReaderLoading(palette)
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(edgeSwipe)
                                .pointerInput(display.size, index) {
                                    detectTapGestures { offset ->
                                        when {
                                            offset.x < size.width * 0.3f -> goPrev()
                                            offset.x > size.width * 0.7f -> goNext()
                                            else -> {
                                                showSettings = false
                                                menuVisible = !menuVisible
                                            }
                                        }
                                    }
                                }
                        ) { page ->
                            val item = display.getOrNull(page) ?: DisplayPage("", curTitle, false)
                            ReaderPage(
                                text = item.text,
                                isFirst = item.isFirst,
                                // 标题跟着「这一页属于哪一章」走：跨章时上一章末页不会顶着新章标题
                                chapterTitle = item.title,
                                bodyStyle = bodyStyle,
                                titleStyle = titleStyle,
                                hPad = hPad,
                                topPad = topPad,
                                bottomPad = indicatorReserve
                            )
                        }
                    }

                    // 页码：极轻，不打扰阅读，但让进度可感知
                    Text(
                        text = if (pages.isEmpty()) "" else "$pagePercent%  ·  ${chapterPage + 1}/${pages.size}",
                        style = TextStyle(fontSize = 12.sp, color = palette.dim),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 7.dp)
                    )
                }
            }
        }

        // ---------- 顶部工具栏（点屏幕中间呼出/收起）----------
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column {
                ReaderTopBar(
                    bookTitle = book?.title.orEmpty(),
                    chapterTitle = if (singleChapter) "" else chapterTitle,
                    palette = palette,
                    onBack = onBack,
                    onRefresh = { viewModel.refresh() }
                )
                // 工具栏下沿做一点渐隐，正文不会像被刀切一样断掉
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.barColor, palette.barColor.copy(alpha = 0f))
                            )
                        )
                )
            }
        }

        // ---------- 底部工具栏 ----------
        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(palette.barColor.copy(alpha = 0f), palette.barColor)
                            )
                        )
                )
                ReaderBottomBar(
                    chapterTitle = pageTitle,
                    chapterIndex = index,
                    chapterCount = chapters.size,
                    palette = palette,
                    darkTheme = palette.dark,
                    onSeek = chapterJump,
                    onPrev = chapterPrev,
                    onNext = chapterNext,
                    onToc = onOpenToc,
                    onSettings = {
                        menuVisible = false
                        showSettings = true
                    },
                    onToggleTheme = {
                        themeIndex = if (palette.dark) 0 else 3
                    }
                )
            }
        }

        if (showSettings) {
            ReaderSettingsPanel(
                themeIndex = themeIndex,
                onTheme = { themeIndex = it },
                fontSize = fontSize,
                onFontSize = { fontSize = it.coerceIn(14, 30) },
                lineSpacing = lineSpacing,
                onLineSpacing = { lineSpacing = it },
                serif = serif,
                onSerif = { serif = it },
                autoNext = autoNext,
                onAutoNext = { autoNext = it },
                palette = palette,
                onDismiss = { showSettings = false }
            )
        }
    }
}

/* ============================ 正文分页 ============================ */

/** 分页结果 + 它对应的排版参数，参数一变就作废，避免把旧排版画到新字号上 */
private data class PagedResult(val key: String, val pages: List<ReaderPage>, val title: String)

/** 一页正文 + 它在整章里的起始字符位置（换字号时靠它保住阅读位置） */
private data class ReaderPage(val start: Int, val text: String)

/** 真正画到 pager 上的一页：正文 + 这一页属于哪一章（跨章时两端会混进相邻章的端点页） */
private data class DisplayPage(val text: String, val title: String, val isFirst: Boolean)

/**
 * 把整章正文按「屏幕一屏」切开。做法是先用行高估算一屏能放几行，
 * 再让 TextMeasurer 量一次拿到真实的换行位置（layout.getLineEnd），
 * 每页只需要一次测量，几十万字也不会卡。
 */
private fun paginateChapter(
    text: String,
    title: String,
    measurer: TextMeasurer,
    bodyStyle: TextStyle,
    titleStyle: TextStyle,
    widthPx: Int,
    heightPx: Int,
    lineHeightPx: Float,
    titleGapPx: Int
): List<ReaderPage> {
    if (text.isBlank()) return listOf(ReaderPage(0, ""))
    val constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))
    val safeLine = lineHeightPx.coerceAtLeast(8f)

    val titleHeight = if (title.isBlank()) 0 else runCatching {
        measurer.measure(AnnotatedString(title), titleStyle, constraints = constraints).size.height
    }.getOrDefault(0)
    val firstPageHeight = (heightPx - titleHeight - titleGapPx)
        .coerceAtLeast((safeLine * 3).toInt())

    // 一个汉字约占 1 个字号宽；用它估出一行的字数，估算只用来定测量窗口
    val charWidth = (bodyStyle.fontSize.value * 0.98f).coerceAtLeast(8f)
    val charsPerLine = (widthPx / charWidth).toInt().coerceAtLeast(6)

    val pages = ArrayList<ReaderPage>()
    var start = 0
    var first = true
    while (start < text.length && pages.size < 20000) {
        val maxH = if (first) firstPageHeight else heightPx
        var lines = (maxH / safeLine).toInt().coerceAtLeast(1)
        var window = (charsPerLine * (lines + 2)).coerceAtLeast(64)
        var end = -1
        while (true) {
            val to = minOf(text.length, start + window)
            val layout = measurer.measure(
                AnnotatedString(text.substring(start, to)),
                bodyStyle,
                constraints = constraints
            )
            if (layout.lineCount > lines) {
                // 行数够了，再用真实行底高度校准一次，确保这一页不会溢出屏幕
                while (lines > 1 && layout.getLineBottom(lines - 1) > maxH) lines--
                end = start + layout.getLineEnd(lines - 1, visibleEnd = false)
                break
            }
            if (to >= text.length) {
                end = text.length
                break
            }
            window *= 2
        }
        if (end <= start) end = start + 1
        pages.add(ReaderPage(start, text.substring(start, end).trimStart('\n')))
        start = end
        first = false
    }
    if (pages.isEmpty()) pages.add(ReaderPage(0, text))
    return pages
}

/** 正文开头就是章节标题时（很多站点会把标题也抓进正文），这几行去掉，避免和页首标题重复 */
private fun stripLeadingHeading(text: String, title: String): String {
    if (text.isEmpty() || title.isBlank()) return text
    val target = title.replace(Regex("\\s+"), " ").trim().lowercase()
    if (target.isEmpty()) return text
    val lines = text.split('\n')
    val joined = StringBuilder()
    for ((i, line) in lines.take(3).withIndex()) {
        joined.append(line.trim().trimStart('　')).append(' ')
        val candidate = joined.toString().replace(Regex("\\s+"), " ").trim().lowercase()
        if (candidate == target) return lines.drop(i + 1).joinToString("\n")
        if (candidate.length >= target.length) break
    }
    return text
}

/** HTML 转成纯文本后整理：去掉图片占位符、段首缩进两格、去掉空行、压掉多余空格 */
private fun prettify(raw: String): String {
    if (raw.isBlank()) return ""
    val cleaned = raw
        .replace("\uFFFC", "")
        .replace("[obj]", "")
        .replace("\r\n", "\n")
        .replace('\u00A0', ' ')
    val sb = StringBuilder(cleaned.length + cleaned.length / 8)
    for (line in cleaned.split('\n')) {
        val t = line.trim().replace(Regex("[ \\t]{2,}"), " ")
        if (t.isEmpty()) continue
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append("　　").append(t)
    }
    return sb.toString()
}

/* ============================ 各种视图 ============================ */

/** 汉字占多数才用两端对齐：中文用它整齐好看，英文用它会把字距拉散 */
private fun isCjkDominant(text: String): Boolean {
    var cjk = 0
    var latin = 0
    for (ch in text) {
        when {
            ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF -> cjk++
            ch.isLetter() -> latin++
        }
    }
    return cjk > latin
}

@Composable
private fun ReaderPage(
    text: String,
    isFirst: Boolean,
    chapterTitle: String,
    bodyStyle: TextStyle,
    titleStyle: TextStyle,
    hPad: androidx.compose.ui.unit.Dp,
    topPad: androidx.compose.ui.unit.Dp,
    bottomPad: androidx.compose.ui.unit.Dp
) {
    val align = remember(text) {
        if (isCjkDominant(text)) TextAlign.Justify else TextAlign.Start
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = hPad, end = hPad, top = topPad, bottom = bottomPad)
    ) {
        if (isFirst && chapterTitle.isNotBlank()) {
            Text(
                text = chapterTitle,
                style = titleStyle,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = text,
            style = bodyStyle,
            textAlign = align
        )
    }
}

@Composable
private fun ReaderLoading(palette: ReaderTheme) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = palette.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "正在排版…",
            fontSize = 13.sp,
            color = palette.dim
        )
    }
}

@Composable
private fun ReaderError(message: String, palette: ReaderTheme, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "这一章没能打开",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = palette.dim,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(24.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = Color.White
            )
        ) { Text("重试") }
    }
}

@Composable
private fun ReaderTopBar(
    bookTitle: String,
    chapterTitle: String,
    palette: ReaderTheme,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(color = palette.barColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.text
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookTitle.ifBlank { "阅读" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        fontSize = 12.sp,
                        color = palette.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重新抓取本章",
                    tint = palette.dim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    palette: ReaderTheme,
    darkTheme: Boolean,
    onSeek: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val lastIndex = (chapterCount - 1).coerceAtLeast(0)
    var seekValue by remember(chapterIndex, chapterCount) {
        mutableFloatStateOf(chapterIndex.toFloat())
    }

    Surface(color = palette.barColor, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chapterTitle.ifBlank { "第 ${chapterIndex + 1} 章" },
                    fontSize = 13.sp,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (chapterCount > 0) "${chapterIndex + 1} / $chapterCount" else "",
                    fontSize = 12.sp,
                    color = palette.dim
                )
            }

            if (chapterCount > 1) {
                Slider(
                    value = seekValue.coerceIn(0f, lastIndex.toFloat()),
                    onValueChange = { seekValue = it },
                    onValueChangeFinished = { onSeek(seekValue.roundToInt()) },
                    valueRange = 0f..lastIndex.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.dim.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderAction(
                    icon = { Icon(Icons.Default.Menu, "目录", tint = palette.text, modifier = Modifier.size(21.dp)) },
                    label = "目录",
                    color = palette.text,
                    onClick = onToc
                )
                ReaderAction(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            "上一章",
                            tint = palette.text,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = "上一章",
                    color = palette.text,
                    enabled = chapterIndex > 0,
                    onClick = onPrev
                )
                ReaderAction(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            "下一章",
                            tint = palette.text,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = "下一章",
                    color = palette.text,
                    enabled = chapterIndex < lastIndex,
                    onClick = onNext
                )
                ReaderAction(
                    icon = { Icon(Icons.Default.FormatSize, "排版", tint = palette.text, modifier = Modifier.size(21.dp)) },
                    label = "排版",
                    color = palette.text,
                    onClick = onSettings
                )
                ReaderAction(
                    icon = {
                        Icon(
                            if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "切换深浅",
                            tint = palette.text,
                            modifier = Modifier.size(21.dp)
                        )
                    },
                    label = if (darkTheme) "日间" else "夜间",
                    color = palette.text,
                    onClick = onToggleTheme
                )
            }
        }
    }
}

@Composable
private fun ReaderAction(
    icon: @Composable () -> Unit,
    label: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .minTouchTarget()
            // 简单重载会带默认 ripple；此前显式 indication=null 把按压反馈去掉了
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .alpha(if (enabled) 1f else 0.35f),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) color else color.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun ReaderSettingsPanel(
    themeIndex: Int,
    onTheme: (Int) -> Unit,
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    lineSpacing: Float,
    onLineSpacing: (Float) -> Unit,
    serif: Boolean,
    onSerif: (Boolean) -> Unit,
    autoNext: Boolean,
    onAutoNext: (Boolean) -> Unit,
    palette: ReaderTheme,
    onDismiss: () -> Unit
) {
    val panelColor = palette.barColor
    val labelColor = palette.dim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = panelColor,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .fillMaxWidth()
                    .padding(20.dp, 14.dp, 20.dp, 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .background(palette.dim.copy(alpha = 0.35f), CircleShape)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 字号
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("字号", fontSize = 13.sp, color = labelColor, modifier = Modifier.width(52.dp))
                    PanelIconButton("A-", palette, enabled = fontSize > 14) { onFontSize(fontSize - 1) }
                    Text(
                        text = "$fontSize",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(44.dp)
                    )
                    PanelIconButton("A+", palette, enabled = fontSize < 30) { onFontSize(fontSize + 1) }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 行距
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("行距", fontSize = 13.sp, color = labelColor, modifier = Modifier.width(52.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReaderPill(
                            text = "紧凑",
                            selected = lineSpacing < 1.75f,
                            palette = palette
                        ) { onLineSpacing(1.6f) }
                        ReaderPill(
                            text = "标准",
                            selected = lineSpacing in 1.75f..2.05f,
                            palette = palette
                        ) { onLineSpacing(1.9f) }
                        ReaderPill(
                            text = "宽松",
                            selected = lineSpacing > 2.05f,
                            palette = palette
                        ) { onLineSpacing(2.2f) }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 字体
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("字体", fontSize = 13.sp, color = labelColor, modifier = Modifier.width(52.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReaderPill(text = "宋体", selected = serif, palette = palette) { onSerif(true) }
                        ReaderPill(text = "黑体", selected = !serif, palette = palette) { onSerif(false) }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 自动下一章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动下一章", fontSize = 13.sp, color = labelColor, modifier = Modifier.width(72.dp))
                    Text(
                        text = "读完本章自动翻下一章",
                        fontSize = 12.sp,
                        color = labelColor,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReaderPill(text = "关", selected = !autoNext, palette = palette) { onAutoNext(false) }
                        ReaderPill(text = "开", selected = autoNext, palette = palette) { onAutoNext(true) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 背景
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("背景", fontSize = 13.sp, color = labelColor, modifier = Modifier.width(52.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReaderTheme.entries.forEachIndexed { i, t ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .minTouchTarget()
                                        .clickable { onTheme(i) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(t.bg, CircleShape)
                                            .border(
                                                width = if (i == themeIndex) 2.dp else 1.dp,
                                                color = if (i == themeIndex) palette.accent
                                                else palette.dim.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (i == themeIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(palette.accent, CircleShape)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = t.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (i == themeIndex) palette.accent else labelColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelIconButton(text: String, palette: ReaderTheme, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minTouchTarget()
            .background(
                palette.dim.copy(alpha = if (enabled) 0.12f else 0.05f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) palette.text else palette.dim.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ReaderPill(
    text: String,
    selected: Boolean,
    palette: ReaderTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minTouchTarget()
            .background(
                if (selected) palette.accent else palette.dim.copy(alpha = 0.12f),
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (selected) onColorFor(palette.accent) else palette.text
        )
    }
}

/* ============================ 小工具 ============================ */

/**
 * 「一章的头 / 尾继续往外滑，松手就翻到上一章 / 下一章」。
 *
 * HorizontalPager 的页数只覆盖当前这一章，滚到最后一页后就没有余量可滚了，
 * 它吞不下的那段位移会通过 [NestedScrollConnection.onPostScroll] 抛给上层
 * （available 就是剩下的位移）。这里攒够一段距离就「上膛」，等手指抬起
 * （[onPreFling]）才真正换章：
 *
 *  - 换章发生在手势结束之后，这一下拖动的残余位移不会被带到新的一章去；
 *  - 顺手把惯性吃掉，否则一次快滑会把新章开头几页直接甩过去，读者就看不到第一页；
 *  - 上膛时给一次触感反馈，让「够数了、可以松手」这件事可感知。
 *
 * 只认手指拖动（UserInput）：惯性甩到末页时不该顺手把最后一页跳过去。
 * 方向判断带上了边界条件，万一滚动符号约定理解反了，最坏是「不触发」，而不是翻错方向。
 */
private class ChapterEdgeSwipeConnection(
    private val triggerPx: Float,
    private val atLastPage: () -> Boolean,
    private val atFirstPage: () -> Boolean,
    private val onArmed: () -> Unit,
    private val onNextChapter: () -> Unit,
    private val onPrevChapter: () -> Unit
) : NestedScrollConnection {

    private var accum = 0f
    private var dir = 0
    private var armed = false
    private var fired = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (source != NestedScrollSource.UserInput || fired) return Offset.Zero
        // 手指又滑回「能正常翻页」的区间，说明这次不打算换章了，撤销上膛
        if (armed && consumed.x != 0f) {
            reset()
            return Offset.Zero
        }
        val dx = available.x
        if (dx == 0f) return Offset.Zero

        // 实测（Pager 的 reverseDirection 在 LTR 下为 true）：手指往左划「往后翻」时，
        // 这里的位移是负值；往右划「往回翻」是正值。
        val wantsNext = dx < 0f && atLastPage()
        val wantsPrev = dx > 0f && atFirstPage()
        if (!wantsNext && !wantsPrev) {
            reset()
            return Offset.Zero
        }
        val sign = if (wantsNext) 1 else -1
        if (dir != sign) {
            dir = sign
            accum = 0f
        }
        accum += abs(dx)
        if (!armed && accum >= triggerPx) {
            armed = true
            onArmed()
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val sign = dir
        val ready = armed && !fired
        reset()
        if (!ready || sign == 0) return Velocity.Zero
        fired = true
        if (sign > 0) onNextChapter() else onPrevChapter()
        // 把这一下的惯性全吃掉，别让它继续推新的一章
        return available
    }

    private fun reset() {
        accum = 0f
        dir = 0
        armed = false
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
