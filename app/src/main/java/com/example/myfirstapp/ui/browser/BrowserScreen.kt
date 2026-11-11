package com.example.myfirstapp.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.browser.BrowserBookmark
import com.example.myfirstapp.data.browser.BrowserUrl
import com.example.myfirstapp.data.browser.BrowserVisit
import com.example.myfirstapp.data.browser.SearchEngine
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.isDarkColorScheme
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor
import kotlinx.coroutines.delay

// 浏览器主页是独立的「起始页」视觉（用户可手动切明暗），与 App 主题解耦；
// 但默认值必须跟随系统/应用明暗，否则浅色手机上会莫名出现深色主页。
private val HomeDarkBg = Color(0xFF16181C)
private val HomeDarkCard = Color(0xFF22252B)
private val HomeDarkText = Color(0xFFF3F5F8)
private val HomeDarkMuted = Color(0xFF98A0AC)
private val HomeLightBg = Color(0xFFF4F6FA)
private val HomeLightCard = Color(0xFFFFFFFF)
private val HomeLightText = Color(0xFF1B1F26)
private val HomeLightMuted = Color(0xFF6B7280)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel()
) {
    val engines by viewModel.engines.collectAsStateWithLifecycle()
    val engine by viewModel.currentEngine.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    // 主页 / 网页 两态；默认停在主页（搜索框），不自动发起网络请求
    var showHome by rememberSaveable { mutableStateOf(true) }
    val systemDark = isDarkColorScheme(MaterialTheme.colorScheme.surface)
    var homeDark by rememberSaveable { mutableStateOf(systemDark) }
    var homeQuery by rememberSaveable { mutableStateOf("") }
    var addressText by rememberSaveable { mutableStateOf("") }
    var currentUrl by rememberSaveable { mutableStateOf("") }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var showEngineSheet by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customTemplate by remember { mutableStateOf("") }

    // 关键：这里绝不能调用 viewModel.webView(context)。
    // WebView 首次构造会在主线程初始化渲染内核、阻塞数秒甚至触发 ANR，
    // 必须等真正要看网页时（下面 AndroidView 的 factory）才创建；
    // 主页态只读 cachedWebView()，拿不到就说明还没创建过。
    val context = LocalContext.current

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(2600)
        viewModel.consumeMessage()
    }

    fun load(target: String) {
        if (target.isBlank()) return
        currentUrl = target
        addressText = target
        showHome = false
        // 已 attach（网页态内搜索 / 换引擎）就直接加载；
        // 主页态下 WebView 还没创建，交给下面的 AndroidView factory 按 currentUrl 加载
        viewModel.cachedWebView()?.let { attached ->
            if (attached.parent != null && attached.url != target) attached.loadUrl(target)
        }
        // 立刻给反馈，WebView 初始化期间不至于看起来像卡死
        isLoading = true
        progress = 5
    }

    fun search(text: String) {
        val target = BrowserUrl.normalize(text, engine) ?: engine.homeUrl
        load(target)
    }

    // 在网页里：能后退就退网页，否则回主页
    BackHandler(enabled = !showHome) {
        val attached = viewModel.cachedWebView()
        if (canGoBack && attached != null) attached.goBack() else showHome = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 网页态才显示地址栏与导航按钮
            if (!showHome) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showHome = true }) {
                        Icon(Icons.Default.Home, contentDescription = "主页")
                    }
                    IconButton(onClick = { viewModel.cachedWebView()?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
                    }
                    IconButton(onClick = { viewModel.cachedWebView()?.goForward() }, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
                    }
                    IconButton(onClick = {
                        viewModel.cachedWebView()?.let {
                            if (isLoading) it.stopLoading() else it.reload()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    OutlinedTextField(
                        value = addressText,
                        onValueChange = { addressText = it },
                        placeholder = { Text("搜索或输入网址", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { search(addressText) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前往", modifier = Modifier.size(18.dp))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(addressText) }),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (currentUrl.isNotBlank()) viewModel.addBookmark(currentUrl, pageTitle)
                        }
                    ) {
                        Icon(
                            if (bookmarks.any { it.url == currentUrl }) Icons.Default.Bookmark
                            else Icons.Default.BookmarkBorder,
                            contentDescription = "收藏",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    engines.forEach { item ->
                        FilterChip(
                            selected = item.id == engine.id,
                            onClick = {
                                viewModel.selectEngine(item.id)
                                load(item.homeUrl)
                            },
                            label = { Text(item.name) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showCustomDialog = true },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("自定义") }
                    )
                }
            }

            if (isLoading && !showHome) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress / 100f).coerceIn(0.02f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // 只在网页态把 WebView attach 到视图树：主页态完全不创建、不初始化渲染内核，
            // 点进浏览器模块因此是瞬时的。实例由 ViewModel 复用，回主页再进不会丢历史。
            if (!showHome) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { _ ->
                        // 只有真正进入网页态才第一次构造 WebView；此时用户是在等网页加载，
                        // 且地址栏下方已有进度条反馈，不会像进模块时那样像「卡死」
                        viewModel.webView(context).apply {
                            // 每次重新 attach 都把回调绑到当前这一轮组合的状态
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val scheme = request?.url?.scheme?.lowercase().orEmpty()
                                    // 全部留在内置浏览器里，不跳外部浏览器
                                    if (scheme == "http" || scheme == "https" || scheme.isEmpty()) return false
                                    viewModel.notify("该链接无法在内置浏览器打开")
                                    return true
                                }

                                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    progress = 5
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                        addressText = url
                                    }
                                }

                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    isLoading = false
                                    progress = 100
                                    val finalUrl = view?.url ?: url.orEmpty()
                                    if (finalUrl.isNotBlank()) {
                                        currentUrl = finalUrl
                                        addressText = finalUrl
                                        viewModel.recordVisit(finalUrl, pageTitle)
                                    }
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                                    progress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onReceivedTitle(view: android.webkit.WebView?, title: String?) {
                                    pageTitle = title.orEmpty()
                                }
                            }
                            if (currentUrl.isNotBlank() && this.url != currentUrl) {
                                loadUrl(currentUrl)
                            }
                        }
                    },
                    // 不 destroy：只从容器移除，实例留在 ViewModel 里等下次复用
                    onRelease = { }
                )
            }
        }

        // 主页覆盖层：不透明，完整盖住下面的 WebView
        if (showHome) {
            BrowserHomePage(
                engines = engines,
                engine = engine,
                history = history,
                bookmarks = bookmarks,
                dark = homeDark,
                query = homeQuery,
                onQueryChange = { homeQuery = it },
                onSearch = { search(homeQuery) },
                onOpenUrl = { load(it) },
                onSelectEngine = { viewModel.selectEngine(it.id) },
                onOpenEngines = { showEngineSheet = true },
                onOpenBookmarks = { showBookmarks = true },
                onOpenHistory = { showHistory = true },
                onToggleTheme = { homeDark = !homeDark },
                onOpenTools = { showCustomDialog = true }
            )
        }

        message?.let { text ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }

    if (showEngineSheet) {
        EnginePickerSheet(
            engines = engines,
            currentId = engine.id,
            onPick = {
                viewModel.selectEngine(it)
                showEngineSheet = false
            },
            onAddCustom = {
                showEngineSheet = false
                showCustomDialog = true
            },
            onDismiss = { showEngineSheet = false }
        )
    }

    if (showBookmarks) {
        LinkListSheet(
            title = "书签",
            emptyHint = "还没有收藏，打开网页后点右上角书签图标即可收藏",
            items = bookmarks.map { it.url to it.title },
            onOpen = { load(it); showBookmarks = false },
            onRemove = { viewModel.removeBookmark(it) },
            onDismiss = { showBookmarks = false }
        )
    }

    if (showHistory) {
        LinkListSheet(
            title = "历史记录",
            emptyHint = "还没有访问记录",
            items = history.map { it.url to it.title },
            onOpen = { load(it); showHistory = false },
            onRemove = null,
            onClear = { viewModel.clearHistory() },
            onDismiss = { showHistory = false }
        )
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义搜索网址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "网址里用 {query} 表示搜索内容，例如\nhttps://example.com/search?q={query}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customTemplate,
                        onValueChange = { customTemplate = it },
                        label = { Text("搜索网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val customs = engines.filterNot { it.builtIn }
                    if (customs.isNotEmpty()) {
                        Text("已添加", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        customs.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { viewModel.removeCustomEngine(item.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.addCustomEngine(customName, customTemplate)) {
                        customName = ""
                        customTemplate = ""
                        showCustomDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("关闭") }
            }
        )
    }
}

/** 搜索框为主的主页，仿参考图：顶部横幅、五个圆形快捷入口、搜索框、最近访问、彩色卡片 */
@Composable
private fun BrowserHomePage(
    engines: List<SearchEngine>,
    engine: SearchEngine,
    history: List<BrowserVisit>,
    bookmarks: List<BrowserBookmark>,
    dark: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSelectEngine: (SearchEngine) -> Unit,
    onOpenEngines: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenTools: () -> Unit
) {
    val bg = if (dark) HomeDarkBg else HomeLightBg
    val card = if (dark) HomeDarkCard else HomeLightCard
    val text = if (dark) HomeDarkText else HomeLightText
    val muted = if (dark) HomeDarkMuted else HomeLightMuted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 顶部：小圆标 + 标题 + 菜单
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3D7BF5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Q",
                    color = onColorFor(Color(0xFF3D7BF5)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "我的主页",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = text
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenEngines) {
                Icon(Icons.Default.Menu, contentDescription = "搜索引擎", tint = text)
            }
        }

        // 渐变海景横幅
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // Canvas 依赖确定高度：heightIn 的无限上限会让 Spacer 测出 0 高、渐变整块消失
                .height(168.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFDCEBF8), Color(0xFFA8CFEA), Color(0xFF5AA6D4), Color(0xFF2F7FB8))
                )
            )
            drawRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(0f, size.height * 0.46f),
                size = Size(size.width, size.height * 0.012f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(-size.width * 0.15f, size.height * 0.30f),
                size = Size(size.width * 0.85f, size.height * 0.24f)
            )
            drawOval(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(size.width * 0.35f, size.height * 0.22f),
                size = Size(size.width * 0.9f, size.height * 0.20f)
            )
            drawOval(
                color = Color(0xFF1E6EA8).copy(alpha = 0.20f),
                topLeft = Offset(-size.width * 0.2f, size.height * 0.72f),
                size = Size(size.width * 1.4f, size.height * 0.5f)
            )
        }

        // 五个圆形快捷入口
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HomeQuickAction(Icons.Default.Bookmark, "书签", Color(0xFF4C8BF5), text, onOpenBookmarks)
            HomeQuickAction(Icons.Default.Language, "常用", Color(0xFF35BE9B), text, onOpenEngines)
            HomeQuickAction(Icons.Default.History, "历史", Color(0xFF9B86F0), text, onOpenHistory)
            HomeQuickAction(Icons.Default.Palette, "主题", Color(0xFFF5796F), text, onToggleTheme)
            HomeQuickAction(Icons.Default.Tune, "工具箱", Color(0xFFF5A44C), text, onOpenTools)
        }

        // 主搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(card)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "搜索",
                tint = muted,
                modifier = Modifier.padding(start = 10.dp).size(AppDimens.IconMedium)
            )
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索或输入网址",
                                color = muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        inner()
                    }
                }
            )
            IconButton(onClick = onSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "搜索", tint = muted)
            }
        }

        // 常用搜索网址（横滑，点一下切换默认引擎）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            engines.take(10).forEach { item ->
                FilterChip(
                    selected = item.id == engine.id,
                    onClick = { onSelectEngine(item) },
                    label = { Text(item.name) }
                )
            }
            FilterChip(
                selected = false,
                onClick = onOpenEngines,
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("更多") }
            )
        }

        // 最近访问
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenHistory)
                .background(card)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.History, contentDescription = "历史记录", tint = muted, modifier = Modifier.size(AppDimens.IconSmall))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (history.isEmpty()) "最近访问" else history.first().title,
                style = MaterialTheme.typography.bodySmall,
                color = if (history.isEmpty()) muted else text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = "展开", tint = muted, modifier = Modifier.size(AppDimens.IconSmall))
        }

        // 彩色卡片区：固定 height 改为 min 高度，大字体时可以伸展而不是裁切内容
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space3)
        ) {
            HomeCard(
                label = "收藏",
                sub = "${bookmarks.size} 个收藏",
                colors = listOf(Color(0xFFE9EDF4), Color(0xFFCFD9E8)),
                darkText = true,
                onClick = onOpenBookmarks,
                modifier = Modifier.weight(1f).heightIn(min = 190.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space3)
            ) {
                HomeCard(
                    label = "百度",
                    sub = "搜索一下",
                    colors = listOf(Color(0xFF7ED9C0), Color(0xFF43BFA3)),
                    onClick = { onOpenUrl("https://www.baidu.com") },
                    modifier = Modifier.heightIn(min = 88.dp).fillMaxWidth()
                )
                HomeCard(
                    label = "必应",
                    sub = "全球搜索",
                    colors = listOf(Color(0xFFB39BF0), Color(0xFF8A73DA)),
                    onClick = { onOpenUrl("https://www.bing.com") },
                    modifier = Modifier.heightIn(min = 88.dp).fillMaxWidth()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space3)
        ) {
            HomeCard(
                label = "哔哩哔哩",
                sub = "视频",
                colors = listOf(Color(0xFF8FC4F5), Color(0xFF5B9FE0)),
                onClick = { onOpenUrl("https://www.bilibili.com") },
                modifier = Modifier.weight(1f).heightIn(min = 112.dp)
            )
            HomeCard(
                label = "知乎",
                sub = "问答",
                colors = listOf(Color(0xFF9FD8F0), Color(0xFF6FB8DC)),
                onClick = { onOpenUrl("https://www.zhihu.com") },
                modifier = Modifier.weight(1f).heightIn(min = 112.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space3)
        ) {
            HomeCard(
                label = "GitHub",
                sub = "开发",
                colors = listOf(Color(0xFFC9B6F5), Color(0xFF9C86E0)),
                onClick = { onOpenUrl("https://github.com") },
                modifier = Modifier.weight(1f).heightIn(min = 112.dp)
            )
            HomeCard(
                label = "更多网站",
                sub = "${engines.size} 个入口",
                colors = listOf(Color(0xFF8FB8F2), Color(0xFF5C8DE0)),
                onClick = onOpenEngines,
                modifier = Modifier.weight(1f).heightIn(min = 112.dp)
            )
        }
    }
}

@Composable
private fun HomeQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = 58.dp)
            .minTouchTarget()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AppDimens.Space1),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = onColorFor(color),
                modifier = Modifier.size(AppDimens.IconMedium)
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.Space2))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun HomeCard(
    label: String,
    sub: String,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    darkText: Boolean = false
) {
    // 渐变里有 0xFF7ED9C0 亮青、0xFFE9EDF4 浅灰，固定白字几乎不可读
    val fg = if (darkText) Color(0xFF2A3140) else onColorFor(colors.first())
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(colors))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label $sub" }
            .padding(AppDimens.Space3)
    ) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(AppDimens.Space1))
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnginePickerSheet(
    engines: List<SearchEngine>,
    currentId: String,
    onPick: (String) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text("选择搜索引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(engines, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (item.id == currentId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onPick(item.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            item.homeUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!item.builtIn) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAddCustom, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("添加自定义搜索网址")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkListSheet(
    title: String,
    emptyHint: String,
    items: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
    onClear: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    onClear?.let {
                        TextButton(onClick = it) { Text("清空") }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }
            items(items, key = { it.first + it.second }) { (url, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .clickable { onOpen(url) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    onRemove?.let { remove ->
                        IconButton(onClick = { remove(url) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
