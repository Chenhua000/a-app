package com.example.myfirstapp.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeText {
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatDate(date: LocalDate): String = DATE_FORMATTER.format(date)

    fun parseDate(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim(), DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            null
        }

    fun parseTimeMinute(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun formatMinute(minute: Int): String =
        TIME_FORMATTER.format(LocalTime.of(minute / 60, minute % 60))

    fun startOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun parseDateTimeToMillis(date: LocalDate, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(date, LocalTime.of(minute / 60, minute % 60))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    fun startOfWeek(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun weekdayShort(index: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(index - 1) { "第 $index 天" }

    fun epochDayToMillis(epochDay: Long, zone: ZoneId): Long =
        startOfDayMillis(LocalDate.ofEpochDay(epochDay), zone)
}
