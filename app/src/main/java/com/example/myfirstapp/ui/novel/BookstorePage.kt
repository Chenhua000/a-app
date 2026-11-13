package com.example.myfirstapp.ui.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.data.bookstore.BookstoreData
import com.example.myfirstapp.data.bookstore.StoreBook
import com.example.myfirstapp.data.bookstore.StoreCategory
import com.example.myfirstapp.data.bookstore.StoreRank
import com.example.myfirstapp.ui.components.BookCover

/** 参考「笔趣阁」书库首页：搜索 + 男女频道 + 分类卡 + 排行榜（封面走真实网络图） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookstorePage(
    data: BookstoreData?,
    channel: String,
    onChannelChange: (String) -> Unit,
    onSearchHistory: () -> Unit,
    onSearch: (String, String?) -> Unit,
    onOpenSources: () -> Unit
) {
    var selected by remember { mutableStateOf<StoreCategory?>(null) }
    var selectedBook by remember { mutableStateOf<StoreBook?>(null) }
    var period by remember { mutableStateOf("day") }
    var showAllRank by remember { mutableStateOf(false) }
    val current = if (channel == "female") data?.female else data?.male

    if (selectedBook != null) {
        BookDetailPage(
            book = selectedBook!!,
            onBack = { selectedBook = null },
            onRead = { onSearch(selectedBook!!.title, selectedBook!!.cover) }
        )
        return
    }

    if (selected != null) {
        CategoryBooksPage(
            category = selected!!,
            onBack = { selected = null },
            onOpenBook = { book -> selectedBook = book }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = "书库",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "书架共享一键直达",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 搜索框 + 男 / 女频道（文字切换，贴近参考 App）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Button) { onSearchHistory() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "搜索书名或作者",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                ChannelTab("男生", channel == "male") { onChannelChange("male") }
                ChannelTab("女生", channel == "female") { onChannelChange("female") }
            }
        }

        Text(
            text = "分类",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        current?.categories.orEmpty().chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { category ->
                    CategoryCard(
                        category = category,
                        modifier = Modifier.weight(1f),
                        onClick = { selected = category }
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "排行榜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            listOf("day" to "日榜", "week" to "周榜", "month" to "月榜").forEach { (key, label) ->
                FilterChip(
                    selected = period == key,
                    onClick = { period = key; showAllRank = false },
                    label = { Text(label) }
                )
                Spacer(Modifier.width(4.dp))
            }
        }

        val ranks = current?.ranks?.get(period).orEmpty()
        ranks.take(if (showAllRank) 10 else 6).forEachIndexed { index, rank ->
            RankRow(index + 1, rank) { book -> selectedBook = book }
        }
        if (ranks.isEmpty()) {
            Text(
                text = "暂无榜单数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (ranks.size > 6) {
            TextButton(onClick = { showAllRank = !showAllRank }) {
                Text(if (showAllRank) "收起" else "查看更多")
            }
        }

        // 大家都在搜（放在分类与榜单之后，避免挤占首屏）
        if (current?.hot?.isNotEmpty() == true) {
            Text(
                text = "大家都在搜",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.hot.take(6).forEach { word ->
                    FilterChip(
                        selected = false,
                        onClick = { onSearch(word, null) },
                        label = { Text(word, maxLines = 1) }
                    )
                }
            }
        }

        // 书源提示：全量导入但默认停用，避免一次启用拖慢搜索
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = "已内置 1744 个书源（默认全部停用）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "到书源管理按分组批量启用，或跑自检筛出能用的源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TextButton(onClick = onOpenSources) { Text("去书源管理") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ChannelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryCard(
    category: StoreCategory,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(66.dp)
            .clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 真实封面：加载失败时 BookCover 自动回退到书名首字色块
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                category.books.take(2).forEach { book ->
                    BookCover(
                        title = book.title,
                        coverUrl = book.cover,
                        modifier = Modifier.width(27.dp)
                    )
                }
            }
        }
    }
}

/** 点书先进详情页：大封面 + 书名 / 作者 / 简介，再决定要不要搜书源 */
@Composable
private fun BookDetailPage(
    book: StoreBook,
    onBack: () -> Unit,
    onRead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回书库")
            }
            Text(
                text = "书籍详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Row {
            BookCover(
                title = book.title,
                coverUrl = book.cover,
                modifier = Modifier.width(112.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = book.author.ifBlank { "佚名" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (book.intro.isNotBlank()) {
            Text(
                text = "简介",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = book.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onRead, modifier = Modifier.fillMaxWidth()) {
            Text("搜索并阅读")
        }
        Text(
            text = "将从已启用的书源里搜索这本书；封面与简介来自书库内置数据。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RankRow(index: Int, rank: StoreRank, onOpen: (StoreBook) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) {
                onOpen(StoreBook(rank.title, rank.author, rank.cover, rank.desc))
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (index <= 3) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        BookCover(
            title = rank.title,
            coverUrl = rank.cover,
            modifier = Modifier.width(34.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = rank.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(rank.author, rank.tag).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (rank.score.isNotBlank()) {
            Text(
                text = rank.score,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 点分类卡后进入的书单，点书名先进详情页看封面与简介 */
@Composable
private fun CategoryBooksPage(
    category: StoreCategory,
    onBack: () -> Unit,
    onOpenBook: (StoreBook) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回书库")
            }
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = category.subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        category.books.forEach { book -> BookRow(book, onOpenBook) }
    }
}

@Composable
private fun BookRow(book: StoreBook, onOpen: (StoreBook) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(book) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp)) {
            BookCover(
                title = book.title,
                coverUrl = book.cover,
                modifier = Modifier.width(52.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author.ifBlank { "佚名" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = book.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
