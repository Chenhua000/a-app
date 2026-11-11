package com.example.myfirstapp.ui.task

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.TaskEntity
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor
import kotlinx.coroutines.launch
import kotlin.comparisons.compareByDescending
import kotlin.comparisons.thenBy
import kotlin.math.roundToInt
import com.example.myfirstapp.data.local.TaskCompletionEntity
import com.example.myfirstapp.domain.TASK_DURATION_LONG
import com.example.myfirstapp.domain.TASK_DURATION_SHORT
import com.example.myfirstapp.domain.TASK_REPEAT_DAILY
import com.example.myfirstapp.domain.TASK_REPEAT_ONCE
import com.example.myfirstapp.domain.TASK_REPEAT_WEEKLY
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.domain.durationLabel
import com.example.myfirstapp.domain.occursOn
import com.example.myfirstapp.domain.repeatLabel
import com.example.myfirstapp.domain.timeMinute
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant

@Composable
fun TaskBoardScreen(
    viewModel: TaskBoardViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val completions by viewModel.completions.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    var selectedDay by remember { mutableStateOf(today) }
    var showSheet by remember { mutableStateOf(false) }
    var showMonth by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var noteEdit by remember { mutableStateOf<TaskEntity?>(null) }

    val doneMap = remember(completions) {
        completions.groupBy { it.taskId }.mapValues { it.value.map { c -> c.epochDay }.toSet() }
    }
    val onToggle: (TaskEntity, Long, Boolean) -> Unit = { task, day, done ->
        viewModel.setDone(task.id, day, done)
    }
    val onDelete: (TaskEntity) -> Unit = { pendingDelete = it }
    val onNote: (TaskEntity) -> Unit = { noteEdit = it }
    val onTogglePin: (TaskEntity) -> Unit = { viewModel.setPinned(it, !it.pinned) }
    val onToggleReminder: (TaskEntity) -> Unit = { viewModel.setReminderOn(it, !it.reminderOn) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部只保留月历入口；页面标题已由 AppRoot 顶栏统一给出
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showMonth = true }) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "查看月历",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            WeekStrip(selected = selectedDay, today = today, onSelect = { selectedDay = it })

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (tasks.isEmpty()) {
                    EmptyBoardState(onAdd = { showSheet = true })
                } else {
                    DayTasksView(
                        day = selectedDay,
                        tasks = tasks,
                        zone = zone,
                        doneMap = doneMap,
                        onToggle = onToggle,
                        onDelete = onDelete,
                        onNote = onNote,
                        onTogglePin = onTogglePin,
                        onToggleReminder = onToggleReminder
                    )
                }
            }
        }

        SmallFloatingActionButton(
            onClick = { showSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加任务",
                modifier = Modifier.size(22.dp)
            )
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条任务？") },
            text = { Text(if (task.title.isNotBlank()) task.title else "这条任务（含它的所有重复记录）") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(task)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    noteEdit?.let { task ->
        var text by remember { mutableStateOf(task.note ?: "") }
        AlertDialog(
            onDismissRequest = { noteEdit = null },
            title = { Text("备注") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("写点备注…") },
                    singleLine = false,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.save(task.copy(note = text.trim().takeIf { it.isNotEmpty() }))
                    noteEdit = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { noteEdit = null }) { Text("取消") }
            }
        )
    }

    if (showSheet) {
        TaskEditorSheet(
            defaultDateText = TimeText.formatDate(selectedDay),
            onDismiss = { showSheet = false },
            onSave = { entity ->
                viewModel.save(entity)
                showSheet = false
            }
        )
    }

    if (showMonth) {
        MonthCalendarOverlay(
            tasks = tasks,
            completions = completions,
            zone = zone,
            today = today,
            onToggle = onToggle,
            onDelete = onDelete,
            onNote = onNote,
            onTogglePin = onTogglePin,
            onToggleReminder = onToggleReminder,
            onClose = { showMonth = false }
        )
    }
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val start = TimeText.startOfWeek(selected)
    val days = remember(selected) { (0L..6L).map { start.plusDays(it) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        days.forEach { day ->
            val isSel = day == selected
            val isToday = day == today
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(
                        selected = isSel,
                        role = Role.Tab,
                        onClick = { onSelect(day) }
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = TimeText.weekdayShort(day.dayOfWeek.value),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            if (isToday && !isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun DayTasksView(
    day: LocalDate,
    tasks: List<TaskEntity>,
    zone: ZoneId,
    doneMap: Map<Long, Set<Long>>,
    onToggle: (TaskEntity, Long, Boolean) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onNote: (TaskEntity) -> Unit,
    onTogglePin: (TaskEntity) -> Unit,
    onToggleReminder: (TaskEntity) -> Unit
) {
    val dayTasks = remember(tasks, day, zone) {
        tasks.filter { it.occursOn(day, zone) }
            .sortedWith(compareByDescending<TaskEntity> { it.pinned }.thenBy { it.timeMinute(zone) })
    }
    val dayLong = day.toEpochDay()
    val notDone = dayTasks.filter { !(doneMap[it.id]?.contains(dayLong) ?: false) }
    val done = dayTasks.filter { doneMap[it.id]?.contains(dayLong) ?: false }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (dayTasks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.DateRange,
                    title = "这一天还没有安排",
                    subtitle = "点右下角添加一条任务"
                )
            }
        }
        if (notDone.isNotEmpty()) {
            item { SectionHeader("未完成", notDone.size, MaterialTheme.colorScheme.error) }
            items(notDone, key = { it.id }) {
                TaskSwipeItem(
                    task = it,
                    dayEpochDay = dayLong,
                    zone = zone,
                    doneMap = doneMap,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onNote = onNote,
                    onTogglePin = onTogglePin,
                    onToggleReminder = onToggleReminder
                )
            }
        }
        if (done.isNotEmpty()) {
            item { SectionHeader("已完成", done.size, MaterialTheme.colorScheme.primary) }
            items(done, key = { it.id }) {
                TaskSwipeItem(
                    task = it,
                    dayEpochDay = dayLong,
                    zone = zone,
                    doneMap = doneMap,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onNote = onNote,
                    onTogglePin = onTogglePin,
                    onToggleReminder = onToggleReminder
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AppDimens.Space2)
                .background(accent, CircleShape)
        )
        Spacer(modifier = Modifier.width(AppDimens.Space2))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppDimens.Space2, vertical = AppDimens.Space1)
            )
        }
    }
}

@Composable
private fun TaskItem(
    task: TaskEntity,
    dayEpochDay: Long,
    zone: ZoneId,
    doneMap: Map<Long, Set<Long>>,
    onToggle: (TaskEntity, Long, Boolean) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    val done = doneMap[task.id]?.contains(dayEpochDay) ?: false
    val isLong = task.durationType == TASK_DURATION_LONG
    val accent = if (isLong) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val timeText = TimeText.formatMinute(task.timeMinute(zone))
    val base = MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f).compositeOver(base)
            else accent.copy(alpha = 0.10f).compositeOver(base)
        ),
        border = BorderStroke(
            1.dp,
            if (done) MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            else accent.copy(alpha = 0.30f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleCheckbox(checked = done, accent = accent) {
                onToggle(task, dayEpochDay, !done)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else accent
                    )
                    if (task.repeatType != TASK_REPEAT_ONCE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (task.repeatType == TASK_REPEAT_DAILY)
                                Icons.Default.Autorenew else Icons.Default.DateRange,
                            contentDescription = repeatLabel(task.repeatType),
                            tint = if (done) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = durationLabel(task.durationType),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
                task.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskSwipeItem(
    task: TaskEntity,
    dayEpochDay: Long,
    zone: ZoneId,
    doneMap: Map<Long, Set<Long>>,
    onToggle: (TaskEntity, Long, Boolean) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onNote: (TaskEntity) -> Unit,
    onTogglePin: (TaskEntity) -> Unit,
    onToggleReminder: (TaskEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 四个图标按钮紧凑连成一排的总宽度（4 × 48dp，符合最小触摸区）
    val stripDp = 192.dp
    val openPx = with(density) { stripDp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val closeStrip: () -> Unit = { scope.launch { offsetX.animateTo(0f, tween(220)) } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        // 背景：右侧连成一排的四个图标按钮（无文字、无间隙）
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End
        ) {
            Row(modifier = Modifier.width(stripDp).fillMaxHeight()) {
                SwipeActionCell(MaterialTheme.colorScheme.primary, Icons.Default.Edit, "备注") {
                    onNote(task); closeStrip()
                }
                SwipeActionCell(
                    MaterialTheme.colorScheme.secondary,
                    Icons.Default.PushPin,
                    if (task.pinned) "取消置顶" else "置顶"
                ) {
                    onTogglePin(task); closeStrip()
                }
                SwipeActionCell(
                    MaterialTheme.colorScheme.tertiary,
                    Icons.Default.AlarmAdd,
                    if (task.reminderOn) "关闭提醒" else "定时提醒"
                ) {
                    onToggleReminder(task); closeStrip()
                }
                SwipeActionCell(MaterialTheme.colorScheme.error, Icons.Default.Delete, "删除") {
                    onDelete(task); closeStrip()
                }
            }
        }

        // 前景：任务卡，可左右拖动；最多滑出按钮条宽度，松手停在刚好露出按钮的位置
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-openPx, 0f))
                        }
                    },
                    onDragStopped = {
                        val target = if (offsetX.value <= -openPx / 2f) -openPx else 0f
                        offsetX.animateTo(target, tween(220))
                    }
                )
        ) {
            TaskItem(
                task = task,
                dayEpochDay = dayEpochDay,
                zone = zone,
                doneMap = doneMap,
                onToggle = onToggle,
                onDelete = onDelete
            )
        }
    }
}

/** 连成一排的单个图标按钮：只显示图标，不显示文字 */
@Composable
private fun SwipeActionCell(
    color: Color,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(min = AppDimens.MinTouch)
            .fillMaxHeight()
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = onColorFor(color),
            modifier = Modifier.size(AppDimens.IconMedium)
        )
    }
}

@Composable
private fun CircleCheckbox(
    checked: Boolean,
    accent: Color,
    onToggle: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        animationSpec = tween(160),
        label = "taskCheckBg"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.4f,
        animationSpec = tween(160),
        label = "taskCheckScale"
    )
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .minTouchTarget()
            .clip(CircleShape)
            .background(bg)
            .then(if (checked) Modifier else Modifier.border(2.dp, outline, CircleShape))
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = onColorFor(accent),
                modifier = Modifier.size(16.dp).scale(checkScale)
            )
        }
    }
}

@Composable
private fun EmptyBoardState(onAdd: () -> Unit) {
    EmptyState(
        icon = Icons.Default.TaskAlt,
        title = "在这里安排你的任务",
        subtitle = "把每天、每周的事分好：短任务随手清，长任务慢慢做",
        action = {
            Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加任务", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun MonthCalendarOverlay(
    tasks: List<TaskEntity>,
    completions: List<TaskCompletionEntity>,
    zone: ZoneId,
    today: LocalDate,
    onToggle: (TaskEntity, Long, Boolean) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onNote: (TaskEntity) -> Unit,
    onTogglePin: (TaskEntity) -> Unit,
    onToggleReminder: (TaskEntity) -> Unit,
    onClose: () -> Unit
) {
    val monthState = remember { mutableStateOf(YearMonth.now(zone)) }
    var detailDay by remember { mutableStateOf<LocalDate?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (detailDay != null) detailDay = null else onClose() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (detailDay != null) TimeText.formatDate(detailDay!!) else "${monthState.value.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (detailDay == null) {
                    IconButton(onClick = { monthState.value = monthState.value.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上个月", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { monthState.value = monthState.value.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下个月", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (detailDay == null) {
                MonthGrid(
                    month = monthState.value,
                    tasks = tasks,
                    completions = completions,
                    zone = zone,
                    today = today,
                    onPick = { detailDay = it }
                )
            } else {
                DayDetailList(
                    day = detailDay!!,
                    tasks = tasks,
                    completions = completions,
                    zone = zone,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onNote = onNote,
                    onTogglePin = onTogglePin,
                    onToggleReminder = onToggleReminder
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    tasks: List<TaskEntity>,
    completions: List<TaskCompletionEntity>,
    zone: ZoneId,
    today: LocalDate,
    onPick: (LocalDate) -> Unit
) {
    val doneMap = remember(completions) {
        completions.groupBy { it.taskId }.mapValues { it.value.map { c -> c.epochDay }.toSet() }
    }
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - 1) % 7).toLong()
    val gridStart = first.minusDays(lead)

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            for (i in 1..7) {
                Text(
                    text = TimeText.weekdayShort(i),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        for (w in 0..5) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
                for (d in 0..6) {
                    val date = gridStart.plusDays((w * 7 + d).toLong())
                    val inMonth = date.year == month.year && date.month == month.month
                    DayCell(
                        date = date,
                        inMonth = inMonth,
                        isToday = date == today,
                        tasks = tasks,
                        doneMap = doneMap,
                        zone = zone,
                        onPick = onPick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    tasks: List<TaskEntity>,
    doneMap: Map<Long, Set<Long>>,
    zone: ZoneId,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayLong = date.toEpochDay()
    val dayTasks = remember(tasks, date, zone) { tasks.filter { it.occursOn(date, zone) } }
    val dayDone = remember(dayTasks, doneMap) { dayTasks.map { doneMap[it.id]?.contains(dayLong) ?: false } }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = inMonth) { onPick(date) }
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = if (isToday) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (!inMonth) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            if (isToday) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
            if (inMonth) {
                Spacer(modifier = Modifier.height(3.dp))
                dayTasks.take(3).forEachIndexed { idx, t ->
                    Spacer(modifier = Modifier.height(2.dp))
                    val bar = barColor(t, dayDone[idx], MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(bar),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = t.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = onColorFor(bar),
                            textDecoration = if (dayDone[idx]) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    }
                }
                if (dayTasks.size > 3) {
                    Text(
                        text = "+${dayTasks.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }
        }
    }
}

private fun barColor(task: TaskEntity, done: Boolean, primary: Color, tertiary: Color): Color {
    val base = if (task.durationType == TASK_DURATION_LONG) tertiary else primary
    return if (done) base.copy(alpha = 0.35f) else base
}

@Composable
private fun DayDetailList(
    day: LocalDate,
    tasks: List<TaskEntity>,
    completions: List<TaskCompletionEntity>,
    zone: ZoneId,
    onToggle: (TaskEntity, Long, Boolean) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onNote: (TaskEntity) -> Unit,
    onTogglePin: (TaskEntity) -> Unit,
    onToggleReminder: (TaskEntity) -> Unit
) {
    val doneMap = remember(completions) {
        completions.groupBy { it.taskId }.mapValues { it.value.map { c -> c.epochDay }.toSet() }
    }
    val dayTasks = remember(tasks, day, zone) {
        tasks.filter { it.occursOn(day, zone) }
            .sortedWith(compareByDescending<TaskEntity> { it.pinned }.thenBy { it.timeMinute(zone) })
    }
    val dayLong = day.toEpochDay()
    val notDone = dayTasks.filter { !(doneMap[it.id]?.contains(dayLong) ?: false) }
    val done = dayTasks.filter { doneMap[it.id]?.contains(dayLong) ?: false }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (dayTasks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.DateRange,
                    title = "这一天还没有安排",
                    subtitle = "换一天看看，或返回月历添加任务"
                )
            }
        }
        if (notDone.isNotEmpty()) {
            item { SectionHeader("未完成", notDone.size, MaterialTheme.colorScheme.error) }
            items(notDone, key = { it.id }) {
                TaskSwipeItem(
                    task = it,
                    dayEpochDay = dayLong,
                    zone = zone,
                    doneMap = doneMap,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onNote = onNote,
                    onTogglePin = onTogglePin,
                    onToggleReminder = onToggleReminder
                )
            }
        }
        if (done.isNotEmpty()) {
            item { SectionHeader("已完成", done.size, MaterialTheme.colorScheme.primary) }
            items(done, key = { it.id }) {
                TaskSwipeItem(
                    task = it,
                    dayEpochDay = dayLong,
                    zone = zone,
                    doneMap = doneMap,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onNote = onNote,
                    onTogglePin = onTogglePin,
                    onToggleReminder = onToggleReminder
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    defaultDateText: String,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val initToday = TimeText.formatDate(today)
    val initTomorrow = TimeText.formatDate(today.plusDays(1))

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateMode by remember {
        mutableStateOf(
            when (defaultDateText) {
                initToday -> "today"
                initTomorrow -> "tomorrow"
                "" -> "today"
                else -> "pick"
            }
        )
    }
    var pickedDate by remember {
        mutableStateOf(if (dateMode == "pick") TimeText.parseDate(defaultDateText) else null)
    }
    var durationType by remember { mutableStateOf(TASK_DURATION_SHORT) }
    var repeatType by remember { mutableStateOf(TASK_REPEAT_ONCE) }
    var reminderOn by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val defaultMinute = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val baseDate = when (dateMode) {
        "today" -> today
        "tomorrow" -> today.plusDays(1)
        "pick" -> pickedDate ?: today
        else -> today
    }

    fun attemptSave() {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) { error = "请填写任务标题"; return }
        val anchor = TimeText.parseDateTimeToMillis(baseDate, defaultMinute, zone)
        onSave(
            TaskEntity(
                id = 0L,
                title = trimmed,
                note = note.trim().takeIf { it.isNotEmpty() },
                durationType = durationType,
                repeatType = repeatType,
                anchorAtEpochMillis = anchor,
                reminderOn = reminderOn
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(onClick = { })
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            shape = RoundedCornerShape(topStart = AppDimens.SheetRadius, topEnd = AppDimens.SheetRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(2.dp)
                        )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { attemptSave() }) {
                        Text(
                            "添加",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text("准备做什么?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = {
                        Text("添加描述", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DatePill("今天", dateMode == "today") { dateMode = "today" }
                        DatePill("明天", dateMode == "tomorrow") { dateMode = "tomorrow" }
                        DatePill(
                            label = if (dateMode == "pick" && pickedDate != null) TimeText.formatDate(pickedDate!!) else "选择日期",
                            selected = dateMode == "pick",
                            icon = Icons.Default.CalendarMonth,
                            onClick = { showDatePicker = true }
                        )
                        DatePill("没有日期", dateMode == "none") { dateMode = "none" }
                    }
                }
                // 类型 / 工作量 分段控件
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("短任务" to TASK_DURATION_SHORT, "长任务" to TASK_DURATION_LONG).forEach { (opt, value) ->
                        val sel = durationType == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minTouchTarget()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { durationType = value }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                opt,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Text(
                    text = "重复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeatType == TASK_REPEAT_ONCE,
                        onClick = { repeatType = TASK_REPEAT_ONCE },
                        label = { Text("一次性") }
                    )
                    FilterChip(
                        selected = repeatType == TASK_REPEAT_DAILY,
                        onClick = { repeatType = TASK_REPEAT_DAILY },
                        label = { Text("每天") }
                    )
                    FilterChip(
                        selected = repeatType == TASK_REPEAT_WEEKLY,
                        onClick = { repeatType = TASK_REPEAT_WEEKLY },
                        label = { Text("每周") }
                    )
                }
                if (repeatType == TASK_REPEAT_WEEKLY) {
                    Text(
                        text = "每周会按所选日期的「星期几」重复出现",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                // 底部工具栏：灰色图标 + 右上青色确认
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarIcon(
                        icon = Icons.Default.AlarmAdd,
                        contentDescription = "定时提醒",
                        tint = if (reminderOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { reminderOn = !reminderOn }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ToolbarIcon(
                        icon = Icons.Default.Autorenew,
                        contentDescription = "切换重复",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            repeatType = when (repeatType) {
                                TASK_REPEAT_ONCE -> TASK_REPEAT_DAILY
                                TASK_REPEAT_DAILY -> TASK_REPEAT_WEEKLY
                                else -> TASK_REPEAT_ONCE
                            }
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .minTouchTarget()
                            .clickable(onClick = { attemptSave() })
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "添加",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(12.dp).size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = (pickedDate ?: today).atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        pickedDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        dateMode = "pick"
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DatePill(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .minTouchTarget()
            .clip(RoundedCornerShape(AppDimens.ChipRadius))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .minTouchTarget()
            .clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.padding(11.dp).size(20.dp)
        )
    }
}
