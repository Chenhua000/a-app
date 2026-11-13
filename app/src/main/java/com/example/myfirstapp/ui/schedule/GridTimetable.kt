package com.example.myfirstapp.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.data.local.TimeSlotEntity
import com.example.myfirstapp.domain.PlacedOccurrence
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.CourseColorDark
import com.example.myfirstapp.ui.theme.CourseColorLight
import com.example.myfirstapp.ui.theme.CourseContainerDark
import com.example.myfirstapp.ui.theme.CourseContainerLight
import com.example.myfirstapp.ui.theme.isDarkColorScheme
import com.example.myfirstapp.ui.theme.minTouchTarget
import java.time.LocalDate

private val TIME_COLUMN_WIDTH = 44.dp

@Composable
fun GridTimetable(
    slots: List<TimeSlotEntity>,
    days: List<Int>,
    weekStart: LocalDate,
    today: LocalDate,
    placed: List<PlacedOccurrence>,
    rowHeight: Dp,
    onCourseClick: (PlacedOccurrence) -> Unit,
    onEmptyCellClick: (dayOfWeek: Int, slot: TimeSlotEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) return

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val byDay = androidx.compose.runtime.remember(placed) { placed.groupBy { it.dayOfWeek } }
    val dark = isDarkColorScheme(MaterialTheme.colorScheme.surface)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val dayWidth = ((maxWidth - TIME_COLUMN_WIDTH) / days.size).coerceAtLeast(36.dp)
        val totalWidth = TIME_COLUMN_WIDTH + dayWidth * days.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .width(totalWidth)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Spacer(modifier = Modifier.width(TIME_COLUMN_WIDTH))
                days.forEach { dayOfWeek ->
                    val date = weekStart.plusDays((dayOfWeek - 1).toLong())
                    HeaderCell(
                        date = date,
                        dayOfWeek = dayOfWeek,
                        isToday = date == today,
                        modifier = Modifier.width(dayWidth)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .width(totalWidth)
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                TimeColumn(
                    slots = slots,
                    rowHeight = rowHeight,
                    gridColor = gridColor
                )
                days.forEach { dayOfWeek ->
                    val dayItems = byDay[dayOfWeek].orEmpty()
                    DayColumn(
                        slots = slots,
                        items = dayItems,
                        rowHeight = rowHeight,
                        gridColor = gridColor,
                        dark = dark,
                        onCourseClick = onCourseClick,
                        onEmptyCellClick = { slot -> onEmptyCellClick(dayOfWeek, slot) },
                        modifier = Modifier.width(dayWidth)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    date: LocalDate,
    dayOfWeek: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .padding(horizontal = AppDimens.Space1, vertical = AppDimens.Space2)
            .clip(RoundedCornerShape(AppDimens.ChipRadius))
            .background(background)
            .padding(vertical = AppDimens.Space2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = TimeText.weekdayShort(dayOfWeek),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun TimeColumn(
    slots: List<TimeSlotEntity>,
    rowHeight: Dp,
    gridColor: Color
) {
    Column(modifier = Modifier.width(TIME_COLUMN_WIDTH)) {
        slots.forEach { slot ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .border(width = Dp.Hairline, color = gridColor),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = slot.label.removePrefix("第").removeSuffix("节"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = TimeText.formatMinute(slot.startMinute),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TimeText.formatMinute(slot.endMinute),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    slots: List<TimeSlotEntity>,
    items: List<PlacedOccurrence>,
    rowHeight: Dp,
    gridColor: Color,
    dark: Boolean,
    onCourseClick: (PlacedOccurrence) -> Unit,
    onEmptyCellClick: (TimeSlotEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.height(rowHeight * slots.size)) {
        Column(modifier = Modifier.fillMaxSize()) {
            slots.forEach { slot ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .border(width = Dp.Hairline, color = gridColor)
                        .minTouchTarget()
                        .semantics { contentDescription = "${slot.label} 空白，点击添加课程" }
                        .clickable(role = Role.Button) { onEmptyCellClick(slot) }
                )
            }
        }
        items.forEach { item ->
            val colorIndex = item.colorIndex.coerceIn(0, 7)
            val container = (if (dark) CourseContainerDark else CourseContainerLight)[colorIndex]
            val accent = (if (dark) CourseColorDark else CourseColorLight)[colorIndex]
            Box(
                modifier = Modifier
                    .padding(horizontal = AppDimens.Space1)
                    .offset(y = rowHeight * item.rowIndex)
                    .height(rowHeight * item.rowSpan - AppDimens.Space1)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppDimens.ChipRadius))
                    .background(container)
                    .minTouchTarget()
                    .semantics {
                        contentDescription = "${item.occurrence.name}，${TimeText.formatMinute(item.startMinute)} 至 ${TimeText.formatMinute(item.endMinute)}"
                    }
                    .clickable(role = Role.Button) { onCourseClick(item) }
                    .padding(horizontal = AppDimens.Space1, vertical = AppDimens.Space1)
            ) {
                Column {
                    Text(
                        text = item.occurrence.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = if (item.rowSpan > 1) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.occurrence.location?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "@$it",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = accent
                        )
                    }
                    if (item.occurrence.isRescheduled) {
                        Surface(
                            shape = RoundedCornerShape(AppDimens.Space2),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(top = AppDimens.Space1)
                        ) {
                            Text(
                                text = "调课",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = AppDimens.Space1, vertical = AppDimens.Space1)
                            )
                        }
                    }
                }
            }
        }
    }
}
