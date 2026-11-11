package com.example.myfirstapp.ui.shortcuts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.shortcuts.AppShortcut
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.minTouchTarget
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutScreen(
    viewModel: ShortcutViewModel = viewModel()
) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // null = 关闭；非 null = 正在编辑的草稿（新增或已有）
    var draft by remember { mutableStateOf<AppShortcut?>(null) }
    // null = 无待删除项；非 null = 正在等待确认删除的快捷方式
    var pendingDelete by remember { mutableStateOf<AppShortcut?>(null) }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(2600)
        viewModel.consumeMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.refreshInstalledApps() }) {
                    Text("刷新应用")
                }
            }
            Text(
                text = "点图标打开应用 · 长按图标可编辑或删除",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (shortcuts.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Link,
                    title = "还没有快捷方式",
                    subtitle = "点右下角 + 添加一个应用快捷方式"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(shortcuts, key = { it.id }) { item ->
                        ShortcutTile(
                            item = item,
                            onClick = { viewModel.launch(item.packageName) },
                            onEdit = { draft = item.copy() },
                            // 不直接删，先弹确认
                            onDelete = { pendingDelete = item }
                        )
                    }
                }
            }
        }

        SmallFloatingActionButton(
            onClick = {
                draft = AppShortcut(
                    id = viewModel.nextId(),
                    name = "",
                    iconIndex = 0,
                    packageName = ""
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加软链接", modifier = Modifier.size(22.dp))
        }

        message?.let { text ->
            AppInlineMessage(
                text = text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppDimens.Space6),
                onDismiss = { viewModel.consumeMessage() }
            )
        }
    }

    draft?.let { current ->
        ShortcutEditorSheet(
            draft = current,
            apps = installedApps,
            isNew = shortcuts.none { it.id == current.id },
            onDismiss = { draft = null },
            onSave = {
                viewModel.save(it)
                draft = null
            },
            // 编辑面板里的删除也走同一个确认对话框
            onDelete = { id ->
                pendingDelete = shortcuts.firstOrNull { it.id == id }
            }
        )
    }

    pendingDelete?.let { target ->
        val label = target.name.ifBlank { "这个快捷方式" }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除软链接") },
            text = { Text("确定删除「$label」吗？删除后需要重新添加。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id)
                        pendingDelete = null
                        draft = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    item: AppShortcut,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = item.name }
                    // 右上角编辑按钮已去掉，改成长按图标弹「编辑 / 删除」
                    .combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = "编辑或删除",
                        onLongClick = { menuExpanded = true },
                        onClick = onClick
                    )
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(AnimeIconCatalog.resOf(item.iconIndex)),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimens.IconSmall)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimens.IconSmall)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutEditorSheet(
    draft: AppShortcut,
    apps: List<com.example.myfirstapp.data.shortcuts.InstalledApp>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (AppShortcut) -> Unit,
    onDelete: (Long) -> Unit
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var iconIndex by remember(draft.id) { mutableStateOf(draft.iconIndex) }
    var packageName by remember(draft.id) { mutableStateOf(draft.packageName) }
    var query by remember(draft.id) { mutableStateOf("") }
    var error by remember(draft.id) { mutableStateOf<String?>(null) }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // 整个面板只用一个 LazyColumn，避免嵌套滚动（嵌套 Lazy 会崩）
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "添加软链接" else "编辑软链接",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称") },
                    placeholder = { Text("例如：刷视频") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = "选一个图标（${AnimeIconCatalog.count} 个）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                // 30 个图标：非懒加载的 5 列小网格，放在单个 Lazy item 里
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimeIconCatalog.icons.chunked(5).forEachIndexed { rowIdx, rowRes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowRes.forEachIndexed { colIdx, res ->
                                val idx = rowIdx * 5 + colIdx
                                val selected = idx == iconIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .minTouchTarget()
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(
                                            if (selected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(14.dp)
                                            ) else Modifier
                                        )
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = { iconIndex = idx }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(res),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                                .size(18.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "已选中",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(5 - rowRes.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = "链接到手机上的应用",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索应用名") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "没有找到可启动的应用，点页面顶部「刷新应用」重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(filtered, key = { it.packageName }) { app ->
                val selected = app.packageName == packageName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .clickable { packageName = app.packageName; error = null }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已选",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            item {
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isNew) {
                        OutlinedButton(
                            onClick = { onDelete(draft.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("删除")
                        }
                    }
                    Button(
                        onClick = {
                            when {
                                name.trim().isEmpty() -> error = "请填写名称"
                                packageName.isBlank() -> error = "请选择要链接的应用"
                                else -> onSave(
                                    draft.copy(
                                        name = name.trim(),
                                        iconIndex = iconIndex,
                                        packageName = packageName
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (isNew) "添加" else "保存", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
