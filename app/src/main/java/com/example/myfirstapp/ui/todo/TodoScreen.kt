package com.example.myfirstapp.ui.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.TodoEntity
import com.example.myfirstapp.domain.ReminderPlanner
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodoScreen(
    viewModel: TodoViewModel = viewModel()
) {
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    var showSheet by remember { mutableStateOf(false) }
    var hideCompleted by remember { mutableStateOf(false) }

    // 顶部提示自动消失
    var hiddenToken by remember { mutableStateOf(-1L) }
    LaunchedEffect(message?.token) {
        val current = message ?: return@LaunchedEffect
        delay(2200)
        hiddenToken = current.token
    }

    val visible = remember(todos, hideCompleted) {
        todos.filter { !hideCompleted || !it.isCompleted }
    }
    val groups = listOf(
        "逾期" to visible.filter {
            !it.isCompleted && dueDateOf(it, zone).isBefore(today)
        },
        "今天" to visible.filter {
            !it.isCompleted && dueDateOf(it, zone) == today
        },
        "以后" to visible.filter {
            !it.isCompleted && dueDateOf(it, zone).isAfter(today)
        },
        "已完成" to visible.filter { it.isCompleted }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TodaySummaryCard(
                todos = todos,
                today = today,
                zone = zone,
                hideCompleted = hideCompleted,
                onToggleHide = { hideCompleted = !hideCompleted },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (todos.isEmpty()) {
                        EmptyTodoState(onAdd = { showSheet = true })
                    } else {
                        FilteredEmptyState(onShowAll = { hideCompleted = false })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEach { (label, items) ->
                        if (items.isNotEmpty()) {
                            item(key = "header_$label") {
                                GroupHeader(
                                    label = label,
                                    count = items.size,
                                    accent = groupAccent(label)
                                )
                            }
                            items(items, key = { it.id }) { todo ->
                                TodoCard(
                                    todo = todo,
                                    today = today,
                                    zone = zone,
                                    overdue = label == "逾期",
                                    onToggle = { viewModel.setCompleted(todo, !todo.isCompleted) },
                                    onDelete = { viewModel.deleteTodo(todo) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (visible.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加待办", fontWeight = FontWeight.SemiBold) }
            )
        }

        val current = message
        if (current != null && current.token != hiddenToken) {
            AppInlineMessage(
                text = current.text,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
                isError = current.text.contains("失败"),
                onDismiss = null
            )
        }
    }

    if (showSheet) {
        TodoEditorSheet(
            defaultDateText = today.toString(),
            onDismiss = { showSheet = false },
            onSave = { title, note, date, time, reminder ->
                val result = viewModel.saveTodo(null, title, note, date, time, reminder)
                if (result is TodoValidation.Success) showSheet = false
                result
            }
        )
    }
}

private fun dueDateOf(todo: TodoEntity, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(todo.dueAtEpochMillis).atZone(zone).toLocalDate()

@Composable
private fun groupAccent(label: String): Color = when (label) {
    "逾期" -> MaterialTheme.colorScheme.error
    "今天" -> MaterialTheme.colorScheme.primary
    "已完成" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.tertiary
}

/** 顶部今日进度卡：环形进度 + 三项统计 + 隐藏已完成开关 */
@Composable
private fun TodaySummaryCard(
    todos: List<TodoEntity>,
    today: LocalDate,
    zone: ZoneId,
    hideCompleted: Boolean,
    onToggleHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overdueCount = todos.count {
        !it.isCompleted && dueDateOf(it, zone).isBefore(today)
    }
    val todayOpen = todos.count {
        !it.isCompleted && dueDateOf(it, zone) == today
    }
    val todayDone = todos.count {
        it.isCompleted && dueDateOf(it, zone) == today
    }
    val todayTotal = todayOpen + todayDone
    val progress = if (todayTotal == 0) 0f else todayDone.toFloat() / todayTotal

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = progress,
                    accent = MaterialTheme.colorScheme.primary,
                    centerText = if (todayTotal == 0) "–" else "$todayDone/$todayTotal"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            todayTotal == 0 -> "今天没有待办"
                            todayOpen == 0 -> "今天全部完成"
                            else -> "还剩 $todayOpen 件要做"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HideCompletedToggle(active = hideCompleted, onToggle = onToggleHide)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(
                    label = "逾期",
                    count = overdueCount,
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "今天",
                    count = todayOpen,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "已完成",
                    count = todayDone,
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    accent: Color,
    centerText: String
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HideCompletedToggle(active: Boolean, onToggle: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(180),
        label = "hideBg"
    )
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .minTouchTarget()
            .clip(CircleShape)
            .background(bg)
            .toggleable(
                value = active,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (active) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = "隐藏已完成",
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, accent: Color) {
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
private fun TodoCard(
    todo: TodoEntity,
    today: LocalDate,
    zone: ZoneId,
    overdue: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val completed = todo.isCompleted
    val due = dueDateOf(todo, zone)
    val clock = ReminderPlanner.formattedClock(todo.dueAtEpochMillis, zone)
    val dueAccent = when {
        completed -> MaterialTheme.colorScheme.onSurfaceVariant
        overdue -> MaterialTheme.colorScheme.error
        due == today -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dueText = when {
        completed -> "${due.monthValue}月${due.dayOfMonth}日 $clock"
        overdue -> "逾期 · ${due.monthValue}月${due.dayOfMonth}日 $clock"
        due == today -> "今天 $clock"
        due == today.plusDays(1) -> "明天 $clock"
        else -> "${due.monthValue}月${due.dayOfMonth}日 $clock"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleCheckbox(
                checked = completed,
                accent = if (completed) MaterialTheme.colorScheme.primary else dueAccent,
                onToggle = onToggle
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = dueAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.labelMedium,
                        color = dueAccent
                    )
                }
                todo.note?.takeIf { it.isNotBlank() }?.let {
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
            IconButton(onClick = onDelete, modifier = Modifier.minTouchTarget()) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
        label = "checkBg"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.4f,
        animationSpec = tween(160),
        label = "checkScale"
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
                modifier = Modifier
                    .size(16.dp)
                    .scale(checkScale)
            )
        }
    }
}

@Composable
private fun EmptyTodoState(onAdd: () -> Unit) {
    EmptyState(
        icon = Icons.Default.TaskAlt,
        title = "今天，从一件小事开始",
        subtitle = "把要做的事记下来，到时间会提醒你",
        action = {
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加待办", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun FilteredEmptyState(onShowAll: () -> Unit) {
    EmptyState(
        icon = Icons.Default.VisibilityOff,
        title = "已完成的任务已隐藏",
        subtitle = "开启「隐藏已完成」后，这些任务就不占列表了",
        action = {
            OutlinedButton(onClick = onShowAll, shape = RoundedCornerShape(14.dp)) {
                Text("显示已完成")
            }
        }
    )
}

@Composable
private fun TodoEditorSheet(
    defaultDateText: String,
    onDismiss: () -> Unit,
    onSave: (title: String, note: String, date: String, time: String, reminder: String) -> TodoValidation
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultDateText) }
    // 时间默认取「打开面板这一刻」的时钟，省得每次都要手动改
    var time by remember {
        val now = LocalTime.now()
        mutableStateOf(TimeText.formatMinute(now.hour * 60 + now.minute))
    }
    var reminder by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
                Text(
                    text = "新建待办",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("要做什么") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("日期 yyyy-MM-dd") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("时间 HH:mm") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可不填）") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("提前提醒分钟，留空跟随默认") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            if (title.trim().isEmpty()) {
                                error = "请填写标题"
                                return@Button
                            }
                            when (val result = onSave(title, note, date, time, reminder)) {
                                is TodoValidation.Error -> error = result.message
                                else -> Unit
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("保存", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
