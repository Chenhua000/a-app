package com.example.myfirstapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.ui.theme.AppDimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.ui.ledger.LedgerScreen
import com.example.myfirstapp.ui.ledger.StatsScreen
import com.example.myfirstapp.ui.navigation.AppRoute
import com.example.myfirstapp.ui.navigation.AppTab
import com.example.myfirstapp.ui.navigation.defaultModuleOrder
import com.example.myfirstapp.ui.navigation.routeForNotificationTarget
import com.example.myfirstapp.ui.navigation.routeFromName
import com.example.myfirstapp.ui.navigation.routeName
import com.example.myfirstapp.ui.navigation.tabOf
import com.example.myfirstapp.ui.novel.BookTocScreen
import com.example.myfirstapp.ui.novel.NovelScreen
import com.example.myfirstapp.ui.novel.NovelPage
import com.example.myfirstapp.ui.novel.ReaderScreen
import com.example.myfirstapp.ui.novel.SourceManagerScreen
import com.example.myfirstapp.ui.schedule.ScheduleScreen
import com.example.myfirstapp.ui.schedule.SlotSettingsScreen
import com.example.myfirstapp.ui.settings.SettingsScreen
import com.example.myfirstapp.ui.shortcuts.ShortcutScreen
import com.example.myfirstapp.ui.browser.BrowserScreen
import com.example.myfirstapp.ui.repo.RepoScreen
import com.example.myfirstapp.ui.idea.IdeasScreen
import com.example.myfirstapp.ui.idea.IdeaEditorScreen
import com.example.myfirstapp.ui.task.TaskBoardScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(pendingTarget: String?) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.SCHEDULE) }
    var secondaryName by rememberSaveable { mutableStateOf<String?>(null) }
    // 小说模块当前子页（书库 / 书架 / 搜索历史 / 搜索结果）：提升到根节点持有，
    // 这样从搜索结果点开书、再返回时仍能回到搜索结果页，而不是被重置回书架。
    var novelPage by rememberSaveable { mutableStateOf(NovelPage.BOOKSTORE) }
    val secondary = routeFromName(secondaryName)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 模块显示/顺序来自设置（DataStore），改了会实时反映到底部导航
    val settingsRepository =
        (LocalContext.current.applicationContext as MyFirstApp).container.settingsRepository
    val moduleOrder by settingsRepository.moduleOrder
        .collectAsStateWithLifecycle(initialValue = defaultModuleOrder)
    val hiddenModules by settingsRepository.hiddenModules
        .collectAsStateWithLifecycle(initialValue = emptySet<AppTab>())
    val visibleTabs = moduleOrder.filter { it !in hiddenModules }.ifEmpty { listOf(AppTab.SCHEDULE) }

    // 当前模块被隐藏后，自动切到第一个可见模块
    LaunchedEffect(visibleTabs) {
        if (currentTab !in visibleTabs) currentTab = visibleTabs.first()
    }

    LaunchedEffect(pendingTarget) {
        if (pendingTarget == null) return@LaunchedEffect
        currentTab = tabOf(routeForNotificationTarget(pendingTarget))
        secondaryName = null
    }

    val onNavigate: (AppRoute) -> Unit = { secondaryName = routeName(it) }
    val onBack: () -> Unit = { secondaryName = null }

    // 抽屉打开时按返回先关抽屉；否则二级页按返回退出二级页
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = secondary != null && !drawerState.isOpen) { onBack() }

    // 阅读页是沉浸式的：不要 App 自己的顶栏 / 底部导航，正文占满整屏
    val immersive = secondary is AppRoute.Reader
    // 目录页只要顶部的返回箭头，底部导航也去掉：目录要一整屏，翻起来才顺手
    val hideBottomBar = immersive || secondary is AppRoute.Toc

    // 顶栏不显示任何文字（用户要求去掉左上角的页面标题）。
    // 只在两种需要图标的场景显示顶栏：首页三杠菜单、二级页返回箭头；
    // 其它主模块直接不显示顶栏，否则会留下一条没有任何内容的空白横条。
    val homeTab = visibleTabs.firstOrNull()
    val showMenu = secondary == null && currentTab == homeTab
    val showTopBar = !immersive && (secondary != null || showMenu)

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 仅在首页点三杠进入设置；关闭边缘滑动手势，避免任何页面右滑误开设置抽屉
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = AppDimens.Space6, vertical = AppDimens.Space6),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "尘",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.width(AppDimens.Space3))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "尘剑",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "设置与模块管理",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭设置")
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SettingsScreen()
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (immersive) WindowInsets(0, 0, 0, 0)
            else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            when {
                                secondary != null -> IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                                showMenu -> IconButton(
                                    onClick = { scope.launch { drawerState.open() } }
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "打开设置")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            },
            bottomBar = {
                if (!hideBottomBar) {
                    AppBottomBar(
                        currentTab = currentTab,
                        tabs = visibleTabs,
                        onSelected = { currentTab = it; secondaryName = null }
                    )
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (secondary) {
                    AppRoute.Stats -> StatsScreen(onBack = onBack)
                    AppRoute.Slots -> SlotSettingsScreen(onBack = onBack)
                    AppRoute.Sources -> SourceManagerScreen(onBack = onBack)
                    is AppRoute.Toc -> BookTocScreen(
                        bookId = (secondary as AppRoute.Toc).bookId,
                        onBack = { onNavigate(AppRoute.Reader((secondary as AppRoute.Toc).bookId, -1)) },
                        onOpenReader = { index -> onNavigate(AppRoute.Reader((secondary as AppRoute.Toc).bookId, index)) }
                    )
                    is AppRoute.Reader -> ReaderScreen(
                        bookId = (secondary as AppRoute.Reader).bookId,
                        startIndex = (secondary as AppRoute.Reader).index,
                        onBack = onBack,
                        onOpenToc = { onNavigate(AppRoute.Toc((secondary as AppRoute.Reader).bookId)) }
                    )
                    is AppRoute.IdeaEditor -> IdeaEditorScreen(
                        ideaId = (secondary as AppRoute.IdeaEditor).ideaId,
                        onBack = onBack
                    )
                    else -> when (currentTab) {
                        AppTab.SCHEDULE -> ScheduleScreen(onOpenSlots = { onNavigate(AppRoute.Slots) })
                        AppTab.LEDGER -> LedgerScreen(onOpenStats = { onNavigate(AppRoute.Stats) })
                        AppTab.NOVEL -> NovelScreen(
                            onOpenSources = { onNavigate(AppRoute.Sources) },
                            onOpenBook = { bookId -> onNavigate(AppRoute.Reader(bookId, -1)) },
                            page = novelPage,
                            onPageChange = { novelPage = it }
                        )
                        AppTab.IDEAS -> IdeasScreen(
                            onOpenEditor = { ideaId -> onNavigate(AppRoute.IdeaEditor(ideaId)) }
                        )
                        AppTab.BOARD -> TaskBoardScreen()
                        AppTab.SHORTCUT -> ShortcutScreen()
                        AppTab.BROWSER -> BrowserScreen()
                        AppTab.REPO -> RepoScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    currentTab: AppTab,
    tabs: List<AppTab>,
    onSelected: (AppTab) -> Unit
) {
    // 8 个模块等分仍可完整显示，不隐藏任何模块（实测横滑会让用户以为模块丢了）；
    // 只有极端情况（>8 个）才启用横滑兜底。
    val scrollable = tabs.size > 8
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(vertical = AppDimens.Space1, horizontal = AppDimens.Space1)
    ) {
        tabs.forEach { tab ->
            TabItem(
                selected = currentTab == tab,
                label = tab.label,
                icon = tabIcon(tab),
                onClick = { onSelected(tab) },
                modifier = if (scrollable) Modifier.widthIn(min = 72.dp) else Modifier.weight(1f)
            )
        }
    }
}

private fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.SCHEDULE -> Icons.Default.CalendarMonth
    AppTab.BOARD -> Icons.Default.ViewAgenda
    AppTab.LEDGER -> Icons.Default.Wallet
    AppTab.NOVEL -> Icons.AutoMirrored.Filled.MenuBook
    AppTab.IDEAS -> Icons.Default.Lightbulb
    AppTab.SHORTCUT -> Icons.Default.Link
    AppTab.BROWSER -> Icons.Default.Language
    AppTab.REPO -> Icons.Default.Inventory2
}

@Composable
private fun TabItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            // selectable 提供 Role.Tab + selected 语义与 ripple，读屏可播报「已选中」
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = AppDimens.Space2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp, 30.dp)
                .background(
                    color = if (selected) colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(AppDimens.CardRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppDimens.IconMedium)
            )
        }
        Spacer(Modifier.height(AppDimens.Space1))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
        )
    }
}
