package com.example.myfirstapp.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.data.local.CourseExceptionEntity
import com.example.myfirstapp.data.local.TimeSlotEntity
import com.example.myfirstapp.domain.PlacedOccurrence
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.ui.components.AppColorSwatch
import com.example.myfirstapp.ui.components.AppInlineMessage
import com.example.myfirstapp.ui.components.EmptyState
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.CourseColorDark
import com.example.myfirstapp.ui.theme.CourseColorLight
import com.example.myfirstapp.ui.theme.isDarkColorScheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenSlots: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rowHeightMode by viewModel.rowHeightMode.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    var editing by remember { mutableStateOf<CourseDraft?>(null) }
    var selected by remember { mutableStateOf<PlacedOccurrence?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message?.token) {
        if (message == null) return@LaunchedEffect
        delay(2600)
        viewModel.consumeMessage()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekToolbar(
            onPrevious = { viewModel.moveWeek(-1) },
            onNext = { viewModel.moveWeek(1) },
            onToday = { viewModel.goCurrentWeek() },
            onOpenSlots = onOpenSlots
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${TimeText.formatDate(uiState.weekStart)} 起这一周",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    editing = CourseDraft(
                        colorIndex = viewModel.suggestedColorIndex(),
                        slotId = uiState.slots.firstOrNull()?.id,
                        startMinute = uiState.slots.firstOrNull()?.startMinute ?: 8 * 60,
                        endMinute = uiState.slots.firstOrNull()?.endMinute ?: 8 * 60 + 45
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "添加课程")
            }
        }

        message?.let { current ->
            AppInlineMessage(
                text = current.text,
                isError = false,
                onDismiss = { viewModel.consumeMessage() }
            )
        }

        errorMessage?.let { text ->
            AppInlineMessage(
                text = text,
                isError = true,
                onDismiss = { errorMessage = null }
            )
        }

        if (uiState.slots.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Tune,
                title = "还没有设置节次",
                subtitle = "先到「节次设置」里设定每天有几节课、每节课的起止时间",
                action = {
                    Button(onClick = onOpenSlots) { Text("去设置节次") }
                }
            )
            return
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.Space4),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = AppDimens.CardRadius, topEnd = AppDimens.CardRadius)
        ) {
            GridTimetable(
                slots = uiState.slots,
                days = uiState.visibleDays,
                weekStart = uiState.weekStart,
                today = uiState.today,
                placed = uiState.placed,
                rowHeight = rowHeightMode.dp.dp,
                onCourseClick = { selected = it },
                onEmptyCellClick = { dayOfWeek, slot ->
                    editing = CourseDraft(
                        dayOfWeek = dayOfWeek,
                        slotId = slot.id,
                        startMinute = slot.startMinute,
                        endMinute = slot.endMinute,
                        colorIndex = viewModel.suggestedColorIndex()
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    editing?.let { draft ->
        CourseEditorSheet(
            draft = draft,
            slots = uiState.slots,
            onDismiss = { editing = null },
            onSave = { updated ->
                val result = viewModel.saveCourse(updated)
                if (result is ValidationResult.Error) {
                    errorMessage = result.message
                } else {
                    editing = null
                }
            }
        )
    }

    selected?.let { item ->
        CourseActionsSheet(
            item = item,
            zone = zone,
            onDismiss = { selected = null },
            onEdit = { courseId ->
                // 用数据库里的原始课程实体来编辑，完整保留 dayOfWeek / slotId / 提醒 / enabled 等字段
                val entity = uiState.courses.firstOrNull { it.id == courseId }
                if (entity != null) {
                    selected = null
                    editing = entity.toCourseDraft()
                } else {
                    // 极端情况下取不到实体：不要用残缺数据覆盖，直接关闭并提示
                    selected = null
                    errorMessage = "未找到该课程，请重新打开课表后再试"
                }
            },
            onCancelOccurrence = {
                viewModel.cancelOccurrence(item.occurrence.courseId, item.occurrence.originalDateEpochDay)
                selected = null
            },
            onReschedule = { date, start, end ->
                val result = viewModel.rescheduleOccurrence(
                    item.occurrence.courseId,
                    item.occurrence.originalDateEpochDay,
                    date,
                    start,
                    end
                )
                if (result is ValidationResult.Error) errorMessage = result.message else selected = null
            },
            onRestore = { viewModel.clearException(it) }
        )
    }
}

@Composable
private fun WeekToolbar(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenSlots: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
        }
        AssistChip(
            onClick = onToday,
            label = { Text(text = "今天") },
            leadingIcon = {
                Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSlots) {
            Icon(Icons.Default.Tune, contentDescription = "节次设置")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseEditorSheet(
    draft: CourseDraft,
    slots: List<TimeSlotEntity>,
    onDismiss: () -> Unit,
    onSave: (CourseDraft) -> Unit
) {
    var name by remember(draft.id, draft.dayOfWeek, draft.slotId) { mutableStateOf(draft.name) }
    var dayOfWeek by remember(draft.id) { mutableStateOf(draft.dayOfWeek) }
    var slotId by remember(draft.id) { mutableStateOf(draft.slotId) }
    var startText by remember(draft.id) { mutableStateOf(TimeText.formatMinute(draft.startMinute)) }
    var endText by remember(draft.id) { mutableStateOf(TimeText.formatMinute(draft.endMinute)) }
    var location by remember(draft.id) { mutableStateOf(draft.location) }
    var teacher by remember(draft.id) { mutableStateOf(draft.teacher) }
    var reminder by remember(draft.id) { mutableStateOf(draft.reminderText) }
    var colorIndex by remember(draft.id) { mutableStateOf(draft.colorIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppDimens.Space5, vertical = AppDimens.Space2),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space3),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Text(
                    text = if (draft.id == null) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(text = "星期", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                    items(listOf(1, 2, 3, 4, 5, 6, 7)) { day ->
                        FilterChip(
                            selected = day == dayOfWeek,
                            onClick = { dayOfWeek = day },
                            label = { Text(TimeText.weekdayShort(day)) }
                        )
                    }
                }
            }
            if (slots.isNotEmpty()) {
                item {
                    Text(text = "第几节", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                        items(slots) { slot ->
                            FilterChip(
                                selected = slot.id == slotId,
                                onClick = {
                                    slotId = slot.id
                                    startText = TimeText.formatMinute(slot.startMinute)
                                    endText = TimeText.formatMinute(slot.endMinute)
                                },
                                label = { Text(slot.label) }
                            )
                        }
                    }
                }
            }
            item {
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
            }
            item {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("提前提醒分钟，留空跟随默认") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(text = "格子颜色", style = MaterialTheme.typography.labelLarge)
                val palette = if (isDarkColorScheme(MaterialTheme.colorScheme.surface)) {
                    CourseColorDark
                } else {
                    CourseColorLight
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                    items(8) { index ->
                        AppColorSwatch(
                            color = palette[index],
                            selected = index == colorIndex,
                            onClick = { colorIndex = index },
                            contentDescription = "课程颜色 ${index + 1}"
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space3)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            val start = TimeText.parseTimeMinute(startText)
                            val end = TimeText.parseTimeMinute(endText)
                            if (start == null || end == null) return@Button
                            onSave(
                                draft.copy(
                                    name = name,
                                    dayOfWeek = dayOfWeek,
                                    slotId = slotId,
                                    startMinute = start,
                                    endMinute = end,
                                    location = location,
                                    teacher = teacher,
                                    reminderText = reminder,
                                    colorIndex = colorIndex
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存") }
                }
                Spacer(modifier = Modifier.height(AppDimens.Space4))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseActionsSheet(
    item: PlacedOccurrence,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onEdit: (courseId: Long) -> Unit,
    onCancelOccurrence: () -> Unit,
    onReschedule: (date: String, start: String, end: String) -> Unit,
    onRestore: (CourseExceptionEntity) -> Unit
) {
    var rescheduleMode by remember { mutableStateOf(false) }
    var dateText by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf(TimeText.formatMinute(item.startMinute)) }
    var endText by remember { mutableStateOf(TimeText.formatMinute(item.endMinute)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppDimens.Space5, vertical = AppDimens.Space2),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space2),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Text(
                    text = item.occurrence.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val date = Instant.ofEpochMilli(item.occurrence.startAtMillis).atZone(zone).toLocalDate()
                Text(
                    text = buildString {
                        append(TimeText.formatDate(date))
                        append(" ")
                        append(TimeText.weekdayShort(date.dayOfWeek.value))
                        append(" ${TimeText.formatMinute(item.startMinute)}-${TimeText.formatMinute(item.endMinute)}")
                        item.occurrence.location?.takeIf { it.isNotBlank() }?.let { append("\n@$it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!rescheduleMode) {
                item {
                    SheetAction(
                        icon = Icons.Default.EditCalendar,
                        text = "编辑这门课",
                        // 只传课程 id，由上层从数据库取真实实体，避免用展示用的 occurrence
                        // 反推 CourseEntity（会写死 dayOfWeek=1 并丢掉 slotId/提醒/enabled，导致课程"消失"）
                        onClick = { onEdit(item.occurrence.courseId) }
                    )
                }
                item {
                    SheetAction(
                        icon = Icons.Default.EventBusy,
                        text = "本次停课",
                        onClick = onCancelOccurrence
                    )
                }
                item {
                    SheetAction(
                        icon = Icons.Default.Restore,
                        text = "本次调课",
                        onClick = { rescheduleMode = true }
                    )
                }
                if (item.occurrence.isRescheduled) {
                    item {
                        Text(
                            text = "这节课已被调课处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("调到哪一天 yyyy-MM-dd") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            label = { Text("开始 HH:mm") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it },
                            label = { Text("结束 HH:mm") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Button(
                        onClick = { onReschedule(dateText, startText, endText) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("确定调课") }
                }
            }
            item { Spacer(modifier = Modifier.height(AppDimens.Space3)) }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(AppDimens.Space3))
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

private fun LocalDate.unusedMarker(): LocalDate = this
