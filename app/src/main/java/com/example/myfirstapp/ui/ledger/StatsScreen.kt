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
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.MoneyFormatter
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
    // 与记账首页一致：长按流水编辑 / 删除，删除前必须确认
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (range == StatsRange.MONTH) viewModel.moveMonth(-1) else viewModel.moveYear(-1)
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "上一个")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.rangeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val hint = if (range == StatsRange.MONTH) {
                    "${month.year} 年 ${month.monthValue} 月全部流水"
                } else {
                    "${month.year} 年全部流水"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                if (range == StatsRange.MONTH) viewModel.moveMonth(1) else viewModel.moveYear(1)
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "下一个")
            }
        }

        error?.let { text ->
            AppInlineMessage(
                text = text,
                isError = true,
                modifier = Modifier.padding(horizontal = 16.dp),
                onDismiss = { error = null }
            )
        }
        if (success) {
            AppInlineMessage(
                text = "已保存",
                modifier = Modifier.padding(horizontal = 16.dp),
                onDismiss = { success = false }
            )
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SegmentedButton(
                selected = range == StatsRange.MONTH,
                onClick = { viewModel.setRange(StatsRange.MONTH) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(StatsRange.MONTH.label) }
            SegmentedButton(
                selected = range == StatsRange.YEAR,
                onClick = { viewModel.setRange(StatsRange.YEAR) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(StatsRange.YEAR.label) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "结余",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = MoneyFormatter.formatCents(uiState.summary.balanceCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = "收入",
                amountCents = uiState.summary.incomeCents,
                highlightColor = incomeColor(),
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "支出",
                amountCents = uiState.summary.expenseCents,
                highlightColor = expenseColor(),
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.entries.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "这段时间还没有记录",
                subtitle = "回记账页点「记一笔」添加第一笔收支",
                compact = true
            )
        } else {
            Text(
                text = "长按流水可编辑或删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val grouped = uiState.entries.groupBy {
                    Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone).toLocalDate()
                }
                grouped.forEach { (date, items) ->
                    item {
                        Text(
                            text = formatGroupLabel(date, zone),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(items, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            zone = zone,
                            onEdit = { editing = entry },
                            onDelete = { pendingDelete = entry }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
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
            onSave = { type, amount, note ->
                viewModel.update(target, type, amount, note, onResult)
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
                        text = com.example.myfirstapp.domain.ReminderPlanner.formattedClock(
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
