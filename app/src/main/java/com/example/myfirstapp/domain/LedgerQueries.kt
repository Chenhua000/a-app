package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TransactionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun filterLedgerEntries(
    entries: List<TransactionEntity>, query: String = "", category: String? = null,
    type: TransactionType? = null
): List<TransactionEntity> {
    val term = query.trim()
    return entries.filter {
        (category == null || it.category == category) && (type == null || it.type == type) &&
            (term.isEmpty() || it.note.orEmpty().contains(term, true) || it.category.contains(term, true) ||
                MoneyFormatter.formatCents(it.amountCents).contains(term))
    }
}

/** UTF-8 BOM and RFC 4180 escaping keep Chinese notes readable in spreadsheet tools. */
fun ledgerCsv(entries: List<TransactionEntity>, zone: ZoneId): String {
    fun text(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return buildString {
        append('\uFEFF')
        append("日期,类型,分类,金额（元）,备注\r\n")
        entries.forEach { entry ->
            append(text(Instant.ofEpochMilli(entry.occurredAtEpochMillis).atZone(zone).format(format)))
            append(','); append(text(if (entry.type == TransactionType.INCOME) "收入" else "支出"))
            append(','); append(text(entry.category))
            append(','); append(MoneyFormatter.formatCents(entry.amountCents))
            append(','); append(text(entry.note.orEmpty())); append("\r\n")
        }
    }
}
