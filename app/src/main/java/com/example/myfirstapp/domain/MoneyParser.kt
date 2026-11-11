package com.example.myfirstapp.domain

object MoneyFormatter {
    fun formatCents(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        return "$sign$yuan.${fen.toString().padStart(2, '0')}"
    }
}

object MoneyParser {
    fun parseYuanToCents(input: String): Long? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val parts = text.split(".")
        return when (parts.size) {
            1 -> parseWholePart(parts[0])?.let { it * 100 }
            2 -> {
                if (parts[1].length > 2) return null
                if (parts[1].any { !it.isDigit() }) return null
                val yuan = parseWholePart(parts[0]) ?: return null
                val fen = parts[1].padEnd(2, '0').toLongOrNull() ?: return null
                if (yuan < 0) return null
                yuan * 100 + fen
            }
            else -> null
        }
    }

    private fun parseWholePart(value: String): Long? {
        if (value.isEmpty()) return null
        if (value.any { !it.isDigit() }) return null
        return value.toLongOrNull()
    }
}
