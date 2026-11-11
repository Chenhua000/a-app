package com.example.myfirstapp.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.TimeSlotEntity
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.minTouchTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotSettingsScreen(
    onBack: () -> Unit,
    viewModel: SlotSettingsViewModel = viewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TimeSlotEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space2)
        ) {
            Text(
                text = "共 ${slots.size} 行，表格的行由这里决定",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { viewModel.resetDefaults() }) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(AppDimens.IconSmall))
                Text("恢复默认")
            }
            Button(onClick = {
                editing = TimeSlotEntity(
                    sortIndex = slots.size,
                    label = "第${slots.size + 1}节",
                    startMinute = 8 * 60,
                    endMinute = 8 * 60 + 45
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AppDimens.IconSmall))
                Text("加一行")
            }
        }

        if (slots.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Add,
                title = "还没有任何节次",
                subtitle = "点「加一行」自定义每天的课程时间段，例如 08:00 - 08:45",
                action = {
                    Button(onClick = {
                        editing = TimeSlotEntity(
                            sortIndex = 0,
                            label = "第1节",
                            startMinute = 8 * 60,
                            endMinute = 8 * 60 + 45
                        )
                    }) { Text("设置节次") }
                }
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = AppDimens.Space4,
                vertical = AppDimens.Space2
            )
        ) {
            itemsIndexed(slots, key = { _, item -> item.id }) { index, slot ->
                SlotRow(
                    slot = slot,
                    canMoveUp = index > 0,
                    canMoveDown = index < slots.lastIndex,
                    onEdit = { editing = slot },
                    onMoveUp = { viewModel.moveUp(slot) },
                    onMoveDown = { viewModel.moveDown(slot) },
                    onDelete = { viewModel.delete(slot) }
                )
            }
        }
    }

    editing?.let { slot ->
        SlotEditorSheet(
            slot = slot,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.save(updated)
                editing = null
            }
        )
    }
}

@Composable
private fun SlotRow(
    slot: TimeSlotEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space3, vertical = AppDimens.Space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${TimeText.formatMinute(slot.startMinute)} - ${TimeText.formatMinute(slot.endMinute)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.minTouchTarget()) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.minTouchTarget()) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
            }
            IconButton(onClick = onEdit, modifier = Modifier.minTouchTarget()) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete, modifier = Modifier.minTouchTarget()) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditorSheet(
    slot: TimeSlotEntity,
    onDismiss: () -> Unit,
    onSave: (TimeSlotEntity) -> Unit
) {
    var label by remember(slot.id) { mutableStateOf(slot.label) }
    var startText by remember(slot.id) { mutableStateOf(TimeText.formatMinute(slot.startMinute)) }
    var endText by remember(slot.id) { mutableStateOf(TimeText.formatMinute(slot.endMinute)) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space5, vertical = AppDimens.Space2),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space3)
        ) {
            Text(
                text = if (slot.id == 0L) "新增节次" else "编辑节次",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("标签，如 第1节") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("开始 HH:mm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("结束 HH:mm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space3)) {
                TextButton(onClick = onDismiss, modifier = Modifier.widthIn(min = 100.dp)) { Text("取消") }
                Button(
                    onClick = {
                        val start = TimeText.parseTimeMinute(startText)
                        val end = TimeText.parseTimeMinute(endText)
                        if (start == null || end == null) {
                            error = "时间格式应为 HH:mm，例如 08:00"
                            return@Button
                        }
                        if (end <= start) {
                            error = "结束时间必须晚于开始时间"
                            return@Button
                        }
                        onSave(
                            slot.copy(
                                label = label.trim().ifEmpty { slot.label },
                                startMinute = start,
                                endMinute = end
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(AppDimens.Space4))
        }
    }
}
