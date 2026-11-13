package com.example.myfirstapp.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.MoneyFormatter
import com.example.myfirstapp.domain.TransactionType
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.components.expenseColor
import com.example.myfirstapp.ui.components.incomeColor
import com.example.myfirstapp.ui.theme.AppDimens

private val RECENT_DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("M月d日")

@Composable
fun LedgerScreen(
    onOpenStats: () -> Unit,
    viewModel: LedgerViewModel = viewModel()
) {
    val summary by viewModel.thisMonthSummary.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    var showEntry by remember { mutableStateOf(false) }
    // null = 新增一笔；非 null = 正在编辑的这条流水
    var entryDraft by remember { mutableStateOf<TransactionEntity?>(null) }
    // 删除不即时执行，先弹确认框
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 原来没有滚动：概览卡 + 两个按钮 + 空状态在小屏会被裁掉标题与说明
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.Space5, vertical = AppDimens.Space2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.Space2))

        // 本月概览：支出 + 收入 + 结余
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "本月支出",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = MoneyFormatter.formatCents(summary.expenseCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(AppDimens.Space4))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MonthMiniStat(
                        label = "收入",
                        value = MoneyFormatter.formatCents(summary.incomeCents),
                        modifier = Modifier.weight(1f)
                    )
                    MonthMiniStat(
                        label = "结余",
                        value = MoneyFormatter.formatCents(summary.balanceCents),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.Space4))

        Button(
            onClick = {
                entryDraft = null
                showEntry = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "记一笔", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(AppDimens.Space4))

        OutlinedButton(
            onClick = onOpenStats,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Insights, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "统计", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(AppDimens.Space4))

        error?.let { text ->
            AppInlineMessage(
                text = text,
                isError = true,
                onDismiss = { error = null }
            )
        }
        if (success) {
            AppInlineMessage(
                text = "已保存",
                onDismiss = { success = false }
            )
        }

        if (recent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppDimens.Space4))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "最近记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenStats) { Text("全部") }
                    }
                    Text(
                        text = "长按记录可编辑或删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    recent.forEach { item ->
                        RecentRow(
                            transaction = item,
                            onEdit = {
                                entryDraft = item
                                showEntry = true
                            },
                            onDelete = { pendingDelete = item }
                        )
                    }
                }
            }
        } else {
            EmptyState(
                icon = Icons.Default.Info,
                title = "还没有记录",
                subtitle = "记一笔后会出现在这里",
                compact = true
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.Space4))
    }

    if (showEntry) {
        val draft = entryDraft
        val onResult: (LedgerValidation) -> Unit = { result ->
            when (result) {
                LedgerValidation.Success -> {
                    showEntry = false
                    entryDraft = null
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
            initial = draft,
            onDismiss = { showEntry = false },
            onSave = { type, amount, note, category, occurredAt, complete ->
                val resultCallback: (LedgerValidation) -> Unit = { result -> complete(result); onResult(result) }
                if (draft == null) {
                    viewModel.save(type, amount, note, resultCallback, category, occurredAt)
                } else {
                    viewModel.update(draft, type, amount, note, resultCallback, category, occurredAt)
                }
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

/** 概览卡片里的小项：收入 / 结余 */
@Composable
private fun MonthMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** 首页「最近记录」里的一行：常驻删除按钮已去掉，改成长按弹「编辑 / 删除」 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    transaction: TransactionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val income = transaction.type == TransactionType.INCOME
    val amountColor = if (income) incomeColor() else expenseColor()
    val label = transaction.note?.takeIf { it.isNotBlank() } ?: (if (income) "收入" else "支出")
    var menuExpanded by remember { mutableStateOf(false) }

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
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = amountColor.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (income) Icons.Default.SouthWest else Icons.Default.NorthEast,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = transaction.category + " · " + RECENT_DATE_FORMAT.format(
                        java.time.Instant.ofEpochMilli(transaction.occurredAtEpochMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = (if (income) "+" else "-") + MoneyFormatter.formatCents(transaction.amountCents),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = amountColor
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

/**
 * 记账首页与统计页共用的长按菜单。
 * 两处都遵循同一范式：不留常驻按钮 → 长按开菜单 → 删除走 pendingDelete 确认框。
 */
@Composable
internal fun LedgerLongPressMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
                onDismiss()
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
                onDismiss()
                onDelete()
            }
        )
    }
}

/** 删除前统一确认，避免误触直接丢数据 */
@Composable
internal fun LedgerDeleteConfirmDialog(
    target: TransactionEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val label = target.note?.takeIf { it.isNotBlank() }
        ?: (if (target.type == TransactionType.INCOME) "收入" else "支出")
    val amount = MoneyFormatter.formatCents(target.amountCents)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除记录") },
        text = { Text("确定删除「$label · ¥$amount」吗？删除后无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 新增与编辑共用的输入面板：initial 非 null 时预填金额 / 类型 / 备注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(
    initial: TransactionEntity? = null,
    onDismiss: () -> Unit,
    onSave: (TransactionType, String, String, String, Long, (LedgerValidation) -> Unit) -> Unit
) {
    var typeIndex by remember(initial?.id) {
        mutableIntStateOf(if (initial?.type == TransactionType.INCOME) 0 else 1)
    }
    var amount by remember(initial?.id) {
        mutableStateOf(initial?.let { MoneyFormatter.formatCents(it.amountCents) } ?: "")
    }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }

    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "餐饮") }
    val zone = java.time.ZoneId.systemDefault()
    var date by remember(initial?.id) { mutableStateOf(java.time.Instant.ofEpochMilli(initial?.occurredAtEpochMillis ?: System.currentTimeMillis()).atZone(zone).toLocalDate()) }
    var pickDate by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    if (pickDate) {
        val dateState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                dateState.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                pickDate = false
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("取消") } }
        ) { androidx.compose.material3.DatePicker(state = dateState) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (initial == null) "记一笔" else "编辑记录",
                style = MaterialTheme.typography.headlineSmall
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = typeIndex == 0,
                    onClick = { typeIndex = 0; category = "生活费" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("收入") }
                SegmentedButton(
                    selected = typeIndex == 1,
                    onClick = { typeIndex = 1; category = "餐饮" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("支出") }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("金额") },
                prefix = { Text("¥") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium
            )

            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("日期  $date  · 点击修改")
            }
            Text("分类", style = MaterialTheme.typography.labelLarge)
            val categories = if (typeIndex == 0) listOf("工资", "生活费", "兼职", "退款", "其他")
                else listOf("餐饮", "交通", "购物", "学习", "娱乐", "住房", "医疗", "其他")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.size) { i ->
                    androidx.compose.material3.FilterChip(selected = category == categories[i],
                        onClick = { category = categories[i] }, label = { Text(categories[i]) })
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可不填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val cents = com.example.myfirstapp.domain.MoneyParser.parseYuanToCents(amount)
                        if (cents == null || cents <= 0) {
                            inputError = "请输入大于 0 的金额，最多两位小数"
                            return@Button
                        }
                        saving = true
                        onSave(
                            if (typeIndex == 0) TransactionType.INCOME else TransactionType.EXPENSE,
                            amount,
                            note,
                            category,
                            date.atTime(java.time.Instant.ofEpochMilli(initial?.occurredAtEpochMillis ?: System.currentTimeMillis()).atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
                            { result ->
                                saving = false
                                inputError = (result as? LedgerValidation.Error)?.message
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !saving
                ) { Text(if (saving) "保存中…" else if (initial == null) "保存" else "保存修改") }
            }
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}
