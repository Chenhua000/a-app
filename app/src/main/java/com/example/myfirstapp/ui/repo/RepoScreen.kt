package com.example.myfirstapp.ui.repo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.provider.OpenableColumns
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.repo.RepoCategory
import com.example.myfirstapp.data.repo.RepoGridBuilder
import com.example.myfirstapp.data.repo.RepoGridRow
import com.example.myfirstapp.data.repo.RepoSite
import kotlinx.coroutines.delay
import java.io.File
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor

/** 头像/渐变用的品牌色，刻意与主题解耦；上面的文字色统一由 onColorFor 按亮度推导 */
private val AvatarColors = listOf(
    Color(0xFF4C8BF5), Color(0xFF35BE9B), Color(0xFF9B86F0), Color(0xFFF5796F),
    Color(0xFFF5A44C), Color(0xFF42AFCB), Color(0xFFE0719B), Color(0xFF6C8AE4)
)

private fun categoryIcon(c: RepoCategory): ImageVector = when (c) {
    RepoCategory.RECENT -> Icons.Default.History
    RepoCategory.UTILS -> Icons.Default.Build
    RepoCategory.MEDIA -> Icons.Default.Movie
    RepoCategory.GAME -> Icons.Default.Gamepad
    RepoCategory.STUDY -> Icons.Default.School
    RepoCategory.BOOKS -> Icons.AutoMirrored.Filled.MenuBook
    RepoCategory.BLOG -> Icons.Default.Forum
    RepoCategory.FORUM -> Icons.Default.Groups
    RepoCategory.DEV -> Icons.Default.Code
    RepoCategory.SOFTWARE -> Icons.Default.Apps
    RepoCategory.NETDISK -> Icons.Default.Cloud
    RepoCategory.LIFE -> Icons.Default.Favorite
    RepoCategory.MY_APK -> Icons.Default.Android
}

private fun avatarColor(name: String): Color =
    AvatarColors[(name.hashCode() and Int.MAX_VALUE) % AvatarColors.size]

@Composable
fun RepoScreen(
    viewModel: RepoViewModel = viewModel()
) {
    val context = LocalContext.current
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val apks by viewModel.apks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val recentIds by viewModel.recentIds.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    var webUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteApk by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(2600)
        viewModel.consumeMessage()
    }

    fun open(site: RepoSite) {
        if (site.url.isNotBlank()) {
            viewModel.recordOpen(site.id)
            webUrl = site.url
        }
    }

    fun installApk(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.repo.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { viewModel.notify("无法唤起安装器：${it.localizedMessage}") }
    }

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.importApk(uri, name)
    }

    // 点网址后进入内置网页视图（不跳外部浏览器）
    if (webUrl != null) {
        RepoWebPage(url = webUrl!!, onClose = { webUrl = null })
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧分类导航
        Column(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                // 深色模式下必须跟随 surfaceVariant，否则浅底 + 浅灰字几乎不可读
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(vertical = AppDimens.Space2, horizontal = AppDimens.Space1),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RepoCategory.entries.forEach { c ->
                val selected = c == category && query.isBlank()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable(role = Role.Tab) {
                            viewModel.selectCategory(c)
                            viewModel.setQuery("")
                        }
                        .padding(vertical = AppDimens.Space2)
                        .minTouchTarget(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        categoryIcon(c),
                        contentDescription = c.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AppDimens.IconSmall)
                    )
                    Spacer(modifier = Modifier.height(AppDimens.Space1))
                    Text(
                        text = c.short,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 右侧内容
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = { Text("搜索全部网址", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(17.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加网址")
                }
            }

            Text(
                text = if (query.isBlank()) category.label else "搜索结果",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary)
            if (category == RepoCategory.MY_APK && query.isBlank()) {
                ApkPanel(
                    apks = apks,
                    onImport = { apkPicker.launch(arrayOf("*/*")) },
                    onInstall = { installApk(it) },
                    onDelete = { pendingDeleteApk = it }
                )
            } else {
                SiteGrid(
                    sites = sites,
                    category = category,
                    query = query,
                    favoriteIds = favoriteIds,
                    recentIds = recentIds,
                    onOpen = { open(it) },
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onRemoveCustom = { viewModel.removeCustomSite(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it.id) }
                )
            }
        }
    }

    message?.let { text ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
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

    if (showAddDialog) {
        AddSiteDialog(
            defaultCategory = if (category == RepoCategory.MY_APK) RepoCategory.UTILS else category,
            onDismiss = { showAddDialog = false },
            onSave = { name, url, desc, c ->
                if (viewModel.addCustomSite(name, url, desc, c)) showAddDialog = false
            }
        )
    }

    pendingDeleteApk?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDeleteApk = null },
            title = { Text("删除这个 APK？") },
            text = { Text(file.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteApk(file)
                    pendingDeleteApk = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteApk = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SiteGrid(
    sites: List<RepoSite>,
    category: RepoCategory,
    query: String,
    favoriteIds: List<String>,
    recentIds: List<String>,
    onOpen: (RepoSite) -> Unit,
    onSelectCategory: (RepoCategory) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onToggleFavorite: (RepoSite) -> Unit
) {
    // 所有行（含分组标题）一次性生成，key 全局唯一，避免同站点跨分组重复 key 导致崩溃
    val rows = remember(sites, category, query, favoriteIds, recentIds) {
        RepoGridBuilder.build(sites, category, query, favoriteIds, recentIds)
    }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(category, query) { gridState.scrollToItem(0) }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(if (query.isBlank()) "这个分类还没有网址，点 + 添加" else "没有找到相关网址，试试其他关键词",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        state = gridState,
        // 只显示名称的小格子：每格约 72dp，常规屏 3~4 列，卡片高度约 68dp
        columns = GridCells.Adaptive(minSize = 88.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.Space3, end = AppDimens.Space3,
            top = AppDimens.Space1, bottom = AppDimens.Space6
        ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space3),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space3)
    ) {
        gridItems(
            items = rows,
            key = { it.key },
            contentType = { if (it.isHeader) "header" else if (it.isPlaceholder) "empty" else "site" },
            span = { row ->
                if (row.isHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }
        ) { row ->
            when {
                row.isHero -> Column {
                    SectionHeader(title = row.title.orEmpty(), onMore = null)
                    HeroRow(sites = row.heroSites, onOpen = onOpen)
                }

                row.isHeader -> SectionHeader(
                    title = row.title.orEmpty(),
                    onMore = row.moreCategory?.let { c -> { onSelectCategory(c) } }
                )

                row.isPlaceholder -> EmptySlotCard(row)

                else -> {
                    val site = row.site ?: return@gridItems
                    val favorited = row.favoriteSlotIndex >= 0 || favoriteIds.contains(site.id)
                    SiteCard(
                        site = site,
                        isFavorite = favorited,
                        onToggleFavorite = { onToggleFavorite(site) },
                        onClick = { onOpen(site) },
                        onLongDelete = if (row.removable && !site.builtIn) {
                            { onRemoveCustom(site.id) }
                        } else null
                    )
                }
            }
        }
    }
}

/** 首屏「编辑精选」：横滑渐变大卡，模仿导航站的 Hero 区 */
@Composable
private fun HeroRow(sites: List<RepoSite>, onOpen: (RepoSite) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 2.dp)
    ) {
        items(sites, key = { it.id }) { site ->
            val idx = AvatarColors.indexOf(avatarColor(site.name)).coerceAtLeast(0)
            val c1 = AvatarColors[idx]
            val c2 = AvatarColors[(idx + 3) % AvatarColors.size]
            // 渐变里含亮青/亮橙，固定白字不可读；按起始色亮度取前景
            val heroFg = onColorFor(c1)
            Column(
                modifier = Modifier
                    // 精选卡只留名称 + 分类，尺寸收到 140×56
                    .width(140.dp)
                    .heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(heroGradient(c1, c2))
                    .clickable(role = Role.Button) { onOpen(site) }
                    .semantics { contentDescription = site.name }
                    .padding(horizontal = AppDimens.Space2, vertical = AppDimens.Space2)
            ) {
                Text(
                    text = site.name,
                    color = heroFg,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = site.category.short + " · 打开",
                    color = heroFg,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun heroGradient(c1: Color, c2: Color) =
    androidx.compose.ui.graphics.Brush.linearGradient(listOf(c1, c2))

@Composable
private fun SectionHeader(title: String, onMore: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.Space2, bottom = AppDimens.Space1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppDimens.Space2)
        ) {}
        Spacer(modifier = Modifier.width(AppDimens.Space2))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        if (onMore != null) {
            TextButton(onClick = onMore, modifier = Modifier.minTouchTarget()) {
                Text("更多", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteCard(
    site: RepoSite,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onLongDelete: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val toggleFavorite = onToggleFavorite
    val delete = onLongDelete
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 软链接式小格子：图标在上，名称 + 一行描述在下
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 星标取消常驻，改成长按卡片弹出「收藏 / 取消收藏」
                    .combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = if (isFavorite) "取消收藏" else "收藏",
                        onLongClick = { menuExpanded = true },
                        onClick = onClick
                    )
                    .semantics { contentDescription = site.name }
                    .padding(horizontal = AppDimens.Space1, vertical = AppDimens.Space2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(avatarColor(site.name)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = site.name.take(1),
                            color = onColorFor(avatarColor(site.name)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (site.isNew) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .semantics { contentDescription = "新收录" }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.Space1))
                // 只留名称，不再显示功能简介
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (toggleFavorite != null) {
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "取消收藏" else "收藏到常用") },
                        leadingIcon = {
                            Icon(
                                if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            toggleFavorite()
                        }
                    )
                }
                if (!site.builtIn && delete != null) {
                    DropdownMenuItem(
                        text = { Text("删除这条") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            delete()
                        }
                    )
                }
            }
        }
    }
}

/** 「常用」页的空占位卡：收藏空槽 / 暂无最近使用 */
@Composable
private fun EmptySlotCard(row: RepoGridRow) {
    val isFav = row.favoriteSlotIndex >= 0
    val title = if (isFav) "收藏位 ${row.favoriteSlotIndex + 1}" else "暂无最近使用"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        // 与站点卡同样的竖向小格子，保证网格每行高度一致
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space1, vertical = AppDimens.Space2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (isFav) Icons.Outlined.Star else Icons.Default.History,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(AppDimens.Space1))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ApkPanel(
    apks: List<File>,
    onImport: () -> Unit,
    onInstall: (File) -> Unit,
    onDelete: (File) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("我的 APK · ${apks.size}", null)
            Text(
                text = "把手机上下载好的安装包存进仓库，随时可以安装或删除。\n文件只保存在本 App 私有目录，不会上传到任何地方。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("从手机导入 APK")
            }
        }
        if (apks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Android,
                    title = "还没有 APK",
                    subtitle = "点上方按钮，把手机里下载好的安装包存进仓库"
                )
            }
        }
        items(apks, key = { it.absolutePath }) { file ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = "APK 安装包",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "%.1f MB".format(file.length() / 1024f / 1024f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onInstall(file) }) {
                        Icon(Icons.Default.Download, contentDescription = "安装", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDelete(file) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSiteDialog(
    defaultCategory: RepoCategory,
    onDismiss: () -> Unit,
    onSave: (String, String, String, RepoCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(defaultCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加网址到仓库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("网址") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("简介（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("归类到", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RepoCategory.entries.filter { it != RepoCategory.MY_APK && it != RepoCategory.RECENT }
                        .forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c.short) }
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url, desc, category) }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 仓库内自带的网页视图：点网址不跳外部浏览器 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RepoWebPage(url: String, onClose: () -> Unit) {
    val holder = remember { arrayOfNulls<WebView>(1) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf(url) }
    var isLoading by remember { mutableStateOf(true) }

    BackHandler(enabled = true) {
        if (canGoBack) holder[0]?.goBack() else onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                holder[0]?.stopLoading()
                holder[0]?.destroy()
            }
            holder[0] = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回仓库")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (isLoading) holder[0]?.stopLoading() else holder[0]?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth((progress / 100f).coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    holder[0] = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val scheme = request?.url?.scheme?.lowercase().orEmpty()
                            return scheme != "http" && scheme != "https"
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            isLoading = newProgress < 100
                        }

                        override fun onReceivedTitle(view: WebView?, pageTitle: String?) {
                            title = pageTitle.orEmpty()
                        }
                    }
                    loadUrl(url)
                }
            }
        )
    }
}
