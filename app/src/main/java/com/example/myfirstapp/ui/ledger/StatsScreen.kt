package com.example.myfirstapp.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.MoneyFormatter
import com.example.myfirstapp.domain.filterLedgerEntries
import com.example.myfirstapp.domain.Statistics
import com.example.myfirstapp.domain.TransactionType
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.components.StatCard
import com.example.myfirstapp.ui.components.expenseColor
import com.example.myfirstapp.ui.components.incomeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    val typeFilter = selectedType?.let { TransactionType.valueOf(it) }
    val shown = remember(uiState.entries, query, selectedCategory, typeFilter) {
        filterLedgerEntries(uiState.entries, query, selectedCategory, typeFilter)
    }
    val summary = remember(shown) { Statistics.summarize(shown) }
    val filtered = query.isNotBlank() || selectedCategory != null || typeFilter != null
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"), viewModel::export)
    val categoryTotals = remember(uiState.entries) { uiState.entries.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { (_, entries) -> entries.sumOf { it.amountCents } }.toList().sortedByDescending { it.second } }

    // 与记账首页一致：长按流水编辑 / 删除，删除前必须确认
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    val grouped = remember(shown, zone) { shown.groupBy {
        Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone).toLocalDate()
    } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("range") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (range == StatsRange.MONTH) viewModel.moveMonth(-1) else viewModel.moveYear(-1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一个")
                }
                Text(uiState.rangeLabel, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { if (range == StatsRange.MONTH) viewModel.moveMonth(1) else viewModel.moveYear(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一个")
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsRange.entries.forEachIndexed { i, option ->
                    SegmentedButton(selected = range == option, onClick = { viewModel.setRange(option) },
                        shape = SegmentedButtonDefaults.itemShape(i, StatsRange.entries.size)) { Text(option.label) }
                }
            }
        }
        item("summary") {
            Text(if (filtered) "当前筛选合计" else "所选期间合计", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("收入" to summary.incomeCents, "支出" to summary.expenseCents, "结余" to summary.balanceCents).forEach { (label, cents) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(MoneyFormatter.formatCents(cents), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
        item("tools") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = viewModel::goCurrentPeriod) { Text(if (range == StatsRange.MONTH) "回到本月" else "回到今年") }
                TextButton(enabled = shown.isNotEmpty() && !exporting, onClick = {
                    viewModel.prepareExport(shown)
                    exportLauncher.launch("尘剑账单-${month}${if (filtered) "-筛选" else ""}.csv")
                }) { Text(if (exporting) "导出中…" else "导出 CSV") }
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("全部" to null, "支出" to TransactionType.EXPENSE.name, "收入" to TransactionType.INCOME.name)) { (label, value) ->
                    androidx.compose.material3.FilterChip(selected = selectedType == value,
                        onClick = { selectedType = value; selectedCategory = null }, label = { Text(label) })
                }
            }
        }
        item("search") {
            androidx.compose.material3.OutlinedTextField(value = query, onValueChange = { query = it },
                placeholder = { Text("搜索备注、分类或金额") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清空") } })
        }
        if (categoryTotals.isNotEmpty() && typeFilter != TransactionType.INCOME) item("categories") {
            Text("支出分类 · 点击筛选", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryTotals, key = { it.first }) { (category, cents) ->
                    androidx.compose.material3.FilterChip(selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category; selectedType = TransactionType.EXPENSE.name },
                        label = { Text("$category  ¥${MoneyFormatter.formatCents(cents)}") })
                }
            }
        }
        if (filtered) item("clear_filters") {
            TextButton(onClick = { query = ""; selectedCategory = null; selectedType = null }) { Text("清除全部筛选") }
        }
        exportMessage?.let { message -> item("export_status") {
            AppInlineMessage(text = message, onDismiss = viewModel::clearExportMessage)
        } }
        error?.let { message -> item("error") { AppInlineMessage(text = message, isError = true, onDismiss = { error = null }) } }
        if (success) item("saved") { AppInlineMessage(text = "已保存", onDismiss = { success = false }) }
        item("count") { Text("${shown.size} 笔记录 · 长按编辑或删除", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (shown.isEmpty()) item("empty") {
            EmptyState(icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = if (query.isBlank()) "这段时间还没有记录" else "没有匹配的记录",
                subtitle = if (query.isBlank()) "回记账页点「记一笔」添加收支" else "试试其他关键词，或清空筛选", compact = true)
        }
        grouped.forEach { (date, records) ->
            item("date:$date") { Text(formatGroupLabel(date, zone), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary) }
            items(records, key = { "entry:${it.id}" }) { entry ->
                EntryRow(entry = entry, zone = zone, onEdit = { editing = entry }, onDelete = { pendingDelete = entry })
            }
        }
    }

    editing?.let { target ->
        val onResult: (LedgerValidation) -> Unit = { result ->
            when (result) {
                LedgerValidation.Success -> {
                    editing = null
                    error = null
                    success = true
                }
                is LedgerValidation.Error -> {
                    error = result.message
                    success = false
                }
            }
        }
        EntrySheet(
            initial = target,
            onDismiss = { editing = null },
            onSave = { type, amount, note, category, occurredAt, complete ->
                val resultCallback: (LedgerValidation) -> Unit = { result -> complete(result); onResult(result) }
                viewModel.update(target, type, amount, note, resultCallback, category, occurredAt)
            }
        )
    }

    pendingDelete?.let { target ->
        LedgerDeleteConfirmDialog(
            target = target,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(target)
                pendingDelete = null
            }
        )
    }
}

/** 统计页的一条流水：常驻删除按钮已去掉，改成长按弹「编辑 / 删除」 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: TransactionEntity,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = if (entry.type == TransactionType.INCOME) incomeColor() else expenseColor()
    val label = entry.note?.takeIf { it.isNotBlank() }
        ?: if (entry.type == TransactionType.INCOME) "收入" else "支出"
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = "编辑或删除",
                        onLongClick = { menuExpanded = true },
                        onClick = { }
                    )
                    .semantics { contentDescription = "$label，长按可编辑或删除" }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (entry.type == TransactionType.INCOME) {
                            Icons.Default.ArrowDownward
                        } else {
                            Icons.Default.ArrowUpward
                        },
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = entry.category + " · " + com.example.myfirstapp.domain.ReminderPlanner.formattedClock(
                            entry.occurredAtEpochMillis,
                            zone
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${if (entry.type == TransactionType.INCOME) "+" else "-"}${
                        MoneyFormatter.formatCents(entry.amountCents)
                    }",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            LedgerLongPressMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onEdit = onEdit,
                onDelete = onDelete
            )
        }
    }
}

private fun formatGroupLabel(date: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue} 月 ${date.dayOfMonth} 日"
    }
}
