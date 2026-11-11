package com.example.myfirstapp.ui.idea

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.IdeaEntity
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 列表排序方式 */
private enum class IdeaSort { UPDATED, CREATED, TITLE }

@Composable
fun IdeasScreen(
    onOpenEditor: (Long) -> Unit,
    viewModel: IdeaListViewModel = viewModel()
) {
    val ideas by viewModel.ideas.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(IdeaSort.UPDATED) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<IdeaEntity?>(null) }

    val allTags = remember(ideas) {
        ideas.flatMap { extractTags(it.title + " " + it.content) }.distinct()
    }

    val filtered = remember(ideas, query, selectedTag, sort) {
        val q = query.trim()
        ideas
            .filter { if (selectedTag == null) true else extractTags(it.title + " " + it.content).contains(selectedTag) }
            .filter {
                if (q.isBlank()) true
                else it.title.contains(q, ignoreCase = true) ||
                    it.content.contains(q, ignoreCase = true)
            }
            .let { list ->
                when (sort) {
                    IdeaSort.UPDATED -> list.sortedByDescending { it.updatedAtMillis }
                    IdeaSort.CREATED -> list.sortedByDescending { it.createdAtMillis }
                    IdeaSort.TITLE -> list.sortedBy { it.title.ifBlank { "\uFFFF" } }
                }
            }
            // 置顶恒在最前，其余保持所选排序
            .sortedByDescending { it.pinned }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 内含式搜索栏（Material 3 Expressive 风格）
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索灵感或 #标签") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isBlank()) null else {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // 头部：计数 + 排序切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (filtered.isEmpty()) "灵感" else "${filtered.size} 条灵感",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SortMenu(current = sort) { sort = it }
            }

            // 标签筛选 chips（Flomo 风格：#标签即分类）
            if (allTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allTags.size) { idx ->
                        val tag = allTags[idx]
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text("#$tag") },
                            leadingIcon = if (selectedTag == tag) {
                                { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                val isEmptyDb = ideas.isEmpty()
                EmptyState(
                    icon = Icons.Default.Lightbulb,
                    title = when {
                        isEmptyDb -> "还没有灵感"
                        query.isNotBlank() || selectedTag != null -> "没有匹配的灵感"
                        else -> "还没有灵感"
                    },
                    subtitle = when {
                        isEmptyDb -> "脑中闪过的想法，点右下角「写想法」马上记下来"
                        query.isNotBlank() || selectedTag != null -> "换个关键词或标签试试"
                        else -> "开始记录你的第一个灵感吧"
                    },
                    action = null
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    items(filtered, key = { it.id }) { idea ->
                        IdeaCard(
                            idea = idea,
                            zone = zone,
                            onClick = { onOpenEditor(idea.id) },
                            onMenu = { menuId = idea.id },
                            menuExpanded = menuId == idea.id,
                            onDismissMenu = { menuId = null },
                            onTogglePin = {
                                viewModel.togglePin(idea)
                                menuId = null
                            },
                            onRequestDelete = {
                                pendingDelete = idea
                                menuId = null
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onOpenEditor(newIdeaKey()) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "写想法")
        }
    }

    // 删除确认弹窗
    pendingDelete?.let { idea ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条灵感？") },
            text = {
                Text(
                    (idea.title.ifBlank { idea.content })
                        .lineSequence().firstOrNull().orEmpty()
                        .let { if (it.length > 40) it.take(40) + "…" else it }
                        .ifBlank { "这条灵感" }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(idea)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SortMenu(
    current: IdeaSort,
    onPick: (IdeaSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = when (current) {
                    IdeaSort.UPDATED -> "最近修改"
                    IdeaSort.CREATED -> "创建时间"
                    IdeaSort.TITLE -> "标题"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("最近修改") },
                onClick = { onPick(IdeaSort.UPDATED); expanded = false },
                leadingIcon = if (current == IdeaSort.UPDATED) {
                    { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("创建时间") },
                onClick = { onPick(IdeaSort.CREATED); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("标题") },
                onClick = { onPick(IdeaSort.TITLE); expanded = false }
            )
        }
    }
}

@Composable
private fun IdeaCard(
    idea: IdeaEntity,
    zone: ZoneId,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val bg = ideaNoteColor(idea.colorIndex, dark)
    val fg = ideaTextColor(dark)
    val tags = remember(idea.title, idea.content) {
        extractTags(idea.title + " " + idea.content)
    }
    val softFg = onColorFor(bg).copy(alpha = 0.92f)
    val cardLabel = idea.title.ifBlank { idea.content }.ifBlank { "灵感便签" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardLabel }
            .clickable(onClick = onClick, role = Role.Button),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (idea.title.isNotBlank()) {
                        Text(
                            text = idea.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (idea.content.isNotBlank()) {
                        Text(
                            text = idea.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fg,
                            modifier = Modifier.padding(top = if (idea.title.isNotBlank()) 6.dp else 0.dp),
                            maxLines = if (idea.title.isNotBlank()) 8 else 12,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 右上角：置顶徽标 + 菜单
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (idea.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "已置顶",
                            tint = softFg,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 2.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = onMenu,
                            modifier = Modifier.minTouchTarget()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多操作",
                                tint = softFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = onDismissMenu
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (idea.pinned) "取消置顶" else "置顶") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = onTogglePin
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = onRequestDelete
                            )
                        }
                    }
                }
            }

            // 标签
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = softFg
                        )
                    }
                }
            }

            // 时间 + 顶部渐隐分隔
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = relativeTime(idea.updatedAtMillis, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = softFg,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}

/** 相对时间：今天 HH:mm / 昨天 HH:mm / N天前 / M月d日 / 年月日 */
internal fun relativeTime(millis: Long, zone: ZoneId): String {
    if (millis <= 0L) return ""
    val then = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    val now = LocalDateTime.now(zone)
    val days = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    return when {
        days == 0L -> "今天 ${then.format(hm)}"
        days == 1L -> "昨天 ${then.format(hm)}"
        days < 7L -> "${days}天前"
        then.year == now.year -> "${then.monthValue}月${then.dayOfMonth}日"
        else -> "${then.year}年${then.monthValue}月${then.dayOfMonth}日"
    }
}

/** 从文本中提取 #标签（Flomo 风格），返回去重后的标签列表 */
private fun extractTags(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val regex = Regex("""#([^\s#@]+)""")
    return regex.findAll(text).map { it.groupValues[1] }.toList().distinct()
}

/** 新建便签用的唯一键：用纳秒时间取负，保证每次打开编辑器都是全新的 ViewModel */
private fun newIdeaKey(): Long {
    val n = System.nanoTime()
    return if (n >= 0) -n else n
}
