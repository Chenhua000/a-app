package com.example.myfirstapp.ui.novel

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.BookSourceEntity
import com.example.myfirstapp.data.repository.BookRepository
import com.example.myfirstapp.data.repository.DefaultBookSources
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.minTouchTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SourceManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.bookRepository

    val sources: StateFlow<List<BookSourceEntity>> = repository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    private val _testProgress = MutableStateFlow(Pair(0, 0))
    val testProgress: StateFlow<Pair<Int, Int>> = _testProgress

    private val cancelFlag = AtomicBoolean(false)

    fun toggle(source: BookSourceEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setSourceEnabled(source.id, enabled) }
    }

    fun delete(source: BookSourceEntity) {
        viewModelScope.launch { repository.deleteSource(source.id) }
    }

    /** 批量启用 / 停用 */
    fun setEnabled(ids: Collection<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.setSourcesEnabled(ids.toList(), enabled)
            _message.value = "已${if (enabled) "启用" else "停用"} ${ids.size} 个书源"
        }
    }

    /** 批量删除 */
    fun deleteMany(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteSources(ids.toList())
            _message.value = "已删除 ${ids.size} 个书源"
        }
    }

    fun resetDefaults() {
        viewModelScope.launch { repository.resetDefaultSources() }
    }

    /** 一键导入 App 内置的推荐书源包（默认停用，用户自己按分组启用） */
    fun importBundled() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "正在导入内置书源包…"
            val count = runCatching {
                repository.importBundledSources(getApplication(), enabled = false)
            }.getOrDefault(0)
            _busy.value = false
            _message.value = if (count > 0) {
                "已导入 $count 个书源（默认停用，勾选你常用的启用）"
            } else {
                "导入失败，内置书源包读取不到"
            }
        }
    }

    fun import(text: String) {
        viewModelScope.launch {
            val count = runCatching { repository.importSources(text) }.getOrDefault(0)
            _message.value = if (count > 0) "成功导入 $count 个书源" else "导入失败，请检查 JSON 格式"
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _message.value = "正在下载书源…"
            val count = runCatching { repository.importFromUrl(url) }.getOrDefault(0)
            _message.value = if (count > 0) "已从网址导入 $count 个书源" else "下载或解析失败，换个链接试试"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 书源自动发现：给一个站点网址，手机端试着逆推出搜索/目录/正文规则并直接导入。
     * 用热门书做试探词（大多数国内小说站都搜得到），用户也可以在界面上改。
     */
    fun discover(url: String, keyword: String = "斗破苍穹") {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            _message.value = "请先填写要发现的网址"
            return
        }
        viewModelScope.launch {
            _discovering.value = true
            _message.value = "正在访问 $trimmed 并尝试识别书源规则…"
            val result = runCatching { repository.discoverSource(trimmed, keyword) }.getOrNull()
            _discovering.value = false
            when {
                result == null -> _message.value = "发现失败（网络异常，请检查网址或稍后再试）"
                !result.ok -> _message.value = "发现失败：${result.message}"
                else -> {
                    val count = runCatching { repository.importSources(result.rulesJson) }.getOrDefault(0)
                    _message.value = if (count > 0) {
                        "已导入：${result.name}（${result.message}）"
                    } else {
                        "规则已识别但未导入成功（JSON 异常）"
                    }
                }
            }
        }
    }

    /** 在手机上逐个测当前所有书源，能搜到书的标「可用」，搜不到 / 连不上的标出来 */
    fun testAll() {
        val ids = sources.value.map { it.id }
        runTest(ids, "自检完成")
    }

    /** 只测勾选的书源（适合按分组挑一批来测） */
    fun testSelected(ids: Collection<String>) {
        if (ids.isEmpty()) return
        runTest(ids.toList(), "选中源自检完成")
    }

    private fun runTest(ids: List<String>, doneTitle: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _testing.value = true
            _testProgress.value = 0 to ids.size
            cancelFlag.set(false)
            repository.runSourceTest(ids, { cancelFlag.get() }) { d, t -> _testProgress.value = d to t }
            _testing.value = false
            // 测完统计一下，给用户一个结论
            val all = repository.countStatuses()
            _message.value = "$doneTitle：可用 ${all.ok} / 不可用 ${all.fail} / 连不上 ${all.timeout} / 未测 ${all.untested}"
        }
    }

    /** 取消正在进行的批量自检 */
    fun cancelTest() {
        cancelFlag.set(true)
    }

    /** 单独测一个书源（点行上的「测试」） */
    fun testOne(source: BookSourceEntity) {
        viewModelScope.launch {
            val r = repository.testSource(source)
            repository.saveSourceStatus(source.id, r.status, System.currentTimeMillis())
            _message.value = "${source.name}：${r.message}"
        }
    }
}

@Composable
fun SourceManagerScreen(
    onBack: () -> Unit,
    viewModel: SourceManagerViewModel = viewModel()
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val discovering by viewModel.discovering.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()

    var keyword by remember { mutableStateOf("") }
    var groupFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }
    var discoverUrl by remember { mutableStateOf("") }

    // 分组来自书源自带的分组字段；空分组统一放到「未分组」
    val groups = remember(sources) {
        sources.map { it.groupName.ifBlank { "未分组" } }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
    }

    val visible = remember(sources, keyword, groupFilter, statusFilter) {
        val kw = keyword.trim()
        sources.filter { source ->
            val inGroup = groupFilter == null ||
                (source.groupName.ifBlank { "未分组" }) == groupFilter
            val matchesKeyword = kw.isEmpty() ||
                source.name.contains(kw, ignoreCase = true) ||
                source.baseUrl.contains(kw, ignoreCase = true)
            val matchesStatus = when (statusFilter) {
                null -> true
                "ok" -> source.lastStatus == "ok"
                "bad" -> source.lastStatus == "fail" || source.lastStatus == "timeout"
                "untested" -> source.lastStatus == null
                else -> true
            }
            inGroup && matchesKeyword && matchesStatus
        }
    }

    // 筛选条件一变就清空勾选，避免「看不见但被误删」
    androidx.compose.runtime.LaunchedEffect(keyword, groupFilter, statusFilter) {
        selected.value = emptySet()
    }

    val enabledCount = sources.count { it.enabled }

    // 概览 / 筛选 / 导入等作为 header item，书源列表用 items 虚拟化。
    // 内置 1805 个书源，若用 Column 一次性铺开会直接卡死主线程。
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
        // 概览
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "共 ${sources.size} 个书源",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "已启用 $enabledCount",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "书源越多搜索越慢，建议只启用常去的几个。支持按分组批量开关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.importBundled() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.LibraryBooks,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入书源包", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetDefaults() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("恢复内置", maxLines = 1)
                    }
                }

                // 自检结果统计 + 一键在手机上自检（手机网络才能判定国内小说站）
                val okCount = sources.count { it.lastStatus == "ok" }
                val badCount = sources.count { it.lastStatus == "fail" || it.lastStatus == "timeout" }
                val untestedCount = sources.size - okCount - badCount
                Text(
                    text = "自检：可用 $okCount · 不可用 $badCount · 未测 $untestedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (testing) viewModel.cancelTest() else viewModel.testAll() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (testing) Icons.Default.RestartAlt else Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (testing) "取消自检" else "一键自检全部", maxLines = 1)
                    }
                }
                if (testing) {
                    val (done, total) = testProgress
                    LinearProgressIndicator(
                        progress = if (total > 0) done.toFloat() / total else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "自检中 $done / $total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 搜索 + 导入
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("搜索书源名或网址") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { showImport = !showImport }) {
                Icon(Icons.Default.ContentPaste, contentDescription = "粘贴", modifier = Modifier.height(18.dp))
            }
        }

        // 分组筛选
        if (groups.size > 1) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupChip(
                    text = "全部 ${sources.size}",
                    selected = groupFilter == null,
                    onClick = { groupFilter = null }
                )
                groups.forEach { (name, count) ->
                    GroupChip(
                        text = "$name $count",
                        selected = groupFilter == name,
                        onClick = { groupFilter = if (groupFilter == name) null else name }
                    )
                }
            }
        }

        // 状态筛选（自检后用来快速筛掉连不上的死源 / 挑出能用的）
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GroupChip(text = "全部状态", selected = statusFilter == null, onClick = { statusFilter = null })
            GroupChip(
                text = "可用",
                selected = statusFilter == "ok",
                onClick = { statusFilter = if (statusFilter == "ok") null else "ok" }
            )
            GroupChip(
                text = "不可用",
                selected = statusFilter == "bad",
                onClick = { statusFilter = if (statusFilter == "bad") null else "bad" }
            )
            GroupChip(
                text = "未测",
                selected = statusFilter == "untested",
                onClick = { statusFilter = if (statusFilter == "untested") null else "untested" }
            )
        }

        // 批量操作
        if (visible.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selected.value.isEmpty()) "当前 ${visible.size} 个" else "已选 ${selected.value.size} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(
                    onClick = {
                        selected.value = if (selected.value.size == visible.size) {
                            emptySet()
                        } else {
                            visible.map { it.id }.toSet()
                        }
                    },
                    modifier = Modifier.minTouchTarget()
                ) { Text(if (selected.value.size == visible.size) "取消全选" else "全选") }
                if (selected.value.isNotEmpty()) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.testSelected(selected.value)
                            selected.value = emptySet()
                        },
                        modifier = Modifier.minTouchTarget()
                    ) { Text("测试选中", color = MaterialTheme.colorScheme.primary) }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.setEnabled(selected.value, true)
                            selected.value = emptySet()
                        },
                        modifier = Modifier.minTouchTarget()
                    ) { Text("启用") }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.setEnabled(selected.value, false)
                            selected.value = emptySet()
                        },
                        modifier = Modifier.minTouchTarget()
                    ) { Text("停用") }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.deleteMany(selected.value)
                            selected.value = emptySet()
                        },
                        modifier = Modifier.minTouchTarget()
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        if (showImport) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = importUrl,
                    onValueChange = { importUrl = it },
                    label = { Text("书源 JSON 的网址（一次可导入成百上千个）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.importFromUrl(importUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("从网址导入") }

                // ===== 书源自动发现：填一个站点网址，手机端尝试逆推出规则并导入 =====
                HorizontalDivider()
                Text(
                    text = "没有现成书源？填站点网址，App 自动识别搜索/目录/正文规则：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = discoverUrl,
                    onValueChange = { discoverUrl = it },
                    label = { Text("站点网址，如 https://www.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.discover(discoverUrl) },
                    enabled = !discovering && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (discovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("正在识别…")
                    } else {
                        Text("发现并导入书源")
                    }
                }
                HorizontalDivider()

                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("或直接粘贴书源 JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 5
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { importText = DefaultBookSources.templateJson() },
                        modifier = Modifier.weight(1f)
                    ) { Text("填入模板") }
                    Button(
                        onClick = { viewModel.import(importText) },
                        modifier = Modifier.weight(1f)
                    ) { Text("导入") }
                }
                Button(
                    onClick = { viewModel.importBundled() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("一键导入内置书源包（${DefaultBookSources.bundledCountHint}）") }
            }
        }

        message?.let {
            AppInlineMessage(text = it)
        }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.ContentPaste,
                    title = if (sources.isEmpty()) "还没有书源" else "没有匹配的书源",
                    subtitle = if (sources.isEmpty()) {
                        "点「导入内置书源包」加载推荐书源，或粘贴自己的书源 JSON"
                    } else {
                        "换个关键词，或把分组切回「全部」"
                    }
                )
            }
        } else {
            items(visible, key = { it.id }) { source ->
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SourceRow(
                        source = source,
                        checked = source.id in selected.value,
                        onCheckedChange = { checked ->
                            selected.value = if (checked) {
                                selected.value + source.id
                            } else {
                                selected.value - source.id
                            }
                        },
                        onToggle = { viewModel.toggle(source, it) },
                        onDelete = { viewModel.delete(source) },
                        onTest = { viewModel.testOne(source) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun GroupChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun SourceRow(
    source: BookSourceEntity,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (source.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (source.builtIn) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "内置",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                Text(
                    text = source.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (source.groupName.isNotBlank()) {
                        Text(
                            text = source.groupName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    StatusBadge(source.lastStatus)
                }
            }
            Switch(checked = source.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onTest) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "测试这个书源",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String?) {
    val (label, color) = when (status) {
        "ok" -> "可用" to MaterialTheme.colorScheme.primary
        "fail" -> "搜不到" to MaterialTheme.colorScheme.tertiary
        "timeout" -> "连不上" to MaterialTheme.colorScheme.error
        else -> "未测" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
